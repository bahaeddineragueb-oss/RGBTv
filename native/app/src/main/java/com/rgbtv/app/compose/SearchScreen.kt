package com.rgbtv.app.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rgbtv.app.R
import com.rgbtv.app.data.FavItem
import com.rgbtv.app.data.LiveCh
import com.rgbtv.app.data.SeriesItem
import com.rgbtv.app.data.Store
import com.rgbtv.app.data.VodItem
import com.rgbtv.app.net.Net
import com.rgbtv.app.repo.Library
import com.rgbtv.app.repo.Repository
import com.rgbtv.app.ui.Ui
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(nav: Navigator) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val gate = rememberGate()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var reload by remember { mutableStateOf(0) }
    var accId by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var qRun by remember { mutableStateOf("") }
    var allLive by remember { mutableStateOf(listOf<LiveCh>()) }
    var allVod by remember { mutableStateOf(listOf<VodItem>()) }
    var allSeries by remember { mutableStateOf(listOf<SeriesItem>()) }
    var resLive by remember { mutableStateOf(listOf<LiveCh>()) }
    var resVod by remember { mutableStateOf(listOf<VodItem>()) }
    var resSeries by remember { mutableStateOf(listOf<SeriesItem>()) }
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.live_tv), stringResource(R.string.movies), stringResource(R.string.series)
    )

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
            allLive = p?.live(null).orEmpty()
            allVod = p?.vod(null).orEmpty()
            allSeries = p?.series(null).orEmpty()
            loading = false
        } catch (e: Exception) {
            loading = false
            error = Net.userMsg(ctx, e)
        }
    }

    LaunchedEffect(query) {
        delay(400)
        qRun = query.trim()
    }

    LaunchedEffect(qRun) {
        if (qRun.length < 2) {
            resLive = emptyList()
            resVod = emptyList()
            resSeries = emptyList()
            return@LaunchedEffect
        }
        val q = qRun.lowercase()
        resLive = allLive.filter { it.name.lowercase().contains(q) || it.num.toString() == q }
        resVod = allVod.filter {
            it.name.lowercase().contains(q) || it.year == q || it.genre.lowercase().contains(q)
        }
        resSeries = allSeries.filter {
            it.name.lowercase().contains(q) || it.year == q || it.genre.lowercase().contains(q)
        }
    }

    Box(Modifier.fillMaxSize().background(Cine.bg)) {
        when {
            loading -> SkeletonGrid()
            error.isNotEmpty() -> ErrorState(error) { reload++ }
            else -> Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text(stringResource(R.string.search), style = Cine.h1)
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.width(420.dp),
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
                Spacer(Modifier.height(10.dp))
                if (qRun.length >= 2) {
                    val counts = listOf(resLive.size, resVod.size, resSeries.size)
                    LazyRow {
                        items(tabs.size) { i ->
                            Chip("${tabs[i]} (${counts[i]})", tab == i) { tab = i }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    val total = counts.sum()
                    if (total == 0) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            EmptyState(stringResource(R.string.no_results))
                        }
                    } else if (tab == 0) {
                        LazyVerticalGrid(columns = GridCells.Adaptive(180.dp), modifier = Modifier.weight(1f)) {
                            items(resLive, key = { "sl:${it.id}" }) { ch ->
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    ChannelCard(
                                        ch.logo.ifEmpty { null }, ch.name,
                                        if (ch.num > 0) ch.num.toString() else "",
                                        onClick = { scope.goLive(ctx, gate, accId, ch, resLive) }
                                    )
                                }
                            }
                        }
                    } else if (tab == 1) {
                        LazyVerticalGrid(columns = GridCells.Adaptive(160.dp), modifier = Modifier.weight(1f)) {
                            items(resVod, key = { "sm:${it.id}" }) { v ->
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    PosterCard(
                                        v.poster, v.name, v.year,
                                        onClick = {
                                            gate.run(accId, "movie", v.id, v.name) {
                                                nav.open(Screen.Detail("movie", v.toJson().toString()))
                                            }
                                        },
                                        onLong = {
                                            scope.launch {
                                                val now = Library.toggleFav(ctx, accId, FavItem("movie", v.id, v.name, v.poster, v.toJson().toString()))
                                                Ui.toast(ctx, ctx.getString(if (now) R.string.added_fav else R.string.removed_fav))
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        LazyVerticalGrid(columns = GridCells.Adaptive(160.dp), modifier = Modifier.weight(1f)) {
                            items(resSeries, key = { "ss:${it.id}" }) { s ->
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    PosterCard(
                                        s.poster, s.name, s.year,
                                        onClick = {
                                            gate.run(accId, "series", s.id, s.name) {
                                                nav.open(Screen.Detail("series", s.toJson().toString()))
                                            }
                                        },
                                        onLong = {
                                            scope.launch {
                                                val now = Library.toggleFav(ctx, accId, FavItem("series", s.id, s.name, s.poster, s.toJson().toString()))
                                                Ui.toast(ctx, ctx.getString(if (now) R.string.added_fav else R.string.removed_fav))
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(stringResource(R.string.search_hint))
                    }
                }
            }
        }
        GateDialog(gate)
    }
}
