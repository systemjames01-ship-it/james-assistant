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

/** Lightweight animated holographic eye/head interface tuned for mid-range Android devices. */
class JamesHologramView(context: Context) : View(context) {
    @Volatile private var mode = 0
    @Volatile private var audioLevel = 0f
    private val d = resources.displayMetrics.density
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()
    private val oval = RectF()
    private val cyan = 0xFF66E8FF.toInt()
    private val ice = 0xFFC8FBFF.toInt()
    private val violet = 0xFFB89AFF.toInt()
    private val green = 0xFF73F0C2.toInt()

    fun setAudioLevel(level: Float) { audioLevel = level.coerceIn(0f, 1f); postInvalidateOnAnimation() }
    fun setMode(nextMode: Int) { mode = nextMode.coerceIn(0, 3); invalidate() }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val t = System.nanoTime() / 1_000_000_000.0
        val accent = when (mode) { 1 -> cyan; 2 -> violet; 3 -> green; else -> cyan }
        val cx = w * .5f
        val cy = h * .47f
        val r = minOf(w * .37f, h * .30f).coerceAtLeast(72f * d)
        val pulse = 1f + (.012f + audioLevel * .035f) * sin(t * 2.6).toFloat()
        val eyeW = r * 1.42f * pulse
        val eyeH = r * (.70f + audioLevel * .10f)
        c.drawColor(0xFF020811.toInt())
        drawGrid(c, w, h, t, accent)
        drawOrbitalRings(c, cx, cy, r, t, accent)

        fill.shader = RadialGradient(cx, cy, r * 2.1f,
            intArrayOf((accent and 0x00FFFFFF) or 0x3A000000, 0x1022B8D0, 0x00000000),
            floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r * 2.1f, fill)
        fill.shader = null

        oval.set(cx-eyeW, cy-eyeH, cx+eyeW, cy+eyeH)
        fill.color = 0xFF061827.toInt()
        c.drawOval(oval, fill)
        stroke.color = accent
        stroke.alpha = 225
        stroke.strokeWidth = 2.1f*d
        c.drawOval(oval, stroke)
        oval.inset(7f*d, 7f*d)
        stroke.alpha = 95
        stroke.strokeWidth = 1f*d
        c.drawOval(oval, stroke)
        drawLid(c, cx, cy, eyeW, eyeH, accent)

        val irisR = r * (.43f + audioLevel*.045f)
        fill.shader = RadialGradient(cx, cy, irisR,
            intArrayOf(ice, accent, 0xFF12617C.toInt(), 0xFF03101D.toInt()),
            floatArrayOf(0f, .22f, .72f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, irisR, fill)
        fill.shader = null
        stroke.color = ice
        stroke.alpha = 210
        stroke.strokeWidth = 1.5f*d
        c.drawCircle(cx, cy, irisR*.92f, stroke)
        for (i in 0 until 64) {
            val a = i * PI / 32.0 + t*.12
            val inner = irisR * if (i % 4 == 0) .69f else .79f
            val outer = irisR * if (i % 4 == 0) .98f else .90f
            stroke.color = if (i % 4 == 0) ice else accent
            stroke.alpha = if (i % 4 == 0) 220 else 95
            stroke.strokeWidth = (if (i % 4 == 0) 1.6f else .7f)*d
            c.drawLine(cx+cos(a).toFloat()*inner, cy+sin(a).toFloat()*inner,
                cx+cos(a).toFloat()*outer, cy+sin(a).toFloat()*outer, stroke)
        }
        fill.color = 0xFF01060D.toInt()
        c.drawCircle(cx, cy, irisR*.40f, fill)
        fill.color = ice
        fill.alpha = 230
        c.drawCircle(cx-irisR*.12f, cy-irisR*.14f, irisR*.09f, fill)
        fill.color = accent
        fill.alpha = 115
        c.drawCircle(cx, cy, irisR*.53f, fill)

        drawScanArcs(c, cx, cy, r, t, accent)
        drawWave(c, cx, h*.83f, w*.29f, t, accent)
        drawHudText(c, w, h, accent)
        postInvalidateOnAnimation()
    }

