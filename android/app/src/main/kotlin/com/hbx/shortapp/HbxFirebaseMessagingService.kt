package com.hbx.shortapp

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class HbxFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val PUSH_CHANNEL_ID   = "hbx_push_channel"
        private const val PUSH_CHANNEL_NAME = "HBX Short Notifications"
        private var notifCounter = 3000
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Token refresh — save locally or send to your server
        val prefs = getSharedPreferences("hbx_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("fcm_token", token).apply()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        ensureChannel()

        // Use notification payload first, fall back to data payload
        val title   = message.notification?.title
            ?: message.data["title"]
            ?: "HBX Short"
        val body    = message.notification?.body
            ?: message.data["message"]
            ?: message.data["body"]
            ?: ""

        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val notifId  = notifCounter++
        val pending  = PendingIntent.getActivity(
            this, notifId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, PUSH_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        nm.notify(notifId, notif)

        // Auto-delete after 24 hours
        scheduleDelete(notifId)
    }

    private fun scheduleDelete(notifId: Int) {
        val intent = Intent(this, NotificationDeleteReceiver::class.java).apply {
            putExtra(NotificationDeleteReceiver.EXTRA_NOTIF_ID, notifId)
        }
        val pending = PendingIntent.getBroadcast(
            this, notifId + 10000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am        = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + 24L * 60 * 60 * 1000
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(PUSH_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    PUSH_CHANNEL_ID,
                    PUSH_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    enableLights(true)
                    enableVibration(true)
                    setShowBadge(true)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }
}
