package dev.ividi.myscanner.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import dev.ividi.myscanner.data.PageFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * Bitmap transforms used by the editor: crop, rotate, content-bounds detection and the
 * on-device enhancement filters. Everything runs on plain Bitmap/Canvas APIs so it works
 * fully offline with no external dependency.
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

    /**
     * Approximates the document's bounding box by trimming uniform-background margins
     * from the border of the image. This is a plain-pixel bounding-box/margin-trim
     * heuristic, not true perspective quadrilateral detection: it scans in from each
     * edge until it finds a row/column with meaningfully varying luminance (an "edge"),
     * assuming a photographed page sits on a comparatively uniform desk/table background.
     * It will not correct rotation, skew or perspective the way a full contour/quad
     * detector (e.g. built on OpenCV) would - it only tightens an axis-aligned rectangle.
     * Returns normalized [0,1] fractions so the result applies at any resolution.
     * Should be called off the main thread; pass a downsampled bitmap for speed.
     */
    suspend fun detectContentBounds(bitmap: Bitmap): RectF = withContext(Dispatchers.Default) {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 4 || height < 4) return@withContext RectF(0f, 0f, 1f, 1f)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        fun luminance(argb: Int): Int {
            val r = Color.red(argb)
            val g = Color.green(argb)
            val b = Color.blue(argb)
            return (r * 299 + g * 587 + b * 114) / 1000
        }

        // Threshold for how much a row/column's luminance must vary from the border
        // background sample before it's considered the start of document content.
        val threshold = 18
        val borderSample = luminance(pixels[0])

        var left = 0
        while (left < width / 2) {
            var varied = 0
            var y = 0
            while (y < height) {
                if (abs(luminance(pixels[y * width + left]) - borderSample) > threshold) varied++
                y += maxOf(1, height / 64)
            }
            if (varied > 2) break
            left++
        }

        var right = width - 1
        while (right > width / 2) {
            var varied = 0
            var y = 0
            while (y < height) {
                if (abs(luminance(pixels[y * width + right]) - borderSample) > threshold) varied++
                y += maxOf(1, height / 64)
            }
            if (varied > 2) break
            right--
        }

        var top = 0
        while (top < height / 2) {
            var varied = 0
            var x = 0
            while (x < width) {
                if (abs(luminance(pixels[top * width + x]) - borderSample) > threshold) varied++
                x += maxOf(1, width / 64)
            }
            if (varied > 2) break
            top++
        }

        var bottom = height - 1
        while (bottom > height / 2) {
            var varied = 0
            var x = 0
            while (x < width) {
                if (abs(luminance(pixels[bottom * width + x]) - borderSample) > threshold) varied++
                x += maxOf(1, width / 64)
            }
            if (varied > 2) break
            bottom--
        }

        // Small safety margin so we don't clip into the document edge itself.
        val marginX = width * 0.01f
        val marginY = height * 0.01f
        val l = ((left - marginX).coerceAtLeast(0f)) / width
        val t = ((top - marginY).coerceAtLeast(0f)) / height
        val r = ((right + marginX).coerceAtMost(width.toFloat())) / width
        val b = ((bottom + marginY).coerceAtMost(height.toFloat())) / height

        // Guard against a degenerate/near-full-frame result (busy background,
        // shadow, or a document that already fills the frame): fall back to
        // the full frame rather than producing a nonsensical tiny/inverted crop.
        if (r - l < 0.3f || b - t < 0.3f || l >= r || t >= b) {
            RectF(0f, 0f, 1f, 1f)
        } else {
            RectF(l, t, r, b)
        }
    }

    fun saveTo(bitmap: Bitmap, file: File, quality: Int = 92) {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
    }
}
