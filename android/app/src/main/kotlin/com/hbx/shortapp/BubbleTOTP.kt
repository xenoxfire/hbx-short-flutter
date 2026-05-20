package com.hbx.shortapp

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.and

/**
 * Minimal TOTP (RFC-6238) generator — no external library needed.
 * Reads clipboard base32 secret → returns 6-digit OTP.
 */
object BubbleTOTP {

    fun generate(secretBase32: String): String {
        val cleaned = secretBase32.trim().uppercase().replace(" ", "").replace("-", "")
        val key     = base32Decode(cleaned)
        val counter = System.currentTimeMillis() / 1000L / 30L
        val data    = ByteArray(8) { i -> (counter shr (56 - 8 * i)).toByte() }

        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash   = mac.doFinal(data)
        val offset = (hash.last() and 0x0f).toInt()

        val code = ((hash[offset]     and 0x7f).toInt() shl 24) or
                   ((hash[offset + 1] and 0xff.toByte()).toInt() shl 16) or
                   ((hash[offset + 2] and 0xff.toByte()).toInt() shl 8)  or
                   ((hash[offset + 3] and 0xff.toByte()).toInt())

        return (code % 1_000_000).toString().padStart(6, '0')
    }

    private fun base32Decode(input: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var bits  = 0
        var value = 0
        val out   = mutableListOf<Byte>()
        for (ch in input) {
            if (ch == '=') break
            val idx = alphabet.indexOf(ch)
            if (idx < 0) continue
            value = (value shl 5) or idx
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.add(((value shr bits) and 0xFF).toByte())
            }
        }
        return out.toByteArray()
    }
}
