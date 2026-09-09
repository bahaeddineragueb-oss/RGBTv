package com.rgbtv.app.compose

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rgbtv.app.R
import com.rgbtv.app.data.Account
import com.rgbtv.app.data.Store
import com.rgbtv.app.repo.Repository
import com.rgbtv.app.ui.Ui
import kotlinx.coroutines.launch

@Composable
fun ProfilesScreen(nav: Navigator) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var accounts by remember { mutableStateOf(listOf<Account>()) }
    var lastId by remember { mutableStateOf("") }
    var subMap by remember { mutableStateOf(mapOf<String, String>()) }

    LaunchedEffect(Unit) {
        accounts = Store.accounts()
        lastId = Store.lastAccount() ?: ""
        val m = mutableMapOf<String, String>()
        for (a in accounts) {
            Store.lastSession(a.id)?.let { s ->
                val exp = s.expires?.let { Ui.dateFull(it) } ?: ctx.getString(R.string.unlimited)
                m[a.id] = "${s.status} · $exp"
            }
        }
        subMap = m
    }

    fun pick(a: Account) {
        scope.launch {
            Store.setLastAccount(a.id)
            Repository.logout()
            Ui.toast(ctx, a.name)
            nav.nav(Screen.Home)
        }
    }

    Box(Modifier.fillMaxSize().background(Cine.bg)) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Text(stringResource(R.string.profiles_title), style = Cine.h1)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.profiles_sub), style = Cine.body)
            Spacer(Modifier.height(16.dp))
            if (accounts.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        EmptyState(stringResource(R.string.no_accounts))
                        Spacer(Modifier.height(12.dp))
                        TvButton("＋ ${stringResource(R.string.add_account)}") {
                            nav.open(Screen.AccountEdit(""))
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(280.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(accounts, key = { "p:${it.id}" }) { a ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(6.dp)
                                .tvFocus(radius = 14)
                                .background(
                                    if (a.id == lastId) Cine.card2 else Cine.card,
                                    RoundedCornerShape(14.dp)
                                )
                                .noRippleClickable { pick(a) }
                                .padding(18.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier
                                        .width(64.dp)
                                        .height(64.dp)
                                        .background(Cine.accentDark, RoundedCornerShape(32.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        a.name.firstOrNull()?.uppercase() ?: "?",
                                        style = Cine.display
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(a.name.ifEmpty { a.url }, style = Cine.h2, maxLines = 1, textAlign = TextAlign.Center)
                                Text(
                                    subMap[a.id] ?: a.url,
                                    style = Cine.small, maxLines = 1, textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(10.dp))
                                Row {
                                    SmallBtn(stringResource(R.string.open)) { pick(a) }
                                    Spacer(Modifier.width(8.dp))
                                    SmallBtn(stringResource(R.string.edit), ghost = true) {
                                        nav.open(Screen.AccountEdit(a.id))
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(6.dp)
                                .tvFocus(radius = 14)
                                .background(Cine.card, RoundedCornerShape(14.dp))
                                .noRippleClickable { nav.open(Screen.AccountEdit("")) }
                                .padding(18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("＋", style = Cine.display.copy(color = Cine.accent))
                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(R.string.add_account), style = Cine.h3)
                            }
                        }
                    }
                }
            }
        }
        BackHandler {
            if (accounts.isNotEmpty() && lastId.isNotEmpty()) nav.back()
            else Ui.toast(ctx, ctx.getString(R.string.pick_account_first))
        }
    }
}
