package com.rgbtv.app.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rgbtv.app.R
import com.rgbtv.app.data.CloudStore
import com.rgbtv.app.data.EpisodeItem
import com.rgbtv.app.data.FavItem
import com.rgbtv.app.data.HistItem
import com.rgbtv.app.data.LiveCh
import com.rgbtv.app.data.Pos
import com.rgbtv.app.data.SeriesItem
import com.rgbtv.app.data.Store
import com.rgbtv.app.data.VodItem
import com.rgbtv.app.net.Net
import com.rgbtv.app.repo.Library
import com.rgbtv.app.repo.Repository
import com.rgbtv.app.ui.PlayerActivity
import com.rgbtv.app.ui.Ui
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class Hero(
    val kind: String, val cat: String, val title: String, val meta: String,
    val plot: String, val bg: String, val vod: VodItem?, val series: SeriesItem?, val live: LiveCh?
)

private data class CItem(val h: HistItem, val pct: Int)

private fun posKey(type: String, id: String) = when (type) {
    "movie" -> "movie:$id"
    "episode" -> "ep:$id"
    else -> "$type:$id"
}

private fun pctOf(p: Pos?): Int = if (p != null && p.dur > 0) (p.pos * 100 / p.dur).toInt() else -1

@Composable
fun IconBtn(icon: Int, desc: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .tvFocus(radius = 20)
            .background(Cine.card, RoundedCornerShape(20.dp))
            .noRippleClickable(onClick)
            .padding(11.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(icon), contentDescription = desc, tint = Cine.text)
    }
}

