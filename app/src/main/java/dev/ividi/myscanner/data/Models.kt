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
 * A saved multi-page document made up of [pages] in display order.
 */
data class ScanDocument(
    val id: String,
    val name: String,
    val createdAtMillis: Long,
    val pages: List<ScanPage> = emptyList()
) {
    val pageCount: Int get() = pages.size
}

enum class AppThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}

enum class AppLanguage(val tag: String) {
    ENGLISH("en"),
    PORTUGUESE_PT("pt-PT")
}
