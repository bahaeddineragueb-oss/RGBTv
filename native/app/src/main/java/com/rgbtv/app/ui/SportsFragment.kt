package com.rgbtv.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.rgbtv.app.R
import com.rgbtv.app.data.Category
import com.rgbtv.app.data.FavItem
import com.rgbtv.app.data.LiveCh
import com.rgbtv.app.data.Store
import com.rgbtv.app.databinding.FragmentSportsBinding
import com.rgbtv.app.img.Images
import com.rgbtv.app.net.Net
import com.rgbtv.app.repo.Repository
import com.rgbtv.app.ui.Ui.main
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SportsFragment : Fragment() {
    private var b: FragmentSportsBinding? = null
    private lateinit var adapter: ChannelAdapter
    private var all: List<LiveCh> = emptyList()
    private var catNames: Map<String, String> = emptyMap()
    private var pool: List<LiveCh> = emptyList()
    private var filtered: List<LiveCh> = emptyList()
    private var tab = 0
    private var current: LiveCh? = null
    private var epgJob: Job? = null

    companion object {
        private val SPORT_ANY = listOf(
            "sport", "foot", "soccer", "futbol", "bein", "espn", "eurosport",
            "sky sport", "skysport", "rmc", "dazn", "nba", "nfl", "nhl", "mlb",
            "tennis", "formula", "motogp", "moto gp", "golf", "rugby", "cricket",
            "boxing", "mma", "ufc", "wwe", "olymp", "liga", "premier", "bundes",
            "serie a", "ligue", "champions", "europa", "copa", "movistar",
            "digi sport", "diema", "orange sport", "super sport", "supersport",
            "setanta", "eleven", "viaplay", "ziggo", "tsn", "rds", "match tv",
            "arena sport", "sportklub", "sfl", "laliga", "epl", "acb", "euroleague"
        )
        private val FOOT = listOf(
            "foot", "soccer", "futbol", "premier", "liga", "bundes", "serie a",
            "ligue", "champions", "europa", "world cup", "copa", "bein", "epl", "laliga"
        )
        private val BASKET = listOf("basket", "nba", "euroleague", "acb")
        private val TENNIS = listOf(
            "tennis", "roland", "wimbledon", "atp", "wta", "us open", "davis cup"
        )
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val v = FragmentSportsBinding.inflate(i, c, false)
        b = v
        return v.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        val b = b ?: return
        b.grid.layoutManager = GridLayoutManager(requireContext(), 4)
        adapter = ChannelAdapter(
            onClick = { onPlay(it) },
            onFocus = { onFocusCh(it) },
            onLong = {},
            badgeOf = { "● ${getString(R.string.live_badge)}" }
        )
        b.grid.adapter = adapter
        Ui.autoSpan(b.grid, 150)
        b.chipAll.setOnClickListener { tab = 0; paintChips(); applyFilter() }
        b.chipFootball.setOnClickListener { tab = 1; paintChips(); applyFilter() }
        b.chipBasket.setOnClickListener { tab = 2; paintChips(); applyFilter() }
        b.chipTennis.setOnClickListener { tab = 3; paintChips(); applyFilter() }
        b.chipOther.setOnClickListener { tab = 4; paintChips(); applyFilter() }
        b.btnPlay.setOnClickListener { current?.let { onPlay(it) } }
        b.btnFav.setOnClickListener { toggleFav() }
        paintChips()
        load()
    }

    private fun accId(): String = Repository.accountId ?: Store.lastAccount() ?: ""

    private fun paintChips() {
        val b = b ?: return
        b.chipAll.isSelected = tab == 0
        b.chipFootball.isSelected = tab == 1
        b.chipBasket.isSelected = tab == 2
        b.chipTennis.isSelected = tab == 3
        b.chipOther.isSelected = tab == 4
    }

    private fun blob(c: LiveCh): String =
        ((catNames[c.catId] ?: "") + " " + c.name).lowercase()

    private fun matches(blob: String, keys: List<String>): Boolean =
        keys.any { blob.contains(it) }

    private fun load() {
        val b = b ?: return
        val p = Repository.provider
        if (p == null) { main().home(); return }
        b.loading.visibility = View.VISIBLE
        b.empty.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val cats: List<Category> = p.liveCats()
                catNames = cats.associate { it.id to it.name }
                all = p.live(null)
                Repository.lastLive = all
                pool = all.filter { matches(blob(it), SPORT_ANY) }
                b.loading.visibility = View.GONE
                applyFilter()
            } catch (e: Exception) {
                b.loading.visibility = View.GONE
                b.empty.visibility = View.VISIBLE
                b.empty.text = Net.userMsg(context, e)
            }
        }
    }

    private fun applyFilter() {
        val b = b ?: return
        filtered = when (tab) {
            1 -> pool.filter { matches(blob(it), FOOT) }
            2 -> pool.filter { matches(blob(it), BASKET) }
            3 -> pool.filter { matches(blob(it), TENNIS) }
            4 -> pool.filter {
                val bl = blob(it)
                !matches(bl, FOOT) && !matches(bl, BASKET) && !matches(bl, TENNIS)
            }
            else -> pool
        }
        adapter.submitList(filtered)
        b.count.text = getString(R.string.items_d, filtered.size)
        b.empty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun onPlay(ch: LiveCh) {
        Guard.run(this, accId(), "live", ch.id, ch.name) {
            PlayerActivity.playLive(requireContext(), ch, filtered)
        }
    }

    private fun onFocusCh(ch: LiveCh) {
        Repository.provider?.prefetch(ch)
        val b = b ?: return
        current = ch
        b.panel.visibility = View.VISIBLE
        Images.load(b.pLogo, ch.logo, Images.Kind.LOGO)
        b.pName.text = ch.name
        paintFav()
        epgJob?.cancel()
        if (Store.isLocked(accId(), "live:${ch.id}")) {
            b.pNow.text = "🔒 ${getString(R.string.locked)}"
            b.pNext.text = ""
            b.pProgress.progress = 0
            return
        }
        epgJob = lifecycleScope.launch {
            delay(350)
            try {
                val (now, next) = Repository.nowNext(requireContext(), ch)
                val bb = b ?: return@launch
                if (now == null && next == null) {
                    bb.pNow.text = getString(R.string.no_epg)
                    bb.pNext.text = ""
                    bb.pProgress.progress = 0
                } else {
                    bb.pNow.text = "• ${now?.title ?: next?.title ?: ""}" +
                        (now?.let { " (${Ui.clock(it.start)}–${Ui.clock(it.end)})" } ?: "")
                    bb.pNext.text = next?.let {
                        "${getString(R.string.next)}: ${it.title} ${Ui.clock(it.start)}"
                    } ?: ""
                    bb.pProgress.progress = if (now != null && now.end > now.start) {
                        ((System.currentTimeMillis() / 1000 - now.start) * 100 / (now.end - now.start))
                            .toInt().coerceIn(0, 100)
                    } else 0
                }
            } catch (e: Exception) { }
        }
    }

    private fun toggleFav() {
        val ch = current ?: return
        val now = Store.toggleFav(accId(), FavItem("live", ch.id, ch.name, ch.logo, ch.toJson().toString()))
        Ui.toast(context, getString(if (now) R.string.added_fav else R.string.removed_fav))
        paintFav()
    }

    private fun paintFav() {
        val b = b ?: return
        val ch = current ?: return
        val fav = Store.isFav(accId(), "live", ch.id)
        b.btnFav.text = if (fav) getString(R.string.remove_fav) else getString(R.string.add_fav)
    }

    override fun onDestroyView() {
        epgJob?.cancel()
        b = null
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as MainActivity).select("sports")
    }
}
