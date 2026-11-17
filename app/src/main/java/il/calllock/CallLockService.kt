package il.calllock

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.hypot

class CallLockService : Service() {

    private lateinit var tm: TelephonyManager
    private lateinit var wm: WindowManager

    private var overlay: View? = null
    private var lastState = TelephonyManager.CALL_STATE_IDLE
    private var dismissedForThisCall = false

    private var callback: Any? = null
    private var legacyListener: PhoneStateListener? = null

    private val handler = Handler(Looper.getMainLooper())

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForeground(NOTIF_ID, buildNotification())
        registerCallListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        unregisterCallListener()
        hideOverlay()
        super.onDestroy()
    }

    // ---------- Notification ----------

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "נעילת שיחה", NotificationManager.IMPORTANCE_MIN)
        )
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("נעילת מגע בשיחה פעילה")
            .setSmallIcon(R.drawable.ic_lock)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    // ---------- Call state ----------

    private fun registerCallListener() {
        if (Build.VERSION.SDK_INT >= 31) {
            val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    handleState(state)
                }
            }
            try {
                tm.registerTelephonyCallback(mainExecutor, cb)
                callback = cb
            } catch (_: SecurityException) {
                stopSelf()
            }
        } else {
            @Suppress("DEPRECATION")
            val l = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    handleState(state)
                }
            }
            @Suppress("DEPRECATION")
            tm.listen(l, PhoneStateListener.LISTEN_CALL_STATE)
            legacyListener = l
        }
    }

    private fun unregisterCallListener() {
        if (Build.VERSION.SDK_INT >= 31) {
            (callback as? TelephonyCallback)?.let { tm.unregisterTelephonyCallback(it) }
        } else {
            @Suppress("DEPRECATION")
            legacyListener?.let { tm.listen(it, PhoneStateListener.LISTEN_NONE) }
        }
        callback = null
        legacyListener = null
    }

    private fun handleState(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (overlay == null && !dismissedForThisCall) {
                    val incoming = lastState == TelephonyManager.CALL_STATE_RINGING
                    showOverlay(incoming)
                }
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                hideOverlay()
                dismissedForThisCall = false
            }
            TelephonyManager.CALL_STATE_RINGING -> {
                dismissedForThisCall = false
            }
        }
        lastState = state
    }

    // ---------- Overlay ----------

    private fun shadowed(tv: TextView) = tv.apply {
        setTextColor(Color.WHITE)
        setShadowLayer(10f, 0f, 0f, Color.BLACK)
    }

    private fun unlock() {
        dismissedForThisCall = true
        hideOverlay()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlay(incoming: Boolean) {
        if (!Settings.canDrawOverlays(this)) return

        val ui: Context = DynamicColors.wrapContextIfAvailable(
            ContextThemeWrapper(this, R.style.Theme_CallLock)
        )

        val root = FrameLayout(ui).apply {
            setBackgroundColor(Color.TRANSPARENT) // שקוף: רואים את מסך השיחה שמתחת
            isClickable = true
            setOnTouchListener { _, _ -> true }   // בולע את כל המגע
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        // ----- כרטיס מידע (אופציונלי) -----
        if (Prefs.showCard(this)) {
            val a = Prefs.cardAlpha(this).coerceIn(0, 100) * 255 / 100
            val card = MaterialCardView(ui).apply {
                radius = dp(24).toFloat()
                cardElevation = 0f
                strokeWidth = 0
                setCardBackgroundColor(Color.argb(a, 0, 0, 0))
            }
            val inner = LinearLayout(ui).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(28), dp(14), dp(28), dp(14))
            }
            inner.addView(shadowed(TextView(ui).apply {
                text = if (incoming) "שיחה נכנסת" else "שיחה יוצאת"
                textSize = 16f
                gravity = Gravity.CENTER
            }))
            inner.addView(shadowed(Chronometer(ui).apply {
                base = SystemClock.elapsedRealtime()
                textSize = 44f
                gravity = Gravity.CENTER
                start()
            }))
            val startTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            inner.addView(shadowed(TextView(ui).apply {
                text = "התחלה: $startTime"
                textSize = 14f
                gravity = Gravity.CENTER
            }))
            card.addView(inner)
            root.addView(
                card,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.CENTER_HORIZONTAL
                ).apply { topMargin = dp(90) }
            )
        }

        // ----- מנעול צף שנגרר -----
        val box = LinearLayout(ui).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val fab = FloatingActionButton(ui).apply {
            setImageResource(R.drawable.ic_lock)
            customSize = dp(72)
            alpha = 0.92f
        }
        box.addView(
            fab,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        box.addView(shadowed(TextView(ui).apply {
            text = "גרור להזזה\nלחיצה ארוכה לפתיחה"
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        }))
        root.addView(
            box,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
            )
        )
        attachDrag(fab, box, root)

        // מיקום ראשוני אחרי שהפריסה מוכנה
        var placed = false
        root.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            if (!placed && r - l > 0 && b - t > 0 && box.width > 0) {
                placed = true
                val maxX = (root.width - box.width).toFloat()
                val maxY = (root.height - box.height).toFloat()
                val sx = Prefs.lockX(this)
                val sy = Prefs.lockY(this)
                box.x = if (sx >= 0f) sx.coerceIn(0f, maxX) else maxX / 2f
                box.y = if (sy >= 0f) sy.coerceIn(0f, maxY) else (maxY - dp(80)).coerceAtLeast(0f)
            }
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        try {
            wm.addView(root, lp)
            overlay = root
        } catch (_: Exception) {
            overlay = null
        }
    }

    /** גרירה = הזזה, החזקה במקום = פתיחת הנעילה */
    @SuppressLint("ClickableViewAccessibility")
    private fun attachDrag(handle: View, box: View, root: View) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0f
        var startY = 0f
        var moved = false

        val longPress = Runnable {
            if (!moved) {
                handle.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                unlock()
            }
        }

        handle.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = box.x
                    startY = box.y
                    moved = false
                    handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!moved && hypot(dx, dy) > slop) {
                        moved = true
                        handler.removeCallbacks(longPress)
                    }
                    if (moved) {
                        box.x = (startX + dx).coerceIn(0f, (root.width - box.width).toFloat())
                        box.y = (startY + dy).coerceIn(0f, (root.height - box.height).toFloat())
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPress)
                    if (moved) Prefs.setLockPos(this, box.x, box.y)
                }
            }
            true
        }
    }

    private fun hideOverlay() {
        handler.removeCallbacksAndMessages(null)
        overlay?.let {
            try {
                wm.removeView(it)
            } catch (_: Exception) {
            }
        }
        overlay = null
    }

    companion object {
        private const val CHANNEL_ID = "calllock"
        private const val NOTIF_ID = 1
    }
}
