package app.dswriter.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dswriter.R
import app.dswriter.domain.model.DEFAULT_CONTEXT_WINDOW
import app.dswriter.domain.model.MAX_CONTEXT_WINDOW
import app.dswriter.domain.model.MIN_CONTEXT_WINDOW
import app.dswriter.domain.model.Provider
import app.dswriter.ui.components.PageScaffold
import app.dswriter.ui.theme.Dimens
import app.dswriter.ui.theme.PillShape
import app.dswriter.ui.theme.Spacing

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onBack = onBack,
        onAddProvider = viewModel::addProvider,
        onEditProvider = viewModel::editProvider,
        onActivateProvider = viewModel::activateProvider,
        onDeleteProvider = viewModel::deleteProvider,
        onCloseDraft = viewModel::closeDraft,
        onDraftNameChanged = viewModel::onDraftNameChanged,
        onDraftBaseUrlChanged = viewModel::onDraftBaseUrlChanged,
        onDraftModelIdChanged = viewModel::onDraftModelIdChanged,
        onDraftContextWindowChanged = viewModel::onDraftContextWindowChanged,
        onDraftMaxOutputTokensChanged = viewModel::onDraftMaxOutputTokensChanged,
        onDraftTemperatureChanged = viewModel::onDraftTemperatureChanged,
        onDraftSupportsVisionChanged = viewModel::onDraftSupportsVisionChanged,
        onDraftApiKeyChanged = viewModel::onDraftApiKeyChanged,
        onDiscoverModels = viewModel::discoverModels,
        onSelectDiscoveredModel = viewModel::onDiscoveredModelSelected,
        onSaveDraft = viewModel::saveDraft,
        onTestConnection = viewModel::testConnection,
        onClearApiKey = viewModel::clearApiKey,
        onSuggestRecapChanged = viewModel::onSuggestRecapChanged,
    )
}

@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onAddProvider: () -> Unit = {},
    onEditProvider: (String) -> Unit = {},
    onActivateProvider: (String) -> Unit = {},
    onDeleteProvider: (String) -> Unit = {},
    onCloseDraft: () -> Unit = {},
    onDraftNameChanged: (String) -> Unit = {},
    onDraftBaseUrlChanged: (String) -> Unit = {},
    onDraftModelIdChanged: (String) -> Unit = {},
    onDraftContextWindowChanged: (String) -> Unit = {},
    onDraftMaxOutputTokensChanged: (String) -> Unit = {},
    onDraftTemperatureChanged: (String) -> Unit = {},
    onDraftSupportsVisionChanged: (Boolean) -> Unit = {},
    onDraftApiKeyChanged: (String) -> Unit = {},
    onDiscoverModels: () -> Unit = {},
    onSelectDiscoveredModel: (String) -> Unit = {},
    onSaveDraft: () -> Unit = {},
    onTestConnection: () -> Unit = {},
    onClearApiKey: () -> Unit = {},
    onSuggestRecapChanged: (Boolean) -> Unit = {},
) {
    PageScaffold(
        title = stringResource(R.string.api_settings_title),
        onBack = onBack,
        footer = state.draft?.let { draft ->
            {
                ProviderFormActions(
                    draft = draft,
                    busy = state.isSaving || state.isTesting,
                    onSave = onSaveDraft,
                    onTest = onTestConnection,
                    onCancel = onCloseDraft,
                )
            }
        },
    ) {
        val draft = state.draft
        if (draft != null) {
            ProviderForm(
                state = state,
                draft = draft,
                onNameChanged = onDraftNameChanged,
                onBaseUrlChanged = onDraftBaseUrlChanged,
                onModelIdChanged = onDraftModelIdChanged,
                onContextWindowChanged = onDraftContextWindowChanged,
                onMaxOutputTokensChanged = onDraftMaxOutputTokensChanged,
                onTemperatureChanged = onDraftTemperatureChanged,
                onSupportsVisionChanged = onDraftSupportsVisionChanged,
                onApiKeyChanged = onDraftApiKeyChanged,
                onDiscoverModels = onDiscoverModels,
                onSelectDiscoveredModel = onSelectDiscoveredModel,
                onClearApiKey = onClearApiKey,
            )
        } else {
            ProviderList(
                providers = state.providers,
                activeProviderId = state.activeProviderId,
                onAdd = onAddProvider,
                onEdit = onEditProvider,
                onActivate = onActivateProvider,
                onDelete = onDeleteProvider,
            )
        }
        state.status?.let { SettingsStatusBanner(it) }
        AssistanceSection(
            enabled = state.suggestRecapWhenContextIsFull,
            onChanged = onSuggestRecapChanged,
        )
    }
}

