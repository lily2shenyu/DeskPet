package com.lili.pet

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.lili.pet.service.OverlayService

/**
 * 入口：引导授权（悬浮窗 / 通知 / 使用情况访问），启动或停止桌宠服务。
 */
class MainActivity : Activity() {

    private lateinit var btnToggle: Button
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnToggle = findViewById(R.id.btnToggle)
        tvStatus = findViewById(R.id.tvStatus)
        btnToggle.setOnClickListener {
            if (OverlayService.isRunning(this)) stopPet() else startPet()
        }
        findViewById<Button>(R.id.btnOverlayPerm).setOnClickListener { requestOverlayPermission() }
        findViewById<Button>(R.id.btnUsagePerm).setOnClickListener { requestUsageAccess() }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun isOverlayEnabled(): Boolean = Settings.canDrawOverlays(this)

    private fun refreshUi() {
        val running = OverlayService.isRunning(this)
        btnToggle.text = if (running) "停止桌宠" else "启动桌宠"
        tvStatus.text = buildString {
            append("悬浮窗权限：")
            append(if (isOverlayEnabled()) "✅ 已授予" else "❌ 未授予（点下方按钮去开）")
            append("\n桌宠状态：")
            append(if (running) "运行中 🐾" else "未运行")
            append("\n通知权限：")
            append(
                if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                        this@MainActivity, Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                ) "✅" else "❌"
            )
        }
    }

    private fun startPet() {
        if (!isOverlayEnabled()) {
            requestOverlayPermission()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100
            )
        }
        startForegroundServiceCompat()
        refreshUi()
    }

    private fun startForegroundServiceCompat() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopPet() {
        stopService(Intent(this, OverlayService::class.java))
        refreshUi()
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun requestUsageAccess() {
        try {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } catch (_: Exception) {
        }
    }
}