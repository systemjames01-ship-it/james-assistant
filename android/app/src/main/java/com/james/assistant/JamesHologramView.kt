package com.james.assistant

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Núcleo holográfico neural do Jâmes.
 * Esfera de conexões tridimensionais, núcleo pulsante e atividade visual por áudio.
 * A animação é apenas visual: sensores só são ativados por autorização explícita.
 */
class JamesHologramView(context: Context) : View(context) {
    @Volatile private var mode = 0
    @Volatile private var audioLevel = 0f

    private data class Node(val x: Float, val y: Float, val z: Float)
    private data class Edge(val a: Int, val b: Int)

    private val d = resources.displayMetrics.density
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()
    private val oval = RectF()
    private val cyan = 0xFF66E8FF.toInt()
    private val ice = 0xFFD8FCFF.toInt()
    private val blue = 0xFF398CFF.toInt()
    private val violet = 0xFFB89AFF.toInt()
    private val green = 0xFF73F0C2.toInt()

    private val nodes: List<Node> = run {
        val count = 76
        val goldenAngle = PI * (3.0 - sqrt(5.0))
        (0 until count).map { i ->
            val y = 1.0 - (i.toDouble() / (count - 1)) * 2.0
            val radius = sqrt(1.0 - y * y)
            val theta = goldenAngle * i
            Node((cos(theta) * radius).toFloat(), y.toFloat(), (sin(theta) * radius).toFloat())
        }
    }
    private val edges: List<Edge> = buildList {
        for (a in nodes.indices) {
            for (b in a + 1 until nodes.size) {
                val dx = nodes[a].x - nodes[b].x
                val dy = nodes[a].y - nodes[b].y
                val dz = nodes[a].z - nodes[b].z
                if (dx * dx + dy * dy + dz * dz < .31f) add(Edge(a, b))
            }
        }
    }

    fun setAudioLevel(level: Float) {
        audioLevel = level.coerceIn(0f, 1f)
        postInvalidateOnAnimation()
    }

