package com.rgbtv.app.net

import android.util.JsonReader
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.io.InputStreamReader
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Shared HTTP layer. Player-like UA so IPTV panels don't reject us (HTTP 444/512/403). */
object Net {
    const val UA = "RGBTv/3.0 (Android) IPTVSmarters/3.1 Media3/1.11.0"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    class HttpEx(val code: Int, message: String) : IOException(message)

    private fun req(url: String, headers: Map<String, String>): Request {
        val b = Request.Builder().url(url)
            .header("User-Agent", UA)
            .header("Accept", "*/*")
        headers.forEach { (k, v) -> b.header(k, v) }
        return b.build()
    }

    @Throws(IOException::class)
    fun execute(url: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 20000): Response {
        val call = client.newCall(req(url, headers))
        if (timeoutMs > 0) call.timeout().timeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        return call.execute()
    }

    /** GET → String body. Throws HttpEx on HTTP errors. */
    @Throws(IOException::class)
    fun get(url: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 20000): String {
        execute(url, headers, timeoutMs).use { r ->
            if (!r.isSuccessful) throw HttpEx(r.code, "HTTP ${r.code} ${snippet(r)}")
            return r.body!!.string()
        }
    }

    @Throws(IOException::class)
    fun getBytes(url: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 20000): ByteArray {
        execute(url, headers, timeoutMs).use { r ->
            if (!r.isSuccessful) throw HttpEx(r.code, "HTTP ${r.code} ${snippet(r)}")
            return r.body!!.bytes()
        }
    }

    @Throws(IOException::class)
    fun getJson(url: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 20000): JSONObject {
        val t = get(url, headers, timeoutMs).trim()
        try {
            return JSONObject(if (t.startsWith("<")) throw IOException("Invalid JSON (HTML page)") else t)
        } catch (e: HttpEx) { throw e } catch (e: IOException) { throw e } catch (e: Exception) {
            throw IOException("Invalid JSON")
        }
    }

    private fun snippet(r: Response): String {
        return try {
            val peek = r.peekBody(300).string().trim().replace(Regex("\\s+"), " ")
            if (peek.isEmpty()) "" else "— $peek"
        } catch (e: Exception) { "" }
    }

    /** Stream a (possibly huge) response straight to disk. Big playlists never sit fully in RAM. */
    @Throws(IOException::class)
    fun download(
        url: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 120000,
        maxBytes: Long = 200L * 1024 * 1024, dest: java.io.File
    ) {
        dest.parentFile?.mkdirs()
        val tmp = java.io.File(dest.parent, dest.name + ".part")
        try {
            execute(url, headers, timeoutMs).use { r ->
                if (!r.isSuccessful) throw HttpEx(r.code, "HTTP ${r.code} ${snippet(r)}")
                val body = r.body ?: throw IOException("Empty body")
                var total = 0L
                body.byteStream().use { inp ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(65536)
                        while (true) {
                            val n = inp.read(buf)
                            if (n <= 0) break
                            total += n
                            if (total > maxBytes) throw IOException("File too large (>${maxBytes / 1048576} MB)")
                            out.write(buf, 0, n)
                        }
                    }
                }
            }
            dest.delete()
            if (!tmp.renameTo(dest)) throw IOException("Cache write failed")
        } catch (e: Exception) {
            try { tmp.delete() } catch (ignored: Exception) { }
            throw e
        }
    }

    fun qs(params: Map<String, String>): String =
        params.entries.joinToString("&") {
            URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
        }

    fun normUrl(u: String): String {
        var s = (u.trim())
        if (!Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(s)) s = "http://$s"
        return s.trimEnd('/')
    }

    /** Friendly message for UI. */
    fun userMsg(ctx: android.content.Context, e: Throwable): String {
        val m = e.message ?: ""
        return when {
            e is HttpEx && (e.code == 401 || e.code == 403) -> ctx.getString(com.rgbtv.app.R.string.err_auth)
            Regex("Auth|auth|401|403").containsMatchIn(m) -> ctx.getString(com.rgbtv.app.R.string.err_auth)
            else -> ctx.getString(com.rgbtv.app.R.string.err_network) + "\n$m"
        }
    }
}

/** Read a JSON value that may be a string, number, boolean or null — always as String. */
fun JsonReader.nextStringAny(): String {
    return when (peek()) {
        android.util.JsonToken.STRING -> nextString()
        android.util.JsonToken.NUMBER -> try { nextLong().toString() } catch (e: Exception) { nextDouble().toString() }
        android.util.JsonToken.BOOLEAN -> nextBoolean().toString()
        android.util.JsonToken.NULL -> { nextNull(); "" }
        else -> { skipValue(); "" }
    }
}

fun JsonReader.nextLongAny(): Long {
    return when (peek()) {
        android.util.JsonToken.NUMBER -> try { nextLong() } catch (e: Exception) { nextDouble().toLong() }
        android.util.JsonToken.STRING -> nextString().toLongOrNull() ?: 0
        android.util.JsonToken.BOOLEAN -> if (nextBoolean()) 1 else 0
        else -> { skipValue(); 0 }
    }
}

fun streamReader(resp: Response): InputStreamReader =
    InputStreamReader(resp.body!!.byteStream(), Charsets.UTF_8)