/**
 * The form's action row.
 *
 * It lives in the scaffold footer, not in the scrolling content, because a form this tall pushes
 * its own submit button off the bottom of a phone screen where it cannot be tapped.
 */
@Composable
private fun ProviderFormActions(
    draft: ProviderDraftState,
    busy: Boolean,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onSave,
            enabled = !busy,
            modifier = Modifier.weight(1f).heightIn(min = Dimens.touchTarget),
        ) {
            Text(stringResource(R.string.save_provider))
        }
        // Testing needs something to test: a new service has no stored key yet.
        if (!draft.isNew) {
            OutlinedButton(
                onClick = onTest,
                enabled = !busy,
                modifier = Modifier.weight(1f).heightIn(min = Dimens.touchTarget),
            ) {
                Text(stringResource(R.string.test_connection))
            }
        }
        TextButton(onClick = onCancel, enabled = !busy) {
            Text(stringResource(R.string.cancel))
        }
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun ProviderList(
    providers: List<Provider>,
    activeProviderId: String?,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onActivate: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    if (providers.isEmpty()) {
        Text(
            text = stringResource(R.string.provider_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    providers.forEach { provider ->
        ProviderRow(
            provider = provider,
            isActive = provider.id == activeProviderId,
            onEdit = { onEdit(provider.id) },
            onActivate = { onActivate(provider.id) },
            onDelete = { onDelete(provider.id) },
        )
    }
    OutlinedButton(
        onClick = onAdd,
        modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.touchTarget),
    ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(
            text = stringResource(R.string.provider_add),
            modifier = Modifier.padding(start = Spacing.small),
        )
    }
}

@Composable
private fun ProviderRow(
    provider: Provider,
    isActive: Boolean,
    onEdit: () -> Unit,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (isActive) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.regular),
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
                if (isActive) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.provider_active),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                    Surface(
                        shape = PillShape,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Text(
                            text = stringResource(R.string.provider_active),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.padding(horizontal = Spacing.small, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    text = provider.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = provider.modelId.ifBlank {
                        stringResource(R.string.provider_incomplete)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = provider.baseUrl,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                if (!isActive) {
                    TextButton(
                        onClick = onActivate,
                        enabled = provider.isConfigured,
                        modifier = Modifier.heightIn(min = Dimens.touchTarget),
                    ) {
                        Text(stringResource(R.string.provider_use))
                    }
                }
                TextButton(
                    onClick = onEdit,
                    modifier = Modifier.heightIn(min = Dimens.touchTarget),
                ) {
                    Text(stringResource(R.string.provider_edit))
                }
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.heightIn(min = Dimens.touchTarget),
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(R.string.provider_delete),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = Spacing.tiny),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderForm(
    state: SettingsUiState,
    draft: ProviderDraftState,
    onNameChanged: (String) -> Unit,
    onBaseUrlChanged: (String) -> Unit,
    onModelIdChanged: (String) -> Unit,
    onContextWindowChanged: (String) -> Unit,
    onMaxOutputTokensChanged: (String) -> Unit,
    onTemperatureChanged: (String) -> Unit,
    onSupportsVisionChanged: (Boolean) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onDiscoverModels: () -> Unit,
    onSelectDiscoveredModel: (String) -> Unit,
    onClearApiKey: () -> Unit,
) {
    val busy = state.isSaving || state.isTesting
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }
    Text(
        text = stringResource(
            if (draft.isNew) R.string.provider_form_new else R.string.provider_form_edit,
        ),
        style = MaterialTheme.typography.titleMedium,
    )
    FormField(
        value = draft.name,
        onValueChange = onNameChanged,
        label = stringResource(R.string.provider_name_label),
        placeholder = stringResource(R.string.provider_name_placeholder),
        enabled = !busy,
    )
    FormField(
        value = draft.baseUrl,
        onValueChange = onBaseUrlChanged,
        label = stringResource(R.string.base_url_label),
        placeholder = stringResource(R.string.base_url_placeholder),
        enabled = !busy,
    )
    Text(
        text = stringResource(R.string.base_url_guide),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FormField(
        value = draft.modelId,
        onValueChange = onModelIdChanged,
        label = stringResource(R.string.model_id_label),
        placeholder = stringResource(R.string.model_id_placeholder),
        enabled = !busy,
    )
    OutlinedButton(
        onClick = onDiscoverModels,
        enabled = !busy && !state.isDiscoveringModels,
        modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.touchTarget),
    ) {
        Text(
            stringResource(
                if (state.isDiscoveringModels) {
                    R.string.discovering_models
                } else {
                    R.string.discover_models
                },
            ),
        )
    }
    state.discoveryStatus?.let { status ->
        Text(
            text = when (status) {
                DiscoveryStatus.FOUND -> stringResource(R.string.discover_found)
                DiscoveryStatus.NONE -> stringResource(R.string.discover_none)
                DiscoveryStatus.UNSUPPORTED -> stringResource(R.string.discover_unsupported)
                DiscoveryStatus.FAILED -> stringResource(R.string.discover_failed)
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (status == DiscoveryStatus.FOUND) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
    }
    state.discoveredModels.forEach { model ->
        Surface(
            onClick = { onSelectDiscoveredModel(model.id) },
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.touchTarget),
        ) {
            Text(
                text = model.displayLabel,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(Spacing.medium),
            )
        }
    }
    FormField(
        value = draft.apiKeyInput,
        onValueChange = onApiKeyChanged,
        label = stringResource(R.string.api_key_label),
        placeholder = stringResource(R.string.api_key_placeholder),
        enabled = !busy,
        isSecret = true,
        supporting = stringResource(
            if (draft.hasStoredApiKey) R.string.api_key_saved else R.string.api_key_not_saved,
        ),
    )
    if (draft.hasStoredApiKey && draft.id != null) {
        TextButton(onClick = onClearApiKey, enabled = !busy) {
            Text(stringResource(R.string.clear_api_key))
        }
    }
    // Everything below only matters when the service needs tuning; collapsed by default so that
    // name / address / model / key stay a short form on a phone screen.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.touchTarget)
            .toggleable(
                value = advancedExpanded,
                enabled = !busy,
                role = Role.Button,
                onValueChange = { advancedExpanded = it },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (advancedExpanded) {
                Icons.Default.ExpandLess
            } else {
                Icons.Default.ExpandMore
            },
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(R.string.advanced_settings),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = Spacing.small),
        )
    }
    if (advancedExpanded) {
        FormField(
            value = draft.contextWindow,
            onValueChange = onContextWindowChanged,
            label = stringResource(R.string.local_context_label),
            placeholder = DEFAULT_CONTEXT_WINDOW.toString(),
            enabled = !busy,
            numeric = true,
            supporting = stringResource(R.string.local_context_help),
        )
        FormField(
            value = draft.maxOutputTokens,
            onValueChange = onMaxOutputTokensChanged,
            label = stringResource(R.string.max_output_label),
            placeholder = "",
            enabled = !busy,
            numeric = true,
            supporting = stringResource(R.string.max_output_help),
        )
        FormField(
            value = draft.temperature,
            onValueChange = onTemperatureChanged,
            label = stringResource(R.string.temperature_label),
            placeholder = "",
            enabled = !busy,
            decimal = true,
            supporting = stringResource(R.string.temperature_help),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = draft.supportsVision,
                    enabled = !busy,
                    role = Role.Switch,
                    onValueChange = onSupportsVisionChanged,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.supports_vision_label),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = draft.supportsVision, onCheckedChange = null, enabled = !busy)
        }
    }
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    enabled: Boolean,
    isSecret: Boolean = false,
    numeric: Boolean = false,
    decimal: Boolean = false,
    supporting: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(label) },
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
        supportingText = supporting?.let { { Text(it) } },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        visualTransformation = if (isSecret) {
            PasswordVisualTransformation()
        } else {
            androidx.compose.ui.text.input.VisualTransformation.None
        },
        keyboardOptions = if (numeric || decimal) {
            KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number)
        } else {
            KeyboardOptions.Default
        },
    )
}

