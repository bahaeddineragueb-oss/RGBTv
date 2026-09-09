package com.rgbtv.app.compose

import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
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
import androidx.core.net.toUri
import com.rgbtv.app.R
import com.rgbtv.app.data.CloudStore
import com.rgbtv.app.net.CloudApi
import com.rgbtv.app.repo.Sync
import com.rgbtv.app.ui.Ui
import kotlinx.coroutines.launch

@Composable
private fun CloudField(label: String, value: String, onValue: (String) -> Unit) {
    Text(label, style = Cine.small)
    Spacer(Modifier.height(2.dp))
    TextField(
        value = value,
        onValueChange = onValue,
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
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
}

@Composable
fun CloudScreen(nav: Navigator) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var ref by remember { mutableStateOf(0) }
    var isRegister by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf(CloudStore.url()) }
    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }

    fun submit() {
        if (url.trim().isEmpty() || email.trim().isEmpty() || pass.isEmpty() ||
            (isRegister && name.trim().isEmpty())
        ) {
            err = ctx.getString(R.string.fill_fields)
            return
        }
        busy = true
        err = ""
        scope.launch {
            try {
                CloudStore.setUrl(url.trim())
                val r = if (isRegister) CloudApi.register(email.trim(), pass, name.trim())
                else CloudApi.login(email.trim(), pass)
                val u = r.json?.let { CloudApi.parseUser(it) }
                if (r.ok && u != null) {
                    CloudStore.login(url.trim(), u.token, u.email, u.admin)
                    Sync.pushProfiles(ctx)
                    val added = Sync.pullProfiles(ctx)
                    Ui.toast(
                        ctx,
                        ctx.getString(R.string.synced) +
                            if (added > 0) " · " + ctx.getString(R.string.pulled_n, added) else ""
                    )
                    ref++
                } else {
                    err = r.err ?: ctx.getString(R.string.sync_failed)
                }
            } catch (e: Exception) {
                err = e.message ?: ctx.getString(R.string.sync_failed)
            }
            busy = false
        }
    }

    ref.let { }
    Box(
        Modifier
            .fillMaxSize()
            .background(Cine.bg),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            Modifier
                .width(520.dp)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text(stringResource(R.string.cloud_title), style = Cine.h1)
            Spacer(Modifier.height(12.dp))
            if (!CloudStore.loggedIn) {
                CloudField(stringResource(R.string.server_url), url) { url = it }
                CloudField(stringResource(R.string.email), email) { email = it }
                if (isRegister) {
                    CloudField(stringResource(R.string.acc_name), name) { name = it }
                }
                CloudField(stringResource(R.string.password), pass) { pass = it }
                if (err.isNotEmpty()) {
                    Text(err, style = Cine.small.copy(color = Cine.red))
                    Spacer(Modifier.height(6.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TvButton(
                        if (busy) "…" else if (isRegister) stringResource(R.string.register)
                        else "☁ ${stringResource(R.string.login)}"
                    ) { if (!busy) submit() }
                    Spacer(Modifier.width(8.dp))
                    SmallBtn(
                        stringResource(if (isRegister) R.string.have_account else R.string.need_account),
                        ghost = true
                    ) { isRegister = !isRegister }
                }
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Cine.card, RoundedCornerShape(14.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Text(CloudStore.email(), style = Cine.h2)
                        Spacer(Modifier.height(2.dp))
                        Text(CloudStore.url(), style = Cine.small)
                        if (CloudStore.isAdmin()) {
                            Spacer(Modifier.height(2.dp))
                            Text("★ ${stringResource(R.string.admin)}", style = Cine.cat)
                        }
                        val ls = CloudStore.lastSync()
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${stringResource(R.string.last_sync)}: ${if (ls > 0) Ui.dateFull(ls) else "—"}",
                            style = Cine.small
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    SmallBtn(stringResource(R.string.sync_now)) {
                        scope.launch {
                            val ok = Sync.pushNow(ctx)
                            Sync.pullNow(ctx)
                            Ui.toast(ctx, ctx.getString(if (ok) R.string.synced else R.string.sync_failed))
                            ref++
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    SmallBtn(stringResource(R.string.profiles_push), ghost = true) {
                        Sync.pushProfiles(ctx)
                        Ui.toast(ctx, ctx.getString(R.string.synced))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    SmallBtn(stringResource(R.string.profiles_pull), ghost = true) {
                        scope.launch {
                            val n = Sync.pullProfiles(ctx)
                            Ui.toast(ctx, ctx.getString(R.string.pulled_n, n))
                        }
                    }
                    if (CloudStore.isAdmin()) {
                        Spacer(Modifier.width(8.dp))
                        SmallBtn(stringResource(R.string.open_admin), ghost = true) {
                            try {
                                ctx.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        (CloudStore.url() + "/admin").toUri()
                                    )
                                )
                            } catch (e: Exception) {
                                Ui.toast(ctx, ctx.getString(R.string.err_unknown))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                SmallBtn(stringResource(R.string.logout), ghost = true) {
                    CloudStore.logout()
                    ref++
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
