package com.edu.controlinflarojo

/**
 * Codificador del protocolo NEC (el que usan casi todas las teles AOC/TPV).
 *
 * Trama: 9 ms marca + 4.5 ms espacio, luego 32 bits LSB primero:
 *   address (8) | subaddress (8) | comando (8) | ~comando (8)
 * Bit = 560 µs de marca + 560 µs (0) o 1690 µs (1) de espacio. Cierra con 560 µs de marca.
 *
 * En NEC "estándar" subaddress = ~address. Las AOC del IRDB usan NEC extendido (0x00 / 0xBD).
 */
object Nec {
    const val CARRIER_HZ = 38000

    private const val HDR_MARK = 9000
    private const val HDR_SPACE = 4500
    private const val BIT_MARK = 560
    private const val ONE_SPACE = 1690
    private const val ZERO_SPACE = 560

    /** Patrón en microsegundos alternando marca/espacio, listo para ConsumerIrManager.transmit(). */
    fun pattern(address: Int, subaddress: Int, command: Int): IntArray {
        val bytes = intArrayOf(
            address and 0xFF,
            subaddress and 0xFF,
            command and 0xFF,
            command.inv() and 0xFF,
        )
        val out = IntArray(2 + 32 * 2 + 1)
        var i = 0
        out[i++] = HDR_MARK
        out[i++] = HDR_SPACE
        for (b in bytes) {
            for (bit in 0 until 8) {
                out[i++] = BIT_MARK
                out[i++] = if (((b shr bit) and 1) == 1) ONE_SPACE else ZERO_SPACE
            }
        }
        out[i] = BIT_MARK
        return out
    }

    /** Subaddress que corresponde en NEC estándar (address invertida). */
    fun standardSub(address: Int): Int = address.inv() and 0xFF
}
