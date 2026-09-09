package com.rgbtv.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.rgbtv.app.R
import com.rgbtv.app.data.LiveCh
import com.rgbtv.app.data.Store
import com.rgbtv.app.databinding.FragmentGuideBinding
import com.rgbtv.app.repo.Repository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GuideFragment : Fragment() {
    private var b: FragmentGuideBinding? = null
    private lateinit var chAdapter: ChannelAdapter
    private lateinit var pgAdapter: EpgAdapter
    private var all: List<LiveCh> = emptyList()
    private var current: LiveCh? = null
    private var job: Job? = null

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val v = FragmentGuideBinding.inflate(i, c, false)
        b = v
        return v.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        val b = b ?: return
        b.channels.layoutManager = GridLayoutManager(requireContext(), 1)
        b.programs.layoutManager = LinearLayoutManager(requireContext())
        chAdapter = ChannelAdapter(
            onClick = { onChClick(it) },
            onFocus = { onChFocus(it) },
            onLong = { toggleLock(it) },
            badgeOf = {
                if (Store.isLocked(accId(), "live:${it.id}")) "🔒"
                else if (it.archive) "⏪" else null
            }
        )
        pgAdapter = EpgAdapter(
            onClick = { onPgClick(it) },
            onFocus = { paintDesc(it) }
        )
        b.channels.adapter = chAdapter
        b.programs.adapter = pgAdapter
        load()
    }

    private fun accId(): String = Repository.accountId ?: Store.lastAccount() ?: ""

    private fun load() {
        val p = Repository.provider ?: return
        lifecycleScope.launch {
            try {
                all = p.live(null).sortedWith(
                    compareBy({ if (it.num > 0) it.num else Int.MAX_VALUE }, { it.name.lowercase() })
                )
                Repository.lastLive = all
                chAdapter.submitList(all)
                b?.channels?.post {
                    b?.channels?.layoutManager?.findViewByPosition(0)?.requestFocus()
                }
            } catch (e: Exception) {
                Ui.toast(context, e.message ?: getString(R.string.err_unknown))
            }
        }
    }

    private fun guarded(ch: LiveCh, action: () -> Unit) {
        Guard.run(this, accId(), "live", ch.id, ch.name, action)
    }

    private fun onChClick(ch: LiveCh) {
        guarded(ch) { PlayerActivity.playLive(requireContext(), ch, all) }
    }

    private fun toggleLock(ch: LiveCh) {
        val now = Store.toggleLock(accId(), "live:${ch.id}")
        Ui.toast(context, getString(if (now) R.string.locked_item else R.string.unlocked_item))
        val idx = chAdapter.currentList.indexOf(ch)
        if (idx >= 0) chAdapter.notifyItemChanged(idx)
    }

    private fun onChFocus(ch: LiveCh) {
        val b = b ?: return
        current = ch
        b.chName.text = ch.name
        Repository.provider?.prefetch(ch)
        job?.cancel()
        job = lifecycleScope.launch {
            delay(250)
            val bb = b ?: return@launch
            if (Store.isLocked(accId(), "live:${ch.id}")) {
                pgAdapter.setData(emptyList())
                bb.empty.visibility = View.VISIBLE
                bb.empty.text = "🔒 ${getString(R.string.locked)}"
                bb.desc.text = ""
                return@launch
            }
            bb.loading.visibility = View.VISIBLE
            bb.empty.visibility = View.GONE
            try {
                val list = Repository.epgFor(requireContext(), ch, 40)
                val now = System.currentTimeMillis() / 1000
                val rows = list.map { e ->
                    val state = when {
                        e.start <= now && now < e.end -> EpgRow.NOW
                        e.end <= now -> EpgRow.PAST
                        else -> EpgRow.FUTURE
                    }
                    val pct = if (state == EpgRow.NOW && e.end > e.start)
                        ((now - e.start) * 100 / (e.end - e.start)).toInt().coerceIn(0, 100) else 0
                    EpgRow(e, state, pct)
                }
                pgAdapter.setData(rows)
                bb.empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
                bb.desc.text = ""
            } catch (e: Exception) {
                pgAdapter.setData(emptyList())
                bb.empty.visibility = View.VISIBLE
            } finally {
                bb.loading.visibility = View.GONE
            }
        }
    }

    private fun paintDesc(r: EpgRow) {
        val b = b ?: return
        b.desc.text = "${Ui.clock(r.ev.start)}–${Ui.clock(r.ev.end)} · ${r.ev.title}" +
            (if (r.ev.desc.isNotEmpty()) "\n${r.ev.desc}" else "")
    }

    private fun onPgClick(r: EpgRow) {
        val ch = current ?: return
        paintDesc(r)
        when (r.state) {
            EpgRow.NOW -> guarded(ch) { PlayerActivity.playLive(requireContext(), ch, all) }
            EpgRow.PAST -> {
                if (!ch.archive) {
                    Ui.toast(context, getString(R.string.past))
                    return
                }
                guarded(ch) { PlayerActivity.playCatchup(requireContext(), ch, r.ev) }
            }
            else -> Ui.toast(context, "${getString(R.string.future)} · ${Ui.clock(r.ev.start)}")
        }
    }

    override fun onDestroyView() {
        job?.cancel()
        b = null
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as MainActivity).select("guide")
    }
}
