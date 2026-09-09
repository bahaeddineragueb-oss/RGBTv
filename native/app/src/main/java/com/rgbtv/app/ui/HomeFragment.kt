package com.rgbtv.app.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
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
import com.rgbtv.app.img.Images
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
    private lateinit var railC: PosterAdapter
    private lateinit var railL: ChannelAdapter
    private lateinit var railT: PosterAdapter
    private lateinit var railS: PosterAdapter
    private lateinit var railF: PosterAdapter
    private var hist: List<HistItem> = emptyList()
    private var favs: List<FavItem> = emptyList()
    private var liveList: List<LiveCh> = emptyList()
    private var vodList: List<VodItem> = emptyList()
    private var seriesList: List<SeriesItem> = emptyList()
    private var heroKind = ""
    private var heroVod: VodItem? = null
    private var heroSeries: SeriesItem? = null
    private var heroLive: LiveCh? = null
    private var pulse: ObjectAnimator? = null

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val v = FragmentHomeBinding.inflate(i, c, false)
        b = v
        return v.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        val b = b ?: return
        b.railContinue.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        b.railLiveNow.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        b.railTrending.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        b.railSeries.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        b.railFavs.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        railC = PosterAdapter(onClick = { openHist(it) })
        railL = ChannelAdapter(onClick = { openLive(it) }, onFocus = {}, onLong = {})
        railT = PosterAdapter(onClick = { openVod(it) })
        railS = PosterAdapter(onClick = { openSeries(it) })
        railF = PosterAdapter(onClick = { openFav(it) }, onLong = { removeFav(it) })
        b.railContinue.adapter = railC
        b.railLiveNow.adapter = railL
        b.railTrending.adapter = railT
        b.railSeries.adapter = railS
        b.railFavs.adapter = railF
        Ui.focusScale(b.heroCard)
        b.heroCard.setOnClickListener { heroWatch() }
        b.btnWatch.setOnClickListener { heroWatch() }
        b.btnHeroFav.setOnClickListener { heroFav() }
        b.btnSwitch.setOnClickListener { main().profiles() }
        b.btnSettings.setOnClickListener { main().open(SettingsFragment()) }
        b.btnRetry.setOnClickListener { load() }
        b.btnSwitchErr.setOnClickListener { main().profiles() }
        pulse = ObjectAnimator.ofFloat(b.skeleton, "alpha", 1f, 0.5f, 1f)
        pulse?.duration = 1200
        pulse?.repeatCount = ValueAnimator.INFINITE
        pulse?.start()
        tickClock()
        load()
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as MainActivity).select("home")
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
        b.skeleton.visibility = View.VISIBLE
        b.scroll.visibility = View.GONE
        b.error.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val s = Repository.sessionFor(a)
                renderHeader(a, s)
                renderRails()
                b.skeleton.visibility = View.GONE
                b.scroll.visibility = View.VISIBLE
                launch {
                    try {
                        loadDiscovery()
                    } catch (e: Exception) { }
                }
            } catch (e: Exception) {
                b.skeleton.visibility = View.GONE
                b.error.visibility = View.VISIBLE
                b.errorText.text = Net.userMsg(context, e)
            }
        }
    }

    private fun renderHeader(a: Account, s: SessionInfo) {
        val b = b ?: return
        b.accName.text = a.name.ifEmpty { a.url }
        val exp = s.expires?.let { Ui.dateFull(it) } ?: getString(R.string.unlimited)
        b.accSub.text = "${s.status} · ${getString(R.string.expires)}: $exp" +
            (if (s.extra.isNotEmpty()) " · ${s.extra}" else "")
    }

    private fun ratingOf(r: String): Double = r.toDoubleOrNull() ?: -1.0

    private suspend fun loadDiscovery() {
        val p = Repository.provider ?: return
        val live = try { p.live(null) } catch (e: Exception) { emptyList() }
        val vod = try { p.vod(null) } catch (e: Exception) { emptyList() }
        val series = try { p.series(null) } catch (e: Exception) { emptyList() }
        Repository.lastLive = live
        liveList = live.take(15)
        vodList = vod.sortedByDescending { it.added }.take(15)
        seriesList = series.sortedByDescending { it.added }.take(15)
        paintHero(vod, series, live)
        val bb = b ?: return
        railL.submitList(liveList)
        railT.submitList(vodList.map { v ->
            PosterRow(
                "movie:${v.id}", v.poster, v.name,
                listOf(v.year, if (v.rating.isNotEmpty()) "★ ${v.rating}" else "")
                    .filter { it.isNotEmpty() }.joinToString(" · ")
            )
        })
        railS.submitList(seriesList.map { s ->
            PosterRow(
                "series:${s.id}", s.poster, s.name,
                listOf(s.year, if (s.rating.isNotEmpty()) "★ ${s.rating}" else "")
                    .filter { it.isNotEmpty() }.joinToString(" · ")
            )
        })
        bb.lblLiveNow.visibility = if (liveList.isNotEmpty()) View.VISIBLE else View.GONE
        bb.railLiveNow.visibility = if (liveList.isEmpty()) View.GONE else View.VISIBLE
        bb.lblTrending.visibility = if (vodList.isNotEmpty()) View.VISIBLE else View.GONE
        bb.railTrending.visibility = if (vodList.isEmpty()) View.GONE else View.VISIBLE
        bb.lblSeries.visibility = if (seriesList.isNotEmpty()) View.VISIBLE else View.GONE
        bb.railSeries.visibility = if (seriesList.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun paintHero(vod: List<VodItem>, series: List<SeriesItem>, live: List<LiveCh>) {
        val b = b ?: return
        val topSeries = series.filter { it.backdrop.isNotEmpty() || it.poster.isNotEmpty() }
            .maxByOrNull { ratingOf(it.rating) }
        val topVod = vod.filter { it.poster.isNotEmpty() }.maxByOrNull { ratingOf(it.rating) }
        if (topSeries != null && (topVod == null || ratingOf(topSeries.rating) >= ratingOf(topVod.rating))) {
            heroKind = "series"
            heroSeries = topSeries
            Images.load(b.heroBg, topSeries.backdrop.ifEmpty { topSeries.poster }, Images.Kind.BACKDROP)
            b.heroCat.text = getString(R.string.series).uppercase(Locale.getDefault())
            b.heroTitle.text = topSeries.name
            b.heroMeta.text = listOf(
                if (topSeries.rating.isNotEmpty()) "★ ${topSeries.rating}" else "",
                topSeries.year, topSeries.genre
            ).filter { it.isNotEmpty() }.joinToString(" · ")
            b.heroPlot.text = topSeries.plot
            b.heroPlot.visibility = if (topSeries.plot.isEmpty()) View.GONE else View.VISIBLE
        } else if (topVod != null) {
            heroKind = "movie"
            heroVod = topVod
            Images.load(b.heroBg, topVod.poster, Images.Kind.BACKDROP)
            b.heroCat.text = getString(R.string.movies).uppercase(Locale.getDefault())
            b.heroTitle.text = topVod.name
            b.heroMeta.text = listOf(
                if (topVod.rating.isNotEmpty()) "★ ${topVod.rating}" else "",
                topVod.year, topVod.genre
            ).filter { it.isNotEmpty() }.joinToString(" · ")
            b.heroPlot.text = topVod.plot
            b.heroPlot.visibility = if (topVod.plot.isEmpty()) View.GONE else View.VISIBLE
        } else if (live.isNotEmpty()) {
            val ch = live.first()
            heroKind = "live"
            heroLive = ch
            Images.load(b.heroBg, ch.logo, Images.Kind.BACKDROP)
            b.heroCat.text = getString(R.string.live_tv).uppercase(Locale.getDefault())
            b.heroTitle.text = ch.name
            b.heroMeta.text = "● ${getString(R.string.live_now)}"
            b.heroPlot.visibility = View.GONE
        } else {
            heroKind = ""
            b.heroTitle.text = "RGBTv"
            b.heroMeta.text = ""
            b.heroPlot.visibility = View.GONE
        }
        paintHeroFav()
    }

    private fun heroWatch() {
        val a = acc() ?: return
        when (heroKind) {
            "movie" -> {
                val v = heroVod ?: return
                Guard.run(this, a.id, "movie", v.id, v.name) {
                    val pos = Store.getPos(a.id, "movie:${v.id}")
                    PlayerActivity.playVod(requireContext(), v, null, pos?.pos ?: 0)
                }
            }
            "series" -> {
                val s = heroSeries ?: return
                Guard.run(this, a.id, "series", s.id, s.name) {
                    main().open(DetailFragment.forItem("series", s.toJson().toString()))
                }
            }
            "live" -> {
                val ch = heroLive ?: return
                Guard.run(this, a.id, "live", ch.id, ch.name) {
                    PlayerActivity.playLive(requireContext(), ch, Repository.lastLive)
                }
            }
        }
    }

    private fun heroFav() {
        val a = acc() ?: return
        val item = when (heroKind) {
            "movie" -> {
                val v = heroVod ?: return
                FavItem("movie", v.id, v.name, v.poster, v.toJson().toString())
            }
            "series" -> {
                val s = heroSeries ?: return
                FavItem("series", s.id, s.name, s.poster, s.toJson().toString())
            }
            "live" -> {
                val ch = heroLive ?: return
                FavItem("live", ch.id, ch.name, ch.logo, ch.toJson().toString())
            }
            else -> return
        }
        Guard.run(this, a.id, item.type, item.id, item.name) {
            val now = Store.toggleFav(a.id, item)
            Ui.toast(context, getString(if (now) R.string.added_fav else R.string.removed_fav))
            paintHeroFav()
            renderRails()
        }
    }

    private fun paintHeroFav() {
        val b = b ?: return
        val a = acc() ?: return
        val fav = when (heroKind) {
            "movie" -> heroVod?.let { Store.isFav(a.id, "movie", it.id) } ?: false
            "series" -> heroSeries?.let { Store.isFav(a.id, "series", it.id) } ?: false
            "live" -> heroLive?.let { Store.isFav(a.id, "live", it.id) } ?: false
            else -> false
        }
        b.btnHeroFav.text = (if (fav) "★ " else "＋ ") + getString(R.string.my_list)
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

    private fun guarded(type: String, id: String, name: String, action: () -> Unit) {
        val a = acc() ?: return action()
        Guard.run(this, a.id, type, id, name, action)
    }

    private fun openLive(ch: LiveCh) {
        guarded("live", ch.id, ch.name) {
            PlayerActivity.playLive(requireContext(), ch, Repository.lastLive)
        }
    }

    private fun openVod(i: Int) {
        val v = vodList.getOrNull(i) ?: return
        guarded("movie", v.id, v.name) {
            main().open(DetailFragment.forItem("movie", v.toJson().toString()))
        }
    }

    private fun openSeries(i: Int) {
        val s = seriesList.getOrNull(i) ?: return
        guarded("series", s.id, s.name) {
            main().open(DetailFragment.forItem("series", s.toJson().toString()))
        }
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
        pulse?.cancel()
        pulse = null
        b = null
        super.onDestroyView()
    }
}
