package com.hbx.shortapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
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
        const val ACTION_RESIZE      = "ACTION_RESIZE_BUBBLE"
        const val EXTRA_SIZE_DP      = "EXTRA_SIZE_DP"
        private const val DEFAULT_SIZE_DP = 62
    }

    private var windowManager: WindowManager? = null
    private var bubbleRoot: View? = null
    private var bubbleImageView: ImageView? = null
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var currentSizeDp = DEFAULT_SIZE_DP

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        showBubble(DEFAULT_SIZE_DP)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP   -> stopSelf()
            ACTION_RESIZE -> {
                val newSize = intent.getIntExtra(EXTRA_SIZE_DP, currentSizeDp)
                resizeBubble(newSize)
            }
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
        val stopIntent = Intent(this, BubbleOverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(
            this, 1, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("HBX Short")
            .setContentText("Bubble active — tap to open app")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openPending)
            .addAction(0, "Stop Bubble", stopPending)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // ── Bubble View ───────────────────────────────────────────────

    private fun showBubble(sizeDp: Int) {
        currentSizeDp = sizeDp
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val density = resources.displayMetrics.density
        val sizePx  = (sizeDp * density).toInt()
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels

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
            x = screenW - sizePx - (12 * density).toInt()
            y = (screenH * 0.40).toInt()
        }

        val container = FrameLayout(this)

        // Use the actual launcher icon as a rounded bitmap
        val iv = ImageView(this).apply {
            setImageBitmap(getRoundedLauncherBitmap(sizePx))
            scaleType = ImageView.ScaleType.FIT_XY
            elevation = 10f
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        bubbleImageView = iv
        container.addView(iv)
        bubbleRoot = container

        attachTouchListener(container, sizePx)
        windowManager?.addView(bubbleRoot, layoutParams)
    }

    /** Decode the launcher icon PNG and clip it into a circle. */
    private fun getRoundedLauncherBitmap(sizePx: Int): Bitmap {
        val raw = try {
            BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher_round)
                ?: BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
        } catch (_: Exception) {
            null
        }

        val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint  = Paint(Paint.ANTI_ALIAS_FLAG)

        // Draw glowing border ring
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color  = Color.parseColor("#3B82F6")
            style  = Paint.Style.STROKE
            strokeWidth = (3 * resources.displayMetrics.density)
            setShadowLayer(8f, 0f, 0f, Color.parseColor("#803B82F6"))
        }
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f - 2f, borderPaint)

        // Clip circle mask
        val radius = sizePx / 2f - (3 * resources.displayMetrics.density)
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, radius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)

        if (raw != null) {
            val scaled = Bitmap.createScaledBitmap(raw, sizePx, sizePx, true)
            canvas.drawBitmap(scaled, 0f, 0f, paint)
            if (scaled != raw) scaled.recycle()
        } else {
            // Fallback: blue circle with "HBX" text
            paint.xfermode = null
            paint.color = Color.parseColor("#0D1117")
            canvas.drawCircle(sizePx / 2f, sizePx / 2f, radius, paint)
            paint.color = Color.parseColor("#3B82F6")
            paint.textSize = sizePx * 0.3f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("HBX", sizePx / 2f, sizePx / 2f + paint.textSize * 0.35f, paint)
        }
        paint.xfermode = null
        return output
    }

    private fun resizeBubble(newSizeDp: Int) {
        currentSizeDp = newSizeDp
        val density  = resources.displayMetrics.density
        val sizePx   = (newSizeDp * density).toInt()
        layoutParams.width  = sizePx
        layoutParams.height = sizePx
        bubbleImageView?.setImageBitmap(getRoundedLauncherBitmap(sizePx))
        try { windowManager?.updateViewLayout(bubbleRoot, layoutParams) } catch (_: Exception) {}
    }

    private fun attachTouchListener(container: FrameLayout, sizePx: Int) {
        var touchStartX  = 0f
        var touchStartY  = 0f
        var windowStartX = 0
        var windowStartY = 0
        var isDragging   = false

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
                    if (!isDragging && (abs(dx) > 8 || abs(dy) > 8)) isDragging = true
                    if (isDragging) {
                        layoutParams.x = windowStartX + dx
                        layoutParams.y = windowStartY + dy
                        try { windowManager?.updateViewLayout(bubbleRoot, layoutParams) }
                        catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) snapToEdge(sizePx) else openApp()
                    true
                }
                else -> false
            }
        }
    }

    private fun snapToEdge(sizePx: Int) {
        val screenW = resources.displayMetrics.widthPixels
        val margin  = (12 * resources.displayMetrics.density).toInt()
        layoutParams.x = if (layoutParams.x + sizePx / 2 < screenW / 2) {
            margin
        } else {
            screenW - sizePx - margin
        }
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
}
