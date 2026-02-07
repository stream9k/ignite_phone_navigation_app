package com.example.ignite  // 주의: ignite 패키지여야 함

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log

class PowerConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("CarNavi", "Received action: $action")

        if (Intent.ACTION_POWER_CONNECTED == action || Intent.ACTION_BOOT_COMPLETED == action) {
            Log.d("CarNavi", "전원 연결됨! 네비 실행 시도")

            // 1. 화면 깨우기
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "CarNavi:WakeLock"
            )
            wakeLock.acquire(3000) // 3초간 화면 켜기 유지

            // 2. Tmap 실행 (패키지명: com.skt.tmap.ku)
            try {
                // Tmap이 없으면 다른거라도 실행하게 예외처리 필요하지만 일단 Tmap 고정
                val launchIntent = context.packageManager.getLaunchIntentForPackage("com.skt.tmap.ku")
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                } else {
                    Log.e("CarNavi", "Tmap 설치 안됨")
                    // 테스트용으로 유튜브라도 켜볼까요? (원하시면 주석 해제)
                    // val testIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
                    // context.startActivity(testIntent)
                }
            } catch (e: Exception) {
                Log.e("CarNavi", "앱 실행 실패: ${e.message}")
            }

            // 종료 타이머 취소 요청 (서비스가 실행중이라면)
            val serviceIntent = Intent(context, MyAccessibilityService::class.java)
            serviceIntent.action = "CANCEL_SHUTDOWN"
            try { context.startService(serviceIntent) } catch(e: Exception){}

        } else if (Intent.ACTION_POWER_DISCONNECTED == action) {
            Log.d("CarNavi", "전원 끊김! 20초 후 종료 카운트다운 시작")

            // 3. 서비스에 신호를 보내 20초 뒤 종료 로직 수행
            val serviceIntent = Intent(context, MyAccessibilityService::class.java)
            serviceIntent.action = "START_SHUTDOWN_TIMER"

            // 안드로이드 8.0 이상에서는 startForegroundService를 써야 할 수도 있으나,
            // AccessibilityService는 이미 시스템 서비스라 startService로 신호 전달 가능
            try {
                context.startService(serviceIntent)
            } catch (e: Exception) {
                Log.e("CarNavi", "서비스 시작 실패 (권한 확인 필요): ${e.message}")
            }
        }
    }
}