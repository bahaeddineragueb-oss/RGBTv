package com.rgbtv.app.data

import org.json.JSONArray
import org.json.JSONObject

/* ---------- accounts & settings ---------- */

data class Account(
    var id: String = "",
    var name: String = "",
    var type: String = "xtream", // xtream | stalker | m3u
    var url: String = "",
    var username: String = "",
    var password: String = "",
    var mac: String = "",
    var epgUrl: String = "",
    var token: String = "",
    var endpoint: String = "",
    var createdAt: Long = 0
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("type", type).put("url", url)
        .put("username", username).put("password", password).put("mac", mac)
        .put("epgUrl", epgUrl).put("token", token).put("endpoint", endpoint)
        .put("createdAt", createdAt)

    companion object {
        fun fromJson(o: JSONObject) = Account(
            id = o.optString("id"), name = o.optString("name"),
            type = o.optString("type", "xtream").ifEmpty { "xtream" },
            url = o.optString("url"), username = o.optString("username"),
            password = o.optString("password"), mac = o.optString("mac"),
            epgUrl = o.optString("epgUrl"), token = o.optString("token"),
            endpoint = o.optString("endpoint"), createdAt = o.optLong("createdAt")
        )
    }
}

data class Settings(
    var liveFormat: String = "auto", // auto | hls | ts
    var lang: String = "system",      // system | en | ar | fr
    var accent: String = "blue",      // blue | green | red | purple | gold
    var parental: Boolean = false,
    var autoNext: Boolean = true,
    var refreshHours: Int = 12
) {
    fun toJson(): JSONObject = JSONObject()
        .put("liveFormat", liveFormat).put("lang", lang).put("accent", accent)
        .put("parental", parental).put("autoNext", autoNext).put("refreshHours", refreshHours)

    companion object {
        fun fromJson(s: String?): Settings {
            val o = try { JSONObject(s ?: "{}") } catch (e: Exception) { JSONObject() }
            return Settings(
                liveFormat = o.optString("liveFormat", "auto").ifEmpty { "auto" },
                lang = o.optString("lang", "system").ifEmpty { "system" },
                accent = o.optString("accent", "blue").ifEmpty { "blue" },
                parental = o.optBoolean("parental", false),
                autoNext = o.optBoolean("autoNext", true),
                refreshHours = o.optInt("refreshHours", 12).takeIf { it > 0 } ?: 12
            )
        }
    }
}

data class SessionSummary(val status: String, val expires: Long?) {
    fun toJson(): JSONObject = JSONObject().put("status", status).put("expires", expires ?: -1)
    companion object {
        fun fromJson(s: String?): SessionSummary? {
            if (s.isNullOrEmpty()) return null
            return try {
                val o = JSONObject(s)
                val e = o.optLong("expires", -1)
                SessionSummary(o.optString("status"), if (e < 0) null else e)
            } catch (e: Exception) { null }
        }
    }
}

/* ---------- content ---------- */

data class Category(val id: String, val name: String, val censored: Boolean = false)

data class LiveCh(
    val id: String, val name: String, val num: Int = 0, val logo: String = "",
    val catId: String = "", val epgId: String = "", val archive: Boolean = false,
    val archiveDays: Int = 0, val cmd: String = "", val url: String = ""
) {
    fun toJson(): JSONObject = JSONObject().put("type", "live").put("id", id).put("name", name)
        .put("num", num).put("logo", logo).put("catId", catId).put("epgId", epgId)
        .put("archive", archive).put("archiveDays", archiveDays).put("cmd", cmd).put("url", url)
    companion object {
        fun fromJson(o: JSONObject) = LiveCh(
            o.optString("id"), o.optString("name"), o.optInt("num"), o.optString("logo"),
            o.optString("catId"), o.optString("epgId"), o.optBoolean("archive"),
            o.optInt("archiveDays"), o.optString("cmd"), o.optString("url")
        )
    }
}

data class VodItem(
    val id: String, val name: String, val poster: String = "", val catId: String = "",
    val rating: String = "", val ext: String = "mp4", val added: Long = 0, val year: String = "",
    val url: String = "", val cmd: String = "", val isSeries: Boolean = false,
    val seriesNums: String = "", val plot: String = "", val genre: String = "", val cast: String = ""
) {
    fun toJson(): JSONObject = JSONObject().put("type", "movie").put("id", id).put("name", name)
        .put("poster", poster).put("catId", catId).put("rating", rating).put("ext", ext)
        .put("added", added).put("year", year).put("url", url).put("cmd", cmd)
        .put("isSeries", isSeries).put("seriesNums", seriesNums)
        .put("plot", plot).put("genre", genre).put("cast", cast)
    companion object {
        fun fromJson(o: JSONObject) = VodItem(
            o.optString("id"), o.optString("name"), o.optString("poster"), o.optString("catId"),
            o.optString("rating"), o.optString("ext", "mp4").ifEmpty { "mp4" },
            o.optLong("added"), o.optString("year"), o.optString("url"), o.optString("cmd"),
            o.optBoolean("isSeries"), o.optString("seriesNums"),
            o.optString("plot"), o.optString("genre"), o.optString("cast")
        )
    }
}

