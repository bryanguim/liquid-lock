package com.bruce.liquidlock

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/** Vue plein écran de l'écran verrouillé (utilisée aussi pour l'aperçu dans l'app). */
@SuppressLint("ViewConstructor")
class LockClockView(context: Context, private val preview: Boolean) : View(context), SensorEventListener {

    private val renderer = ClockRenderer(context)

    var settings: LockSettings
        get() = renderer.settings
        set(value) {
            val old = renderer.settings
            renderer.settings = value
            if (old.gyro != value.gyro) updateSensors()
            if (old.showSeconds != value.showSeconds && isAttachedToWindow) scheduleTick()
            invalidate()
        }

    var onUnlock: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    // ------------------------------------------------------------ Heure

    private val ticker = Runnable {
        invalidate()
        scheduleTick()
    }

    private fun scheduleTick() {
        main.removeCallbacks(ticker)
        val now = System.currentTimeMillis()
        val period = if (renderer.settings.showSeconds) 1000L else 60_000L
        main.postDelayed(ticker, period - now % period + 20)
    }

    // ------------------------------------------------------------ Batterie

    private var battery: Int? = null
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) = readBattery(intent)
    }

    private fun readBattery(intent: Intent?) {
        intent ?: return
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level >= 0 && scale > 0) {
            battery = level * 100 / scale
            invalidate()
        }
    }

    // ------------------------------------------------------------ Gyroscope (reflets dynamiques)

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val rotationSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private var sensing = false
    private val rotMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private var basePitch = 0f
    private var baseRoll = 0f
    private var hasBase = false

    private fun updateSensors() {
        val want = renderer.settings.gyro && isAttachedToWindow && windowVisibility == VISIBLE
        if (want && !sensing && rotationSensor != null) {
            sensorManager?.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
            sensing = true
            hasBase = false
        } else if (!want && sensing) {
            sensorManager?.unregisterListener(this)
            sensing = false
            renderer.tiltX = 0f
            renderer.tiltY = 0f
            invalidate()
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
        SensorManager.getOrientation(rotMatrix, orientation)
        val pitch = orientation[1]
        val roll = orientation[2]
        if (!hasBase) {
            basePitch = pitch; baseRoll = roll; hasBase = true
        }
        // La position « neutre » suit lentement la main : l'effet se recentre tout seul.
        basePitch += (pitch - basePitch) * 0.01f
        baseRoll += (roll - baseRoll) * 0.01f
        val tx = ((roll - baseRoll) / 0.45f).coerceIn(-1f, 1f)
        val ty = ((pitch - basePitch) / 0.45f).coerceIn(-1f, 1f)
        val nx = renderer.tiltX + (tx - renderer.tiltX) * 0.2f
        val ny = renderer.tiltY + (ty - renderer.tiltY) * 0.2f
        if (abs(nx - renderer.tiltX) + abs(ny - renderer.tiltY) > 0.003f) {
            renderer.tiltX = nx
            renderer.tiltY = ny
            invalidate()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    // ------------------------------------------------------------ Lampe torche

    private val cameraManager = context.getSystemService(CameraManager::class.java)
    private val torchId: String? = try {
        val cm: CameraManager? = cameraManager
        cm?.cameraIdList?.firstOrNull { id ->
            cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    } catch (e: Exception) {
        null
    }
    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchId) {
                renderer.torchOn = enabled
                invalidate()
            }
        }
    }

    private fun toggleTorch() {
        val id = torchId ?: return
        try {
            cameraManager?.setTorchMode(id, !renderer.torchOn)
        } catch (_: Exception) {
        }
    }

    private fun openCamera() {
        val secure = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE)
        val normal = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        for (intent in listOf(secure, normal)) {
            try {
                context.startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
    }

    // ------------------------------------------------------------ Cycle de vie

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scheduleTick()
        val sticky = ContextCompat.registerReceiver(
            context, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        readBattery(sticky)
        updateSensors()
        if (!preview && torchId != null) {
            try {
                cameraManager?.registerTorchCallback(torchCallback, main)
            } catch (_: Exception) {
            }
        }
    }

    override fun onDetachedFromWindow() {
        main.removeCallbacks(ticker)
        main.removeCallbacks(longPress)
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {
        }
        if (sensing) {
            sensorManager?.unregisterListener(this)
            sensing = false
        }
        if (!preview) {
            try {
                cameraManager?.unregisterTorchCallback(torchCallback)
            } catch (_: Exception) {
            }
        }
        shineAnim?.cancel()
        settleAnim?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateSensors()
        if (visibility == VISIBLE) {
            scheduleTick()
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        renderer.draw(canvas, width, height, Calendar.getInstance(), battery)
    }

    // ------------------------------------------------------------ Animations

    private var shineAnim: ValueAnimator? = null
    private var settleAnim: ValueAnimator? = null

    fun playShine() {
        if (!renderer.settings.shine) return
        shineAnim?.cancel()
        shineAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1300
            startDelay = 120
            interpolator = DecelerateInterpolator(1.4f)
            addUpdateListener {
                renderer.shinePhase = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    renderer.shinePhase = -1f
                    invalidate()
                }
            })
            start()
        }
    }

    fun reset() {
        settleAnim?.cancel()
        renderer.contentOffsetY = 0f
        renderer.contentAlpha = 1f
        invalidate()
    }

    fun animateBack() = settle(0f, 1f, 280, null)

    fun animateOut() = settle(-height * 0.45f, 0f, 220) { onUnlock?.invoke() }

    private fun settle(targetOffset: Float, targetAlpha: Float, ms: Long, end: (() -> Unit)?) {
        settleAnim?.cancel()
        val startOffset = renderer.contentOffsetY
        val startAlpha = renderer.contentAlpha
        settleAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ms
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                renderer.contentOffsetY = startOffset + (targetOffset - startOffset) * t
                renderer.contentAlpha = startAlpha + (targetAlpha - startAlpha) * t
                invalidate()
            }
            if (end != null) {
                addListener(object : AnimatorListenerAdapter() {
                    private var cancelled = false
                    override fun onAnimationCancel(animation: Animator) {
                        cancelled = true
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        if (!cancelled) end()
                    }
                })
            }
            start()
        }
    }

    // ------------------------------------------------------------ Gestes

    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var pressedShortcut = 0
    private var longPressFired = false
    private var velocity: VelocityTracker? = null

    // Raccourcis : appui long (comme sur iPhone) pour éviter les déclenchements dans la poche.
    private val longPress = Runnable {
        if (pressedShortcut != 0 && !dragging) {
            longPressFired = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            if (pressedShortcut == 1) toggleTorch() else openCamera()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (preview) {
            if (e.actionMasked == MotionEvent.ACTION_UP) playShine()
            return true
        }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                settleAnim?.cancel()
                downX = e.x
                downY = e.y
                dragging = false
                longPressFired = false
                pressedShortcut = renderer.hitShortcut(e.x, e.y)
                velocity?.recycle()
                velocity = VelocityTracker.obtain().also { it.addMovement(e) }
                if (pressedShortcut != 0) main.postDelayed(longPress, 380)
            }

            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(e)
                val dy = e.y - downY
                if (!dragging && hypot(e.x - downX, dy) > touchSlop) {
                    main.removeCallbacks(longPress)
                    if (!longPressFired && abs(dy) > abs(e.x - downX)) dragging = true
                }
                if (dragging) {
                    val off = min(0f, dy)
                    renderer.contentOffsetY = off
                    renderer.contentAlpha = (1f + off / (height * 0.35f)).coerceIn(0f, 1f)
                    invalidate()
                }
            }

            MotionEvent.ACTION_UP -> {
                main.removeCallbacks(longPress)
                velocity?.addMovement(e)
                velocity?.computeCurrentVelocity(1000)
                val vy = velocity?.yVelocity ?: 0f
                velocity?.recycle()
                velocity = null
                if (dragging) {
                    if (-renderer.contentOffsetY > height * 0.16f || vy < -1800f) animateOut() else animateBack()
                }
                dragging = false
                pressedShortcut = 0
            }

            MotionEvent.ACTION_CANCEL -> {
                main.removeCallbacks(longPress)
                velocity?.recycle()
                velocity = null
                dragging = false
                pressedShortcut = 0
                animateBack()
            }
        }
        return true
    }
}
