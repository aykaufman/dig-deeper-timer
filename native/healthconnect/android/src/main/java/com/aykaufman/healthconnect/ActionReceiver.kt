package com.aykaufman.healthconnect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput

/** Buttons on the workout notification (phone or watch) land here and go to the app. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.action?.substringAfterLast('.') ?: return
        if (id == "testok") {
            androidx.core.app.NotificationManagerCompat.from(context).cancel(WorkoutService.ID_TEST)
            WorkoutSessionPlugin.emitAction(id, null)
            return
        }
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(WorkoutService.INPUT_KEY)?.toString()
        WorkoutSessionPlugin.emitAction(id, text)
        // re-post so a reply doesn't leave a spinner; the app sends the new step right after
        if (WorkoutService.running) WorkoutService.post(context, false)
    }
}
