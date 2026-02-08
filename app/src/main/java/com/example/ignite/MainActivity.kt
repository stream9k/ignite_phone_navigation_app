package com.example.ignite

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var editDelaySeconds: EditText
    private lateinit var editSystemDelayMinutes: EditText
    private lateinit var btnSaveSystemDelay: Button
    private lateinit var switchMasterToggle: SwitchCompat
    private lateinit var prefs: SharedPreferences
    private lateinit var radioGroupAction: RadioGroup
    private lateinit var radioShutdown: RadioButton
    private lateinit var radioAirplane: RadioButton
    private lateinit var radioNone: RadioButton
    private lateinit var llAppListContainer: LinearLayout

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
        btnSaveSystemDelay = findViewById(R.id.btnSaveSystemDelay)
        switchMasterToggle = findViewById(R.id.switchMasterToggle)
        radioGroupAction = findViewById(R.id.radioGroupAction)
        radioShutdown = findViewById(R.id.radioShutdown)
        radioAirplane = findViewById(R.id.radioAirplane)
        radioNone = findViewById(R.id.radioNone)
        llAppListContainer = findViewById(R.id.llAppListContainer)

        val savedSeconds = prefs.getInt("app_shutdown_delay_seconds", 60)
        editDelaySeconds.setText(savedSeconds.toString())

        val savedSystemMinutes = prefs.getInt("system_shutdown_delay_minutes", 90)
        editSystemDelayMinutes.setText(savedSystemMinutes.toString())

        val isMasterEnabled = prefs.getBoolean("is_master_enabled", true)
        switchMasterToggle.isChecked = isMasterEnabled

        switchMasterToggle.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean("is_master_enabled", isChecked) }
            val msg = if (isChecked) "자동 기능 활성화됨" else "자동 기능 비활성화됨"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            updateStatus()
        }
        
        migrateLegacyAppData()
        refreshAppListUi()
        
        val actionType = prefs.getString("action_type", "shutdown")
        when (actionType) {
            "airplane" -> radioAirplane.isChecked = true
            "none" -> radioNone.isChecked = true
            else -> radioShutdown.isChecked = true
        }
        
        updateActionDelayUi(actionType ?: "shutdown")
        
        radioGroupAction.setOnCheckedChangeListener { _, checkedId ->
            val type = when (checkedId) {
                R.id.radioAirplane -> "airplane"
                R.id.radioNone -> "none"
                else -> "shutdown"
            }
            prefs.edit { putString("action_type", type) }
            updateActionDelayUi(type)
        }

        setupButtonListeners()
        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }
    
    // --- 앱 목록 관리 로직 ---
    
    private fun getAppList(): JSONArray {
        val jsonString = prefs.getString("target_app_list", "[]")
        return try {
            JSONArray(jsonString)
        } catch (_: Exception) { // 파라미터 e는 사용되지 않으므로 _로 변경
            JSONArray()
        }
    }
    
    private fun saveAppList(list: JSONArray) {
        prefs.edit { putString("target_app_list", list.toString()) }
        refreshAppListUi()
    }
    
    private fun migrateLegacyAppData() {
        val list = getAppList()
        if (list.length() == 0) {
            val legacyPackage = prefs.getString("target_navi_package", null)
            val legacyLabel = prefs.getString("target_navi_label", null)
            
            if (legacyPackage != null) {
                val obj = JSONObject()
                obj.put("package", legacyPackage)
                obj.put("label", legacyLabel ?: "Tmap")
                list.put(obj)
                saveAppList(list)
                
                prefs.edit { 
                    remove("target_navi_package")
                    remove("target_navi_label")
                }
            } else {
                val obj = JSONObject()
                obj.put("package", "com.skt.tmap.ku")
                obj.put("label", "Tmap")
                list.put(obj)
                saveAppList(list)
            }
        }
    }
    
    private fun refreshAppListUi() {
        llAppListContainer.removeAllViews()
        val list = getAppList()
        
        for (i in 0 until list.length()) {
            val item = list.getJSONObject(i)
            val label = item.optString("label", "알 수 없는 앱")
            addAppRow(i, label, i == 0)
        }
    }
    
    private fun addAppRow(index: Int, label: String, isFirst: Boolean) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = 8
        }
        
        // 1. [ - ] 버튼 (첫 번째 항목이 아닐 때만 표시)
        if (!isFirst) {
            val btnMinus = Button(this)
            btnMinus.text = "-"
            // 버튼 크기 조절 (작게)
            val paramsMinus = LinearLayout.LayoutParams(100, LinearLayout.LayoutParams.WRAP_CONTENT)
            paramsMinus.marginEnd = 8
            btnMinus.layoutParams = paramsMinus
            
            btnMinus.setOnClickListener {
                val list = getAppList()
                list.remove(index)
                saveAppList(list)
            }
            row.addView(btnMinus)
        }
        
        // 2. [ 앱 선택 ] 버튼 (가운데, weight 1)
        val btnSelect = Button(this)
        btnSelect.text = label
        btnSelect.backgroundTintList = ColorStateList.valueOf(0xFF673AB7.toInt())
        btnSelect.setTextColor(Color.WHITE) // 글자색 흰색으로 복구
        
        val paramsBtn = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        btnSelect.layoutParams = paramsBtn
        btnSelect.setOnClickListener { showAppPicker(index) }
        
        row.addView(btnSelect)
        
        // 3. [ + ] 버튼 (항상 표시, 맨 오른쪽에 위치)
        val btnPlus = Button(this)
        btnPlus.text = "+"
        val paramsPlus = LinearLayout.LayoutParams(100, LinearLayout.LayoutParams.WRAP_CONTENT)
        paramsPlus.marginStart = 8
        btnPlus.layoutParams = paramsPlus
        
        btnPlus.setOnClickListener {
            val list = getAppList()
            val newItem = JSONObject()
            newItem.put("package", "")
            newItem.put("label", "눌러서 앱 선택")
            list.put(newItem)
            saveAppList(list)
        }
        
        row.addView(btnPlus)
        llAppListContainer.addView(row)
    }

    private fun updateActionDelayUi(actionType: String) {
        val isEnabled = actionType != "none"
        editSystemDelayMinutes.isEnabled = isEnabled
        btnSaveSystemDelay.isEnabled = isEnabled
        
        if (!isEnabled) {
            editSystemDelayMinutes.setText("")
            editSystemDelayMinutes.hint = "-"
        } else {
            val savedSystemMinutes = prefs.getInt("system_shutdown_delay_minutes", 90)
            editSystemDelayMinutes.setText(savedSystemMinutes.toString())
        }
    }

    private fun setupButtonListeners() {
        findViewById<Button>(R.id.btnSaveAppDelay).setOnClickListener { saveAppShutdownDelay() }
        btnSaveSystemDelay.setOnClickListener { saveSystemShutdownDelay() }
        findViewById<Button>(R.id.btnRequestNotification).setOnClickListener { requestNotificationPermission() }
        findViewById<Button>(R.id.btnFullScreenIntent).setOnClickListener { openFullScreenIntentSettings() }
        findViewById<Button>(R.id.btnDrawOverlay).setOnClickListener { openDrawOverlaySettings() }
        
        findViewById<Button>(R.id.btnAccessibility).setOnClickListener { openAccessibilitySettings() }
        findViewById<Button>(R.id.btnRestrictedSettings).setOnClickListener { openAppInfoSettings() }
        findViewById<Button>(R.id.btnBattery).setOnClickListener { openBatterySettings() }
        
        findViewById<Button>(R.id.btnTestNavi).setOnClickListener { testLaunchNavi() }
        findViewById<Button>(R.id.btnTestShutdown).setOnClickListener { testKillAllApps() }
        findViewById<Button>(R.id.btnTestSystemShutdown).setOnClickListener { testSystemShutdown() }
        findViewById<Button>(R.id.btnTestAirplane).setOnClickListener { testAirplaneToggle() }
    }
    
    private fun showAppPicker(index: Int) {
        val mainIntent = Intent(Intent.ACTION_MAIN, null)
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        val pkgAppsList = packageManager.queryIntentActivities(mainIntent, 0)
        
        val appList = pkgAppsList.sortedBy { it.loadLabel(packageManager).toString() }
        val allNames = appList.map { it.loadLabel(packageManager).toString() }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_app_picker, null)
        val etSearch = dialogView.findViewById<EditText>(R.id.et_search)
        val lvApps = dialogView.findViewById<ListView>(R.id.lv_apps)

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList(allNames))
        lvApps.adapter = adapter
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("앱 선택")
            .setView(dialogView)
            .setNegativeButton("취소", null)
            .create()

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.clear()
                val filterText = s.toString().trim()
                if (filterText.isEmpty()) {
                    adapter.addAll(allNames)
                } else {
                    val filteredList = allNames.filter { it.contains(filterText, ignoreCase = true) }
                    adapter.addAll(filteredList)
                }
                adapter.notifyDataSetChanged()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        lvApps.setOnItemClickListener { _, _, position, _ ->
            val selectedName = adapter.getItem(position)
            val selectedApp = appList.firstOrNull { it.loadLabel(packageManager).toString() == selectedName }
            
            if (selectedApp != null) {
                val packageName = selectedApp.activityInfo.packageName
                val label = selectedApp.loadLabel(packageManager).toString()
                
                val list = getAppList()
                val item = list.optJSONObject(index) ?: JSONObject()
                item.put("package", packageName)
                item.put("label", label)
                
                if (index < list.length()) {
                    list.put(index, item)
                } else {
                    list.put(item)
                }
                saveAppList(list)
                
                Toast.makeText(this, "$label 선택됨", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
        
        dialog.show()
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
            Toast.makeText(this, "동작 대기 시간: ${minutes}분 저장됨", Toast.LENGTH_SHORT).show()
            updateStatus()
        } else {
            Toast.makeText(this, "시간(분)을 입력해주세요.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateStatus() {
        val notificationEnabled = hasNotificationPermission()
        val overlayEnabled = canDrawOverlays()
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        val isMasterEnabled = prefs.getBoolean("is_master_enabled", true)
        val airplaneModeOn = isAirplaneModeOn()
        
        var versionName = "Unknown"
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            versionName = pInfo.versionName ?: "Unknown" // Null safety fix
        } catch (_: PackageManager.NameNotFoundException) {
            // e is not used, so it's replaced with _
        }

        val statusMsg = """
            [앱 상태] V$versionName
            - 전체 기능: ${if (isMasterEnabled) "ON" else "OFF"}
            - 알림: ${if (notificationEnabled) "✅" else "❌ (필수)"}
            - 다른 앱 위에 표시: ${if (overlayEnabled) "✅" else "❌ (필수)"}
            - 접근성 서비스: ${if (accessibilityEnabled) "✅" else "❌ (필수)"}
            - 비행기 모드: ${if (airplaneModeOn) "ON" else "OFF"}
        """.trimIndent()
        statusText.text = statusMsg
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun canDrawOverlays(): Boolean {
        return Settings.canDrawOverlays(this)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
            it.id.contains(packageName)
        }
    }

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
        // Play Store 정책 경고는 개인용 앱이므로 기능을 유지합니다.
        startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:$packageName".toUri()))
    }
    
    private fun testLaunchNavi() {
        startService(Intent(this, MyAccessibilityService::class.java).apply { action = "ACTION_LAUNCH_TMAP_MANUALLY" })
    }

    private fun testKillAllApps() {
        startService(Intent(this, MyAccessibilityService::class.java).apply { action = "ACTION_KILL_ALL_APPS_MANUALLY" })
    }

    private fun testSystemShutdown() {
        startService(Intent(this, MyAccessibilityService::class.java).apply { action = "ACTION_SYSTEM_SHUTDOWN_NOW" })
    }
    
    private fun testAirplaneToggle() {
        startService(Intent(this, MyAccessibilityService::class.java).apply { action = "ACTION_TOGGLE_AIRPLANE_MODE_MANUALLY" })
    }
    
    private fun isAirplaneModeOn(): Boolean {
        return Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
    }
}
