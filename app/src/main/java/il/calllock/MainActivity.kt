package il.calllock

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

class MainActivity : AppCompatActivity() {

    private lateinit var switchService: MaterialSwitch
    private lateinit var statusOverlay: TextView
    private lateinit var statusPhone: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        switchService = findViewById(R.id.switchService)
        statusOverlay = findViewById(R.id.statusOverlay)
        statusPhone = findViewById(R.id.statusPhone)

        findViewById<MaterialButton>(R.id.btnOverlay).setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
        findViewById<MaterialButton>(R.id.btnPhone).setOnClickListener {
            val perms = mutableListOf(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
            requestPermissions(perms.toTypedArray(), 1)
        }
        findViewById<MaterialButton>(R.id.btnBattery).setOnClickListener {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }

        switchService.setOnClickListener { onServiceSwitchClicked() }

        val switchCard = findViewById<MaterialSwitch>(R.id.switchCard)
        switchCard.isChecked = Prefs.showCard(this)
        switchCard.setOnCheckedChangeListener { _, checked -> Prefs.setShowCard(this, checked) }

        val slider = findViewById<Slider>(R.id.sliderAlpha)
        slider.value = Prefs.cardAlpha(this).coerceIn(0, 100).toFloat()
        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) Prefs.setCardAlpha(this, value.toInt())
        }

        findViewById<MaterialButton>(R.id.btnResetLock).setOnClickListener {
            Prefs.resetLockPos(this)
            Toast.makeText(this, "מיקום המנעול אופס", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    private fun phoneOk() =
        checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    private fun notifOk() =
        Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun overlayOk() = Settings.canDrawOverlays(this)

    private fun refresh() {
        statusOverlay.text = if (overlayOk()) "✓ מאושר" else "✗ לא מאושר"
        statusPhone.text =
            (if (phoneOk()) "✓ מצב טלפון" else "✗ מצב טלפון") + "\n" +
                (if (notifOk()) "✓ התראות" else "✗ התראות")
        switchService.isChecked = Prefs.isEnabled(this)
    }

    private fun onServiceSwitchClicked() {
        if (switchService.isChecked) {
            if (!overlayOk() || !phoneOk()) {
                switchService.isChecked = false
                Toast.makeText(this, "חסרות הרשאות. אשר את ההרשאות שלמעלה", Toast.LENGTH_LONG).show()
                return
            }
            Prefs.setEnabled(this, true)
            startForegroundService(Intent(this, CallLockService::class.java))
        } else {
            Prefs.setEnabled(this, false)
            stopService(Intent(this, CallLockService::class.java))
        }
    }
}
