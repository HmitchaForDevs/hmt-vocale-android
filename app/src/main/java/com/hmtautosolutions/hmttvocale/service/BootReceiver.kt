package com.hmtautosolutions.hmttvocale.service

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.hmtautosolutions.hmttvocale.Prefs

/** Starts background listening after boot (no window is opened). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != "android.intent.action.QUICKBOOT_POWERON") return
        val prefs = Prefs(context)
        if (!prefs.setupDone || !prefs.autoStartOnBoot || !prefs.backgroundListening) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) return
        ListeningService.start(context)
    }
}
