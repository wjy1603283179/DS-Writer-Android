package app.dswriter.data.local.attachment

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import app.dswriter.domain.generation.RequestAttachmentSnapshot
import app.dswriter.domain.model.ImageAttachmentPreparer
import app.dswriter.domain.model.PreparedImageAttachment
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImagePreparationException(message: String) : Exception(message)

interface ImageDataUrlEncoder {
    fun encode(attachment: RequestAttachmentSnapshot): String
}

@Singleton
class PrivateImageStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ImageAttachmentPreparer, ImageDataUrlEncoder {
    private val attachmentDirectory: File
        get() = File(context.filesDir, DIRECTORY_NAME).apply { mkdirs() }

    override suspend fun prepare(sourceUri: String): PreparedImageAttachment = withContext(Dispatchers.IO) {
        val uri = Uri.parse(sourceUri)
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = resolver.openInputStream(uri)
            ?: throw ImagePreparationException("Cannot open selected image")
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outMimeType !in INPUT_MIME_TYPES) {
            throw ImagePreparationException("Unsupported image content")
        }
        val orientation = resolver.openInputStream(uri)?.use { stream ->
            runCatching {
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw ImagePreparationException("Cannot decode selected image")
        var normalized: Bitmap? = null
        var scaled: Bitmap? = null
        val destination = File(attachmentDirectory, "${UUID.randomUUID()}.jpg")
        try {
            normalized = applyOrientation(decoded, orientation)
            val oriented = normalized ?: decoded
            val scale = minOf(1f, MAX_DIMENSION.toFloat() / max(oriented.width, oriented.height))
            scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    oriented,
                    (oriented.width * scale).toInt().coerceAtLeast(1),
                    (oriented.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
            } else {
                oriented
            }
            destination.outputStream().buffered().use { output ->
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                    throw ImagePreparationException("Cannot encode selected image")
                }
            }
            if (destination.length() !in 1..MAX_PREPARED_BYTES) {
                throw ImagePreparationException("Prepared image is too large")
            }
            PreparedImageAttachment(
                localUri = destination.toURI().toString(),
                mimeType = OUTPUT_MIME_TYPE,
                displayName = queryDisplayName(uri) ?: "图片.jpg",
                width = scaled.width,
                height = scaled.height,
                sizeBytes = destination.length(),
            )
        } catch (exception: Exception) {
            destination.delete()
            throw exception
        } finally {
            if (scaled !== normalized && scaled !== decoded) scaled?.recycle()
            if (normalized !== decoded) normalized?.recycle()
            decoded.recycle()
        }
    }

    override suspend fun discard(localUri: String) = withContext(Dispatchers.IO) {
        resolvePrivateFile(localUri).delete()
        Unit
    }

    override suspend fun discardAll() = withContext(Dispatchers.IO) {
        val root = attachmentDirectory.canonicalFile
        root.listFiles().orEmpty().filter { it.parentFile?.canonicalFile == root && it.isFile }
            .forEach(File::delete)
    }

    override fun encode(attachment: RequestAttachmentSnapshot): String {
        require(attachment.mimeType == OUTPUT_MIME_TYPE)
        val file = resolvePrivateFile(attachment.localUri)
        require(file.length() == attachment.sizeBytes && file.length() in 1..MAX_PREPARED_BYTES)
        return "data:${attachment.mimeType};base64,${Base64.getEncoder().encodeToString(file.readBytes())}"
    }

    private fun resolvePrivateFile(localUri: String): File {
        val uri = Uri.parse(localUri)
        require(uri.scheme == "file")
        val root = attachmentDirectory.canonicalFile
        val file = File(requireNotNull(uri.path)).canonicalFile
        require(file.parentFile == root && file.isFile) { "Attachment is outside private storage" }
        return file
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    companion object {
        private const val DIRECTORY_NAME = "attachments"
        const val MAX_DIMENSION = 2048
        const val MAX_PREPARED_BYTES = 24L * 1024 * 1024
        private const val JPEG_QUALITY = 88
        private const val OUTPUT_MIME_TYPE = "image/jpeg"
        private val INPUT_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")

        fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
            require(width > 0 && height > 0 && maxDimension > 0)
            var sample = 1
            while (max(width / (sample * 2), height / (sample * 2)) >= maxDimension) sample *= 2
            return sample
        }

        private fun applyOrientation(source: Bitmap, orientation: Int): Bitmap? {
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
                else -> return null
            }
            return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        }
    }
}
