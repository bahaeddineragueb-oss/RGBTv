package com.rgbtv.app.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rgbtv.app.R
import com.rgbtv.app.data.EpisodeItem
import com.rgbtv.app.data.FavItem
import com.rgbtv.app.data.HistItem
import com.rgbtv.app.data.LiveCh
import com.rgbtv.app.data.Store
import com.rgbtv.app.data.VodItem
import com.rgbtv.app.repo.Library
import com.rgbtv.app.repo.Repository
import com.rgbtv.app.ui.PlayerActivity
import com.rgbtv.app.ui.Ui
import kotlinx.coroutines.launch
import org.json.JSONObject

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MyListScreen(nav: Navigator) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val gate = rememberGate()
    var accId by remember { mutableStateOf("") }
    var favs by remember { mutableStateOf(listOf<FavItem>()) }
    var favTab by remember { mutableStateOf(0) }
    var hist by remember { mutableStateOf(listOf<HistItem>()) }
    var pctMap by remember { mutableStateOf(mapOf<String, Int>()) }
    var ready by remember { mutableStateOf(false) }
    val tabs = listOf(
        stringResource(R.string.all), stringResource(R.string.live_tv),
        stringResource(R.string.movies), stringResource(R.string.series)
    )

    suspend fun reload() {
        favs = Library.favs(ctx, accId)
        hist = Library.history(ctx, accId)
        val m = mutableMapOf<String, Int>()
        for (h in hist) {
            val key = when (h.type) {
                "movie" -> "movie:${h.id}"
                "episode" -> "ep:${h.id}"
                else -> ""
            }
            if (key.isNotEmpty()) {
                val p = Library.getPos(ctx, accId, key)
                if (p != null && p.dur > 0) m[h.type + ":" + h.id] = (p.pos * 100 / p.dur).toInt()
            }
        }
        pctMap = m
    }

    LaunchedEffect(Unit) {
        val a = Store.getAccount(Store.lastAccount()) ?: Store.accounts().firstOrNull()
        if (a == null) {
            nav.nav(Screen.Profiles)
            return@LaunchedEffect
        }
        accId = a.id
        reload()
        ready = true
    }

    fun playJson(type: String, data: String) {
        scope.launch {
            if (type == "series") {
                nav.open(Screen.Detail("series", data))
                return@launch
            }
            try {
                val o = JSONObject(data)
                when (type) {
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

    val favsShown = when (favTab) {
        1 -> favs.filter { it.type == "live" }
        2 -> favs.filter { it.type == "movie" }
        3 -> favs.filter { it.type == "series" || it.type == "episode" }
        else -> favs
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Cine.bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(stringResource(R.string.my_list), style = Cine.h1)
        Spacer(Modifier.height(10.dp))
        if (!ready) {
            SkeletonGrid()
        } else {
            Text(stringResource(R.string.favorites), style = Cine.h2)
            Spacer(Modifier.height(6.dp))
            LazyRow {
                items(tabs.size) { i ->
                    Chip(tabs[i], favTab == i) { favTab = i }
                }
            }
            Spacer(Modifier.height(6.dp))
            if (favsShown.isEmpty()) {
                EmptyState(stringResource(R.string.no_results))
            } else {
                FlowRow {
                    favsShown.forEach { f ->
                        PosterCard(
                            f.img, f.name,
                            onClick = { playJson(f.type, f.data) },
                            onLong = {
                                scope.launch {
                                    Library.toggleFav(ctx, accId, f)
                                    favs = Library.favs(ctx, accId)
                                    Ui.toast(ctx, ctx.getString(R.string.removed_fav))
                                }
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.history), style = Cine.h2)
                Spacer(Modifier.weight(1f))
                if (hist.isNotEmpty()) {
                    SmallBtn(stringResource(R.string.clear_history), ghost = true) {
                        scope.launch {
                            Library.clearHistory(ctx, accId)
                            hist = emptyList()
                            Ui.toast(ctx, ctx.getString(R.string.done))
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            if (hist.isEmpty()) {
                EmptyState(stringResource(R.string.no_results))
            } else {
                FlowRow {
                    hist.forEach { h ->
                        PosterCard(
                            h.img, h.name, Ui.dateFull(h.at),
                            pctMap[h.type + ":" + h.id] ?: -1,
                            onClick = { playJson(h.type, h.data) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
        GateDialog(gate)
    }
}
