package com.handy.app.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.view.View
import kotlin.math.min

/**
 * Seven-layer Glass Lens render — cursorbuddy recipe #1 (scope §15).
 *
 * **Recipes-not-source discipline**: the layer structure and the
 * numeric stop positions come from the recipe in
 * `Handy_Android_Build_Plan_V2_Scope.md` §15 recipe #1. Colour values
 * and paint configurations were authored fresh against that recipe;
 * no `cursorbuddy-android-main/**/*.kt` source was imported. See
 * `DESIGN_NOTES.md` → "cursorbuddy licensing".
 *
 * Public state:
 *  - [lensBaseScale]  — 0.82 at rest, 0.78 while thinking.
 *  - [pulseScale]    — 1.0 base; 1.14 peak during `Pointing` dwell.
 *  - [saturationTint] — overlay tint for state (amber / cyan / teal
 *    / green / etc.).
 *
 * Software-layer is mandatory for `setShadowLayer` to render; this
 * class sets it in the constructor.
 */
@SuppressLint("ViewConstructor")
class LensRenderer(
    context: Context,
) : View(context) {

    enum class Tint { Amber, Cyan, Teal, Green, Blue, Neutral }

    var lensBaseScale: Float = 0.82f
        set(value) {
            val clamped = value.coerceIn(0.5f, 1.1f)
            if (field != clamped) {
                field = clamped
                invalidate()
            }
        }

    var pulseScale: Float = 1.0f
        set(value) {
            val clamped = value.coerceIn(0.8f, 1.3f)
            if (field != clamped) {
                field = clamped
                invalidate()
            }
        }

    var saturationTint: Tint = Tint.Amber
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    // Paints allocated once — never during onDraw.
    private val dropShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x55000000.toInt()
        style = Paint.Style.FILL
        setShadowLayer(14f, 0f, 6f, 0x66000000.toInt())
    }
    private val lensBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val saturationPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val innerShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val specularPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = (min(width, height) / 2f - 6f) * lensBaseScale * pulseScale
        if (radius <= 0f) return

        // Layer 1: drop shadow (offset down a touch for depth).
        canvas.drawCircle(cx, cy + 3f, radius, dropShadowPaint)

        // Layer 2: lens body — very light cool tint, lets background read through.
        lensBodyPaint.shader = RadialGradient(
            cx - radius * 0.25f,
            cy - radius * 0.25f,
            radius * 1.1f,
            intArrayOf(0x30FFFFFF, 0x18AEE9FF, 0x22000000),
            floatArrayOf(0f, 0.65f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, radius, lensBodyPaint)

        // Layer 3: saturation punch — state-specific warm/cool tint.
        val (innerStop, midStop) = saturationColors(saturationTint)
        saturationPaint.shader = RadialGradient(
            cx,
            cy,
            radius * 0.95f,
            intArrayOf(innerStop, midStop, 0x00000000),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, radius, saturationPaint)

        // Layer 4: inner shadow ring — fakes the refracted edge.
        innerShadowPaint.strokeWidth = radius * 0.22f
        innerShadowPaint.shader = RadialGradient(
            cx,
            cy,
            radius,
            intArrayOf(0x00000000, 0x00000000, 0x55000000.toInt()),
            floatArrayOf(0f, 0.78f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, radius - innerShadowPaint.strokeWidth / 2f, innerShadowPaint)

        // Layer 5: chrome sweep rim.
        rimPaint.shader = SweepGradient(
            cx,
            cy,
            intArrayOf(
                0xFFFFFFFF.toInt(),
                0xFFB3E5FC.toInt(),
                0xFF81D4FA.toInt(),
                0xFFFFFFFF.toInt(),
                0xFFB3E5FC.toInt(),
                0xFFFFFFFF.toInt(),
            ),
            null,
        )
        canvas.drawCircle(cx, cy, radius - 1f, rimPaint)

        // Layer 6: top-left specular highlight.
        specularPaint.shader = RadialGradient(
            cx - radius * 0.35f,
            cy - radius * 0.45f,
            radius * 0.55f,
            intArrayOf(0xCCFFFFFF.toInt(), 0x33FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        val highlightRect = RectF(
            cx - radius * 0.7f,
            cy - radius * 0.8f,
            cx + radius * 0.05f,
            cy - radius * 0.1f,
        )
        canvas.drawOval(highlightRect, specularPaint)

        // Layer 7: bottom-right secondary specular.
        specularPaint.shader = RadialGradient(
            cx + radius * 0.4f,
            cy + radius * 0.5f,
            radius * 0.25f,
            intArrayOf(0x66FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx + radius * 0.4f, cy + radius * 0.5f, radius * 0.25f, specularPaint)
    }

    private fun saturationColors(tint: Tint): Pair<Int, Int> = when (tint) {
        Tint.Amber -> 0x66FFB347 to 0x3340E0FF
        Tint.Cyan -> 0x6640E0FF to 0x333B82F6
        Tint.Teal -> 0x6614B8A6 to 0x3334D399
        Tint.Green -> 0x6610B981 to 0x3334D399
        Tint.Blue -> 0x663B82F6 to 0x3340E0FF
        Tint.Neutral -> 0x44AEE9FF to 0x22FFFFFF
    }
}
