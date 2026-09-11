package app.dswriter.data.repository

import androidx.room.withTransaction
import app.dswriter.data.local.db.DSWriterDatabase
import app.dswriter.data.local.db.ExportDao
import app.dswriter.domain.export.ExportConversation
import app.dswriter.domain.export.ExportDocument
import app.dswriter.domain.export.ExportMessage
import app.dswriter.domain.export.ReadableExportFormatter
import app.dswriter.domain.export.ReadableExportService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomReadableExportService @Inject constructor(
    private val database: DSWriterDatabase,
    private val exportDao: ExportDao,
) : ReadableExportService {
    override suspend fun createMarkdownExport(): String = ReadableExportFormatter.markdown(loadDocument())

    override suspend fun createTextExport(): String = ReadableExportFormatter.text(loadDocument())

    private suspend fun loadDocument(): ExportDocument = database.withTransaction {
        ExportDocument(
            conversations = exportDao.loadConversations().map {
                ExportConversation(it.id, it.title, it.createdAt)
            },
            messages = exportDao.loadMessages().map {
                ExportMessage(it.conversationId, it.role.name, it.content, it.sequence)
            },
        )
    }
}
