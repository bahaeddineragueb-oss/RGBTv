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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Xtream Codes API provider. Big lists stream straight to disk, then parse with JsonReader. */
class XtreamProvider(override val account: Account) : Provider {
    private val base = Net.normUrl(account.url)
    private val user = account.username
    private val pass = account.password
    private var allowedFormats: List<String> = emptyList()
    private val mutex = Mutex()
    private var memLive: List<LiveCh>? = null
    private var memVod: List<VodItem>? = null
    private var memSeries: List<SeriesItem>? = null

    private fun api(action: String?, params: Map<String, String> = emptyMap()): String {
        val q = mutableMapOf("username" to user, "password" to pass)
        if (action != null) q["action"] = action
        q.putAll(params)
        return "$base/player_api.php?${Net.qs(q)}"
    }

    override suspend fun login(): SessionInfo = io {
        val r = Net.getJson(api(null))
        val u = r.optJSONObject("user_info") ?: throw Exception("Invalid server response")
        val auth = u.optString("auth", u.optInt("auth", 0).toString())
        if (auth != "1") throw Exception("Authentication failed (check username/password)")
        allowedFormats = u.optJSONArray("allowed_output_formats")?.let { a ->
            (0 until a.length()).map { a.optString(it) }
        } ?: emptyList()
        val exp = u.optString("exp_date", "").toLongOrNull()?.takeIf { it > 0 }?.times(1000)
        SessionInfo(
            status = u.optString("status", "Active").ifEmpty { "Active" },
            expires = exp,
            extra = "max ${u.optString("max_connections", "?")}"
        )
    }

    /* ---------- categories (small) ---------- */
    private fun cats(action: String, cacheKey: String): List<Category> {
        DiskCache.getJson("${account.id}:$cacheKey", 6 * 3600_000)?.let { return catsFromJson(it) }
        val arr = try { JSONArray(Net.get(api(action))) } catch (e: Exception) { JSONArray() }
        val list = mutableListOf<Category>()
        for (i in 0 until arr.length()) {
            val x = arr.optJSONObject(i) ?: continue
            list.add(Category(x.optString("category_id"), x.optString("category_name")))
        }
        DiskCache.putJson("${account.id}:$cacheKey", JSONArray(list.map {
            JSONObject().put("id", it.id).put("name", it.name)
        }).toString())
        return list
    }

    private fun catsFromJson(s: String): List<Category> {
        val out = mutableListOf<Category>()
        try {
            val a = JSONArray(s)
            for (i in 0 until a.length()) {
                val x = a.getJSONObject(i)
                out.add(Category(x.optString("id"), x.optString("name")))
            }
        } catch (e: Exception) { }
        return out
    }

    override suspend fun liveCats(): List<Category> = io { cats("get_live_categories", "live_cats") }
    override suspend fun vodCats(): List<Category> = io { cats("get_vod_categories", "vod_cats") }
    override suspend fun seriesCats(): List<Category> = io { cats("get_series_categories", "series_cats") }

