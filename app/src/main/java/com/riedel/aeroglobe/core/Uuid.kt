package com.riedel.aeroglobe.core

import java.security.SecureRandom

/**
 * UUID version 7 (RFC 9562): 48 bits of Unix-millisecond timestamp, then random.
 *
 * Time-ordered ids are what make the phase-5 sync cheap. Rows created on two
 * devices still sort into rough creation order, so "give me everything after
 * <id>" is one indexed range scan instead of a full table diff. Random v4 ids
 * would scatter and force us to keep a separate sequence column.
 */
object Uuid {
    private val random = SecureRandom()
    private const val HEX = "0123456789abcdef"

    fun v7(nowMillis: Long = System.currentTimeMillis()): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        for (i in 0..5) {
            bytes[i] = ((nowMillis shr (8 * (5 - i))) and 0xFF).toByte()
        }
        // Version 7 in the top nibble of byte 6, RFC 9562 variant bits in byte 8.
        bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x70).toByte()
        bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()

        return buildString(36) {
            for (i in bytes.indices) {
                if (i == 4 || i == 6 || i == 8 || i == 10) append('-')
                val b = bytes[i].toInt()
                append(HEX[(b shr 4) and 0xF])
                append(HEX[b and 0xF])
            }
        }
    }
}
