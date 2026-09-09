package com.rgbtv.app.compose

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.rgbtv.app.ui.Kit
import com.rgbtv.app.R
import com.rgbtv.app.data.LiveCh
import com.rgbtv.app.data.Store
import com.rgbtv.app.net.Net
import com.rgbtv.app.repo.Repository
import com.rgbtv.app.ui.PlayerActivity
import com.rgbtv.app.ui.Ui
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class GEv(val start: Long, val end: Long, val title: String)
private data class GRow(val ch: LiveCh, val evs: List<GEv>)

@Composable
fun GuideScreen(nav: Navigator) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val gate = rememberGate()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var reload by remember { mutableStateOf(0) }
    var accId by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf(listOf<GRow>()) }
    var chanIdx by remember { mutableStateOf(0) }
    var evIdx by remember { mutableStateOf(0) }
    var clockTxt by remember { mutableStateOf("") }
    val nowSec = System.currentTimeMillis() / 1000
    val tf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    LaunchedEffect(Unit) {
        while (true) {
            clockTxt = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            delay(30000)
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
            val chans = Repository.provider?.live(null).orEmpty()
            rows = chans.map { GRow(it, emptyList()) }
            loading = false
            chans.forEachIndexed { idx, ch ->
                val list = try { Repository.epgFor(ctx, ch, 1) } catch (e: Exception) { emptyList() }
                val evs = list.mapNotNull { p ->
                    var s = p.start
                    var e = p.end
                    if (s > 1000000000000L) s /= 1000
                    if (e > 1000000000000L) e /= 1000
                    if (s <= 0 || e <= s) null
                    else GEv(s, e, p.title.ifEmpty { ch.name })
                }.sortedBy { it.start }
                rows = rows.toMutableList().also { it[idx] = GRow(ch, evs) }
            }
        } catch (e: Exception) {
            loading = false
            error = Net.userMsg(ctx, e)
        }
    }

    suspend fun play(ch: LiveCh) {
        if (Store.isLocked(accId, "live", ch.id) || (Store.settings().parental && Kit.isAdult(ch.name))) {
            val ok = gate.askPin(ctx.getString(R.string.pin_title))
            if (!ok) return
        }
        PlayerActivity.playLive(ctx, ch, rows.map { it.ch })
    }

    Box(Modifier.fillMaxSize().background(Cine.bg)) {
        when {
            loading -> SkeletonGrid()
            error.isNotEmpty() -> ErrorState(error) { reload++ }
            else -> Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.guide), style = Cine.h1)
                    Text(" · ${rows.size}", style = Cine.body)
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .background(Cine.card, RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(clockTxt, style = Cine.h3)
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (rows.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(stringResource(R.string.no_results))
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        itemsIndexed(rows, key = { _, r -> "g:${r.ch.id}" }) { ri, row ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .background(
                                        if (ri == chanIdx) Cine.card else Color.Transparent,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .border(
                                        if (ri == chanIdx) 2.dp else 0.dp,
                                        if (ri == chanIdx) Cine.accent else Color.Transparent,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    Modifier.width(190.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = row.ch.logo.ifEmpty { null },
                                        contentDescription = null,
                                        placeholder = painterResource(R.drawable.img_ph),
                                        error = painterResource(R.drawable.img_ph),
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .width(52.dp)
                                            .height(36.dp)
                                            .background(Cine.card2, RoundedCornerShape(6.dp))
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(row.ch.name, style = Cine.small, maxLines = 2, modifier = Modifier.weight(1f))
                                }
                                LazyRow(Modifier.weight(1f)) {
                                    itemsIndexed(row.evs, key = { ei, e -> "ge:${row.ch.id}:$ei:${e.start}" }) { ei, e ->
                                        val live = nowSec in e.start until e.end
                                        val durMin = ((e.end - e.start) / 60).toInt().coerceAtLeast(15)
                                        val w = (durMin * 5).coerceAtMost(600).dp
                                        Box(
                                            Modifier
                                                .width(w)
                                                .height(56.dp)
                                                .padding(2.dp)
                                                .tvFocus(radius = 8)
                                                .background(
                                                    if (live) Cine.accentDark else Cine.card2,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .noRippleClickable { scope.launch { play(row.ch) } }
                                                .onFocusChanged { if (it.isFocused) { chanIdx = ri; evIdx = ei } }
                                                .padding(6.dp)
                                        ) {
                                            Column {
                                                Text(
                                                    (if (live) "● " else "") + e.title,
                                                    style = if (live) Cine.h3 else Cine.small,
                                                    maxLines = 1
                                                )
                                                Text(
                                                    tf.format(Date(e.start * 1000)) + " - " + tf.format(Date(e.end * 1000)),
                                                    style = Cine.tiny
                                                )
                                            }
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
        BackHandler { nav.back() }
    }
}
