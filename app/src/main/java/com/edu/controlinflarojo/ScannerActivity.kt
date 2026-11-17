package com.edu.controlinflarojo

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Escáner para encontrar los códigos de la tele.
 *
 * Modo "comando": deja fija la address y recorre comandos 0..255 → para hallar Vol/Canal/OK.
 * Modo "address": deja fijo el comando (ej. 1 = power) y recorre addresses → si la AOC no usa 0x00/0xBD.
 *
 * Cuando la tele reaccione: Pausa, usa ◀ ▶ para confirmar cuál fue y "Asignar a botón…".
 */
class ScannerActivity : Activity() {

    private lateinit var ir: IrSender
    private lateinit var config: Config
    private val handler = Handler(Looper.getMainLooper())

    private var address = Config.DEFAULT_ADDRESS
    private var sub = Config.DEFAULT_SUB
    private var command = 1
    private var standard = false
    private var varyCommand = true
    private var intervalMs = 1500
    private var skipKnown = true

    private var running = false
    private var stepsLeft = 0
    private var updatingInputs = false
    private val history = ArrayDeque<String>()

    private lateinit var addrInput: EditText
    private lateinit var subInput: EditText
    private lateinit var cmdInput: EditText
    private lateinit var intervalInput: EditText
    private lateinit var stdCheck: CheckBox
    private lateinit var current: TextView
    private lateinit var historyText: TextView
    private lateinit var autoButton: Button
    private lateinit var mappingText: TextView

