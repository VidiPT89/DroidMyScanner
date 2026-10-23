package dev.ividi.myscanner

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.ividi.myscanner.data.DocumentRepository
import dev.ividi.myscanner.data.ScanDocument
import dev.ividi.myscanner.data.ScanPage
import dev.ividi.myscanner.scanner.ImageProcessor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DocumentRegressionTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(target.cacheDir, "repository-test-${UUID.randomUUID()}").apply { mkdirs() }
    private val context: Context = object : ContextWrapper(target) {
        override fun getFilesDir(): File = root
    }

    @After fun cleanUp() { root.deleteRecursively() }

    @Test fun concurrentUpdatesSurviveReload() = runBlocking {
        val repository = DocumentRepository(context)
        (1..20).map { index -> async {
            repository.upsertDocument(ScanDocument("$index", "Document $index", index.toLong()))
        } }.awaitAll()
        val reloaded = DocumentRepository(context)
        reloaded.load()
        assertEquals(20, reloaded.documents.value.size)
    }

    @Test fun concurrentTransformsKeepEveryTag() = runBlocking {
        val repository = DocumentRepository(context)
        repository.upsertDocument(ScanDocument("doc", "Test", 0))
        (1..20).map { index -> async {
            repository.updateDocument("doc") { it.copy(tags = it.tags + "$index") }
        } }.awaitAll()
        assertEquals(20, repository.getDocument("doc")!!.tags.size)
    }

    @Test fun deletingDocumentRemovesInboxFilesButPreservesSharedImages() = runBlocking {
        val repository = DocumentRepository(context)
        val original = repository.newPageFile("inbox").apply { writeText("original") }
        val edited = repository.newPageFile("temporary-pdf-id").apply { writeText("edited") }
        val page = ScanPage("page", original.path, edited.path)
        repository.upsertDocument(ScanDocument("a", "A", 0, pages = listOf(page)))
        repository.upsertDocument(ScanDocument("b", "B", 0, pages = listOf(page.copy(editedPath = original.path))))
        repository.deleteDocument("a")
        assertTrue(original.exists())
        assertFalse(edited.exists())
        repository.deleteDocument("b")
        assertFalse(original.exists())
    }

    @Test fun deletingImportedFileRemovesItsBytes() = runBlocking {
        val repository = DocumentRepository(context)
        val file = repository.newImportedFile("contract.docx").apply { writeText("contract") }
        repository.upsertDocument(ScanDocument("doc", "Contract", 0, importedFilePath = file.path))
        repository.deleteDocument("doc")
        assertFalse(file.exists())
    }

    @Test fun corruptIndexIsNotOverwritten() = runBlocking {
        val index = File(root, "documents_index.json").apply { writeText("invalid JSON") }
        val repository = DocumentRepository(context)
        val result = runCatching { repository.upsertDocument(ScanDocument("new", "New", 0)) }
        assertTrue(result.isFailure)
        assertEquals("invalid JSON", index.readText())
    }

    @Test fun deletingFolderKeepsDocuments() = runBlocking {
        val repository = DocumentRepository(context)
        repository.addFolder("Receipts")
        val folder = repository.folders.value.single()
        repository.upsertDocument(ScanDocument("doc", "Receipt", 0, folderId = folder.id))
        repository.deleteFolder(folder.id)
        val reloaded = DocumentRepository(context)
        reloaded.load()
        assertEquals(1, reloaded.documents.value.size)
        assertNull(reloaded.documents.value.single().folderId)
        assertTrue(reloaded.folders.value.isEmpty())
    }

    @Test fun indexStoresPortableImagePaths() = runBlocking {
        val repository = DocumentRepository(context)
        val file = repository.newPageFile("inbox").apply { writeText("image") }
        repository.upsertDocument(ScanDocument("doc", "Test", 0,
            pages = listOf(ScanPage("page", file.path, file.path))))
        assertFalse(File(root, "documents_index.json").readText().contains(root.path))
        val reloaded = DocumentRepository(context)
        reloaded.load()
        assertEquals(file.path, reloaded.getDocument("doc")!!.pages.single().originalPath)
    }

    @Test fun importedPhotoRespectsExifRotation() {
        val file = File(root, "rotated.jpg")
        val bitmap = Bitmap.createBitmap(10, 20, Bitmap.Config.ARGB_8888)
        ImageProcessor.saveTo(bitmap, file)
        bitmap.recycle()
        android.media.ExifInterface(file.path).apply {
            setAttribute(android.media.ExifInterface.TAG_ORIENTATION, "6")
            saveAttributes()
        }
        val loaded = ImageProcessor.loadBitmap(file.path)!!
        assertEquals(20, loaded.width)
        assertEquals(10, loaded.height)
        loaded.recycle()
    }

    @Test fun cropHandlesAtImageEdgeDoNotCrash() {
        val bitmap = Bitmap.createBitmap(10, 20, Bitmap.Config.ARGB_8888)
        val cropped = ImageProcessor.crop(bitmap, 1f, 1f, 1f, 1f)
        assertEquals(1, cropped.width)
        assertEquals(1, cropped.height)
        cropped.recycle()
        bitmap.recycle()
    }

    @Test fun invertedCropIsNormalized() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val cropped = ImageProcessor.crop(bitmap, .8f, .8f, .2f, .2f)
        assertEquals(60, cropped.width)
        assertEquals(60, cropped.height)
        cropped.recycle()
        bitmap.recycle()
    }
}
