package dev.ividi.myscanner.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import dev.ividi.myscanner.scanner.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Renders each page of a picked PDF file to a plain bitmap using the platform's
 * [PdfRenderer], mirroring how [PdfExporter] writes pages the other way around. This lets a
 * PDF picked via "Browse Files" become a normal scanned document, so editing, filters, OCR
 * and re-export all reuse the same page pipeline as camera/photo-sourced pages.
 */
object PdfImporter {

    private const val RENDER_SCALE = 2 // renders at ~2x the PDF's default point resolution

    /** Renders every page of the PDF at [uri] to a JPEG file via [newPageFile], returning their paths. */
    suspend fun renderPagesToFiles(
        context: Context,
        uri: Uri,
        newPageFile: () -> File
    ): List<String> = withContext(Dispatchers.IO) {
        val paths = mutableListOf<String>()
        val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: return@withContext paths
        pfd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                for (index in 0 until renderer.pageCount) {
                    renderer.openPage(index).use { page ->
                        val width = (page.width * RENDER_SCALE).coerceAtLeast(1)
                        val height = (page.height * RENDER_SCALE).coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val outFile = newPageFile()
                        ImageProcessor.saveTo(bitmap, outFile)
                        paths.add(outFile.absolutePath)
                    }
                }
            }
        }
        paths
    }
}
