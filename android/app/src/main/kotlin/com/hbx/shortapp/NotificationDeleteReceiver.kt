package com.hbx.shortapp

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BroadcastReceiver that cancels a notification after 24 hours.
 * Scheduled via AlarmManager whenever a push notification is shown.
 */
class NotificationDeleteReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_NOTIF_ID = "notif_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
        if (notifId != -1) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(notifId)
        }
    }
}
