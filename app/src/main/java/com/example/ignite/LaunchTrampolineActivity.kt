package com.example.ignite

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast

class LaunchTrampolineActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("CarNavi", "LaunchTrampolineActivity 시작됨")
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage("com.skt.tmap.ku")
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                Log.d("CarNavi", "Tmap 실행 인텐트 전송 성공")
            } else {
                Toast.makeText(this, "Tmap이 설치되어 있지 않습니다.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e("CarNavi", "Tmap 실행 실패", e)
        }
        finish()
    }
}