    private val autoTask = object : Runnable {
        override fun run() {
            if (!running) return
            if (stepsLeft <= 0) {
                setRunning(false)
                toast("Vuelta completa (256). Nada más que probar en este modo.")
                return
            }
            stepsLeft--
            step(+1)
            handler.postDelayed(this, intervalMs.toLong())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ir = IrSender(this)
        config = Config(this)
        window.statusBarColor = Ui.BG
        window.navigationBarColor = Ui.BG
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        loadState()
        setContentView(buildUi())
        refreshAll()
    }

    override fun onPause() {
        setRunning(false)
        saveState()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        ir.shutdown()
        super.onDestroy()
    }

    // ---------- estado ----------

    private fun loadState() {
        address = config.getInt("scan_addr", Config.DEFAULT_ADDRESS)
        sub = config.getInt("scan_sub", Config.DEFAULT_SUB)
        command = config.getInt("scan_cmd", 1)
        standard = config.getBool("scan_std", false)
        varyCommand = config.getBool("scan_vary_cmd", true)
        intervalMs = config.getInt("scan_interval", 1500)
        skipKnown = config.getBool("scan_skip_known", true)
    }

    private fun saveState() {
        config.putInt("scan_addr", address)
        config.putInt("scan_sub", sub)
        config.putInt("scan_cmd", command)
        config.putBool("scan_std", standard)
        config.putBool("scan_vary_cmd", varyCommand)
        config.putInt("scan_interval", intervalMs)
        config.putBool("scan_skip_known", skipKnown)
    }

    /** Botones que ya tienen asignado exactamente este código. */
    private fun buttonsWith(code: IrCode): List<TvButton> =
        Buttons.ALL.filter { config.code(it.key) == code }

    private fun currentCode(): IrCode =
        IrCode(address, if (standard) Nec.standardSub(address) else sub, command)

    // ---------- acciones ----------

    private fun step(delta: Int) {
        if (varyCommand) {
            // Al recorrer comandos se brinca los que ya tienen botón (evita apagar la tele o abrir el menú cada vuelta).
            var tries = 0
            do {
                command = (command + delta).mod(256)
                tries++
            } while (skipKnown && tries < 256 && buttonsWith(currentCode()).isNotEmpty())
        } else {
            address = (address + delta).mod(256)
        }
        refreshInputs()
        sendCurrent()
    }

    private fun sendCurrent() {
        if (!ir.available) {
            toast("No hay emisor infrarrojo en este teléfono")
            return
        }
        val code = currentCode()
        ir.send(code)
        history.addFirst(code.toString())
        while (history.size > 6) history.removeLast()
        refreshStatus()
    }

    private fun setRunning(on: Boolean) {
        running = on
        handler.removeCallbacks(autoTask)
        if (on) {
            stepsLeft = 256
            sendCurrent()
            handler.postDelayed(autoTask, intervalMs.toLong())
        }
        if (::autoButton.isInitialized) autoButton.text = if (on) "⏸ Pausa" else "▶ Auto"
    }

    private fun assignDialog() {
        setRunning(false)
        val code = currentCode()
        val labels = Buttons.ALL.map { b ->
            val c = config.code(b.key)
            "${b.name}   ${if (c == null) "—" else "(cmd ${c.command})"}"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Asignar $code a…")
            .setItems(labels) { _, i ->
                val b = Buttons.ALL[i]
                config.setCode(b.key, code)
                toast("${b.name} = $code")
                refreshMapping()
                refreshStatus()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun clearDialog() {
        val labels = Buttons.ALL.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Quitar código de…")
            .setItems(labels) { _, i ->
                config.setCode(Buttons.ALL[i].key, null)
                refreshMapping()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun resetDialog() {
        AlertDialog.Builder(this)
            .setTitle("Restaurar códigos AOC")
            .setMessage("Se borran los códigos asignados y vuelven los del IRDB (NEC 0x00/0xBD).")
            .setPositiveButton("Restaurar") { _, _ ->
                config.resetCodes()
                refreshMapping()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ---------- UI ----------

    private fun buildUi(): View {
        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val p = Ui.dp(ctx, 14)
            setPadding(p, p, p, p)
        }

        root.addView(label("Escáner IR", 22f, Ui.TEXT).apply { gravity = Gravity.CENTER })
        root.addView(label(
            if (ir.available) "Emisor IR OK · portadora: ${ir.carrierInfo()}"
            else "⚠ Este teléfono no tiene emisor infrarrojo",
            12f, if (ir.available) Ui.MUTED else Ui.POWER,
        ).apply { gravity = Gravity.CENTER })

        // Qué se recorre
        val modeGroup = RadioGroup(ctx).apply {
            orientation = RadioGroup.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val rCmd = radio("Recorrer comando")
        val rAddr = radio("Recorrer address")
        modeGroup.addView(rCmd)
        modeGroup.addView(rAddr)
        modeGroup.check(if (varyCommand) rCmd.id else rAddr.id)
        modeGroup.setOnCheckedChangeListener { _, id -> varyCommand = id == rCmd.id }
        root.addView(modeGroup)

        // Campos
        addrInput = numberInput { address = it.coerceIn(0, 255); refreshStatus() }
        subInput = numberInput { sub = it.coerceIn(0, 255); refreshStatus() }
        cmdInput = numberInput { command = it.coerceIn(0, 255); refreshStatus() }
        intervalInput = numberInput { intervalMs = it.coerceIn(200, 10000) }

        root.addView(Ui.row(ctx, field("Address", addrInput), field("Subaddress", subInput), field("Comando", cmdInput)))

        stdCheck = CheckBox(ctx).apply {
            text = "NEC estándar (sub = address invertida)"
            setTextColor(Ui.TEXT)
            isChecked = standard
            setOnCheckedChangeListener { _, checked ->
                standard = checked
                subInput.isEnabled = !checked
                refreshInputs()
            }
        }
        root.addView(stdCheck)
        root.addView(CheckBox(ctx).apply {
            text = "Saltar comandos que ya tienen botón"
            setTextColor(Ui.TEXT)
            isChecked = skipKnown
            setOnCheckedChangeListener { _, checked -> skipKnown = checked }
        })
        root.addView(Ui.row(ctx, field("Intervalo auto (ms)", intervalInput), Ui.spacer(ctx)))

        // Lo que se está mandando
        current = label("", 20f, Ui.TEXT).apply {
            gravity = Gravity.CENTER
            setPadding(0, Ui.dp(ctx, 10), 0, Ui.dp(ctx, 4))
        }
        root.addView(current)
        historyText = label("", 12f, Ui.MUTED).apply { gravity = Gravity.CENTER }
        root.addView(historyText)

        // Controles
        val prev = Ui.key(ctx, "◀", textSize = 22f).apply { setOnClickListener { setRunning(false); step(-1) } }
        val send = Ui.key(ctx, "Enviar", Ui.KEY_ACCENT, 18f).apply { setOnClickListener { setRunning(false); sendCurrent() } }
        val next = Ui.key(ctx, "▶", textSize = 22f).apply { setOnClickListener { setRunning(false); step(+1) } }
        root.addView(Ui.row(ctx, prev, send, next))

        autoButton = Ui.key(ctx, "▶ Auto", Ui.KEY_ACCENT, 18f).apply { setOnClickListener { setRunning(!running) } }
        val assign = Ui.key(ctx, "Asignar a botón…", Ui.POWER, 16f).apply { setOnClickListener { assignDialog() } }
        root.addView(Ui.row(ctx, autoButton, assign))

        // Mapeo actual
        root.addView(label("Códigos del control", 16f, Ui.TEXT).apply {
            setPadding(0, Ui.dp(ctx, 14), 0, Ui.dp(ctx, 4))
        })
        mappingText = label("", 13f, Ui.MUTED).apply { typeface = android.graphics.Typeface.MONOSPACE }
        root.addView(mappingText)

        val clear = Ui.key(ctx, "Quitar código…", textSize = 14f, heightDp = 52).apply { setOnClickListener { clearDialog() } }
        val reset = Ui.key(ctx, "Restaurar AOC", textSize = 14f, heightDp = 52).apply { setOnClickListener { resetDialog() } }
        root.addView(Ui.row(ctx, clear, reset))

        root.addView(label(
            "Tip: en Auto, cuando la tele haga algo pon Pausa y usa ◀ ▶ + Enviar para confirmar " +
                "cuál código fue (a veces reacciona al anterior). Luego \"Asignar a botón…\".",
            12f, Ui.MUTED,
        ).apply { setPadding(0, Ui.dp(ctx, 10), 0, 0) })

        return ScrollView(ctx).apply {
            setBackgroundColor(Ui.BG)
            fitsSystemWindows = true
            addView(root)
        }
    }

    private fun refreshAll() {
        subInput.isEnabled = !standard
        refreshInputs()
        intervalInput.setText(intervalMs.toString())
        refreshMapping()
    }

    private fun refreshInputs() {
        updatingInputs = true
        addrInput.setText(address.toString())
        subInput.setText((if (standard) Nec.standardSub(address) else sub).toString())
        cmdInput.setText(command.toString())
        updatingInputs = false
        refreshStatus()
    }

    private fun refreshStatus() {
        val code = currentCode()
        val owners = buttonsWith(code)
        current.text = if (owners.isEmpty()) code.toString()
        else "$code\n= ${owners.joinToString { it.name }}"
        historyText.text = if (history.isEmpty()) "Aún no se ha enviado nada"
        else "Enviados: " + history.joinToString("\n")
    }

    private fun refreshMapping() {
        mappingText.text = Buttons.ALL.joinToString("\n") { b ->
            val c = config.code(b.key)
            "%-18s %s".format(b.name.take(18), c?.toString() ?: "—")
        }
    }

    // ---------- helpers de vistas ----------

    private fun label(text: String, size: Float, color: Int) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
    }

    private fun radio(text: String) = RadioButton(this).apply {
        this.text = text
        setTextColor(Ui.TEXT)
        id = View.generateViewId()
    }

    private fun numberInput(onValue: (Int) -> Unit) = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_NUMBER
        setTextColor(Ui.TEXT)
        setSingleLine()
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (updatingInputs) return
                s?.toString()?.toIntOrNull()?.let(onValue)
            }
        })
    }

    private fun field(title: String, input: EditText): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            val m = Ui.dp(this@ScannerActivity, 4)
            setMargins(m, m, m, m)
        }
        addView(label(title, 12f, Ui.MUTED))
        addView(input)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
