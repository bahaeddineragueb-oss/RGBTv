package com.rgbtv.app.compose

import androidx.appcompat.app.AlertDialog
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.rgbtv.app.data.Account
import com.rgbtv.app.data.DiskCache
import com.rgbtv.app.data.Store
import com.rgbtv.app.net.ProviderFactory
import com.rgbtv.app.repo.Library
import com.rgbtv.app.repo.Repository
import com.rgbtv.app.ui.Kit
import com.rgbtv.app.ui.Ui
import kotlinx.coroutines.launch

@Composable
private fun AccField(label: String, value: String, onValue: (String) -> Unit) {
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
fun AccountEditScreen(nav: Navigator, id: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val isNew = id.isEmpty()
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("xtream") }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var mac by remember { mutableStateOf("") }
    var epgUrl by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(isNew) }
    val types = listOf("xtream", "m3u", "stalker")

    LaunchedEffect(id) {
        if (!isNew) {
            Store.getAccount(id)?.let { a ->
                name = a.name
                type = a.type.ifEmpty { "xtream" }
                url = a.url
                username = a.username
                password = a.password
                mac = a.mac
                epgUrl = a.epgUrl
            }
            loaded = true
        }
    }

    fun build(): Account? {
        if (url.trim().isEmpty()) return null
        if (type == "xtream" && (username.trim().isEmpty() || password.isEmpty())) return null
        if (type == "stalker" && mac.trim().isEmpty()) return null
        return Account(
            id = if (isNew) Kit.uuid() else id,
            name = name.trim().ifEmpty { url.trim() },
            type = type, url = url.trim(), username = username.trim(), password = password,
            mac = mac.trim(), epgUrl = epgUrl.trim(),
            createdAt = Store.getAccount(id)?.createdAt ?: System.currentTimeMillis()
        )
    }

    fun test() {
        val a = build()
        if (a == null) {
            Ui.toast(ctx, ctx.getString(R.string.fill_fields))
            return
        }
        testing = true
        scope.launch {
            try {
                val p = ProviderFactory.create(a)
                try { p.login() } finally { try { p.close() } catch (e: Exception) { } }
                Ui.toast(ctx, ctx.getString(R.string.test_ok))
            } catch (e: Exception) {
                Ui.toast(ctx, ctx.getString(R.string.test_fail))
            }
            testing = false
        }
    }

    fun save() {
        val a = build()
        if (a == null) {
            Ui.toast(ctx, ctx.getString(R.string.fill_fields))
            return
        }
        scope.launch {
            if (isNew) {
                Store.addAccount(a)
                Store.setLastAccount(a.id)
            } else {
                Store.updateAccount(a)
                DiskCache.clearAccount(a.id)
                Repository.dropXmltv(a.id)
            }
            Repository.logout()
            Ui.toast(ctx, ctx.getString(R.string.done))
            nav.back()
        }
    }

    fun delete() {
        AlertDialog.Builder(ctx)
            .setMessage(ctx.getString(R.string.confirm_delete))
            .setPositiveButton(ctx.getString(R.string.yes)) { _, _ ->
                scope.launch {
                    Store.removeAccount(id)
                    DiskCache.clearAccount(id)
                    Repository.dropXmltv(id)
                    try { ctx.deleteDatabase("epg_$id.db") } catch (e: Exception) { }
                    Library.clearAccount(ctx, id)
                    Repository.logout()
                    nav.nav(Screen.Profiles)
                }
            }
            .setNegativeButton(ctx.getString(R.string.no), null)
            .show()
    }

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
            Text(
                if (isNew) stringResource(R.string.add_account) else stringResource(R.string.edit),
                style = Cine.h1
            )
            Spacer(Modifier.height(12.dp))
            if (!loaded) {
                SkeletonRow()
            } else {
                AccField(stringResource(R.string.acc_name), name) { name = it }
                Text(stringResource(R.string.acc_type), style = Cine.small)
                Spacer(Modifier.height(4.dp))
                LazyRow {
                    items(types) { t ->
                        Chip(t.replaceFirstChar { it.uppercase() }, type == t) { type = t }
                    }
                }
                Spacer(Modifier.height(10.dp))
                AccField(stringResource(R.string.acc_url), url) { url = it }
                if (type == "xtream") {
                    AccField(stringResource(R.string.acc_user), username) { username = it }
                    AccField(stringResource(R.string.acc_pass), password) { password = it }
                }
                if (type == "stalker") {
                    AccField(stringResource(R.string.acc_mac), mac) { mac = it }
                }
                AccField(stringResource(R.string.acc_epg), epgUrl) { epgUrl = it }
                Spacer(Modifier.height(6.dp))
                Row {
                    TvButton(
                        if (testing) "…" else "✓ ${stringResource(R.string.test)}",
                        ghost = true
                    ) { if (!testing) test() }
                    Spacer(Modifier.width(8.dp))
                    TvButton("💾 ${stringResource(R.string.save)}") { save() }
                    if (!isNew) {
                        Spacer(Modifier.width(8.dp))
                        TvButton("🗑 ${stringResource(R.string.delete)}", ghost = true) { delete() }
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}
