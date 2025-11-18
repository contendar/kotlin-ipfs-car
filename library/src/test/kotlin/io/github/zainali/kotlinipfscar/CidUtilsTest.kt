package io.github.contendar.kotlinipfscar

import io.github.contendar.ipfscar.Base32
import org.junit.Assert.assertEquals
import org.junit.Test

class CidUtilsTest {
    @Test
    fun base32EncodeDecodeRoundtrip() {
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val enc = Base32.encodeLower(data)
        val dec = Base32.decodeLowerToBytes(enc)
        assertEquals(data.toList(), dec.toList())
    }
}
