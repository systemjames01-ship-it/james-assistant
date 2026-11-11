package com.james.assistant

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Layered, low-cost holographic HUD designed for the Galaxy A13. */
class JamesHologramView(context: Context) : View(context) {
    @Volatile private var mode = 0 // 0 idle, 1 listening, 2 speaking, 3 vision
    private val density = resources.displayMetrics.density
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val bounds = RectF()
    private val cyan = 0xFF66E8FF.toInt()
    private val pale = 0xFFB7F7FF.toInt()
    private val violet = 0xFFB99AFF.toInt()
    private val green = 0xFF73F0C2.toInt()

    fun setMode(nextMode: Int) {
        val next = nextMode.coerceIn(0, 3)
        if (mode != next) { mode = next; invalidate() }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val t = System.nanoTime() / 1_000_000_000.0
        val accent = when (mode) { 1 -> 0xFF56D9FF.toInt(); 2 -> violet; 3 -> green; else -> cyan }
        val cx = w * .5f
        val cy = h * .47f
        val r = minOf(w * .285f, h * .285f).coerceAtLeast(64f * density)
        val pulse = 1f + (if (mode == 2) .035f else .014f) * sin(t * 2.4).toFloat()
        val radius = r * pulse

        canvas.drawColor(0xFF030A12.toInt())
        drawBackgroundGrid(canvas, w, h, t, accent)
        drawCornerBrackets(canvas, w, h, accent)

        fill.shader = android.graphics.RadialGradient(cx, cy, radius * 1.65f,
            intArrayOf((accent and 0x00FFFFFF) or 0x38200000, (accent and 0x00FFFFFF) or 0x18200000, 0x00000000),
            floatArrayOf(0f, .64f, 1f), android.graphics.Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius * 1.65f, fill)
        fill.shader = null

        drawOrbit(canvas, cx, cy, radius * 1.25f, .28f, t * .42, accent, 115)
        drawOrbit(canvas, cx, cy, radius * 1.43f, .58f, -t * .31, pale, 72)
        drawOrbit(canvas, cx, cy, radius * 1.62f, .20f, t * .22, accent, 48)

        // Rotating spherical wireframe.
        val rotY = t * .24
        val rotX = .32 + sin(t * .17) * .08
        val points = 24
        for (latIndex in -5..5) {
            val lat = latIndex * PI / 12.0
            path.reset()
            for (i in 0..points) {
                val lon = (i.toDouble() / points) * PI * 2.0
                val p = projectSphere(cx, cy, radius * .91f, lat, lon + rotY, rotX)
                if (i == 0) path.moveTo(p.first, p.second) else path.lineTo(p.first, p.second)
            }
            line.color = accent
            line.alpha = if (latIndex == 0) 125 else 58
            line.strokeWidth = (if (latIndex == 0) 1.25f else .7f) * density
            canvas.drawPath(path, line)
        }
        for (lonIndex in 0 until 18) {
            val lon = lonIndex * PI / 9.0 + rotY
            path.reset()
            for (i in 0..points) {
                val lat = -PI / 2 + (i.toDouble() / points) * PI
                val p = projectSphere(cx, cy, radius * .91f, lat, lon, rotX)
                if (i == 0) path.moveTo(p.first, p.second) else path.lineTo(p.first, p.second)
            }
            line.color = accent
            line.alpha = if (lonIndex % 3 == 0) 86 else 42
            line.strokeWidth = .7f * density
            canvas.drawPath(path, line)
        }

        // Central reactor aperture.
        fill.color = (accent and 0x00FFFFFF) or 0x1E000000
        canvas.drawCircle(cx, cy, radius * .34f, fill)
        line.color = pale; line.alpha = 220; line.strokeWidth = 1.4f * density
        canvas.drawCircle(cx, cy, radius * .30f, line)
        line.alpha = 110; line.strokeWidth = .8f * density
        canvas.drawCircle(cx, cy, radius * .23f, line)
        fill.color = pale; fill.alpha = 225
        canvas.drawCircle(cx, cy, radius * .045f, fill)
        fill.color = accent; fill.alpha = 85
        canvas.drawCircle(cx, cy, radius * .12f, fill)

        // Radial ticks and data packets.
        for (i in 0 until 36) {
            val a = i * PI / 18.0 + t * .12
            val inner = radius * 1.07f
            val outer = radius * if (i % 3 == 0) 1.16f else 1.11f
            line.color = accent; line.alpha = if (i % 3 == 0) 175 else 75
            line.strokeWidth = (if (i % 3 == 0) 1.2f else .65f) * density
            canvas.drawLine(cx + cos(a).toFloat() * inner, cy + sin(a).toFloat() * inner,
                cx + cos(a).toFloat() * outer, cy + sin(a).toFloat() * outer, line)
            if (i % 3 == 0) {
                fill.color = pale; fill.alpha = 175
                canvas.drawCircle(cx + cos(a).toFloat() * radius * 1.20f,
                    cy + sin(a).toFloat() * radius * 1.20f, 1.7f * density, fill)
            }
        }

        drawTelemetry(canvas, w, cx, cy, radius, t, accent)
        drawWaveform(canvas, cx, h * .82f, w * .28f, t, accent)
        drawLabels(canvas, w, h, accent)
        postInvalidateOnAnimation()
    }

    private fun projectSphere(cx: Float, cy: Float, r: Float, lat: Double, lon: Double, tilt: Double): Pair<Float, Float> {
        val x = cos(lat) * sin(lon)
        val y = sin(lat)
        val z = cos(lat) * cos(lon)
        val yy = y * cos(tilt) - z * sin(tilt)
        val zz = y * sin(tilt) + z * cos(tilt)
        val perspective = 1.0 / (1.0 + zz * .16)
        return Pair(cx + (x * r * perspective).toFloat(), cy + (yy * r * perspective).toFloat())
    }

