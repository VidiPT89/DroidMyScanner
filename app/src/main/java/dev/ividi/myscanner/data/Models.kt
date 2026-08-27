package dev.ividi.myscanner.data

/**
 * Non-destructive page filter applied on top of the edited (cropped/rotated) bitmap.
 */
enum class PageFilter {
    ORIGINAL,
    BLACK_AND_WHITE,
    GRAYSCALE,
    ENHANCE
}

/**
 * A single scanned page. [originalPath] always points at the untouched capture so edits
 * can be redone or reset; [editedPath] is the current crop/rotate result that filters are
 * rendered on top of at display/export time.
 */
data class ScanPage(
    val id: String,
    val originalPath: String,
    val editedPath: String,
    val rotationDegrees: Int = 0,
    val filter: PageFilter = PageFilter.ORIGINAL,
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f
)

/**
 * Distinguishes documents made up of editable scanned pages from documents that are just
 * a copy of an arbitrary file (e.g. a Word document) picked via "Browse Files", which has
 * no in-app editing pipeline and is opened externally instead. Defaults to [Scanned] so
 * documents saved before this field existed still deserialize correctly.
 */
sealed class DocumentKind {
    object Scanned : DocumentKind()
    data class ImportedFile(val originalFileName: String) : DocumentKind()
}

/**
 * A saved multi-page document made up of [pages] in display order.
 * [folderId] points at a [DocumentFolder]; null means "All Documents".
 * [tags] are free-text labels the user can attach for filtering.
 * [documentKind] distinguishes normal scanned/imported-image documents from an
 * [DocumentKind.ImportedFile], whose actual bytes live at [importedFilePath].
 */
data class ScanDocument(
    val id: String,
    val name: String,
    val createdAtMillis: Long,
    val pages: List<ScanPage> = emptyList(),
    val folderId: String? = null,
    val tags: List<String> = emptyList(),
    val documentKind: DocumentKind = DocumentKind.Scanned,
    val importedFilePath: String? = null
) {
    val pageCount: Int get() = pages.size
}

/**
 * A user-created grouping for documents. Deleting a folder does not delete its
 * documents; they simply revert to having no folder ("All Documents").
 */
data class DocumentFolder(
    val id: String,
    val name: String
)

enum class AppThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}

enum class AppLanguage(val tag: String) {
    ENGLISH("en"),
    PORTUGUESE_PT("pt-PT")
}
