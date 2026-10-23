package dev.ividi.myscanner.scanner

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One recognized line and where it sits on the bitmap, in bitmap pixels. */
data class RecognizedLine(val text: String, val bounds: Rect)

/**
 * Extracts text from a page bitmap on-device using ML Kit's Latin text recognizer.
 */
class TextRecognizerClient {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun close() { recognizer.close() }

    suspend fun recognize(bitmap: Bitmap): String = process(bitmap).text

    /** Line-level results with positions, used to build the searchable PDF text layer. */
    suspend fun recognizeLines(bitmap: Bitmap): List<RecognizedLine> =
        process(bitmap).textBlocks.flatMap { block ->
            block.lines.mapNotNull { line -> line.boundingBox?.let { RecognizedLine(line.text, it) } }
        }

    private suspend fun process(bitmap: Bitmap): Text = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { result -> continuation.resume(result) }
            .addOnFailureListener { error -> continuation.resumeWithException(error) }
    }
}
