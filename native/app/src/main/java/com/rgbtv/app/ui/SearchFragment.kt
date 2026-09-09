package com.rgbtv.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.rgbtv.app.R
import com.rgbtv.app.data.Store
import com.rgbtv.app.databinding.FragmentSearchBinding
import com.rgbtv.app.repo.Repository
import com.rgbtv.app.ui.Ui.main
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {
    private var b: FragmentSearchBinding? = null
    private var tab = 0
    private var job: Job? = null
    private var result: Repository.SearchResult? = null
    private lateinit var chAdapter: ChannelAdapter
    private lateinit var posterAdapter: PosterAdapter

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val v = FragmentSearchBinding.inflate(i, c, false)
        b = v
        return v.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        val b = b ?: return
        b.grid.layoutManager = GridLayoutManager(requireContext(), 4)
        chAdapter = ChannelAdapter(
            onClick = { ch ->
                Guard.run(this, accId(), "live", ch.id, ch.name) {
                    PlayerActivity.playLive(requireContext(), ch, result?.live ?: listOf(ch))
                }
            },
            onFocus = {},
            onLong = {},
            badgeOf = { if (Store.isLocked(accId(), "live:${it.id}")) "🔒" else null }
        )
        posterAdapter = PosterAdapter(onClick = { onPosterClick(it) })
        b.grid.adapter = chAdapter
        Ui.autoSpan(b.grid, 150)
        b.tabLive.setOnClickListener { tab = 0; paint() }
        b.tabVod.setOnClickListener { tab = 1; paint() }
        b.tabSeries.setOnClickListener { tab = 2; paint() }
        b.query.doOnTextChanged { _, _, _, _ ->
            job?.cancel()
            job = lifecycleScope.launch {
                delay(450)
                runSearch()
            }
        }
        b.query.post { b.query.requestFocus() }
    }

    private fun accId(): String = Repository.accountId ?: Store.lastAccount() ?: ""

    private fun runSearch() {
        val b = b ?: return
        val q = b.query.text.toString()
        if (q.trim().length < 2) {
            result = null
            b.loading.visibility = View.GONE
            b.empty.visibility = View.VISIBLE
            chAdapter.submitList(emptyList())
            posterAdapter.submitList(emptyList())
            paintTabs(0, 0, 0)
            return
        }
        b.loading.visibility = View.VISIBLE
        b.empty.visibility = View.GONE
        lifecycleScope.launch {
            try {
                result = Repository.search(q)
            } catch (e: Exception) {
                result = Repository.SearchResult(emptyList(), emptyList(), emptyList())
            }
            b.loading.visibility = View.GONE
            paint()
        }
    }

    private fun paintTabs(l: Int, v: Int, s: Int) {
        val b = b ?: return
        b.tabLive.text = "${getString(R.string.live_tv)} ($l)"
        b.tabVod.text = "${getString(R.string.movies)} ($v)"
        b.tabSeries.text = "${getString(R.string.series)} ($s)"
        b.tabLive.alpha = if (tab == 0) 1f else 0.6f
        b.tabVod.alpha = if (tab == 1) 1f else 0.6f
        b.tabSeries.alpha = if (tab == 2) 1f else 0.6f
    }

    private fun paint() {
        val b = b ?: return
        val r = result
        if (r == null) {
            paintTabs(0, 0, 0)
            return
        }
        paintTabs(r.live.size, r.vod.size, r.series.size)
        when (tab) {
            0 -> {
                b.grid.adapter = chAdapter
                chAdapter.submitList(r.live)
                b.count.text = getString(R.string.results_d, r.live.size)
                b.empty.visibility = if (r.live.isEmpty()) View.VISIBLE else View.GONE
            }
            1 -> {
                b.grid.adapter = posterAdapter
                posterAdapter.submitList(r.vod.map { v ->
                    PosterRow("movie:${v.id}", v.poster, v.name, v.year)
                })
                b.count.text = getString(R.string.results_d, r.vod.size)
                b.empty.visibility = if (r.vod.isEmpty()) View.VISIBLE else View.GONE
            }
            else -> {
                b.grid.adapter = posterAdapter
                posterAdapter.submitList(r.series.map { s ->
                    PosterRow("series:${s.id}", s.poster, s.name, s.year)
                })
                b.count.text = getString(R.string.results_d, r.series.size)
                b.empty.visibility = if (r.series.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun onPosterClick(pos: Int) {
        val r = result ?: return
        if (tab == 1) {
            val v = r.vod.getOrNull(pos) ?: return
            Guard.run(this, accId(), "movie", v.id, v.name) {
                main().open(DetailFragment.forItem("movie", v.toJson().toString()))
            }
        } else {
            val s = r.series.getOrNull(pos) ?: return
            Guard.run(this, accId(), "series", s.id, s.name) {
                main().open(DetailFragment.forItem("series", s.toJson().toString()))
            }
        }
    }

    override fun onDestroyView() {
        job?.cancel()
        b = null
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as MainActivity).select("search")
    }
}
