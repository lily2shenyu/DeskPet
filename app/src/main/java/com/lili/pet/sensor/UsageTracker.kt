package com.lili.pet.sensor

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Timer
import java.util.TimerTask

/**
 * 前台 App 检测：每 3 秒轮询 UsageStats，切换时回调。
 */
class UsageTracker(private val context: Context) {

    var listener: ((packageName: String) -> Unit)? = null

    private var timer: Timer? = null
    private var lastApp = ""
    private var lastNotifyAt = 0L

    private val COOLDOWN_MS = 15_000L // 快速切 app 防误触

    fun start() {
        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                val current = getForegroundApp()
                if (current.isNotEmpty() && current != lastApp) {
                    val now = System.currentTimeMillis()
                    lastApp = current
                    if (now - lastNotifyAt >= COOLDOWN_MS) {
                        lastNotifyAt = now
                        listener?.invoke(current)
                    }
                }
            }
        }, 0, 3000)
    }

    private fun getForegroundApp(): String {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(now - 5000, now)
            val event = UsageEvents.Event()
            var foreground = ""
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    foreground = event.packageName ?: ""
                }
            }
            foreground
        } catch (_: Exception) {
            ""
        }
    }

    fun stop() {
        timer?.cancel()
        timer = null
    }
}