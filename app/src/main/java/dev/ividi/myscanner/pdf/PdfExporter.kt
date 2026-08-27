package dev.ividi.myscanner.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import dev.ividi.myscanner.data.PageFilter
import dev.ividi.myscanner.data.ScanDocument
import dev.ividi.myscanner.scanner.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Renders a [ScanDocument] to a multi-page PDF using the platform's built-in
 * [PdfDocument], applying each page's current filter/crop/rotation as it draws.
 */
object PdfExporter {

    suspend fun export(context: Context, document: ScanDocument): File = withContext(Dispatchers.IO) {
        val pdf = PdfDocument()
        try {
            document.pages.forEach { page ->
                val bitmap = renderPageBitmap(page.editedPath, page.rotationDegrees, page.filter)
                    ?: return@forEach
                val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, document.pages.indexOf(page) + 1).create()
                val pdfPage = pdf.startPage(pageInfo)
                pdfPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                pdf.finishPage(pdfPage)
            }

            val exportsDir = File(context.filesDir, "exports").apply { mkdirs() }
            val outFile = File(exportsDir, "${sanitize(document.name)}.pdf")
            FileOutputStream(outFile).use { pdf.writeTo(it) }
            outFile
        } finally {
            pdf.close()
        }
    }

    private fun renderPageBitmap(path: String, rotation: Int, filter: PageFilter): Bitmap? {
        val bitmap = ImageProcessor.loadBitmap(path) ?: return null
        val rotated = ImageProcessor.rotate(bitmap, rotation)
        return ImageProcessor.applyFilter(rotated, filter)
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9-_ ]"), "_").ifBlank { "document" }
}
