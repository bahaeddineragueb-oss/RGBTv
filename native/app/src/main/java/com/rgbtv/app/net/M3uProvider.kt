package com.rgbtv.app.net

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
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

/** M3U / M3U8 playlist provider. Raw playlist cached on disk; parsed streaming. */
class M3uProvider(override val account: Account) : Provider {
    private val mutex = Mutex()
    private var live: List<LiveCh>? = null
    private var vod: List<VodItem>? = null
    private var series: List<SeriesItem>? = null
    private var seasons: Map<String, MutableMap<Int, MutableList<EpisodeItem>>> = emptyMap()

    override suspend fun login(): SessionInfo {
        ensureLoaded()
        val n = (live?.size ?: 0) + (vod?.size ?: 0) + (series?.size ?: 0)
        if (n == 0) throw Exception("Playlist is empty")
        return SessionInfo("Loaded", null, "$n items")
    }

    private suspend fun ensureLoaded() {
        if (live != null) return
        mutex.withLock {
            if (live != null) return
            io { load() }
        }
    }

    private fun load() {
        val f = DiskCache.rawFile("${account.id}:m3u_raw")
        val ttl = Store.settings().refreshHours * 3600_000L
        if (!(f.exists() && System.currentTimeMillis() - f.lastModified() < ttl)) {
            Net.download(account.url.trim(), timeoutMs = 180000, maxBytes = 120L * 1024 * 1024, dest = f)
        }
        parse(f)
    }

    private fun parse(f: File) {
        val liveOut = mutableListOf<LiveCh>()
        val vodOut = mutableListOf<VodItem>()
        val seriesOut = mutableListOf<SeriesItem>()
        val seriesMap = LinkedHashMap<String, SeriesItem>()
        val seasonsMap = HashMap<String, MutableMap<Int, MutableList<EpisodeItem>>>()
        var sawHeader = false
        var firstNonEmpty: String? = null
        var n = 0
        var curName = ""; var curLogo = ""; var curGroup = ""; var curEpg = ""
        var hasCur = false

        BufferedReader(InputStreamReader(FileInputStream(f), Charsets.UTF_8)).use { br ->
            var line = br.readLine()
            if (line != null && line.isNotEmpty() && line[0] == '\uFEFF') line = line.substring(1)
            while (line != null) {
                val l = line.trim()
                if (l.isNotEmpty()) {
                    if (firstNonEmpty == null) firstNonEmpty = l
                    when {
                        l.startsWith("#EXTM3U", ignoreCase = true) -> sawHeader = true
                        l.startsWith("#EXTINF", ignoreCase = true) -> {
                            sawHeader = true
                            val attrs = parseAttrs(l)
                            val ci = l.lastIndexOf(',')
                            curName = (if (ci > 0) l.substring(ci + 1) else "").trim()
                                .ifEmpty { attrs["tvg-name"] ?: "Unknown" }
                            curLogo = attrs["tvg-logo"] ?: ""
                            curGroup = attrs["group-title"]?.ifEmpty { "Uncategorized" } ?: "Uncategorized"
                            curEpg = attrs["tvg-id"] ?: ""
                            hasCur = true
                        }
                        l.startsWith("#EXTGRP:", ignoreCase = true) -> {
                            if (hasCur) curGroup = l.substring(8).trim().ifEmpty { curGroup }
                        }
                        l.startsWith("#") -> { /* skip */ }
                        hasCur -> {
                            n++
                            val url = l
                            when (guessType(url, curGroup, curName)) {
                                "series" -> {
                                    val sm = Regex("^(.*?)[\\s\\-]*S(\\d{1,2})\\s*E(\\d{1,3})", RegexOption.IGNORE_CASE).find(curName)
                                    val sName = sm?.groupValues?.get(1)?.trim()?.ifEmpty { curName } ?: curName
                                    val key = "$curGroup|${sName.lowercase()}"
                                    var s = seriesMap[key]
                                    if (s == null) {
                                        s = SeriesItem(id = "s$n", name = sName, poster = curLogo, catId = curGroup)
                                        seriesMap[key] = s
                                        seriesOut.add(s)
                                    }
                                    val sn = sm?.groupValues?.get(2)?.toIntOrNull() ?: 1
                                    val en = sm?.groupValues?.get(3)?.toIntOrNull()
                                        ?: ((seasonsMap[s.id]?.values?.sumOf { it.size } ?: 0) + 1)
                                    val smap = seasonsMap.getOrPut(s.id) { mutableMapOf() }
                                    smap.getOrPut(sn) { mutableListOf() }.add(
                                        EpisodeItem(id = "m$n", season = sn, episode = en, name = curName, url = url, thumb = curLogo)
                                    )
                                }
                                "movie" -> vodOut.add(
                                    VodItem(id = "m$n", name = curName, poster = curLogo, catId = curGroup,
                                        ext = Kit.guessExt(url).ifEmpty { "mp4" }, url = url)
                                )
                                else -> liveOut.add(
                                    LiveCh(id = "m$n", name = curName, num = n, logo = curLogo,
                                        catId = curGroup, epgId = curEpg, url = url)
                                )
                            }
                            hasCur = false
                        }
                    }
                }
                line = br.readLine()
            }
        }
        if (!sawHeader && (firstNonEmpty?.startsWith("<") == true)) {
            throw Exception("The address returned a web page, not a playlist (check the URL / login data)")
        }
        if (liveOut.isEmpty() && vodOut.isEmpty() && seriesOut.isEmpty()) {
            if (!sawHeader) throw Exception("Not a valid M3U playlist")
            throw Exception("Playlist is empty")
        }
        live = liveOut; vod = vodOut; series = seriesOut; seasons = seasonsMap
    }

