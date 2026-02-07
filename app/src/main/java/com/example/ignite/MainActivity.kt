package com.example.ignite

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var editDelaySeconds: EditText
    private lateinit var editSystemDelayMinutes: EditText
    private lateinit var switchMasterToggle: SwitchCompat
    private lateinit var prefs: SharedPreferences

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "알림 권한이 허용되었습니다.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "알림 권한이 거부되었습니다.", Toast.LENGTH_LONG).show()
        }
        updateStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("CarNaviPrefs", Context.MODE_PRIVATE)
        statusText = findViewById(R.id.statusText)
        editDelaySeconds = findViewById(R.id.editDelaySeconds)
        editSystemDelayMinutes = findViewById(R.id.editSystemDelayMinutes)
        switchMasterToggle = findViewById(R.id.switchMasterToggle)

        val savedSeconds = prefs.getInt("app_shutdown_delay_seconds", 60)
        editDelaySeconds.setText(savedSeconds.toString())

        val savedSystemMinutes = prefs.getInt("system_shutdown_delay_minutes", 90)
        editSystemDelayMinutes.setText(savedSystemMinutes.toString())

        // 마스터 스위치 상태 불러오기
        val isMasterEnabled = prefs.getBoolean("is_master_enabled", true)
        switchMasterToggle.isChecked = isMasterEnabled

        // 마스터 스위치 리스너
        switchMasterToggle.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean("is_master_enabled", isChecked) }
            val msg = if (isChecked) "자동 기능 활성화됨" else "자동 기능 비활성화됨"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            updateStatus()
        }

        setupButtonListeners()
        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun setupButtonListeners() {
        findViewById<Button>(R.id.btnSaveAppDelay).setOnClickListener { saveAppShutdownDelay() }
        findViewById<Button>(R.id.btnSaveSystemDelay).setOnClickListener { saveSystemShutdownDelay() }
        findViewById<Button>(R.id.btnRequestNotification).setOnClickListener { requestNotificationPermission() }
        findViewById<Button>(R.id.btnFullScreenIntent).setOnClickListener { openFullScreenIntentSettings() }
        findViewById<Button>(R.id.btnDrawOverlay).setOnClickListener { openDrawOverlaySettings() }
        
        // 접근성 설정 버튼
        findViewById<Button>(R.id.btnAccessibility).setOnClickListener { openAccessibilitySettings() }
        
        // 제한된 설정 허용 버튼 (앱 정보 열기)
        findViewById<Button>(R.id.btnRestrictedSettings).setOnClickListener { openAppInfoSettings() }
        
        findViewById<Button>(R.id.btnBattery).setOnClickListener { openBatterySettings() }
        findViewById<Button>(R.id.btnTestNavi).setOnClickListener { testLaunchNavi() }
        findViewById<Button>(R.id.btnTestShutdown).setOnClickListener { testKillAllApps() }
        findViewById<Button>(R.id.btnTestSystemShutdown).setOnClickListener { testSystemShutdown() }
    }

    private fun saveAppShutdownDelay() {
        val secondsText = editDelaySeconds.text.toString()
        if (secondsText.isNotEmpty()) {
            val seconds = secondsText.toInt()
            prefs.edit { putInt("app_shutdown_delay_seconds", seconds) }
            Toast.makeText(this, "앱 종료 대기 시간: ${seconds}초 저장됨", Toast.LENGTH_SHORT).show()
            updateStatus()
        } else {
            Toast.makeText(this, "시간(초)을 입력해주세요.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveSystemShutdownDelay() {
        val minutesText = editSystemDelayMinutes.text.toString()
        if (minutesText.isNotEmpty()) {
            val minutes = minutesText.toInt()
            prefs.edit { putInt("system_shutdown_delay_minutes", minutes) }
            Toast.makeText(this, "시스템 종료 대기 시간: ${minutes}분 저장됨", Toast.LENGTH_SHORT).show()
            updateStatus()
        } else {
            Toast.makeText(this, "시간(분)을 입력해주세요.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateStatus() {
        val notificationEnabled = hasNotificationPermission()
        val overlayEnabled = canDrawOverlays()
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        val appDelay = prefs.getInt("app_shutdown_delay_seconds", 60)
        val systemDelay = prefs.getInt("system_shutdown_delay_minutes", 90)
        val isMasterEnabled = prefs.getBoolean("is_master_enabled", true)

        val statusMsg = """
            [앱 상태]
            - 전체 기능: ${if (isMasterEnabled) "ON" else "OFF"}
            - 알림: ${if (notificationEnabled) "✅" else "❌ (필수)"}
            - 다른 앱 위에 표시: ${if (overlayEnabled) "✅" else "❌ (필수)"}
            - 접근성 서비스: ${if (accessibilityEnabled) "✅" else "❌ (필수)"}
            - 앱 자동 종료: ${appDelay}초 후
            - 시스템 자동 종료: ${systemDelay}분 후
        """.trimIndent()
        statusText.text = statusMsg
    }

    // --- 권한 확인 함수들 ---
    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun canDrawOverlays(): Boolean {
        // minSdk가 28이므로 Build.VERSION.SDK_INT >= M (23) 체크 불필요
        return Settings.canDrawOverlays(this)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
            it.id.contains(packageName)
        }
    }

    // --- 설정 화면 열기 함수들 ---
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasNotificationPermission()) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                Toast.makeText(this, "알림 권한이 이미 허용되어 있습니다.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "이 기기(Android 12 이하)에서는 별도의 알림 권한이 필요하지 않습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openFullScreenIntentSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:$packageName".toUri())
        } else { // Android 10-13
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
        }
        try {
            startActivity(intent)
            Toast.makeText(this, "'Ignite'의 전체 화면 알림 관련 스위치를 켜주세요.", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(this, "설정 화면을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openDrawOverlaySettings() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri()))
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    // 제한된 설정 허용을 위해 앱 정보 화면으로 이동
    private fun openAppInfoSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = "package:$packageName".toUri()
            startActivity(intent)
            Toast.makeText(this, "우측 상단 메뉴 [⋮] -> [제한된 설정 허용]을 눌러주세요.", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(this, "설정 화면 이동 실패", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openBatterySettings() {
        // REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 정책 위반 경고는 개인용 앱이므로 무시합니다.
        startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:$packageName".toUri()))
    }
    
    // --- 테스트 함수들 ---
    private fun testLaunchNavi() {
        startService(Intent(this, MyAccessibilityService::class.java).apply { action = "ACTION_LAUNCH_TMAP_MANUALLY" })
    }

    private fun testKillAllApps() {
        startService(Intent(this, MyAccessibilityService::class.java).apply { action = "ACTION_KILL_ALL_APPS_MANUALLY" })
    }

    private fun testSystemShutdown() {
        startService(Intent(this, MyAccessibilityService::class.java).apply { action = "ACTION_SYSTEM_SHUTDOWN_NOW" })
    }
}
