package com.rgbtv.app.compose

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.rgbtv.app.R

/* ---------- poster card (2:3) ---------- */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosterCard(
    img: String?,
    title: String,
    sub: String = "",
    progress: Int = -1,
    badge: String? = null,
    onClick: () -> Unit,
    onLong: () -> Unit = {},
    onFocus: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .width(148.dp)
            .tvFocus(onFocus = { if (it) onFocus() })
            .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick, onLongClick = onLong)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(Cine.card)
        ) {
            AsyncImage(
                model = img,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.img_ph),
                error = painterResource(R.drawable.img_ph)
            )
            if (!badge.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(Cine.live, RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(badge, style = Cine.cap.copy(color = Cine.text))
                }
            }
            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(4.dp),
                    color = Cine.blue,
                    trackColor = Cine.line
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(title, style = Cine.h3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sub.isNotEmpty()) {
            Text(sub, style = Cine.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/* ---------- channel card (16:9) ---------- */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelCard(
    logo: String?,
    name: String,
    num: String = "",
    badge: String? = null,
    onClick: () -> Unit,
    onLong: () -> Unit = {},
    onFocus: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .width(168.dp)
            .tvFocus(onFocus = { if (it) onFocus() })
            .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick, onLongClick = onLong)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(Cine.card)
        ) {
            AsyncImage(
                model = logo,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                contentScale = ContentScale.Fit,
                placeholder = painterResource(R.drawable.img_ph),
                error = painterResource(R.drawable.img_ph)
            )
            if (!badge.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(Cine.live, RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(badge, style = Cine.cap.copy(color = Cine.text))
                }
            }
            if (num.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Cine.card2, RoundedCornerShape(20.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(num, style = Cine.small.copy(color = Cine.text))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            name, style = Cine.body, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(40.dp)
        )
    }
}

/* ---------- horizontal rail ---------- */
@Composable
fun <T> Rail(
    title: String,
    items: List<T>,
    keyOf: (T) -> Any,
    card: @Composable (T) -> Unit
) {
    if (items.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = Cine.h2, modifier = Modifier.padding(start = 20.dp, top = 12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(items, key = { _, t -> keyOf(t) }) { _, t -> card(t) }
        }
    }
}

/* ---------- hero banner ---------- */
@Composable
fun HeroBanner(
    bg: String,
    cat: String,
    title: String,
    meta: String,
    plot: String,
    watchLabel: String,
    favLabel: String,
    onWatch: () -> Unit,
    onFav: () -> Unit,
    watchMod: Modifier = Modifier
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(300.dp)
            .tvFocus(radius = 16, scale = 1.01f)
            .noRippleClickable(onWatch)
    ) {
        AsyncImage(
            model = bg,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(R.drawable.hero_fallback),
            error = painterResource(R.drawable.hero_fallback)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        0f to androidx.compose.ui.graphics.Color.Transparent,
                        0.55f to Cine.bg.copy(alpha = 0.6f),
                        1f to Cine.bg.copy(alpha = 0.92f)
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp)
        ) {
            Text(cat, style = Cine.section)
            Spacer(Modifier.height(4.dp))
            Text(title, style = Cine.display, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(meta, style = Cine.body.copy(color = Cine.sub, fontWeight = FontWeight.Bold))
            }
            if (plot.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(plot, style = Cine.body.copy(color = Cine.sub), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(12.dp))
            Row {
                TvButton(label = watchLabel, modifier = watchMod, onClick = onWatch)
                Spacer(Modifier.width(8.dp))
                TvButton(label = favLabel, ghost = true, onClick = onFav)
            }
        }
    }
}

/* ---------- chips ---------- */
@Composable
fun ChipRow(
    options: List<String>,
    selected: Int,
    onPick: (Int) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(options) { i, t ->
            val sel = i == selected
            Box(
                modifier = Modifier
                    .tvFocus(radius = 10)
                    .background(if (sel) Cine.card2 else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(10.dp))
                    .noRippleClickable { onPick(i) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(t, style = Cine.h3.copy(color = if (sel) Cine.text else Cine.sub))
            }
        }
    }
}

