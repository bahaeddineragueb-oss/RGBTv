package com.rgbtv.app.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.rgbtv.app.R
import com.rgbtv.app.data.Store
import com.rgbtv.app.repo.Repository
import com.rgbtv.app.ui.Kit
import com.rgbtv.app.ui.Ui
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

/** Compose mirror of Guard: runs the action immediately, or after a PIN check. */
class GateState internal constructor(private val defaultTitle: String) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    internal var pending by mutableStateOf<Pair<String, Continuation<Boolean>>?>(null)

    fun run(accId: String, type: String, id: String, name: String, action: () -> Unit) {
        if (!Store.settings().parental) {
            action()
            return
        }
        val key = "$accId:$type:$id"
        if (Repository.unlocked.contains(key)) {
            action()
            return
        }
        if (!Store.isLocked(accId, "$type:$id") && !Kit.isAdult(name)) {
            action()
            return
        }
        scope.launch {
            if (askPin(defaultTitle)) {
                Repository.unlocked.add(key)
                action()
            }
        }
    }

    suspend fun askPin(title: String): Boolean = suspendCancellableCoroutine { c ->
        pending = title to c
    }

    fun dismiss(ok: Boolean) {
        pending?.second?.resume(ok)
        pending = null
    }
}

@Composable
fun rememberGate(): GateState {
    val ctx = LocalContext.current
    return remember { GateState(ctx.getString(R.string.pin_title)) }
}

@Composable
fun GateDialog(gate: GateState) {
    val ctx = LocalContext.current
    gate.pending?.let { (title, _) ->
        PinPadDialog(
            title = title,
            onCancel = { gate.dismiss(false) },
            onDone = { pin ->
                if (pin == Store.pin()) gate.dismiss(true)
                else Ui.toast(ctx, ctx.getString(R.string.pin_wrong))
            }
        )
    }
}

@Composable
fun PinPadDialog(title: String, onCancel: () -> Unit, onDone: (String) -> Unit) {
    var entry by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onCancel) {
        Box(
            Modifier
                .width(340.dp)
                .background(Cine.card, RoundedCornerShape(18.dp))
                .padding(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = Cine.h2, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.Center) {
                    repeat(4) { i ->
                        Box(
                            Modifier
                                .padding(5.dp)
                                .size(18.dp)
                                .background(
                                    if (i < entry.length) Cine.accent else Cine.card2,
                                    RoundedCornerShape(9.dp)
                                )
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth().height(240.dp)
                ) {
                    items(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "OK")) { k ->
                        Box(
                            Modifier
                                .padding(4.dp)
                                .fillMaxWidth()
                                .height(52.dp)
                                .tvFocus(radius = 12)
                                .background(
                                    if (k == "OK") Cine.accent else Cine.card2,
                                    RoundedCornerShape(12.dp)
                                )
                                .noRippleClickable {
                                    when (k) {
                                        "C" -> entry = ""
                                        "OK" -> if (entry.length == 4) onDone(entry)
                                        else -> if (entry.length < 4) entry += k
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(k, style = Cine.h2)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                TvButton("✕", ghost = true, onClick = onCancel)
            }
        }
    }
}

@Composable
fun TvButton(
    label: String,
    modifier: Modifier = Modifier,
    ghost: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .tvFocus(radius = 12)
            .background(
                if (ghost) Cine.card2 else Cine.accent,
                RoundedCornerShape(12.dp)
            )
            .noRippleClickable(onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = if (ghost) Cine.h3 else Cine.h3.copy(color = Cine.onAccent),
            maxLines = 1
        )
    }
}
