package com.example.ignite

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.core.app.NotificationCompat

class MyAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var powerReceiver: BroadcastReceiver? = null
    private lateinit var prefs: SharedPreferences

    private val CHANNEL_ID = "CarNaviChannel"

    // [전원 감지 로직]
    private fun registerPowerReceiver() {
        if (powerReceiver != null) return

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }

        powerReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val isMasterEnabled = prefs.getBoolean("is_master_enabled", true)

                when (intent?.action) {
                    Intent.ACTION_POWER_CONNECTED -> {
                        Log.d("CarNavi", "✅ 전원 연결됨! (Receiver)")
                        cancelShutdown()
                        
                        if (isMasterEnabled) {
                            launchTmap() // 변경된 Tmap 실행 함수 호출
                        } else {
                            Log.d("CarNavi", "마스터 스위치 OFF: Tmap 자동 실행 생략")
                        }
                    }
                    Intent.ACTION_POWER_DISCONNECTED -> {
                        Log.d("CarNavi", "❌ 전원 해제됨! (Receiver)")
                        
                        if (isMasterEnabled) {
                            val appDelaySeconds = prefs.getInt("app_shutdown_delay_seconds", 60)
                            val systemDelayMinutes = prefs.getInt("system_shutdown_delay_minutes", 90)
                            Toast.makeText(context, "전원 해제: ${appDelaySeconds}초 후 앱 종료, ${systemDelayMinutes}분 후 시스템 종료", Toast.LENGTH_LONG).show()
                            startShutdownTimer()
                        } else {
                            Log.d("CarNavi", "마스터 스위치 OFF: 자동 종료 타이머 생략")
                        }
                    }
                }
            }
        }
        registerReceiver(powerReceiver, filter)
        Log.d("CarNavi", "전원 감지 리시버 등록 완료")
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("CarNaviPrefs", Context.MODE_PRIVATE)
        createNotificationChannel()
        registerPowerReceiver()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (powerReceiver != null) {
            unregisterReceiver(powerReceiver)
            powerReceiver = null
        }
    }

    // MainActivity로부터 명령을 받아서 처리하는 부분
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_LAUNCH_TMAP_MANUALLY" -> launchTmap()
            "ACTION_KILL_ALL_APPS_MANUALLY" -> killAllApps()
            "ACTION_SYSTEM_SHUTDOWN_NOW" -> shutdownSystem()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    // [변경] 알림 대신 직접 Tmap 실행
    private fun launchTmap() {
        Log.d("CarNavi", "✅ Tmap 직접 실행 시도")
        Toast.makeText(this, "Tmap 실행을 준비합니다...", Toast.LENGTH_SHORT).show()

        try {
            val intent = Intent(this, LaunchTrampolineActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("CarNavi", "❌ Tmap 실행 실패", e)
            Toast.makeText(this, "Tmap 실행 실패: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Tmap 자동 실행 채널"
            val descriptionText = "Tmap 자동 실행을 위한 알림 채널"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // 타이머 및 종료 관련 함수들은 기존과 동일...
    private fun startShutdownTimer() {
        val appDelaySeconds = prefs.getInt("app_shutdown_delay_seconds", 60)
        val appDelayMillis = appDelaySeconds * 1000L
        
        val systemDelayMinutes = prefs.getInt("system_shutdown_delay_minutes", 90)
        val systemDelayMillis = systemDelayMinutes * 60 * 1000L
        
        handler.removeCallbacks(shutdownRunnable)
        handler.postDelayed(shutdownRunnable, appDelayMillis)
        
        handler.removeCallbacks(systemShutdownRunnable)
        handler.postDelayed(systemShutdownRunnable, systemDelayMillis)
    }

    private fun cancelShutdown() {
        handler.removeCallbacks(shutdownRunnable)
        handler.removeCallbacks(systemShutdownRunnable)
    }

    private val shutdownRunnable = Runnable { killAllApps() }
    private val systemShutdownRunnable = Runnable { shutdownSystem() }

    private fun killAllApps() {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
        handler.postDelayed({
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                val closeAllNode = findNodeByText(rootNode, "모두 닫기") ?: findNodeByText(rootNode, "Close all")
                if (closeAllNode != null) {
                    closeAllNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, 500)
                } else {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            } else {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }, 1500)
    }

    private fun shutdownSystem() {
        performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
        handler.postDelayed({
            val rootNode = rootInActiveWindow
            val powerOffNode = findNodeByText(rootNode, "전원 끄기") ?: findNodeByText(rootNode, "종료") ?: findNodeByText(rootNode, "Power off")
            if (powerOffNode != null) {
                powerOffNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                handler.postDelayed({
                    val confirmRootNode = rootInActiveWindow
                    val confirmNode = findNodeByText(confirmRootNode, "전원 끄기") ?: findNodeByText(confirmRootNode, "종료")
                    confirmNode?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }, 1000)
            } else {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }, 1500)
    }

    private fun findNodeByText(root: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (root == null) return null
        val nodes = root.findAccessibilityNodeInfosByText(text)
        for (node in nodes) {
            var clickableNode: AccessibilityNodeInfo? = node
            while (clickableNode != null) {
                if (clickableNode.isClickable) return clickableNode
                clickableNode = clickableNode.parent
            }
        }
        return null
    }

    override fun onInterrupt() {}
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
}
