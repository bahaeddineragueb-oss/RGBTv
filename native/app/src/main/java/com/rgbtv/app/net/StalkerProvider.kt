package com.rgbtv.app.net

import android.util.JsonReader
import android.util.JsonToken
import com.rgbtv.app.data.Account
import com.rgbtv.app.data.Category
import com.rgbtv.app.data.DiskCache
import com.rgbtv.app.data.EpgEvent
import com.rgbtv.app.data.EpisodeItem
import com.rgbtv.app.data.LiveCh
import com.rgbtv.app.data.SeasonItem
import com.rgbtv.app.data.SeriesDetail
import com.rgbtv.app.data.SeriesItem
import com.rgbtv.app.data.Store
import com.rgbtv.app.data.VodDetail
import com.rgbtv.app.data.VodItem
import com.rgbtv.app.ui.Kit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/** Stalker / Ministra portal provider (MAG emulation). */
class StalkerProvider(override val account: Account) : Provider {
    private val base: String
    private val endpoints = mutableListOf<String>()
    private var endpoint: String
    private val mac: String
    private val sn: String
    private val dev1: String
    private val dev2: String
    private val sig: String
    @Volatile private var token: String?
    private val mutex = Mutex()
    private var memLive: List<LiveCh>? = null
    private val linkCache = ConcurrentHashMap<String, Pair<Long, String>>()
    private var keepalive: CoroutineScope? = null

    init {
        var b = Net.normUrl(account.url)
        b = b.replace(Regex("/(c|stalker_portal/c)/?$"), "").replace(Regex("/(server/load\\.php|portal\\.php).*$"), "")
        base = b
        endpoints.addAll(
            listOf(
                "$b/server/load.php", "$b/stalker_portal/server/load.php", "$b/portal.php",
                "$b/c/portal.php", "$b/stalker_portal/portal.php"
            )
        )
        if (account.endpoint.isNotEmpty()) endpoints.add(0, account.endpoint)
        endpoint = endpoints[0]
        mac = (account.mac.ifEmpty { Store.deviceMac() }).uppercase()
        sn = Store.deviceSn(); dev1 = Store.deviceId(); dev2 = Store.deviceId2()
        sig = Kit.sha1(mac + sn)
        token = account.token.ifEmpty { null }
    }

    private fun headers(): Map<String, String> {
        val h = mutableMapOf(
            "User-Agent" to "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3",
            "X-User-Agent" to "Model: MAG250; Link: WiFi",
            "Referer" to "$base/c/",
            "Cookie" to "mac=${URLEncoder.encode(mac, "UTF-8")}; stb_lang=en; timezone=Europe%2FParis"
        )
        if (!token.isNullOrEmpty()) h["Authorization"] = "Bearer $token"
        return h
    }

    private fun url(params: Map<String, String>): String {
        val q = params.toMutableMap()
        q["JsHttpRequest"] = "1-xml"
        if (!q.containsKey("mac")) q["mac"] = mac
        return "$endpoint?${Net.qs(q)}"
    }

    /** Portal call → unwrapped "js" value (object/array/string). Retries once via handshake on auth errors. */
    private fun call(params: Map<String, String>, noRetry: Boolean = false): Any? {
        try {
            val t = Net.get(url(params), headers()).trim()
            if (t.contains("Authorization failed", ignoreCase = true)) throw Exception("AUTH")
            val o = try { JSONObject(t) } catch (e: Exception) { throw Exception("Invalid JSON") }
            return if (o.has("js")) o.get("js") else o
        } catch (e: Exception) {
            val m = e.message ?: ""
            if (!noRetry && Regex("AUTH|HTTP 401|HTTP 403|Invalid JSON").containsMatchIn(m)) {
                handshake()
                return call(params, true)
            }
            throw e
        }
    }

