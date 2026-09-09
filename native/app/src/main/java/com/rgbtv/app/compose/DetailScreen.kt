package com.rgbtv.app.compose

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.rgbtv.app.R
import com.rgbtv.app.data.EpisodeItem
import com.rgbtv.app.data.FavItem
import com.rgbtv.app.data.SeasonItem
import com.rgbtv.app.data.SeriesDetail
import com.rgbtv.app.data.SeriesItem
import com.rgbtv.app.data.Store
import com.rgbtv.app.data.VodDetail
import com.rgbtv.app.data.VodItem
import com.rgbtv.app.net.Net
import com.rgbtv.app.repo.Library
import com.rgbtv.app.repo.Repository
import com.rgbtv.app.ui.Ui
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URLEncoder

private fun openTrailer(ctx: android.content.Context, name: String, year: String) {
    try {
        val q = URLEncoder.encode("$name $year trailer", "UTF-8")
        ctx.startActivity(Intent(Intent.ACTION_VIEW, "https://www.youtube.com/results?search_query=$q".toUri()))
    } catch (e: Exception) {
        Ui.toast(ctx, ctx.getString(R.string.err_unknown))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(nav: Navigator, type: String, data: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val gate = rememberGate()
    val isSeries = type == "series"
    val vod = remember(data) {
        if (!isSeries) try { VodItem.fromJson(JSONObject(data)) } catch (e: Exception) { null } else null
    }
    val ser = remember(data) {
        if (isSeries) try { SeriesItem.fromJson(JSONObject(data)) } catch (e: Exception) { null } else null
    }
    if (vod == null && ser == null) {
        ErrorState(stringResource(R.string.err_unknown)) { nav.back() }
        return
    }
    val itemId = if (isSeries) ser!!.id else vod!!.id
    val itemName = if (isSeries) ser!!.name else vod!!.name
    val poster = if (isSeries) ser!!.poster else vod!!.poster
    val plot = if (isSeries) ser!!.plot else vod!!.plot
    val year = if (isSeries) ser!!.year else vod!!.year
    val genre = if (isSeries) ser!!.genre else vod!!.genre
    val rating = if (isSeries) ser!!.rating else vod!!.rating

    var accId by remember { mutableStateOf("") }
    var epsLoading by remember { mutableStateOf(isSeries) }
    var serD by remember { mutableStateOf<SeriesDetail?>(null) }
    var vodD by remember { mutableStateOf<VodDetail?>(null) }
    var selSea by remember { mutableStateOf(0) }
    var selEp by remember { mutableStateOf(0) }
    var isFav by remember { mutableStateOf(false) }
    var resumeMs by remember { mutableStateOf(0L) }
    var epPct by remember { mutableStateOf(mapOf<String, Int>()) }
    var related by remember { mutableStateOf(listOf<VodItem>()) }

    val seasons: List<SeasonItem> = serD?.seasons.orEmpty()
    val seasonEps: List<EpisodeItem> = seasons.getOrNull(selSea)?.episodes.orEmpty()
    val bgImg = if (isSeries) serD?.backdrop?.ifEmpty { ser!!.backdrop.ifEmpty { poster } }
    else vodD?.backdrop?.ifEmpty { poster }
    val metaStr = listOf(
        year,
        Ui.fmtRating(rating).let { if (it.isNotEmpty()) "★ $it" else "" },
        genre
    ).filter { it.isNotEmpty() }.joinToString(" · ")
    val extraStr = if (!isSeries) {
        listOf(
            vodD?.duration.orEmpty(),
            vodD?.director?.let { if (it.isNotEmpty()) "🎬 $it" else "" }.orEmpty(),
            vodD?.cast?.let { if (it.isNotEmpty()) "👥 $it" else "" }.orEmpty()
        ).filter { it.isNotEmpty() }.joinToString(" · ")
    } else ""

    LaunchedEffect(data) {
        val a = Store.getAccount(Store.lastAccount()) ?: Store.accounts().firstOrNull()
        if (a == null) {
            nav.nav(Screen.Profiles)
            return@LaunchedEffect
        }
        accId = a.id
        isFav = Library.isFav(ctx, a.id, if (isSeries) "series" else "movie", itemId)
        try {
            if (Repository.provider == null) Repository.sessionFor(a)
            if (!isSeries) {
                resumeMs = Library.getPos(ctx, a.id, "movie:$itemId")?.pos ?: 0L
                vodD = try { Repository.provider?.vodInfo(vod!!) } catch (e: Exception) { null }
                val all = Repository.provider?.vod(null).orEmpty()
                related = all.filter { it.id != itemId && (it.catId == vod!!.catId || it.genre == vod.genre) }.take(12)
            } else {
                val d = Repository.provider?.seriesInfo(ser!!)
                serD = d
                val first = d?.seasons?.firstOrNull()?.episodes.orEmpty()
                selEp = first.indexOfFirst { !Store.isDone(a.id, "ep:${it.id}") }.takeIf { it >= 0 } ?: 0
            }
        } catch (e: Exception) {
            if (isSeries) Ui.toast(ctx, Net.userMsg(ctx, e))
        }
        epsLoading = false
    }

    LaunchedEffect(seasonEps, accId) {
        if (accId.isEmpty() || seasonEps.isEmpty()) return@LaunchedEffect
        val m = mutableMapOf<String, Int>()
        for (e in seasonEps) {
            val p = Library.getPos(ctx, accId, "ep:${e.id}")
            if (p != null && p.dur > 0) m[e.id] = (p.pos * 100 / p.dur).toInt()
        }
        epPct = m
    }

    fun toggleFav() {
        scope.launch {
            val item = if (isSeries) FavItem("series", itemId, itemName, poster, ser!!.toJson().toString())
            else FavItem("movie", itemId, itemName, poster, vod!!.toJson().toString())
            isFav = Library.toggleFav(ctx, accId, item)
            Ui.toast(ctx, ctx.getString(if (isFav) R.string.added_fav else R.string.removed_fav))
        }
    }

    Box(Modifier.fillMaxSize().background(Cine.bg)) {
        AsyncImage(
            model = bgImg.ifEmpty { null },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(0.25f)
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Row(Modifier.fillMaxWidth()) {
                AsyncImage(
                    model = poster.ifEmpty { null },
                    contentDescription = null,
                    placeholder = painterResource(R.drawable.img_ph),
                    error = painterResource(R.drawable.img_ph),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(200.dp)
                        .height(300.dp)
                        .background(Cine.card2, RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        (if (isSeries) stringResource(R.string.series) else stringResource(R.string.movies))
                            .uppercase(),
                        style = Cine.cat
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(itemName, style = Cine.h1)
                    if (metaStr.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(metaStr, style = Cine.body)
                    }
                    if (extraStr.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text(extraStr, style = Cine.small, maxLines = 2)
                    }
                    if (plot.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(plot, style = Cine.small, maxLines = 6)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isSeries) {
                            TvButton(
                                if (resumeMs > 10000) "▶ ${stringResource(R.string.resume)}" else "▶ ${stringResource(R.string.watch_now)}"
                            ) { scope.goVod(ctx, gate, accId, vod!!, resumeMs) }
                            Spacer(Modifier.width(8.dp))
                        } else if (seasonEps.isNotEmpty()) {
                            TvButton("▶ ${seasonEps.getOrNull(selEp)?.name?.ifEmpty { stringResource(R.string.watch_now) }}") {
                                scope.goEp(ctx, gate, accId, itemName, seasonEps, selEp)
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        TvButton("🎬 Trailer", ghost = true) { openTrailer(ctx, itemName, year) }
                        Spacer(Modifier.width(8.dp))
                        TvButton((if (isFav) "★ " else "＋ ") + stringResource(R.string.my_list), ghost = true) { toggleFav() }
                    }
                }
            }
            if (isSeries) {
                Spacer(Modifier.height(16.dp))
                if (epsLoading) {
                    SkeletonRow()
                } else if (seasons.isEmpty()) {
                    EmptyState(stringResource(R.string.no_results))
                } else {
                    LazyRow {
                        items(seasons.size) { i ->
                            Chip(seasons[i].name.ifEmpty { "S${seasons[i].num}" }, selSea == i) {
                                selSea = i
                                selEp = 0
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    FlowRow {
                        seasonEps.forEachIndexed { i, e ->
                            EpCard(
                                e.thumb.ifEmpty { null },
                                e.name.ifEmpty { "E${e.episode}" },
                                e.duration.ifEmpty { "S${e.season} E${e.episode}" },
                                selEp == i, epPct[e.id] ?: -1,
                                onClick = {
                                    selEp = i
                                    scope.goEp(ctx, gate, accId, itemName, seasonEps, i)
                                }
                            )
                        }
                    }
                }
            }
            if (!isSeries && related.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.related), style = Cine.h2)
                Spacer(Modifier.height(8.dp))
                LazyRow {
                    items(related, key = { "rel:${it.id}" }) { v ->
                        PosterCard(v.poster, v.name, v.year, onClick = {
                            nav.open(Screen.Detail("movie", v.toJson().toString()))
                        })
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
        GateDialog(gate)
        BackHandler { nav.back() }
    }
}