data class SeriesItem(
    val id: String, val name: String, val poster: String = "", val catId: String = "",
    val rating: String = "", val year: String = "", val plot: String = "",
    val genre: String = "", val cast: String = "", val backdrop: String = "", val added: Long = 0
) {
    fun toJson(): JSONObject = JSONObject().put("type", "series").put("id", id).put("name", name)
        .put("poster", poster).put("catId", catId).put("rating", rating).put("year", year)
        .put("plot", plot).put("genre", genre).put("cast", cast)
        .put("backdrop", backdrop).put("added", added)
    companion object {
        fun fromJson(o: JSONObject) = SeriesItem(
            o.optString("id"), o.optString("name"), o.optString("poster"), o.optString("catId"),
            o.optString("rating"), o.optString("year"), o.optString("plot"),
            o.optString("genre"), o.optString("cast"), o.optString("backdrop"), o.optLong("added")
        )
    }
}

data class VodDetail(
    val id: String, val name: String, val poster: String = "", val backdrop: String = "",
    val rating: String = "", val year: String = "", val genre: String = "",
    val duration: String = "", val cast: String = "", val director: String = "",
    val plot: String = "", val ext: String = "mp4", val url: String = "", val cmd: String = ""
)

data class SeasonItem(val num: Int, val name: String, val episodes: List<EpisodeItem>)

data class EpisodeItem(
    val id: String, val season: Int = 1, val episode: Int = 0, val name: String = "",
    val ext: String = "mp4", val thumb: String = "", val plot: String = "",
    val duration: String = "", val url: String = "", val cmd: String = "", val seriesNum: String = ""
) {
    fun toJson(): JSONObject = JSONObject().put("type", "episode").put("id", id)
        .put("season", season).put("episode", episode).put("name", name).put("ext", ext)
        .put("thumb", thumb).put("plot", plot).put("duration", duration)
        .put("url", url).put("cmd", cmd).put("seriesNum", seriesNum)
    companion object {
        fun fromJson(o: JSONObject) = EpisodeItem(
            o.optString("id"), o.optInt("season", 1), o.optInt("episode"),
            o.optString("name"), o.optString("ext", "mp4").ifEmpty { "mp4" },
            o.optString("thumb"), o.optString("plot"), o.optString("duration"),
            o.optString("url"), o.optString("cmd"), o.optString("seriesNum")
        )
    }
}

data class SeriesDetail(
    val id: String, val name: String, val poster: String = "", val backdrop: String = "",
    val rating: String = "", val year: String = "", val genre: String = "",
    val cast: String = "", val director: String = "", val plot: String = "",
    val seasons: List<SeasonItem> = emptyList()
)

data class EpgEvent(val title: String, val desc: String = "", val start: Long = 0, val end: Long = 0)

/* ---------- favorites / history / positions ---------- */

data class FavItem(val type: String, val id: String, val name: String, val img: String, val data: String) {
    fun toJson(): JSONObject = JSONObject().put("type", type).put("id", id)
        .put("name", name).put("img", img).put("data", data)
    companion object {
        fun fromJson(o: JSONObject) = FavItem(
            o.optString("type"), o.optString("id"), o.optString("name"),
            o.optString("img"), o.optString("data")
        )
        fun fromArray(s: String?): MutableList<FavItem> {
            val out = mutableListOf<FavItem>()
            if (s.isNullOrEmpty()) return out
            try {
                val a = JSONArray(s)
                for (i in 0 until a.length()) out.add(fromJson(a.getJSONObject(i)))
            } catch (e: Exception) { }
            return out
        }
    }
}

data class HistItem(val type: String, val id: String, val name: String, val img: String, val data: String, val at: Long = 0) {
    fun toJson(): JSONObject = JSONObject().put("type", type).put("id", id)
        .put("name", name).put("img", img).put("data", data).put("at", at)
    companion object {
        fun fromJson(o: JSONObject) = HistItem(
            o.optString("type"), o.optString("id"), o.optString("name"),
            o.optString("img"), o.optString("data"), o.optLong("at")
        )
        fun fromArray(s: String?): MutableList<HistItem> {
            val out = mutableListOf<HistItem>()
            if (s.isNullOrEmpty()) return out
            try {
                val a = JSONArray(s)
                for (i in 0 until a.length()) out.add(fromJson(a.getJSONObject(i)))
            } catch (e: Exception) { }
            return out
        }
    }
}

data class Pos(val pos: Long, val dur: Long, val at: Long)
