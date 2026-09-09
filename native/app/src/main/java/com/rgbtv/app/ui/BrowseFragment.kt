package com.rgbtv.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.rgbtv.app.R
import com.rgbtv.app.data.Category
import com.rgbtv.app.data.LiveCh
import com.rgbtv.app.data.SeriesItem
import com.rgbtv.app.data.Store
import com.rgbtv.app.data.VodItem
import com.rgbtv.app.databinding.FragmentBrowseBinding
import com.rgbtv.app.net.Net
import com.rgbtv.app.repo.Repository
import com.rgbtv.app.ui.Ui.main
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BrowseFragment : Fragment() {
    private var b: FragmentBrowseBinding? = null
    private var mode = MODE_LIVE
    private lateinit var catsAdapter: CategoryAdapter
    private var chAdapter: ChannelAdapter? = null
    private var posterAdapter: PosterAdapter? = null

    private var cats: List<Category> = emptyList()
    private var selCat: Category? = null
    private var allLive: List<LiveCh> = emptyList()
    private var allVod: List<VodItem> = emptyList()
    private var allSeries: List<SeriesItem> = emptyList()
    private var filteredLive: List<LiveCh> = emptyList()
    private var filteredVod: List<VodItem> = emptyList()
    private var filteredSeries: List<SeriesItem> = emptyList()
    private var sortIdx = 0
    private var epgJob: Job? = null
    private var filterJob: Job? = null

    companion object {
        const val MODE_LIVE = 0
        const val MODE_VOD = 1
        const val MODE_SERIES = 2
        fun forMode(m: Int): BrowseFragment {
            val f = BrowseFragment()
            f.arguments = Bundle().apply { putInt("mode", m) }
            return f
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val v = FragmentBrowseBinding.inflate(i, c, false)
        b = v
        return v.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        val b = b ?: return
        mode = arguments?.getInt("mode", MODE_LIVE) ?: MODE_LIVE
        b.title.text = when (mode) {
            MODE_VOD -> getString(R.string.movies)
            MODE_SERIES -> getString(R.string.series)
            else -> getString(R.string.live_tv)
        }
        b.epgPanel.visibility = if (mode == MODE_LIVE) View.VISIBLE else View.GONE
        b.cats.layoutManager = LinearLayoutManager(requireContext())
        b.grid.layoutManager = GridLayoutManager(requireContext(), 4)
        catsAdapter = CategoryAdapter(onPick = { onPickCat(it) })
        b.cats.adapter = catsAdapter
        if (mode == MODE_LIVE) {
            chAdapter = ChannelAdapter(
                onClick = { onLiveClick(it) },
                onFocus = { onLiveFocus(it) },
                onLong = { toggleLiveLock(it) },
                badgeOf = { badgeFor("live", it.id, it.name, it.archive) }
            )
            b.grid.adapter = chAdapter
            Ui.autoSpan(b.grid, 150)
        } else {
            posterAdapter = PosterAdapter(
                onClick = { onPosterClick(it) },
                onLong = { onPosterLong(it) }
            )
            b.grid.adapter = posterAdapter
            Ui.autoSpan(b.grid, 155)
        }
        paintSort()
        b.btnSort.setOnClickListener {
            sortIdx = (sortIdx + 1) % sortLabels().size
            paintSort()
            applyFilter()
        }
        b.filter.doOnTextChanged { _, _, _, _ ->
            filterJob?.cancel()
            filterJob = lifecycleScope.launch { delay(300); applyFilter() }
        }
        load()
    }

    private fun sortLabels(): List<String> = when (mode) {
        MODE_LIVE -> listOf(getString(R.string.sort_num), getString(R.string.sort_name))
        else -> listOf(getString(R.string.sort_added), getString(R.string.sort_name))
    }

    private fun paintSort() {
        b?.btnSort?.text = "⇅ ${sortLabels()[sortIdx]}"
    }

    private fun accId(): String = Repository.accountId ?: Store.lastAccount() ?: ""

    private fun load() {
        val b = b ?: return
        val p = Repository.provider
        if (p == null) { main().home(); return }
        b.loading.visibility = View.VISIBLE
        b.empty.visibility = View.GONE
        lifecycleScope.launch {
            try {
                when (mode) {
                    MODE_LIVE -> {
                        cats = p.liveCats()
                        allLive = p.live(null)
                        Repository.lastLive = allLive
                        val counts = allLive.groupingBy { it.catId }.eachCount()
                        catsAdapter.setData(cats, counts, null)
                    }
                    MODE_VOD -> {
                        cats = p.vodCats()
                        allVod = p.vod(null)
                        val counts = allVod.groupingBy { it.catId }.eachCount()
                        catsAdapter.setData(cats, counts, null)
                    }
                    else -> {
                        cats = p.seriesCats()
                        allSeries = p.series(null)
                        val counts = allSeries.groupingBy { it.catId }.eachCount()
                        catsAdapter.setData(cats, counts, null)
                    }
                }
                b.loading.visibility = View.GONE
                applyFilter()
            } catch (e: Exception) {
                b.loading.visibility = View.GONE
                b.empty.visibility = View.VISIBLE
                b.empty.text = Net.userMsg(context, e)
            }
        }
    }

    private fun onPickCat(c: Category?) {
        if (c?.censored == true && Store.settings().parental) {
            Guard.runForce(this, "${accId()}:cat:${c.id}") {
                selCat = c
                catsAdapter.setData(cats, counts(), c.id)
                applyFilter()
            }
        } else {
            selCat = c
            catsAdapter.setData(cats, counts(), c?.id)
            applyFilter()
        }
    }

    private fun counts(): Map<String, Int> = when (mode) {
        MODE_LIVE -> allLive.groupingBy { it.catId }.eachCount()
        MODE_VOD -> allVod.groupingBy { it.catId }.eachCount()
        else -> allSeries.groupingBy { it.catId }.eachCount()
    }

    private fun query(): String = b?.filter?.text?.toString()?.trim()?.lowercase() ?: ""

    private fun applyFilter() {
        val b = b ?: return
        val q = query()
        val cat = selCat?.id
        when (mode) {
            MODE_LIVE -> {
                var l = if (cat.isNullOrEmpty()) allLive else allLive.filter { it.catId == cat }
                if (q.length >= 2) l = l.filter { it.name.lowercase().contains(q) }
                l = if (sortIdx == 0) l.sortedWith(compareBy({ if (it.num > 0) it.num else Int.MAX_VALUE }, { it.name.lowercase() }))
                else l.sortedBy { it.name.lowercase() }
                filteredLive = l
                chAdapter?.submitList(l)
                b.count.text = getString(R.string.items_d, l.size)
                b.empty.visibility = if (l.isEmpty()) View.VISIBLE else View.GONE
                if (l.isNotEmpty()) b.empty.text = getString(R.string.no_results)
            }
            MODE_VOD -> {
                var l = if (cat.isNullOrEmpty()) allVod else allVod.filter { it.catId == cat }
                if (q.length >= 2) l = l.filter { it.name.lowercase().contains(q) }
                l = if (sortIdx == 0) l.sortedByDescending { it.added } else l.sortedBy { it.name.lowercase() }
                filteredVod = l
                posterAdapter?.submitList(l.map { v ->
                    PosterRow(
                        "movie:${v.id}", v.poster, v.name,
                        listOf(v.year, v.rating.ifEmpty { "" }.let { if (it.isNotEmpty()) "★$it" else "" })
                            .filter { it.isNotEmpty() }.joinToString(" · "),
                        badge = badgeFor("movie", v.id, v.name, false)
                    )
                })
                b.count.text = getString(R.string.items_d, l.size)
                b.empty.visibility = if (l.isEmpty()) View.VISIBLE else View.GONE
            }
            else -> {
                var l = if (cat.isNullOrEmpty()) allSeries else allSeries.filter { it.catId == cat }
                if (q.length >= 2) l = l.filter { it.name.lowercase().contains(q) }
                l = if (sortIdx == 0) l.sortedByDescending { it.added } else l.sortedBy { it.name.lowercase() }
                filteredSeries = l
                posterAdapter?.submitList(l.map { s ->
                    PosterRow(
                        "series:${s.id}", s.poster, s.name,
                        listOf(s.year, s.rating.ifEmpty { "" }.let { if (it.isNotEmpty()) "★$it" else "" })
                            .filter { it.isNotEmpty() }.joinToString(" · "),
                        badge = badgeFor("series", s.id, s.name, false)
                    )
                })
                b.count.text = getString(R.string.items_d, l.size)
                b.empty.visibility = if (l.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun badgeFor(type: String, id: String, name: String, archive: Boolean): String? {
        val locked = Store.isLocked(accId(), "$type:$id") ||
            (Store.settings().parental && Kit.isAdult(name))
        val sb = StringBuilder()
        if (locked) sb.append("🔒")
        if (archive) {
            if (sb.isNotEmpty()) sb.append(" ")
            sb.append("⏪")
        }
        return if (sb.isEmpty()) null else sb.toString()
    }

    private fun onLiveClick(ch: LiveCh) {
        Guard.run(this, accId(), "live", ch.id, ch.name) {
            PlayerActivity.playLive(requireContext(), ch, filteredLive)
        }
    }

    private fun onLiveFocus(ch: LiveCh) {
        Repository.provider?.prefetch(ch)
        val b = b ?: return
        epgJob?.cancel()
        if (Store.isLocked(accId(), "live:${ch.id}")) {
            b.epgNow.text = "🔒 ${getString(R.string.locked)}"
            b.epgNext.text = ""
            b.epgProgress.progress = 0
            return
        }
        epgJob = lifecycleScope.launch {
            delay(350)
            try {
                val (now, next) = Repository.nowNext(requireContext(), ch)
                val bb = b ?: return@launch
                if (now == null && next == null) {
                    bb.epgNow.text = getString(R.string.no_epg)
                    bb.epgNext.text = ""
                    bb.epgProgress.progress = 0
                } else {
                    bb.epgNow.text = "• ${now?.title ?: next?.title ?: ""}" +
                        (now?.let { " (${Ui.clock(it.start)}–${Ui.clock(it.end)})" } ?: "")
                    bb.epgNext.text = next?.let { "${getString(R.string.next)}: ${it.title} ${Ui.clock(it.start)}" } ?: ""
                    bb.epgProgress.progress = if (now != null && now.end > now.start) {
                        (((System.currentTimeMillis() / 1000 - now.start) * 100 / (now.end - now.start)).toInt().coerceIn(0, 100))
                    } else 0
                }
            } catch (e: Exception) { }
        }
    }

    private fun toggleLiveLock(ch: LiveCh) {
        val now = Store.toggleLock(accId(), "live:${ch.id}")
        Ui.toast(context, getString(if (now) R.string.locked_item else R.string.unlocked_item))
        val idx = chAdapter?.currentList?.indexOf(ch) ?: -1
        if (idx >= 0) chAdapter?.notifyItemChanged(idx)
    }

    private fun onPosterClick(pos: Int) {
        if (mode == MODE_VOD) {
            val v = filteredVod.getOrNull(pos) ?: return
            Guard.run(this, accId(), "movie", v.id, v.name) {
                main().open(DetailFragment.forItem("movie", v.toJson().toString()))
            }
        } else {
            val s = filteredSeries.getOrNull(pos) ?: return
            Guard.run(this, accId(), "series", s.id, s.name) {
                main().open(DetailFragment.forItem("series", s.toJson().toString()))
            }
        }
    }

    private fun onPosterLong(pos: Int) {
        val (type, id) = if (mode == MODE_VOD) {
            val v = filteredVod.getOrNull(pos) ?: return
            "movie" to v.id
        } else {
            val s = filteredSeries.getOrNull(pos) ?: return
            "series" to s.id
        }
        val now = Store.toggleLock(accId(), "$type:$id")
        Ui.toast(context, getString(if (now) R.string.locked_item else R.string.unlocked_item))
        applyFilter()
    }

    override fun onResume() {
        super.onResume()
        val navKey = when (mode) {
            MODE_VOD -> "movies"
            MODE_SERIES -> "series"
            else -> "live"
        }
        (requireActivity() as MainActivity).select(navKey)
        // locks may have changed elsewhere
        if (b != null && chAdapter != null) {
            val l = chAdapter!!.currentList
            if (l.isNotEmpty()) chAdapter!!.notifyDataSetChanged()
        }
    }

    override fun onDestroyView() {
        epgJob?.cancel()
        filterJob?.cancel()
        b = null
        super.onDestroyView()
    }
}
