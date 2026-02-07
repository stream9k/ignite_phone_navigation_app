package com.example.ignite

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

class PowerConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("PowerConnectionReceiver", "Received action: $action")

        val prefs = context.getSharedPreferences("CarNaviPrefs", Context.MODE_PRIVATE)
        val isMasterEnabled = prefs.getBoolean("is_master_enabled", true)

        if (!isMasterEnabled) {
            Log.d("PowerConnectionReceiver", "마스터 스위치 OFF: 기능 중단")
            return
        }

        if (Intent.ACTION_POWER_CONNECTED == action || Intent.ACTION_BOOT_COMPLETED == action) {
            Log.d("PowerConnectionReceiver", "전원 연결됨. TmapLaunchService 시작 시도.")

            val serviceIntent = Intent(context, TmapLaunchService::class.java)
            // Android O (API 26) 이상에서는 포그라운드 서비스를 시작해야 함
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            // 기존 종료 타이머가 있다면 취소
            val cancelIntent = Intent(context, MyAccessibilityService::class.java).apply {
                this.action = "CANCEL_SHUTDOWN"
            }
            context.startService(cancelIntent)

        } else if (Intent.ACTION_POWER_DISCONNECTED == action) {
            Log.d("PowerConnectionReceiver", "전원 끊김! 종료 카운트다운 시작")

            val shutdownIntent = Intent(context, MyAccessibilityService::class.java).apply {
                this.action = "START_SHUTDOWN_TIMER"
            }
            context.startService(shutdownIntent)
        }
    }
}
