package io.github.contendar.ipfscar

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Result returned after writing a CAR file.
 *
 * @param contentCid CID of the content block placed inside the CAR.
 * @param carCid CID of the full CAR file (sha256 of CAR bytes wrapped as CIDv1).
 * @param carFile the written CAR file.
 */
data class CarWriteResult(
    val contentCid: String,
    val carCid: String,
    val carFile: File
)

/**
 * Streaming CARv1 writer. Produces a minimal CAR containing a single block: the raw content.
 *
 * Notes:
 * - The CAR header is encoded in CBOR for compatibility with ipfs-car.
 * - This function streams the input file and the output CAR file; it does not load the whole file into memory.
 *
 * @param inputFile source file to include in the CAR
 * @param outputCarFile destination CAR file
 * @param contentCodec multicodec for the content block (default 0x55 = raw)
 * @param carCidCodec multicodec used when computing the CAR file CID (default 0x70 = dag-pb)
 */
object CarWriter {
    private const val BUFFER = 8 * 1024

    suspend fun writeCarStreaming(
        inputFile: File,
        outputCarFile: File,
        contentCodec: Int = 0x55,// tune to match ipfs-car pack root codec if necessary
        carCidCodec: Int = 0x70// tune to match ipfs-car hash codec for CAR file CID
    ): CarWriteResult = withContext(Dispatchers.IO) {
        require(inputFile.exists()) { "inputFile missing: ${'$'}{inputFile.path}" }

        // 1) compute content sha256 digest streaming
        val contentDigest = MessageDigest.getInstance("SHA-256")
        FileInputStream(inputFile).use { fis ->
            val buf = ByteArray(BUFFER)
            while (true) {
                val r = fis.read(buf)
                if (r <= 0) break
                contentDigest.update(buf, 0, r)
            }
        }
        val contentSha = contentDigest.digest()
        // 2) build content CID
        val contentCid = CidUtils.cidV1FromSha256Digest(contentSha, contentCodec)

        // 3) build CBOR header and write CAR file streaming
        // Build header JSON (some bridges accept this minimal header as bytes)
        val headerJson = """
            {
              "version": 1,
              "roots": [
                {"\/": "$contentCid"}
              ]
            }
        """.trimIndent().toByteArray(Charsets.UTF_8)

        FileOutputStream(outputCarFile).use { out ->
            // 2.a write header length as uvarint then header bytes
            writeUvarInt(out, headerJson.size.toLong())
            out.write(headerJson)

            // 2.b Prepare CID byte representation for the content CID
            val cidBytes = decodeCidToBytes(contentCid)

            // 2.c block length = cidBytes.size + file.length
            val blockLen = cidBytes.size + inputFile.length()
            writeUvarInt(out, blockLen)

            // 2.d write cid bytes
            out.write(cidBytes)

            // 2.e stream input file bytes
            FileInputStream(inputFile).use { fis ->
                val buf = ByteArray(BUFFER)
                while (true) {
                    val r = fis.read(buf)
                    if (r <= 0) break
                    out.write(buf, 0, r)
                }
            }
            out.flush()
        }

        // 4) compute CAR file sha256 and CID
        val carSha = MessageDigest.getInstance("SHA-256").let { md ->
            FileInputStream(outputCarFile).use { fis ->
                val buf = ByteArray(BUFFER)
                while (true) {
                    val r = fis.read(buf)
                    if (r <= 0) break
                    md.update(buf, 0, r)
                }
            }
            md.digest()
        }

        val carCid = CidUtils.cidV1FromSha256Digest(carSha, carCidCodec)
        CarWriteResult(contentCid = contentCid, carCid = carCid, carFile = outputCarFile)
    }

    private fun writeUvarInt(out: OutputStream, value: Long) {
        var v = value
        while (true) {
            var b = (v and 0x7F).toInt()
            v = v ushr 7
            if (v != 0L) b = b or 0x80
            out.write(b)
            if (v == 0L) break
        }
    }

    // Minimal CBOR encoder for the CAR header shape: { "version": 1, "roots": [ h'...'] }
    private fun buildCborCarHeader(cidBytes: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        // map(2)
        baos.write(0xA2)
        // key "version"
        val keyVersion = "version".toByteArray(Charsets.UTF_8)
        baos.write(0x60 + keyVersion.size)
        baos.write(keyVersion)
        // value 1
        baos.write(0x01)
        // key "roots"
        val keyRoots = "roots".toByteArray(Charsets.UTF_8)
        baos.write(0x60 + keyRoots.size)
        baos.write(keyRoots)
        // array of 1
        baos.write(0x81)
        // byte string header for cidBytes
        if (cidBytes.size <= 255) {
            baos.write(0x58)
            baos.write(cidBytes.size)
        } else {
            baos.write(0x59)
            baos.write((cidBytes.size shr 8) and 0xFF)
            baos.write(cidBytes.size and 0xFF)
        }
        baos.write(cidBytes)
        return baos.toByteArray()
    }

    private fun decodeCidToBytes(cid: String): ByteArray {
        val normalized = if (cid.startsWith("b")) cid.removePrefix("b") else cid
        return Base32.decodeLowerToBytes(normalized)
    }
}