/** Writing assistance. Collapsed to one short line per switch. */
@Composable
private fun AssistanceSection(enabled: Boolean, onChanged: (Boolean) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.regular),
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
                Text(
                    text = stringResource(R.string.assist_section_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                )
                Surface(shape = PillShape, color = MaterialTheme.colorScheme.onSecondaryContainer) {
                    Text(
                        text = stringResource(
                            if (enabled) R.string.assist_status_on else R.string.assist_status_off,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(horizontal = Spacing.small, vertical = 2.dp),
                    )
                }
            }
            Text(
                text = stringResource(R.string.assist_section_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                    value = enabled,
                    role = Role.Switch,
                    onValueChange = onChanged,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.assist_recap_suggestion_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        text = stringResource(
                            if (enabled) {
                                R.string.assist_recap_suggestion_desc_on
                            } else {
                                R.string.assist_recap_suggestion_desc_off
                            },
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Switch(checked = enabled, onCheckedChange = null)
            }
        }
    }
}

/** One short line for the outcome of the last action. */
@Composable
private fun SettingsStatusBanner(status: SettingsStatus) {
    val success = status.isSuccess()
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (success) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = settingsStatusText(status),
            style = MaterialTheme.typography.bodyMedium,
            color = if (success) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onErrorContainer
            },
            modifier = Modifier.padding(Spacing.medium),
        )
    }
}

