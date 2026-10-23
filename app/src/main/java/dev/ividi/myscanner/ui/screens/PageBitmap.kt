package dev.ividi.myscanner.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.ividi.myscanner.data.PageFilter
import dev.ividi.myscanner.scanner.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decodes a downsampled page preview off the main thread. Decoding and filtering even a
 * 600px JPEG per thumbnail inside composition stutters scrolling and typing, so screens
 * get null first and recompose once the bitmap is ready. The previous bitmap stays on
 * screen while a new filter/rotation renders, so edits never flash an empty frame.
 */
@Composable
fun rememberPageBitmap(
    path: String,
    rotationDegrees: Int = 0,
    filter: PageFilter = PageFilter.ORIGINAL,
    maxDimension: Int = 1200
): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(null, path, rotationDegrees, filter, maxDimension) {
        value = withContext(Dispatchers.Default) {
            ImageProcessor.loadDownsampledBitmap(path, maxDimension)?.let {
                ImageProcessor.applyFilter(ImageProcessor.rotate(it, rotationDegrees), filter).asImageBitmap()
            }
        }
    }
    return bitmap
}
