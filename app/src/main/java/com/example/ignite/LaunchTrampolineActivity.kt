package com.example.ignite

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast

class LaunchTrampolineActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("CarNavi", "LaunchTrampolineActivity 시작됨")
        
        val prefs = getSharedPreferences("CarNaviPrefs", Context.MODE_PRIVATE)
        val targetPackage = prefs.getString("target_navi_package", "com.skt.tmap.ku") ?: "com.skt.tmap.ku"
        
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                Log.d("CarNavi", "$targetPackage 실행 인텐트 전송 성공")
            } else {
                Toast.makeText(this, "선택한 앱($targetPackage)이 설치되어 있지 않습니다.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e("CarNavi", "앱 실행 실패", e)
            Toast.makeText(this, "앱 실행 실패: ${e.message}", Toast.LENGTH_LONG).show()
        }
        finish()
    }
}
