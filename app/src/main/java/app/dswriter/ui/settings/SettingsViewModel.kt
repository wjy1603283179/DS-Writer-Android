package app.dswriter.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.dswriter.data.remote.ApiConnectionTester
import app.dswriter.data.remote.ConnectionFailure
import app.dswriter.data.remote.ConnectionTestResult
import app.dswriter.domain.model.MAX_CONTEXT_WINDOW
import app.dswriter.domain.model.MIN_CONTEXT_WINDOW
import app.dswriter.domain.model.ModelTuning
import app.dswriter.domain.model.Provider
import app.dswriter.domain.settings.BaseUrlRejection
import app.dswriter.domain.settings.BaseUrlResolution
import app.dswriter.domain.settings.BaseUrlValidator
import app.dswriter.domain.settings.DiscoveredModel
import app.dswriter.domain.settings.ModelDiscovery
import app.dswriter.domain.settings.ModelDiscoveryFailure
import app.dswriter.domain.settings.ModelDiscoveryResult
import app.dswriter.domain.settings.ProviderDraft
import app.dswriter.domain.settings.ProvidersRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The provider form.
 *
 * Every field is a string so it can be edited freely, including transiently invalid values;
 * validation happens on save. Temperature and the output cap are blank by default, which means
 * "send nothing" so the server's own defaults apply.
 */
data class ProviderDraftState(
    val id: String? = null,
    val name: String = "",
    val baseUrl: String = "",
    val modelId: String = "",
    val contextWindow: String = "24576",
    val maxOutputTokens: String = "",
    val temperature: String = "",
    val supportsVision: Boolean = false,
    val apiKeyInput: String = "",
    val hasStoredApiKey: Boolean = false,
    val isNew: Boolean = true,
)

data class SettingsUiState(
    val providers: List<Provider> = emptyList(),
    val activeProviderId: String? = null,
    val suggestRecapWhenContextIsFull: Boolean = false,
    val draft: ProviderDraftState? = null,
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val status: SettingsStatus? = null,
    val isDiscoveringModels: Boolean = false,
    val discoveredModels: List<DiscoveredModel> = emptyList(),
    val discoveryStatus: DiscoveryStatus? = null,
)

enum class DiscoveryStatus { FOUND, NONE, UNSUPPORTED, FAILED }

enum class SettingsStatus {
    SAVED,
    DELETED,
    KEY_CLEARED,
    CONNECTION_SUCCESS,
    BASE_URL_REQUIRED,
    BASE_URL_INVALID,
    BASE_URL_INSECURE,
    MODEL_ID_REQUIRED,
    CONTEXT_WINDOW_INVALID,
    NUMERIC_FIELD_INVALID,
    API_KEY_REQUIRED,
    UNAUTHORIZED,
    RATE_LIMITED,
    TIMEOUT,
    TLS_ERROR,
    NETWORK_ERROR,
    SERVER_ERROR,
    UNEXPECTED_RESPONSE,
    STORAGE_FAILED,
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val providers: ProvidersRepository,
    private val connectionTester: ApiConnectionTester,
    private val modelDiscovery: ModelDiscovery,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var draftEdited = false

    init {
        viewModelScope.launch {
            providers.catalog.collect { catalog ->
                _uiState.update { current ->
                    current.copy(
                        providers = catalog.providers,
                        activeProviderId = catalog.activeProviderId,
                    )
                }
            }
        }
        viewModelScope.launch {
            providers.suggestRecapWhenContextIsFull.collect { enabled ->
                _uiState.update { it.copy(suggestRecapWhenContextIsFull = enabled) }
            }
        }
    }

    // --- provider list -------------------------------------------------------------------

    fun editProvider(id: String) {
        val provider = _uiState.value.providers.firstOrNull { it.id == id } ?: return
        draftEdited = false
        _uiState.update {
            it.copy(
                status = null,
                discoveredModels = emptyList(),
                discoveryStatus = null,
                draft = ProviderDraftState(
                    id = provider.id,
                    name = provider.name,
                    baseUrl = provider.baseUrl,
                    modelId = provider.modelId,
                    contextWindow = provider.contextWindow.toString(),
                    maxOutputTokens = provider.tuning.maxOutputTokens?.toString().orEmpty(),
                    temperature = provider.tuning.temperature?.toString().orEmpty(),
                    supportsVision = provider.supportsVision,
                    hasStoredApiKey = provider.hasApiKey,
                    isNew = false,
                ),
            )
        }
    }

    fun addProvider() {
        draftEdited = false
        _uiState.update {
            it.copy(
                status = null,
                discoveredModels = emptyList(),
                discoveryStatus = null,
                draft = ProviderDraftState(),
            )
        }
    }

    fun closeDraft() {
        _uiState.update {
            it.copy(draft = null, status = null, discoveredModels = emptyList(), discoveryStatus = null)
        }
    }

