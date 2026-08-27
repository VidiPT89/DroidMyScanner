package dev.ividi.myscanner.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.ividi.myscanner.data.AppLanguage
import dev.ividi.myscanner.data.AppThemeMode
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

    fun getDocument(id: String): ScanDocument? = repository.getDocument(id)

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
        val original = ImageProcessor.loadBitmap(page.originalPath) ?: return
        val cropped = ImageProcessor.crop(original, left, top, right, bottom)
        val outFile = repository.newPageFile(documentId)
        ImageProcessor.saveTo(cropped, outFile)
        updatePage(documentId, pageId) {
            it.copy(
                editedPath = outFile.absolutePath,
                cropLeft = left, cropTop = top, cropRight = right, cropBottom = bottom,
                rotationDegrees = 0
            )
        }
    }

    fun resetPageCrop(documentId: String, pageId: String) {
        val doc = repository.getDocument(documentId) ?: return
        val page = doc.pages.find { it.id == pageId } ?: return
        updatePage(documentId, pageId) {
            it.copy(editedPath = page.originalPath, cropLeft = 0f, cropTop = 0f, cropRight = 1f, cropBottom = 1f, rotationDegrees = 0)
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

    fun documentDirectory(documentId: String): File = repository.directoryFor(documentId)

    fun newPageFile(documentId: String): File = repository.newPageFile(documentId)

    private fun updatePage(documentId: String, pageId: String, transform: (ScanPage) -> ScanPage) {
        val doc = repository.getDocument(documentId) ?: return
        val updatedPages = doc.pages.map { if (it.id == pageId) transform(it) else it }
        viewModelScope.launch { repository.upsertDocument(doc.copy(pages = updatedPages)) }
    }
}
