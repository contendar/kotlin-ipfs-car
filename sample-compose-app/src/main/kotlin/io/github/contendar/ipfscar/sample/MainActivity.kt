package io.github.contendar.ipfscar.sample

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import io.github.contendar.ipfscar.CarWriter
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.buffer
import okio.source
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : ComponentActivity() {
    private val client = OkHttpClient()

    var carFile: File? = null
    var contentCid: String? = null
//
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val space_key = "Storacha Space key"
        val authToken = "Storacha Authorization"
        val authSecret ="Storacha Secret"
        setContent {
            Surface(color = MaterialTheme.colorScheme.background) {
                val pickedName = remember { mutableStateOf<String?>(null) }
                val status = remember { mutableStateOf<String?>(null) }
                val progress = remember { mutableStateOf(0f) }
                val canUpload = remember { mutableStateOf(false) }
                val pickLauncher =
                    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
                        uri?.let {
                            val name = queryFileName(uri)
                            pickedName.value = name
                            status.value = "Preparing..."
                            lifecycleScope.launch {
                                val temp = File(cacheDir, name ?: "picked")
                                contentResolver.openInputStream(uri)?.use { input ->
                                    FileOutputStream(temp).use { out ->
                                        input.copyTo(out)
                                    }
                                }
                                carFile = File(cacheDir, "${temp.name}.car")
                                val result = CarWriter.writeCarStreaming(temp, carFile!!,contentCodec = 0x55,  // try 0x55 (raw) or 0x70 (dag-pb) to match ipfs-car -- experiment
                                    carCidCodec = 0x202    // try 0x70 or 0x0202 (decimal 514) depending on ipfs-car output
                                )
                                contentCid = result.carCid
                                status.value = "CAR created. carCid=${result.carCid}"
                                canUpload.value = true
                            }
                        }
                    }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { pickLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        ) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Pick file")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(text = "File: ${pickedName.value ?: "(none)"}")

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(onClick = {
                        if (carFile == null) {
                            Toast.makeText(this@MainActivity, "No CAR ready", Toast.LENGTH_SHORT)
                                .show()
                            return@Button
                        }
                        lifecycleScope.launch {
                            status.value = "Creating upload intent..."
                            val carCid = contentCid ?: ""
                            val size = carFile!!.length()
                            // tasks: [ [ "store/add", "did:key:...", { "link": {"/": "<cid>"}, "size": <size> } ] ]
                            val tasks = JSONArray().apply {
                                val inner = JSONArray().apply {
                                    put("store/add")
                                    put("did:key:$space_key")
                                    val meta = JSONObject()
                                    meta.put("link", JSONObject().put("/", carCid))
                                    meta.put("size", size)
                                    put(meta)
                                }
                                put(inner)
                            }
                            val root = JSONObject().put("tasks", tasks)
                            val body =
                                root.toString().toRequestBody()
                            val req = Request.Builder()
                                .url("https://up.storacha.network/bridge")
                                .header("Authorization", authToken)
                                .header("X-Auth-Secret", authSecret)
                                .header("Content-Type", "application/json")
                                .post(body)
                                .build()

                            client.newCall(req).enqueue(object : okhttp3.Callback {
                                override fun onFailure(call: Call, e: IOException) {
                                    status.value = "Upload failed: ${e.message}!"
                                }

                                override fun onResponse(
                                    call: Call,
                                    resp: Response
                                ) {
                                    val text = resp.body?.string()
                                    if (!resp.isSuccessful) {
                                        status.value = "Create intent failed: ${resp.code} -> ${text}"
                                     }
                                    status.value = "Intent created. Parsing response..."
                                    val arr = JSONArray(text)
                                    val first = arr.getJSONObject(0)
                                    val p = first.getJSONObject("p")
                                    val out = p.getJSONObject("out")
                                    val error = out.has("error")
                                    if (error) {
                                        //Expected link to be CID with 0x202 codec
                                        val errorName = out.getJSONObject("error").optString("name")
                                        status.value = "Error:$errorName"
                                    } else {
                                        val ok = out.getJSONObject("ok")
                                        val _status = ok.getString("status")
                                        if (_status != "upload") {
                                            status.value = "Unexpected status: $_status"
                                        } else {
                                            val headers = ok.getJSONObject("headers")

                                            val contentLength = headers.getString("content-length")
                                            val checksum = headers.getString("x-amz-checksum-sha256")
                                            val uploadUrl = ok.getString("url")

                                            if (uploadUrl == null) {
                                                status.value =
                                                    "No upload URL present in intent response"
                                            }
                                            status.value = "Uploading..."
                                            val total = carFile!!.length().toDouble()
                                            val requestBody = object : RequestBody() {
                                                override fun contentType() =
                                                    "application/vnd.ipld.car".toMediaType()

                                                override fun contentLength() = carFile!!.length()
                                                override fun writeTo(sink: okio.BufferedSink) {
                                                    carFile!!.source().buffer().use { source ->
                                                        var uploaded = 0L
                                                        val buf = okio.Buffer()
                                                        while (true) {
                                                            val read = source.read(buf, 8 * 1024)
                                                            if (read == -1L) break
                                                            sink.write(buf, read)
                                                            uploaded += read
                                                            val p = (uploaded / total).toFloat()
                                                            progress.value = p
                                                        }
                                                    }
                                                }
                                            }
                                            val putReqBuilder =
                                                Request.Builder().url(uploadUrl).put(requestBody)
                                            contentLength.takeIf { it.isNotBlank() }
                                                ?.let { putReqBuilder.header("content-length", it) }
                                            checksum.takeIf { it.isNotBlank() }
                                                ?.let {
                                                    putReqBuilder.header(
                                                        "x-amz-checksum-sha256",
                                                        it
                                                    )
                                                }
                                            val putReq = putReqBuilder.build()
                                            client.newCall(putReq).execute().use { putResp ->
                                                val bodyText = putResp.body?.string()
                                                if (!putResp.isSuccessful) {
                                                    status.value =
                                                        "Upload failed: ${'$'}{putResp.code} -> ${'$'}bodyText"
                                                }
                                                status.value = "Upload successful!"
                                            }
                                        }
                                    }
                                }
                            })
                            /*client.newCall(req).execute().use { resp ->
                            }*/
                        }
                    }, modifier = Modifier.fillMaxWidth(), enabled = canUpload.value) {
                        Text("Upload to Storacha")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (progress.value > 0f) {
                        LinearProgressIndicator(
                            progress = progress.value,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "Status: ${status.value ?: "idle"}")
                }
            }
        }
    }

    private fun queryFileName(uri: Uri): String? {
        var name: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }
}