    fun onDraftNameChanged(value: String) = updateDraft { it.copy(name = value) }

    fun onDraftBaseUrlChanged(value: String) = updateDraft { it.copy(baseUrl = value) }

    fun onDraftModelIdChanged(value: String) = updateDraft { it.copy(modelId = value) }

    fun onDraftContextWindowChanged(value: String) =
        updateDraft { it.copy(contextWindow = value.filter(Char::isDigit)) }

    fun onDraftMaxOutputTokensChanged(value: String) =
        updateDraft { it.copy(maxOutputTokens = value.filter(Char::isDigit)) }

    fun onDraftTemperatureChanged(value: String) =
        updateDraft { it.copy(temperature = value.filter { c -> c.isDigit() || c == '.' }) }

    fun onDraftSupportsVisionChanged(value: Boolean) = updateDraft { it.copy(supportsVision = value) }

    fun onDraftApiKeyChanged(value: String) = updateDraft { it.copy(apiKeyInput = value) }

    private fun updateDraft(transform: (ProviderDraftState) -> ProviderDraftState) {
        draftEdited = true
        _uiState.update { current ->
            val draft = current.draft ?: return@update current
            current.copy(draft = transform(draft), status = null)
        }
    }

    fun activateProvider(id: String) {
        viewModelScope.launch {
            runCatching { providers.setActiveProvider(id) }
                .onFailure { _uiState.update { s -> s.copy(status = SettingsStatus.STORAGE_FAILED) } }
        }
    }