/* ---------- skeleton ---------- */
@Composable
fun SkeletonHome() {
    val t = rememberInfiniteTransition(label = "sk")
    val a by t.animateFloat(0.45f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "sk2")
    Column(modifier = Modifier.padding(20.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(300.dp)
                .alpha(a)
                .background(Cine.card, RoundedCornerShape(16.dp))
        )
        Spacer(Modifier.height(16.dp))
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(4) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(150.dp)
                            .alpha(a)
                            .background(Cine.card, RoundedCornerShape(12.dp))
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/* ---------- states ---------- */
@Composable
fun EmptyState(text: String, sub: String = "") {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text, style = Cine.h2.copy(color = Cine.sub))
        if (sub.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(sub, style = Cine.small)
        }
    }
}

@Composable
fun ErrorState(text: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("⚠", style = Cine.display)
        Spacer(Modifier.height(8.dp))
        Text(text, style = Cine.body)
        Spacer(Modifier.height(14.dp))
        TvButton(label = stringResource(R.string.retry), onClick = onRetry)
    }
}

/* ---------- single chip ---------- */
@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(end = 8.dp, bottom = 4.dp)
            .tvFocus(radius = 20)
            .background(if (selected) Cine.blue else Cine.card2, RoundedCornerShape(20.dp))
            .noRippleClickable(onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = Cine.h3.copy(color = if (selected) Cine.text else Cine.sub), maxLines = 1)
    }
}

/* ---------- small button ---------- */
@Composable
fun SmallBtn(label: String, ghost: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .tvFocus(radius = 10)
            .background(if (ghost) Cine.card2 else Cine.blue, RoundedCornerShape(10.dp))
            .noRippleClickable(onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = Cine.h3.copy(color = Cine.text), maxLines = 1)
    }
}

/* ---------- resume progress line ---------- */
@Composable
fun ProgressLine(pct: Int) {
    LinearProgressIndicator(
        progress = { pct.coerceIn(0, 100) / 100f },
        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
        color = Cine.blue,
        trackColor = Cine.line
    )
}

/* ---------- featured strip (browse) ---------- */
@Composable
fun FeaturedStrip(
    poster: String,
    cat: String,
    title: String,
    meta: String,
    plot: String,
    playLabel: String,
    detailsLabel: String,
    onPlay: () -> Unit,
    onDetails: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Cine.card, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = poster.ifEmpty { null },
            contentDescription = null,
            modifier = Modifier
                .width(86.dp)
                .height(126.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Cine.card2),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(R.drawable.img_ph),
            error = painterResource(R.drawable.img_ph)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (cat.isNotEmpty()) Text(cat, style = Cine.section)
            Text(title, style = Cine.h1, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (meta.isNotEmpty()) Text(meta, style = Cine.small, maxLines = 1)
            if (plot.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(plot, style = Cine.small, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column {
            TvButton(label = playLabel, onClick = onPlay)
            Spacer(Modifier.height(8.dp))
            TvButton(label = detailsLabel, ghost = true, onClick = onDetails)
        }
    }
}

/* ---------- episode card ---------- */
@Composable
fun EpCard(
    img: String?,
    name: String,
    meta: String,
    selected: Boolean = false,
    pct: Int = -1,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(220.dp)
            .padding(end = 10.dp, bottom = 10.dp)
            .tvFocus(radius = 12)
            .background(if (selected) Cine.card2 else Cine.card, RoundedCornerShape(12.dp))
            .noRippleClickable(onClick)
            .padding(10.dp)
    ) {
        AsyncImage(
            model = img,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Cine.card2),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(R.drawable.img_ph),
            error = painterResource(R.drawable.img_ph)
        )
        Spacer(Modifier.height(6.dp))
        Text(name, style = Cine.h3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(meta, style = Cine.tiny, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (pct in 0..100) {
            Spacer(Modifier.height(4.dp))
            ProgressLine(pct)
        }
    }
}

/* ---------- skeleton grid + row ---------- */
@Composable
fun SkeletonGrid() {
    val t = rememberInfiniteTransition(label = "skg")
    val a by t.animateFloat(0.45f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "skg2")
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Box(Modifier.width(220.dp).height(28.dp).alpha(a).background(Cine.card, RoundedCornerShape(8.dp)))
        Spacer(Modifier.height(12.dp))
        repeat(3) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(4) {
                    Box(
                        Modifier.weight(1f).height(150.dp).alpha(a)
                            .background(Cine.card, RoundedCornerShape(12.dp))
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun SkeletonRow() {
    val t = rememberInfiniteTransition(label = "skr")
    val a by t.animateFloat(0.45f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "skr2")
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(4) {
            Box(
                Modifier.weight(1f).height(150.dp).alpha(a)
                    .background(Cine.card, RoundedCornerShape(12.dp))
            )
        }
    }
}
