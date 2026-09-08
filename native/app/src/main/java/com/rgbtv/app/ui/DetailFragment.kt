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
import com.rgbtv.app.data.FavItem
import com.rgbtv.app.data.SeriesDetail
import com.rgbtv.app.data.SeriesItem
import com.rgbtv.app.data.Store
import com.rgbtv.app.data.VodDetail
import com.rgbtv.app.data.VodItem
import com.rgbtv.app.databinding.FragmentDetailBinding
import com.rgbtv.app.img.Images
import com.rgbtv.app.repo.Repository
import kotlinx.coroutines.launch
import org.json.JSONObject

class DetailFragment : Fragment() {
    private var b: FragmentDetailBinding? = null
    private var kind = "movie"
    private var itemJson = ""
    private var vodItem: VodItem? = null
    private var seriesItem: SeriesItem? = null
    private var vodDetail: VodDetail? = null
    private var seriesDetail: SeriesDetail? = null
    private var seasonIdx = 0
    private lateinit var seasonAdapter: SeasonAdapter
    private lateinit var epAdapter: EpisodeAdapter

    companion object {
        fun forItem(kind: String, json: String): DetailFragment {
            val f = DetailFragment()
            f.arguments = Bundle().apply { putString("kind", kind); putString("item", json) }
            return f
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val v = FragmentDetailBinding.inflate(i, c, false)
        b = v
        return v.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        val b = b ?: return
        kind = arguments?.getString("kind", "movie") ?: "movie"
        itemJson = arguments?.getString("item", "") ?: ""
        b.seasons.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        b.episodes.layoutManager = GridLayoutManager(requireContext(), 2)
        seasonAdapter = SeasonAdapter(onPick = { seasonIdx = it; paintEpisodes() })
        epAdapter = EpisodeAdapter(onClick = { playEpisode(it) })
        b.seasons.adapter = seasonAdapter
        b.episodes.adapter = epAdapter
        b.btnPlay.setOnClickListener { playMain() }
        b.btnFav.setOnClickListener { toggleFav() }
        Ui.autoSpan(b.episodes, 340)
        load()
    }

    private fun accId(): String = Repository.accountId ?: Store.lastAccount() ?: ""

    private fun load() {
        val b = b ?: return
        val p = Repository.provider
        if (p == null) { parentFragmentManager.popBackStack(); return }
        lifecycleScope.launch {
            try {
                if (kind == "movie") {
                    val item = VodItem.fromJson(JSONObject(itemJson))
                    vodItem = item
                    // paint instantly from list data, enrich after
                    paintVod(VodDetail(item.id, item.name, item.poster, rating = item.rating, year = item.year, ext = item.ext, url = item.url, cmd = item.cmd))
                    val d = p.vodInfo(item)
                    vodDetail = d
                    paintVod(d)
                } else {
                    val item = SeriesItem.fromJson(JSONObject(itemJson))
                    seriesItem = item
                    val d = p.seriesInfo(item)
                    seriesDetail = d
                    paintSeries(d)
                }
                b.loading.visibility = View.GONE
                b.scroll.visibility = View.VISIBLE
                paintFav()
            } catch (e: Exception) {
                b.loading.visibility = View.GONE
                Ui.toast(context, e.message ?: getString(R.string.err_unknown))
                parentFragmentManager.popBackStack()
            }
        }
    }

    private fun paintVod(d: VodDetail) {
        val b = b ?: return
        Images.load(b.backdrop, d.backdrop.ifEmpty { d.poster }, Images.Kind.BACKDROP)
        Images.load(b.poster, d.poster, Images.Kind.POSTER)
        b.dTitle.text = d.name
        b.dMeta.text = listOf(d.year, d.rating.let { if (it.isNotEmpty()) "★ $it" else "" }, d.duration)
            .filter { it.isNotEmpty() }.joinToString(" · ")
        b.dSub.text = listOf(d.genre, d.director.let { if (it.isNotEmpty()) "Dir: $it" else "" }, d.cast)
            .filter { it.isNotEmpty() }.joinToString(" · ")
        b.dPlot.text = d.plot
        b.dPlot.visibility = if (d.plot.isEmpty()) View.GONE else View.VISIBLE
        val pos = Store.getPos(accId(), "movie:${d.id}")
        b.btnPlay.text = if (pos != null && pos.pos > 10000) getString(R.string.resume) else getString(R.string.play)
    }

    private fun paintSeries(d: SeriesDetail) {
        val b = b ?: return
        Images.load(b.backdrop, d.backdrop.ifEmpty { d.poster }, Images.Kind.BACKDROP)
        Images.load(b.poster, d.poster, Images.Kind.POSTER)
        b.dTitle.text = d.name
        b.dMeta.text = listOf(d.year, d.rating.let { if (it.isNotEmpty()) "★ $it" else "" })
            .filter { it.isNotEmpty() }.joinToString(" · ")
        b.dSub.text = listOf(d.genre, d.cast).filter { it.isNotEmpty() }.joinToString(" · ")
        b.dPlot.text = d.plot
        b.dPlot.visibility = if (d.plot.isEmpty()) View.GONE else View.VISIBLE
        if (d.seasons.isNotEmpty()) {
            b.lblSeasons.visibility = View.GONE
            b.seasons.visibility = View.VISIBLE
            b.episodes.visibility = View.VISIBLE
            // jump to first unfinished season
            seasonIdx = firstUnfinishedSeason(d)
            seasonAdapter.setData(d.seasons, seasonIdx)
            paintEpisodes()
            b.btnPlay.text = getString(R.string.play)
        } else {
            b.btnPlay.text = getString(R.string.play)
        }
    }

    private fun watchedSet(): Set<String> {
        val d = seriesDetail ?: return emptySet()
        val out = mutableSetOf<String>()
        d.seasons.forEach { s -> s.episodes.forEach { e ->
            if (Store.isDone(accId(), "ep:${e.id}")) out.add(e.id)
        } }
        return out
    }

    private fun paintEpisodes() {
        val b = b ?: return
        val d = seriesDetail ?: return
        val s = d.seasons.getOrNull(seasonIdx) ?: return
        epAdapter.setData(s.episodes, watchedSet())
    }

    private fun firstUnfinishedSeason(d: SeriesDetail): Int {
        d.seasons.forEachIndexed { idx, s ->
            if (s.episodes.any { !Store.isDone(accId(), "ep:${it.id}") }) return idx
        }
        return 0
    }

    private fun playMain() {
        if (kind == "movie") {
            val d = vodDetail ?: return
            val item = vodItem ?: return
            val pos = Store.getPos(accId(), "movie:${d.id}")
            PlayerActivity.playVod(requireContext(), item, d, pos?.pos ?: 0)
        } else {
            val d = seriesDetail ?: return
            val s = d.seasons.getOrNull(seasonIdx) ?: return
            if (s.episodes.isEmpty()) return
            val idx = s.episodes.indexOfFirst { !Store.isDone(accId(), "ep:${it.id}") }.takeIf { it >= 0 } ?: 0
            val ep = s.episodes[idx]
            val pos = Store.getPos(accId(), "ep:${ep.id}")
            PlayerActivity.playEpisode(requireContext(), d.name, s.episodes, idx, pos?.pos ?: 0)
        }
    }

    private fun playEpisode(ep: com.rgbtv.app.data.EpisodeItem) {
        val d = seriesDetail ?: return
        val s = d.seasons.getOrNull(seasonIdx) ?: return
        val idx = s.episodes.indexOfFirst { it.id == ep.id }.takeIf { it >= 0 } ?: 0
        val pos = Store.getPos(accId(), "ep:${ep.id}")
        PlayerActivity.playEpisode(requireContext(), d.name, s.episodes, idx, pos?.pos ?: 0)
    }

    private fun toggleFav() {
        val (type, id, name, img) = if (kind == "movie") {
            val v = vodItem ?: return
            listOf("movie", v.id, v.name, v.poster)
        } else {
            val s = seriesItem ?: return
            listOf("series", s.id, s.name, s.poster)
        }
        val now = Store.toggleFav(accId(), FavItem(type, id, name, img, itemJson))
        Ui.toast(context, getString(if (now) R.string.added_fav else R.string.removed_fav))
        paintFav()
    }

    private fun paintFav() {
        val b = b ?: return
        val (type, id) = if (kind == "movie") {
            val v = vodItem ?: return
            "movie" to v.id
        } else {
            val s = seriesItem ?: return
            "series" to s.id
        }
        val fav = Store.isFav(accId(), type, id)
        b.btnFav.text = if (fav) "★ ${getString(R.string.remove_fav)}" else "☆ ${getString(R.string.add_fav)}"
    }

    override fun onResume() {
        super.onResume()
        if (seriesDetail != null) paintEpisodes()
        if (vodDetail != null || seriesDetail != null) {
            paintFav()
            vodDetail?.let { paintVod(it) }
        }
    }

    override fun onDestroyView() {
        b = null
        super.onDestroyView()
    }
}
