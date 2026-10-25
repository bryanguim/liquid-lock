package com.bruce.liquidlock

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import androidx.core.graphics.ColorUtils
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** Flou rapide (3 passes de box blur) sur une version réduite de l'image. */
object Blur {
    fun blurred(src: Bitmap, radius: Int, maxDim: Int): Bitmap {
        val scale = max(src.width, src.height).toFloat() / maxDim
        val w = max(1, (src.width / scale).roundToInt())
        val h = max(1, (src.height / scale).roundToInt())
        val small = Bitmap.createScaledBitmap(src, w, h, true)
            .let { if (it !== src && it.isMutable && it.config == Bitmap.Config.ARGB_8888) it else it.copy(Bitmap.Config.ARGB_8888, true) }
        val r = radius.coerceIn(0, 40)
        if (r < 1) return small
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        repeat(3) {
            boxH(px, w, h, r)
            boxV(px, w, h, r)
        }
        small.setPixels(px, 0, w, 0, 0, w, h)
        return small
    }

    private fun boxH(p: IntArray, w: Int, h: Int, r: Int) {
        val out = IntArray(w)
        val div = 2 * r + 1
        for (y in 0 until h) {
            val off = y * w
            var sa = 0; var sr = 0; var sg = 0; var sb = 0
            for (i in -r..r) {
                val c = p[off + i.coerceIn(0, w - 1)]
                sa += c ushr 24; sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF
            }
            for (x in 0 until w) {
                out[x] = ((sa / div) shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val add = p[off + (x + r + 1).coerceAtMost(w - 1)]
                val rem = p[off + (x - r).coerceAtLeast(0)]
                sa += (add ushr 24) - (rem ushr 24)
                sr += ((add shr 16) and 0xFF) - ((rem shr 16) and 0xFF)
                sg += ((add shr 8) and 0xFF) - ((rem shr 8) and 0xFF)
                sb += (add and 0xFF) - (rem and 0xFF)
            }
            System.arraycopy(out, 0, p, off, w)
        }
    }

    private fun boxV(p: IntArray, w: Int, h: Int, r: Int) {
        val out = IntArray(h)
        val div = 2 * r + 1
        for (x in 0 until w) {
            var sa = 0; var sr = 0; var sg = 0; var sb = 0
            for (i in -r..r) {
                val c = p[i.coerceIn(0, h - 1) * w + x]
                sa += c ushr 24; sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF
            }
            for (y in 0 until h) {
                out[y] = ((sa / div) shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val add = p[(y + r + 1).coerceAtMost(h - 1) * w + x]
                val rem = p[(y - r).coerceAtLeast(0) * w + x]
                sa += (add ushr 24) - (rem ushr 24)
                sr += ((add shr 16) and 0xFF) - ((rem shr 16) and 0xFF)
                sg += ((add shr 8) and 0xFF) - ((rem shr 8) and 0xFF)
                sb += (add and 0xFF) - (rem and 0xFF)
            }
            for (y in 0 until h) p[y * w + x] = out[y]
        }
    }
}

/** Fond d'écran : photo choisie ou dégradé avec taches de couleur. */
object BackgroundStore {
    fun photoFile(context: Context) = File(context.filesDir, "background.jpg")

    /** Copie la photo choisie dans l'app (redressée, réduite). Renvoie true si OK. */
    fun savePhoto(context: Context, uri: Uri): Boolean = try {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 2200) sample *= 2
        var bmp = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("decode")
        val rotation = cr.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        if (rotation != 0f) {
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation) }, true)
        }
        FileOutputStream(photoFile(context)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        true
    } catch (e: Exception) {
        false
    }

    fun render(context: Context, s: LockSettings, w: Int, h: Int): Bitmap {
        val scale = minOf(1f, 1440f / max(w, h))
        val bw = max(1, (w * scale).roundToInt())
        val bh = max(1, (h * scale).roundToInt())
        if (s.bgMode == BgMode.PHOTO) {
            loadPhoto(photoFile(context), bw, bh)?.let { return it }
        }
        return gradient(GRADIENTS[s.gradient.coerceIn(0, GRADIENTS.size - 1)], bw, bh)
    }

    private fun loadPhoto(file: File, tw: Int, th: Int): Bitmap? {
        if (!file.exists()) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, o)
        if (o.outWidth <= 0) return null
        var sample = 1
        while (o.outWidth / (sample * 2) >= tw && o.outHeight / (sample * 2) >= th) sample *= 2
        val src = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val out = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        val k = max(tw / src.width.toFloat(), th / src.height.toFloat())
        val dw = src.width * k
        val dh = src.height * k
        val dst = RectF((tw - dw) / 2f, (th - dh) / 2f, (tw + dw) / 2f, (th + dh) / 2f)
        Canvas(out).drawBitmap(src, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private val BLOB_POS = arrayOf(floatArrayOf(0.18f, 0.22f), floatArrayOf(0.88f, 0.52f), floatArrayOf(0.30f, 0.86f))

    fun gradient(g: GradientTheme, w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, w * 0.3f, h.toFloat(), g.base, null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        val radius = max(w, h) * 0.55f
        g.blobs.forEachIndexed { i, color ->
            val pos = BLOB_POS[i % BLOB_POS.size]
            val cx = pos[0] * w
            val cy = pos[1] * h
            p.shader = RadialGradient(
                cx, cy, radius,
                intArrayOf(ColorUtils.setAlphaComponent(color, 210), ColorUtils.setAlphaComponent(color, 70), 0),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
            )
            c.drawCircle(cx, cy, radius, p)
        }
        return out
    }
}

class Scene(val background: Bitmap, val glass: Bitmap)

/** Garde en mémoire les fonds déjà calculés (calcul fait une seule fois). */
object SceneCache {
    private val cache = object : LinkedHashMap<String, Scene>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Scene>?) = size > 2
    }

    fun get(context: Context, s: LockSettings, w: Int, h: Int): Scene {
        val key = "${s.bgMode}|${s.gradient}|${s.photoVersion}|${s.bgBlur}|${s.glassBlur}|$w|$h"
        cache[key]?.let { return it }
        val raw = BackgroundStore.render(context.applicationContext, s, w, h)
        val shown = if (s.bgBlur > 0.01f) Blur.blurred(raw, (1 + s.bgBlur * 12).roundToInt(), 420) else raw
        val glass = Blur.blurred(raw, (2 + s.glassBlur * 10).roundToInt(), 300)
        return Scene(shown, glass).also { cache[key] = it }
    }

    fun clear() = cache.clear()
}
