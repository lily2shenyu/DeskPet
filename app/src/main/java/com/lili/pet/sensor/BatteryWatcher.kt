package com.lili.pet.sensor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build

/**
 * 充电 / 断电 / 低电量感知。
 */
class BatteryWatcher(
    private val onChanged: (level: Int, status: String) -> Unit
) {

    private var receiver: BroadcastReceiver? = null
    private var appContext: Context? = null

    fun register(context: Context) {
        appContext = context.applicationContext
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val pct = if (scale > 0) (level * 100 / scale) else -1
                val status = when (intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
                    BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
                    BatteryManager.BATTERY_STATUS_FULL -> "full"
                    else -> "draining"
                }
                onChanged(pct, status)
            }
        }
        val r = receiver!!
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(r, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(r, filter)
        }
    }

    fun unregister() {
        receiver?.let { r ->
            try {
                appContext?.unregisterReceiver(r)
            } catch (_: Exception) {
            }
        }
        receiver = null
        appContext = null
    }
}