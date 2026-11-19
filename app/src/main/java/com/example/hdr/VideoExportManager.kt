package com.example.hdr

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@UnstableApi
object VideoExportManager {

    /**
     * Hardware-accelerated GPU export via Media3 Transformer.
     * Preserves 100% original audio and exact 1.0x playback speed.
     */
    suspend fun exportVideoToGallery(
        context: Context,
        sourceUri: Uri,
        intensityPercent: Float,
        onProgress: (Int) -> Unit
    ): Uri? = withContext(Dispatchers.Main.immediate) {
        val tempFile = File(context.cacheDir, "gbook_export_${System.currentTimeMillis()}.mp4")
        if (tempFile.exists()) tempFile.delete()

        val hdrEffect = SmartHdrGlEffect(
            intensityProvider = { intensityPercent },
            compareOriginalProvider = { false }
        )

        val mediaItem = MediaItem.fromUri(sourceUri)
        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            // Audio is preserved at 100% original quality & 1.0x speed by default
            .setEffects(Effects(emptyList(), listOf(hdrEffect)))
            .build()

        val mainHandler = Handler(Looper.getMainLooper())
        var activeTransformer: Transformer? = null

        try {
            suspendCancellableCoroutine<Unit> { cont ->
                val progressHolder = ProgressHolder()
                var finished = false

                val listener = object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        finished = true
                        onProgress(100)
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exception: ExportException
                    ) {
                        finished = true
                        if (cont.isActive) cont.resumeWithException(exception)
                    }
                }

                val transformer = Transformer.Builder(context)
                    .addListener(listener)
                    .build()
                activeTransformer = transformer

                cont.invokeOnCancellation {
                    mainHandler.post {
                        runCatching { transformer.cancel() }
                    }
                }

                transformer.start(editedMediaItem, tempFile.absolutePath)

                val progressRunnable = object : Runnable {
                    override fun run() {
                        if (finished || !cont.isActive) return
                        val state = transformer.getProgress(progressHolder)
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgress(progressHolder.progress.coerceIn(0, 99))
                        }
                        if (state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                            mainHandler.postDelayed(this, 120L)
                        }
                    }
                }
                mainHandler.postDelayed(progressRunnable, 120L)
            }

            // Move completed MP4 cleanly into Android Gallery
            saveVideoFileToGallery(context, tempFile)
        } finally {
            activeTransformer = null
            if (tempFile.exists()) tempFile.delete()
        }
    }

    private suspend fun saveVideoFileToGallery(context: Context, sourceFile: File): Uri? =
        withContext(Dispatchers.IO) {
            if (!sourceFile.exists() || sourceFile.length() == 0L) return@withContext null
            val displayName = "GBook_HDR_${System.currentTimeMillis()}.mp4"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(
                        MediaStore.Video.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_MOVIES}/G-Book Camera"
                    )
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext null

                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(out, bufferSize = 64 * 1024)
                    }
                }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } else {
                val dir = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                    "G-Book Camera"
                ).apply { mkdirs() }
                val destFile = File(dir, displayName)
                sourceFile.copyTo(destFile, overwrite = true)
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destFile.absolutePath),
                    arrayOf("video/mp4"),
                    null
                )
                Uri.fromFile(destFile)
            }
        }
}
