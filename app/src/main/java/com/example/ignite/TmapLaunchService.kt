package com.example.ignite

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat

class TmapLaunchService : Service() {

    companion object {
        const val CHANNEL_ID = "TmapLaunchServiceChannel"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("TmapLaunchService", "서비스 시작됨")
        createNotificationChannel()

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ignite 서비스 실행 중")
            .setContentText("Tmap 실행을 준비합니다.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()

        startForeground(1, notification)

        // Tmap 실행 로직
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage("com.skt.tmap.ku")
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                Log.d("TmapLaunchService", "Tmap 실행 성공")
            } else {
                Log.w("TmapLaunchService", "Tmap이 설치되어 있지 않습니다.")
                Toast.makeText(applicationContext, "Tmap 앱을 찾을 수 없습니다.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e("TmapLaunchService", "Tmap 실행 중 오류 발생", e)
            Toast.makeText(applicationContext, "Tmap 실행에 실패했습니다.", Toast.LENGTH_LONG).show()
        } finally {
            // 작업 완료 후 서비스 종료
            stopSelf()
            Log.d("TmapLaunchService", "서비스 종료됨")
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Tmap 실행 서비스 채널",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
