package com.bruce.liquidlock

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.graphics.ColorUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Dessine tout l'écran verrouillé : fond, horloge en verre liquide, date, raccourcis.
 * Toutes les tailles sont relatives à la largeur de l'écran (u = 1 % de la largeur),
 * donc l'aperçu dans l'app est identique au vrai écran.
 */
class ClockRenderer(private val context: Context) {

    var settings = LockSettings()
    var tiltX = 0f
    var tiltY = 0f
    var shinePhase = -1f
    var contentOffsetY = 0f
    var contentAlpha = 1f
    var torchOn = false
    var showChrome = true

    val torchCircle = RectF()
    val cameraCircle = RectF()

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val glassPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
        val cm = ColorMatrix().apply { setSaturation(1.35f) }
        val bright = 24f
        cm.postConcat(
            ColorMatrix(
                floatArrayOf(
                    1.08f, 0f, 0f, 0f, bright,
                    0f, 1.08f, 0f, 0f, bright,
                    0f, 0f, 1.08f, 0f, bright,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        colorFilter = ColorMatrixColorFilter(cm)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val full = RectF()
    private val tmpRect = RectF()
    private val clockPath = Path()
    private val panePath = Path()
    private val linePath = Path()
    private val iconPath = Path()
    private val circlePath = Path()
    private val clockBounds = RectF()
    private val paneRect = RectF()
    private var pathKey = ""
    private var lastShadowRadius = -1f

    fun draw(canvas: Canvas, w: Int, h: Int, now: Calendar, battery: Int?) {
        if (w <= 0 || h <= 0) return
        val s = settings
        val scene = SceneCache.get(context, s, w, h)
        val u = w / 100f
        full.set(0f, 0f, w.toFloat(), h.toFloat())

        canvas.drawBitmap(scene.background, null, full, bitmapPaint)
        if (s.bgDim > 0.005f) canvas.drawColor(Color.argb((s.bgDim * 255).toInt(), 0, 0, 0))

        val a = contentAlpha.coerceIn(0f, 1f)
        val saved = if (a < 0.995f) canvas.saveLayerAlpha(full, (a * 255).toInt()) else canvas.save()
        canvas.translate(0f, contentOffsetY)

        if (s.style == ClockStyle.ANALOG) drawAnalog(canvas, scene, w, h, u, now)
        else drawDigital(canvas, scene, w, h, u, now)

        drawInfo(canvas, w, u, now, battery)
        if (showChrome) drawChrome(canvas, scene, w, h, u)

        canvas.restoreToCount(saved)
    }

    // ---------------------------------------------------------------- Horloge numérique

    private fun typeface(weight: Int): Typeface =
        Typeface.create(Typeface.create(settings.font.family, Typeface.NORMAL), weight.coerceIn(100, 900), false)

    private fun buildDigital(w: Int, h: Int, u: Float, now: Calendar) {
        val s = settings
        val locale = Locale.getDefault()
        val hh = SimpleDateFormat(if (s.use24h) "HH" else "h", locale).format(now.time)
        val mm = SimpleDateFormat("mm", locale).format(now.time)
        val ss = SimpleDateFormat("ss", locale).format(now.time)

        val key = "${s.hashCode()}|$w|$h|$hh$mm${if (s.showSeconds) ss else ""}"
        if (key == pathKey) return
        pathKey = key

        val base = when (s.style) {
            ClockStyle.STACKED -> 40f
            ClockStyle.INLINE -> 25f
            else -> 15f
        } * u * s.size

        val lines = when (s.style) {
            ClockStyle.STACKED -> buildList {
                add(hh to 1f); add(mm to 1f)
                if (s.showSeconds) add(ss to 0.3f)
            }
            else -> listOf((if (s.showSeconds) "$hh:$mm:$ss" else "$hh:$mm") to (if (s.showSeconds) 0.8f else 1f))
        }

        textPaint.typeface = typeface(s.weight)
        clockPath.reset()
        var baseline = 0f
        lines.forEachIndexed { i, (text, mult) ->
            textPaint.textSize = base * mult
            if (i > 0) baseline += textPaint.textSize * 0.86f + (if (mult < 0.5f) base * 0.08f else 0f)
            val tw = textPaint.measureText(text)
            val x = when (s.align) {
                Align.LEFT -> 0f
                Align.CENTER -> -tw / 2f
                Align.RIGHT -> -tw
            }
            linePath.reset()
            textPaint.getTextPath(text, 0, text.length, x, baseline, linePath)
            clockPath.addPath(linePath)
        }

        clockPath.computeBounds(clockBounds, true)
        val margin = 8f * u
        val dx = when (s.align) {
            Align.LEFT -> margin - clockBounds.left
            Align.CENTER -> w / 2f - clockBounds.centerX()
            Align.RIGHT -> w - margin - clockBounds.right
        }
        val dy = h * s.position - clockBounds.centerY()
        clockPath.offset(dx, dy)
        clockBounds.offset(dx, dy)

        panePath.reset()
        paneRect.set(clockBounds)
        paneRect.inset(-6f * u, -4.5f * u)
        val r = s.corner * min(paneRect.width(), paneRect.height()) / 2f
        panePath.addRoundRect(paneRect, r, r, Path.Direction.CW)
    }

    private fun drawDigital(c: Canvas, scene: Scene, w: Int, h: Int, u: Float, now: Calendar) {
        buildDigital(w, h, u, now)
        val s = settings
        if (s.glassMode != GlassMode.TEXT) drawGlass(c, panePath, paneRect, scene, u, pane = true)
        if (s.glassMode == GlassMode.PANE) {
            fillPaint.shader = null
            fillPaint.color = ColorUtils.setAlphaComponent(s.tint, 245)
            c.drawPath(clockPath, fillPaint)
        } else {
            drawGlass(c, clockPath, clockBounds, scene, u, pane = false)
        }
    }

    // ---------------------------------------------------------------- Horloge à aiguilles

    private fun drawAnalog(c: Canvas, scene: Scene, w: Int, h: Int, u: Float, now: Calendar) {
        val s = settings
        val r = 30f * u * s.size
        val margin = 8f * u
        val cx = when (s.align) {
            Align.LEFT -> margin + r
            Align.CENTER -> w / 2f
            Align.RIGHT -> w - margin - r
        }
        val cy = h * s.position
        clockBounds.set(cx - r, cy - r, cx + r, cy + r)
        paneRect.set(clockBounds)
        panePath.reset()
        panePath.addCircle(cx, cy, r, Path.Direction.CW)
        pathKey = ""

        drawGlass(c, panePath, clockBounds, scene, u, pane = true)

        // Graduations
        strokePaint.shader = null
        strokePaint.color = ColorUtils.setAlphaComponent(s.tint, 210)
        for (i in 0 until 12) {
            val ang = Math.toRadians(i * 30.0 - 90.0)
            val major = i % 3 == 0
            val r1 = r * (if (major) 0.72f else 0.80f)
            val r2 = r * 0.88f
            strokePaint.strokeWidth = if (major) 0.9f * u else 0.5f * u
            c.drawLine(
                cx + (cos(ang) * r1).toFloat(), cy + (sin(ang) * r1).toFloat(),
                cx + (cos(ang) * r2).toFloat(), cy + (sin(ang) * r2).toFloat(), strokePaint
            )
        }

        val hour = now.get(Calendar.HOUR) + now.get(Calendar.MINUTE) / 60f
        val minute = now.get(Calendar.MINUTE) + now.get(Calendar.SECOND) / 60f
        val second = now.get(Calendar.SECOND).toFloat()

        fillPaint.shader = null
        fillPaint.color = ColorUtils.setAlphaComponent(s.tint, 250)
        if (s.shadow) fillPaint.setShadowLayer(1.2f * u, 0f, 0.5f * u, Color.argb(110, 0, 0, 0))
        hand(c, cx, cy, hour * 30f, r * 0.50f, 1.9f * u)
        hand(c, cx, cy, minute * 6f, r * 0.76f, 1.3f * u)
        if (s.showSeconds) {
            fillPaint.color = Color.rgb(255, 159, 10)
            hand(c, cx, cy, second * 6f, r * 0.84f, 0.45f * u)
        }
        fillPaint.clearShadowLayer()
        fillPaint.color = ColorUtils.setAlphaComponent(s.tint, 255)
        c.drawCircle(cx, cy, 1.8f * u, fillPaint)
        fillPaint.color = Color.argb(120, 0, 0, 0)
        c.drawCircle(cx, cy, 0.6f * u, fillPaint)
    }

    private fun hand(c: Canvas, cx: Float, cy: Float, deg: Float, length: Float, width: Float) {
        c.save()
        c.rotate(deg, cx, cy)
        tmpRect.set(cx - width / 2f, cy - length, cx + width / 2f, cy + width * 1.6f)
        c.drawRoundRect(tmpRect, width / 2f, width / 2f, fillPaint)
        c.restore()
    }

    // ---------------------------------------------------------------- Effet verre liquide

    private fun drawGlass(c: Canvas, path: Path, b: RectF, scene: Scene, u: Float, pane: Boolean) {
        val s = settings
        val cx = b.centerX()
        val cy = b.centerY()

        // Ombre douce sous le verre
        if (s.shadow) {
            val radius = u * (if (pane) 3f else 1.6f)
            if (radius != lastShadowRadius) {
                shadowPaint.maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
                lastShadowRadius = radius
            }
            shadowPaint.color = Color.argb(if (pane) 55 else 75, 0, 0, 0)
            c.save()
            c.translate(0f, u * (if (pane) 1.5f else 0.8f))
            c.drawPath(path, shadowPaint)
            c.restore()
        }

        c.save()
        c.clipPath(path)

        // Réfraction : le fond flouté, légèrement grossi et décalé selon l'inclinaison
        c.save()
        c.translate(cx + tiltX * 2.5f * u, cy + tiltY * 2.5f * u)
        c.scale(1.12f, 1.12f)
        c.translate(-cx, -cy)
        c.drawBitmap(scene.glass, null, full, glassPaint)
        c.restore()

        // Givre (teinte)
        c.drawColor(ColorUtils.setAlphaComponent(s.tint, (s.frost * 255 * (if (pane) 0.8f else 1f)).toInt().coerceIn(0, 255)))

        // Lumière interne : haut lumineux, bas légèrement éclairé
        val hl = s.highlight
        fillPaint.shader = LinearGradient(
            0f, b.top, 0f, b.bottom,
            intArrayOf(
                Color.argb((hl * 95).toInt(), 255, 255, 255),
                Color.argb(0, 255, 255, 255),
                Color.argb((hl * 35).toInt(), 255, 255, 255)
            ),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(b, fillPaint)

        // Reflet qui traverse au réveil
        if (shinePhase in 0f..1f) {
            val band = max(b.width(), b.height()) * 0.45f
            val x = b.left - band + (b.width() + 2 * band) * shinePhase
            fillPaint.shader = LinearGradient(
                x - band / 2f, b.top, x + band / 2f, b.bottom,
                intArrayOf(0, Color.argb(160, 255, 255, 255), 0), null, Shader.TileMode.CLAMP
            )
            c.drawRect(b, fillPaint)
        }
        fillPaint.shader = null
        c.restore()

        // Bord lumineux (la lumière vient d'en haut à gauche, bouge avec le téléphone)
        val ang = -2.36f + tiltX * 0.9f + tiltY * 0.4f
        val rr = max(b.width(), b.height()) / 2f
        val dx = cos(ang) * rr
        val dy = sin(ang) * rr
        strokePaint.strokeWidth = if (pane) 0.35f * u else max(1.5f, 0.3f * u * s.size)
        strokePaint.shader = LinearGradient(
            cx + dx, cy + dy, cx - dx, cy - dy,
            intArrayOf(
                Color.argb((hl * 255).toInt(), 255, 255, 255),
                Color.argb((hl * 35).toInt(), 255, 255, 255),
                Color.argb((hl * 170).toInt(), 255, 255, 255)
            ),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        c.drawPath(path, strokePaint)
        strokePaint.shader = null
    }

    // ---------------------------------------------------------------- Date + batterie

    private fun drawInfo(c: Canvas, w: Int, u: Float, now: Calendar, battery: Int?) {
        val s = settings
        val parts = mutableListOf<String>()
        if (s.showDate) {
            val d = SimpleDateFormat(s.dateStyle.pattern, Locale.getDefault()).format(now.time)
            parts += d.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
        if (s.showBattery && battery != null) parts += "$battery %"
        if (parts.isEmpty()) return
        val text = parts.joinToString("  ·  ")

        infoPaint.typeface = typeface(500)
        infoPaint.textSize = 4.4f * u
        infoPaint.color = ColorUtils.setAlphaComponent(s.tint, 235)
        infoPaint.setShadowLayer(1f * u, 0f, 0.3f * u, Color.argb(90, 0, 0, 0))

        val ref = if (s.glassMode != GlassMode.TEXT || s.style == ClockStyle.ANALOG) paneRect else clockBounds
        val below = s.style == ClockStyle.ANALOG || s.style == ClockStyle.COMPACT
        val y = if (below) ref.bottom + 8f * u else ref.top - 3.5f * u

        val x: Float
        if (s.style == ClockStyle.ANALOG) {
            infoPaint.textAlign = Paint.Align.CENTER
            x = ref.centerX()
        } else {
            when (s.align) {
                Align.LEFT -> { infoPaint.textAlign = Paint.Align.LEFT; x = ref.left + (if (ref === paneRect) 6f * u else 0f) }
                Align.CENTER -> { infoPaint.textAlign = Paint.Align.CENTER; x = w / 2f }
                Align.RIGHT -> { infoPaint.textAlign = Paint.Align.RIGHT; x = ref.right - (if (ref === paneRect) 6f * u else 0f) }
            }
        }
        c.drawText(text, x, y, infoPaint)
    }

    // ---------------------------------------------------------------- Raccourcis + indication

    private fun drawChrome(c: Canvas, scene: Scene, w: Int, h: Int, u: Float) {
        val s = settings
        if (s.shortcuts) {
            val r = 6.5f * u
            val cy = h - 11f * u
            torchCircle.set(13f * u - r, cy - r, 13f * u + r, cy + r)
            cameraCircle.set(w - 13f * u - r, cy - r, w - 13f * u + r, cy + r)

            circlePath.reset()
            circlePath.addCircle(torchCircle.centerX(), cy, r, Path.Direction.CW)
            drawGlass(c, circlePath, torchCircle, scene, u, pane = true)
            if (torchOn) {
                fillPaint.shader = null
                fillPaint.color = Color.argb(240, 255, 255, 255)
                c.drawCircle(torchCircle.centerX(), cy, r, fillPaint)
            }
            drawTorchIcon(c, torchCircle.centerX(), cy, u, if (torchOn) Color.BLACK else Color.WHITE)

            circlePath.reset()
            circlePath.addCircle(cameraCircle.centerX(), cy, r, Path.Direction.CW)
            drawGlass(c, circlePath, cameraCircle, scene, u, pane = true)
            drawCameraIcon(c, cameraCircle.centerX(), cy, u, Color.WHITE)
        } else {
            torchCircle.setEmpty()
            cameraCircle.setEmpty()
        }

        // Barre « glisser vers le haut »
        hintPaint.textAlign = Paint.Align.CENTER
        hintPaint.textSize = 3.2f * u
        hintPaint.typeface = typeface(500)
        hintPaint.color = Color.argb(175, 255, 255, 255)
        hintPaint.setShadowLayer(0.8f * u, 0f, 0.2f * u, Color.argb(90, 0, 0, 0))
        c.drawText("Glisser vers le haut", w / 2f, h - 6.5f * u, hintPaint)
        fillPaint.shader = null
        fillPaint.color = Color.argb(215, 255, 255, 255)
        tmpRect.set(w / 2f - 7f * u, h - 3.4f * u, w / 2f + 7f * u, h - 2.3f * u)
        c.drawRoundRect(tmpRect, u, u, fillPaint)
    }

    private fun drawTorchIcon(c: Canvas, cx: Float, cy: Float, u: Float, color: Int) {
        val k = 1.9f * u
        strokePaint.shader = null
        strokePaint.color = color
        strokePaint.strokeWidth = 0.45f * u
        iconPath.reset()
        iconPath.moveTo(cx - 0.95f * k, cy - 1.1f * k)
        iconPath.lineTo(cx + 0.95f * k, cy - 1.1f * k)
        iconPath.lineTo(cx + 0.55f * k, cy - 0.15f * k)
        iconPath.lineTo(cx - 0.55f * k, cy - 0.15f * k)
        iconPath.close()
        c.drawPath(iconPath, strokePaint)
        tmpRect.set(cx - 0.55f * k, cy - 0.15f * k, cx + 0.55f * k, cy + 1.15f * k)
        c.drawRoundRect(tmpRect, 0.2f * k, 0.2f * k, strokePaint)
        fillPaint.shader = null
        fillPaint.color = color
        c.drawCircle(cx, cy + 0.4f * k, 0.13f * k, fillPaint)
    }

    private fun drawCameraIcon(c: Canvas, cx: Float, cy: Float, u: Float, color: Int) {
        val k = 1.9f * u
        strokePaint.shader = null
        strokePaint.color = color
        strokePaint.strokeWidth = 0.45f * u
        tmpRect.set(cx - 1.3f * k, cy - 0.75f * k, cx + 1.3f * k, cy + 0.95f * k)
        c.drawRoundRect(tmpRect, 0.3f * k, 0.3f * k, strokePaint)
        tmpRect.set(cx - 0.45f * k, cy - 1.1f * k, cx + 0.45f * k, cy - 0.75f * k)
        c.drawRoundRect(tmpRect, 0.12f * k, 0.12f * k, strokePaint)
        c.drawCircle(cx, cy + 0.1f * k, 0.45f * k, strokePaint)
    }

    /** 0 = rien, 1 = lampe, 2 = caméra */
    fun hitShortcut(x: Float, y: Float): Int {
        if (!settings.shortcuts || torchCircle.isEmpty) return 0
        val slop = 1.3f
        if (hypot(x - torchCircle.centerX(), y - torchCircle.centerY()) <= torchCircle.width() / 2f * slop) return 1
        if (hypot(x - cameraCircle.centerX(), y - cameraCircle.centerY()) <= cameraCircle.width() / 2f * slop) return 2
        return 0
    }
}
