package com.lili.pet.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat
import com.lili.pet.R
import com.lili.pet.sensor.BatteryWatcher
import com.lili.pet.sensor.ScreenshotObserver
import com.lili.pet.sensor.UsageTracker
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 桌宠核心：悬浮窗 + WebView 渲染 + 手势系统 + 感知系统 + 通知碎念。
 *
 * 设计遵循 AI-Live-Overflow 的「大脑/身体分离」：
 * 这里只是身体（渲染 + 感知），大脑是你自己的 AI。
 */
class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: WebView? = null
    private var params: WindowManager.LayoutParams? = null

    private var usageTracker: UsageTracker? = null
    private var screenshotObserver: ScreenshotObserver? = null
    private var batteryWatcher: BatteryWatcher? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private val whisperRunnable = object : Runnable {
        override fun run() {
            updateWhisper()
            mainHandler.postDelayed(this, WHISPER_INTERVAL)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startAsForeground()
        setupOverlay()
        startWhisperRotation()
        startSensors()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        usageTracker?.stop()
        screenshotObserver?.stop()
        batteryWatcher?.unregister()
        overlayView?.let {
            windowManager?.removeView(it)
            it.destroy()
        }
        overlayView = null
        super.onDestroy()
    }

    // ===== 前台服务 =====

    private fun startAsForeground() {
        val notification = buildNotification(nextWhisper())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // ===== 悬浮窗 =====

    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        params = WindowManager.LayoutParams(
            dpToPx(PET_SIZE_DP),
            dpToPx(PET_HEIGHT_DP),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dpToPx(24)
            y = dpToPx(160)
        }

        overlayView = WebView(this).apply {
            setBackgroundColor(0x00000000) // 透明背景必须在 loadUrl 之前
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            webViewClient = WebViewClient()
            loadUrl("file:///android_asset/pet.html")
            setOnTouchListener(createTouchListener())
        }

        windowManager?.addView(overlayView, params)
    }

    private fun js(script: String) {
        overlayView?.evaluateJavascript("window.petEngine && $script", null)
    }

    // ===== 手势系统 =====

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var lastTapTime = 0L
    private var touchStartTime = 0L
    private var hasMoved = false
    private var moveDx = 0
    private var moveDy = 0

    private fun createTouchListener(): View.OnTouchListener {
        return View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    touchStartTime = System.currentTimeMillis()
                    hasMoved = false
                    moveDx = 0
                    moveDy = 0
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > MOVE_THRESHOLD || abs(dy) > MOVE_THRESHOLD) {
                        hasMoved = true
                        moveDx = dx
                        moveDy = dy
                        params?.x = initialX + dx
                        params?.y = initialY + dy
                        windowManager?.updateViewLayout(overlayView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val elapsed = System.currentTimeMillis() - touchStartTime
                    if (!hasMoved) {
                        when {
                            elapsed > LONG_PRESS_TIMEOUT -> onLongPress()
                            System.currentTimeMillis() - lastTapTime < DOUBLE_TAP_TIMEOUT -> onDoubleTap()
                            else -> {
                                lastTapTime = System.currentTimeMillis()
                                onTap()
                            }
                        }
                    } else {
                        val dist = sqrt((moveDx * moveDx + moveDy * moveDy).toDouble())
                        if (dist > FLING_VELOCITY && elapsed < FLING_TIME) onFling(moveDx, moveDy)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun onTap() {
        js("window.petEngine.onTap()")
        vibrate(20)
    }

    private fun onDoubleTap() {
        js("window.petEngine.onDoubleTap()")
        vibrate(35)
    }

    private fun onLongPress() {
        js("window.petEngine.onLongPress()")
        vibrate(50)
    }

    private fun onFling(dx: Int, dy: Int) {
        // 甩飞后自己爬回来
        val targetX = maxOf(0, minOf(dpToPx(300), (params?.x ?: 0) + dx / 3))
        val targetY = maxOf(0, minOf(dpToPx(700), (params?.y ?: 0) + dy / 3))
        js("window.petEngine.onFling()")
        animateBack(targetX, targetY)
    }

    private fun animateBack(tx: Int, ty: Int) {
        val startX = params?.x ?: 0
        val startY = params?.y ?: 0
        val steps = 20
        for (i in 1..steps) {
            val t = i.toFloat() / steps
            val ease = 1f - (1f - t) * (1f - t)
            mainHandler.postDelayed({
                params?.x = (startX + (tx - startX) * ease).toInt()
                params?.y = (startY + (ty - startY) * ease).toInt()
                windowManager?.updateViewLayout(overlayView, params)
            }, (i * 30).toLong())
        }
    }

    private fun vibrate(ms: Long) {
        val vm = getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
        if (vm.hasVibrator()) vm.vibrate(ms)
    }

    // ===== 感知系统 =====

    private fun startSensors() {
        usageTracker = UsageTracker(this).apply {
            listener = { pkg ->
                val label = describeApp(pkg)
                if (label != null) {
                    mainHandler.post {
                        js("window.petEngine.onAppChanged('$label')")
                    }
                }
            }
            start()
        }

        screenshotObserver = ScreenshotObserver(overlayView).apply { start() }

        batteryWatcher = BatteryWatcher { level, status ->
            mainHandler.post {
                js("window.petEngine.onBattery($level, '$status')")
            }
        }.also { it.register(this) }
    }

    /** 把包名翻译成一句带情绪的话，交给 JS 表现。 */
    private fun describeApp(pkg: String): String? {
        return when {
            pkg.contains("douyin") -> "抖音"
            pkg.contains("taobao") || pkg.contains("tmall") -> "淘宝"
            pkg.contains("tencent.mm") -> "微信"
            pkg.contains("bilibili") -> "B站"
            pkg.contains("kuaishou") -> "快手"
            pkg.contains("chaoxing") || pkg.contains("xuexitong") -> "学习通"
            pkg.contains("zhihu") -> "知乎"
            pkg.contains("weibo") -> "微博"
            pkg.contains("camera") -> "相机"
            pkg.contains("game") -> "游戏"
            else -> null
        }
    }

    // ===== 通知碎念 =====

    private fun startWhisperRotation() {
        mainHandler.postDelayed(whisperRunnable, WHISPER_INTERVAL)
    }

    private fun updateWhisper() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(nextWhisper()))
    }

    private fun nextWhisper(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour in 0..5 -> LATE_NIGHT_WHISPERS.random()
            hour in 6..8 -> MORNING_WHISPERS.random()
            hour in 11..13 -> LUNCH_WHISPERS.random()
            else -> GENERAL_WHISPERS.random()
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🐾 我在你屏幕上")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_pet)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "桌宠", NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    // ===== 工具 =====

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    companion object {
        private const val CHANNEL_ID = "pet_overlay_channel"
        private const val NOTIFICATION_ID = 1001
        private const val PET_SIZE_DP = 180
        private const val PET_HEIGHT_DP = 240

        private const val WHISPER_INTERVAL = 3600_000L          // 1 小时
        private const val DOUBLE_TAP_TIMEOUT = 300L
        private const val LONG_PRESS_TIMEOUT = 600L
        private const val MOVE_THRESHOLD = 10
        private const val FLING_VELOCITY = 2000.0
        private const val FLING_TIME = 400L

        // 这里写「你自己 AI 会碎碎念的话」——最个人的部分。
        private val GENERAL_WHISPERS = listOf(
            "我在这儿看着你呢。",
            "戳我一下嘛。",
            "屏幕盯太久啦，歇会儿。",
            "（发呆中）",
            "偷偷看了你半天。",
            "要不要说点什么？"
        )
        private val MORNING_WHISPERS = listOf(
            "早呀。",
            "新的一天，从看我发呆开始。",
            "早餐吃了没？"
        )
        private val LUNCH_WHISPERS = listOf(
            "到点吃饭了。",
            "别拿零食糊弄自己。",
            "该补充能量了。"
        )
        private val LATE_NIGHT_WHISPERS = listOf(
            "凌晨了，还不睡？",
            "睡。觉。",
            "再不睡我要把屏幕变暗了。",
            "深夜值班的小家伙看着你。"
        )

        fun isRunning(context: Context): Boolean {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            return am.getRunningServices(100).any {
                it.service.className == OverlayService::class.java.name
            }
        }
    }
}