package dev.ividi.myscanner

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import dev.ividi.myscanner.pdf.PdfImporter
import dev.ividi.myscanner.ui.Routes
import dev.ividi.myscanner.viewmodel.AppViewModel

/**
 * Handles a batch of files picked via "Browse Files": images are gathered into a single new
 * scanned document, each PDF becomes its own document with pages rendered via [PdfImporter],
 * and any other file (Word, etc.) is copied into app storage as a distinct "Imported File"
 * document that is opened externally rather than through the scan-editing pipeline.
 */
internal suspend fun importBrowsedFiles(
    activity: ComponentActivity,
    uris: List<Uri>,
    viewModel: AppViewModel,
    navController: androidx.navigation.NavController
) {
    val imagePaths = mutableListOf<String>()
    var lastPdfOrImageDocId: String? = null

    uris.forEach { uri ->
        val mimeType = activity.contentResolver.getType(uri) ?: ""
        val fileName = queryDisplayName(activity, uri) ?: "file"
        when {
            mimeType.startsWith("image/") -> {
                copyUriToLocalFile(activity, uri, viewModel, null)?.let { imagePaths.add(it) }
            }
            mimeType == "application/pdf" -> {
                val tempId = java.util.UUID.randomUUID().toString()
                val pages = runCatching {
                    PdfImporter.renderPagesToFiles(activity, uri) { viewModel.newPageFile(tempId) }
                }.getOrDefault(emptyList())
                if (pages.isNotEmpty()) {
                    val docName = fileName.substringBeforeLast('.').ifBlank { fileName }
                    lastPdfOrImageDocId = viewModel.createDocumentFromPages(docName, pages)
                }
            }
            else -> {
                val destFile = viewModel.newImportedFile(fileName)
                val copied = runCatching {
                    activity.contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    true
                }.getOrDefault(false)
                if (copied) {
                    viewModel.createImportedFileDocument(fileName, destFile.absolutePath)
                }
            }
        }
    }

    if (imagePaths.isNotEmpty()) {
        lastPdfOrImageDocId = viewModel.createDocumentFromScan(imagePaths)
    }
    lastPdfOrImageDocId?.let { navController.navigate(Routes.viewer(it)) }
}

/** Reads the SAF display name for [uri], falling back to null if it cannot be resolved. */
internal fun queryDisplayName(activity: ComponentActivity, uri: Uri): String? {
    return runCatching {
        activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else {
                null
            }
        }
    }.getOrNull()
}

/** Best-effort MIME type lookup from a file name's extension, used for imported-file sharing/opening. */
internal fun mimeTypeForFileName(fileName: String): String {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
}

/** Opens an imported file (e.g. a Word document) with whatever app the system offers for it. */
internal fun openImportedFile(activity: ComponentActivity, filePath: String, originalFileName: String) {
    val file = java.io.File(filePath)
    val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeTypeForFileName(originalFileName))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { activity.startActivity(intent) }
        .onFailure {
            android.widget.Toast.makeText(
                activity,
                activity.getString(R.string.imported_file_open_error),
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
}

internal suspend fun copyUriToLocalFile(
    activity: ComponentActivity,
    uri: Uri,
    viewModel: AppViewModel,
    targetDocId: String?
): String? {
    val tempId = targetDocId ?: "inbox"
    val destFile = viewModel.newPageFile(tempId)
    return runCatching {
        activity.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }
        destFile.absolutePath
    }.getOrNull()
}

internal fun shareFile(activity: ComponentActivity, file: java.io.File, mimeType: String) {
    val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    activity.startActivity(Intent.createChooser(intent, null))
}

internal fun shareFiles(activity: ComponentActivity, files: List<java.io.File>, mimeType: String) {
    if (files.isEmpty()) return
    val uris = ArrayList(files.map { FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", it) })
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = mimeType
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    activity.startActivity(Intent.createChooser(intent, null))
}

internal fun openUrl(activity: ComponentActivity, url: String) {
    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

internal fun openAppSettings(activity: ComponentActivity) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", activity.packageName, null)
    }
    activity.startActivity(intent)
}
