package com.hbx.shortapp

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
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
    private var bubbleRoot: View?        = null
    private var expandedPanel: View?     = null
    private var backdropView: View?      = null
    private var bubbleImageView: ImageView? = null
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private var currentSizeDp = DEFAULT_SIZE_DP
    private var isExpanded    = false
    private val mainHandler   = Handler(Looper.getMainLooper())

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
            ACTION_RESIZE -> resizeBubble(intent.getIntExtra(EXTRA_SIZE_DP, currentSizeDp))
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        collapseMenu()
        removeBubble()
        super.onDestroy()
    }

    // ── Notification ──────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "HBX Short Bubble",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false); enableLights(false); enableVibration(false) }
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
            .setContentText("Bubble active — tap to expand menu")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openPI)
            .addAction(0, "Stop Bubble", stopPI)
            .setOngoing(true).setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // ── Main Bubble ───────────────────────────────────────────────

    private fun showBubble(sizeDp: Int) {
        currentSizeDp = sizeDp
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val d       = resources.displayMetrics.density
        val sizePx  = (sizeDp * d).toInt()
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        bubbleParams = WindowManager.LayoutParams(
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

        attachBubbleTouchListener(container, sizePx)
        windowManager?.addView(bubbleRoot, bubbleParams)
    }

    // ── Expand / Collapse Menu ────────────────────────────────────

    private fun expandMenu() {
        if (isExpanded) return
        isExpanded = true

        val cfg     = BubbleConfigStore.load(this)
        val d       = resources.displayMetrics.density
        val btnDp   = cfg.buttonSizeDp
        val btnPx   = (btnDp * d).toInt()
        val gap     = (12 * d).toInt()
        val mainPx  = (currentSizeDp * d).toInt()

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        // ── Backdrop ─────────────────────────────────────────────
        val backdrop = View(this).apply {
            setBackgroundColor(Color.argb(115, 0, 0, 0))
            setOnClickListener { collapseMenu() }
        }
        val bdParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        backdropView = backdrop
        windowManager?.addView(backdrop, bdParams)

        // ── Button panel ──────────────────────────────────────────
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = Gravity.CENTER_HORIZONTAL
        }

        // ① 2FA button (☯)
        panel.addView(makeCircleBtn(btnPx, "☯", "#1e2535", gap) {
            handleTwoFA()
        })

        // ② Column buttons (A, B, C…)
        for (col in cfg.columns) {
            panel.addView(makeCircleBtn(btnPx, col, "#1e2535", gap) {
                handleColTap(col, cfg)
            })
        }

        // ③ Minus / close button
        panel.addView(makeCircleBtn(btnPx, "−", "#2a2f40", gap) {
            collapseMenu()
        })

        // Total panel height: (buttons + gaps) above the bubble
        val itemCount   = cfg.columns.size + 2      // 2FA + cols + minus
        val panelH      = itemCount * btnPx + (itemCount + 1) * gap
        val panelW      = btnPx + (16 * d).toInt()  // slight horizontal padding

        // Position panel centred over the bubble X, just above bubble Y
        val screenH     = resources.displayMetrics.heightPixels
        val bubbleTop   = bubbleParams.y
        val panelTop    = (bubbleTop - panelH - gap).coerceAtLeast(
            (resources.displayMetrics.heightPixels * 0.05f).toInt()
        )
        val panelLeft   = bubbleParams.x + (mainPx / 2) - (panelW / 2)

        val panelParams = WindowManager.LayoutParams(
            panelW, panelH,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = panelLeft
            y = panelTop
        }
        expandedPanel = panel
        windowManager?.addView(panel, panelParams)
    }

    private fun collapseMenu() {
        if (!isExpanded) return
        isExpanded = false
        try { backdropView?.let  { windowManager?.removeView(it) } } catch (_: Exception) {}
        try { expandedPanel?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        backdropView  = null
        expandedPanel = null

        // Restore bubble to launcher icon (not red expanded state)
        val sizePx = (currentSizeDp * resources.displayMetrics.density).toInt()
        bubbleImageView?.setImageBitmap(buildBubbleBitmap(sizePx))
    }

    // ── Column Tap → save clipboard to Sheet ─────────────────────

    private fun handleColTap(col: String, cfg: BubbleConfig) {
        val text = readClipboard() ?: run {
            toast("Clipboard খালি — আগে কিছু copy করুন")
            return
        }
        if (text.isBlank()) { toast("Clipboard খালি — আগে কিছু copy করুন"); return }

        toast("$col → সেভ হচ্ছে…")

        Thread {
            try {
                when (cfg.saveMode) {
                    "local" -> {
                        // Local mode: save to SharedPreferences (simple list per column)
                        val prefs = getSharedPreferences("hbx_local_sheet", Context.MODE_PRIVATE)
                        val key   = "col_$col"
                        val prev  = prefs.getString(key, "") ?: ""
                        val rows  = if (prev.isEmpty()) mutableListOf() else prev.split("|||").toMutableList()
                        rows.add(text.trim().take(400))
                        prefs.edit().putString(key, rows.joinToString("|||")).apply()
                        mainHandler.post { toast("✅ $col → Local ${rows.size}") }
                    }
                    else -> {
                        // Google Sheet mode: POST to Apps Script
                        if (cfg.webAppUrl.isBlank()) {
                            mainHandler.post { toast("Apps Script URL নেই — Float Sheet Set-Up করুন") }
                            return@Thread
                        }
                        val body = """{"sheetId":"${cfg.sheetId}","sheetName":"${cfg.sheetName}","column":"$col","action":"APPEND","text":"${text.trim().take(400).replace("\"","\\\"").replace("\n","\\n")}"}"""
                        val result = postJson(cfg.webAppUrl, body)
                        mainHandler.post {
                            if (result.contains("\"success\"")) {
                                val rowMatch = Regex("\"savedRow\"\\s*:\\s*(\\d+)").find(result)
                                val row = rowMatch?.groupValues?.getOrNull(1) ?: "?"
                                toast("✅ $col → Row $row")
                            } else {
                                toast("❌ $col সেভ হয়নি")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                mainHandler.post { toast("❌ Error: ${e.message?.take(60)}") }
            }
        }.start()
    }

    // ── 2FA ───────────────────────────────────────────────────────

    private fun handleTwoFA() {
        val secret = readClipboard() ?: run {
            toast("Clipboard খালি — 2FA secret copy করুন")
            return
        }
        if (secret.isBlank()) { toast("Clipboard খালি — 2FA secret copy করুন"); return }
        try {
            val code = BubbleTOTP.generate(secret.trim())
            val cm   = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("2FA", code))
            toast("✅ 2FA কপি = $code")
        } catch (_: Exception) {
            toast("❌ Invalid 2FA key — সঠিক base32 secret copy করুন")
        }
    }

    // ── Clipboard ─────────────────────────────────────────────────

    private fun readClipboard(): String? {
        return try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
        } catch (_: Exception) { null }
    }

    // ── HTTP POST helper ──────────────────────────────────────────

    private fun postJson(urlStr: String, body: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod        = "POST"
            conn.doOutput             = true
            conn.connectTimeout       = 12_000
            conn.readTimeout          = 15_000
            conn.setRequestProperty("Content-Type", "text/plain;charset=utf-8")
            conn.instanceFollowRedirects = true
            val writer = OutputStreamWriter(conn.outputStream, "UTF-8")
            writer.write(body); writer.flush()
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                conn.errorStream?.bufferedReader()?.readText() ?: ""
            }
        } finally {
            conn.disconnect()
        }
    }

    // ── UI Helpers ────────────────────────────────────────────────

    /**
     * Creates a circular button with label text.
     * [bg] can be a hex colour string.
     */
    private fun makeCircleBtn(
        sizePx: Int,
        label: String,
        bgHex: String,
        gapPx: Int,
        onClick: () -> Unit
    ): FrameLayout {
        val d    = resources.displayMetrics.density
        val fl   = FrameLayout(this)
        val lp   = LinearLayout.LayoutParams(sizePx, sizePx).apply {
            bottomMargin = gapPx
        }
        fl.layoutParams = lp

        // Circular background
        val bg = android.graphics.drawable.GradientDrawable().apply {
            shape     = android.graphics.drawable.GradientDrawable.OVAL
            setColor(Color.parseColor(bgHex))
            setStroke((2 * d).toInt(), Color.argb(60, 167, 139, 250))
        }
        fl.background = bg
        fl.elevation  = (6 * d)

        val tv = TextView(this).apply {
            text      = label
            textSize  = sizePx / d * 0.30f
            setTextColor(Color.WHITE)
            gravity   = Gravity.CENTER
            typeface  = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        fl.addView(tv)
        fl.setOnClickListener { onClick() }
        return fl
    }

    private fun toast(msg: String) {
        mainHandler.post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    }

    // ── Bubble bitmap (launcher-icon.png + purple glow) ───────────

    private fun buildBubbleBitmap(sizePx: Int): Bitmap {
        val d  = resources.displayMetrics.density
        val cx = sizePx / 2f
        val cy = sizePx / 2f

        val src: Bitmap? = try {
            BitmapFactory.decodeResource(resources, R.drawable.launcher_bubble_icon)
        } catch (_: Exception) { null }

        val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Outer glow — rgba(109,40,217,.25)
        canvas.drawCircle(cx, cy, cx - (2 * d), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (9 * d)
            color = Color.argb(64, 109, 40, 217)
            setShadowLayer(18f, 0f, 0f, Color.argb(100, 139, 92, 246))
        })
        // Mid glow — rgba(139,92,246,.9)
        canvas.drawCircle(cx, cy, cx - (3.5f * d), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (4.5f * d)
            color = Color.argb(230, 139, 92, 246)
            setShadowLayer(10f, 0f, 0f, Color.argb(200, 167, 139, 250))
        })
        // Inner ring — rgba(167,139,250,.75)
        canvas.drawCircle(cx, cy, cx - (5.5f * d), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (2f * d)
            color = Color.argb(191, 167, 139, 250)
        })

        val imgRadius = cx - (6.5f * d)
        // Dark fill
        canvas.drawCircle(cx, cy, imgRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#060606")
        })

        // Clip launcher icon
        if (src != null) {
            val iconSz = (imgRadius * 2 * 1.30f).toInt()
            val scaled = Bitmap.createScaledBitmap(src, iconSz, iconSz, true)
            val ox     = cx - iconSz / 2f
            val oy     = cy - iconSz / 2f

            val maskBmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            Canvas(maskBmp).drawCircle(cx, cy, imgRadius, Paint(Paint.ANTI_ALIAS_FLAG))

            val clipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val saved = canvas.saveLayer(null, null)
            canvas.drawBitmap(maskBmp, 0f, 0f, null)
            clipPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(scaled, ox, oy, clipPaint)
            canvas.restoreToCount(saved)
            if (scaled != src) scaled.recycle()
        }

        return output
    }

    // ── Resize ────────────────────────────────────────────────────

    private fun resizeBubble(newSizeDp: Int) {
        currentSizeDp = newSizeDp
        val sizePx = (newSizeDp * resources.displayMetrics.density).toInt()
        bubbleParams.width  = sizePx
        bubbleParams.height = sizePx
        bubbleImageView?.setImageBitmap(buildBubbleBitmap(sizePx))
        try { windowManager?.updateViewLayout(bubbleRoot, bubbleParams) } catch (_: Exception) {}
    }

    // ── Touch: drag + tap ─────────────────────────────────────────

    private fun attachBubbleTouchListener(container: FrameLayout, initSizePx: Int) {
        var touchX   = 0f;  var touchY   = 0f
        var winX     = 0;   var winY     = 0
        var dragging = false

        container.setOnTouchListener { _, event ->
            val sizePx = bubbleParams.width
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchX   = event.rawX; touchY   = event.rawY
                    winX     = bubbleParams.x; winY = bubbleParams.y
                    dragging = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (!dragging && (abs(dx) > 8 || abs(dy) > 8)) dragging = true
                    if (dragging) {
                        bubbleParams.x = winX + dx
                        bubbleParams.y = winY + dy
                        try { windowManager?.updateViewLayout(bubbleRoot, bubbleParams) }
                        catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        animateSnapToEdge(sizePx)
                    } else {
                        if (isExpanded) collapseMenu() else expandMenu()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun animateSnapToEdge(sizePx: Int) {
        val screenW = resources.displayMetrics.widthPixels
        val margin  = (14 * resources.displayMetrics.density).toInt()
        val targetX = if (bubbleParams.x + sizePx / 2 < screenW / 2) margin
                      else screenW - sizePx - margin
        ValueAnimator.ofInt(bubbleParams.x, targetX).apply {
            duration     = 260
            interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener { anim ->
                bubbleParams.x = anim.animatedValue as Int
                try { windowManager?.updateViewLayout(bubbleRoot, bubbleParams) }
                catch (_: Exception) {}
            }
            start()
        }
    }

    private fun removeBubble() {
        try { bubbleRoot?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        bubbleRoot = null; windowManager = null
    }
}
