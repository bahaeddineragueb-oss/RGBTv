package com.rgbtv.app.repo

import android.content.Context
import com.rgbtv.app.data.Account
import com.rgbtv.app.data.EpgEvent
import com.rgbtv.app.data.LiveCh
import com.rgbtv.app.data.SessionSummary
import com.rgbtv.app.data.Store
import com.rgbtv.app.net.Provider
import com.rgbtv.app.net.ProviderFactory
import com.rgbtv.app.net.SessionInfo
import com.rgbtv.app.net.Xmltv
import com.rgbtv.app.net.io
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Single session: current account + provider + EPG. Survives rotation & fragment swaps. */
object Repository {
    private val mutex = Mutex()
    var provider: Provider? = null
        private set
    var accountId: String? = null
        private set
    var session: SessionInfo? = null
        private set
    private val xmltv = mutableMapOf<String, Xmltv>()
    /** Last loaded live list (for channel zapping) + keys unlocked with PIN this session. */
    var lastLive: List<LiveCh> = emptyList()
    val unlocked = mutableSetOf<String>()

    fun currentAccount(): Account? = Store.getAccount(accountId ?: Store.lastAccount())

    suspend fun ensure(acc: Account): Provider = mutex.withLock {
        if (provider != null && accountId == acc.id) return provider!!
        try { provider?.close() } catch (e: Exception) { }
        val p = ProviderFactory.create(acc)
        provider = p
        accountId = acc.id
        session = null
        p
    }

    /** Login, or reuse the live session after rotation. */
    suspend fun sessionFor(acc: Account): SessionInfo {
        val p = ensure(acc)
        session?.let { return it }
        return mutex.withLock {
            session?.let { return it }
            val s = p.login()
            session = s
            Store.setLastAccount(acc.id)
            Store.setLastSession(acc.id, SessionSummary(s.status, s.expires))
            s
        }
    }

    suspend fun logout() = mutex.withLock {
        try { provider?.close() } catch (e: Exception) { }
        provider = null; accountId = null; session = null
    }

    fun xmltvFor(ctx: Context, acc: Account): Xmltv? {
        if (acc.epgUrl.isBlank()) return null
        return xmltv.getOrPut(acc.id) { Xmltv(ctx.applicationContext, acc.id, acc.epgUrl.trim()) }
    }

    fun dropXmltv(accId: String) {
        xmltv.remove(accId)?.clear()
    }

    data class SearchResult(
        val live: List<LiveCh>,
        val vod: List<com.rgbtv.app.data.VodItem>,
        val series: List<com.rgbtv.app.data.SeriesItem>
    )

    suspend fun search(q: String): SearchResult = io {
        val p = provider ?: return@io SearchResult(emptyList(), emptyList(), emptyList())
        val needle = q.trim().lowercase()
        if (needle.length < 2) return@io SearchResult(emptyList(), emptyList(), emptyList())
        // Lists come from memory/disk cache — no repeated downloads.
        val l = p.live(null).filter { it.name.lowercase().contains(needle) }.take(120)
        val v = p.vod(null).filter { it.name.lowercase().contains(needle) }.take(120)
        val s = p.series(null).filter { it.name.lowercase().contains(needle) }.take(120)
        SearchResult(l, v, s)
    }

    /** API short-EPG first, XMLTV fallback. */
    suspend fun epgFor(ctx: Context, ch: LiveCh, limit: Int): List<EpgEvent> {
        val p = provider
        if (p != null) {
            try {
                val api = p.epg(ch.id, ch.epgId, limit)
                if (api.isNotEmpty()) return api
            } catch (e: Exception) { }
        }
        val acc = currentAccount() ?: return emptyList()
        val x = xmltvFor(ctx, acc) ?: return emptyList()
        return try {
            io {
                val now = System.currentTimeMillis() / 1000
                x.programs(ch, now - 86400, now + 3 * 86400, limit)
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun nowNext(ctx: Context, ch: LiveCh): Pair<EpgEvent?, EpgEvent?> {
        val p = provider
        if (p != null && currentAccount()?.type != "m3u") {
            try {
                val api = p.epg(ch.id, ch.epgId, 4)
                if (api.isNotEmpty()) {
                    val now = System.currentTimeMillis() / 1000
                    val cur = api.firstOrNull { it.start <= now && now < it.end }
                    val nxt = api.firstOrNull { it.start > now }
                        ?: api.firstOrNull { it !== cur && it.end > now }
                    if (cur != null || nxt != null) return cur to nxt
                    val first = api.first()
                    return (if (first.start <= now) first else null) to (if (first.start > now) first else api.getOrNull(1))
                }
            } catch (e: Exception) { }
        }
        val acc = currentAccount() ?: return null to null
        val x = xmltvFor(ctx, acc) ?: return null to null
        return try { io { x.nowNext(ch) } } catch (e: Exception) { null to null }
    }
}
