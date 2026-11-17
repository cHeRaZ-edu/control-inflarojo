package com.edu.controlinflarojo

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout

/** Helpers para armar la UI por código (sin XML ni AndroidX). */
object Ui {
    const val BG = 0xFF121212.toInt()
    const val KEY = 0xFF2B2B2B.toInt()
    const val KEY_ACCENT = 0xFF3D3D3D.toInt()
    const val POWER = 0xFFC62828.toInt()
    const val TEXT = 0xFFF2F2F2.toInt()
    const val MUTED = 0xFF9E9E9E.toInt()

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    fun keyBackground(ctx: Context, color: Int): RippleDrawable {
        val shape = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(ctx, 14).toFloat()
        }
        return RippleDrawable(ColorStateList.valueOf(0x55FFFFFF), shape, null)
    }

    fun key(ctx: Context, label: String, color: Int = KEY, textSize: Float = 20f, heightDp: Int = 64): Button =
        Button(ctx).apply {
            text = label
            isAllCaps = false
            setTextColor(TEXT)
            this.textSize = textSize
            background = keyBackground(ctx, color)
            stateListAnimator = null
            layoutParams = LinearLayout.LayoutParams(0, dp(ctx, heightDp), 1f).apply {
                val m = dp(ctx, 5)
                setMargins(m, m, m, m)
            }
        }

    fun row(ctx: Context, vararg views: View): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            views.forEach { addView(it) }
        }

    /** Hueco del mismo ancho que un botón, para alinear el 0 al centro. */
    fun spacer(ctx: Context): View = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(0, dp(ctx, 1), 1f)
        setBackgroundColor(Color.TRANSPARENT)
    }
}