    /* ---------- big lists: download raw → parse streaming ---------- */
    private fun rawFile(kind: String): File {
        val key = "${account.id}:${kind}_raw"
        val f = DiskCache.rawFile(key)
        val ttl = Store.settings().refreshHours * 3600_000L
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < ttl) return f
        val action = when (kind) {
            "live" -> "get_live_streams"; "vod" -> "get_vod_streams"; else -> "get_series"
        }
        Net.download(api(action), dest = f, timeoutMs = 180000, maxBytes = 250L * 1024 * 1024)
        return f
    }

    private fun readerOf(f: File): JsonReader =
        JsonReader(InputStreamReader(FileInputStream(f), Charsets.UTF_8))

    private suspend fun allLive(): List<LiveCh> {
        memLive?.let { return it }
        return mutex.withLock {
            memLive?.let { return it }
            val list = io { parseLiveFile(rawFile("live")) }
            memLive = list
            list
        }
    }

    private suspend fun allVod(): List<VodItem> {
        memVod?.let { return it }
        return mutex.withLock {
            memVod?.let { return it }
            val list = io { parseVodFile(rawFile("vod")) }
            memVod = list
            list
        }
    }

    private suspend fun allSeries(): List<SeriesItem> {
        memSeries?.let { return it }
        return mutex.withLock {
            memSeries?.let { return it }
            val list = io { parseSeriesFile(rawFile("series")) }
            memSeries = list
            list
        }
    }

    override suspend fun live(catId: String?): List<LiveCh> {
        val l = allLive()
        return if (catId.isNullOrEmpty()) l else l.filter { it.catId == catId }
    }

    override suspend fun vod(catId: String?): List<VodItem> {
        val l = allVod()
        return if (catId.isNullOrEmpty()) l else l.filter { it.catId == catId }
    }

    override suspend fun series(catId: String?): List<SeriesItem> {
        val l = allSeries()
        return if (catId.isNullOrEmpty()) l else l.filter { it.catId == catId }
    }

    /* ---------- streaming parsers ---------- */
    private fun parseLiveFile(f: File): List<LiveCh> {
        val out = ArrayList<LiveCh>(8000)
        readerOf(f).use { r ->
            if (r.peek() != JsonToken.BEGIN_ARRAY) return emptyList()
            r.beginArray()
            while (r.hasNext()) {
                var id = ""; var name = ""; var num = 0; var logo = ""; var cat = ""
                var epg = ""; var arch = false; var archDays = 0
                r.beginObject()
                while (r.hasNext()) {
                    when (r.nextName()) {
                        "stream_id" -> id = r.nextStringAny()
                        "name" -> name = r.nextStringAny()
                        "num" -> num = r.nextStringAny().toIntOrNull() ?: 0
                        "stream_icon" -> logo = r.nextStringAny()
                        "category_id" -> cat = r.nextStringAny()
                        "epg_channel_id" -> epg = r.nextStringAny()
                        "tv_archive" -> arch = r.nextStringAny() == "1"
                        "tv_archive_duration" -> archDays = r.nextStringAny().toIntOrNull() ?: 0
                        else -> r.skipValue()
                    }
                }
                r.endObject()
                if (id.isNotEmpty()) out.add(LiveCh(id, name, num, logo, cat, epg, arch, archDays))
            }
            r.endArray()
        }
        return out
    }

    private fun parseVodFile(f: File): List<VodItem> {
        val out = ArrayList<VodItem>(8000)
        readerOf(f).use { r ->
            if (r.peek() != JsonToken.BEGIN_ARRAY) return emptyList()
            r.beginArray()
            while (r.hasNext()) {
                var id = ""; var name = ""; var poster = ""; var cat = ""; var rating = ""
                var ext = "mp4"; var added = 0L; var year = ""
                r.beginObject()
                while (r.hasNext()) {
                    when (r.nextName()) {
                        "stream_id" -> id = r.nextStringAny()
                        "name" -> name = r.nextStringAny()
                        "stream_icon" -> poster = r.nextStringAny()
                        "category_id" -> cat = r.nextStringAny()
                        "rating" -> { val v = r.nextStringAny(); if (v.isNotEmpty()) rating = v }
                        "rating_5based" -> { val v = r.nextStringAny(); if (rating.isEmpty()) rating = v }
                        "container_extension" -> { val v = r.nextStringAny(); if (v.isNotEmpty()) ext = v }
                        "added" -> added = r.nextStringAny().toLongOrNull() ?: 0
                        "year" -> year = r.nextStringAny()
                        else -> r.skipValue()
                    }
                }
                r.endObject()
                if (id.isNotEmpty()) out.add(VodItem(id, name, poster, cat, rating, ext, added, year))
            }
            r.endArray()
        }
        return out
    }

    private fun parseSeriesFile(f: File): List<SeriesItem> {
        val out = ArrayList<SeriesItem>(4000)
        readerOf(f).use { r ->
            if (r.peek() != JsonToken.BEGIN_ARRAY) return emptyList()
            r.beginArray()
            while (r.hasNext()) {
                var id = ""; var name = ""; var poster = ""; var cat = ""; var rating = ""
                var plot = ""; var year = ""; var genre = ""; var cast = ""; var backdrop = ""; var added = 0L
                r.beginObject()
                while (r.hasNext()) {
                    when (r.nextName()) {
                        "series_id" -> id = r.nextStringAny()
                        "name" -> name = r.nextStringAny()
                        "cover" -> poster = r.nextStringAny()
                        "category_id" -> cat = r.nextStringAny()
                        "rating" -> rating = r.nextStringAny()
                        "plot" -> plot = r.nextStringAny()
                        "releaseDate", "release_date" -> year = r.nextStringAny()
                        "genre" -> genre = r.nextStringAny()
                        "cast" -> cast = r.nextStringAny()
                        "backdrop_path" -> {
                            if (r.peek() == JsonToken.BEGIN_ARRAY) {
                                r.beginArray()
                                if (r.hasNext()) backdrop = r.nextStringAny()
                                while (r.hasNext()) r.skipValue()
                                r.endArray()
                            } else r.skipValue()
                        }
                        "last_modified" -> added = r.nextStringAny().toLongOrNull() ?: 0
                        else -> r.skipValue()
                    }
                }
                r.endObject()
                if (id.isNotEmpty()) out.add(SeriesItem(id, name, poster, cat, rating, year, plot, genre, cast, backdrop, added))
            }
            r.endArray()
        }
        return out
    }

    /* ---------- details ---------- */
    override suspend fun vodInfo(item: VodItem): VodDetail = io {
        val r = Net.getJson(api("get_vod_info", mapOf("vod_id" to item.id)))
        val i = r.optJSONObject("info") ?: JSONObject()
        val m = r.optJSONObject("movie_data") ?: JSONObject()
        VodDetail(
            id = item.id, name = m.optString("name").ifEmpty { i.optString("name").ifEmpty { item.name } },
            plot = i.optString("plot").ifEmpty { i.optString("description") },
            poster = i.optString("movie_image").ifEmpty { i.optString("cover_big").ifEmpty { item.poster } },
            backdrop = i.optJSONArray("backdrop_path")?.optString(0) ?: "",
            rating = i.optString("rating").ifEmpty { item.rating },
            year = i.optString("releasedate").ifEmpty { i.optString("release_date").ifEmpty { item.year } },
            genre = i.optString("genre"), duration = i.optString("duration"),
            cast = i.optString("cast").ifEmpty { i.optString("actors") },
            director = i.optString("director"),
            ext = m.optString("container_extension", "mp4").ifEmpty { "mp4" }
        )
    }

    override suspend fun seriesInfo(item: SeriesItem): SeriesDetail = io {
        val r = Net.getJson(api("get_series_info", mapOf("series_id" to item.id)))
        val i = r.optJSONObject("info") ?: JSONObject()
        val eps = r.optJSONObject("episodes") ?: JSONObject()
        val seasons = mutableListOf<SeasonItem>()
        eps.keys().asSequence().map { it }.sortedBy { it.toIntOrNull() ?: 0 }.forEach { s ->
            val arr = eps.optJSONArray(s) ?: JSONArray()
            val list = mutableListOf<EpisodeItem>()
            for (k in 0 until arr.length()) {
                val e = arr.optJSONObject(k) ?: continue
                val info = e.optJSONObject("info")
                list.add(
                    EpisodeItem(
                        id = e.optString("id"), season = s.toIntOrNull() ?: 0,
                        episode = e.optString("episode_num").toIntOrNull() ?: 0,
                        name = e.optString("title").ifEmpty { "Episode ${e.optString("episode_num")}" },
                        ext = e.optString("container_extension", "mp4").ifEmpty { "mp4" },
                        thumb = info?.optString("movie_image") ?: "",
                        plot = info?.optString("plot") ?: "",
                        duration = info?.optString("duration") ?: ""
                    )
                )
            }
            list.sortBy { it.episode }
            seasons.add(SeasonItem(s.toIntOrNull() ?: 0, "Season $s", list))
        }
        SeriesDetail(
            id = item.id, name = i.optString("name").ifEmpty { item.name },
            plot = i.optString("plot").ifEmpty { item.plot },
            poster = i.optString("cover").ifEmpty { item.poster },
            backdrop = i.optJSONArray("backdrop_path")?.optString(0) ?: item.backdrop,
            rating = i.optString("rating").ifEmpty { item.rating },
            year = (i.optString("releaseDate").ifEmpty { i.optString("release_date") }).ifEmpty { item.year },
            genre = i.optString("genre").ifEmpty { item.genre },
            cast = i.optString("cast").ifEmpty { item.cast },
            director = i.optString("director"), seasons = seasons
        )
    }

    /* ---------- EPG ---------- */
    override suspend fun epg(id: String, epgId: String, limit: Int): List<EpgEvent> = io {
        try {
            val t = Net.get(api("get_short_epg", mapOf("stream_id" to id, "limit" to limit.toString()))).trim()
            if (t.startsWith("[")) return@io emptyList()
            val arr = JSONObject(t).optJSONArray("epg_listings") ?: return@io emptyList()
            val out = mutableListOf<EpgEvent>()
            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                out.add(
                    EpgEvent(
                        Kit.b64orRaw(e.optString("title")),
                        Kit.b64orRaw(e.optString("description")),
                        e.optString("start_timestamp").toLongOrNull() ?: e.optLong("start_timestamp"),
                        e.optString("stop_timestamp").toLongOrNull() ?: e.optLong("stop_timestamp")
                    )
                )
            }
            out
        } catch (e: Exception) { emptyList() }
    }

    /* ---------- resolve ---------- */
    private fun liveFormat(): String {
        val want = Store.settings().liveFormat
        if (allowedFormats.isNotEmpty()) {
            if (want == "hls" || want == "ts") {
                val f = if (want == "ts") "ts" else "m3u8"
                if (allowedFormats.contains(f)) return f
            }
            return when {
                allowedFormats.contains("m3u8") -> "m3u8"
                allowedFormats.contains("ts") -> "ts"
                else -> "m3u8"
            }
        }
        return if (want == "ts") "ts" else "m3u8"
    }

    override suspend fun resolveLive(ch: LiveCh): StreamRef = io {
        val fmt = liveFormat()
        val url = if (fmt == "ts") "$base/live/$user/$pass/${ch.id}.ts"
        else "$base/live/$user/$pass/${ch.id}.m3u8"
        StreamRef(url, if (fmt == "ts") StreamKind.PROGRESSIVE else StreamKind.HLS)
    }

    override suspend fun resolveVod(v: VodItem): StreamRef = io {
        val ext = v.ext.ifEmpty { "mp4" }
        StreamRef("$base/movie/$user/$pass/${v.id}.$ext", Kit.streamKind("x.$ext"))
    }

    override suspend fun resolveEpisode(ep: EpisodeItem): StreamRef = io {
        val ext = ep.ext.ifEmpty { "mp4" }
        StreamRef("$base/series/$user/$pass/${ep.id}.$ext", Kit.streamKind("x.$ext"))
    }

    override suspend fun catchup(ch: LiveCh, startSec: Long, durMin: Int): StreamRef = io {
        val s = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US).format(Date(startSec * 1000))
        val url = "$base/streaming/timeshift.php?" + Net.qs(
            mapOf("username" to user, "password" to pass, "stream" to ch.id, "start" to s, "duration" to durMin.toString())
        )
        StreamRef(url, StreamKind.HLS)
    }
}
