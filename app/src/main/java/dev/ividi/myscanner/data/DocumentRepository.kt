package dev.ividi.myscanner.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Simple file-backed store for scanned documents. Page bitmaps live under
 * filesDir/scans/<docId>/ and metadata is kept in a single JSON index file so the whole
 * catalogue can be loaded quickly on startup without a database dependency.
 */
class DocumentRepository(private val context: Context) {

    private val scansDir: File by lazy {
        File(context.filesDir, "scans").apply { mkdirs() }
    }
    private val indexFile: File by lazy {
        File(context.filesDir, "documents_index.json")
    }

    private val _documents = MutableStateFlow<List<ScanDocument>>(emptyList())
    val documents: StateFlow<List<ScanDocument>> = _documents.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        if (!indexFile.exists()) {
            _documents.value = emptyList()
            return@withContext
        }
        val docs = runCatching {
            val json = JSONArray(indexFile.readText())
            (0 until json.length()).map { i -> json.getJSONObject(i).toDocument() }
        }.getOrDefault(emptyList())
        _documents.value = docs.sortedByDescending { it.createdAtMillis }
    }

    fun directoryFor(documentId: String): File =
        File(scansDir, documentId).apply { mkdirs() }

    fun newPageFile(documentId: String, extension: String = "jpg"): File =
        File(directoryFor(documentId), "${UUID.randomUUID()}.$extension")

    suspend fun upsertDocument(document: ScanDocument) = withContext(Dispatchers.IO) {
        val current = _documents.value.toMutableList()
        val idx = current.indexOfFirst { it.id == document.id }
        if (idx >= 0) current[idx] = document else current.add(0, document)
        _documents.value = current.sortedByDescending { it.createdAtMillis }
        persist()
    }

    suspend fun deleteDocument(documentId: String) = withContext(Dispatchers.IO) {
        _documents.value = _documents.value.filterNot { it.id == documentId }
        directoryFor(documentId).deleteRecursively()
        persist()
    }

    suspend fun renameDocument(documentId: String, newName: String) = withContext(Dispatchers.IO) {
        val doc = _documents.value.find { it.id == documentId } ?: return@withContext
        upsertDocument(doc.copy(name = newName))
    }

    fun getDocument(documentId: String): ScanDocument? =
        _documents.value.find { it.id == documentId }

    private suspend fun persist() = withContext(Dispatchers.IO) {
        val array = JSONArray()
        _documents.value.forEach { array.put(it.toJson()) }
        indexFile.writeText(array.toString())
    }

    private fun ScanDocument.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("createdAtMillis", createdAtMillis)
        val pagesArray = JSONArray()
        pages.forEach { pagesArray.put(it.toJson()) }
        put("pages", pagesArray)
    }

    private fun ScanPage.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("originalPath", originalPath)
        put("editedPath", editedPath)
        put("rotationDegrees", rotationDegrees)
        put("filter", filter.name)
        put("cropLeft", cropLeft)
        put("cropTop", cropTop)
        put("cropRight", cropRight)
        put("cropBottom", cropBottom)
    }

    private fun JSONObject.toDocument(): ScanDocument {
        val pagesJson = getJSONArray("pages")
        val pages = (0 until pagesJson.length()).map { i -> pagesJson.getJSONObject(i).toPage() }
        return ScanDocument(
            id = getString("id"),
            name = getString("name"),
            createdAtMillis = getLong("createdAtMillis"),
            pages = pages
        )
    }

    private fun JSONObject.toPage(): ScanPage = ScanPage(
        id = getString("id"),
        originalPath = getString("originalPath"),
        editedPath = getString("editedPath"),
        rotationDegrees = optInt("rotationDegrees", 0),
        filter = runCatching { PageFilter.valueOf(getString("filter")) }.getOrDefault(PageFilter.ORIGINAL),
        cropLeft = optDouble("cropLeft", 0.0).toFloat(),
        cropTop = optDouble("cropTop", 0.0).toFloat(),
        cropRight = optDouble("cropRight", 1.0).toFloat(),
        cropBottom = optDouble("cropBottom", 1.0).toFloat()
    )
}