@Composable
private fun settingsStatusText(status: SettingsStatus): String =
    if (status == SettingsStatus.CONTEXT_WINDOW_INVALID) {
        stringResource(R.string.context_window_invalid, MIN_CONTEXT_WINDOW, MAX_CONTEXT_WINDOW)
    } else {
        stringResource(status.stringResource())
    }

private fun SettingsStatus.stringResource(): Int = when (this) {
    SettingsStatus.SAVED -> R.string.settings_saved
    SettingsStatus.DELETED -> R.string.provider_deleted
    SettingsStatus.KEY_CLEARED -> R.string.api_key_cleared
    SettingsStatus.CONNECTION_SUCCESS -> R.string.connection_success
    SettingsStatus.BASE_URL_REQUIRED -> R.string.base_url_required
    SettingsStatus.BASE_URL_INVALID -> R.string.base_url_invalid
    SettingsStatus.BASE_URL_INSECURE -> R.string.base_url_insecure
    SettingsStatus.MODEL_ID_REQUIRED -> R.string.model_id_required
    SettingsStatus.CONTEXT_WINDOW_INVALID -> R.string.context_window_invalid
    SettingsStatus.NUMERIC_FIELD_INVALID -> R.string.numeric_field_invalid
    SettingsStatus.UNAUTHORIZED -> R.string.connection_unauthorized
    SettingsStatus.RATE_LIMITED -> R.string.connection_rate_limited
    SettingsStatus.TIMEOUT -> R.string.connection_timeout
    SettingsStatus.TLS_ERROR -> R.string.connection_tls_error
    SettingsStatus.NETWORK_ERROR -> R.string.connection_network_error
    SettingsStatus.SERVER_ERROR -> R.string.connection_server_error
    SettingsStatus.UNEXPECTED_RESPONSE -> R.string.connection_unexpected_response
    SettingsStatus.STORAGE_FAILED -> R.string.api_key_storage_failed
    SettingsStatus.API_KEY_REQUIRED -> R.string.api_key_required
}

private fun SettingsStatus.isSuccess(): Boolean = when (this) {
    SettingsStatus.SAVED,
    SettingsStatus.DELETED,
    SettingsStatus.KEY_CLEARED,
    SettingsStatus.CONNECTION_SUCCESS,
    -> true
    else -> false
}
