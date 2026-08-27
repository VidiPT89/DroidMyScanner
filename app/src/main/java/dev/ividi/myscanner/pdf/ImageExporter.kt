package dev.ividi.myscanner.pdf

import android.content.Context
import dev.ividi.myscanner.data.ScanDocument
import dev.ividi.myscanner.scanner.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Exports every page of a document as a standalone JPEG, applying its current
 * crop/rotation/filter, for sharing outside of the PDF flow.
 */
object ImageExporter {

    suspend fun export(context: Context, document: ScanDocument): List<File> = withContext(Dispatchers.IO) {
        val exportsDir = File(context.filesDir, "exports/${document.id}").apply { mkdirs() }
        document.pages.mapIndexedNotNull { index, page ->
            val bitmap = ImageProcessor.loadBitmap(page.editedPath) ?: return@mapIndexedNotNull null
            val rotated = ImageProcessor.rotate(bitmap, page.rotationDegrees)
            val filtered = ImageProcessor.applyFilter(rotated, page.filter)
            val outFile = File(exportsDir, "page_${index + 1}.jpg")
            ImageProcessor.saveTo(filtered, outFile)
            outFile
        }
    }
}
