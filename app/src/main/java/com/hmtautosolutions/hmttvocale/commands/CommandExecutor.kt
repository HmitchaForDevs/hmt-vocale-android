package com.hmtautosolutions.hmttvocale.commands

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.text.format.DateFormat
import android.view.KeyEvent
import com.hmtautosolutions.hmttvocale.R
import com.hmtautosolutions.hmttvocale.nlu.AppCatalog
import com.hmtautosolutions.hmttvocale.nlu.CommandIntent
import com.hmtautosolutions.hmttvocale.nlu.IntentType
import com.hmtautosolutions.hmttvocale.vehicle.VehicleAction
import com.hmtautosolutions.hmttvocale.vehicle.VehicleCommandProvider
import com.hmtautosolutions.hmttvocale.vehicle.VehicleResultStatus
import java.util.Date

enum class OutcomeStatus { SUCCESS, SENT_UNVERIFIED, SIMULATED, NOT_SUPPORTED, FAILED, NEEDS_CONFIRMATION, CLARIFY, REFUSED, INFO }

data class Outcome(val status: OutcomeStatus, val message: String) {
    val isPositive: Boolean
        get() = status != OutcomeStatus.FAILED && status != OutcomeStatus.NOT_SUPPORTED && status != OutcomeStatus.REFUSED
}

/**
 * Executes understood intents with real Android APIs. Responses describe exactly what happened:
 * "sent" when the result cannot be verified, "simulated" in simulation mode, never fake success.
 * [ctx] must be a context localized to the user's language.
 */
class CommandExecutor(private val ctx: Context, private val vehicle: VehicleCommandProvider) {

    private val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val stream = AudioManager.STREAM_MUSIC

    fun execute(intent: CommandIntent, confirmed: Boolean): Outcome = try {
        when (intent.type) {
            IntentType.OPEN_APP -> openApp(intent.params["app"].orEmpty())
            IntentType.NAVIGATE -> navigate(intent.params["destination"].orEmpty())
            IntentType.MEDIA_PLAY -> media(KeyEvent.KEYCODE_MEDIA_PLAY)
            IntentType.MEDIA_PAUSE -> media(KeyEvent.KEYCODE_MEDIA_PAUSE)
            IntentType.MEDIA_NEXT -> media(KeyEvent.KEYCODE_MEDIA_NEXT)
            IntentType.MEDIA_PREVIOUS -> media(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            IntentType.VOLUME_UP -> adjustVolume(AudioManager.ADJUST_RAISE)
            IntentType.VOLUME_DOWN -> adjustVolume(AudioManager.ADJUST_LOWER)
            IntentType.VOLUME_MUTE -> mute()
            IntentType.VOLUME_SET -> setVolume(intent.params["value"]?.toIntOrNull() ?: 50)
            IntentType.IDENTITY -> Outcome(OutcomeStatus.INFO, s(R.string.resp_identity))
            IntentType.TIME -> Outcome(OutcomeStatus.INFO,
                ctx.getString(R.string.resp_time, DateFormat.getTimeFormat(ctx).format(Date())))
            IntentType.HELP -> Outcome(OutcomeStatus.INFO, s(R.string.resp_help))
            IntentType.CLIMATE_ON -> vehicleCmd(VehicleAction.CLIMATE_ON, null, confirmed)
            IntentType.CLIMATE_OFF -> vehicleCmd(VehicleAction.CLIMATE_OFF, null, confirmed)
            IntentType.TEMP_SET -> vehicleCmd(VehicleAction.TEMP_SET, intent.params["value"]?.toIntOrNull(), confirmed)
            IntentType.TEMP_UP -> vehicleCmd(VehicleAction.TEMP_UP, null, confirmed)
            IntentType.TEMP_DOWN -> vehicleCmd(VehicleAction.TEMP_DOWN, null, confirmed)
            IntentType.FAN_UP -> vehicleCmd(VehicleAction.FAN_UP, null, confirmed)
            IntentType.FAN_DOWN -> vehicleCmd(VehicleAction.FAN_DOWN, null, confirmed)
            IntentType.WINDOWS_OPEN -> vehicleCmd(VehicleAction.WINDOWS_OPEN, null, confirmed)
            IntentType.WINDOWS_CLOSE -> vehicleCmd(VehicleAction.WINDOWS_CLOSE, null, confirmed)
            IntentType.UNSAFE -> Outcome(OutcomeStatus.REFUSED, s(R.string.resp_unsafe))
            IntentType.CHANGE_LANGUAGE, IntentType.UNKNOWN -> Outcome(OutcomeStatus.CLARIFY, s(R.string.resp_unknown))
        }
    } catch (e: Exception) {
        Outcome(OutcomeStatus.FAILED, ctx.getString(R.string.resp_failed, e.message ?: e.javaClass.simpleName))
    }

    private fun s(id: Int) = ctx.getString(id)

    private fun openApp(key: String): Outcome {
        val app = AppCatalog.byKey(key) ?: return Outcome(OutcomeStatus.CLARIFY, s(R.string.resp_unknown))
        val name = app.displayName
        val launch: Intent? = when {
            app.openSystemSettings -> Intent(Settings.ACTION_SETTINGS)
            app.category != null -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, app.category)
            else -> app.packages.firstNotNullOfOrNull { ctx.packageManager.getLaunchIntentForPackage(it) }
        }
        if (launch == null) return Outcome(OutcomeStatus.NOT_SUPPORTED, ctx.getString(R.string.resp_app_missing, name))
        return try {
            ctx.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Outcome(OutcomeStatus.SUCCESS, ctx.getString(R.string.resp_app_opened, name))
        } catch (e: ActivityNotFoundException) {
            Outcome(OutcomeStatus.NOT_SUPPORTED, ctx.getString(R.string.resp_app_missing, name))
        }
    }

