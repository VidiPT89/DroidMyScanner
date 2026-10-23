package dev.ividi.myscanner.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val importsDir: File by lazy {
        File(context.filesDir, "imports").apply { mkdirs() }
    }
    private val indexFile: File by lazy {
        File(context.filesDir, "documents_index.json")
    }
    private val foldersFile: File by lazy {
        File(context.filesDir, "folders_index.json")
    }

    private val _documents = MutableStateFlow<List<ScanDocument>>(emptyList())
    val documents: StateFlow<List<ScanDocument>> = _documents.asStateFlow()

    private val _folders = MutableStateFlow<List<DocumentFolder>>(emptyList())
    val folders: StateFlow<List<DocumentFolder>> = _folders.asStateFlow()

    private val mutex = Mutex()
    private var loaded = false

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock { loadLocked() }
    }

    private fun loadLocked() {
        if (loaded) return
        // Do not turn a corrupt index into an empty library that the next write destroys.
        fun read(file: File): JSONArray = try {
            JSONArray(AtomicFile(file).openRead().bufferedReader().use { it.readText() })
        } catch (error: java.io.FileNotFoundException) {
            if (file.exists() || File(file.path + ".bak").exists()) throw error
            JSONArray()
        }
        val docsJson = read(indexFile)
        val docs = (0 until docsJson.length()).map { docsJson.getJSONObject(it).toDocument() }
        val foldersJson = read(foldersFile)
        val folders = (0 until foldersJson.length()).map {
            val obj = foldersJson.getJSONObject(it)
            DocumentFolder(id = obj.getString("id"), name = obj.getString("name"))
        }
        _documents.value = docs.sortedByDescending { it.createdAtMillis }
        _folders.value = folders
        loaded = true
    }

    fun newPageFile(documentId: String, extension: String = "jpg"): File =
        File(File(scansDir, documentId).apply { mkdirs() }, "${UUID.randomUUID()}.$extension")

    fun newImportedFile(fileName: String): File =
        File(importsDir, "${UUID.randomUUID()}_${File(fileName).name}")

    suspend fun upsertDocument(document: ScanDocument) = mutateDocuments { current ->
        (current.filterNot { it.id == document.id } + document).sortedByDescending { it.createdAtMillis }
    }

    suspend fun updateDocument(documentId: String, transform: (ScanDocument) -> ScanDocument) =
        mutateDocuments { docs -> docs.map { if (it.id == documentId) transform(it) else it } }

    suspend fun deleteDocument(documentId: String) = mutateDocuments { docs ->
        docs.filterNot { it.id == documentId }
    }

    suspend fun renameDocument(documentId: String, newName: String) {
        val name = newName.trim()
        if (name.isNotEmpty()) updateDocument(documentId) { it.copy(name = name) }
    }

    fun getDocument(documentId: String): ScanDocument? =
        _documents.value.find { it.id == documentId }

    private suspend fun mutateDocuments(transform: (List<ScanDocument>) -> List<ScanDocument>) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                loadLocked()
                val old = _documents.value
                val updated = transform(old)
                persist(updated)
                _documents.value = updated
                // Only remove files after the new index is safely committed. Paths may live
                // in the shared inbox or an import directory, not under the document ID.
                val retained = updated.flatMap { it.filePaths() }.toSet()
                old.flatMap { it.filePaths() }.toSet().minus(retained).forEach { path ->
                    val file = File(path).canonicalFile
                    val root = context.filesDir.canonicalFile
                    if (file.path.startsWith(root.path + File.separator)) file.delete()
                }
            }
        }

    private fun ScanDocument.filePaths(): List<String> =
        pages.flatMap { listOf(it.originalPath, it.editedPath) } + listOfNotNull(importedFilePath)

    suspend fun addFolder(name: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadLocked()
            val updated = _folders.value + DocumentFolder(id = UUID.randomUUID().toString(), name = name)
            persistFolders(updated)
            _folders.value = updated
        }
    }

    suspend fun renameFolder(folderId: String, newName: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadLocked()
            val updated = _folders.value.map { if (it.id == folderId) it.copy(name = newName) else it }
            persistFolders(updated)
            _folders.value = updated
        }
    }

    suspend fun deleteFolder(folderId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadLocked()
            val docs = _documents.value.map { if (it.folderId == folderId) it.copy(folderId = null) else it }
            val folders = _folders.value.filterNot { it.id == folderId }
            persist(docs)
            _documents.value = docs
            persistFolders(folders)
            _folders.value = folders
        }
    }

    private fun writeAtomically(file: File, content: String) {
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write(content.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (error: Throwable) {
            atomic.failWrite(stream)
            throw error
        }
    }

    private fun persist(documents: List<ScanDocument>) {
        val array = JSONArray()
        documents.forEach { array.put(it.toJson()) }
        writeAtomically(indexFile, array.toString())
    }

    private fun persistFolders(folders: List<DocumentFolder>) {
        val array = JSONArray()
        folders.forEach { folder ->
            array.put(JSONObject().apply {
                put("id", folder.id)
                put("name", folder.name)
            })
        }
        writeAtomically(foldersFile, array.toString())
    }

    private fun portablePath(path: String): String {
        val prefix = context.filesDir.absolutePath + File.separator
        return if (path.startsWith(prefix)) path.removePrefix(prefix) else path
    }

    private fun resolvePath(path: String): String {
        if (!File(path).isAbsolute) return File(context.filesDir, path).absolutePath
        // Legacy backups stored the old installation's absolute app-data directory.
        if (!File(path).exists() && path.contains("/files/")) {
            val relative = path.substringAfterLast("/files/")
            if (relative.startsWith("scans/") || relative.startsWith("imports/")) {
                return File(context.filesDir, relative).absolutePath
            }
        }
        return path
    }

    private fun ScanDocument.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("createdAtMillis", createdAtMillis)
        val pagesArray = JSONArray()
        pages.forEach { pagesArray.put(it.toJson()) }
        put("pages", pagesArray)
        put("folderId", folderId ?: JSONObject.NULL)
        val tagsArray = JSONArray()
        tags.forEach { tagsArray.put(it) }
        put("tags", tagsArray)
        when (val kind = documentKind) {
            is DocumentKind.Scanned -> put("documentKind", "scanned")
            is DocumentKind.ImportedFile -> {
                put("documentKind", "imported_file")
                put("importedFileName", kind.originalFileName)
            }
        }
        put("importedFilePath", importedFilePath?.let(::portablePath) ?: JSONObject.NULL)
    }

    private fun ScanPage.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("originalPath", portablePath(originalPath))
        put("editedPath", portablePath(editedPath))
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
        val tags = if (has("tags")) {
            val tagsJson = getJSONArray("tags")
            (0 until tagsJson.length()).map { i -> tagsJson.getString(i) }
        } else {
            emptyList()
        }
        val documentKind = when (optString("documentKind", "scanned")) {
            "imported_file" -> DocumentKind.ImportedFile(optString("importedFileName", ""))
            else -> DocumentKind.Scanned
        }
        return ScanDocument(
            id = getString("id"),
            name = getString("name"),
            createdAtMillis = getLong("createdAtMillis"),
            pages = pages,
            folderId = if (isNull("folderId")) null else optString("folderId").takeIf { it.isNotEmpty() },
            tags = tags,
            documentKind = documentKind,
            importedFilePath = if (has("importedFilePath") && !isNull("importedFilePath")) {
                resolvePath(getString("importedFilePath"))
            } else {
                null
            }
        )
    }

    private fun JSONObject.toPage(): ScanPage = ScanPage(
        id = getString("id"),
        originalPath = resolvePath(getString("originalPath")),
        editedPath = resolvePath(getString("editedPath")),
        rotationDegrees = optInt("rotationDegrees", 0),
        filter = runCatching { PageFilter.valueOf(getString("filter")) }.getOrDefault(PageFilter.ORIGINAL),
        cropLeft = optDouble("cropLeft", 0.0).toFloat(),
        cropTop = optDouble("cropTop", 0.0).toFloat(),
        cropRight = optDouble("cropRight", 1.0).toFloat(),
        cropBottom = optDouble("cropBottom", 1.0).toFloat()
    )
}
