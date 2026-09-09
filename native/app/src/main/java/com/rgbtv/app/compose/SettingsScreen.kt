package com.rgbtv.app.compose

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.Composable
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
import coil.Coil
import com.rgbtv.app.App
import com.rgbtv.app.R
import com.rgbtv.app.data.CloudStore
import com.rgbtv.app.data.DiskCache
import com.rgbtv.app.data.Store
import com.rgbtv.app.repo.Repository
import com.rgbtv.app.repo.Sync
import com.rgbtv.app.ui.Ui
import kotlinx.coroutines.launch

@Composable
private fun SetSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, style = Cine.h2)
    Spacer(Modifier.height(6.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .background(Cine.card, RoundedCornerShape(14.dp))
            .padding(6.dp),
        content = content
    )
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun SetRow(label: String, desc: String = "", value: String = "", onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.tvFocus(radius = 10).noRippleClickable(onClick) else Modifier)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = Cine.h3)
            if (desc.isNotEmpty()) Text(desc, style = Cine.small)
        }
        if (value.isNotEmpty()) Text(value, style = Cine.small)
    }
}

@Composable
fun SettingsScreen(nav: Navigator) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var ref by remember { mutableStateOf(0) }
    var pinMode by remember { mutableStateOf("") }
    var pinFirst by remember { mutableStateOf("") }

    val s = Store.settings()
    val langs = listOf("system", "en", "ar", "fr")
    val fmts = listOf("auto", "hls", "ts")
    fun cycle(list: List<String>, cur: String): String = list[(list.indexOf(cur) + 1 + list.size) % list.size]
    fun langLabel(l: String) = when (l) {
        "en" -> "English"
        "ar" -> "العربية"
        "fr" -> "Français"
        else -> ctx.getString(R.string.lang_system)
    }
    fun fmtLabel(f: String) = when (f) {
        "hls" -> ctx.getString(R.string.fmt_hls)
        "ts" -> ctx.getString(R.string.fmt_ts)
        else -> ctx.getString(R.string.fmt_auto)
    }

    fun toggleParental() {
        if (!s.parental && Store.pin().isNullOrEmpty()) {
            pinFirst = ""
            pinMode = "set-parental"
        } else if (s.parental) {
            pinMode = "verify-disable"
        } else {
            Store.updateSettings { it.parental = true }
            Ui.toast(ctx, ctx.getString(R.string.lock_hint))
            ref++
        }
    }

    fun changePin() {
        if (Store.pin().isNullOrEmpty()) {
            pinFirst = ""
            pinMode = "set"
        } else {
            pinMode = "verify-change"
        }
    }

    fun unlockAll() {
        if (s.parental) pinMode = "verify-unlock"
        else {
            val id = Repository.accountId ?: Store.lastAccount()
            if (id != null) {
                Store.clearLocks(id)
                Ui.toast(ctx, ctx.getString(R.string.unlocked_all))
            }
        }
    }

    fun refresh() {
        scope.launch {
            val id = Repository.accountId
            if (id != null) {
                DiskCache.clearAccount(id)
                Repository.dropXmltv(id)
                try { ctx.deleteDatabase("epg_$id.db") } catch (e: Exception) { }
            }
            Repository.logout()
            Ui.toast(ctx, ctx.getString(R.string.refreshed))
            nav.nav(Screen.Home)
        }
    }

    ref.let { }
    Column(
        Modifier
            .fillMaxSize()
            .background(Cine.bg)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(stringResource(R.string.settings), style = Cine.h1)
        Spacer(Modifier.height(12.dp))

        SetSection(stringResource(R.string.sec_account)) {
            val acc = Repository.currentAccount()
            SetRow(stringResource(R.string.set_account), acc?.name ?: "", "›") { nav.nav(Screen.Profiles) }
        }

        SetSection(stringResource(R.string.sec_playback)) {
            SetRow(
                stringResource(R.string.set_livefmt),
                stringResource(R.string.set_livefmt_d),
                fmtLabel(s.liveFormat)
            ) {
                Store.updateSettings { it.liveFormat = cycle(fmts, it.liveFormat) }
                ref++
            }
        }

        SetSection(stringResource(R.string.sec_appearance)) {
            SetRow(stringResource(R.string.set_lang), "", langLabel(s.lang)) {
                Store.updateSettings { it.lang = cycle(langs, it.lang) }
                (ctx.applicationContext as App).applyLocale()
                (ctx as? Activity)?.recreate()
            }
        }

        SetSection(stringResource(R.string.sec_parental)) {
            SetRow(
                stringResource(R.string.set_parental),
                stringResource(R.string.set_parental_d),
                stringResource(if (s.parental) R.string.on else R.string.off)
            ) { toggleParental() }
            SetRow(stringResource(R.string.set_pin), stringResource(R.string.set_pin_d), "›") { changePin() }
            SetRow(stringResource(R.string.set_unlockall), stringResource(R.string.set_unlockall_d), "") { unlockAll() }
        }

        SetSection(stringResource(R.string.sec_advanced)) {
            SetRow(stringResource(R.string.set_refresh), stringResource(R.string.set_refresh_d), "") { refresh() }
            SetRow(stringResource(R.string.clear_cache), "", "") {
                scope.launch {
                    try {
                        DiskCache.clearAll()
                        Coil.imageLoader(ctx).diskCache?.clear()
                        Coil.imageLoader(ctx).memoryCache?.clear()
                        Ui.toast(ctx, ctx.getString(R.string.done))
                    } catch (e: Exception) {
                        Ui.toast(ctx, ctx.getString(R.string.err_unknown))
                    }
                }
            }
        }

        SetSection(stringResource(R.string.cloud_title)) {
            if (CloudStore.loggedIn) {
                SetRow(
                    CloudStore.email(),
                    "",
                    if (CloudStore.isAdmin()) stringResource(R.string.admin) else ""
                )
                val ls = CloudStore.lastSync()
                SetRow(
                    stringResource(R.string.last_sync),
                    "",
                    if (ls > 0) Ui.dateFull(ls) else "—"
                )
                Row(Modifier.padding(12.dp)) {
                    SmallBtn(stringResource(R.string.sync_now)) {
                        scope.launch {
                            val ok = Sync.pushNow(ctx)
                            Sync.pullNow(ctx)
                            Ui.toast(ctx, ctx.getString(if (ok) R.string.synced else R.string.sync_failed))
                            ref++
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    SmallBtn(stringResource(R.string.logout), ghost = true) {
                        CloudStore.logout()
                        ref++
                    }
                }
            } else {
                SetRow(stringResource(R.string.cloud_login), "", "›") { nav.open(Screen.Cloud) }
            }
        }

        SetSection(stringResource(R.string.sec_about)) {
            SetRow(stringResource(R.string.set_about), "RGBTv ${App.VERSION}", "›") {
                AlertDialog.Builder(ctx)
                    .setMessage(ctx.getString(R.string.about_text, App.VERSION))
                    .setPositiveButton("OK", null)
                    .show()
            }
            SetRow(stringResource(R.string.set_exit), "", "") {
                (ctx as? Activity)?.finishAffinity()
            }
        }
        Spacer(Modifier.height(40.dp))
    }

    if (pinMode.isNotEmpty()) {
        val verifyMode = pinMode.startsWith("verify")
        PinPadDialog(
            title = when (pinMode) {
                "verify-disable", "verify-change", "verify-unlock" -> ctx.getString(R.string.pin_title)
                else -> if (pinFirst.isEmpty()) ctx.getString(R.string.pin_new) else ctx.getString(R.string.pin_confirm)
            },
            onCancel = { pinMode = ""; pinFirst = "" },
            onDone = { pin ->
                if (verifyMode) {
                    if (pin == Store.pin()) {
                        when (pinMode) {
                            "verify-disable" -> {
                                Store.updateSettings { it.parental = false }
                                ref++
                            }
                            "verify-change" -> {
                                pinFirst = ""
                                pinMode = "set"
                                return@PinPadDialog
                            }
                            "verify-unlock" -> {
                                val id = Repository.accountId ?: Store.lastAccount()
                                if (id != null) {
                                    Store.clearLocks(id)
                                    Ui.toast(ctx, ctx.getString(R.string.unlocked_all))
                                }
                            }
                        }
                        pinMode = ""
                    } else {
                        Ui.toast(ctx, ctx.getString(R.string.pin_wrong))
                    }
                } else {
                    if (pinFirst.isEmpty()) {
                        pinFirst = pin
                    } else {
                        if (pin == pinFirst) {
                            Store.setPin(pin)
                            if (pinMode == "set-parental") {
                                Store.updateSettings { it.parental = true }
                            }
                            Ui.toast(ctx, ctx.getString(R.string.done))
                            ref++
                            pinMode = ""
                        } else {
                            Ui.toast(ctx, ctx.getString(R.string.pin_mismatch))
                        }
                        pinFirst = ""
                    }
                }
            }
        )
    }
}
