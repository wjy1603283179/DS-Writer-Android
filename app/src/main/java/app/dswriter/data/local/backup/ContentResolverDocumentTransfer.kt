package app.dswriter.data.local.export

import android.content.Context
import android.net.Uri
import app.dswriter.domain.export.DocumentTransfer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class ContentResolverDocumentTransfer @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : DocumentTransfer {
    override suspend fun writeUtf8(uri: String, content: String) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(Uri.parse(uri), "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
            it.write(content)
        } ?: error("Cannot open output document")
    }
}
