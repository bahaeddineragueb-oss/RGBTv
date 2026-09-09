package com.rgbtv.app.compose

import android.content.Context
import com.rgbtv.app.data.EpisodeItem
import com.rgbtv.app.data.LiveCh
import com.rgbtv.app.data.VodItem
import com.rgbtv.app.repo.Library
import com.rgbtv.app.ui.PlayerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

fun CoroutineScope.goLive(ctx: Context, gate: GateState, accId: String, ch: LiveCh, list: List<LiveCh>) =
    launch {
        gate.run(accId, "live", ch.id, ch.name) {
            PlayerActivity.playLive(ctx, ch, list)
        }
    }

fun CoroutineScope.goVod(ctx: Context, gate: GateState, accId: String, v: VodItem, start: Long = -1) =
    launch {
        val p = try { Library.getPos(ctx, accId, "movie:${v.id}") } catch (e: Exception) { null }
        gate.run(accId, "movie", v.id, v.name) {
            PlayerActivity.playVod(ctx, v, null, if (start >= 0) start else p?.pos ?: 0)
        }
    }

fun CoroutineScope.goEp(
    ctx: Context, gate: GateState, accId: String, name: String,
    eps: List<EpisodeItem>, idx: Int
) =
    launch {
        val ep = eps.getOrNull(idx) ?: return@launch
        val p = try { Library.getPos(ctx, accId, "ep:${ep.id}") } catch (e: Exception) { null }
        gate.run(accId, "episode", ep.id, ep.name.ifEmpty { name }) {
            PlayerActivity.playEpisode(ctx, name, eps, idx, p?.pos ?: 0)
        }
    }
