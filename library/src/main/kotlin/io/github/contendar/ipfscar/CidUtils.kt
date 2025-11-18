package io.github.contendar.ipfscar

import java.io.File
import java.security.MessageDigest

/**
 * CID utilities: minimal CIDv1 builder from a SHA-256 digest.
 * Produces multibase base32 (lowercase) CIDv1 strings with provided codec.
 */
object CidUtils {
    private const val SHA2_256_CODE = 0x12

    // Encode unsigned varint (LEB128)
    private fun varintEncode(value: Long): ByteArray {
        var v = value
        val out = ArrayList<Byte>()
        while (true) {
            var b = (v and 0x7F).toInt()
            v = v ushr 7
            if (v != 0L) b = b or 0x80
            out.add(b.toByte())
            if (v == 0L) break
        }
        return out.toByteArray()
    }

    /**
     * Build CIDv1 string from a raw SHA-256 digest and a multicodec number.
     *
     * @param digest raw sha256 digest bytes (32 bytes)
     * @param codec multicodec integer (e.g. 0x70 for dag-pb, 0x55 for raw)
     * @return CIDv1 base32 string starting with 'b'
     */
    fun cidV1FromSha256Digest(digest: ByteArray, codec: Int): String {
        // multihash = varint(hashfn) + varint(length) + digest
        val mhFn = varintEncode(SHA2_256_CODE.toLong())
        val mhLen = varintEncode(digest.size.toLong())
        val mh = mhFn + mhLen + digest

        // cid = varint(version=1) + varint(codec) + multihash
        val version = varintEncode(1L)
        val codecVar = varintEncode(codec.toLong())
        val cidBytes = version + codecVar + mh

        // base32 (lowercase) with 'b' prefix (multibase)
        val encoded = Base32.encodeLower(cidBytes)
        return "b$encoded"
    }

    private operator fun ByteArray.plus(other: ByteArray): ByteArray {
        val out = ByteArray(this.size + other.size)
        System.arraycopy(this, 0, out, 0, this.size)
        System.arraycopy(other, 0, out, this.size, other.size)
        return out
    }

    /**
     * Compute SHA-256 digest of a file using streaming (constant memory).
     *
     * @param file input file
     * @return sha256 digest (32 bytes)
     */
    fun sha256OfFileStreaming(file: File): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val r = fis.read(buffer)
                if (r <= 0) break
                md.update(buffer, 0, r)
            }
        }
        return md.digest()
    }
}
