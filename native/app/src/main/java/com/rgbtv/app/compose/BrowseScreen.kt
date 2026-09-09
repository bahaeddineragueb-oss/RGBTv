package com.rgbtv.app.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.rgbtv.app.R
import com.rgbtv.app.data.Category
import com.rgbtv.app.data.FavItem
import com.rgbtv.app.data.LiveCh
import com.rgbtv.app.data.SeriesItem
import com.rgbtv.app.data.Store
import com.rgbtv.app.data.VodItem
import com.rgbtv.app.ui.Kit
import com.rgbtv.app.net.Net
import com.rgbtv.app.repo.Library
import com.rgbtv.app.repo.Repository
import com.rgbtv.app.ui.PlayerActivity
import com.rgbtv.app.ui.Ui
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun BrowseScreen(nav: Navigator, mode: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val gate = rememberGate()
    val liveMode = mode == Screen.LIVE
    val vodMode = mode == Screen.VOD
    val title = when (mode) {
        Screen.LIVE -> stringResource(R.string.live_tv)
        Screen.VOD -> stringResource(R.string.movies)
        else -> stringResource(R.string.series)
    }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var reload by remember { mutableStateOf(0) }
    var accId by remember { mutableStateOf("") }
    var cats by remember { mutableStateOf(listOf<Category>()) }
    var selCat by remember { mutableStateOf("") }
    var allLive by remember { mutableStateOf(listOf<LiveCh>()) }
    var allVod by remember { mutableStateOf(listOf<VodItem>()) }
    var allSeries by remember { mutableStateOf(listOf<SeriesItem>()) }
    var shownLive by remember { mutableStateOf(listOf<LiveCh>()) }
    var shownVod by remember { mutableStateOf(listOf<VodItem>()) }
    var shownSeries by remember { mutableStateOf(listOf<SeriesItem>()) }
    var query by remember { mutableStateOf("") }
    var qRun by remember { mutableStateOf("") }
    var sortIdx by remember { mutableStateOf(0) }
    var featV by remember { mutableStateOf<VodItem?>(null) }
    var featS by remember { mutableStateOf<SeriesItem?>(null) }
    var currentLive by remember { mutableStateOf<LiveCh?>(null) }
    var pvInfo by remember { mutableStateOf<Triple<String, String, Int>?>(null) }
    var lockTick by remember { mutableStateOf(0) }

    val sortLabels = listOf(
        stringResource(R.string.sort_default), stringResource(R.string.sort_az),
        stringResource(R.string.sort_added), stringResource(R.string.sort_rating)
    )

    fun apply() {
        val q = qRun.trim().lowercase()
        if (liveMode) {
            var l = if (selCat.isEmpty()) allLive else allLive.filter { it.catId == selCat }
            if (q.isNotEmpty()) l = l.filter { it.name.lowercase().contains(q) || it.num.toString() == q }
            shownLive = l
        } else if (vodMode) {
            var l = if (selCat.isEmpty()) allVod else allVod.filter { it.catId == selCat }
            if (q.isNotEmpty()) l = l.filter { it.name.lowercase().contains(q) || it.year == q }
            l = when (sortIdx) {
                1 -> l.sortedBy { it.name.lowercase() }
                2 -> l.sortedByDescending { it.added }
                3 -> l.sortedByDescending { Ui.fmtRating(it.rating).toDoubleOrNull() ?: -1.0 }
                else -> l
            }
            shownVod = l
            featV = if (selCat.isEmpty() && q.isEmpty()) allVod.maxByOrNull { Ui.fmtRating(it.rating).toDoubleOrNull() ?: -1.0 } else null
        } else {
            var l = if (selCat.isEmpty()) allSeries else allSeries.filter { it.catId == selCat }
            if (q.isNotEmpty()) l = l.filter { it.name.lowercase().contains(q) || it.year == q }
            l = when (sortIdx) {
                1 -> l.sortedBy { it.name.lowercase() }
                2 -> l.sortedByDescending { it.added }
                3 -> l.sortedByDescending { Ui.fmtRating(it.rating).toDoubleOrNull() ?: -1.0 }
                else -> l
            }
            shownSeries = l
            featS = if (selCat.isEmpty() && q.isEmpty()) allSeries.maxByOrNull { Ui.fmtRating(it.rating).toDoubleOrNull() ?: -1.0 } else null
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
            Repository.sessionFor(a)
            val p = Repository.provider
            cats = if (liveMode) p?.liveCats().orEmpty() else if (vodMode) p?.vodCats().orEmpty() else p?.seriesCats().orEmpty()
            if (liveMode) allLive = p?.live(null).orEmpty()
            else if (vodMode) allVod = p?.vod(null).orEmpty()
            else allSeries = p?.series(null).orEmpty()
            apply()
            loading = false
        } catch (e: Exception) {
            loading = false
            error = Net.userMsg(ctx, e)
        }
    }

    LaunchedEffect(query) {
        delay(300)
        qRun = query
    }

    LaunchedEffect(qRun, selCat, sortIdx) { apply() }

    LaunchedEffect(currentLive) {
        pvInfo = null
        val ch = currentLive ?: return@LaunchedEffect
        delay(350)
        scope.launch {
            try {
                val (n, nx) = Repository.nowNext(ctx, ch)
                if (n != null) {
                    val st = if (n.start > 1000000000000L) n.start / 1000 else n.start
                    val en = if (n.end > 1000000000000L) n.end / 1000 else n.end
                    val now = System.currentTimeMillis() / 1000
                    val pct = if (en > st) ((now - st) * 100 / (en - st)).toInt().coerceIn(0, 100) else 0
                    pvInfo = Triple(n.title, nx?.title.orEmpty(), pct)
                }
            } catch (e: Exception) { }
        }
    }

    fun badgeForLive(ch: LiveCh): String {
        lockTick.let { }
        return if (Store.isLocked(accId, "live", ch.id)) "🔒" else ""
    }

    fun badgeForVod(v: VodItem): String {
        return if (Store.isLocked(accId, "movie", v.id)) "🔒" else ""
    }

    fun badgeForSeries(s: SeriesItem): String {
        return if (Store.isLocked(accId, "series", s.id)) "🔒" else ""
    }

    suspend fun openLive(ch: LiveCh) {
        if (Store.isLocked(accId, "live", ch.id) || (Store.settings().parental && Kit.isAdult(ch.name))) {
            val ok = gate.askPin(accId, ctx.getString(R.string.pin_title))
            if (!ok) return
        }
        PlayerActivity.playLive(ctx, ch, shownLive)
    }

    Box(Modifier.fillMaxSize().background(Cine.bg)) {
        when {
            loading -> SkeletonGrid()
            error.isNotEmpty() -> ErrorState(error) { reload++ }
            else -> Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = Cine.h1)
                    Text(
                        " · " + when {
                            liveMode -> shownLive.size
                            vodMode -> shownVod.size
                            else -> shownSeries.size
                        },
                        style = Cine.body
                    )
                    Spacer(Modifier.weight(1f))
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.filter_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.width(200.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Cine.card2,
                            unfocusedContainerColor = Cine.card2,
                            focusedTextColor = Cine.text,
                            unfocusedTextColor = Cine.text,
                            cursorColor = Cine.accent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                    if (!liveMode) {
                        Spacer(Modifier.width(8.dp))
                        SmallBtn("⇅ ${sortLabels[sortIdx]}") { sortIdx = (sortIdx + 1) % 4 }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (vodMode && featV != null) {
                    FeaturedStrip(
                        poster = featV!!.poster, cat = cats.find { it.id == featV!!.catId }?.name.orEmpty(),
                        title = featV!!.name,
                        meta = listOf(featV!!.year, Ui.fmtRating(featV!!.rating).let { if (it.isNotEmpty()) "★ $it" else "" }).filter { it.isNotEmpty() }.joinToString(" · "),
                        plot = featV!!.plot,
                        playLabel = stringResource(R.string.watch_now), detailsLabel = stringResource(R.string.details),
                        onPlay = { val v = featV!!; scope.goVod(ctx, gate, accId, v) },
                        onDetails = {
                            val v = featV!!
                            gate.run(accId, "movie", v.id, v.name) {
                                nav.open(Screen.Detail("movie", v.toJson().toString()))
                            }
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (!vodMode && !liveMode && featS != null) {
                    FeaturedStrip(
                        poster = featS!!.poster, cat = cats.find { it.id == featS!!.catId }?.name.orEmpty(),
                        title = featS!!.name,
                        meta = listOf(featS!!.year, Ui.fmtRating(featS!!.rating).let { if (it.isNotEmpty()) "★ $it" else "" }).filter { it.isNotEmpty() }.joinToString(" · "),
                        plot = featS!!.plot,
                        playLabel = stringResource(R.string.watch_now), detailsLabel = stringResource(R.string.details),
                        onPlay = {
                            val s = featS!!
                            scope.launch {
                                try {
                                    Repository.sessionFor(Store.getAccount(accId)!!)
                                    val eps = Repository.provider?.episodes(s.id).orEmpty()
                                    val first = eps.firstOrNull()?.second?.firstOrNull()
                                    if (first != null) scope.goEp(ctx, gate, accId, s.name, listOf(first), 0)
                                    else nav.open(Screen.Detail("series", s.toJson().toString()))
                                } catch (e: Exception) { Ui.toast(ctx, Net.userMsg(ctx, e)) }
                            }
                        },
                        onDetails = {
                            val s = featS!!
                            gate.run(accId, "series", s.id, s.name) {
                                nav.open(Screen.Detail("series", s.toJson().toString()))
                            }
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Row(Modifier.weight(1f)) {
                    LazyColumn(Modifier.weight(1.05f)) {
                        item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .tvFocus(radius = 10)
                                    .background(if (selCat.isEmpty()) Cine.card2 else Color.Transparent, RoundedCornerShape(10.dp))
                                    .noRippleClickable { selCat = "" }
                                    .padding(12.dp)
                            ) {
                                Text(stringResource(R.string.all), style = Cine.h3)
                            }
                        }
                        items(cats, key = { "c:${it.id}" }) { c ->
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .tvFocus(radius = 10)
                                    .background(if (selCat == c.id) Cine.card2 else Color.Transparent, RoundedCornerShape(10.dp))
                                    .noRippleClickable {
                                        if (c.censored && Store.settings().parental &&
                                            !Repository.unlocked.contains("cat:$accId:${c.id}")
                                        ) {
                                            scope.launch {
                                                if (gate.askPin(ctx.getString(R.string.pin_title))) {
                                                    Repository.unlocked.add("cat:$accId:${c.id}")
                                                    selCat = c.id
                                                }
                                            }
                                        } else selCat = c.id
                                    }
                                    .padding(12.dp)
                            ) {
                                Text(c.name, style = Cine.h3, maxLines = 1)
                            }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    if (liveMode) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(180.dp),
                            modifier = Modifier.weight(3f)
                        ) {
                            items(shownLive, key = { "l:${it.id}" }) { ch ->
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    ChannelCard(
                                        ch.logo.ifEmpty { null }, ch.name,
                                        if (ch.num > 0) ch.num.toString() else "",
                                        badge = badgeForLive(ch).ifEmpty { null },
                                        onClick = { scope.launch { openLive(ch) } },
                                        onLong = {
                                            scope.launch {
                                                val k = Store.toggleLock(accId, "live", ch.id)
                                                lockTick++
                                                Ui.toast(ctx, ctx.getString(if (k) R.string.locked else R.string.unlocked))
                                            }
                                        },
                                        onFocus = { currentLive = ch }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.width(290.dp)) {
                            currentLive?.let { ch ->
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(Cine.card, RoundedCornerShape(16.dp))
                                        .padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    AsyncImage(
                                        model = ch.logo.ifEmpty { null },
                                        contentDescription = null,
                                        placeholder = painterResource(R.drawable.img_ph),
                                        error = painterResource(R.drawable.img_ph),
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp)
                                            .background(Cine.card2, RoundedCornerShape(12.dp))
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(ch.name, style = Cine.h2, maxLines = 2)
                                    if (pvInfo != null) {
                                        Spacer(Modifier.height(6.dp))
                                        ProgressLine(pvInfo!!.third)
                                        Spacer(Modifier.height(6.dp))
                                        Text(pvInfo!!.first, style = Cine.small, maxLines = 1)
                                        if (pvInfo!!.second.isNotEmpty()) Text("⏭ ${pvInfo!!.second}", style = Cine.small, maxLines = 1)
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Row {
                                        SmallBtn(stringResource(R.string.watch_now)) { scope.launch { openLive(ch) } }
                                        Spacer(Modifier.width(8.dp))
                                        var ll by remember(ch.id) { mutableStateOf(false) }
                                        SmallBtn(stringResource(R.string.favorite), ghost = true) {
                                            scope.launch {
                                                ll = Library.toggleFav(ctx, accId, FavItem("live", ch.id, ch.name, ch.logo, ch.toJson().toString()))
                                                Ui.toast(ctx, ctx.getString(if (ll) R.string.added_fav else R.string.removed_fav))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (vodMode) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(160.dp),
                            modifier = Modifier.weight(3f)
                        ) {
                            items(shownVod, key = { "m:${it.id}" }) { v ->
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    PosterCard(
                                        v.poster, v.name,
                                        listOf(v.year, Ui.fmtRating(v.rating).let { if (it.isNotEmpty()) "★ $it" else "" })
                                            .filter { it.isNotEmpty() }.joinToString(" · "),
                                        badge = badgeForVod(v).ifEmpty { null },
                                        onClick = {
                                            gate.run(accId, "movie", v.id, v.name) {
                                                nav.open(Screen.Detail("movie", v.toJson().toString()))
                                            }
                                        },
                                        onLong = {
                                            Store.toggleLock(accId, "movie", v.id)
                                            lockTick++
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(160.dp),
                            modifier = Modifier.weight(3f)
                        ) {
                            items(shownSeries, key = { "s:${it.id}" }) { s ->
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    PosterCard(
                                        s.poster, s.name,
                                        listOf(s.year, Ui.fmtRating(s.rating).let { if (it.isNotEmpty()) "★ $it" else "" })
                                            .filter { it.isNotEmpty() }.joinToString(" · "),
                                        badge = badgeForSeries(s).ifEmpty { null },
                                        onClick = {
                                            gate.run(accId, "series", s.id, s.name) {
                                                nav.open(Screen.Detail("series", s.toJson().toString()))
                                            }
                                        },
                                        onLong = {
                                            Store.toggleLock(accId, "series", s.id)
                                            lockTick++
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        GateDialog(gate)
    }
}
