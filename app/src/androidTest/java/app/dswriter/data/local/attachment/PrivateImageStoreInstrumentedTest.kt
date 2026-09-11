package app.dswriter.data.local.attachment

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Debug
import android.os.SystemClock
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.net.URI
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivateImageStoreInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = PrivateImageStore(context)

    @Test
    fun normalizesExifOrientationAndCreatesPrivateDownsampledJpeg() = runTest {
        val source = File(context.cacheDir, "orientation-source.jpg")
        val bitmap = Bitmap.createBitmap(120, 60, Bitmap.Config.ARGB_8888)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        ExifInterface(source).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }

        val prepared = store.prepare(Uri.fromFile(source).toString())
        val output = File(URI(prepared.localUri))
        val decoded = BitmapFactory.decodeFile(output.path)

        assertEquals("image/jpeg", prepared.mimeType)
        assertEquals(60, prepared.width)
        assertEquals(120, prepared.height)
        assertEquals(60, decoded.width)
        assertEquals(120, decoded.height)
        assertTrue(output.canonicalPath.startsWith(File(context.filesDir, "attachments").canonicalPath))
        decoded.recycle()
        store.discard(prepared.localUri)
        source.delete()
    }

    @Test
    fun rejectsNonImageContentEvenWhenSelectedAsAUri() = runTest {
        val source = File(context.cacheDir, "not-an-image.jpg").apply { writeText("not an image") }

        assertTrue(runCatching { store.prepare(Uri.fromFile(source).toString()) }.isFailure)
        source.delete()
    }

    @Test
    fun largeImagePreparationStaysWithinDimensionTimeAndRetainedMemoryBudgets() = runTest {
        val source = File(context.cacheDir, "large-performance-source.jpg")
        val bitmap = Bitmap.createBitmap(3200, 1800, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bitmap.recycle()
        Runtime.getRuntime().gc()
        val beforePssKb = totalPssKb()
        val start = SystemClock.elapsedRealtimeNanos()

        val prepared = store.prepare(Uri.fromFile(source).toString())

        val elapsedMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000
        Runtime.getRuntime().gc()
        val retainedDeltaKb = (totalPssKb() - beforePssKb).coerceAtLeast(0)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(File(URI(prepared.localUri)).path, bounds)
        println(
            "PERF_IMAGE source=3200x1800 output=${bounds.outWidth}x${bounds.outHeight} " +
                "elapsedMs=$elapsedMs retainedPssDeltaKb=$retainedDeltaKb",
        )

        assertTrue(maxOf(bounds.outWidth, bounds.outHeight) <= PrivateImageStore.MAX_DIMENSION)
        assertTrue("Image preparation exceeded budget: ${elapsedMs}ms", elapsedMs <= 5_000)
        assertTrue("Retained PSS exceeded budget: ${retainedDeltaKb}KiB", retainedDeltaKb <= 96 * 1024)
        store.discard(prepared.localUri)
        source.delete()
    }

    private fun totalPssKb(): Int = Debug.MemoryInfo().also(Debug::getMemoryInfo).totalPss
}
