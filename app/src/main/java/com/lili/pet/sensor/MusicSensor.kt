package com.lili.pet.sensor

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import java.util.Timer
import java.util.TimerTask

/**
 * 音乐感知：监听系统媒体会话（MediaSession）。
 *
 * 不需要任何额外权限，就能拿到正在播放的歌名/歌手。
 * 每 5 秒轮询一次，换歌、停播都会回调。
 */
class MusicSensor(private val context: Context) {

    /** title 为空字符串表示停止播放。 */
    var listener: ((title: String, artist: String) -> Unit)? = null

    private val msm =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

    private var timer: Timer? = null
    private var lastTrack = ""

    fun start() {
        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                poll()
            }
        }, 0, POLL_INTERVAL_MS)
    }

    fun stop() {
        timer?.cancel()
        timer = null
        lastTrack = ""
    }

    private fun poll() {
        val current = currentTrack()
        val key = if (current != null) "${current.first}|${current.second}" else ""

        if (current != null && key != lastTrack) {
            lastTrack = key
            listener?.invoke(current.first, current.second)
        } else if (current == null && lastTrack.isNotEmpty()) {
            lastTrack = ""
            listener?.invoke("", "")
        }
    }

    /** 返回正在播放的第一首歌 (title, artist)，没有则 null。 */
    private fun currentTrack(): Pair<String, String>? {
        return try {
            val controllers = msm.getActiveSessions(null)
            for (controller in controllers) {
                val state = controller.playbackState ?: continue
                if (state.state != PlaybackState.STATE_PLAYING) continue
                val meta = controller.metadata ?: continue
                val title = meta.getString(MediaMetadata.METADATA_KEY_TITLE) ?: continue
                val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                return title to artist
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 5000L
    }
}