    private fun navigate(destination: String): Outcome {
        if (destination.isBlank()) return Outcome(OutcomeStatus.CLARIFY, s(R.string.resp_nav_where))
        val q = Uri.encode(destination)
        val candidates = listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$q")),
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$q"))
        )
        for (i in candidates) {
            try {
                ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return Outcome(OutcomeStatus.SUCCESS, ctx.getString(R.string.resp_nav_started, destination))
            } catch (_: ActivityNotFoundException) {
            }
        }
        return Outcome(OutcomeStatus.NOT_SUPPORTED, s(R.string.resp_nav_missing))
    }

    private fun media(keyCode: Int): Outcome {
        val t = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, keyCode, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, keyCode, 0))
        // The media app does not report back, so we say "sent", not "done".
        return Outcome(OutcomeStatus.SENT_UNVERIFIED, s(R.string.resp_media_sent))
    }

    private fun percent(): Int {
        val max = audio.getStreamMaxVolume(stream).coerceAtLeast(1)
        return audio.getStreamVolume(stream) * 100 / max
    }

    private fun adjustVolume(direction: Int): Outcome {
        if (audio.isVolumeFixed) return Outcome(OutcomeStatus.NOT_SUPPORTED, s(R.string.resp_volume_fixed))
        val before = audio.getStreamVolume(stream)
        audio.adjustStreamVolume(stream, direction, AudioManager.FLAG_SHOW_UI)
        val after = audio.getStreamVolume(stream)
        return when {
            after > before -> Outcome(OutcomeStatus.SUCCESS, ctx.getString(R.string.resp_volume_now, percent()))
            after < before -> Outcome(OutcomeStatus.SUCCESS, ctx.getString(R.string.resp_volume_now, percent()))
            direction == AudioManager.ADJUST_RAISE -> Outcome(OutcomeStatus.INFO, s(R.string.resp_volume_max))
            else -> Outcome(OutcomeStatus.INFO, s(R.string.resp_volume_min))
        }
    }

    private fun setVolume(pct: Int): Outcome {
        if (audio.isVolumeFixed) return Outcome(OutcomeStatus.NOT_SUPPORTED, s(R.string.resp_volume_fixed))
        val max = audio.getStreamMaxVolume(stream)
        audio.setStreamVolume(stream, (max * pct.coerceIn(0, 100) / 100).coerceIn(0, max), AudioManager.FLAG_SHOW_UI)
        return Outcome(OutcomeStatus.SUCCESS, ctx.getString(R.string.resp_volume_now, percent()))
    }

    private fun mute(): Outcome {
        if (audio.isVolumeFixed) return Outcome(OutcomeStatus.NOT_SUPPORTED, s(R.string.resp_volume_fixed))
        audio.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
        return if (audio.isStreamMute(stream)) Outcome(OutcomeStatus.SUCCESS, s(R.string.resp_volume_muted))
        else Outcome(OutcomeStatus.FAILED, s(R.string.resp_volume_mute_failed))
    }

    private fun vehicleLabel(action: VehicleAction, value: Int?): String = when (action) {
        VehicleAction.CLIMATE_ON -> s(R.string.veh_climate_on)
        VehicleAction.CLIMATE_OFF -> s(R.string.veh_climate_off)
        VehicleAction.TEMP_SET -> ctx.getString(R.string.veh_temp_set, value ?: 22)
        VehicleAction.TEMP_UP -> s(R.string.veh_temp_up)
        VehicleAction.TEMP_DOWN -> s(R.string.veh_temp_down)
        VehicleAction.FAN_UP -> s(R.string.veh_fan_up)
        VehicleAction.FAN_DOWN -> s(R.string.veh_fan_down)
        VehicleAction.WINDOWS_OPEN -> s(R.string.veh_windows_open)
        VehicleAction.WINDOWS_CLOSE -> s(R.string.veh_windows_close)
    }

    private fun vehicleCmd(action: VehicleAction, value: Int?, confirmed: Boolean): Outcome {
        val label = vehicleLabel(action, value)
        if (action == VehicleAction.WINDOWS_OPEN && !confirmed) {
            return Outcome(OutcomeStatus.NEEDS_CONFIRMATION, ctx.getString(R.string.resp_confirm, label))
        }
        val r = vehicle.execute(action, value)
        return when (r.status) {
            VehicleResultStatus.EXECUTED_VERIFIED -> Outcome(OutcomeStatus.SUCCESS, ctx.getString(R.string.vehicle_done, label))
            VehicleResultStatus.SIMULATED -> Outcome(OutcomeStatus.SIMULATED, ctx.getString(R.string.vehicle_simulated, label))
            VehicleResultStatus.NOT_SUPPORTED -> Outcome(OutcomeStatus.NOT_SUPPORTED, ctx.getString(R.string.vehicle_not_supported, label))
            VehicleResultStatus.FAILED -> Outcome(OutcomeStatus.FAILED, ctx.getString(R.string.vehicle_failed, label))
        }
    }
}
