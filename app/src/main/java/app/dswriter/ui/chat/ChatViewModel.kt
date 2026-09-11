package app.dswriter.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.dswriter.data.remote.ApiException
import app.dswriter.data.remote.ChatStreamingClient
import app.dswriter.data.remote.GenerationStreamEvent
import app.dswriter.data.remote.RemoteFailure
import app.dswriter.domain.generation.AttachmentUnavailableException
import app.dswriter.domain.model.Provider
import app.dswriter.domain.generation.GenerationOutputStore
import app.dswriter.domain.generation.GenerationRequestSnapshot
import app.dswriter.domain.generation.GenerationRequestSnapshotFactory
import app.dswriter.domain.generation.GenerationTaskManager
import app.dswriter.domain.generation.GenerationTaskRepository
import app.dswriter.domain.generation.GenerationTaskStatus
import app.dswriter.domain.generation.GenerationTaskSubmission
import app.dswriter.domain.generation.RecapPrompts
import app.dswriter.domain.generation.VisionModelRequiredException
import app.dswriter.domain.context.ContextWindowSelector
import app.dswriter.domain.model.AttachmentState
import app.dswriter.domain.model.ConversationMessage
import app.dswriter.domain.model.ConversationMessageStatus
import app.dswriter.domain.model.ConversationRepository
import app.dswriter.domain.model.ConversationRole
import app.dswriter.domain.model.ConversationSummary
import app.dswriter.domain.model.ImageAttachmentPreparer
import app.dswriter.domain.model.PreparedImageAttachment
import app.dswriter.domain.model.ProviderCatalog
import app.dswriter.domain.model.toResolvedModel
import app.dswriter.domain.settings.CONTEXT_PRESSURE_THRESHOLD
import app.dswriter.domain.settings.ProvidersRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ChatError {
    API_KEY_REQUIRED, UNAUTHORIZED, RATE_LIMITED, TIMEOUT, NETWORK, SERVER, BUSY,
    IMAGE_INVALID, VISION_MODEL_REQUIRED, ATTACHMENT_UNAVAILABLE, FAILED,
    RECAP_NOT_ENOUGH_CONTENT, RECAP_FAILED,
    /** No provider is usable yet, so there is nowhere to send a request. */
    NO_PROVIDER,
}

/**
 * A recap the user asked for.
 *
 * It is never stored as an assistant message and never enters a conversation on its own: the
 * text is presented for review and editing, and only becomes conversation content if the user
 * submits it as the opening message of a new conversation.
 */
sealed interface RecapState {
    data object Idle : RecapState
    data object Generating : RecapState
    data class Ready(
        val sourceConversationTitle: String,
        val text: String,
        val condensedMessageCount: Int,
        /** Messages left out of this pass; above zero the user should recap again. */
        val remainingMessageCount: Int,
    ) : RecapState
}

sealed interface PendingImageState {
    data object Preparing : PendingImageState
    data class Ready(val image: PreparedImageAttachment) : PendingImageState
}

