package com.unshoo.pixelmusic.tv

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.media.MediaScannerConnection
import android.os.Environment
import android.widget.Toast
import com.unshoo.pixelmusic.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object TvUtils {
    /**
     * Checks if the device running the app is an Android TV / Google TV.
     */
    fun isTvDevice(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    private val AUDIO_EXTENSIONS = setOf("mp3", "flac", "wav", "m4a", "ogg", "opus", "aac", "wma")

    /**
     * Scans common storage folders (Downloads, Music) and notifies MediaScanner so
     * newly downloaded or dropped files are indexed by Android.
     */
    suspend fun scanStorageForMusic(context: Context, onComplete: () -> Unit) = withContext(Dispatchers.IO) {
        val dirs = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            File("/sdcard/Download"),
            File("/sdcard/Music"),
            context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        )

        val foundFiles = mutableListOf<String>()
        for (dir in dirs) {
            if (dir.exists() && dir.isDirectory) {
                dir.walkTopDown().maxDepth(4).forEach { file ->
                    if (file.isFile && file.extension.lowercase() in AUDIO_EXTENSIONS) {
                        foundFiles.add(file.absolutePath)
                    }
                }
            }
        }

        withContext(Dispatchers.Main) {
            if (foundFiles.isEmpty()) {
                Toast.makeText(context, context.getString(R.string.tv_toast_no_files), Toast.LENGTH_LONG).show()
                onComplete()
            } else {
                Toast.makeText(context, context.getString(R.string.tv_toast_files_found, foundFiles.size), Toast.LENGTH_SHORT).show()
                MediaScannerConnection.scanFile(
                    context,
                    foundFiles.toTypedArray(),
                    null
                ) { _, _ ->
                    onComplete()
                }
            }
        }
    }

    /**
     * Extracts built-in demo MP3 tracks to device storage and triggers MediaScanner.
     */
    suspend fun installDemoMusic(context: Context, onComplete: () -> Unit) = withContext(Dispatchers.IO) {
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            ?.takeIf { it.exists() || it.mkdirs() }
            ?: context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?: File("/sdcard/Music").apply { mkdirs() }

        val assetManager = context.assets
        val assetList = try {
            assetManager.list("demo") ?: emptyArray()
        } catch (e: Exception) {
            emptyArray()
        }

        val copiedPaths = mutableListOf<String>()
        for (fileName in assetList) {
            try {
                val outFile = File(musicDir, fileName)
                assetManager.open("demo/$fileName").use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
                copiedPaths.add(outFile.absolutePath)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        withContext(Dispatchers.Main) {
            if (copiedPaths.isNotEmpty()) {
                Toast.makeText(context, context.getString(R.string.tv_toast_demo_loaded, copiedPaths.size), Toast.LENGTH_SHORT).show()
                MediaScannerConnection.scanFile(
                    context,
                    copiedPaths.toTypedArray(),
                    null
                ) { _, _ ->
                    onComplete()
                }
            } else {
                Toast.makeText(context, context.getString(R.string.tv_toast_demo_failed), Toast.LENGTH_SHORT).show()
                onComplete()
            }
        }
    }
}
