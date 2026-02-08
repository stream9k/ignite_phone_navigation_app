package com.example.ignite

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import org.json.JSONArray

class TmapLaunchService : Service() {

    companion object {
        const val CHANNEL_ID = "TmapLaunchServiceChannel"
    }
    
    private val handler = Handler(Looper.getMainLooper())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("TmapLaunchService", "서비스 시작됨")
        createNotificationChannel()

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ignite 서비스 실행 중")
            .setContentText("앱 자동 실행을 준비합니다.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()

        startForeground(1, notification)

        launchTargetApps()

        return START_NOT_STICKY
    }

    private fun launchTargetApps() {
        val prefs = getSharedPreferences("CarNaviPrefs", Context.MODE_PRIVATE)
        val jsonString = prefs.getString("target_app_list", "[]")
        val appList = try {
            JSONArray(jsonString)
        } catch (e: Exception) {
            JSONArray()
        }

        if (appList.length() == 0) {
            val legacyPackage = prefs.getString("target_navi_package", "com.skt.tmap.ku")
            launchApp(legacyPackage ?: "com.skt.tmap.ku")
            handler.postDelayed({ stopSelf() }, 2000)
            return
        }

        for (i in 0 until appList.length()) {
            val item = appList.getJSONObject(i)
            val pkgName = item.optString("package")
            if (pkgName.isNotEmpty()) {
                handler.postDelayed({
                    launchApp(pkgName)
                }, (i * 2000).toLong())
            }
        }
        
        val totalDelay = (appList.length() - 1) * 2000L + 2000L
        handler.postDelayed({ stopSelf() }, totalDelay)
    }
    
    private fun launchApp(packageName: String) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                Log.d("TmapLaunchService", "$packageName 실행 성공")
            } else {
                Log.w("TmapLaunchService", "$packageName 설치되어 있지 않습니다.")
            }
        } catch (e: Exception) {
            Log.e("TmapLaunchService", "실행 오류", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "앱 실행 서비스 채널",
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
