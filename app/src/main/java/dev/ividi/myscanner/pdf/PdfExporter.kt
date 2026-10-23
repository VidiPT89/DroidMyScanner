package dev.ividi.myscanner.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import dev.ividi.myscanner.data.ScanDocument
import dev.ividi.myscanner.scanner.ImageProcessor
import dev.ividi.myscanner.scanner.RecognizedLine
import dev.ividi.myscanner.scanner.TextRecognizerClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * Renders a [ScanDocument] to a multi-page, searchable PDF using the platform's built-in
 * [PdfDocument], applying each page's current filter/crop/rotation as it draws.
 *
 * Pages are laid out at A4 width (595pt) with the scan's own aspect ratio; the bitmap
 * keeps its full resolution inside the PDF, it is only placed at a sensible physical size
 * (a 12MP scan used to become a ~1m tall page). OCR text from ML Kit is written beneath
 * the opaque page image: invisible on screen, but selectable and found by search.
 */
object PdfExporter {

    internal const val PAGE_WIDTH_POINTS = 595f

    suspend fun export(context: Context, document: ScanDocument): File = withContext(Dispatchers.IO) {
        require(document.pages.isNotEmpty()) { "Document has no pages" }
        val pdf = PdfDocument()
        val textRecognizer = TextRecognizerClient()
        try {
            document.pages.forEachIndexed { index, page ->
                val bitmap = ImageProcessor.loadBitmap(page.editedPath)
                    ?.let { ImageProcessor.applyFilter(ImageProcessor.rotate(it, page.rotationDegrees), page.filter) }
                    ?: error("Unable to read page ${index + 1}")
                // A failed recognition must not block the export; the page is just not searchable.
                val lines = runCatching { textRecognizer.recognizeLines(bitmap) }.getOrDefault(emptyList())
                val scale = PAGE_WIDTH_POINTS / bitmap.width
                val pageHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH_POINTS.roundToInt(), pageHeight, index + 1).create()
                val pdfPage = pdf.startPage(pageInfo)
                drawTextLayer(pdfPage.canvas, lines, scale)
                pdfPage.canvas.drawBitmap(
                    bitmap, null, RectF(0f, 0f, PAGE_WIDTH_POINTS, pageHeight.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG)
                )
                pdf.finishPage(pdfPage)
                bitmap.recycle()
            }

            val outFile = File(ExportFiles.newDirectory(context), "${ExportFiles.safeName(document.name)}.pdf")
            FileOutputStream(outFile).use { pdf.writeTo(it) }
            outFile
        } finally {
            textRecognizer.close()
            pdf.close()
        }
    }

    /** Draws each line stretched to its recognized box, in page points. */
    private fun drawTextLayer(canvas: Canvas, lines: List<RecognizedLine>, scale: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        lines.forEach { line ->
            val box = RectF(line.bounds).apply {
                left *= scale; top *= scale; right *= scale; bottom *= scale
            }
            if (line.text.isBlank() || box.width() <= 0f || box.height() <= 0f) return@forEach
            paint.textScaleX = 1f
            paint.textSize = box.height() * 0.85f
            val measured = paint.measureText(line.text)
            if (measured > 0f) paint.textScaleX = box.width() / measured
            canvas.drawText(line.text, box.left, box.bottom - paint.descent(), paint)
        }
    }
}
