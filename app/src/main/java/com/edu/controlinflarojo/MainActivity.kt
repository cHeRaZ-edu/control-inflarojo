package com.edu.controlinflarojo

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Pantalla del control: solo botones grandes.
 * El escáner está escondido: mantener presionado el título "Control TV".
 */
class MainActivity : Activity() {

    private lateinit var ir: IrSender
    private lateinit var config: Config
    private val handler = Handler(Looper.getMainLooper())
    private val keyViews = mutableMapOf<String, Button>()
    private var repeatTask: Runnable? = null
    private var lastToast: Toast? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ir = IrSender(this)
        config = Config(this)
        window.statusBarColor = Ui.BG
        window.navigationBarColor = Ui.BG
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        refreshKeys() // por si se asignaron códigos en el escáner
    }

    override fun onPause() {
        stopRepeat()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        ir.shutdown()
        super.onDestroy()
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = Ui.dp(this@MainActivity, 14)
            setPadding(p, p, p, p)
        }

        val title = TextView(this).apply {
            text = "Control TV"
            textSize = 22f
            setTextColor(Ui.TEXT)
            gravity = Gravity.CENTER
            setPadding(0, Ui.dp(this@MainActivity, 8), 0, Ui.dp(this@MainActivity, 12))
            setOnLongClickListener {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                startActivity(Intent(this@MainActivity, ScannerActivity::class.java))
                true
            }
        }
        root.addView(title)

        if (!ir.available) {
            root.addView(TextView(this).apply {
                text = "Este teléfono no tiene emisor infrarrojo (o no está disponible)."
                setTextColor(Ui.POWER)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, Ui.dp(this@MainActivity, 12))
            })
        }

        root.addView(Ui.row(this, key("power", Ui.POWER, 18f), key("input", Ui.KEY_ACCENT), key("mute")))
        root.addView(gap())
        root.addView(Ui.row(this, key("vol_up", Ui.KEY_ACCENT, 22f, 76), key("ch_up", Ui.KEY_ACCENT, 22f, 76)))
        root.addView(Ui.row(this, key("vol_down", Ui.KEY_ACCENT, 22f, 76), key("ch_down", Ui.KEY_ACCENT, 22f, 76)))
        root.addView(gap())
        root.addView(Ui.row(this, key("menu"), key("up"), key("exit")))
        root.addView(Ui.row(this, key("left"), key("ok", Ui.KEY_ACCENT), key("right")))
        root.addView(Ui.row(this, key("info"), key("down"), key("prev")))
        root.addView(gap())
        root.addView(Ui.row(this, key("source", textSize = 16f), key("pc", textSize = 16f), key("hdmi1", textSize = 16f)))
        root.addView(Ui.row(this, key("hdmi2", textSize = 16f), key("component", textSize = 16f), key("av", textSize = 16f)))
        root.addView(gap())
        root.addView(Ui.row(this, key("picture", textSize = 16f), key("wide", textSize = 16f), key("sleep", textSize = 16f)))
        root.addView(gap())
        root.addView(Ui.row(this, key("1"), key("2"), key("3")))
        root.addView(Ui.row(this, key("4"), key("5"), key("6")))
        root.addView(Ui.row(this, key("7"), key("8"), key("9")))
        root.addView(Ui.row(this, Ui.spacer(this), key("0"), Ui.spacer(this)))

        return ScrollView(this).apply {
            setBackgroundColor(Ui.BG)
            fitsSystemWindows = true // Android 15+ fuerza edge-to-edge con targetSdk 35
            isFillViewport = true
            addView(root)
        }
    }

    private fun gap(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, Ui.dp(this@MainActivity, 10))
    }

    private fun key(k: String, color: Int = Ui.KEY, textSize: Float = 20f, heightDp: Int = 64): Button {
        val def = Buttons.byKey(k) ?: error("Botón desconocido: $k")
        val b = Ui.key(this, def.short, color, textSize, heightDp)
        b.contentDescription = def.name
        attachPress(b, def)
        keyViews[k] = b
        return b
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachPress(view: Button, def: TvButton) {
        if (!def.repeat) {
            view.setOnClickListener { press(it, def.key) }
            return
        }
        // Botones con repetición (volumen, canal, flechas): manda al tocar y repite mientras se mantenga.
        view.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    v.isPressed = true
                    if (press(v, def.key)) startRepeat(def.key)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    stopRepeat()
                    if (ev.actionMasked == MotionEvent.ACTION_UP) v.performClick()
                    true
                }
                else -> true
            }
        }
    }

    /** Regresa true si se mandó algo. */
    private fun press(v: View, key: String): Boolean {
        if (!ir.available) {
            toast("No hay emisor infrarrojo en este teléfono")
            return false
        }
        val code = config.code(key)
        if (code == null) {
            toast("Ese botón no tiene código.\nMantén presionado \"Control TV\" para abrir el escáner.")
            return false
        }
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        ir.send(code)
        return true
    }

    private fun startRepeat(key: String) {
        stopRepeat()
        val task = object : Runnable {
            override fun run() {
                config.code(key)?.let { ir.send(it) }
                handler.postDelayed(this, REPEAT_MS)
            }
        }
        repeatTask = task
        handler.postDelayed(task, REPEAT_DELAY_MS)
    }

    private fun stopRepeat() {
        repeatTask?.let { handler.removeCallbacks(it) }
        repeatTask = null
    }

    private fun refreshKeys() {
        for ((k, b) in keyViews) {
            b.alpha = if (config.code(k) != null) 1f else 0.35f
        }
    }

    private fun toast(msg: String) {
        lastToast?.cancel()
        lastToast = Toast.makeText(this, msg, Toast.LENGTH_SHORT).also { it.show() }
    }

    companion object {
        private const val REPEAT_DELAY_MS = 450L
        private const val REPEAT_MS = 160L
    }
}