    @Synchronized
    private fun handshake() {
        token = null
        var lastErr: String? = null
        val seen = mutableSetOf<String>()
        for (ep in endpoints) {
            if (!seen.add(ep)) continue
            endpoint = ep
            try {
                val t = Net.get(url(mapOf("type" to "stb", "action" to "handshake", "token" to "", "prehash" to "")), headers()).trim()
                if (t.contains("Authorization failed", ignoreCase = true)) throw Exception("MAC not authorized")
                val js = try { JSONObject(t).optJSONObject("js") } catch (e: Exception) { null }
                val tok = js?.optString("token") ?: ""
                if (tok.isEmpty()) throw Exception("no token in handshake")
                token = tok
                account.token = tok; account.endpoint = endpoint
                Store.updateAccount(account)
                return
            } catch (e: Exception) { lastErr = e.message }
        }
        throw Exception("Portal: ${lastErr ?: "not reachable"}")
    }

    override suspend fun login(): SessionInfo = io {
        handshake()
        val p = call(
            mapOf(
                "type" to "stb", "action" to "get_profile", "hd" to "1",
                "ver" to "ImageDescription: 0.2.18-r23-250; ImageDate: Thu Sep 13 11:31:16 EEST 2018; PORTAL version: 5.6.2; API Version: JS API version: 343; STB API version: 146; Player Engine version: 0x58c",
                "num_banks" to "2", "sn" to sn, "stb_type" to "MAG250", "client_type" to "STB",
                "image_version" to "218", "video_out" to "hdmi", "device_id" to dev1, "device_id2" to dev2,
                "signature" to sig, "auth_second_step" to "1", "hw_version" to "1.7-BD-00",
                "not_valid_token" to "0",
                "metrics" to JSONObject().put("mac", mac).put("sn", sn).put("type", "STB")
                    .put("model", "MAG250").put("uid", "").put("random", Kit.uuid()).toString(),
                "hw_version_2" to Kit.sha1(mac),
                "timestamp" to (System.currentTimeMillis() / 1000).toString(),
                "api_signature" to "263", "prehash" to ""
            ), true
        ) as? JSONObject ?: JSONObject()
        val status = p.optString("status")
        if ((status == "2") && !p.has("id")) throw Exception("Device is not authorized on this portal (MAC not registered)")
        if (p.has("block_msg") && p.optString("block_msg").isNotEmpty()) throw Exception(p.optString("block_msg"))
        val info = try { call(mapOf("type" to "account_info", "action" to "get_main_info"), true) as? JSONObject } catch (e: Exception) { null }
        startKeepalive()
        var exp: Long? = null
        if (info != null) {
            exp = Kit.parseDateLenient(info.optString("end_date"))
                ?: Kit.parseDateLenient(info.optString("phone"))
        }
        val tariff = info?.optString("tariff_plan") ?: ""
        val loginName = info?.optString("login") ?: ""
        SessionInfo("Active", exp, listOf(loginName, tariff).filter { it.isNotEmpty() }.joinToString(" · "))
    }

    private var keepJob: kotlinx.coroutines.Job? = null

    private fun startKeepalive() {
        keepJob?.cancel()
        keepJob = null
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        keepalive = scope
        keepJob = scope.launch {
            while (true) {
                delay(120000)
                try {
                    call(
                        mapOf("type" to "watchdog", "action" to "get_events", "init" to "0", "cur_play_type" to "1", "event_active_id" to "0"),
                        true
                    )
                } catch (e: Exception) { }
            }
        }
    }

    override fun close() {
        try { keepJob?.cancel() } catch (e: Exception) { }
        keepJob = null
        keepalive = null
    }

    /* ---------- categories ---------- */
    private fun genres(type: String, cacheKey: String): List<Category> {
        DiskCache.getJson("${account.id}:$cacheKey", 6 * 3600_000)?.let { return catsFromJson(it) }
        val params = if (type == "itv") mapOf("type" to "itv", "action" to "get_genres")
        else mapOf("type" to type, "action" to "get_categories")
        val r = call(params)
        val arr = r as? JSONArray ?: JSONArray()
        val list = mutableListOf<Category>()
        for (i in 0 until arr.length()) {
            val g = arr.optJSONObject(i) ?: continue
            val id = g.optString("id")
            if (id.isEmpty() || id == "*") continue
            list.add(Category(id, g.optString("title"), g.optString("censored") == "1"))
        }
        DiskCache.putJson("${account.id}:$cacheKey", JSONArray(list.map {
            JSONObject().put("id", it.id).put("name", it.name).put("c", it.censored)
        }).toString())
        return list
    }

