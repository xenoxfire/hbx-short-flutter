package com.hbx.shortapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class BubbleOverlayService : Service() {

    companion object {
        @Volatile var isRunning = false
        private const val CHANNEL_ID = "hbx_bubble_channel"
        private const val NOTIF_ID   = 1001
        const val ACTION_STOP        = "ACTION_STOP_BUBBLE"
    }

    private var windowManager: WindowManager? = null
    private var bubbleRoot: View? = null
    private lateinit var layoutParams: WindowManager.LayoutParams

    // ── Lifecycle ─────────────────────────────────────────────────

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        showBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        removeBubble()
        super.onDestroy()
    }

    // ── Notification ──────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "HBX Short Bubble",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Floating bubble overlay service"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        // "Stop" action from notification
        val stopIntent = Intent(this, BubbleOverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Tap notification → open app
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(
            this, 1, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("HBX Short")
            .setContentText("Floating bubble is active. Tap to open app.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openPending)
            .addAction(0, "Stop Bubble", stopPending)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // ── Bubble View ───────────────────────────────────────────────

    private fun showBubble() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val density  = resources.displayMetrics.density
        val sizePx   = (62 * density).toInt()
        val screenW  = resources.displayMetrics.widthPixels
        val screenH  = resources.displayMetrics.heightPixels

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            sizePx, sizePx,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenW - sizePx - (12 * density).toInt()  // start on right edge
            y = (screenH * 0.45).toInt()
        }

        // ── Build the bubble view ──
        val container = FrameLayout(this)

        val iv = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = circleBg()
            elevation = 8f
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(iv)
        bubbleRoot = container

        // ── Touch: drag + click ────────────────────────────────────
        var touchStartX = 0f
        var touchStartY = 0f
        var windowStartX = 0
        var windowStartY = 0
        var isDragging = false

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX  = event.rawX
                    touchStartY  = event.rawY
                    windowStartX = layoutParams.x
                    windowStartY = layoutParams.y
                    isDragging   = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchStartX).toInt()
                    val dy = (event.rawY - touchStartY).toInt()
                    if (!isDragging && (abs(dx) > 10 || abs(dy) > 10)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        layoutParams.x = windowStartX + dx
                        layoutParams.y = windowStartY + dy
                        try { windowManager?.updateViewLayout(bubbleRoot, layoutParams) }
                        catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        snapToEdge(sizePx)
                    } else {
                        // Click → bring app to foreground
                        openApp()
                    }
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(bubbleRoot, layoutParams)
    }

    /** Snap bubble to the nearest screen edge (left or right). */
    private fun snapToEdge(sizePx: Int) {
        val screenW = resources.displayMetrics.widthPixels
        val targetX = if (layoutParams.x + sizePx / 2 < screenW / 2) {
            0
        } else {
            screenW - sizePx
        }
        layoutParams.x = targetX
        try { windowManager?.updateViewLayout(bubbleRoot, layoutParams) }
        catch (_: Exception) {}
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun removeBubble() {
        try { bubbleRoot?.let { windowManager?.removeView(it) } }
        catch (_: Exception) {}
        bubbleRoot    = null
        windowManager = null
    }

    // ── Helpers ───────────────────────────────────────────────────

    private fun circleBg(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.parseColor("#0d1117"))
        setStroke(4, Color.parseColor("#3b82f6"))
    }
}
