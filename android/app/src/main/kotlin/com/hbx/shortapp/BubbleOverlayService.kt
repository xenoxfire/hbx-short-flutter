package com.hbx.shortapp

import android.animation.ValueAnimator
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
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class BubbleOverlayService : Service() {

    companion object {
        @Volatile var isRunning = false
        private const val CHANNEL_ID  = "hbx_bubble_channel"
        private const val NOTIF_ID    = 1001
        const val ACTION_STOP         = "ACTION_STOP_BUBBLE"
        const val ACTION_RESIZE       = "ACTION_RESIZE_BUBBLE"
        const val EXTRA_SIZE_DP       = "EXTRA_SIZE_DP"
        private const val DEFAULT_SIZE_DP = 62
    }

    private var windowManager: WindowManager? = null
    private var bubbleRoot: View? = null
    private var bubbleImageView: ImageView? = null
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var currentSizeDp = DEFAULT_SIZE_DP

    // ── Lifecycle ─────────────────────────────────────────────────

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
                val sz = intent.getIntExtra(EXTRA_SIZE_DP, currentSizeDp)
                resizeBubble(sz)
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
            val ch = NotificationChannel(
                CHANNEL_ID, "HBX Short Bubble",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val stopPI = PendingIntent.getService(
            this, 0,
            Intent(this, BubbleOverlayService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openPI = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("HBX Short")
            .setContentText("Bubble active — tap to open app")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openPI)
            .addAction(0, "Stop Bubble", stopPI)
            .setOngoing(true).setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // ── Bubble ────────────────────────────────────────────────────

    private fun showBubble(sizeDp: Int) {
        currentSizeDp = sizeDp
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val d      = resources.displayMetrics.density
        val sizePx = (sizeDp * d).toInt()
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        layoutParams = WindowManager.LayoutParams(
            sizePx, sizePx, overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenW - sizePx - (14 * d).toInt()
            y = (screenH * 0.55).toInt()
        }

        val container = FrameLayout(this)
        val iv = ImageView(this).apply {
            setImageBitmap(buildBubbleBitmap(sizePx))
            scaleType = ImageView.ScaleType.FIT_XY
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

    /**
     * Builds the bubble bitmap from launcher_bubble_icon.png (the web app's /launcher-icon.png),
     * clipped to a circle with a purple/violet glow ring — matching the web app's design exactly.
     *
     * Web app glow colours: rgba(167,139,250,.75) inner ring  ← #A78BFA
     *                       rgba(139,92,246,.9)  mid glow    ← #8B5CF6
     */
    private fun buildBubbleBitmap(sizePx: Int): Bitmap {
        val density = resources.displayMetrics.density

        // Decode the launcher bubble icon (web app's /launcher-icon.png)
        val srcBitmap: Bitmap? = try {
            val opts = BitmapFactory.Options().apply { inSampleSize = 1 }
            BitmapFactory.decodeResource(resources, R.drawable.launcher_bubble_icon, opts)
        } catch (_: Exception) { null }

        val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val cx = sizePx / 2f
        val cy = sizePx / 2f

        // ── 1. Outer glow (purple, soft) ──────────────────────────
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (9 * density)
            color = Color.argb(64, 109, 40, 217)   // rgba(109,40,217,.25)
            setShadowLayer(18f, 0f, 0f, Color.argb(100, 139, 92, 246))
        }
        canvas.drawCircle(cx, cy, cx - (2 * density), glowPaint)

        // ── 2. Mid glow ────────────────────────────────────────────
        val midPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (4.5f * density)
            color = Color.argb(230, 139, 92, 246)  // rgba(139,92,246,.9)
            setShadowLayer(10f, 0f, 0f, Color.argb(200, 167, 139, 250))
        }
        canvas.drawCircle(cx, cy, cx - (3.5f * density), midPaint)

        // ── 3. Inner ring (bright violet) ─────────────────────────
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (2f * density)
            color = Color.argb(191, 167, 139, 250) // rgba(167,139,250,.75)
        }
        canvas.drawCircle(cx, cy, cx - (5.5f * density), ringPaint)

        // ── 4. Dark circle fill (background) ─────────────────────
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#060606")
        }
        val imgRadius = cx - (6.5f * density)
        canvas.drawCircle(cx, cy, imgRadius, fillPaint)

        // ── 5. Clip & draw launcher icon ─────────────────────────
        if (srcBitmap != null) {
            val clipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            // Clip mask
            val maskBmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val maskCanvas = Canvas(maskBmp)
            maskCanvas.drawCircle(cx, cy, imgRadius, Paint(Paint.ANTI_ALIAS_FLAG))

            // Scale icon to fill circle (130% size, centered — same as web app)
            val iconSz = (imgRadius * 2 * 1.30f).toInt()
            val scaled = Bitmap.createScaledBitmap(srcBitmap, iconSz, iconSz, true)
            val ox = cx - iconSz / 2f
            val oy = cy - iconSz / 2f

            // Draw icon clipped to circle
            val savedCount = canvas.saveLayer(null, null)
            canvas.drawBitmap(maskBmp, 0f, 0f, null)
            clipPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(scaled, ox, oy, clipPaint)
            canvas.restoreToCount(savedCount)

            if (scaled != srcBitmap) scaled.recycle()
        }

        // ── 6. Inner shadow overlay (depth) ──────────────────────
        val innerShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (3f * density)
            color = Color.argb(30, 255, 255, 255)
            setShadowLayer(2f, 0f, 1f, Color.argb(100, 255, 255, 255))
        }
        canvas.drawCircle(cx, cy, imgRadius - density, innerShadow)

        return output
    }

    private fun resizeBubble(newSizeDp: Int) {
        currentSizeDp = newSizeDp
        val d      = resources.displayMetrics.density
        val sizePx = (newSizeDp * d).toInt()
        layoutParams.width  = sizePx
        layoutParams.height = sizePx
        bubbleImageView?.setImageBitmap(buildBubbleBitmap(sizePx))
        try { windowManager?.updateViewLayout(bubbleRoot, layoutParams) } catch (_: Exception) {}
    }

    // ── Touch: drag + snap + click ────────────────────────────────

    private fun attachTouchListener(container: FrameLayout, initialSizePx: Int) {
        var touchStartX  = 0f
        var touchStartY  = 0f
        var windowStartX = 0
        var windowStartY = 0
        var isDragging   = false

        container.setOnTouchListener { _, event ->
            val sizePx = layoutParams.width
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
                    if (isDragging) animateSnapToEdge(sizePx) else openApp()
                    true
                }
                else -> false
            }
        }
    }

    /** Smoothly animate the bubble snapping to the nearest screen edge. */
    private fun animateSnapToEdge(sizePx: Int) {
        val screenW = resources.displayMetrics.widthPixels
        val margin  = (14 * resources.displayMetrics.density).toInt()
        val targetX = if (layoutParams.x + sizePx / 2 < screenW / 2)
            margin else screenW - sizePx - margin

        val startX = layoutParams.x
        ValueAnimator.ofInt(startX, targetX).apply {
            duration = 260
            interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener { anim ->
                layoutParams.x = anim.animatedValue as Int
                try { windowManager?.updateViewLayout(bubbleRoot, layoutParams) }
                catch (_: Exception) {}
            }
            start()
        }
    }

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
    }

    private fun removeBubble() {
        try { bubbleRoot?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        bubbleRoot    = null
        windowManager = null
    }
}
