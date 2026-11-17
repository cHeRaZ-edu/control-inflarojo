package com.edu.controlinflarojo

import android.content.Context

/** Un código NEC completo. Cada botón guarda el suyo (address + subaddress + comando). */
data class IrCode(val address: Int, val sub: Int, val command: Int) {
    fun pack(): Int = ((address and 0xFF) shl 16) or ((sub and 0xFF) shl 8) or (command and 0xFF)

    override fun toString(): String = "%s/%s cmd %d (%s)".format(hex(address), hex(sub), command, hex(command))

    companion object {
        fun unpack(v: Int): IrCode? =
            if (v < 0) null else IrCode((v shr 16) and 0xFF, (v shr 8) and 0xFF, v and 0xFF)

        fun hex(v: Int): String = "0x%02X".format(v and 0xFF)
    }
}

/** Botón del control: llave interna, texto corto (en el botón) y nombre largo (en el escáner). */
data class TvButton(val key: String, val short: String, val name: String, val repeat: Boolean = false)

object Buttons {
    val ALL = listOf(
        TvButton("power", "ON/OFF", "Encender / Apagar"),
        TvButton("input", "Input", "Input (menú de entradas)"),
        TvButton("mute", "Mute", "Silencio"),
        TvButton("source", "TV/AV", "TV / Video"),
        TvButton("pc", "PC", "PC / VGA"),
        TvButton("hdmi1", "HDMI 1", "HDMI 1 directo"),
        TvButton("hdmi2", "HDMI 2", "HDMI 2 directo"),
        TvButton("component", "Comp", "Componente directo"),
        TvButton("av", "AV", "AV directo"),
        TvButton("picture", "Imagen", "Modo de imagen (Vívidos/Película/Personal)"),
        TvButton("wide", "Formato", "Formato de pantalla"),
        TvButton("sleep", "Sleep", "Temporizador de apagado"),
        TvButton("vol_up", "Vol +", "Subir volumen", repeat = true),
        TvButton("vol_down", "Vol −", "Bajar volumen", repeat = true),
        TvButton("ch_up", "Canal +", "Subir canal", repeat = true),
        TvButton("ch_down", "Canal −", "Bajar canal", repeat = true),
        TvButton("menu", "Menú", "Menú"),
        TvButton("exit", "Salir", "Salir / Regresar"),
        TvButton("info", "Info", "Info"),
        TvButton("prev", "↺", "Canal anterior"),
        TvButton("up", "▲", "Arriba", repeat = true),
        TvButton("down", "▼", "Abajo", repeat = true),
        TvButton("left", "◀", "Izquierda", repeat = true),
        TvButton("right", "▶", "Derecha", repeat = true),
        TvButton("ok", "OK", "OK / Enter"),
    ) + (1..9).map { TvButton("$it", "$it", "Número $it") } + TvButton("0", "0", "Número 0")

    fun byKey(key: String): TvButton? = ALL.firstOrNull { it.key == key }
}

class Config(context: Context) {

    private val prefs = context.getSharedPreferences("control", Context.MODE_PRIVATE)

    fun code(key: String): IrCode? {
        val stored = "code_$key"
        if (prefs.contains(stored)) return IrCode.unpack(prefs.getInt(stored, -1))
        val cmd = DEFAULT_COMMANDS[key] ?: return null
        return IrCode(DEFAULT_ADDRESS, DEFAULT_SUB, cmd)
    }

    fun setCode(key: String, code: IrCode?) {
        prefs.edit().putInt("code_$key", code?.pack() ?: -1).apply()
    }

    /** Borra todo lo asignado y regresa a los códigos AOC del IRDB. */
    fun resetCodes() {
        val e = prefs.edit()
        prefs.all.keys.filter { it.startsWith("code_") }.forEach { e.remove(it) }
        e.apply()
    }

    fun getInt(key: String, def: Int): Int = prefs.getInt(key, def)
    fun putInt(key: String, v: Int) = prefs.edit().putInt(key, v).apply()
    fun getBool(key: String, def: Boolean): Boolean = prefs.getBoolean(key, def)
    fun putBool(key: String, v: Boolean) = prefs.edit().putBoolean(key, v).apply()

    companion object {
        // IRDB: codes/AOC/TV/0,189.csv  → NEC, device 0, subdevice 189
        const val DEFAULT_ADDRESS = 0
        const val DEFAULT_SUB = 189

        val DEFAULT_COMMANDS = mapOf(
            "power" to 1,
            "mute" to 4,
            "source" to 5,
            "menu" to 10,
            "right" to 12,
            "left" to 16,
            "up" to 24,
            "down" to 28,
            "info" to 64,
            "prev" to 68,
            "pc" to 69,
            // Encontrados con el escáner en la LC42H163:
            "picture" to 3,
            "hdmi1" to 6,
            "sleep" to 19,
            "wide" to 20,
            "1" to 21, "2" to 22, "3" to 23,
            "4" to 25, "5" to 26, "6" to 27,
            "7" to 29, "8" to 30, "9" to 31,
            "0" to 65,
            // vol_up, vol_down, ch_up, ch_down, ok, exit, input, hdmi1/2, component, av:
            // no vienen en el IRDB → encontrarlos con el escáner
        )
    }
}