    fun deleteProvider(id: String) {
        viewModelScope.launch {
            runCatching { providers.removeProvider(id) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            draft = it.draft?.takeIf { draft -> draft.id != id },
                            status = SettingsStatus.DELETED,
                        )
                    }
                }
                .onFailure { _uiState.update { s -> s.copy(status = SettingsStatus.STORAGE_FAILED) } }
        }
    }

    // --- provider form -------------------------------------------------------------------

    fun saveDraft() {
        val draft = _uiState.value.draft ?: return
        if (_uiState.value.isSaving || _uiState.value.isTesting) return

        val resolution = BaseUrlValidator.normalize(draft.baseUrl)
        if (resolution is BaseUrlResolution.Rejected) {
            _uiState.update { it.copy(status = resolution.reason.toStatus()) }
            return
        }
        val baseUrl = (resolution as BaseUrlResolution).normalizedUrl
        if (draft.modelId.isBlank()) {
            _uiState.update { it.copy(status = SettingsStatus.MODEL_ID_REQUIRED) }
            return
        }
        val contextWindow = draft.contextWindow.toIntOrNull()
        if (contextWindow == null || contextWindow !in MIN_CONTEXT_WINDOW..MAX_CONTEXT_WINDOW) {
            _uiState.update { it.copy(status = SettingsStatus.CONTEXT_WINDOW_INVALID) }
            return
        }
        val maxOutputTokens = draft.maxOutputTokens.toIntOrNull()
        if (draft.maxOutputTokens.isNotBlank() && (maxOutputTokens == null || maxOutputTokens <= 0)) {
            _uiState.update { it.copy(status = SettingsStatus.NUMERIC_FIELD_INVALID) }
            return
        }
        val temperature = draft.temperature.toDoubleOrNull()
        if (draft.temperature.isNotBlank() &&
            (temperature == null || temperature < 0.0 || temperature > 2.0)
        ) {
            _uiState.update { it.copy(status = SettingsStatus.NUMERIC_FIELD_INVALID) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, status = null) }
            runCatching {
                val providerDraft = ProviderDraft(
                    name = draft.name,
                    baseUrl = baseUrl,
                    modelId = draft.modelId.trim(),
                    contextWindow = contextWindow,
                    tuning = ModelTuning(
                        maxOutputTokens = maxOutputTokens,
                        temperature = temperature,
                    ),
                    supportsVision = draft.supportsVision,
                )
                if (draft.id == null) {
                    val newId = providers.addProvider(providerDraft)
                    if (draft.apiKeyInput.isNotBlank()) {
                        providers.updateApiKey(newId, draft.apiKeyInput)
                    }
                } else {
                    providers.upsertProvider(draft.id, providerDraft)
                    if (draft.apiKeyInput.isNotBlank()) {
                        providers.updateApiKey(draft.id, draft.apiKeyInput)
                    }
                }
            }.onSuccess {
                draftEdited = false
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        draft = null,
                        discoveredModels = emptyList(),
                        discoveryStatus = null,
                        status = SettingsStatus.SAVED,
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isSaving = false, status = SettingsStatus.STORAGE_FAILED) }
            }
        }
    }

    fun clearApiKey() {
        val id = _uiState.value.draft?.id ?: return
        viewModelScope.launch {
            runCatching { providers.clearApiKey(id) }
                .onSuccess {
                    _uiState.update { current ->
                        current.copy(
                            draft = current.draft?.copy(hasStoredApiKey = false, apiKeyInput = ""),
                            status = SettingsStatus.KEY_CLEARED,
                        )
                    }
                }
                .onFailure {
                    _uiState.update { s -> s.copy(status = SettingsStatus.STORAGE_FAILED) }
                }
        }
    }

    fun testConnection() {
        val draft = _uiState.value.draft ?: return
        if (_uiState.value.isTesting) return
        val resolution = BaseUrlValidator.normalize(draft.baseUrl)
        if (resolution is BaseUrlResolution.Rejected) {
            _uiState.update { it.copy(status = resolution.reason.toStatus()) }
            return
        }
        val baseUrl = (resolution as BaseUrlResolution).normalizedUrl

        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true, status = null) }
            val apiKey = draft.apiKeyInput.takeIf { it.isNotBlank() }
                ?: draft.id?.let { runCatching { providers.getApiKey(it) }.getOrNull() }
            // No key is not a refusal: a local server commonly has none, and the answer from the
            // server is what settles whether the key is needed.
            val status = runCatching { connectionTester.test(baseUrl, apiKey) }.fold(
                onSuccess = { it.toStatus() },
                onFailure = { SettingsStatus.NETWORK_ERROR },
            )
            _uiState.update { it.copy(isTesting = false, status = status) }
        }
    }

    /** Lists what the server advertises, so the model name never has to be typed. */
    fun discoverModels() {
        val draft = _uiState.value.draft ?: return
        if (_uiState.value.isDiscoveringModels) return
        val resolution = BaseUrlValidator.normalize(draft.baseUrl)
        if (resolution is BaseUrlResolution.Rejected) {
            _uiState.update {
                it.copy(discoveryStatus = null, status = resolution.reason.toStatus())
            }
            return
        }
        val baseUrl = (resolution as BaseUrlResolution).normalizedUrl

        viewModelScope.launch {
            _uiState.update { it.copy(isDiscoveringModels = true, discoveryStatus = null) }
            val apiKey = draft.apiKeyInput.takeIf { it.isNotBlank() }
                ?: draft.id?.let { runCatching { providers.getApiKey(it) }.getOrNull() }
            val result = runCatching { modelDiscovery.listModels(baseUrl, apiKey) }
                .getOrElse { ModelDiscoveryResult.Failure(ModelDiscoveryFailure.NETWORK) }
            _uiState.update { state ->
                when (result) {
                    is ModelDiscoveryResult.Success -> state.copy(
                        isDiscoveringModels = false,
                        discoveredModels = result.models,
                        discoveryStatus = if (result.models.isEmpty()) {
                            DiscoveryStatus.NONE
                        } else {
                            DiscoveryStatus.FOUND
                        },
                    )
                    ModelDiscoveryResult.Unsupported -> state.copy(
                        isDiscoveringModels = false,
                        discoveredModels = emptyList(),
                        discoveryStatus = DiscoveryStatus.UNSUPPORTED,
                    )
                    is ModelDiscoveryResult.Failure -> state.copy(
                        isDiscoveringModels = false,
                        discoveredModels = emptyList(),
                        discoveryStatus = DiscoveryStatus.FAILED,
                    )
                }
            }
        }
    }

    fun onDiscoveredModelSelected(modelId: String) = updateDraft { it.copy(modelId = modelId) }

    // --- assistance ----------------------------------------------------------------------

    fun onSuggestRecapChanged(enabled: Boolean) {
        _uiState.update { it.copy(suggestRecapWhenContextIsFull = enabled) }
        viewModelScope.launch { runCatching { providers.updateSuggestRecapWhenContextIsFull(enabled) } }
    }

    private fun BaseUrlRejection.toStatus(): SettingsStatus = when (this) {
        BaseUrlRejection.REQUIRED -> SettingsStatus.BASE_URL_REQUIRED
        BaseUrlRejection.INSECURE_PUBLIC_HOST -> SettingsStatus.BASE_URL_INSECURE
        else -> SettingsStatus.BASE_URL_INVALID
    }

    private fun ConnectionTestResult.toStatus(): SettingsStatus = when (this) {
        ConnectionTestResult.Success -> SettingsStatus.CONNECTION_SUCCESS
        is ConnectionTestResult.Failure -> when (reason) {
            ConnectionFailure.UNAUTHORIZED -> SettingsStatus.UNAUTHORIZED
            ConnectionFailure.RATE_LIMITED -> SettingsStatus.RATE_LIMITED
            ConnectionFailure.TIMEOUT -> SettingsStatus.TIMEOUT
            ConnectionFailure.TLS -> SettingsStatus.TLS_ERROR
            ConnectionFailure.NETWORK -> SettingsStatus.NETWORK_ERROR
            ConnectionFailure.SERVER -> SettingsStatus.SERVER_ERROR
            ConnectionFailure.UNEXPECTED_RESPONSE -> SettingsStatus.UNEXPECTED_RESPONSE
        }
    }
}
