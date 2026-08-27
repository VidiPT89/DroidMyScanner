package dev.ividi.myscanner.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import dev.ividi.myscanner.data.PageFilter
import java.io.File
import java.io.FileOutputStream

/**
 * Bitmap transforms used by the editor: crop, rotate and the on-device "AI-style"
 * enhancement filters. Everything runs synchronously on plain Bitmap/Canvas APIs so it
 * works fully offline with no external dependency.
 */
object ImageProcessor {

    fun loadBitmap(path: String): Bitmap? = BitmapFactory.decodeFile(path)

    /**
     * Decodes a bitmap downsampled to at most [maxDimension] on its longest side.
     * Camera scans are commonly 12MP+; decoding them at full resolution just to show a
     * thumbnail or an on-screen preview can spike memory enough to be jetsam-killed on a
     * real device, especially when several pages are decoded at once (e.g. a thumbnail row).
     * Use [loadBitmap] only where the true full-resolution pixels are actually needed
     * (export, OCR, final crop/filter processing).
     */
    fun loadDownsampledBitmap(path: String, maxDimension: Int = 1200): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= maxDimension || bounds.outHeight / (sampleSize * 2) >= maxDimension) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeFile(path, options)
    }

    fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** Crops using normalized [0,1] rectangle coordinates relative to the bitmap. */
    fun crop(bitmap: Bitmap, left: Float, top: Float, right: Float, bottom: Float): Bitmap {
        val l = (left.coerceIn(0f, 1f) * bitmap.width).toInt()
        val t = (top.coerceIn(0f, 1f) * bitmap.height).toInt()
        val r = (right.coerceIn(0f, 1f) * bitmap.width).toInt()
        val b = (bottom.coerceIn(0f, 1f) * bitmap.height).toInt()
        val width = (r - l).coerceAtLeast(1)
        val height = (b - t).coerceAtLeast(1)
        return Bitmap.createBitmap(bitmap, l, t, width, height)
    }

    fun applyFilter(bitmap: Bitmap, filter: PageFilter): Bitmap = when (filter) {
        PageFilter.ORIGINAL -> bitmap
        PageFilter.GRAYSCALE -> applyColorMatrix(bitmap, grayscaleMatrix())
        PageFilter.BLACK_AND_WHITE -> applyColorMatrix(bitmap, blackAndWhiteMatrix())
        PageFilter.ENHANCE -> applyColorMatrix(bitmap, enhanceMatrix())
    }

    private fun grayscaleMatrix(): ColorMatrix = ColorMatrix().apply { setSaturation(0f) }

    private fun blackAndWhiteMatrix(): ColorMatrix {
        // High-contrast grayscale that approximates a scanned black & white document.
        return ColorMatrix(
            floatArrayOf(
                1.5f, 1.5f, 1.5f, 0f, -180f,
                1.5f, 1.5f, 1.5f, 0f, -180f,
                1.5f, 1.5f, 1.5f, 0f, -180f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    private fun enhanceMatrix(): ColorMatrix {
        // Mild contrast + brightness + saturation boost to make scans look crisper.
        val contrast = 1.15f
        val brightness = 12f
        val translate = (-.5f * contrast + .5f) * 255f + brightness
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val saturationMatrix = ColorMatrix().apply { setSaturation(1.1f) }
        contrastMatrix.postConcat(saturationMatrix)
        return contrastMatrix
    }

    private fun applyColorMatrix(bitmap: Bitmap, matrix: ColorMatrix): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return output
    }

    fun saveTo(bitmap: Bitmap, file: File, quality: Int = 92) {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
    }
}
