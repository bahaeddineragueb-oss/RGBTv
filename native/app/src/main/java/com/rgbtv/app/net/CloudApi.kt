package com.rgbtv.app.net

import com.rgbtv.app.data.CloudStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Minimal JSON client for the RGBTv cloud backend (OkHttp, no extra deps). */
object CloudApi {
    data class Res(val ok: Boolean, val json: JSONObject?, val err: String?)
    data class CloudUser(val email: String, val name: String, val admin: Boolean, val token: String)
    data class CloudProfile(
        val id: Int, val name: String, val type: String, val url: String,
        val username: String, val password: String, val mac: String, val epgUrl: String
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private suspend fun call(method: String, path: String, body: JSONObject? = null): Res =
        withContext(Dispatchers.IO) {
            try {
                val base = CloudStore.url()
                if (base.isEmpty()) return@withContext Res(false, null, "no server")
                val rb = Request.Builder().url(base + path)
                val tok = CloudStore.token()
                if (tok.isNotEmpty()) rb.header("Authorization", "Bearer $tok")
                when (method) {
                    "GET" -> rb.get()
                    "DELETE" -> rb.delete()
                    "PUT" -> rb.put((body?.toString() ?: "{}").toRequestBody(JSON))
                    else -> rb.post((body?.toString() ?: "{}").toRequestBody(JSON))
                }
                client.newCall(rb.build()).execute().use { r ->
                    val txt = r.body?.string() ?: "{}"
                    val o = try { JSONObject(txt) } catch (e: Exception) { JSONObject() }
                    if (r.isSuccessful) Res(true, o, null)
                    else Res(false, o, o.optString("error", "http ${r.code}"))
                }
            } catch (e: Exception) {
                Res(false, null, e.message ?: "network error")
            }
        }

    suspend fun register(email: String, pass: String, name: String): Res =
        call("POST", "/api/auth/register", JSONObject().put("email", email).put("password", pass).put("name", name))

    suspend fun login(email: String, pass: String): Res =
        call("POST", "/api/auth/login", JSONObject().put("email", email).put("password", pass))

    fun parseUser(o: JSONObject): CloudUser? {
        val u = o.optJSONObject("user") ?: return null
        val t = o.optString("token")
        if (t.isEmpty()) return null
        return CloudUser(u.optString("email"), u.optString("name"), u.optBoolean("admin"), t)
    }

    suspend fun profiles(): List<CloudProfile> {
        val r = call("GET", "/api/profiles")
        if (!r.ok || r.json == null) return emptyList()
        val out = mutableListOf<CloudProfile>()
        val a = r.json.optJSONArray("profiles") ?: JSONArray()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            out.add(
                CloudProfile(
                    o.optInt("id"), o.optString("name"), o.optString("type", "xtream"),
                    o.optString("url"), o.optString("username"), o.optString("password"),
                    o.optString("mac"), o.optString("epg_url")
                )
            )
        }
        return out
    }

    suspend fun pushProfile(p: CloudProfile, id: Int = -1): Int {
        val b = JSONObject().put("name", p.name).put("type", p.type).put("url", p.url)
            .put("username", p.username).put("password", p.password)
            .put("mac", p.mac).put("epg_url", p.epgUrl)
        val r = if (id > 0) call("PUT", "/api/profiles/$id", b) else call("POST", "/api/profiles", b)
        return r.json?.optJSONObject("profile")?.optInt("id", id) ?: id
    }

    suspend fun deleteProfile(id: Int) {
        if (id > 0) call("DELETE", "/api/profiles/$id")
    }

    suspend fun push(favs: JSONArray, hist: JSONArray, pos: JSONArray): Boolean {
        val r = call(
            "POST", "/api/sync/push",
            JSONObject().put("favs", favs).put("history", hist).put("positions", pos)
        )
        return r.ok
    }

    suspend fun pull(since: Long): JSONObject? {
        val r = call("GET", "/api/sync/pull?since=$since")
        return if (r.ok) r.json else null
    }
}
