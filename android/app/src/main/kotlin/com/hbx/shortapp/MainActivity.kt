package com.hbx.shortapp

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessaging
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    private val CHANNEL = "com.hbx.short/overlay"

    // Heads-up notification channel for push messages
    private val PUSH_CHANNEL_ID   = "hbx_push_channel"
    private val PUSH_CHANNEL_NAME = "HBX Short Notifications"
    private var pushNotifCounter  = 2000

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        createPushNotificationChannel()

        // Edge-to-edge: draw behind status bar & nav bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
        }

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {

                    "hasOverlayPermission" -> {
                        result.success(Settings.canDrawOverlays(this))
                    }

                    "requestOverlayPermission" -> {
                        if (!Settings.canDrawOverlays(this)) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                            startActivity(intent)
                        }
                        result.success(null)
                    }

                    "startBubble" -> {
                        if (!Settings.canDrawOverlays(this)) {
                            result.error("NO_PERMISSION", "Overlay permission not granted", null)
                            return@setMethodCallHandler
                        }
                        val sizeDp = call.argument<Int>("sizeDp") ?: 62
                        val intent = Intent(this, BubbleOverlayService::class.java).apply {
                            putExtra(BubbleOverlayService.EXTRA_SIZE_DP, sizeDp)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(intent)
                        } else {
                            startService(intent)
                        }
                        result.success(true)
                    }

                    "stopBubble" -> {
                        stopService(Intent(this, BubbleOverlayService::class.java))
                        result.success(null)
                    }

                    "isBubbleRunning" -> {
                        result.success(BubbleOverlayService.isRunning)
                    }

                    "setBubbleSize" -> {
                        val sizeDp = call.argument<Int>("sizeDp") ?: 62
                        if (BubbleOverlayService.isRunning) {
                            val intent = Intent(this, BubbleOverlayService::class.java).apply {
                                action = BubbleOverlayService.ACTION_RESIZE
                                putExtra(BubbleOverlayService.EXTRA_SIZE_DP, sizeDp)
                            }
                            startService(intent)
                        }
                        result.success(null)
                    }

                    "showHeadsUpNotification" -> {
                        val title   = call.argument<String>("title")   ?: "HBX Short"
                        val message = call.argument<String>("message") ?: ""
                        showHeadsUpNotification(title, message)
                        result.success(null)
                    }

                    "shareText" -> {
                        val text = call.argument<String>("text") ?: ""
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        startActivity(Intent.createChooser(shareIntent, "Share via"))
                        result.success(null)
                    }

                    "copyToClipboard" -> {
                        val text = call.argument<String>("text") ?: ""
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("HBX Short", text))
                        result.success(null)
                    }

                    "syncBubbleConfig" -> {
                        // Web app sends its localStorage config → save to SharedPreferences
                        // so BubbleOverlayService can read it without needing the WebView
                        val json = call.argument<String>("configJson") ?: "{}"
                        BubbleConfigStore.save(this, json)
                        result.success(null)
                    }

                    "getFcmToken" -> {
                        try {
                            FirebaseMessaging.getInstance().token
                                .addOnSuccessListener { token -> result.success(token) }
                                .addOnFailureListener { result.success(null) }
                        } catch (e: Exception) {
                            result.success(null)
                        }
                    }

                    else -> result.notImplemented()
                }
            }
    }

    // ── Push notification channel & heads-up ──────────────────────

    private fun createPushNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PUSH_CHANNEL_ID,
                PUSH_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "App notifications from HBX Short"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    private fun showHeadsUpNotification(title: String, message: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(
            this, pushNotifCounter, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, PUSH_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openPending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val notifId = pushNotifCounter++
        nm.notify(notifId, notif)

        // Auto-delete after 24 hours
        scheduleNotificationDelete(notifId)
    }

    private fun scheduleNotificationDelete(notifId: Int) {
        val intent = Intent(this, NotificationDeleteReceiver::class.java).apply {
            putExtra(NotificationDeleteReceiver.EXTRA_NOTIF_ID, notifId)
        }
        val pending = PendingIntent.getBroadcast(
            this, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val triggerAt    = System.currentTimeMillis() + 24L * 60 * 60 * 1000 // 24h
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                android.app.AlarmManager.RTC_WAKEUP, triggerAt, pending
            )
        } else {
            alarmManager.setExact(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }
}
