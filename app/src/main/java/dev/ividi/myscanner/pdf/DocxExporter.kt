package dev.ividi.myscanner.pdf

import android.content.Context
import dev.ividi.myscanner.data.ScanDocument
import dev.ividi.myscanner.scanner.ImageProcessor
import dev.ividi.myscanner.scanner.TextRecognizerClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds a minimal but valid Word (.docx) file from a document's OCR'd text, one page of
 * text per scanned page, in order. This is a text-only export: embedding the page images
 * themselves would need a real OOXML/image-relationship writer, which is a reasonable
 * future improvement but out of scope for a dependency-free implementation. A .docx is
 * just a zip of a handful of XML parts, so [ZipOutputStream] from the Android SDK is
 * enough -- no external library is required.
 */
object DocxExporter {

    private val textRecognizer by lazy { TextRecognizerClient() }

    suspend fun export(context: Context, document: ScanDocument): File = withContext(Dispatchers.IO) {
        val pageTexts = document.pages.mapIndexed { index, page ->
            val bitmap = ImageProcessor.loadBitmap(page.editedPath)?.let { bmp ->
                val rotated = ImageProcessor.rotate(bmp, page.rotationDegrees)
                ImageProcessor.applyFilter(rotated, page.filter)
            }
            val text = if (bitmap != null) {
                runCatching { textRecognizer.recognize(bitmap) }.getOrDefault("")
            } else {
                ""
            }
            index to text
        }

        val exportsDir = File(context.filesDir, "exports").apply { mkdirs() }
        val outFile = File(exportsDir, "${sanitize(document.name)}.docx")
        ZipOutputStream(FileOutputStream(outFile)).use { zip ->
            writeEntry(zip, "[Content_Types].xml", contentTypesXml())
            writeEntry(zip, "_rels/.rels", relsXml())
            writeEntry(zip, "word/document.xml", documentXml(pageTexts))
        }
        outFile
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun contentTypesXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
        <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
        <Default Extension="xml" ContentType="application/xml"/>
        <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
        </Types>
    """.trimIndent()

    private fun relsXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
        <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
        </Relationships>
    """.trimIndent()

    private fun documentXml(pageTexts: List<Pair<Int, String>>): String {
        val body = StringBuilder()
        pageTexts.forEach { (index, text) ->
            val breakBefore = if (index > 0) "<w:pPr><w:pageBreakBefore/></w:pPr>" else ""
            val lines = text.split("\n").ifEmpty { listOf("") }
            body.append("<w:p>").append(breakBefore)
            lines.forEachIndexed { lineIndex, line ->
                if (lineIndex > 0) body.append("<w:r><w:br/></w:r>")
                body.append("<w:r><w:t xml:space=\"preserve\">").append(escapeXml(line)).append("</w:t></w:r>")
            }
            body.append("</w:p>")
        }
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
            <w:body>$body<w:sectPr/></w:body>
            </w:document>
        """.trimIndent()
    }

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9-_ ]"), "_").ifBlank { "document" }
}