@Composable
fun HomeScreen(nav: Navigator) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val gate = rememberGate()
    var reload by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var accId by remember { mutableStateOf("") }
    var accName by remember { mutableStateOf("") }
    var accSub by remember { mutableStateOf("") }
    var hero by remember { mutableStateOf<Hero?>(null) }
    var heroFav by remember { mutableStateOf(false) }
    var cont by remember { mutableStateOf(listOf<CItem>()) }
    var live by remember { mutableStateOf(listOf<LiveCh>()) }
    var vod by remember { mutableStateOf(listOf<VodItem>()) }
    var series by remember { mutableStateOf(listOf<SeriesItem>()) }
    var favs by remember { mutableStateOf(listOf<FavItem>()) }
    var clockTxt by remember { mutableStateOf("") }
    val watchFr = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        while (true) {
            clockTxt = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            delay(30000)
        }
    }

    fun ratingOf(r: String): Double = Ui.fmtRating(r).toDoubleOrNull() ?: -1.0

    suspend fun refreshHeroFav(aid: String, h: Hero?) {
        heroFav = when (h?.kind) {
            "movie" -> h.vod?.let { Library.isFav(ctx, aid, "movie", it.id) } ?: false
            "series" -> h.series?.let { Library.isFav(ctx, aid, "series", it.id) } ?: false
            "live" -> h.live?.let { Library.isFav(ctx, aid, "live", it.id) } ?: false
            else -> false
        }
    }

    LaunchedEffect(reload) {
        loading = true
        error = ""
        val a = Store.getAccount(Store.lastAccount()) ?: Store.accounts().firstOrNull()
        if (a == null) {
            nav.nav(Screen.Profiles)
            return@LaunchedEffect
        }
        accId = a.id
        try {
            val s = Repository.sessionFor(a)
            accName = a.name.ifEmpty { a.url }
            val exp = s.expires?.let { Ui.dateFull(it) } ?: ctx.getString(R.string.unlimited)
            accSub = "${s.status} · ${ctx.getString(R.string.expires)}: $exp" +
                (if (s.extra.isNotEmpty()) " · ${s.extra}" else "") +
                (if (CloudStore.loggedIn) " · ${CloudStore.email()}" else "")
            val h = Library.history(ctx, a.id).take(15)
            cont = h.map { CItem(it, pctOf(Library.getPos(ctx, a.id, posKey(it.type, it.id)))) }
            favs = Library.favs(ctx, a.id).take(15)
            loading = false
            val p = Repository.provider
            if (p != null) {
                val l = try { p.live(null) } catch (e: Exception) { emptyList() }
                val v = try { p.vod(null) } catch (e: Exception) { emptyList() }
                val sr = try { p.series(null) } catch (e: Exception) { emptyList() }
                Repository.lastLive = l
                live = l.take(15)
                vod = v.sortedByDescending { it.added }.take(15)
                series = sr.sortedByDescending { it.added }.take(15)
                val topS = sr.filter { it.backdrop.isNotEmpty() || it.poster.isNotEmpty() }
                    .maxByOrNull { ratingOf(it.rating) }
                val topV = v.filter { it.poster.isNotEmpty() }.maxByOrNull { ratingOf(it.rating) }
                val hh = when {
                    topS != null && (topV == null || ratingOf(topS.rating) >= ratingOf(topV.rating)) -> Hero(
                        "series", ctx.getString(R.string.series).uppercase(Locale.getDefault()), topS.name,
                        listOf(Ui.fmtRating(topS.rating).let { if (it.isNotEmpty()) "★ $it" else "" }, topS.year, topS.genre)
                            .filter { it.isNotEmpty() }.joinToString(" · "),
                        topS.plot, topS.backdrop.ifEmpty { topS.poster }, null, topS, null
                    )
                    topV != null -> Hero(
                        "movie", ctx.getString(R.string.movies).uppercase(Locale.getDefault()), topV.name,
                        listOf(Ui.fmtRating(topV.rating).let { if (it.isNotEmpty()) "★ $it" else "" }, topV.year, topV.genre)
                            .filter { it.isNotEmpty() }.joinToString(" · "),
                        topV.plot, topV.poster, topV, null, null
                    )
                    l.isNotEmpty() -> Hero(
                        "live", ctx.getString(R.string.live_tv).uppercase(Locale.getDefault()), l.first().name,
                        "● ${ctx.getString(R.string.live_now)}", "", l.first().logo, null, null, l.first()
                    )
                    else -> null
                }
                hero = hh
                refreshHeroFav(a.id, hh)
            }
        } catch (e: Exception) {
            loading = false
            error = Net.userMsg(ctx, e)
        }
    }

    LaunchedEffect(loading, reload) {
        if (!loading && error.isEmpty()) {
            try { watchFr.requestFocus() } catch (e: Exception) { }
        }
    }

    fun openHist(h: HistItem) {
        scope.launch {
            try {
                val o = JSONObject(h.data)
                when (h.type) {
                    "live" -> {
                        val ch = LiveCh.fromJson(o)
                        gate.run(accId, "live", ch.id, ch.name) {
                            PlayerActivity.playLive(ctx, ch, Repository.lastLive)
                        }
                    }
                    "movie" -> {
                        val v = VodItem.fromJson(o)
                        val pos = Library.getPos(ctx, accId, "movie:${v.id}")
                        gate.run(accId, "movie", v.id, v.name) {
                            PlayerActivity.playVod(ctx, v, null, pos?.pos ?: 0)
                        }
                    }
                    "episode" -> {
                        val ep = EpisodeItem.fromJson(o)
                        val pos = Library.getPos(ctx, accId, "ep:${ep.id}")
                        gate.run(accId, "episode", ep.id, ep.name) {
                            PlayerActivity.playEpisode(ctx, "", listOf(ep), 0, pos?.pos ?: 0)
                        }
                    }
                    "series" -> nav.open(Screen.Detail("series", h.data))
                }
            } catch (e: Exception) { Ui.toast(ctx, ctx.getString(R.string.err_unknown)) }
        }
    }

    fun openFav(f: FavItem) {
        scope.launch {
            if (f.type == "series") {
                nav.open(Screen.Detail("series", f.data))
                return@launch
            }
            try {
                val o = JSONObject(f.data)
                when (f.type) {
                    "live" -> {
                        val ch = LiveCh.fromJson(o)
                        gate.run(accId, "live", ch.id, ch.name) {
                            PlayerActivity.playLive(ctx, ch, Repository.lastLive)
                        }
                    }
                    "movie" -> {
                        val v = VodItem.fromJson(o)
                        val pos = Library.getPos(ctx, accId, "movie:${v.id}")
                        gate.run(accId, "movie", v.id, v.name) {
                            PlayerActivity.playVod(ctx, v, null, pos?.pos ?: 0)
                        }
                    }
                    "episode" -> {
                        val ep = EpisodeItem.fromJson(o)
                        val pos = Library.getPos(ctx, accId, "ep:${ep.id}")
                        gate.run(accId, "episode", ep.id, ep.name) {
                            PlayerActivity.playEpisode(ctx, "", listOf(ep), 0, pos?.pos ?: 0)
                        }
                    }
                }
            } catch (e: Exception) { Ui.toast(ctx, ctx.getString(R.string.err_unknown)) }
        }
    }

    fun heroWatch() {
        val h = hero ?: return
        when (h.kind) {
            "movie" -> h.vod?.let { scope.goVod(ctx, gate, accId, it) }
            "series" -> h.series?.let {
                gate.run(accId, "series", it.id, it.name) {
                    nav.open(Screen.Detail("series", it.toJson().toString()))
                }
            }
            "live" -> h.live?.let { scope.goLive(ctx, gate, accId, it, Repository.lastLive) }
        }
    }

    fun heroFavToggle() {
        val h = hero ?: return
        val item = when (h.kind) {
            "movie" -> h.vod?.let { FavItem("movie", it.id, it.name, it.poster, it.toJson().toString()) }
            "series" -> h.series?.let { FavItem("series", it.id, it.name, it.poster, it.toJson().toString()) }
            "live" -> h.live?.let { FavItem("live", it.id, it.name, it.logo, it.toJson().toString()) }
            else -> null
        } ?: return
        scope.launch {
            val now = Library.toggleFav(ctx, accId, item)
            heroFav = now
            favs = Library.favs(ctx, accId).take(15)
            Ui.toast(ctx, ctx.getString(if (now) R.string.added_fav else R.string.removed_fav))
        }
    }

    Box(Modifier.fillMaxSize().background(Cine.bg)) {
        when {
            loading -> SkeletonHome()
            error.isNotEmpty() -> ErrorState(error) { reload++ }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("RGBTv", style = Cine.display.copy(fontSize = 28.sp))
                        Column(Modifier.weight(1f).padding(start = 16.dp)) {
                            Text(accName, style = Cine.h3)
                            Text(accSub, style = Cine.small, maxLines = 1)
                        }
                        Box(
                            Modifier
                                .background(Cine.card, RoundedCornerShape(20.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(clockTxt, style = Cine.h3)
                        }
                        Spacer(Modifier.width(8.dp))
                        IconBtn(R.drawable.ic_nav_users, "profiles") { nav.nav(Screen.Profiles) }
                        Spacer(Modifier.width(8.dp))
                        IconBtn(R.drawable.ic_nav_settings, "settings") { nav.nav(Screen.Settings) }
                    }
                }
                hero?.let { h ->
                    item {
                        HeroBanner(
                            bg = h.bg, cat = h.cat, title = h.title, meta = h.meta, plot = h.plot,
                            watchLabel = ctx.getString(R.string.watch_now),
                            favLabel = (if (heroFav) "★ " else "＋ ") + ctx.getString(R.string.my_list),
                            onWatch = { heroWatch() },
                            onFav = { heroFavToggle() },
                            watchMod = Modifier.focusRequester(watchFr)
                        )
                    }
                }
                if (cont.isNotEmpty()) {
                    item {
                        Rail(ctx.getString(R.string.continue_watching), cont, { "h:${it.h.type}:${it.h.id}" }) { c ->
                            PosterCard(c.h.img, c.h.name, Ui.dateFull(c.h.at), c.pct, onClick = { openHist(c.h) })
                        }
                    }
                }
                if (live.isNotEmpty()) {
                    item {
                        Rail(ctx.getString(R.string.live_now), live, { "l:${it.id}" }) { ch ->
                            ChannelCard(
                                ch.logo, ch.name,
                                if (ch.num > 0) ch.num.toString() else "",
                                onClick = { scope.goLive(ctx, gate, accId, ch, Repository.lastLive) }
                            )
                        }
                    }
                }
                if (vod.isNotEmpty()) {
                    item {
                        Rail(ctx.getString(R.string.trending_now), vod, { "m:${it.id}" }) { v ->
                            PosterCard(
                                v.poster, v.name,
                                listOf(v.year, Ui.fmtRating(v.rating).let { if (it.isNotEmpty()) "★ $it" else "" })
                                    .filter { it.isNotEmpty() }.joinToString(" · "),
                                onClick = {
                                    gate.run(accId, "movie", v.id, v.name) {
                                        nav.open(Screen.Detail("movie", v.toJson().toString()))
                                    }
                                }
                            )
                        }
                    }
                }
                if (series.isNotEmpty()) {
                    item {
                        Rail(ctx.getString(R.string.popular_series), series, { "s:${it.id}" }) { s ->
                            PosterCard(
                                s.poster, s.name,
                                listOf(s.year, Ui.fmtRating(s.rating).let { if (it.isNotEmpty()) "★ $it" else "" })
                                    .filter { it.isNotEmpty() }.joinToString(" · "),
                                onClick = {
                                    gate.run(accId, "series", s.id, s.name) {
                                        nav.open(Screen.Detail("series", s.toJson().toString()))
                                    }
                                }
                            )
                        }
                    }
                }
                if (favs.isNotEmpty()) {
                    item {
                        Rail(ctx.getString(R.string.favorites), favs, { "f:${it.type}:${it.id}" }) { f ->
                            PosterCard(
                                f.img, f.name,
                                onClick = { openFav(f) },
                                onLong = {
                                    scope.launch {
                                        Library.toggleFav(ctx, accId, f)
                                        favs = Library.favs(ctx, accId).take(15)
                                        Ui.toast(ctx, ctx.getString(R.string.removed_fav))
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
        GateDialog(gate)
    }
}
