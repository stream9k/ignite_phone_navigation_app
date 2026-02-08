package com.example.ignite

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.annotation.RequiresApi
import org.json.JSONArray

class MyAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var powerReceiver: BroadcastReceiver? = null
    private lateinit var prefs: SharedPreferences

    private val channelId = "CarNaviChannel"

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
                            if (isAirplaneModeOn()) {
                                Log.d("CarNavi", "비행기 모드 해제 시도")
                                toggleAirplaneMode()
                                handler.postDelayed({ launchTargetApps() }, 4000) // 딜레이 약간 증가
                            } else {
                                launchTargetApps()
                            }
                        } else {
                            Log.d("CarNavi", "마스터 스위치 OFF: 자동 실행 생략")
                        }
                    }
                    Intent.ACTION_POWER_DISCONNECTED -> {
                        Log.d("CarNavi", "❌ 전원 해제됨! (Receiver)")
                        
                        if (isMasterEnabled) {
                            val appDelaySeconds = prefs.getInt("app_shutdown_delay_seconds", 60)
                            val systemDelayMinutes = prefs.getInt("system_shutdown_delay_minutes", 90)
                            val actionType = prefs.getString("action_type", "shutdown")
                            val actionText = when (actionType) {
                                "airplane" -> "비행기 모드 실행"
                                "none" -> "추가 동작 없음"
                                else -> "시스템 종료"
                            }
                            
                            val toastMsg = if (actionType == "none") {
                                "전원 해제: ${appDelaySeconds}초 후 앱 종료"
                            } else {
                                "전원 해제: ${appDelaySeconds}초 후 앱 종료, ${systemDelayMinutes}분 후 ${actionText}"
                            }
                            
                            Toast.makeText(context, toastMsg, Toast.LENGTH_LONG).show()
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_LAUNCH_TMAP_MANUALLY" -> launchTargetApps()
            "ACTION_KILL_ALL_APPS_MANUALLY" -> killAllApps()
            "ACTION_SYSTEM_SHUTDOWN_NOW" -> shutdownSystem()
            "ACTION_TOGGLE_AIRPLANE_MODE_MANUALLY" -> toggleAirplaneMode()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun launchTargetApps() {
        Log.d("CarNavi", "✅ 앱 자동 실행 시도")
        
        val jsonString = prefs.getString("target_app_list", "[]")
        val appList = try { JSONArray(jsonString) } catch (_: Exception) { JSONArray() }
        
        if (appList.length() == 0) {
            val legacyPackage = prefs.getString("target_navi_package", "com.skt.tmap.ku")
            launchApp(legacyPackage ?: "com.skt.tmap.ku")
            return
        }

        for (i in 0 until appList.length()) {
            val item = appList.getJSONObject(i)
            val pkgName = item.optString("package")
            if (pkgName.isNotEmpty()) {
                handler.postDelayed({ launchApp(pkgName) }, (i * 2000).toLong())
            }
        }
    }
    
    private fun launchApp(packageName: String) {
        Log.d("CarNavi", "실행 시도: $packageName")
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                Toast.makeText(this, "$packageName 실행", Toast.LENGTH_SHORT).show()
            } else {
                Log.w("CarNavi", "$packageName 앱을 찾을 수 없음")
            }
        } catch (_: Exception) {
            Log.e("CarNavi", "앱 실행 실패: $packageName")
        }
    }

    private fun createNotificationChannel() {
        val name = "내비 자동 실행 채널"
        val descriptionText = "내비게이션 자동 실행을 위한 알림 채널"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = descriptionText
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun startShutdownTimer() {
        val appDelaySeconds = prefs.getInt("app_shutdown_delay_seconds", 60)
        val appDelayMillis = appDelaySeconds * 1000L
        
        val systemDelayMinutes = prefs.getInt("system_shutdown_delay_minutes", 90)
        val systemDelayMillis = systemDelayMinutes * 60 * 1000L
        
        handler.removeCallbacks(shutdownRunnable)
        handler.postDelayed(shutdownRunnable, appDelayMillis)
        
        val actionType = prefs.getString("action_type", "shutdown")
        
        handler.removeCallbacks(systemShutdownRunnable)
        handler.removeCallbacks(airplaneModeRunnable)
        
        when (actionType) {
            "airplane" -> handler.postDelayed(airplaneModeRunnable, systemDelayMillis)
            "shutdown" -> handler.postDelayed(systemShutdownRunnable, systemDelayMillis)
            "none" -> Log.d("CarNavi", "최종 동작 없음 선택됨. 앱 종료 후 대기.")
        }
    }

    private fun cancelShutdown() {
        handler.removeCallbacks(shutdownRunnable)
        handler.removeCallbacks(systemShutdownRunnable)
        handler.removeCallbacks(airplaneModeRunnable)
    }

    private val shutdownRunnable = Runnable { killAllApps() }
    private val systemShutdownRunnable = Runnable { shutdownSystem() }
    private val airplaneModeRunnable = Runnable { 
        if (!isAirplaneModeOn()) {
            toggleAirplaneMode() 
        }
    }

    private fun killAllApps() {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
        handler.postDelayed({
            val closeTexts = listOf("모두 닫기", "Close all", "모두 지우기", "Clear all")
            var closeAllNode: AccessibilityNodeInfo? = null

            val rootToSearch = rootInActiveWindow ?: windows.lastOrNull()?.root
            
            if (rootToSearch != null) {
                for (text in closeTexts) {
                    closeAllNode = findNodeByText(rootToSearch, text)
                    if (closeAllNode != null) break
                }
            }

            if (closeAllNode != null) {
                closeAllNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, 500)
            } else {
                Log.w("CarNavi", "모두 닫기 버튼을 찾지 못했습니다.")
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }, 2000)
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
    
    private fun toggleAirplaneMode() {
        performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        findAndClickAirplaneButton(3)
    }
    
    private fun findAndClickAirplaneButton(retries: Int) {
        handler.postDelayed({
            if (retries <= 0) {
                Toast.makeText(this, "비행기 모드 버튼을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                performGlobalAction(GLOBAL_ACTION_BACK)
                return@postDelayed
            }

            val airplaneTexts = listOf("비행기", "Airplane", "Flight", "비행기 탑승 모드")
            var airplaneNode: AccessibilityNodeInfo? = null
            val rootNode = rootInActiveWindow
            
            if (rootNode != null) {
                for (text in airplaneTexts) {
                    airplaneNode = findNodeByText(rootNode, text)
                    if (airplaneNode != null) break
                }
            }
                
            if (airplaneNode != null) {
                airplaneNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Toast.makeText(this, "비행기 모드 토글됨", Toast.LENGTH_SHORT).show()
                handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 1000)
            } else {
                if (performScrollAction(rootNode)) {
                    findAndClickAirplaneButton(retries - 1)
                } else {
                    swipeQuickSettings()
                    findAndClickAirplaneButton(retries - 1)
                }
            }
        }, 2000)
    }

    private fun performScrollAction(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        
        val scrollableNodes = ArrayList<AccessibilityNodeInfo>()
        findScrollableActionNodes(root, scrollableNodes)
        
        if (scrollableNodes.isNotEmpty()) {
            for (node in scrollableNodes) {
                if (node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                    Log.d("CarNavi", "논리적 스크롤 성공: ${node.className}")
                    return true
                }
            }
        }
        return false
    }

    private fun findScrollableActionNodes(node: AccessibilityNodeInfo?, list: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        
        if (node.actionList.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)) {
            list.add(node)
        }
        
        for (i in 0 until node.childCount) {
            findScrollableActionNodes(node.getChild(i), list)
        }
    }

    private fun swipeQuickSettings() {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val bounds: Rect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            bounds = windowManager.currentWindowMetrics.bounds
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            bounds = Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        }

        val path = Path()
        path.moveTo((bounds.width() * 0.8f), (bounds.height() * 0.5f))
        path.lineTo((bounds.width() * 0.1f), (bounds.height() * 0.5f))

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 500))
            .build()

        dispatchGesture(gesture, null, null)
    }

    private fun isAirplaneModeOn(): Boolean {
        return Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
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
