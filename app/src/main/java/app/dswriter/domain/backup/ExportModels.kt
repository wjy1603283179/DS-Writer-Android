package app.dswriter.domain.export

data class ExportDocument(
    val conversations: List<ExportConversation>,
    val messages: List<ExportMessage>,
)

data class ExportConversation(
    val id: String,
    val title: String,
    val createdAt: Long,
)

data class ExportMessage(
    val conversationId: String,
    val role: String,
    val content: String,
    val sequence: Long,
)

interface ReadableExportService {
    suspend fun createMarkdownExport(): String
    suspend fun createTextExport(): String
}

interface DocumentTransfer {
    suspend fun writeUtf8(uri: String, content: String)
}
