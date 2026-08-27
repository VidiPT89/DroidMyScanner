package dev.ividi.myscanner.scanner

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/**
 * Thin wrapper around ML Kit's Document Scanner API. The scanner runs as a Google Play
 * services activity that handles capture, edge detection and multi-page flow on its own;
 * this class only wires the launcher and exposes the resulting page image URIs.
 */
class DocumentScannerClient(private val activity: Activity) {

    private val options = GmsDocumentScannerOptions.Builder()
        .setGalleryImportAllowed(true)
        .setPageLimit(50)
        .setResultFormats(
            GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
            GmsDocumentScannerOptions.RESULT_FORMAT_PDF
        )
        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
        .build()

    private val scanner = GmsDocumentScanning.getClient(options)

    fun start(launcher: ActivityResultLauncher<IntentSenderRequest>, onError: (Exception) -> Unit) {
        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                launcher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener(onError)
    }

    companion object {
        fun pageUris(result: GmsDocumentScanningResult): List<String> =
            result.pages?.map { it.imageUri.toString() } ?: emptyList()
    }
}
