package com.rgbtv.app.compose

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rgbtv.app.R

private data class NavItem(val key: String, val icon: Int, val label: Int, val screen: Screen)

@Composable
fun AppShell(nav: Navigator) {
    var focusedRow by remember { mutableStateOf<String?>(null) }
    val expanded = focusedRow != null
    val sideW by animateDpAsState(
        targetValue = if (expanded) 280.dp else 72.dp,
        animationSpec = tween(220),
        label = "side"
    )
    Row(Modifier.fillMaxSize().background(Cine.bg)) {
        Sidebar(
            width = sideW,
            expanded = expanded,
            selected = nav.sideKey,
            onFocusRow = { focusedRow = it },
            onPick = { nav.nav(it) }
        )
        Box(Modifier.width(1.dp).fillMaxHeight().background(Cine.line))
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (val s = nav.current) {
                is Screen.Home -> HomeScreen(nav)
                is Screen.Browse -> BrowseScreen(nav, s.mode)
                is Screen.Sports -> SportsScreen(nav)
                is Screen.Guide -> GuideScreen(nav)
                is Screen.Search -> SearchScreen(nav)
                is Screen.MyList -> MyListScreen(nav)
                is Screen.Settings -> SettingsScreen(nav)
                is Screen.Profiles -> ProfilesScreen(nav)
                is Screen.Detail -> DetailScreen(nav, s.kind, s.json)
                is Screen.AccountEdit -> AccountEditScreen(nav, s.id.orEmpty())
                is Screen.Cloud -> CloudScreen(nav)
            }
        }
    }
}

@Composable
private fun Sidebar(
    width: androidx.compose.ui.unit.Dp,
    expanded: Boolean,
    selected: String,
    onFocusRow: (String?) -> Unit,
    onPick: (Screen) -> Unit
) {
    val items = listOf(
        NavItem("home", R.drawable.ic_nav_home, R.string.nav_home, Screen.Home),
        NavItem("live", R.drawable.ic_nav_live, R.string.live_tv, Screen.Browse(Screen.LIVE)),
        NavItem("movies", R.drawable.ic_nav_movie, R.string.movies, Screen.Browse(Screen.VOD)),
        NavItem("series", R.drawable.ic_nav_series, R.string.series, Screen.Browse(Screen.SERIES)),
        NavItem("sports", R.drawable.ic_nav_sports, R.string.nav_sports, Screen.Sports),
        NavItem("guide", R.drawable.ic_nav_guide, R.string.guide, Screen.Guide),
        NavItem("mylist", R.drawable.ic_nav_fav, R.string.my_list, Screen.MyList),
        NavItem("search", R.drawable.ic_nav_search, R.string.search, Screen.Search),
        NavItem("settings", R.drawable.ic_nav_settings, R.string.settings, Screen.Settings),
        NavItem("profiles", R.drawable.ic_nav_users, R.string.switch_profile, Screen.Profiles)
    )
    Column(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(Cine.bg2)
            .padding(8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        if (expanded) {
            Text(
                "RGBTv",
                style = Cine.h1.copy(color = Cine.blue),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 8.dp)
            )
        } else {
            Spacer(Modifier.height(8.dp))
        }
        for (it in items) {
            val sel = it.key == selected
            Row(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .tvFocus(
                        radius = 10,
                        onFocus = { f -> onFocusRow(if (f) it.key else null) }
                    )
                    .background(if (sel) Cine.card2 else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(10.dp))
                    .noRippleClickable { onPick(it.screen) }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painterResource(it.icon),
                    contentDescription = null,
                    tint = if (sel) Cine.text else Cine.sub,
                    modifier = Modifier.size(30.dp)
                )
                if (expanded) {
                    Text(
                        stringResource(it.label),
                        style = Cine.h3.copy(color = if (sel) Cine.text else Cine.sub),
                        maxLines = 1,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
            }
        }
    }
}
