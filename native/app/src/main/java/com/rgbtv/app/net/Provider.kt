package com.rgbtv.app.net

import com.rgbtv.app.data.Account
import com.rgbtv.app.data.Category
import com.rgbtv.app.data.EpgEvent
import com.rgbtv.app.data.EpisodeItem
import com.rgbtv.app.data.LiveCh
import com.rgbtv.app.data.SeriesDetail
import com.rgbtv.app.data.SeriesItem
import com.rgbtv.app.data.VodDetail
import com.rgbtv.app.data.VodItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLDecoder

enum class StreamKind { HLS, DASH, PROGRESSIVE, UNKNOWN }

data class SessionInfo(val status: String, val expires: Long?, val extra: String = "")
data class StreamRef(val url: String, val kind: StreamKind, val headers: Map<String, String> = emptyMap())

/** Common provider interface. All calls are main-safe (they switch to IO internally). */
interface Provider {
    val account: Account
    suspend fun login(): SessionInfo
    suspend fun liveCats(): List<Category>
    suspend fun vodCats(): List<Category>
    suspend fun seriesCats(): List<Category>
    suspend fun live(catId: String?): List<LiveCh>
    suspend fun vod(catId: String?): List<VodItem>
    suspend fun series(catId: String?): List<SeriesItem>
    suspend fun vodInfo(item: VodItem): VodDetail
    suspend fun seriesInfo(item: SeriesItem): SeriesDetail
    suspend fun epg(id: String, epgId: String, limit: Int): List<EpgEvent>
    suspend fun resolveLive(ch: LiveCh): StreamRef
    suspend fun resolveVod(v: VodItem): StreamRef
    suspend fun resolveEpisode(ep: EpisodeItem): StreamRef
    suspend fun catchup(ch: LiveCh, startSec: Long, durMin: Int): StreamRef?
    fun prefetch(ch: LiveCh) {}
    fun close() {}
}

suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

object ProviderFactory {
    fun create(acc: Account): Provider {
        if (acc.type == "stalker") return StalkerProvider(acc)
        if (acc.type == "m3u") {
            // Xtream panels behind a get.php link work far better through the API.
            val m = Regex(
                "^(https?://[^/?#]+)(?:/[^?#]*)?/get\\.php\\?(?:.*&)?username=([^&]+)&password=([^&#]+)",
                RegexOption.IGNORE_CASE
            ).find(acc.url.trim())
            if (m != null) {
                val x = acc.copy(
                    type = "xtream", url = m.groupValues[1],
                    username = URLDecoder.decode(m.groupValues[2], "UTF-8"),
                    password = URLDecoder.decode(m.groupValues[3], "UTF-8")
                )
                return XtreamProvider(x)
            }
            return M3uProvider(acc)
        }
        return XtreamProvider(acc)
    }
}
