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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.rgbtv.app.R
import com.rgbtv.app.data.FavItem
import com.rgbtv.app.data.LiveCh
import com.rgbtv.app.data.Store
import com.rgbtv.app.net.Net
import com.rgbtv.app.repo.Library
import com.rgbtv.app.repo.Repository
import com.rgbtv.app.ui.PlayerActivity
import com.rgbtv.app.ui.Ui
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SPORT_KEYS = listOf(
    "sport", "bein", "sky sport", "espn", "dazn", "canal+ sport", "canal plus",
    "rai sport", "eurosport", "movistar", "super sport",
    "رياض", "الرياض", "الكأس", "الكاس", "ابوظبي الرياضية", "أبوظبي", "دبي الرياضية",
    "الشارقة الرياضية", "عمان الرياضية", "الكويت الرياضية",
    "الجزائرية الرياضية", "المغربية الرياضية", "تونس الرياضية", "ليبيا الرياضية",
    "الأردن الرياضية", "فلسطين الرياضية", "السعودية الرياضية", "قطر",
    "نايل سبورت", "النيل", "أون تايم", "اون تايم", "الزمالك", "الأهلي", "الاهلي"
)

private fun isSportCh(ch: LiveCh): Boolean {
    val n = ch.name.lowercase()
    if (SPORT_KEYS.any { n.contains(it) }) return true
    return false
}

@Composable
fun SportsScreen(nav: Navigator) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val gate = rememberGate()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var reload by remember { mutableStateOf(0) }
    var accId by remember { mutableStateOf("") }
    var all by remember { mutableStateOf(listOf<LiveCh>()) }
    var shown by remember { mutableStateOf(listOf<LiveCh>()) }
    var chip by remember { mutableStateOf(0) }
    var current by remember { mutableStateOf<LiveCh?>(null) }
    var pvInfo by remember { mutableStateOf<Triple<String, String, Int>?>(null) }
    val chips = listOf(
        stringResource(R.string.all), "bein", "Sky", "ESPN",
        stringResource(R.string.arabic), stringResource(R.string.others)
    )

    fun apply() {
        var l = all
        l = when (chip) {
            1 -> l.filter { it.name.lowercase().contains("bein") }
            2 -> l.filter { it.name.lowercase().contains("sky") }
            3 -> l.filter { it.name.lowercase().contains("espn") }
            4 -> l.filter { ch -> SPORT_KEYS.takeLast(26).any { ch.name.lowercase().contains(it) } }
            5 -> l.filter { it.name.lowercase().let { n -> !n.contains("bein") && !n.contains("sky") && !n.contains("espn") } }
            else -> l
        }
        shown = l
        if (current == null || !l.contains(current)) current = l.firstOrNull()
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
            val l = Repository.provider?.live(null).orEmpty().filter { isSportCh(it) }
            all = l
            apply()
            loading = false
        } catch (e: Exception) {
            loading = false
            error = Net.userMsg(ctx, e)
        }
    }

    LaunchedEffect(chip) { apply() }

    LaunchedEffect(current) {
        pvInfo = null
        val ch = current ?: return@LaunchedEffect
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

    suspend fun openLive(ch: LiveCh) {
        gate.run(accId, "live", ch.id, ch.name) {
            PlayerActivity.playLive(ctx, ch, shown)
        }
    }

    Box(Modifier.fillMaxSize().background(Cine.bg)) {
        when {
            loading -> SkeletonGrid()
            error.isNotEmpty() -> ErrorState(error) { reload++ }
            else -> Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.sports), style = Cine.h1)
                    Text(" · ${shown.size}", style = Cine.body)
                }
                Spacer(Modifier.height(8.dp))
                LazyRow {
                    items(chips.size) { i ->
                        Chip(chips[i], chip == i) { chip = i }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.weight(1f)) {
                    if (shown.isEmpty()) {
                        Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                            EmptyState(stringResource(R.string.no_results))
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(180.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(shown, key = { "sp:${it.id}" }) { ch ->
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    ChannelCard(
                                        ch.logo.ifEmpty { null }, ch.name,
                                        if (ch.num > 0) ch.num.toString() else "",
                                        badge = "● ${stringResource(R.string.live_now)}",
                                        onClick = { scope.launch { openLive(ch) } },
                                        onFocus = { current = ch }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.width(290.dp)) {
                        current?.let { ch ->
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
                                    SmallBtn(stringResource(R.string.favorite), ghost = true) {
                                        scope.launch {
                                            val now = Library.toggleFav(ctx, accId, FavItem("live", ch.id, ch.name, ch.logo, ch.toJson().toString()))
                                            Ui.toast(ctx, ctx.getString(if (now) R.string.added_fav else R.string.removed_fav))
                                        }
                                    }
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