    private fun catsFromJson(s: String): List<Category> {
        val out = mutableListOf<Category>()
        try {
            val a = JSONArray(s)
            for (i in 0 until a.length()) {
                val x = a.getJSONObject(i)
                out.add(Category(x.optString("id"), x.optString("name"), x.optBoolean("c")))
            }
        } catch (e: Exception) { }
        return out
    }

    override suspend fun liveCats(): List<Category> = io { genres("itv", "live_cats") }
    override suspend fun vodCats(): List<Category> = io { genres("vod", "vod_cats") }
    override suspend fun seriesCats(): List<Category> {
        val l = io { genres("series", "series_cats") }
        return if (l.isNotEmpty()) l else vodCats()
    }

    /* ---------- live ---------- */
    private suspend fun allLive(): List<LiveCh> {
        memLive?.let { return it }
        return mutex.withLock {
            memLive?.let { return it }
            val list = io { fetchLive() }
            memLive = list
            list
        }
    }

    private fun fetchLive(): List<LiveCh> {
        val key = "${account.id}:live_raw"
        val f = DiskCache.rawFile(key)
        val ttl = Store.settings().refreshHours * 3600_000L
        if (!(f.exists() && System.currentTimeMillis() - f.lastModified() < ttl)) {
            // authed streaming download (token may refresh mid-way → single attempt, call() handles retry)
            val u = url(mapOf("type" to "itv", "action" to "get_all_channels"))
            try {
                Net.download(u, headers(), 120000, 120L * 1024 * 1024, f)
            } catch (e: Exception) {
                val m = e.message ?: ""
                if (Regex("AUTH|HTTP 401|HTTP 403|Invalid JSON").containsMatchIn(m)) {
                    handshake()
                    Net.download(url(mapOf("type" to "itv", "action" to "get_all_channels")), headers(), 120000, 120L * 1024 * 1024, f)
                } else throw e
            }
        }
        return parseLiveFile(f)
    }

    private fun parseLiveFile(f: File): List<LiveCh> {
        val out = ArrayList<LiveCh>(4000)
        JsonReader(InputStreamReader(FileInputStream(f), Charsets.UTF_8)).use { r ->
            if (r.peek() != JsonToken.BEGIN_OBJECT) return emptyList()
            r.beginObject()
            while (r.hasNext()) {
                if (r.nextName() == "js") parseJsChannels(r, out) else r.skipValue()
            }
            r.endObject()
        }
        return out
    }

    private fun parseJsChannels(r: JsonReader, out: MutableList<LiveCh>) {
        if (r.peek() == JsonToken.BEGIN_ARRAY) {
            r.beginArray()
            while (r.hasNext()) readChannel(r)?.let { out.add(it) }
            r.endArray()
            return
        }
        if (r.peek() != JsonToken.BEGIN_OBJECT) { r.skipValue(); return }
        r.beginObject()
        while (r.hasNext()) {
            if (r.nextName() == "data" && r.peek() == JsonToken.BEGIN_ARRAY) {
                r.beginArray()
                while (r.hasNext()) readChannel(r)?.let { out.add(it) }
                r.endArray()
            } else r.skipValue()
        }
        r.endObject()
    }

