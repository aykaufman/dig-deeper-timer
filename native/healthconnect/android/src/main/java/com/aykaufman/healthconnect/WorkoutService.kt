package com.aykaufman.healthconnect

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import org.json.JSONObject

/** Keeps a workout alive with the screen off, shows it on the lock screen and the watch, and ticks the app's timer. */
class WorkoutService : Service() {
    companion object {
        const val CH_RUN = "ddt_running"
        const val CH_LIVE = "ddt_live"
        const val ID_RUN = 4101
        const val ID_LIVE = 4102
        const val ID_TEST = 4103
        const val INPUT_KEY = "ddt_input"

        @Volatile var state: JSONObject = JSONObject()
        @Volatile var running = false
        var tickListener: (() -> Unit)? = null

        fun channels(ctx: Context) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CH_RUN) == null) {
                val c = NotificationChannel(CH_RUN, "Workout running", NotificationManager.IMPORTANCE_MIN)
                c.setShowBadge(false)
                nm.createNotificationChannel(c)
            }
            if (nm.getNotificationChannel(CH_LIVE) == null) {
                val c = NotificationChannel(CH_LIVE, "Workout steps", NotificationManager.IMPORTANCE_DEFAULT)
                c.setSound(null, null)
                c.enableVibration(true)
                c.vibrationPattern = longArrayOf(0, 180)
                c.setShowBadge(false)
                nm.createNotificationChannel(c)
            }
        }

        private fun openApp(ctx: Context): PendingIntent? {
            val i = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: return null
            i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            return PendingIntent.getActivity(ctx, 1, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        fun runningNotification(ctx: Context): Notification =
            NotificationCompat.Builder(ctx, CH_RUN)
                .setSmallIcon(R.drawable.ic_stat_ddt)
                .setContentTitle("Workout in progress")
                .setOngoing(true)
                .setSilent(true)
                .setLocalOnly(true)
                .setContentIntent(openApp(ctx))
                .build()

        private fun liveNotification(ctx: Context, alert: Boolean): Notification {
            val s = state
            val endsAt = s.optLong("endsAt", 0L)
            val b = NotificationCompat.Builder(ctx, CH_LIVE)
                .setSmallIcon(R.drawable.ic_stat_ddt)
                .setContentTitle(s.optString("title", "Workout"))
                .setContentText(s.optString("text", ""))
                .setStyle(NotificationCompat.BigTextStyle().bigText(s.optString("text", "")))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setLocalOnly(false)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(!alert)
                .setAutoCancel(false)
                .setContentIntent(openApp(ctx))
            if (endsAt > System.currentTimeMillis()) {
                b.setWhen(endsAt).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
            } else {
                b.setShowWhen(false)
            }
            val wear = NotificationCompat.WearableExtender()
            val actions = s.optJSONArray("actions")
            if (actions != null) {
                for (k in 0 until actions.length()) {
                    val a = actions.optJSONObject(k) ?: continue
                    val id = a.optString("id")
                    val label = a.optString("label", id)
                    val hint = a.optString("input", "")
                    val intent = Intent(ctx, ActionReceiver::class.java).setAction("com.aykaufman.ddt.$id")
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                        (if (hint.isNotEmpty()) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE)
                    val pi = PendingIntent.getBroadcast(ctx, 100 + k + id.hashCode() % 1000, intent, flags)
                    val ab = NotificationCompat.Action.Builder(0, label, pi)
                    if (hint.isNotEmpty()) {
                        val rb = RemoteInput.Builder(INPUT_KEY).setLabel(hint)
                        val ch = a.optJSONArray("choices")
                        if (ch != null && ch.length() > 0) {
                            rb.setChoices(Array<CharSequence>(ch.length()) { ch.optString(it) })
                        }
                        ab.addRemoteInput(rb.build()).setAllowGeneratedReplies(false)
                    }
                    val built = ab.build()
                    b.addAction(built)
                    wear.addAction(built)
                }
            }
            b.extend(wear)
            return b.build()
        }

        fun test(ctx: Context) {
            val saved = state
            state = JSONObject().put("title", "Dig Deeper watch test")
                .put("text", "If you can see this on your watch, workout steps will show there too.")
                .put("actions", org.json.JSONArray().put(JSONObject().put("id", "testok").put("label", "Got it")))
            try {
                channels(ctx)
                NotificationManagerCompat.from(ctx).notify(ID_TEST, liveNotification(ctx, true))
            } catch (e: SecurityException) {}
            state = saved
        }

        fun post(ctx: Context, alert: Boolean) {
            try {
                channels(ctx)
                NotificationManagerCompat.from(ctx).notify(ID_LIVE, liveNotification(ctx, alert))
            } catch (e: SecurityException) {
                // notifications not allowed: the workout still runs, there's just nothing to show
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            try { tickListener?.invoke() } catch (e: Exception) {}
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        channels(this)
        val n = runningNotification(this)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID_RUN, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ID_RUN, n)
        }
        post(this, true)
        if (!running) {
            running = true
            handler.post(tick)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        running = false
        NotificationManagerCompat.from(this).cancel(ID_LIVE)
        super.onDestroy()
    }
}
