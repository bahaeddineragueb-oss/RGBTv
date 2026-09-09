package com.rgbtv.app.repo

import android.content.Context
import com.rgbtv.app.data.Account
import com.rgbtv.app.data.CloudStore
import com.rgbtv.app.data.Store
import com.rgbtv.app.data.db.AppDb
import com.rgbtv.app.data.db.FavEntity
import com.rgbtv.app.data.db.HistEntity
import com.rgbtv.app.data.db.PosEntity
import com.rgbtv.app.net.CloudApi
import com.rgbtv.app.ui.Kit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Cloud sync: push local Room rows, pull newer server rows. Local-first, best-effort. */
object Sync {
    private val io = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pending: Job? = null

    /** Debounced push after local writes. */
    fun pushSoon(ctx: Context) {
        if (!CloudStore.loggedIn) return
        pending?.cancel()
        pending = io.launch {
            delay(5000)
            try { pushNow(ctx) } catch (e: Exception) { }
        }
    }

    suspend fun pushNow(ctx: Context): Boolean {
        if (!CloudStore.loggedIn) return false
        val db = AppDb.get(ctx)
        val favs = JSONArray()
        db.favs().all().forEach {
            favs.put(JSONObject().put("acc", it.acc).put("type", it.type).put("id", it.itemId)
                .put("name", it.name).put("img", it.img).put("data", it.data).put("at", it.at))
        }
        val hist = JSONArray()
        db.hist().all().forEach {
            hist.put(JSONObject().put("acc", it.acc).put("type", it.type).put("id", it.itemId)
                .put("name", it.name).put("img", it.img).put("data", it.data).put("at", it.at))
        }
        val pos = JSONArray()
        db.pos().all().forEach {
            pos.put(JSONObject().put("acc", it.acc).put("key", it.itemKey)
                .put("pos", it.pos).put("dur", it.dur).put("at", it.at))
        }
        return CloudApi.push(favs, hist, pos)
    }

    /** Pull server rows newer than lastSync; returns counts triple or null on error. */
    suspend fun pullNow(ctx: Context): Triple<Int, Int, Int>? {
        if (!CloudStore.loggedIn) return null
        val o = CloudApi.pull(CloudStore.lastSync()) ?: return null
        val db = AppDb.get(ctx)
        var nf = 0
        var nh = 0
        var np = 0
        val fa = o.optJSONArray("favs") ?: JSONArray()
        for (i in 0 until fa.length()) {
            val r = fa.optJSONObject(i) ?: continue
            db.favs().upsert(
                FavEntity(
                    r.optString("acc_key"), r.optString("type"), r.optString("item_id"),
                    r.optString("name"), r.optString("img"), r.optString("data"), r.optLong("updated_at")
                )
            )
            nf++
        }
        val ha = o.optJSONArray("history") ?: JSONArray()
        for (i in 0 until ha.length()) {
            val r = ha.optJSONObject(i) ?: continue
            db.hist().upsert(
                HistEntity(
                    r.optString("acc_key"), r.optString("type"), r.optString("item_id"),
                    r.optString("name"), r.optString("img"), r.optString("data"), r.optLong("updated_at")
                )
            )
            nh++
        }
        val pa = o.optJSONArray("positions") ?: JSONArray()
        for (i in 0 until pa.length()) {
            val r = pa.optJSONObject(i) ?: continue
            db.pos().upsert(
                PosEntity(
                    r.optString("acc_key"), r.optString("item_key"),
                    r.optLong("pos"), r.optLong("dur"), r.optLong("updated_at")
                )
            )
            np++
        }
        CloudStore.setLastSync(o.optLong("ts", System.currentTimeMillis()))
        return Triple(nf, nh, np)
    }

    /** Upload local IPTV profiles to the server account. */
    fun pushProfiles(ctx: Context) {
        if (!CloudStore.loggedIn) return
        io.launch {
            try {
                for (a in Store.accounts()) {
                    val srv = CloudStore.serverId(a.id)
                    val cp = CloudApi.CloudProfile(
                        srv, a.name, a.type, a.url, a.username, a.password, a.mac, a.epgUrl
                    )
                    val id = CloudApi.pushProfile(cp, srv)
                    if (id > 0) {
                        CloudStore.setServerId(a.id, id)
                        CloudStore.setLocalId(id, a.id)
                    }
                }
            } catch (e: Exception) { }
        }
    }

    /** Download server profiles missing locally. Returns number added. */
    suspend fun pullProfiles(ctx: Context): Int {
        if (!CloudStore.loggedIn) return 0
        var added = 0
        try {
            for (cp in CloudApi.profiles()) {
                val local = CloudStore.localId(cp.id)
                if (local != null && Store.getAccount(local) != null) continue
                val a = Account(
                    name = cp.name, type = cp.type.ifEmpty { "xtream" }, url = cp.url,
                    username = cp.username, password = cp.password, mac = cp.mac, epgUrl = cp.epgUrl
                )
                a.id = Kit.uuid()
                Store.addAccount(a)
                CloudStore.setServerId(a.id, cp.id)
                CloudStore.setLocalId(cp.id, a.id)
                added++
            }
        } catch (e: Exception) { }
        return added
    }
}
