package com.lili.pet.sensor

import android.os.FileObserver
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import java.io.File

/**
 * 截图检测：监听截图目录，拍到就摆 pose。
 * FileObserver 回调在后台线程，切主线程再操作 WebView。
 */
class ScreenshotObserver(private val webView: WebView?) {

    private val observers = mutableListOf<FileObserver>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val screenshotPaths = listOf(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            .resolve("Screenshots").absolutePath,
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            .resolve("Screenshots").absolutePath,
        "/storage/emulated/0/Pictures/Screenshots",
        "/storage/emulated/0/DCIM/Screenshots",
    )

    fun start() {
        for (path in screenshotPaths) {
            val dir = File(path)
            if (!dir.exists()) continue
            try {
                val observer = object : FileObserver(dir, CREATE or MOVED_TO) {
                    override fun onEvent(event: Int, path: String?) {
                        if (path != null && isImageFile(path)) {
                            onScreenshotDetected()
                        }
                    }
                }
                observer.startWatching()
                observers.add(observer)
            } catch (_: Exception) {
            }
        }
    }

    private fun isImageFile(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".png") ||
            lower.endsWith(".jpg") ||
            lower.endsWith(".jpeg")
    }

    private fun onScreenshotDetected() {
        mainHandler.post {
            webView?.evaluateJavascript(
                "window.petEngine && window.petEngine.onScreenshot()", null
            )
        }
    }

    fun stop() {
        observers.forEach { it.stopWatching() }
        observers.clear()
    }
}