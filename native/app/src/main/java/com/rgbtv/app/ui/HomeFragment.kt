package com.rgbtv.app.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.rgbtv.app.R
import com.rgbtv.app.data.Account
import com.rgbtv.app.data.FavItem
import com.rgbtv.app.data.HistItem
import com.rgbtv.app.data.LiveCh
import com.rgbtv.app.data.SeriesItem
import com.rgbtv.app.data.Store
import com.rgbtv.app.data.VodItem
import com.rgbtv.app.databinding.FragmentHomeBinding
import com.rgbtv.app.net.Net
import com.rgbtv.app.net.SessionInfo
import com.rgbtv.app.repo.Repository
import com.rgbtv.app.ui.Ui.main
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {
    private var b: FragmentHomeBinding? = null
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var tiles: TileAdapter
    private lateinit var railC: PosterAdapter
    private lateinit var railF: PosterAdapter
    private var hist: List<HistItem> = emptyList()
    private var favs: List<FavItem> = emptyList()
    private var liveCount = -1

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val v = FragmentHomeBinding.inflate(i, c, false)
        b = v
        return v.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        val b = b ?: return
        b.railContinue.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        b.railFavs.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        b.tiles.layoutManager = GridLayoutManager(requireContext(), 4)
        railC = PosterAdapter(onClick = { openHist(it) })
        railF = PosterAdapter(onClick = { openFav(it) }, onLong = { removeFav(it) })
        b.railContinue.adapter = railC
        b.railFavs.adapter = railF
        tiles = TileAdapter(onClick = { openTile(it) })
        b.tiles.adapter = tiles
        b.bannerSearch.setOnClickListener { main().open(SearchFragment()) }
        b.bannerMylist.setOnClickListener { main().open(MyListFragment()) }
        Ui.focusScale(b.bannerSearch)
        Ui.focusScale(b.bannerMylist)
        b.btnSwitch.setOnClickListener { main().profiles() }
        b.btnSettings.setOnClickListener { main().open(SettingsFragment()) }
        b.btnRetry.setOnClickListener { load() }
        b.btnSwitchErr.setOnClickListener { main().profiles() }
        tickClock()
        load()
    }

    override fun onResume() {
        super.onResume()
        if (b?.scroll?.visibility == View.VISIBLE) renderRails()
    }

    private val clockTick = object : Runnable {
        override fun run() {
            b?.clockText?.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            ui.postDelayed(this, 30000)
        }
    }

    private fun tickClock() {
        ui.removeCallbacks(clockTick)
        ui.post(clockTick)
    }

    private fun acc(): Account? =
        Store.getAccount(Store.lastAccount()) ?: Store.accounts().firstOrNull()

    private fun load() {
        val b = b ?: return
        val a = acc()
        if (a == null) { main().profiles(); return }
        b.loading.visibility = View.VISIBLE
        b.scroll.visibility = View.GONE
        b.error.visibility = View.GONE
        b.loadingText.text = getString(R.string.connecting)
        lifecycleScope.launch {
            try {
                val s = Repository.sessionFor(a)
                render(a, s)
                b.loading.visibility = View.GONE
                b.scroll.visibility = View.VISIBLE
                // warm live list in background (tile count + faster first open)
                launch {
                    try {
                        liveCount = Repository.provider?.live(null)?.size ?: 0
                        renderTiles()
                    } catch (e: Exception) { }
                }
            } catch (e: Exception) {
                b.loading.visibility = View.GONE
                b.error.visibility = View.VISIBLE
                b.errorText.text = Net.userMsg(context, e)
            }
        }
    }

    private fun render(a: Account, s: SessionInfo) {
        val b = b ?: return
        b.accName.text = a.name.ifEmpty { a.url }
        val exp = s.expires?.let { Ui.dateFull(it) } ?: getString(R.string.unlimited)
        b.accSub.text = "${s.status} · ${getString(R.string.expires)}: $exp" +
            (if (s.extra.isNotEmpty()) " · ${s.extra}" else "")
        renderTiles()
        renderRails()
        Ui.autoSpan(b.tiles, 200)
    }

    private fun renderTiles() {
        tiles.setData(
            listOf(
                TileRow("📺", getString(R.string.live_tv), if (liveCount >= 0) getString(R.string.channels_d, liveCount) else "", 0),
                TileRow("🎬", getString(R.string.movies), "", 1),
                TileRow("🎭", getString(R.string.series), "", 2),
                TileRow("📖", getString(R.string.guide), "", 3)
            )
        )
    }

    private fun posKey(type: String, id: String) = when (type) {
        "movie" -> "movie:$id"
        "episode" -> "ep:$id"
        else -> "$type:$id"
    }

    private fun renderRails() {
        val b = b ?: return
        val a = acc() ?: return
        hist = Store.history(a.id).take(15)
        favs = Store.favs(a.id).take(15)
        railC.submitList(hist.map { h ->
            val p = Store.getPos(a.id, posKey(h.type, h.id))
            val pct = if (p != null && p.dur > 0) (p.pos * 100 / p.dur).toInt() else -1
            PosterRow("h:${h.type}:${h.id}", h.img, h.name, Ui.dateFull(h.at), pct)
        })
        railF.submitList(favs.map { f -> PosterRow("f:${f.type}:${f.id}", f.img, f.name) })
        val showC = hist.isNotEmpty()
        val showF = favs.isNotEmpty()
        b.lblContinue.visibility = if (showC) View.VISIBLE else View.GONE
        b.railContinue.visibility = if (showC) View.VISIBLE else View.GONE
        b.lblFavs.visibility = if (showF) View.VISIBLE else View.GONE
        b.railFavs.visibility = if (showF) View.VISIBLE else View.GONE
    }

    private fun openTile(i: Int) {
        when (i) {
            0 -> main().open(BrowseFragment.forMode(BrowseFragment.MODE_LIVE))
            1 -> main().open(BrowseFragment.forMode(BrowseFragment.MODE_VOD))
            2 -> main().open(BrowseFragment.forMode(BrowseFragment.MODE_SERIES))
            3 -> main().open(GuideFragment())
        }
    }

    private fun guarded(type: String, id: String, name: String, action: () -> Unit) {
        val a = acc() ?: return action()
        Guard.run(this, a.id, type, id, name, action)
    }

    private fun openHist(i: Int) {
        val h = hist.getOrNull(i) ?: return
        guarded(h.type, h.id, h.name) {
            try {
                val o = JSONObject(h.data)
                when (h.type) {
                    "live" -> {
                        val ch = LiveCh.fromJson(o)
                        PlayerActivity.playLive(requireContext(), ch, Repository.lastLive)
                    }
                    "movie" -> {
                        val v = VodItem.fromJson(o)
                        val p = Store.getPos(acc()!!.id, "movie:${v.id}")
                        PlayerActivity.playVod(requireContext(), v, null, p?.pos ?: 0)
                    }
                    "episode" -> {
                        val ep = com.rgbtv.app.data.EpisodeItem.fromJson(o)
                        val p = Store.getPos(acc()!!.id, "ep:${ep.id}")
                        PlayerActivity.playEpisode(requireContext(), "", listOf(ep), 0, p?.pos ?: 0)
                    }
                    "series" -> main().open(DetailFragment.forItem("series", h.data))
                }
            } catch (e: Exception) { Ui.toast(context, getString(R.string.err_unknown)) }
        }
    }

    private fun openFav(i: Int) {
        val f = favs.getOrNull(i) ?: return
        guarded(f.type, f.id, f.name) {
            if (f.type == "series") {
                main().open(DetailFragment.forItem("series", f.data))
                return@guarded
            }
            try {
                val o = JSONObject(f.data)
                when (f.type) {
                    "live" -> PlayerActivity.playLive(requireContext(), LiveCh.fromJson(o), Repository.lastLive)
                    "movie" -> {
                        val v = VodItem.fromJson(o)
                        val p = Store.getPos(acc()!!.id, "movie:${v.id}")
                        PlayerActivity.playVod(requireContext(), v, null, p?.pos ?: 0)
                    }
                    "episode" -> {
                        val ep = com.rgbtv.app.data.EpisodeItem.fromJson(o)
                        val p = Store.getPos(acc()!!.id, "ep:${ep.id}")
                        PlayerActivity.playEpisode(requireContext(), "", listOf(ep), 0, p?.pos ?: 0)
                    }
                }
            } catch (e: Exception) { Ui.toast(context, getString(R.string.err_unknown)) }
        }
    }

    private fun removeFav(i: Int) {
        val a = acc() ?: return
        val f = favs.getOrNull(i) ?: return
        Store.toggleFav(a.id, f)
        Ui.toast(context, getString(R.string.removed_fav))
        renderRails()
    }

    override fun onDestroyView() {
        ui.removeCallbacks(clockTick)
        b = null
        super.onDestroyView()
    }
}
