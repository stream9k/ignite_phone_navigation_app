package com.example.ignite

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class MyAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var powerReceiver: BroadcastReceiver? = null
    private lateinit var prefs: SharedPreferences

    // [전원 감지 로직]
    private fun registerPowerReceiver() {
        if (powerReceiver != null) return

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }

        powerReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_POWER_CONNECTED -> {
                        Log.d("CarNavi", "✅ 전원 연결됨! (Receiver)")
                        Toast.makeText(context, "전원 연결: Tmap 실행", Toast.LENGTH_SHORT).show()
                        cancelShutdown()
                        launchTmap()
                    }
                    Intent.ACTION_POWER_DISCONNECTED -> {
                        val minutes = prefs.getInt("shutdown_delay_minutes", 1)
                        Log.d("CarNavi", "❌ 전원 해제됨! (Receiver)")
                        Toast.makeText(context, "전원 해제: ${minutes}분 후 모든 앱 종료", Toast.LENGTH_SHORT).show()
                        startShutdownTimer()
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
            "START_SHUTDOWN_TIMER" -> {
                Toast.makeText(this, "(테스트) 모든 앱 종료 타이머 시작", Toast.LENGTH_SHORT).show()
                startShutdownTimer()
            }
            "ACTION_SYSTEM_SHUTDOWN_NOW" -> {
                Toast.makeText(this, "(테스트) 시스템 즉시 종료", Toast.LENGTH_SHORT).show()
                shutdownSystem()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }


    // [Tmap 실행 함수]
    private fun launchTmap() {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage("com.skt.tmap.ku")
            launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        } catch (e: Exception) {
            Log.e("CarNavi", "Tmap 실행 실패", e)
        }
    }

    // [타이머 관련 함수]
    private fun startShutdownTimer() {
        val delayMinutes = prefs.getInt("shutdown_delay_minutes", 1)
        val delayMillis = delayMinutes * 60 * 1000L

        handler.removeCallbacks(shutdownRunnable)
        handler.postDelayed(shutdownRunnable, delayMillis)

        handler.removeCallbacks(systemShutdownRunnable)
        handler.postDelayed(systemShutdownRunnable, 5400 * 1000)
    }

    private fun cancelShutdown() {
        handler.removeCallbacks(shutdownRunnable)
        handler.removeCallbacks(systemShutdownRunnable)
    }

    // 모든 앱 종료 타이머
    private val shutdownRunnable = Runnable {
        Log.d("CarNavi", "설정 시간 경과: 모든 앱 종료 시도")
        killAllApps()
    }

    // 시스템 종료 타이머
    private val systemShutdownRunnable = Runnable {
        Log.d("CarNavi", "90분 경과: 시스템 종료 시도")
        shutdownSystem()
    }

    // [모든 앱 종료 기능]
    private fun killAllApps() {
        Toast.makeText(applicationContext, "모든 앱 종료 중...", Toast.LENGTH_SHORT).show()
        performGlobalAction(GLOBAL_ACTION_RECENTS)

        handler.postDelayed({
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                val closeAllNode = findNodeByText(rootNode, "모두 닫기")
                    ?: findNodeByText(rootNode, "Close all")

                if (closeAllNode != null) {
                    Log.d("CarNavi", "✅ '모두 닫기' 버튼 찾음! 클릭!")
                    closeAllNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, 500)
                } else {
                    Log.e("CarNavi", "❌ '모두 닫기' 버튼을 못 찾음")
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            } else {
                Log.e("CarNavi", "❌ 화면 정보를 가져올 수 없음")
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }, 1500)
    }

    // [시스템 종료 기능]
    private fun shutdownSystem() {
        Toast.makeText(applicationContext, "시스템 종료를 시도합니다.", Toast.LENGTH_LONG).show()
        performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)

        handler.postDelayed({
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                val powerOffNode = findNodeByText(rootNode, "전원 끄기")
                    ?: findNodeByText(rootNode, "종료")
                    ?: findNodeByText(rootNode, "Power off")

                if (powerOffNode != null) {
                    Log.d("CarNavi", "✅ '전원 끄기' 버튼 찾음! 클릭!")
                    powerOffNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)

                    handler.postDelayed({
                        val confirmRootNode = rootInActiveWindow
                        val confirmNode = findNodeByText(confirmRootNode, "전원 끄기")
                            ?: findNodeByText(confirmRootNode, "종료")
                        confirmNode?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }, 1000)

                } else {
                    Log.e("CarNavi", "❌ '전원 끄기' 버튼을 찾지 못했습니다.")
                    Toast.makeText(applicationContext, "'전원 끄기' 버튼을 찾지 못했습니다.", Toast.LENGTH_SHORT).show()
                    performGlobalAction(GLOBAL_ACTION_HOME) 
                }
            } else {
                Log.e("CarNavi", "❌ 전원 메뉴 화면 정보를 가져올 수 없음")
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
