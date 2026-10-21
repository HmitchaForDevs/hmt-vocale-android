package com.hmtautosolutions.hmttvocale.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.hmtautosolutions.hmttvocale.LocaleHelper
import com.hmtautosolutions.hmttvocale.Prefs
import com.hmtautosolutions.hmttvocale.R
import com.hmtautosolutions.hmttvocale.ui.MainActivity
import com.hmtautosolutions.hmttvocale.wake.WakeWordEngine
import com.hmtautosolutions.hmttvocale.wake.WakeWordEngineFactory

/**
 * Foreground service that owns the microphone while hands-free listening is enabled.
 * Android requires a visible notification while it runs.
 */
class ListeningService : Service(), WakeWordEngine.Listener {

    companion object {
        const val ACTION_START = "com.hmtautosolutions.hmttvocale.START"
        const val ACTION_PAUSE = "com.hmtautosolutions.hmttvocale.PAUSE"
        const val ACTION_RESUME = "com.hmtautosolutions.hmttvocale.RESUME"
        const val CHANNEL_ID = "hmt_listening"
        const val NOTIF_ID = 42

        @Volatile var isRunning = false
            private set
        @Volatile var isListening = false
            private set

        fun start(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx,
                    Intent(ctx, ListeningService::class.java).setAction(ACTION_START))
            } catch (e: Exception) {
                Prefs(ctx).lastError = "Service start failed: ${e.message}"
            }
        }

        fun send(ctx: Context, action: String) {
            if (!isRunning) return
            try {
                ctx.startService(Intent(ctx, ListeningService::class.java).setAction(action))
            } catch (e: Exception) {
                Prefs(ctx).lastError = "Service command failed: ${e.message}"
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, ListeningService::class.java))
        }
    }

    private lateinit var prefs: Prefs
    private var engine: WakeWordEngine? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase, Prefs(newBase).language))
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        isRunning = true
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID,
            getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW))
        startForeground(NOTIF_ID, buildNotification(getString(R.string.notif_text_off)))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> pauseEngine()
            else -> startEngineIfAllowed()
        }
        return START_STICKY
    }

    private fun startEngineIfAllowed() {
        if (!prefs.backgroundListening) { stopSelf(); return }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            updateNotification(getString(R.string.mic_no_permission))
            return
        }
        if (!prefs.wakeWordEnabled) {
            updateNotification(getString(R.string.notif_text_off))
            return
        }
        val e = engine ?: WakeWordEngineFactory.create(this, prefs, this).also { engine = it }
        e.start()
        isListening = true
        updateNotification(getString(R.string.notif_text))
    }

    private fun pauseEngine() {
        engine?.stop()
        isListening = false
        updateNotification(getString(R.string.notif_text_paused))
    }

    override fun onWakeWord(transcript: String) {
        isListening = false
        val i = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_FROM_WAKE, true)
            .putExtra(MainActivity.EXTRA_TRANSCRIPT, transcript)
        try {
            startActivity(i)
        } catch (e: Exception) {
            prefs.lastError = "Could not open assistant: ${e.message}"
            startEngineIfAllowed()
        }
    }

    override fun onEngineError(message: String, fatal: Boolean) {
        prefs.lastError = message
        if (fatal) {
            isListening = false
            updateNotification(message)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pi)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
    }

    override fun onDestroy() {
        engine?.destroy()
        engine = null
        isRunning = false
        isListening = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
