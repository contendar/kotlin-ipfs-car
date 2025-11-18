package io.github.contendar.ipfscar

import kotlin.text.iterator

/**
 * Minimal Base32 (RFC4648) encoder/decoder used for multibase CID encoding.
 * Produces lowercase output without padding.
 */
object Base32 {
    private val ALPHABET = "abcdefghijklmnopqrstuvwxyz234567"

    /**
     * Encode bytes into lowercase base32 string (no padding).
     *
     * @param data bytes to encode
     * @return base32 lowercase string (no multibase prefix)
     */
    fun encodeLower(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val output = StringBuilder()
        var buffer = 0
        var bitsLeft = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                val index = (buffer shr (bitsLeft - 5)) and 0x1F
                bitsLeft -= 5
                output.append(ALPHABET[index])
            }
        }
        if (bitsLeft > 0) {
            val index = (buffer shl (5 - bitsLeft)) and 0x1F
            output.append(ALPHABET[index])
        }
        return output.toString()
    }

    /**
     * Decode a lowercase base32 string into bytes.
     *
     * @param input base32 lowercase string (no padding)
     * @return decoded bytes
     * @throws IllegalArgumentException for invalid characters
     */
    fun decodeLowerToBytes(input: String): ByteArray {
        val cleaned = input.trim().lowercase()
        if (cleaned.isEmpty()) return ByteArray(0)
        val bits = ArrayList<Int>()
        for (ch in cleaned) {
            val idx = ALPHABET.indexOf(ch)
            if (idx < 0) throw IllegalArgumentException("Invalid base32 char: $ch")
            for (i in 4 downTo 0) bits.add((idx shr i) and 1)
        }
        val bytes = ArrayList<Byte>()
        var i = 0
        while (i + 7 < bits.size) {
            var v = 0
            for (j in 0..7) {
                v = (v shl 1) or bits[i + j]
            }
            bytes.add(v.toByte())
            i += 8
        }
        return bytes.toByteArray()
    }
}
