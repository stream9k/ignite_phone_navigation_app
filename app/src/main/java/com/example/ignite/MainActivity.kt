package com.example.ignite

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var editDelayMinutes: EditText
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // SharedPreferences 초기화 (설정값 저장용)
        prefs = getSharedPreferences("CarNaviPrefs", Context.MODE_PRIVATE)

        statusText = findViewById(R.id.statusText)
        editDelayMinutes = findViewById(R.id.editDelayMinutes)

        // 저장된 시간 불러와서 EditText에 표시
        val savedMinutes = prefs.getInt("shutdown_delay_minutes", 1) // 기본값 1분
        editDelayMinutes.setText(savedMinutes.toString())

        // 저장 버튼 리스너
        findViewById<Button>(R.id.btnSaveDelay).setOnClickListener {
            val minutesText = editDelayMinutes.text.toString()
            if (minutesText.isNotEmpty()) {
                val minutes = minutesText.toInt()
                prefs.edit().putInt("shutdown_delay_minutes", minutes).apply()
                Toast.makeText(this, "자동 종료 시간이 ${minutes}분으로 저장되었습니다.", Toast.LENGTH_SHORT).show()
                updateStatus() // 상태 텍스트 업데이트
            } else {
                Toast.makeText(this, "시간을 입력해주세요.", Toast.LENGTH_SHORT).show()
            }
        }

        // 나머지 버튼들 설정
        findViewById<Button>(R.id.btnAccessibility).setOnClickListener { openAccessibilitySettings() }
        findViewById<Button>(R.id.btnBattery).setOnClickListener { openBatterySettings() }
        findViewById<Button>(R.id.btnTestNavi).setOnClickListener { testLaunchNavi() }
        findViewById<Button>(R.id.btnTestShutdown).setOnClickListener { testShutdownTimer() }

        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        val batteryOptimized = isBatteryOptimizationEnabled()
        val currentDelay = prefs.getInt("shutdown_delay_minutes", 1)

        val statusMsg = """
            [앱 상태]
            - 접근성 서비스: ${if (accessibilityEnabled) "✅ 켜짐" else "❌ 꺼짐 (필수)"}
            - 배터리 최적화: ${if (batteryOptimized) "⚠️ 제한 중 (해제 권장)" else "✅ 해제됨"}
            - 자동 종료 시간: ${currentDelay}분
        """.trimIndent()
        statusText.text = statusMsg
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
            it.id.contains(packageName)
        }
    }

    private fun isBatteryOptimizationEnabled(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return !pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openBatterySettings() {
        // 배터리 최적화 해제 화면으로 바로 이동 시도
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } catch (e: Exception) {
            // 실패 시 일반 앱 설정 화면으로 이동
            Toast.makeText(this, "배터리 최적화 메뉴를 직접 찾아주세요.", Toast.LENGTH_SHORT).show()
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }

    // --- 테스트용 함수들 ---
    private fun testLaunchNavi() {
        Toast.makeText(this, "Tmap 실행 테스트!", Toast.LENGTH_SHORT).show()
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage("com.skt.tmap.ku")
            launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "Tmap 실행 실패: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun testShutdownTimer() {
        val minutes = prefs.getInt("shutdown_delay_minutes", 1)
        Toast.makeText(this, "테스트: ${minutes}분 후 모든 앱 종료 시작!", Toast.LENGTH_SHORT).show()
        val serviceIntent = Intent(this, MyAccessibilityService::class.java)
        serviceIntent.action = "START_SHUTDOWN_TIMER"
        try {
            startService(serviceIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "서비스 시작 실패 (접근성 권한 확인)", Toast.LENGTH_LONG).show()
        }
    }
}
