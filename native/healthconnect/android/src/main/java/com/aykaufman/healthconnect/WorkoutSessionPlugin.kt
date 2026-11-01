package com.aykaufman.healthconnect

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.getcapacitor.JSObject
import com.getcapacitor.PermissionState
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback
import org.json.JSONObject
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

@CapacitorPlugin(
    name = "WorkoutSession",
    permissions = [Permission(alias = "notifications", strings = [Manifest.permission.POST_NOTIFICATIONS])]
)
class WorkoutSessionPlugin : Plugin() {
    companion object {
        @Volatile var instance: WorkoutSessionPlugin? = null

        fun emitAction(id: String, text: String?) {
            val p = instance ?: return
            val o = JSObject()
            o.put("id", id)
            if (text != null) o.put("text", text)
            p.emit("action", o)
        }
    }

    private var focus: AudioFocusRequest? = null

    // buttons pressed in the watch app arrive here
    private val watchListener = MessageClient.OnMessageReceivedListener { ev ->
        if (ev.path == "/ddt/action") {
            val s = String(ev.data)
            val i = s.indexOf('|')
            emitAction(if (i < 0) s else s.substring(0, i), if (i < 0) null else s.substring(i + 1))
        }
    }

    override fun load() {
        instance = this
        WorkoutService.tickListener = { instance?.emit("tick", JSObject()) }
        try { Wearable.getMessageClient(context).addListener(watchListener) } catch (e: Exception) {}
    }

    override fun handleOnDestroy() {
        try { Wearable.getMessageClient(context).removeListener(watchListener) } catch (e: Exception) {}
        super.handleOnDestroy()
    }

    /** Sends the current step to the watch app. */
    @PluginMethod
    fun watch(call: PluginCall) {
        try {
            val req = PutDataMapRequest.create("/ddt/state")
            req.dataMap.putString("json", call.data.toString())
            req.dataMap.putLong("ts", System.currentTimeMillis())
            Wearable.getDataClient(context).putDataItem(req.asPutDataRequest().setUrgent())
        } catch (e: Exception) {}
        call.resolve()
    }

    fun emit(name: String, data: JSObject) {
        bridge?.executeOnMainThread { notifyListeners(name, data) }
    }

    private fun notificationsGranted(): Boolean =
        Build.VERSION.SDK_INT < 33 || getPermissionState("notifications") == PermissionState.GRANTED

    @PluginMethod
    fun requestNotifications(call: PluginCall) {
        if (notificationsGranted()) {
            val r = JSObject(); r.put("granted", true); call.resolve(r); return
        }
        requestPermissionForAlias("notifications", call, "onNotifications")
    }

    @PermissionCallback
    private fun onNotifications(call: PluginCall) {
        val r = JSObject(); r.put("granted", notificationsGranted()); call.resolve(r)
    }

    @PluginMethod
    fun start(call: PluginCall) {
        WorkoutService.state = JSONObject(call.data.toString())
        try {
            ContextCompat.startForegroundService(context, Intent(context, WorkoutService::class.java))
            call.resolve()
        } catch (e: Exception) {
            call.reject(e.message ?: "could not start")
        }
    }

    @PluginMethod
    fun update(call: PluginCall) {
        WorkoutService.state = JSONObject(call.data.toString())
        if (WorkoutService.running) WorkoutService.post(context, call.getBoolean("alert", false) ?: false)
        call.resolve()
    }

    @PluginMethod
    fun test(call: PluginCall) {
        WorkoutService.test(context)
        val r = JSObject(); r.put("granted", notificationsGranted()); call.resolve(r)
    }

    @PluginMethod
    fun stop(call: PluginCall) {
        context.stopService(Intent(context, WorkoutService::class.java))
        call.resolve()
    }

    private fun audio() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @PluginMethod
    fun duck(call: PluginCall) {
        if (focus == null) {
            focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .build()
        }
        audio().requestAudioFocus(focus!!)
        call.resolve()
    }

    @PluginMethod
    fun unduck(call: PluginCall) {
        focus?.let { audio().abandonAudioFocusRequest(it) }
        call.resolve()
    }
}
