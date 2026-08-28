package dev.ividi.myscanner.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.ividi.myscanner.data.AppLanguage
import dev.ividi.myscanner.data.AppThemeMode
import dev.ividi.myscanner.data.DocumentFolder
import dev.ividi.myscanner.data.DocumentKind
import dev.ividi.myscanner.data.DocumentRepository
import dev.ividi.myscanner.data.LocaleManager
import dev.ividi.myscanner.data.PageFilter
import dev.ividi.myscanner.data.ScanDocument
import dev.ividi.myscanner.data.ScanPage
import dev.ividi.myscanner.data.UserPreferences
import dev.ividi.myscanner.scanner.ImageProcessor
import dev.ividi.myscanner.scanner.TextRecognizerClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DocumentRepository(application)
    private val preferences = UserPreferences(application)
    private val textRecognizer = TextRecognizerClient()

    val documents: StateFlow<List<ScanDocument>> = repository.documents

    val folders: StateFlow<List<DocumentFolder>> = repository.folders

    /** Null means "All Documents"; otherwise the id of the folder currently filtered on. */
    private val _selectedFolderId = MutableStateFlow<String?>(null)
    val selectedFolderId: StateFlow<String?> = _selectedFolderId

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    /** Documents narrowed by the current folder selection and search query. */
    val filteredDocuments: StateFlow<List<ScanDocument>> = combine(
        repository.documents, _selectedFolderId, _searchQuery
    ) { docs, folderId, query ->
        val byFolder = if (folderId == null) docs else docs.filter { it.folderId == folderId }
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
            byFolder
        } else {
            byFolder.filter { doc ->
                doc.name.contains(trimmedQuery, ignoreCase = true) ||
                    doc.tags.any { it.contains(trimmedQuery, ignoreCase = true) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val themeMode: StateFlow<AppThemeMode> = preferences.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppThemeMode.DARK)

    val language: StateFlow<AppLanguage> = preferences.language
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppLanguage.ENGLISH)

    val onboardingDone: StateFlow<Boolean> = preferences.onboardingDone
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _extractedText = MutableStateFlow<String?>(null)
    val extractedText: StateFlow<String?> = _extractedText

    init {
        viewModelScope.launch { repository.load() }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            preferences.setLanguage(language)
            LocaleManager.apply(language)
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch { preferences.setOnboardingDone(true) }
    }

    /** Creates a new document from freshly scanned page image paths (already local files). */
    fun createDocumentFromScan(pagePaths: List<String>): String {
        val docId = UUID.randomUUID().toString()
        val timestamp = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date())
        val pages = pagePaths.map { path ->
            ScanPage(id = UUID.randomUUID().toString(), originalPath = path, editedPath = path)
        }
        val document = ScanDocument(
            id = docId,
            name = timestamp,
            createdAtMillis = System.currentTimeMillis(),
            pages = pages
        )
        viewModelScope.launch { repository.upsertDocument(document) }
        return docId
    }

    /** Creates a new document from freshly rendered PDF page image paths (already local files). */
    fun createDocumentFromPages(name: String, pagePaths: List<String>): String {
        val docId = UUID.randomUUID().toString()
        val pages = pagePaths.map { path ->
            ScanPage(id = UUID.randomUUID().toString(), originalPath = path, editedPath = path)
        }
        val document = ScanDocument(
            id = docId,
            name = name,
            createdAtMillis = System.currentTimeMillis(),
            pages = pages
        )
        viewModelScope.launch { repository.upsertDocument(document) }
        return docId
    }

    /** Creates an "imported file" document entry for a non-image, non-PDF file copied as-is. */
    fun createImportedFileDocument(originalFileName: String, storedFilePath: String): String {
        val docId = UUID.randomUUID().toString()
        val document = ScanDocument(
            id = docId,
            name = originalFileName.substringBeforeLast('.').ifBlank { originalFileName },
            createdAtMillis = System.currentTimeMillis(),
            documentKind = DocumentKind.ImportedFile(originalFileName),
            importedFilePath = storedFilePath
        )
        viewModelScope.launch { repository.upsertDocument(document) }
        return docId
    }

    fun newImportedFile(fileName: String): File = repository.newImportedFile(fileName)

    fun getDocument(id: String): ScanDocument? = repository.getDocument(id)

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectFolder(folderId: String?) {
        _selectedFolderId.value = folderId
    }

    fun createFolder(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.addFolder(name.trim()) }
    }

    fun renameFolder(folderId: String, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch { repository.renameFolder(folderId, newName.trim()) }
    }

    fun deleteFolder(folderId: String) {
        viewModelScope.launch {
            repository.deleteFolder(folderId)
            if (_selectedFolderId.value == folderId) _selectedFolderId.value = null
        }
    }

    fun setDocumentFolder(documentId: String, folderId: String?) {
        val doc = repository.getDocument(documentId) ?: return
        viewModelScope.launch { repository.upsertDocument(doc.copy(folderId = folderId)) }
    }

    fun addTag(documentId: String, tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isEmpty()) return
        val doc = repository.getDocument(documentId) ?: return
        if (doc.tags.any { it.equals(trimmed, ignoreCase = true) }) return
        viewModelScope.launch { repository.upsertDocument(doc.copy(tags = doc.tags + trimmed)) }
    }

    fun removeTag(documentId: String, tag: String) {
        val doc = repository.getDocument(documentId) ?: return
        viewModelScope.launch { repository.upsertDocument(doc.copy(tags = doc.tags - tag)) }
    }

    fun renameDocument(id: String, newName: String) {
        viewModelScope.launch { repository.renameDocument(id, newName) }
    }

    fun deleteDocument(id: String) {
        viewModelScope.launch { repository.deleteDocument(id) }
    }

    fun deletePage(documentId: String, pageId: String) {
        val doc = repository.getDocument(documentId) ?: return
        val updated = doc.copy(pages = doc.pages.filterNot { it.id == pageId })
        viewModelScope.launch { repository.upsertDocument(updated) }
    }

    fun reorderPages(documentId: String, orderedPageIds: List<String>) {
        val doc = repository.getDocument(documentId) ?: return
        val byId = doc.pages.associateBy { it.id }
        val reordered = orderedPageIds.mapNotNull { byId[it] }
        viewModelScope.launch { repository.upsertDocument(doc.copy(pages = reordered)) }
    }

    fun rotatePage(documentId: String, pageId: String, deltaDegrees: Int) {
        updatePage(documentId, pageId) { it.copy(rotationDegrees = (it.rotationDegrees + deltaDegrees + 360) % 360) }
    }

    fun setPageFilter(documentId: String, pageId: String, filter: PageFilter) {
        updatePage(documentId, pageId) { it.copy(filter = filter) }
    }

    fun cropPage(documentId: String, pageId: String, left: Float, top: Float, right: Float, bottom: Float) {
        val doc = repository.getDocument(documentId) ?: return
        val page = doc.pages.find { it.id == pageId } ?: return
        viewModelScope.launch {
            val outFile = withContext(Dispatchers.Default) {
                val original = ImageProcessor.loadBitmap(page.originalPath) ?: return@withContext null
                val cropped = ImageProcessor.crop(original, left, top, right, bottom)
                val file = repository.newPageFile(documentId)
                ImageProcessor.saveTo(cropped, file)
                file
            } ?: return@launch
            updatePage(documentId, pageId) {
                it.copy(
                    editedPath = outFile.absolutePath,
                    cropLeft = left, cropTop = top, cropRight = right, cropBottom = bottom,
                    rotationDegrees = 0
                )
            }
        }
    }

    fun addPagesToDocument(documentId: String, pagePaths: List<String>) {
        val doc = repository.getDocument(documentId) ?: return
        val newPages = pagePaths.map { path -> ScanPage(id = UUID.randomUUID().toString(), originalPath = path, editedPath = path) }
        viewModelScope.launch { repository.upsertDocument(doc.copy(pages = doc.pages + newPages)) }
    }

    fun extractTextFromPage(page: ScanPage) {
        viewModelScope.launch {
            _extractedText.value = null
            val bitmap = ImageProcessor.loadBitmap(page.editedPath)?.let { bmp ->
                val rotated = ImageProcessor.rotate(bmp, page.rotationDegrees)
                ImageProcessor.applyFilter(rotated, page.filter)
            }
            _extractedText.value = if (bitmap != null) {
                runCatching { textRecognizer.recognize(bitmap) }.getOrDefault("")
            } else {
                ""
            }
        }
    }

    fun clearExtractedText() {
        _extractedText.value = null
    }

    /**
     * Returns true the first time the camera-scanner fallback (gallery picker) triggers,
     * so the caller can show an explanatory message exactly once, then remembers it fired.
     */
    suspend fun consumeScanFallbackNotice(): Boolean {
        val alreadyShown = preferences.scanFallbackShown.first()
        if (!alreadyShown) preferences.setScanFallbackShown(true)
        return !alreadyShown
    }

    fun documentDirectory(documentId: String): File = repository.directoryFor(documentId)

    fun newPageFile(documentId: String): File = repository.newPageFile(documentId)

    private fun updatePage(documentId: String, pageId: String, transform: (ScanPage) -> ScanPage) {
        val doc = repository.getDocument(documentId) ?: return
        val updatedPages = doc.pages.map { if (it.id == pageId) transform(it) else it }
        viewModelScope.launch { repository.upsertDocument(doc.copy(pages = updatedPages)) }
    }
}
