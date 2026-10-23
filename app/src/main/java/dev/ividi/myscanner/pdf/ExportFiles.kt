package dev.ividi.myscanner.pdf

import android.content.Context
import java.io.File
import java.util.UUID

/** Shared naming and location rules for every exporter. */
internal object ExportFiles {

    /** A fresh cache directory per export, so concurrent or repeated exports never collide. */
    fun newDirectory(context: Context): File =
        File(context.cacheDir, "exports/${UUID.randomUUID()}").apply { mkdirs() }

    /** Keeps letters (including accented ones), digits, spaces, '-' and '_'. */
    fun safeName(name: String): String =
        name.replace(Regex("[^\\p{L}\\p{N} _-]"), "_").trim().ifBlank { "document" }
}
