package com.rgbtv.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.rgbtv.app.R
import com.rgbtv.app.data.FavItem
import com.rgbtv.app.data.HistItem
import com.rgbtv.app.data.LiveCh
import com.rgbtv.app.data.Store
import com.rgbtv.app.data.VodItem
import com.rgbtv.app.databinding.FragmentMylistBinding
import com.rgbtv.app.repo.Repository
import com.rgbtv.app.ui.Ui.main
import org.json.JSONObject

class MyListFragment : Fragment() {
    private var b: FragmentMylistBinding? = null
    private lateinit var favAdapter: PosterAdapter
    private lateinit var histAdapter: PosterAdapter
    private var favs: List<FavItem> = emptyList()
    private var hist: List<HistItem> = emptyList()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val v = FragmentMylistBinding.inflate(i, c, false)
        b = v
        return v.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        val b = b ?: return
        b.gridFavs.layoutManager = GridLayoutManager(requireContext(), 4)
        b.gridHist.layoutManager = GridLayoutManager(requireContext(), 4)
        favAdapter = PosterAdapter(onClick = { openFav(it) }, onLong = { removeFav(it) })
        histAdapter = PosterAdapter(onClick = { openHist(it) })
        b.gridFavs.adapter = favAdapter
        b.gridHist.adapter = histAdapter
        b.btnClearHist.setOnClickListener {
            Store.clearHistory(accId())
            render()
        }
        render()
    }

    private fun accId(): String = Repository.accountId ?: Store.lastAccount() ?: ""

    private fun render() {
        val b = b ?: return
        favs = Store.favs(accId())
        hist = Store.history(accId())
        favAdapter.submitList(favs.map { f -> PosterRow("f:${f.type}:${f.id}", f.img, f.name) })
        histAdapter.submitList(hist.map { h ->
            val p = Store.getPos(accId(), if (h.type == "movie") "movie:${h.id}" else "ep:${h.id}")
            val pct = if (p != null && p.dur > 0) (p.pos * 100 / p.dur).toInt() else -1
            PosterRow("h:${h.type}:${h.id}", h.img, h.name, Ui.dateFull(h.at), pct)
        })
        b.emptyFavs.visibility = if (favs.isEmpty()) View.VISIBLE else View.GONE
        b.emptyHist.visibility = if (hist.isEmpty()) View.VISIBLE else View.GONE
        Ui.autoSpan(b.gridFavs, 155)
        Ui.autoSpan(b.gridHist, 155)
    }

    private fun openFav(i: Int) {
        val f = favs.getOrNull(i) ?: return
        Guard.run(this, accId(), f.type, f.id, f.name) {
            if (f.type == "series") {
                main().open(DetailFragment.forItem("series", f.data)); return@run
            }
            try {
                when (f.type) {
                    "live" -> PlayerActivity.playLive(
                        requireContext(), LiveCh.fromJson(JSONObject(f.data)), Repository.lastLive
                    )
                    "movie" -> {
                        val v = VodItem.fromJson(JSONObject(f.data))
                        val p = Store.getPos(accId(), "movie:${v.id}")
                        PlayerActivity.playVod(requireContext(), v, null, p?.pos ?: 0)
                    }
                    "episode" -> {
                        val ep = com.rgbtv.app.data.EpisodeItem.fromJson(JSONObject(f.data))
                        val p = Store.getPos(accId(), "ep:${ep.id}")
                        PlayerActivity.playEpisode(requireContext(), "", listOf(ep), 0, p?.pos ?: 0)
                    }
                }
            } catch (e: Exception) { }
        }
    }

    private fun removeFav(i: Int) {
        val f = favs.getOrNull(i) ?: return
        Store.toggleFav(accId(), f)
        Ui.toast(context, getString(R.string.removed_fav))
        render()
    }

    private fun openHist(i: Int) {
        val h = hist.getOrNull(i) ?: return
        Guard.run(this, accId(), h.type, h.id, h.name) {
            if (h.type == "series") {
                main().open(DetailFragment.forItem("series", h.data)); return@run
            }
            try {
                when (h.type) {
                    "live" -> PlayerActivity.playLive(
                        requireContext(), LiveCh.fromJson(JSONObject(h.data)), Repository.lastLive
                    )
                    "movie" -> {
                        val v = VodItem.fromJson(JSONObject(h.data))
                        val p = Store.getPos(accId(), "movie:${v.id}")
                        PlayerActivity.playVod(requireContext(), v, null, p?.pos ?: 0)
                    }
                    "episode" -> {
                        val ep = com.rgbtv.app.data.EpisodeItem.fromJson(JSONObject(h.data))
                        val p = Store.getPos(accId(), "ep:${ep.id}")
                        PlayerActivity.playEpisode(requireContext(), "", listOf(ep), 0, p?.pos ?: 0)
                    }
                }
            } catch (e: Exception) { }
        }
    }

    override fun onDestroyView() {
        b = null
        super.onDestroyView()
    }
}