    private fun readChannel(r: JsonReader): LiveCh? {
        if (r.peek() != JsonToken.BEGIN_OBJECT) { r.skipValue(); return null }
        var id = ""; var name = ""; var num = 0; var logo = ""; var cat = ""
        var cmd = ""; var epg = ""; var arch = false; var archDays = 0
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "id" -> id = r.nextStringAny()
                "name" -> name = r.nextStringAny()
                "number" -> num = r.nextStringAny().toIntOrNull() ?: 0
                "logo" -> logo = r.nextStringAny()
                "tv_genre_id" -> cat = r.nextStringAny()
                "cmd" -> cmd = r.nextStringAny()
                "xmltv_id" -> epg = r.nextStringAny()
                "enable_tv_archive" -> arch = r.nextStringAny() == "1"
                "tv_archive_duration" -> archDays = r.nextStringAny().toIntOrNull() ?: 0
                else -> r.skipValue()
            }
        }
        r.endObject()
        if (id.isEmpty()) return null
        return LiveCh(id, name, num, fixLogo(logo), cat, epg, arch, archDays, cmd)
    }

    private fun fixLogo(l: String): String {
        if (l.isEmpty() || l.startsWith("http")) return l
        return "$base/stalker_portal/misc/logos/320/$l"
    }

    override suspend fun live(catId: String?): List<LiveCh> {
        val l = allLive()
        return if (catId.isNullOrEmpty()) l else l.filter { it.catId == catId }
    }

    /* ---------- vod / series (paged) ---------- */
    private data class VodRow(
        val id: String, val name: String, val poster: String, val cat: String,
        val rating: String, val plot: String, val year: String, val genre: String,
        val cast: String, val director: String, val duration: String, val added: Long,
        val cmd: String, val isSeries: Boolean, val seriesNums: String
    )

    private fun pageAll(type: String, catId: String?, maxPages: Int): List<VodRow> {
        val out = mutableListOf<VodRow>()
        var page = 1
        var total = Int.MAX_VALUE
        while (page <= maxPages && out.size < total) {
            val r = call(
                mapOf(
                    "type" to type, "action" to "get_ordered_list",
                    "category" to (catId ?: "*"), "genre" to (catId ?: "*"),
                    "sortby" to "added", "fav" to "0", "hd" to "0", "not_ended" to "0", "p" to page.toString()
                )
            ) as? JSONObject ?: break
            val data = r.optJSONArray("data") ?: JSONArray()
            total = r.optString("total_items").toIntOrNull() ?: r.optInt("total_items", 0)
            if (total <= 0) total = out.size + data.length()
            for (i in 0 until data.length()) {
                val x = data.optJSONObject(i) ?: continue
                val seriesArr = x.optJSONArray("series")
                val nums = if (seriesArr != null) (0 until seriesArr.length()).map { seriesArr.optString(it) }.filter { it.isNotEmpty() }.joinToString(",") else ""
                out.add(
                    VodRow(
                        id = x.optString("id"), name = x.optString("name"),
                        poster = x.optString("screenshot_uri").ifEmpty { x.optString("pic") },
                        cat = x.optString("category_id").ifEmpty { catId ?: "" },
                        rating = x.optString("rating_imdb").ifEmpty { x.optString("rating_kinopoisk") },
                        plot = x.optString("description"), year = x.optString("year"),
                        genre = x.optString("genres_str"), cast = x.optString("actors"),
                        director = x.optString("director"), duration = x.optString("time"),
                        added = Kit.parseDateLenient(x.optString("added")) ?: 0,
                        cmd = x.optString("cmd"),
                        isSeries = x.optString("is_series") == "1", seriesNums = nums
                    )
                )
            }
            if (data.length() == 0) break
            page++
        }
        return out
    }

    private fun rowToVod(x: VodRow) = VodItem(
        id = x.id, name = x.name, poster = x.poster, catId = x.cat,
        rating = x.rating, ext = Kit.guessExt(cleanCmd(x.cmd)).ifEmpty { "mp4" },
        added = x.added, year = x.year, cmd = x.cmd,
        isSeries = x.isSeries, seriesNums = x.seriesNums, plot = x.plot,
        genre = x.genre, cast = x.cast
    )

    private fun rowToSeries(x: VodRow) = SeriesItem(
        id = x.id, name = x.name, poster = x.poster, catId = x.cat,
        rating = x.rating, year = x.year, plot = x.plot, genre = x.genre,
        cast = x.cast, added = x.added, cmd = x.cmd, seriesNums = x.seriesNums
    )

    override suspend fun vod(catId: String?): List<VodItem> = io {
        val key = "${account.id}:vod_${catId ?: "all"}"
        DiskCache.getJson(key, 3600_000)?.let { return@io vodsFromJson(it) }
        val rows = pageAll("vod", catId, if (catId.isNullOrEmpty()) 20 else 60)
        val list = rows.filter { !it.isSeries }.map { rowToVod(it) }
        DiskCache.putJson(key, JSONArray(list.map { it.toJson() }).toString())
        list
    }

    private fun vodsFromJson(s: String): List<VodItem> {
        val out = mutableListOf<VodItem>()
        try {
            val a = JSONArray(s)
            for (i in 0 until a.length()) out.add(VodItem.fromJson(a.getJSONObject(i)))
        } catch (e: Exception) { }
        return out
    }

    override suspend fun series(catId: String?): List<SeriesItem> = io {
        val key = "${account.id}:series_${catId ?: "all"}"
        DiskCache.getJson(key, 3600_000)?.let { return@io seriesFromJson(it) }
        var list = try {
            pageAll("series", catId, if (catId.isNullOrEmpty()) 20 else 60).map { rowToSeries(it) }
        } catch (e: Exception) { emptyList() }
        if (list.isEmpty()) {
            // portals exposing series inside VOD
            list = pageAll("vod", catId, if (catId.isNullOrEmpty()) 20 else 60)
                .filter { it.isSeries }.map { rowToSeries(it) }
        }
        DiskCache.putJson(key, JSONArray(list.map { it.toJson() }).toString())
        list
    }

    private fun seriesFromJson(s: String): List<SeriesItem> {
        val out = mutableListOf<SeriesItem>()
        try {
            val a = JSONArray(s)
            for (i in 0 until a.length()) out.add(SeriesItem.fromJson(a.getJSONObject(i)))
        } catch (e: Exception) { }
        return out
    }

    override suspend fun vodInfo(item: VodItem): VodDetail = io {
        VodDetail(
            id = item.id, name = item.name, poster = item.poster,
            rating = item.rating, year = item.year, genre = item.genre,
            cast = item.cast, plot = item.plot, ext = item.ext.ifEmpty { "mp4" }, cmd = item.cmd
        )
    }

    override suspend fun seriesInfo(item: SeriesItem): SeriesDetail = io {
        val seasons = mutableListOf<SeasonItem>()
        try {
            val r = call(
                mapOf(
                    "type" to "series", "action" to "get_ordered_list",
                    "movie_id" to item.id, "category" to "*", "p" to "1"
                )
            ) as? JSONObject
            val data = r?.optJSONArray("data") ?: JSONArray()
            for (idx in 0 until data.length()) {
                val s = data.optJSONObject(idx) ?: continue
                val arr = s.optJSONArray("series") ?: JSONArray()
                val eps = mutableListOf<EpisodeItem>()
                for (k in 0 until arr.length()) {
                    val n = arr.optString(k)
                    if (n.isEmpty()) continue
                    eps.add(
                        EpisodeItem(
                            id = "${s.optString("id")}:$n", season = idx + 1,
                            episode = n.toIntOrNull() ?: 0, name = "Episode $n",
                            ext = Kit.guessExt(cleanCmd(s.optString("cmd"))).ifEmpty { "mp4" },
                            thumb = s.optString("screenshot_uri"), cmd = s.optString("cmd"), seriesNum = n
                        )
                    )
                }
                eps.sortBy { it.episode }
                seasons.add(SeasonItem(idx + 1, s.optString("name").ifEmpty { "Season ${idx + 1}" }, eps))
            }
        } catch (e: Exception) { }
        if (seasons.isEmpty() && item.seriesNums.isNotEmpty()) {
            val eps = item.seriesNums.split(",").mapNotNull { n ->
                if (n.isEmpty()) null else EpisodeItem(
                    id = "${item.id}:$n", season = 1, episode = n.toIntOrNull() ?: 0,
                    name = "Episode $n",
                    ext = Kit.guessExt(cleanCmd(item.cmd)).ifEmpty { "mp4" },
                    thumb = item.poster, cmd = item.cmd, seriesNum = n
                )
            }.sortedBy { it.episode }
            seasons.add(SeasonItem(1, "Season 1", eps))
        }
        SeriesDetail(
            id = item.id, name = item.name, poster = item.poster,
            rating = item.rating, year = item.year, genre = item.genre,
            cast = item.cast, plot = item.plot, seasons = seasons
        )
    }

    /* ---------- epg ---------- */
    override suspend fun epg(id: String, epgId: String, limit: Int): List<EpgEvent> = io {
        try {
            val r = call(mapOf("type" to "itv", "action" to "get_short_epg", "ch_id" to id, "size" to limit.toString()))
            val arr = r as? JSONArray ?: return@io emptyList()
            val out = mutableListOf<EpgEvent>()
            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                out.add(
                    EpgEvent(
                        e.optString("name"),
                        e.optString("descr"),
                        e.optString("start_timestamp").toLongOrNull() ?: e.optLong("start_timestamp"),
                        e.optString("stop_timestamp").toLongOrNull() ?: e.optLong("stop_timestamp")
                    )
                )
            }
            out
        } catch (e: Exception) { emptyList() }
    }

    /* ---------- resolve ---------- */
    override fun prefetch(ch: LiveCh) {
        if (ch.cmd.isEmpty()) return
        if (linkCache[ch.id]?.let { System.currentTimeMillis() - it.first < 25000 } == true) return
        CoroutineScope(Dispatchers.IO).launch {
            try { resolveLive(ch) } catch (e: Exception) { }
        }
    }

    private fun cachedLink(id: String): String? {
        val c = linkCache[id] ?: return null
        return if (System.currentTimeMillis() - c.first < 25000) c.second else null
    }

    private fun resolveCmd(type: String, cmd: String, series: String): String {
        val r = call(
            mapOf(
                "type" to type, "action" to "create_link", "cmd" to cmd, "series" to series,
                "forced_storage" to "", "disable_ad" to "0", "download" to "0"
            )
        ) as? JSONObject
        return cleanCmd(r?.optString("cmd") ?: cmd)
    }

    override suspend fun resolveLive(ch: LiveCh): StreamRef = io {
        cachedLink(ch.id)?.let { return@io StreamRef(it, Kit.streamKind(it)) }
        val u = try {
            resolveCmd("itv", ch.cmd, "")
        } catch (e: Exception) { cleanCmd(ch.cmd) }
        if (u.isEmpty()) throw Exception("Empty stream link")
        linkCache[ch.id] = System.currentTimeMillis() to u
        StreamRef(u, Kit.streamKind(u))
    }

    override suspend fun resolveVod(v: VodItem): StreamRef = io {
        val u = try {
            resolveCmd("vod", v.cmd, "")
        } catch (e: Exception) { cleanCmd(v.cmd) }
        if (u.isEmpty()) throw Exception("Empty stream link")
        StreamRef(u, Kit.streamKind(u).let { if (it == StreamKind.UNKNOWN) Kit.streamKind("x.${v.ext}") else it })
    }

    override suspend fun resolveEpisode(ep: EpisodeItem): StreamRef = io {
        val u = try {
            resolveCmd("vod", ep.cmd, ep.seriesNum.ifEmpty { ep.episode.toString() })
        } catch (e: Exception) { cleanCmd(ep.cmd) }
        if (u.isEmpty()) throw Exception("Empty stream link")
        StreamRef(u, Kit.streamKind(u).let { if (it == StreamKind.UNKNOWN) Kit.streamKind("x.${ep.ext}") else it })
    }

    override suspend fun catchup(ch: LiveCh, startSec: Long, durMin: Int): StreamRef? = io {
        try {
            val r = call(
                mapOf(
                    "type" to "tv_archive", "action" to "create_link", "cmd" to ch.cmd,
                    "series" to "", "forced_storage" to "", "disable_ad" to "0",
                    "download" to "0", "start" to startSec.toString(), "real_time" to "1"
                )
            ) as? JSONObject
            val u = cleanCmd(r?.optString("cmd") ?: "")
            if (u.isEmpty()) null else StreamRef(u, Kit.streamKind(u))
        } catch (e: Exception) { null }
    }

    private fun cleanCmd(c: String): String {
        var s = c.trim().replace(Regex("^(ffmpeg|ffrt\\d?|auto)\\s+", RegexOption.IGNORE_CASE), "")
        Regex("https?://\\S+").find(s)?.let { s = it.value }
        return s.trim()
    }
}
