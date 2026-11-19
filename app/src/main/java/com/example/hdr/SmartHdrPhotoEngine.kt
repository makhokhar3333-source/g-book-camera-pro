package com.example.hdr

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object SmartHdrPhotoEngine {

    private const val PREVIEW_MAX_EDGE = 1600
    private const val EXPORT_MAX_EDGE = 4096

    /**
     * Safely decodes arbitrarily large photos (including 50MP / 108MP camera shots)
     * with EXIF orientation correction and bounded memory footprint.
     */
    suspend fun decodeSafeBitmap(
        context: Context,
        uri: Uri,
        maxEdge: Int = PREVIEW_MAX_EDGE
    ): Bitmap? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        val orientation = resolver.openInputStream(uri)?.use { stream ->
            runCatching {
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }

        val rawW = bounds.outWidth
        val rawH = bounds.outHeight
        if (rawW <= 0 || rawH <= 0) return@withContext null

        var sampleSize = 1
        while (max(rawW / sampleSize, rawH / sampleSize) > maxEdge * 1.35f) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decoded = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return@withContext null

        val rotated = applyExifOrientation(decoded, orientation)
        val longest = max(rotated.width, rotated.height)
        if (longest > maxEdge) {
            val scale = maxEdge.toFloat() / longest.toFloat()
            val targetW = (rotated.width * scale).toInt().coerceAtLeast(1)
            val targetH = (rotated.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(rotated, targetW, targetH, true)
            if (scaled !== rotated) rotated.recycle()
            scaled
        } else {
            rotated
        }
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
            else -> return bitmap
        }
        val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (transformed !== bitmap) bitmap.recycle()
        return transformed
    }

    /**
     * Authentic iPhone 18 Pro Max Smart HDR tone-mapping:
     * - f/1.48 shadow lift with true-black anchor (never milky/gray)
     * - f/4.0 highlight & sky detail recovery
     * - Natural golden studio skin warmth
     * - Crisp 48MP micro-contrast clarity & perceptual vibrance
     */
    suspend fun applySmartHdr(
        source: Bitmap,
        intensityPercent: Float
    ): Bitmap = withContext(Dispatchers.Default) {
        val s = (intensityPercent / 100f).coerceIn(0f, 1f)
        if (s <= 0.005f) {
            return@withContext source.copy(Bitmap.Config.ARGB_8888, false)
        }

        val w = source.width
        val h = source.height
        val count = w * h
        val pixels = IntArray(count)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        // Precompute perceptual luminance channel for fast 3x3 unsharp micro-contrast clarity
        val luma = FloatArray(count)
        for (i in 0 until count) {
            val c = pixels[i]
            val r = (c ushr 16) and 0xFF
            val g = (c ushr 8) and 0xFF
            val b = c and 0xFF
            luma[i] = (0.2126f * r + 0.7152f * g + 0.0722f * b) * (1f / 255f)
        }

        val out = IntArray(count)
        val clarityAmount = 0.36f * s
        val shadowLiftStrength = 0.56f * s
        val highlightRecoveryStrength = 0.27f * s
        val vibranceAmount = 0.24f * s

        for (y in 0 until h) {
            val yUp = if (y > 0) y - 1 else 0
            val yDown = if (y < h - 1) y + 1 else h - 1
            val rowOffset = y * w
            val rowUp = yUp * w
            val rowDown = yDown * w

            for (x in 0 until w) {
                val idx = rowOffset + x
                val c = pixels[idx]
                val a = c ushr 24

                var r = ((c ushr 16) and 0xFF) * (1f / 255f)
                var g = ((c ushr 8) and 0xFF) * (1f / 255f)
                var b = (c and 0xFF) * (1f / 255f)

                val l = luma[idx]

                // 1. Local 48MP micro-contrast / detail clarity (3x3 box high-pass)
                val xLeft = if (x > 0) x - 1 else 0
                val xRight = if (x < w - 1) x + 1 else w - 1
                val localAvg = (
                    luma[rowUp + xLeft] + luma[rowUp + x] + luma[rowUp + xRight] +
                        luma[rowOffset + xLeft] + l + luma[rowOffset + xRight] +
                        luma[rowDown + xLeft] + luma[rowDown + x] + luma[rowDown + xRight]
                    ) * (1f / 9f)
                val highPass = (l - localAvg).coerceIn(-0.18f, 0.18f)

                // 2. Shadow lift (f/1.48) with smooth true-black anchor so shadows stay deep, never milky
                val blackAnchor = smoothstep(0.012f, 0.085f, l)
                val shadowWeight = exp(-l * 4.3f) * blackAnchor
                val shadowScale = 1f + shadowLiftStrength * shadowWeight

                // 3. Highlight recovery (f/4.0) soft-knee roll-off preserving sky detail
                val highWeight = smoothstep(0.56f, 0.98f, l)
                val highScale = 1f - highlightRecoveryStrength * highWeight * (1f - 0.35f * (1f - l))

                val toneScale = shadowScale * highScale
                r = (r * toneScale + highPass * clarityAmount).coerceIn(0f, 1.25f)
                g = (g * toneScale + highPass * clarityAmount).coerceIn(0f, 1.25f)
                b = (b * toneScale + highPass * clarityAmount).coerceIn(0f, 1.25f)

                // Soft filmic shoulder to prevent harsh clipping
                r = filmicToeShoulder(r, s)
                g = filmicToeShoulder(g, s)
                b = filmicToeShoulder(b, s)

                // 4. Natural golden studio skin & warm sunlight glow
                val skinWeight = if (r > g && g >= b * 0.84f) {
                    val rgDelta = (r - g).coerceIn(0f, 0.35f) / 0.35f
                    val midMask = smoothstep(0.14f, 0.45f, l) * (1f - smoothstep(0.78f, 0.96f, l))
                    rgDelta * midMask
                } else {
                    0f
                }
                val studioWarmth = s * (0.018f + 0.042f * skinWeight)
                r = (r + studioWarmth).coerceIn(0f, 1f)
                g = (g + studioWarmth * 0.56f).coerceIn(0f, 1f)
                b = (b - studioWarmth * 0.22f).coerceIn(0f, 1f)

                // 5. Perceptual vibrance (boosts muted tones, protects skin/already saturated colors)
                val newL = 0.2126f * r + 0.7152f * g + 0.0722f * b
                val maxCh = max(r, max(g, b))
                val minCh = min(r, min(g, b))
                val sat = maxCh - minCh
                val vibranceBoost = 1f + vibranceAmount * (1f - sat.coerceIn(0f, 1f)) * (1f - 0.55f * skinWeight)

                r = (newL + (r - newL) * vibranceBoost).coerceIn(0f, 1f)
                g = (newL + (g - newL) * vibranceBoost).coerceIn(0f, 1f)
                b = (newL + (b - newL) * vibranceBoost).coerceIn(0f, 1f)

                val ri = (r * 255f + 0.5f).toInt().coerceIn(0, 255)
                val gi = (g * 255f + 0.5f).toInt().coerceIn(0, 255)
                val bi = (b * 255f + 0.5f).toInt().coerceIn(0, 255)
                out[idx] = (a shl 24) or (ri shl 16) or (gi shl 8) or bi
            }
        }

        Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun filmicToeShoulder(v: Float, strength: Float): Float {
        val clamped = v.coerceIn(0f, 1.25f)
        // Rich micro-contrast S-curve anchored at 0 and compressed cleanly near 1.0
        val compressed = clamped / (1f + 0.14f * strength * clamped)
        val norm = (compressed * (1f + 0.14f * strength)).coerceIn(0f, 1f)
        val sCurve = norm * norm * (3f - 2f * norm)
        return (norm * (1f - 0.16f * strength) + sCurve * (0.16f * strength)).pow(0.985f).coerceIn(0f, 1f)
    }

    /**
     * Exports full/high-res Smart HDR processed photo cleanly to the Android Gallery.
     */
    suspend fun exportPhotoToGallery(
        context: Context,
        sourceUri: Uri,
        intensityPercent: Float
    ): Uri? = withContext(Dispatchers.IO) {
        val fullSafe = decodeSafeBitmap(context, sourceUri, EXPORT_MAX_EDGE) ?: return@withContext null
        val enhanced = try {
            applySmartHdr(fullSafe, intensityPercent)
        } finally {
            if (!fullSafe.isRecycled) fullSafe.recycle()
        }

        val fileName = "GBook_HDR_${System.currentTimeMillis()}.jpg"
        return@withContext try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/G-Book Camera"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext null

                resolver.openOutputStream(uri)?.use { out ->
                    enhanced.compress(Bitmap.CompressFormat.JPEG, 96, out)
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } else {
                val dir = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                    "G-Book Camera"
                ).apply { mkdirs() }
                val file = File(dir, fileName)
                FileOutputStream(file).use { out ->
                    enhanced.compress(Bitmap.CompressFormat.JPEG, 96, out)
                }
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    arrayOf("image/jpeg"),
                    null
                )
                Uri.fromFile(file)
            }
        } finally {
            if (!enhanced.isRecycled) enhanced.recycle()
        }
    }
}
