package il.calllock

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Prefs.isEnabled(context)) return
        val phoneOk = context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
        if (!phoneOk || !Settings.canDrawOverlays(context)) return
        try {
            context.startForegroundService(Intent(context, CallLockService::class.java))
        } catch (_: Exception) {
        }
    }
}