data class ChatUiState(
    val conversations: List<ConversationSummary> = emptyList(),
    val selectedConversationId: String? = null,
    val conversation: ConversationSummary? = null,
    val messages: List<ConversationMessage> = emptyList(),
    val searchQuery: String = "",
    val unreadConversationIds: Set<String> = emptySet(),
    val draft: String = "",
    val isGenerating: Boolean = false,
    val generationStatus: GenerationTaskStatus? = null,
    val canLoadOlder: Boolean = false,
    val error: ChatError? = null,
    val requestSnapshot: GenerationRequestSnapshot? = null,
    val pendingImage: PendingImageState? = null,
    val needsVisionModel: Boolean = false,
    val migrationNoticeVisible: Boolean = false,
    /** Every configured provider, so the picker can switch between them. */
    val providers: List<Provider> = emptyList(),
    val activeProvider: Provider? = null,
    val recap: RecapState = RecapState.Idle,
    /**
     * How full the current conversation is, when the optional recap suggestion is switched on
     * and the conversation has passed the pressure threshold. Null means no suggestion.
     */
    val contextPressurePercent: Int? = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val conversations: ConversationRepository,
    private val taskRepository: GenerationTaskRepository,
    private val providers: ProvidersRepository,
    private val outputStore: GenerationOutputStore,
    private val generationTaskManager: GenerationTaskManager,
    private val imagePreparer: ImageAttachmentPreparer,
    private val streamingClient: ChatStreamingClient,
) : ViewModel() {
    private val selectedId = MutableStateFlow<String?>(null)
    private val searchQuery = MutableStateFlow("")
    private val draft = MutableStateFlow("")
    private val error = MutableStateFlow<ChatError?>(null)
    private val olderMessages = MutableStateFlow<List<ConversationMessage>>(emptyList())
    private val canLoadOlder = MutableStateFlow(true)
    private val pendingImage = MutableStateFlow<PendingImageState?>(null)
    private val migrationNotice = MutableStateFlow(false)
    private val submitMutex = Mutex()
    private var draftSaveJob: Job? = null
    private var recapJob: Job? = null

    private val selectedConversation: Flow<ConversationSummary?> = selectedId.flatMapLatest { id ->
        if (id == null) flowOf(null) else conversations.observeConversation(id)
    }
    private val recentMessages: Flow<List<ConversationMessage>> = selectedId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else conversations.observeSelectedBranch(id, DISPLAY_PAGE_SIZE)
    }
    private val displayedMessages = combine(recentMessages, olderMessages) { recent, older ->
        (older + recent).distinctBy(ConversationMessage::id).sortedBy(ConversationMessage::sequence)
    }
    private val visibleConversations = searchQuery.flatMapLatest(conversations::searchConversations)
    private val catalog = providers.catalog
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProviderCatalog())
    private val recapState = MutableStateFlow<RecapState>(RecapState.Idle)
    private val contextPressure = MutableStateFlow<Int?>(null)
    private var dismissedSuggestionFor: String? = null

    init {
        // Recompute the context-pressure suggestion when the conversation or provider changes.
        viewModelScope.launch {
            combine(selectedId, catalog) { id, current -> id to current.active }
                .collect { (id, provider) ->
                    contextPressure.value = computeContextPressure(id, provider)
                }
        }
    }

    /**
     * Reports how full the current conversation is, but only when the user switched the optional
     * recap suggestion on. It never sends anything: it produces a visible prompt, nothing more.
     */
    private suspend fun computeContextPressure(id: String?, provider: Provider?): Int? {
        if (id == null || provider == null || !provider.isConfigured) return null
        if (!providers.suggestRecapWhenContextIsFull.first()) return null
        if (dismissedSuggestionFor == id) return null
        if (conversations.observeConversation(id).first() == null) return null
        val branch = conversations.loadSelectedBranch(id)
        if (branch.size < MIN_MESSAGES_FOR_RECAP) return null
        val fraction = ContextWindowSelector.usageFraction(
            branch,
            provider.toResolvedModel().contextBudget,
        )
        if (fraction < CONTEXT_PRESSURE_THRESHOLD) return null
        return (fraction * 100).toInt().coerceIn(1, 100)
    }

    /** Hides the suggestion for the current conversation until the user switches to another one. */
    fun dismissContextSuggestion() {
        dismissedSuggestionFor = selectedId.value
        contextPressure.value = null
    }

    val uiState: StateFlow<ChatUiState> = combine(
        combine(visibleConversations, selectedId, searchQuery, taskRepository.unreadConversationIds) {
                list, id, query, unread -> DrawerData(list, id, query, unread)
        },
        combine(selectedConversation, displayedMessages, canLoadOlder) { conversation, messages, canLoad ->
            ConversationData(conversation, messages, canLoad)
        },
        combine(generationTaskManager.states, generationTaskManager.latestSnapshots, pendingImage) {
                states, snapshots, image -> ManagerData(states, snapshots, image)
        },
        combine(draft, error, migrationNotice) { currentDraft, currentError, notice ->
            LocalData(currentDraft, currentError, notice)
        },
        combine(catalog, recapState, contextPressure) { current, recap, pressure ->
            ConfiguredData(current, recap, pressure)
        },
    ) { drawer, conversationData, manager, local, configured ->
        val id = drawer.selectedId
        val activeProvider = configured.catalog.active
        val generation = id?.let(manager.states::get)
        val messages = if (generation?.progress == null) conversationData.messages else conversationData.messages.map { message ->
            if (message.id == generation.assistantMessageId) message.copy(
                content = generation.progress.content,
                reasoningContent = generation.progress.reasoningContent,
                reasoningCharacterCount = generation.progress.reasoningCharacterCount,
                reasoningIsPreview = generation.progress.reasoningIsPreview,
                generationStatus = if (generation.progress.isComplete) ConversationMessageStatus.COMPLETE
                    else ConversationMessageStatus.STREAMING,
            ) else message
        }
        val hasImage = manager.image is PendingImageState.Ready || messages.any { message ->
            message.attachments.any { it.preparationState == AttachmentState.READY }
        }
        ChatUiState(
            conversations = drawer.conversations,
            selectedConversationId = id,
            conversation = conversationData.conversation,
            messages = messages,
            searchQuery = drawer.query,
            unreadConversationIds = drawer.unread,
            draft = local.draft,
            isGenerating = generation != null && generation.failure == null,
            generationStatus = generation?.status,
            canLoadOlder = conversationData.canLoad && conversationData.messages.size >= DISPLAY_PAGE_SIZE,
            error = local.error ?: generation?.failure?.toChatError(),
            requestSnapshot = id?.let(manager.snapshots::get),
            pendingImage = manager.image,
            needsVisionModel = (hasImage || local.error == ChatError.VISION_MODEL_REQUIRED) &&
                activeProvider?.supportsVision != true,
            migrationNoticeVisible = local.migrationNotice,
            providers = configured.catalog.providers,
            activeProvider = activeProvider,
            recap = configured.recap,
            contextPressurePercent = configured.contextPressure,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    init {
        viewModelScope.launch {
            migrationNotice.value = conversations.consumeMigrationNotice()
            val initial = conversations.recentConversations.first().firstOrNull()?.id ?: conversations.create()
            selectConversation(initial)
        }
    }

    fun updateSearchQuery(value: String) { searchQuery.value = value }

    fun newConversation() = viewModelScope.launch { selectConversation(conversations.create()) }

    fun selectConversation(id: String) {
        if (id == selectedId.value) return
        val previous = selectedId.value
        draftSaveJob?.cancel()
        previous?.let { generationTaskManager.setConversationVisible(it, false) }
        (pendingImage.value as? PendingImageState.Ready)?.image?.let { image ->
            viewModelScope.launch { imagePreparer.discard(image.localUri) }
        }
        selectedId.value = id
        olderMessages.value = emptyList()
        canLoadOlder.value = true
        pendingImage.value = null
        error.value = null
        viewModelScope.launch {
            val conversation = conversations.observeConversation(id).first() ?: return@launch
            if (selectedId.value != id) return@launch
            draft.value = conversation.draft
            taskRepository.markConversationRead(id)
            generationTaskManager.setConversationVisible(id, true)
        }
    }

    fun renameConversation(id: String, title: String) = viewModelScope.launch {
        runCatching { conversations.rename(id, title) }.onFailure { error.value = ChatError.FAILED }
    }

    fun moveConversationToTrash(id: String) = viewModelScope.launch {
        runCatching { conversations.moveToTrash(id) }.onSuccess {
            if (selectedId.value == id) {
                val next = conversations.recentConversations.first().firstOrNull()?.id ?: conversations.create()
                selectConversation(next)
            }
        }.onFailure { error.value = ChatError.FAILED }
    }

    fun dismissMigrationNotice() { migrationNotice.value = false }

    fun updateDraft(value: String) {
        val id = selectedId.value ?: return
        draft.value = value
        draftSaveJob?.cancel()
        draftSaveJob = viewModelScope.launch { delay(250); conversations.updateDraft(id, value) }
    }

    /**
     * Switches the provider used for subsequent requests.
     *
     * A conversation does not carry its own model choice: the provider is application-wide, so the
     * picker changes it for the whole app rather than per conversation.
     */
    fun selectProvider(providerId: String) {
        viewModelScope.launch {
            runCatching { providers.setActiveProvider(providerId) }
                .onSuccess {
                    if (error.value == ChatError.VISION_MODEL_REQUIRED) error.value = null
                }
                .onFailure { error.value = ChatError.FAILED }
        }
    }

    fun selectImage(sourceUri: String) {
        if (pendingImage.value is PendingImageState.Preparing) return
        viewModelScope.launch {
            val previous = (pendingImage.value as? PendingImageState.Ready)?.image
            pendingImage.value = PendingImageState.Preparing
            previous?.let { imagePreparer.discard(it.localUri) }
            runCatching { imagePreparer.prepare(sourceUri) }
                .onSuccess { pendingImage.value = PendingImageState.Ready(it); error.value = null }
                .onFailure { pendingImage.value = null; error.value = ChatError.IMAGE_INVALID }
        }
    }

    fun removePendingImage() {
        val image = (pendingImage.value as? PendingImageState.Ready)?.image ?: return
        pendingImage.value = null
        viewModelScope.launch { imagePreparer.discard(image.localUri) }
    }

    fun send() = submit(false)
    fun regenerate() = submit(true)
    fun stop() { selectedId.value?.let(generationTaskManager::cancelConversation) }
    fun setVisible(visible: Boolean) { selectedId.value?.let { generationTaskManager.setConversationVisible(it, visible) } }

    fun loadOlder() {
        val id = selectedId.value ?: return
        viewModelScope.launch {
            val before = uiState.value.messages.firstOrNull()?.sequence ?: return@launch
            val page = conversations.loadSelectedBranch(id, before, DISPLAY_PAGE_SIZE)
            olderMessages.value = (page + olderMessages.value).distinctBy(ConversationMessage::id)
                .sortedBy(ConversationMessage::sequence)
            canLoadOlder.value = page.size == DISPLAY_PAGE_SIZE
        }
    }

    fun clearError() { error.value = null }

    /**
     * Asks the model for a recap of the current conversation.
     *
     * This is the only place DS Writer sends something other than a plain conversation turn, and
     * it happens only after an explicit user action. The result is never persisted as a message
     * and never enters a request by itself; it is shown for review and editing first.
     */
    fun generateRecap() {
        val id = selectedId.value ?: return
        if (recapState.value is RecapState.Generating) return
        if (generationTaskManager.states.value[id]?.status in ACTIVE_TASK_STATUSES) {
            error.value = ChatError.BUSY
            return
        }
        recapJob = viewModelScope.launch {
            recapState.value = RecapState.Generating
            error.value = null
            val provider = catalog.value.active
            val conversation = conversations.observeConversation(id).first()
            if (provider == null || !provider.isConfigured) {
                recapState.value = RecapState.Idle
                error.value = ChatError.NO_PROVIDER
                return@launch
            }
            val apiKey = providers.getApiKey(provider.id)
            if (apiKey.isNullOrEmpty()) {
                recapState.value = RecapState.Idle
                error.value = ChatError.API_KEY_REQUIRED
                return@launch
            }
            val branch = conversations.loadSelectedBranch(id)
            if (branch.size < MIN_MESSAGES_FOR_RECAP) {
                recapState.value = RecapState.Idle
                error.value = ChatError.RECAP_NOT_ENOUGH_CONTENT
                return@launch
            }

            var condensedMessageCount = 0
            var remainingMessageCount = 0
            val text = runCatching {
                val request = RecapPrompts.buildRecapRequest(provider, branch)
                condensedMessageCount = request.condensedMessageCount
                remainingMessageCount = request.remainingMessageCount
                collectRecapText(request.snapshot, apiKey)
            }.getOrElse { failure ->
                recapState.value = RecapState.Idle
                error.value = failure.toChatError()
                return@launch
            }

            if (text.isBlank()) {
                recapState.value = RecapState.Idle
                error.value = ChatError.RECAP_FAILED
            } else {
                recapState.value = RecapState.Ready(
                    sourceConversationTitle = conversation?.title.orEmpty(),
                    text = text.trim(),
                    condensedMessageCount = condensedMessageCount,
                    remainingMessageCount = remainingMessageCount,
                )
            }
        }
    }

    private suspend fun collectRecapText(snapshot: GenerationRequestSnapshot, apiKey: String): String {
        val builder = StringBuilder()
        streamingClient.stream(snapshot, apiKey).collect { event ->
            // Reasoning is intentionally discarded: only visible recap text is offered to the user.
            if (event is GenerationStreamEvent.ContentDelta) builder.append(event.text)
        }
        return builder.toString()
    }

    /**
     * Starts a new conversation whose first real message is the recap the user reviewed.
     *
     * The recap becomes ordinary user-authored conversation content, so later requests contain it
     * exactly as the user approved it and no hidden summary is ever involved.
     */
    fun startNewConversationFromRecap(recap: String) {
        val text = recap.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            val newId = conversations.create()
            conversations.appendMessage(newId, null, ConversationRole.USER, text)
            recapState.value = RecapState.Idle
            selectConversation(newId)
        }
    }

    fun dismissRecap() {
        recapJob?.cancel()
        recapJob = null
        recapState.value = RecapState.Idle
    }

    private fun submit(regenerate: Boolean) {
        val id = selectedId.value ?: return
        viewModelScope.launch {
            submitMutex.withLock {
                if (generationTaskManager.states.value[id]?.status in ACTIVE_TASK_STATUSES) {
                    error.value = ChatError.BUSY
                    return@withLock
                }
                val provider = catalog.value.active
                if (provider == null || !provider.isConfigured) {
                    error.value = ChatError.NO_PROVIDER; return@withLock
                }
                val apiKey = providers.getApiKey(provider.id)
                if (apiKey.isNullOrEmpty()) { error.value = ChatError.API_KEY_REQUIRED; return@withLock }
                val selected = conversations.loadSelectedBranch(id)
                val selectedBranchCount = conversations.countSelectedBranch(id)
                val requestMessages = if (regenerate) {
                    val last = selected.lastOrNull()
                    if (last?.role != ConversationRole.ASSISTANT || last.parentMessageId == null) {
                        error.value = ChatError.FAILED; return@withLock
                    }
                    selected.dropLast(1)
                } else {
                    val exactText = draft.value
                    val image = (pendingImage.value as? PendingImageState.Ready)?.image
                    if (pendingImage.value is PendingImageState.Preparing || (exactText.isBlank() && image == null)) return@withLock
                    if (image != null && !provider.supportsVision) {
                        error.value = ChatError.VISION_MODEL_REQUIRED; return@withLock
                    }
                    val userId = conversations.appendMessage(id, selected.lastOrNull()?.id, ConversationRole.USER, exactText)
                    image?.let { conversations.addPreparedImage(userId, it) }
                    pendingImage.value = null
                    draftSaveJob?.cancel()
                    draft.value = ""
                    conversations.updateDraft(id, "")
                    conversations.loadSelectedBranch(id)
                }
                val parentMessageId = requestMessages.lastOrNull()?.id ?: return@withLock
                val snapshot = try {
                    GenerationRequestSnapshotFactory.create(
                        provider = provider,
                        selectedMessages = requestMessages,
                        totalBranchMessageCount = if (regenerate) {
                            selectedBranchCount - 1
                        } else {
                            conversations.countSelectedBranch(id)
                        },
                    )
                } catch (_: VisionModelRequiredException) {
                    error.value = ChatError.VISION_MODEL_REQUIRED; return@withLock
                } catch (_: AttachmentUnavailableException) {
                    error.value = ChatError.ATTACHMENT_UNAVAILABLE; return@withLock
                } catch (_: IllegalArgumentException) {
                    // The provider is unusable, for example a missing model id.
                    error.value = ChatError.NO_PROVIDER; return@withLock
                }
                val assistantId = outputStore.createStreamingAssistantMessage(id, parentMessageId)
                error.value = null
                if (!generationTaskManager.submit(
                        GenerationTaskSubmission(id, parentMessageId, assistantId, snapshot), apiKey,
                    )) error.value = ChatError.BUSY
            }
        }
    }

    private fun Throwable.toChatError(): ChatError = when ((this as? ApiException)?.failure) {
        RemoteFailure.UNAUTHORIZED -> ChatError.UNAUTHORIZED
        RemoteFailure.RATE_LIMITED -> ChatError.RATE_LIMITED
        RemoteFailure.TIMEOUT -> ChatError.TIMEOUT
        RemoteFailure.NETWORK, RemoteFailure.TLS -> ChatError.NETWORK
        RemoteFailure.SERVER -> ChatError.SERVER
        else -> ChatError.FAILED
    }

    private data class DrawerData(
        val conversations: List<ConversationSummary>, val selectedId: String?, val query: String,
        val unread: Set<String>,
    )
    private data class ConversationData(
        val conversation: ConversationSummary?, val messages: List<ConversationMessage>, val canLoad: Boolean,
    )
    private data class ManagerData(
        val states: Map<String, app.dswriter.domain.generation.ConversationGenerationState>,
        val snapshots: Map<String, GenerationRequestSnapshot>, val image: PendingImageState?,
    )
    private data class LocalData(val draft: String, val error: ChatError?, val migrationNotice: Boolean)

    private data class ConfiguredData(
        val catalog: ProviderCatalog,
        val recap: RecapState,
        val contextPressure: Int?,
    )

    private companion object {
        const val DISPLAY_PAGE_SIZE = 50

        /** A recap needs at least one exchange to condense into something useful. */
        const val MIN_MESSAGES_FOR_RECAP = 2
        val ACTIVE_TASK_STATUSES = setOf(GenerationTaskStatus.QUEUED, GenerationTaskStatus.RUNNING)
    }
}