    fun setMode(nextMode: Int) {
        mode = nextMode.coerceIn(0, 3)
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val t = System.nanoTime() / 1_000_000_000.0
        val accent = when (mode) { 1 -> cyan; 2 -> violet; 3 -> green; else -> cyan }
        val cx = w * .5f
        val cy = h * .43f
        val radius = minOf(w * .34f, h * .32f).coerceAtLeast(68f * d)
        val pulse = 1f + (.012f + audioLevel * .035f) * sin(t * 2.5).toFloat()

        c.drawColor(0xFF020811.toInt())
        drawGrid(c, w, h, t, accent)
        drawOrbitalRings(c, cx, cy, radius, t, accent)

        fill.shader = RadialGradient(
            cx, cy, radius * 2.15f,
            intArrayOf(0x6637CFFF, 0x2022A9D2, 0x00000000),
            floatArrayOf(0f, .56f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, radius * 2.15f, fill)
        fill.shader = null

        fill.shader = RadialGradient(
            cx - radius * .2f, cy - radius * .25f, radius * 1.25f,
            intArrayOf(0xFF123C59.toInt(), 0xFF071B2C.toInt(), 0xFF020914.toInt()),
            floatArrayOf(0f, .72f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, radius * pulse, fill)
        fill.shader = null
        stroke.color = accent
        stroke.alpha = 130
        stroke.strokeWidth = 1.25f * d
        c.drawCircle(cx, cy, radius * pulse, stroke)
        stroke.alpha = 55
        stroke.strokeWidth = .8f * d
        c.drawCircle(cx, cy, radius * .91f, stroke)

        drawNeuralNetwork(c, cx, cy, radius * pulse, t, accent)
        drawCore(c, cx, cy, radius, t, accent)
        drawScanArcs(c, cx, cy, radius, t, accent)
        drawWave(c, cx, h * .82f, w * .29f, t, accent)
        drawHudText(c, w, h, accent)

        // 30 FPS mantém a esfera fluida sem exigir atualização de 60 FPS contínua.
        postInvalidateDelayed(33)
    }

    private fun drawNeuralNetwork(c: Canvas, cx: Float, cy: Float, r: Float, t: Double, color: Int) {
        val rotation = t * .20
        val tilt = .23
        val projected = ArrayList<FloatArray>(nodes.size)
        for (node in nodes) {
            val x1 = node.x * cos(rotation).toFloat() - node.z * sin(rotation).toFloat()
            val z1 = node.x * sin(rotation).toFloat() + node.z * cos(rotation).toFloat()
            val y1 = node.y * cos(tilt).toFloat() - z1 * sin(tilt).toFloat()
            val z2 = node.y * sin(tilt).toFloat() + z1 * cos(tilt).toFloat()
            val perspective = .82f + z2 * .18f
            projected.add(floatArrayOf(cx + x1 * r * perspective, cy + y1 * r * perspective, z2))
        }

        for (edge in edges) {
            val a = projected[edge.a]
            val b = projected[edge.b]
            val depth = (a[2] + b[2]) * .5f
            if (depth < -.38f) continue
            stroke.color = color
            stroke.alpha = (38 + ((depth + 1f) * 55f)).toInt().coerceIn(25, 150)
            stroke.strokeWidth = (if (depth > .55f) 1.15f else .72f) * d
            c.drawLine(a[0], a[1], b[0], b[1], stroke)
        }

        projected.forEachIndexed { index, point ->
            val depth = point[2]
            if (depth < -.48f) return@forEachIndexed
            val bright = index % 7 == 0 || depth > .62f
            fill.color = if (bright) ice else color
            fill.alpha = (65 + (depth + 1f) * 80f).toInt().coerceIn(45, 220)
            val size = (if (bright) 2.25f else 1.35f) * d
            c.drawCircle(point[0], point[1], size, fill)
            if (bright) {
                stroke.color = color
                stroke.alpha = 75
                stroke.strokeWidth = .7f * d
                c.drawCircle(point[0], point[1], size * 2.5f, stroke)
            }
        }
    }

    private fun drawCore(c: Canvas, cx: Float, cy: Float, r: Float, t: Double, color: Int) {
        val beat = 1f + (.08f + audioLevel * .08f) * sin(t * 3.1).toFloat()
        val coreR = r * .19f * beat
        fill.shader = RadialGradient(
            cx, cy, coreR * 2.5f,
            intArrayOf(ice, color, 0xB822A9E0.toInt(), 0x00000000),
            floatArrayOf(0f, .22f, .56f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, coreR * 2.5f, fill)
        fill.shader = null
        fill.color = 0xFF061322.toInt()
        c.drawCircle(cx, cy, coreR * .46f, fill)
        stroke.color = ice
        stroke.alpha = 230
        stroke.strokeWidth = 1.3f * d
        c.drawCircle(cx, cy, coreR * .58f, stroke)
        drawHeart(c, cx, cy, coreR * .34f, color)
    }

    private fun drawHeart(c: Canvas, cx: Float, cy: Float, size: Float, color: Int) {
        path.reset()
        path.moveTo(cx, cy + size * .72f)
        path.cubicTo(cx - size * 1.45f, cy - size * .05f, cx - size * .78f, cy - size * 1.12f, cx, cy - size * .42f)
        path.cubicTo(cx + size * .78f, cy - size * 1.12f, cx + size * 1.45f, cy - size * .05f, cx, cy + size * .72f)
        fill.color = color
        fill.alpha = 230
        c.drawPath(path, fill)
        stroke.color = ice
        stroke.alpha = 220
        stroke.strokeWidth = .8f * d
        c.drawPath(path, stroke)
    }

    private fun drawOrbitalRings(c: Canvas, cx: Float, cy: Float, r: Float, t: Double, color: Int) {
        c.save()
        c.rotate((t * 11).toFloat(), cx, cy)
        stroke.color = color
        stroke.alpha = 95
        stroke.strokeWidth = 1.1f * d
        oval.set(cx - r * 1.55f, cy - r * .38f, cx + r * 1.55f, cy + r * .38f)
        c.drawOval(oval, stroke)
        c.restore()
        c.save()
        c.rotate((-t * 8).toFloat(), cx, cy)
        stroke.color = ice
        stroke.alpha = 65
        stroke.strokeWidth = .8f * d
        oval.set(cx - r * 1.42f, cy - r * .72f, cx + r * 1.42f, cy + r * .72f)
        c.drawOval(oval, stroke)
        c.restore()
        stroke.color = color
        stroke.alpha = 120
        stroke.strokeWidth = 1f * d
        oval.set(cx - r * 1.72f, cy - r * 1.1f, cx + r * 1.72f, cy + r * 1.1f)
        c.drawArc(oval, ((t * 29) % 360).toFloat(), 54f, false, stroke)
    }

    private fun drawScanArcs(c: Canvas, cx: Float, cy: Float, r: Float, t: Double, color: Int) {
        stroke.color = color
        stroke.alpha = 85
        stroke.strokeWidth = .8f * d
        oval.set(cx - r * 1.16f, cy - r * 1.16f, cx + r * 1.16f, cy + r * 1.16f)
        c.drawArc(oval, ((t * 32) % 360).toFloat(), 72f, false, stroke)
        stroke.alpha = 145
        stroke.strokeWidth = 1.1f * d
        oval.inset(5f * d, 5f * d)
        c.drawArc(oval, (-t * 25 % 360).toFloat(), 28f, false, stroke)
    }

    private fun drawGrid(c: Canvas, w: Float, h: Float, t: Double, color: Int) {
        stroke.color = color
        stroke.alpha = 19
        stroke.strokeWidth = .6f * d
        val step = 36f * d
        var x = 0f
        while (x < w) { c.drawLine(x, 0f, x, h, stroke); x += step }
        var y = 0f
        while (y < h) { c.drawLine(0f, y, w, y, stroke); y += step }
        stroke.alpha = 42
        stroke.strokeWidth = 1f * d
        val scan = (t * 24 % h).toFloat()
        c.drawLine(0f, scan, w, scan, stroke)
    }

    private fun drawWave(c: Canvas, cx: Float, y: Float, half: Float, t: Double, color: Int) {
        path.reset()
        for (i in 0..80) {
            val x = cx - half + 2f * half * i / 80f
            val envelope = sin(i * PI / 80).toFloat()
            val wave = sin(i * .48 + t * 5).toFloat() * (.035f + audioLevel * .34f) * envelope
            if (i == 0) path.moveTo(x, y + wave * 45f * d) else path.lineTo(x, y + wave * 45f * d)
        }
        stroke.color = color
        stroke.alpha = if (mode == 1 || mode == 2) 220 else 90
        stroke.strokeWidth = 1.4f * d
        c.drawPath(path, stroke)
    }

    private fun drawHudText(c: Canvas, w: Float, h: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = android.graphics.Typeface.create("monospace", android.graphics.Typeface.BOLD)
            textSize = 9f * d
            this.color = color
            alpha = 180
        }
        c.drawText("JÂMES // NEURAL CORE", 12f * d, 18f * d, p)
        p.textAlign = Paint.Align.RIGHT
        c.drawText(when (mode) { 1 -> "LISTENING"; 2 -> "THINKING / SPEAKING"; 3 -> "VISION ACTIVE"; else -> "STANDBY" }, w - 12f * d, 18f * d, p)
        p.textAlign = Paint.Align.LEFT
        p.textSize = 8f * d
        p.alpha = 125
        c.drawText("HOLOGRAPHIC NEURAL MATRIX · JAMERSON RAMOS", 12f * d, h - 12f * d, p)
    }
}