    private fun parseAttrs(l: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        val re = Regex("([A-Za-z0-9\\-_]+)=(\"([^\"]*)\"|'([^']*)')")
        re.findAll(l).forEach { m ->
            out[m.groupValues[1].lowercase()] = m.groupValues[3].ifEmpty { m.groupValues[4] }
        }
        return out
    }

    private fun guessType(url: String, group: String, name: String): String {
        val u = url.lowercase()
        val g = group.lowercase()
        if ("/series/" in u || Regex("series|séries").containsMatchIn(g) ||
            Regex("S\\d{1,2}\\s*E\\d{1,3}", RegexOption.IGNORE_CASE).containsMatchIn(name)
        ) return "series"
        if ("/movie/" in u || Regex("\\.(mp4|mkv|avi|mov)(\\?|$)").containsMatchIn(u) ||
            Regex("movie|film|vod|cinema").containsMatchIn(g)
        ) return "movie"
        return "live"
    }

    private fun catsOf(items: List<Pair<String, String>>): List<Category> {
        val seen = LinkedHashSet<String>()
        val out = mutableListOf<Category>()
        items.forEach { (id, name) ->
            if (seen.add(id)) out.add(Category(id, name.ifEmpty { id }))
        }
        return out
    }

    override suspend fun liveCats(): List<Category> {
        ensureLoaded()
        return catsOf(live!!.map { it.catId to it.catId })
    }

    override suspend fun vodCats(): List<Category> {
        ensureLoaded()
        return catsOf(vod!!.map { it.catId to it.catId })
    }

    override suspend fun seriesCats(): List<Category> {
        ensureLoaded()
        return catsOf(series!!.map { it.catId to it.catId })
    }

    override suspend fun live(catId: String?): List<LiveCh> {
        ensureLoaded()
        return if (catId.isNullOrEmpty()) live!! else live!!.filter { it.catId == catId }
    }

    override suspend fun vod(catId: String?): List<VodItem> {
        ensureLoaded()
        return if (catId.isNullOrEmpty()) vod!! else vod!!.filter { it.catId == catId }
    }

    override suspend fun series(catId: String?): List<SeriesItem> {
        ensureLoaded()
        return if (catId.isNullOrEmpty()) series!! else series!!.filter { it.catId == catId }
    }

    override suspend fun vodInfo(item: VodItem): VodDetail = io {
        VodDetail(id = item.id, name = item.name, poster = item.poster, ext = item.ext, url = item.url)
    }

    override suspend fun seriesInfo(item: SeriesItem): SeriesDetail = io {
        ensureLoaded()
        val smap = seasons[item.id] ?: emptyMap()
        val list = smap.keys.sorted().map { k ->
            SeasonItem(k, "Season $k", (smap[k] ?: mutableListOf()).sortedBy { it.episode })
        }
        SeriesDetail(id = item.id, name = item.name, poster = item.poster, seasons = list)
    }

    override suspend fun epg(id: String, epgId: String, limit: Int): List<EpgEvent> = emptyList()

    override suspend fun resolveLive(ch: LiveCh): StreamRef = io {
        if (ch.url.isEmpty()) throw Exception("Empty stream link")
        StreamRef(ch.url, Kit.streamKind(ch.url))
    }

    override suspend fun resolveVod(v: VodItem): StreamRef = io {
        if (v.url.isEmpty()) throw Exception("Empty stream link")
        StreamRef(v.url, Kit.streamKind(v.url).let { if (it == StreamKind.UNKNOWN) Kit.streamKind("x.${v.ext}") else it })
    }

    override suspend fun resolveEpisode(ep: EpisodeItem): StreamRef = io {
        if (ep.url.isEmpty()) throw Exception("Empty stream link")
        StreamRef(ep.url, Kit.streamKind(ep.url).let { if (it == StreamKind.UNKNOWN) Kit.streamKind("x.${ep.ext}") else it })
    }

    override suspend fun catchup(ch: LiveCh, startSec: Long, durMin: Int): StreamRef? = null
}
