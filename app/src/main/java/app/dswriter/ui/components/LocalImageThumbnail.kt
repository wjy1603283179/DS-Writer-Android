package app.dswriter.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.dswriter.R
import app.dswriter.data.local.attachment.PrivateImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Thumbnail for an explicitly selected private image.
 *
 * Decoding uses bounds-first sampling so a large source image never inflates fully in memory.
 */
@Composable
fun LocalImageThumbnail(
    localUri: String,
    displayName: String,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState<ImageBitmap?>(null, localUri) {
        value = withContext(Dispatchers.IO) {
            runCatching { java.net.URI(localUri).path }.getOrNull()?.let { path ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    null
                } else {
                    val sample = PrivateImageStore.calculateInSampleSize(
                        bounds.outWidth,
                        bounds.outHeight,
                        256,
                    )
                    BitmapFactory.decodeFile(
                        path,
                        BitmapFactory.Options().apply { inSampleSize = sample },
                    )?.asImageBitmap()
                }
            }
        }
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = stringResource(R.string.image_preview_named, displayName),
            modifier = modifier.size(72.dp),
            contentScale = ContentScale.Crop,
        )
    }
}