    private fun drawOrbit(c: Canvas, cx: Float, cy: Float, r: Float, squash: Float, angle: Double, color: Int, alpha: Int) {
        c.save()
        c.rotate(Math.toDegrees(angle).toFloat(), cx, cy)
        line.color = color; line.alpha = alpha; line.strokeWidth = 1.05f * density
        bounds.set(cx-r, cy-r*squash, cx+r, cy+r*squash)
        c.drawOval(bounds, line)
        line.alpha = (alpha * .48f).toInt()
        bounds.set(cx-r*1.035f, cy-r*squash*1.035f, cx+r*1.035f, cy+r*squash*1.035f)
        c.drawOval(bounds, line)
        fill.color = color; fill.alpha = 210
        c.drawCircle(cx+r, cy, 2.3f*density, fill)
        c.drawCircle(cx-r, cy, 1.6f*density, fill)
        c.restore()
    }

    private fun drawBackgroundGrid(c: Canvas, w: Float, h: Float, t: Double, accent: Int) {
        line.color = accent; line.alpha = 16; line.strokeWidth = .6f*density
        val step = 28f*density
        var x = (-(t*4 % step).toFloat())
        while (x < w) { c.drawLine(x, 0f, x, h, line); x += step }
        var y = 0f
        while (y < h) { c.drawLine(0f, y, w, y, line); y += step }
    }

    private fun drawCornerBrackets(c: Canvas, w: Float, h: Float, accent: Int) {
        line.color = accent; line.alpha = 115; line.strokeWidth = 1.2f*density
        val m = 13f*density; val l = 17f*density
        val corners = arrayOf(floatArrayOf(m,m,1f,1f), floatArrayOf(w-m,m,-1f,1f),
            floatArrayOf(m,h-m,1f,-1f), floatArrayOf(w-m,h-m,-1f,-1f))
        corners.forEach { p ->
            c.drawLine(p[0], p[1], p[0]+l*p[2], p[1], line)
            c.drawLine(p[0], p[1], p[0], p[1]+l*p[3], line)
        }
    }

    private fun drawTelemetry(c: Canvas, w: Float, cx: Float, cy: Float, r: Float, t: Double, accent: Int) {
        line.color = accent; line.alpha = 75; line.strokeWidth = .7f*density
        c.drawLine(12f*density, cy, 34f*density, cy, line)
        c.drawLine(w-34f*density, cy, w-12f*density, cy, line)
        text.color = accent; text.alpha = 175; text.textSize = 8f*density
        text.typeface = Typeface.create("monospace", Typeface.NORMAL)
        c.drawText("CORE MATRIX", 12f*density, cy-9f*density, text)
        text.textAlign = Paint.Align.RIGHT
        c.drawText("NEURAL LINK", w-12f*density, cy-9f*density, text)
        text.textAlign = Paint.Align.LEFT
        val sys = when (mode) { 1 -> "LISTEN"; 2 -> "SPEAK"; 3 -> "VISION"; else -> "STANDBY" }
        c.drawText("SYS " + sys, 12f*density, cy+13f*density, text)
        text.textAlign = Paint.Align.RIGHT
        c.drawText("SYNC " + (96+3*sin(t).toInt()) + "%", w-12f*density, cy+13f*density, text)
        text.textAlign = Paint.Align.LEFT
        for (i in 0 until 8) {
            val a = t*(.22+i*.012)+i*PI/4
            val rr = r*(1.34+(i%2)*.12)
            fill.color = accent; fill.alpha = 125
            c.drawCircle(cx+cos(a).toFloat()*rr, cy+sin(a).toFloat()*rr*.56f, 1.5f*density, fill)
        }
    }

    private fun drawWaveform(c: Canvas, cx: Float, y: Float, width: Float, t: Double, accent: Int) {
        line.color = accent; line.alpha = 115; line.strokeWidth = 1.1f*density
        val bars = 40
        val gap = width/bars
        for (i in 0 until bars) {
            val envelope = if (mode == 0) .18 else if (mode == 1) .48 else .78
            val wave = (.15 + .85*kotlin.math.abs(sin(t*(if (mode == 2) 8.0 else 3.2)+i*.52)))*envelope
            val bh = (3f+wave.toFloat()*17f)*density
            val x = cx-width/2+i*gap
            c.drawLine(x, y-bh, x, y+bh, line)
        }
    }

    private fun drawLabels(c: Canvas, w: Float, h: Float, accent: Int) {
        text.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        text.textSize = 12f*density; text.color = pale; text.alpha = 235
        c.drawText("J Â M E S", 16f*density, 24f*density, text)
        text.textSize = 7.5f*density; text.color = accent; text.alpha = 170
        text.typeface = Typeface.create("monospace", Typeface.NORMAL)
        c.drawText("PERSONAL INTELLIGENCE SYSTEM", 16f*density, 37f*density, text)
        text.textAlign = Paint.Align.RIGHT
        val state = when (mode) { 1 -> "LISTENING"; 2 -> "SPEAKING"; 3 -> "VISION ACTIVE"; else -> "SYSTEM ONLINE" }
        c.drawText(state, w-16f*density, 24f*density, text)
        c.drawText("REALTIME / PT-BR", w-16f*density, 37f*density, text)
        text.textAlign = Paint.Align.LEFT
        text.textSize = 7f*density
        c.drawText("NEURAL PROCESSING", 16f*density, h-22f*density, text)
        c.drawText("SECURE SESSION", w-91f*density, h-22f*density, text)
    }
}
