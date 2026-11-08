package cz.luna.voice

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val requestCode = 42
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = TextView(this).apply { text = "Luna – hlasový asistent\nAktivační fráze: Ahoj Luno"; textSize = 22f; setPadding(24, 32, 24, 24) }
        val info = TextView(this).apply {
            text = "Vývojová základna aplikace. Zatím ověřuje instalaci, oprávnění a běh služby. Detekce fráze, rozpoznávání řeči a propojení s Open WebUI budou doplněny v dalších krocích."
            textSize = 16f; setPadding(24, 8, 24, 24)
        }
        val start = Button(this).apply { text = "Spustit službu Luny"; setOnClickListener { startLuna() } }
        val stop = Button(this).apply { text = "Zastavit službu"; setOnClickListener { stopService(Intent(this@MainActivity, LunaService::class.java)) } }
        setContentView(LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20,20,20,20); addView(title); addView(info); addView(start); addView(stop) })
    }
    private fun startLuna() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), requestCode); return
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), requestCode + 1); return
        }
        val intent = Intent(this, LunaService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        Toast.makeText(this, "Služba Luny spuštěna", Toast.LENGTH_SHORT).show()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) startLuna()
    }
}
