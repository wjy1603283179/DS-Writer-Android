package app.dswriter.ui.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.dswriter.domain.export.DocumentTransfer
import app.dswriter.domain.export.ReadableExportService
import app.dswriter.domain.model.ActiveGenerationDeletionException
import app.dswriter.domain.model.ConversationRepository
import app.dswriter.domain.model.TrashedConversation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DataOperation { MARKDOWN_EXPORT, TEXT_EXPORT }

sealed interface DataNotice {
    data class Exported(val operation: DataOperation) : DataNotice
    data object Failed : DataNotice
    data object DeletionBlocked : DataNotice
}

data class DataSafetyUiState(
    val trashedConversations: List<TrashedConversation> = emptyList(),
    val busy: Boolean = false,
    val notice: DataNotice? = null,
)

@HiltViewModel
class DataSafetyViewModel @Inject constructor(
    private val exports: ReadableExportService,
    private val documents: DocumentTransfer,
    private val conversations: ConversationRepository,
) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val notice = MutableStateFlow<DataNotice?>(null)
    val uiState: StateFlow<DataSafetyUiState> = combine(
        conversations.trashedConversations, busy, notice,
    ) { trashed, isBusy, currentNotice -> DataSafetyUiState(trashed, isBusy, currentNotice) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DataSafetyUiState())

    fun export(uri: String, operation: DataOperation) = runOperation {
        val content = when (operation) {
            DataOperation.MARKDOWN_EXPORT -> exports.createMarkdownExport()
            DataOperation.TEXT_EXPORT -> exports.createTextExport()
        }
        documents.writeUtf8(uri, content)
        notice.value = DataNotice.Exported(operation)
    }

    fun restoreConversation(id: String) = runOperation { conversations.restore(id) }
    fun deleteConversationPermanently(id: String) = runOperation { conversations.deletePermanently(id) }
    fun clearNotice() { notice.value = null }

    private fun runOperation(action: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            runCatching { action() }.onFailure { failure ->
                notice.value = if (failure is ActiveGenerationDeletionException) DataNotice.DeletionBlocked else DataNotice.Failed
            }
            busy.value = false
        }
    }
}
