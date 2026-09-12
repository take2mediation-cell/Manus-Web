package com.take2.chainarrator.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.app.NotificationCompat
import com.take2.chainarrator.NarratorApp
import com.take2.chainarrator.R
import com.take2.chainarrator.data.NarratorBus
import com.take2.chainarrator.data.Settings
import com.take2.chainarrator.databinding.ViewBubbleBinding
import com.take2.chainarrator.ui.MainActivity
import kotlin.math.abs

/**
 * The floating bubble.
 *
 * It is a foreground service because the overlay has to survive the user
 * switching to another app, which is the entire point of it. It carries no
 * business logic: it shows what the narrator is doing and gives the user one
 * reachable control (tap to silence) without leaving the app they are reading.
 */
class BubbleService : Service(), NarratorBus.Observer {

    private lateinit var windowManager: WindowManager
    private lateinit var settings: Settings
    private lateinit var binding: ViewBubbleBinding
    private lateinit var layoutParams: WindowManager.LayoutParams

    private var attached = false
    private var touchSlop = 0

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WindowManager::class.java)
        settings = Settings.get(this)
        touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        startAsForeground()
        addBubble()

        NarratorBus.addObserver(this)
        (application as? NarratorApp)?.narrator?.initialise()
        isRunning = true
    }

    /**
     * From API 29 a foreground service must declare its type at start time, and
     * from API 34 the type must match the manifest or the platform throws.
     */
    private fun startAsForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Refresh the notification so it names the app the user has now chosen.
        getSystemService(android.app.NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification())

        return START_STICKY
    }

    private fun addBubble() {
        if (attached) return

        binding = ViewBubbleBinding.inflate(LayoutInflater.from(this))

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = settings.bubbleX.takeIf { it != Int.MIN_VALUE } ?: defaultX()
            y = settings.bubbleY.takeIf { it != Int.MIN_VALUE } ?: defaultY()
        }

        binding.root.setOnTouchListener(BubbleTouchListener())

        try {
            windowManager.addView(binding.root, layoutParams)
            attached = true
            render()
        } catch (error: Exception) {
            // Almost always the overlay permission being revoked while running.
            stopSelf()
        }
    }

    private fun defaultX(): Int = resources.displayMetrics.widthPixels - dp(72)

    private fun defaultY(): Int = resources.displayMetrics.heightPixels / 3

    private inner class BubbleTouchListener : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var downAt = 0L
        private var dragging = false
        private var longPressFired = false

        private val longPressRunnable = Runnable {
            longPressFired = true
            vibrate(28)
            openSettings()
        }

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = layoutParams.x
                    startY = layoutParams.y
                    downAt = System.currentTimeMillis()
                    dragging = false
                    longPressFired = false
                    view.animate().scaleX(0.92f).scaleY(0.92f).setDuration(90).start()
                    view.postDelayed(longPressRunnable, LONG_PRESS_MS)
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragging = true
                        view.removeCallbacks(longPressRunnable)
                    }
                    if (dragging) {
                        layoutParams.x = startX + dx.toInt()
                        layoutParams.y = startY + dy.toInt()
                        safeUpdate()
                    }
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.removeCallbacks(longPressRunnable)
                    view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()

                    if (dragging) {
                        snapToEdge()
                    } else if (!longPressFired &&
                        event.actionMasked == MotionEvent.ACTION_UP &&
                        System.currentTimeMillis() - downAt < LONG_PRESS_MS
                    ) {
                        onBubbleTapped()
                    }
                    return true
                }
            }
            return false
        }
    }

    /**
     * A tap is the panic button: it silences the narrator immediately and keeps
     * it silent until tapped again. Reaching for settings mid-sentence is no use
     * to anyone, so this is the one control that has to be a single touch.
     */
    private fun onBubbleTapped() {
        val muted = NarratorBus.toggleMuted()
        if (muted) {
            (application as? NarratorApp)?.narrator?.stopSpeaking()
        }
        vibrate(if (muted) 18 else 12)
        render()
    }

    private fun openSettings() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
    }

    private fun snapToEdge() {
        val screenWidth = resources.displayMetrics.widthPixels
        val bubbleWidth = binding.root.width.takeIf { it > 0 } ?: dp(56)
        val margin = dp(8)

        val targetX = if (layoutParams.x + bubbleWidth / 2 < screenWidth / 2) {
            margin
        } else {
            screenWidth - bubbleWidth - margin
        }

        binding.root.animate().cancel()
        animateX(from = layoutParams.x, to = targetX)
    }

    private fun animateX(from: Int, to: Int) {
        val animator = android.animation.ValueAnimator.ofInt(from, to)
        animator.duration = 180
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.addUpdateListener {
            layoutParams.x = it.animatedValue as Int
            safeUpdate()
        }
        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                settings.bubbleX = layoutParams.x
                settings.bubbleY = layoutParams.y
            }
        })
        animator.start()
    }

    private fun safeUpdate() {
        if (!attached) return
        try {
            windowManager.updateViewLayout(binding.root, layoutParams)
        } catch (error: IllegalArgumentException) {
            attached = false
        }
    }

    private fun render() {
        if (!attached) return

        val speaking = NarratorBus.isSpeaking
        val muted = NarratorBus.isMuted

        binding.bubbleSurface.setBackgroundResource(
            if (speaking && !muted) R.drawable.bubble_background_speaking
            else R.drawable.bubble_background
        )
        binding.root.alpha = if (muted) 0.45f else 1f

        binding.root.contentDescription = when {
            muted -> getString(R.string.app_name) + ", muted"
            speaking -> getString(R.string.app_name) + ", reading"
            else -> getString(R.string.app_name) + ", listening"
        }

        if (speaking && !muted) {
            binding.bubbleIcon.animate()
                .scaleX(1.12f).scaleY(1.12f)
                .setDuration(420)
                .withEndAction {
                    binding.bubbleIcon.animate().scaleX(1f).scaleY(1f).setDuration(420).start()
                }
                .start()
        }
    }

    override fun onSpeakingChanged(speaking: Boolean) = render()

    override fun onMutedChanged(muted: Boolean) = render()

    private fun buildNotification(): android.app.Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, BubbleService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val label = settings.targetLabel
        val body = if (label.isNullOrBlank()) {
            getString(R.string.notification_text_no_target)
        } else {
            getString(R.string.notification_text, label)
        }

        return NotificationCompat.Builder(this, NarratorApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_narrator)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(body)
            .setContentIntent(open)
            .addAction(0, getString(R.string.notification_action_stop), stop)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun vibrate(milliseconds: Long) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        } ?: return

        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        isRunning = false
        NarratorBus.removeObserver(this)
        (application as? NarratorApp)?.narrator?.stopSpeaking()

        if (attached) {
            try {
                windowManager.removeView(binding.root)
            } catch (error: IllegalArgumentException) {
                // Already gone; nothing to undo.
            }
            attached = false
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.take2.chainarrator.STOP_BUBBLE"

        private const val NOTIFICATION_ID = 4101
        private const val LONG_PRESS_MS = 480L

        @Volatile
        var isRunning: Boolean = false
            internal set

        fun start(context: Context) {
            context.startForegroundService(Intent(context, BubbleService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BubbleService::class.java))
        }
    }
}