    private fun drawLid(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, color: Int) {
        path.reset()
        path.moveTo(cx-rx, cy)
        path.cubicTo(cx-rx*.48f, cy-ry*1.65f, cx+rx*.48f, cy-ry*1.65f, cx+rx, cy)
        stroke.color=color
        stroke.alpha=230
        stroke.strokeWidth=2.2f*d
        c.drawPath(path, stroke)
        path.reset()
        path.moveTo(cx-rx, cy)
        path.cubicTo(cx-rx*.45f, cy+ry*1.48f, cx+rx*.45f, cy+ry*1.48f, cx+rx, cy)
        stroke.alpha=155
        stroke.strokeWidth=1.4f*d
        c.drawPath(path, stroke)
        stroke.alpha=80
        stroke.strokeWidth=.7f*d
        for (i in 0..8) {
            val x = cx-rx*.82f + i*rx*.205f
            val y = cy-ry*(.44f + .34f*sin(i*PI/8).toFloat())
            c.drawLine(x,y,x+rx*.025f,y-ry*.13f,stroke)
        }
    }

    private fun drawOrbitalRings(c: Canvas, cx: Float, cy: Float, r: Float, t: Double, color: Int) {
        c.save()
        c.rotate((t*13).toFloat(),cx,cy)
        stroke.color=color
        stroke.alpha=75
        stroke.strokeWidth=1.1f*d
        oval.set(cx-r*1.63f,cy-r*.40f,cx+r*1.63f,cy+r*.40f)
        c.drawOval(oval,stroke)
        c.restore()
        c.save()
        c.rotate((-t*9).toFloat(),cx,cy)
        stroke.color=ice
        stroke.alpha=55
        stroke.strokeWidth=.8f*d
        oval.set(cx-r*1.48f,cy-r*.75f,cx+r*1.48f,cy+r*.75f)
        c.drawOval(oval,stroke)
        c.restore()
        for (i in 0 until 8) {
            val a=i*PI/4.0-t*.22
            fill.color=color
            fill.alpha=if(i%2==0)210 else 95
            c.drawCircle(cx+cos(a).toFloat()*r*1.73f,cy+sin(a).toFloat()*r*.82f,2.1f*d,fill)
        }
    }

    private fun drawScanArcs(c: Canvas,cx:Float,cy:Float,r:Float,t:Double,color:Int) {
        val start=((t*38)%360).toFloat()
        stroke.color=color
        stroke.alpha=95
        stroke.strokeWidth=1f*d
        oval.set(cx-r*1.78f,cy-r*1.18f,cx+r*1.78f,cy+r*1.18f)
        c.drawArc(oval,start,68f,false,stroke)
        stroke.alpha=150
        stroke.strokeWidth=1.2f*d
        oval.inset(7f*d,7f*d)
        c.drawArc(oval,-start,34f,false,stroke)
    }

    private fun drawGrid(c:Canvas,w:Float,h:Float,t:Double,color:Int) {
        stroke.color=color
        stroke.alpha=20
        stroke.strokeWidth=.6f*d
        val step=36f*d
        var x=0f
        while(x<w){c.drawLine(x,0f,x,h,stroke);x+=step}
        var y=0f
        while(y<h){c.drawLine(0f,y,w,y,stroke);y+=step}
        stroke.alpha=48
        stroke.strokeWidth=1f*d
        val scan=(t*26%h).toFloat()
        c.drawLine(0f,scan,w,scan,stroke)
    }

    private fun drawWave(c:Canvas,cx:Float,y:Float,half:Float,t:Double,color:Int) {
        path.reset()
        for(i in 0..80){
            val x=cx-half+2*half*i/80f
            val envelope=sin(i*PI/80).toFloat()
            val wave=sin(i*.48+t*5).toFloat()*(.08f+audioLevel*.42f)*envelope
            if(i==0)path.moveTo(x,y+wave*45f*d) else path.lineTo(x,y+wave*45f*d)
        }
        stroke.color=color
        stroke.alpha=if(mode==1||mode==2)220 else 85
        stroke.strokeWidth=1.4f*d
        c.drawPath(path,stroke)
    }

    private fun drawHudText(c:Canvas,w:Float,h:Float,color:Int) {
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface=android.graphics.Typeface.create("monospace",android.graphics.Typeface.BOLD)
            textSize=9f*d
            this.color=color
            alpha=170
        }
        c.drawText("JÂMES // VISUAL CORE",12f*d,18f*d,p)
        p.textAlign=Paint.Align.RIGHT
        c.drawText(when(mode){1->"LISTENING";2->"SPEAKING";3->"VISION ACTIVE";else->"STANDBY"},w-12f*d,18f*d,p)
        p.textAlign=Paint.Align.LEFT
        p.textSize=8f*d
        p.alpha=120
        c.drawText("OPTICAL SENSOR  ·  NEURAL LINK",12f*d,h-12f*d,p)
    }
}
