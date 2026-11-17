package com.edu.controlinflarojo

import android.content.Context
import android.hardware.ConsumerIrManager
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Envía códigos NEC por el emisor infrarrojo del teléfono, fuera del hilo de UI. */
class IrSender(context: Context) {

    private val ir = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
    private val executor = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)

    val available: Boolean
        get() = try {
            ir?.hasIrEmitter() == true
        } catch (e: Exception) {
            false
        }

    /** Rangos de portadora que reporta el hardware, para diagnóstico. */
    fun carrierInfo(): String = try {
        ir?.carrierFrequencies
            ?.joinToString { "${it.minFrequency}-${it.maxFrequency} Hz" }
            ?: "sin datos"
    } catch (e: Exception) {
        "sin datos"
    }

    /**
     * Manda una trama. Si todavía se está transmitiendo la anterior, descarta esta
     * (evita que se acumulen al dejar presionado un botón con repetición).
     */
    fun send(code: IrCode): Boolean {
        val mgr = ir ?: return false
        if (!available) return false
        if (!busy.compareAndSet(false, true)) return true
        val pattern = Nec.pattern(code.address, code.sub, code.command)
        executor.execute {
            try {
                mgr.transmit(Nec.CARRIER_HZ, pattern)
            } catch (e: Exception) {
                Log.w(TAG, "Fallo al transmitir $code", e)
            } finally {
                busy.set(false)
            }
        }
        return true
    }

    fun shutdown() {
        executor.shutdownNow()
    }

    companion object {
        private const val TAG = "IrSender"
    }
}
