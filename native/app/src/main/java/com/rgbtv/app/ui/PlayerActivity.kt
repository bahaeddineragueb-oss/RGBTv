package com.rgbtv.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.rgbtv.app.R
import com.rgbtv.app.data.EpgEvent
import com.rgbtv.app.data.EpisodeItem
import com.rgbtv.app.data.HistItem
import com.rgbtv.app.data.LiveCh
import com.rgbtv.app.data.Store
import com.rgbtv.app.repo.Library
import com.rgbtv.app.data.VodDetail
import com.rgbtv.app.data.VodItem
import com.rgbtv.app.databinding.ActivityPlayerBinding
import com.rgbtv.app.net.Net
import com.rgbtv.app.repo.Repository
import com.rgbtv.app.net.StreamKind
import com.rgbtv.app.net.StreamRef
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Native playback: Media3 ExoPlayer + format fallback + zapping + resume + auto-next. */
class PlayerActivity : AppCompatActivity() {
    private lateinit var b: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private val ui = Handler(Looper.getMainLooper())

    private var kind = "live"
    private var title = ""
    private var sub = ""
    private var img = ""
    private var itemJson = ""
    private var resumeMs = 0L
    private var catchStart = 0L
    private var catchDur = 0
    private var accId = ""
    private var nextEps: List<EpisodeItem> = emptyList()
    private var curEp: EpisodeItem? = null
    private var zapIdx = -1
    private var numBuf = ""
    private var numJob: Runnable? = null

    private var chain: List<StreamKind> = emptyList()
    private var attempt = 0
    private var hadReady = false
    private var controllerVisible = false
    private var ratioIdx = 0
    private var audioIdx = 0
    private var subIdx = 0
    private var countLeft = 0
    private var countJob: Runnable? = null
    private var resolving = false

    companion object {
        private const val E_KIND = "kind"
        private const val E_TITLE = "title"
        private const val E_SUB = "sub"
        private const val E_IMG = "img"
        private const val E_ITEM = "item"
        private const val E_RESUME = "resume"
        private const val E_NEXT = "next"
        private const val E_CS = "cs"
        private const val E_CD = "cd"
        private const val E_ACC = "acc"

        fun playLive(c: Context, ch: LiveCh, zap: List<LiveCh>) {
            Repository.lastLive = zap
            c.startActivity(
                Intent(c, PlayerActivity::class.java)
                    .putExtra(E_KIND, "live").putExtra(E_TITLE, ch.name)
                    .putExtra(E_IMG, ch.logo).putExtra(E_ITEM, ch.toJson().toString())
                    .putExtra(E_ACC, Repository.accountId ?: Store.lastAccount())
            )
        }

        fun playVod(c: Context, item: VodItem, detail: VodDetail?, resumeMs: Long) {
            c.startActivity(
                Intent(c, PlayerActivity::class.java)
                    .putExtra(E_KIND, "vod").putExtra(E_TITLE, detail?.name ?: item.name)
                    .putExtra(E_SUB, listOf(detail?.year ?: item.year, detail?.genre ?: "").filter { it.isNotEmpty() }.joinToString(" · "))
                    .putExtra(E_IMG, detail?.poster ?: item.poster)
                    .putExtra(E_ITEM, item.toJson().toString()).putExtra(E_RESUME, resumeMs)
                    .putExtra(E_ACC, Repository.accountId ?: Store.lastAccount())
            )
        }

        fun playEpisode(c: Context, seriesTitle: String, eps: List<EpisodeItem>, idx: Int, resumeMs: Long) {
            val ep = eps.getOrNull(idx) ?: return
            val rest = eps.drop(idx + 1).take(12)
            val arr = JSONArray()
            rest.forEach { arr.put(it.toJson()) }
            c.startActivity(
                Intent(c, PlayerActivity::class.java)
                    .putExtra(E_KIND, "episode")
                    .putExtra(E_TITLE, "S${ep.season} E${ep.episode} · ${ep.name}")
                    .putExtra(E_SUB, seriesTitle).putExtra(E_IMG, ep.thumb)
                    .putExtra(E_ITEM, ep.toJson().toString()).putExtra(E_RESUME, resumeMs)
                    .putExtra(E_NEXT, arr.toString())
                    .putExtra(E_ACC, Repository.accountId ?: Store.lastAccount())
            )
        }

        fun playCatchup(c: Context, ch: LiveCh, ev: EpgEvent) {
            val dur = ((ev.end - ev.start) / 60).toInt().coerceAtLeast(5)
            c.startActivity(
                Intent(c, PlayerActivity::class.java)
                    .putExtra(E_KIND, "catchup").putExtra(E_TITLE, ev.title.ifEmpty { ch.name })
                    .putExtra(E_SUB, "${ch.name} · ${Ui.clock(ev.start)}")
                    .putExtra(E_IMG, ch.logo).putExtra(E_ITEM, ch.toJson().toString())
                    .putExtra(E_CS, ev.start).putExtra(E_CD, dur)
                    .putExtra(E_ACC, Repository.accountId ?: Store.lastAccount())
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (Store.settings().accent) {
            "green" -> setTheme(R.style.Overlay_Accent_Green)
            "red" -> setTheme(R.style.Overlay_Accent_Red)
            "purple" -> setTheme(R.style.Overlay_Accent_Purple)
            "gold" -> setTheme(R.style.Overlay_Accent_Gold)
            else -> setTheme(R.style.Overlay_Accent_Blue)
        }
        b = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(b.root)
        immersive()

        kind = intent.getStringExtra(E_KIND) ?: "live"
        title = intent.getStringExtra(E_TITLE) ?: ""
        sub = intent.getStringExtra(E_SUB) ?: ""
        img = intent.getStringExtra(E_IMG) ?: ""
        itemJson = intent.getStringExtra(E_ITEM) ?: ""
        resumeMs = intent.getLongExtra(E_RESUME, 0)
        catchStart = intent.getLongExtra(E_CS, 0)
        catchDur = intent.getIntExtra(E_CD, 0)
        accId = intent.getStringExtra(E_ACC) ?: Store.lastAccount() ?: ""
        try {
            val a = JSONArray(intent.getStringExtra(E_NEXT) ?: "[]")
            val l = mutableListOf<EpisodeItem>()
            for (i in 0 until a.length()) l.add(EpisodeItem.fromJson(a.getJSONObject(i)))
            nextEps = l
        } catch (e: Exception) { }
        if (kind == "episode") {
            try { curEp = EpisodeItem.fromJson(JSONObject(itemJson)) } catch (e: Exception) { }
        }

        paintHeader()
        b.playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { vis ->
            controllerVisible = vis == View.VISIBLE
            b.topBar.visibility = vis
            b.sidePanel.visibility = if (kind == "live" || kind == "catchup" || vis != View.VISIBLE) vis else {
                // VOD: hide CH buttons but keep panel
                b.btnChUp.visibility = View.GONE
                b.btnChDown.visibility = View.GONE
                vis
            }
            if (kind != "live") {
                b.btnChUp.visibility = View.GONE
                b.btnChDown.visibility = View.GONE
            }
            if (vis != View.VISIBLE) b.epgPanel.visibility = View.GONE
        })
        b.btnAudio.setOnClickListener { cycleAudio() }
        b.btnSubs.setOnClickListener { cycleSubs() }
        b.btnRatio.setOnClickListener { cycleRatio() }
        b.btnInfo.setOnClickListener { toggleInfo() }
        b.btnChUp.setOnClickListener { zap(1) }
        b.btnChDown.setOnClickListener { zap(-1) }
        b.btnRetry.setOnClickListener { startPlayback() }
        b.btnErrBack.setOnClickListener { finish() }
        b.countdown.setOnClickListener { playNextNow() }
        tickClock()
        boot()
    }

    private fun isLiveMode() = kind == "live" || kind == "catchup"

    private fun paintHeader() {
        b.pTitle.text = title
        b.pSub.text = sub
        b.pSub.visibility = if (sub.isEmpty()) View.GONE else View.VISIBLE
        b.liveBadge.visibility = if (kind == "live") View.VISIBLE else View.GONE
        if (kind != "live") {
            b.btnChUp.visibility = View.GONE
            b.btnChDown.visibility = View.GONE
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Suppress("DEPRECATION")
    private fun immersive() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    override fun onWindowFocusChanged(f: Boolean) {
        super.onWindowFocusChanged(f)
        if (f) immersive()
    }

    /* ---------- boot / session / resolve ---------- */
    private fun boot() {
        showLoading(getString(R.string.loading))
        lifecycleScope.launch {
            val p = try {
                if (Repository.provider == null) {
                    val acc = Store.getAccount(accId) ?: Store.getAccount(Store.lastAccount())
                    if (acc == null) throw Exception(getString(R.string.err_unknown))
                    Repository.sessionFor(acc)
                }
                Repository.provider ?: throw Exception(getString(R.string.err_unknown))
            } catch (e: Exception) {
                showError(Net.userMsg(this@PlayerActivity, e))
                return@launch
            }
            // EPG for live header + info panel
            if (kind == "live") {
                try {
                    val ch = LiveCh.fromJson(JSONObject(itemJson))
                    zapIdx = Repository.lastLive.indexOfFirst { it.id == ch.id }
                    val (now, next) = Repository.nowNext(this@PlayerActivity, ch)
                    if (now != null || next != null) {
                        sub = listOfNotNull(
                            now?.let { "• ${it.title}" },
                            next?.let { "${getString(R.string.next)}: ${it.title} ${Ui.clock(it.start)}" }
                        ).joinToString("  ")
                        paintHeader()
                    }
                } catch (e: Exception) { }
            }
            startPlayback()
        }
    }

    private fun showLoading(t: String) {
        b.loading.visibility = View.VISIBLE
        b.loadingText.text = t
        b.errorPanel.visibility = View.GONE
    }

    private fun showError(msg: String) {
        b.loading.visibility = View.GONE
        b.errorPanel.visibility = View.VISIBLE
        b.errorText.text = msg
        b.btnRetry.requestFocus()
    }

    @Volatile private var pendingRestart = false

    private fun startPlayback() {
        if (resolving) { pendingRestart = true; return }
        resolving = true
        pendingRestart = false
        cancelCountdown()
        showLoading(getString(R.string.loading))
        lifecycleScope.launch {
            try {
                val p = Repository.provider ?: throw Exception(getString(R.string.err_unknown))
                val ref: StreamRef = when (kind) {
                    "live" -> p.resolveLive(LiveCh.fromJson(JSONObject(itemJson)))
                    "vod" -> p.resolveVod(VodItem.fromJson(JSONObject(itemJson)))
                    "episode" -> {
                        val ep = curEp ?: EpisodeItem.fromJson(JSONObject(itemJson))
                        curEp = ep
                        p.resolveEpisode(ep)
                    }
                    else -> {
                        val ch = LiveCh.fromJson(JSONObject(itemJson))
                        p.catchup(ch, catchStart, catchDur)
                            ?: throw Exception(getString(R.string.p_nosource))
                    }
                }
                if (ref.url.isEmpty()) throw Exception(getString(R.string.p_nosource))
                if (Kit.guessExt(ref.url) == "avi") throw AviEx()
                if (pendingRestart) {
                    pendingRestart = false
                    resolving = false
                    startPlayback()
                    return@launch
                }
                resolving = false
                chain = chainFor(ref.kind)
                attempt = 0
                hadReady = false
                ensurePlayer(ref.headers)
                playAttempt(ref.url)
            } catch (e: Exception) {
                resolving = false
                if (e is AviEx) showError(getString(R.string.p_unsupported, "AVI"))
                else showError(Net.userMsg(this@PlayerActivity, e))
            }
        }
    }

    private class AviEx : Exception()

    private fun chainFor(hint: StreamKind): List<StreamKind> {
        val base = if (hint == StreamKind.UNKNOWN) emptyList() else listOf(hint)
        return (base + listOf(StreamKind.HLS, StreamKind.PROGRESSIVE, StreamKind.DASH))
            .distinct().filter { it != StreamKind.UNKNOWN }
    }

    private fun ensurePlayer(headers: Map<String, String>) {
        if (player != null) return
        val ds = DefaultHttpDataSource.Factory()
            .setUserAgent(Net.UA)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
            .setAllowCrossProtocolRedirects(true)
        if (headers.isNotEmpty()) ds.setDefaultRequestProperties(headers)
        val pl = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        pl.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
        pl.addListener(playerListener)
        player = pl
        b.playerView.player = pl
        playerDs = ds
    }

    private var playerDs: DefaultHttpDataSource.Factory? = null

    private fun buildSource(url: String, k: StreamKind): MediaSource {
        val item = MediaItem.fromUri(url)
        val ds = playerDs!!
        return when (k) {
            StreamKind.HLS -> HlsMediaSource.Factory(ds).createMediaSource(item)
            StreamKind.DASH -> DashMediaSource.Factory(ds).createMediaSource(item)
            else -> ProgressiveMediaSource.Factory(ds).createMediaSource(item)
        }
    }

    private fun playAttempt(url: String) {
        val pl = player ?: return
        val k = chain.getOrNull(attempt) ?: StreamKind.PROGRESSIVE
        if (attempt > 0) showLoading(getString(R.string.p_will_retry, attempt + 1, chain.size))
        currentUrl = url
        pl.stop()
        pl.setMediaSource(buildSource(url, k))
        if (!isLiveMode() && resumeMs > 0) {
            pl.seekTo(resumeMs)
            if (resumeMs > 10000) Ui.toast(this, getString(R.string.resumed_from, Ui.fmtDur(resumeMs)))
            resumeMs = 0
        }
        pl.prepare()
        pl.playWhenReady = true
    }

    private var currentUrl = ""

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            val pl = player ?: return
            when (state) {
                Player.STATE_BUFFERING -> {
                    if (!hadReady) showLoading(if (attempt > 0) getString(R.string.p_will_retry, attempt + 1, chain.size) else getString(R.string.loading))
                }
                Player.STATE_READY -> {
                    hadReady = true
                    b.loading.visibility = View.GONE
                    b.errorPanel.visibility = View.GONE
                    b.playerView.showController()
                    pushHistory()
                    startPosSaver()
                }
                Player.STATE_ENDED -> onEnded()
                Player.STATE_IDLE -> { }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (!hadReady && attempt + 1 < chain.size) {
                attempt++
                playAttempt(currentUrl)
            } else {
                showError("${getString(R.string.p_error)}\n${shortErr(error)}")
            }
        }
    }

    private fun shortErr(e: PlaybackException): String {
        val m = e.message ?: ""
        return if (m.length > 160) m.substring(0, 160) + "…" else m
    }

    private fun onEnded() {
        savePos()
        when (kind) {
            "episode" -> {
                curEp?.let {
                    Store.markDone(accId, "ep:${it.id}")
                    val epId = it.id
                    lifecycleScope.launch { Library.clearPos(this@PlayerActivity, accId, "ep:$epId") }
                }
                if (Store.settings().autoNext && nextEps.isNotEmpty()) startCountdown()
                else finish()
            }
            "vod" -> {
                try {
                    val v = VodItem.fromJson(JSONObject(itemJson))
                    lifecycleScope.launch { Library.clearPos(this@PlayerActivity, accId, "movie:${v.id}") }
                } catch (e: Exception) { }
                finish()
            }
            else -> {
                // live/catchup shouldn't end; show retry
                showError(getString(R.string.p_error))
            }
        }
    }

    /* ---------- history / resume ---------- */
    private fun histType() = when (kind) {
        "vod" -> "movie"; "episode" -> "episode"; else -> "live"
    }

    private fun histId(): String {
        return try {
            JSONObject(itemJson).optString("id")
        } catch (e: Exception) { "" }
    }

    private fun pushHistory() {
        try {
            lifecycleScope.launch { Library.pushHistory(this@PlayerActivity, accId, HistItem(histType(), histId(), title, img, itemJson)) }
        } catch (e: Exception) { }
    }

    private fun posKey(): String {
        return when (kind) {
            "vod" -> "movie:${histId()}"
            "episode" -> "ep:${histId()}"
            else -> ""
        }
    }

    private val posTick = object : Runnable {
        override fun run() {
            savePos()
            ui.postDelayed(this, 10000)
        }
    }

    private fun startPosSaver() {
        if (isLiveMode()) return
        ui.removeCallbacks(posTick)
        ui.postDelayed(posTick, 10000)
    }

    private fun savePos() {
        val k = posKey()
        val pl = player
        if (k.isEmpty() || pl == null) return
        val pos = pl.currentPosition
        val dur = pl.duration.takeIf { it > 0 } ?: 0
        if (pos > 5000) lifecycleScope.launch { Library.setPos(this@PlayerActivity, accId, k, pos, dur) }
    }

    /* ---------- audio / subs / ratio / info ---------- */
    private fun langsOf(type: Int): List<String> {
        val out = mutableListOf<String>()
        val t = player?.currentTracks ?: return out
        for (g in t.groups) {
            if (g.type == type) {
                for (i in 0 until g.mediaTrackGroup.length) {
                    val l = g.getTrackFormat(i).language
                    if (!l.isNullOrEmpty() && !out.contains(l)) out.add(l)
                }
            }
        }
        return out
    }

    private fun cycleAudio() {
        val langs = langsOf(C.TRACK_TYPE_AUDIO)
        audioIdx = (audioIdx + 1) % (langs.size + 1)
        val pl = player ?: return
        val lang = if (audioIdx == 0) null else langs[audioIdx - 1]
        pl.trackSelectionParameters = pl.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguage(lang).build()
        Ui.toast(this, "🎧 ${lang ?: getString(R.string.audio_auto)}")
    }

    private fun cycleSubs() {
        val langs = langsOf(C.TRACK_TYPE_TEXT)
        subIdx = (subIdx + 1) % (langs.size + 1)
        val pl = player ?: return
        if (subIdx == 0) {
            pl.trackSelectionParameters = pl.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
            Ui.toast(this, "💬 ${getString(R.string.subs_off)}")
        } else {
            val lang = langs[subIdx - 1]
            pl.trackSelectionParameters = pl.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setPreferredTextLanguage(lang).build()
            Ui.toast(this, "💬 $lang")
        }
    }

    private fun cycleRatio() {
        ratioIdx = (ratioIdx + 1) % 3
        b.playerView.resizeMode = when (ratioIdx) {
            1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            2 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        Ui.toast(this, "${getString(R.string.p_ratio)}: ${when (ratioIdx) { 1 -> "Zoom"; 2 -> "Stretch"; else -> "Fit" }}")
    }

    private fun toggleInfo() {
        if (b.epgPanel.visibility == View.VISIBLE) {
            b.epgPanel.visibility = View.GONE
            return
        }
        b.epgPanel.visibility = View.VISIBLE
        if (kind != "live") {
            b.epgNow.text = title
            b.epgNext.text = sub
            val pl = player
            b.epgProgress.progress = if (pl != null && pl.duration > 0)
                (pl.currentPosition * 100 / pl.duration).toInt().coerceIn(0, 100) else 0
            return
        }
        b.epgNow.text = getString(R.string.loading)
        b.epgNext.text = ""
        lifecycleScope.launch {
            try {
                val ch = LiveCh.fromJson(JSONObject(itemJson))
                val (now, next) = Repository.nowNext(this@PlayerActivity, ch)
                if (now == null && next == null) {
                    b.epgNow.text = getString(R.string.no_epg)
                } else {
                    b.epgNow.text = "• ${now?.title ?: ""} ${now?.let { "(${Ui.clock(it.start)}–${Ui.clock(it.end)})" } ?: ""}"
                    b.epgNext.text = next?.let { "${getString(R.string.next)}: ${it.title} ${Ui.clock(it.start)}" } ?: ""
                    b.epgProgress.progress = if (now != null && now.end > now.start) {
                        (((System.currentTimeMillis() / 1000 - now.start) * 100 / (now.end - now.start)).toInt().coerceIn(0, 100))
                    } else 0
                }
            } catch (e: Exception) {
                b.epgNow.text = getString(R.string.no_epg)
            }
        }
    }

    /* ---------- zapping ---------- */
    private fun zapList(): List<LiveCh> = Repository.lastLive

    private fun zap(d: Int) {
        if (kind != "live") return
        val list = zapList()
        if (list.isEmpty()) return
        if (zapIdx < 0) zapIdx = list.indexOfFirst {
            try { JSONObject(itemJson).optString("id") == it.id } catch (e: Exception) { false }
        }
        zapIdx = ((if (zapIdx < 0) 0 else zapIdx) + d + list.size) % list.size
        zapTo(list[zapIdx])
    }

    private fun zapTo(ch: LiveCh) {
        itemJson = ch.toJson().toString()
        title = ch.name
        img = ch.logo
        b.zapNum.text = if (ch.num > 0) ch.num.toString() else ""
        b.zapName.text = ch.name
        b.zapBanner.visibility = View.VISIBLE
        ui.removeCallbacks(hideBanner)
        ui.postDelayed(hideBanner, 2500)
        lifecycleScope.launch {
            try {
                val (now, _) = Repository.nowNext(this@PlayerActivity, ch)
                sub = now?.let { "• ${it.title}" } ?: ""
                paintHeader()
            } catch (e: Exception) { paintHeader() }
        }
        paintHeader()
        player?.stop()
        b.epgPanel.visibility = View.GONE
        startPlayback()
    }

    private val hideBanner = Runnable { b.zapBanner.visibility = View.GONE }

    private fun digit(d: Int) {
        if (kind != "live" || zapList().isEmpty()) return
        numBuf += d.toString()
        b.zapNum.text = numBuf
        b.zapName.text = ""
        b.zapBanner.visibility = View.VISIBLE
        numJob?.let { ui.removeCallbacks(it) }
        numJob = Runnable {
            val n = numBuf.toIntOrNull()
            numBuf = ""
            ui.removeCallbacks(hideBanner)
            if (n != null) {
                val idx = zapList().indexOfFirst { it.num == n }
                if (idx >= 0) {
                    zapIdx = idx
                    zapTo(zapList()[idx])
                    return@Runnable
                }
            }
            b.zapBanner.visibility = View.GONE
        }
        ui.postDelayed(numJob!!, 1200)
    }

    /* ---------- auto-next ---------- */
    private fun startCountdown() {
        val nxt = nextEps.firstOrNull() ?: run { finish(); return }
        b.nextTitle.text = "S${nxt.season} E${nxt.episode} · ${nxt.name}"
        b.countdown.visibility = View.VISIBLE
        countLeft = 10
        b.nextSec.text = countLeft.toString()
        countJob?.let { ui.removeCallbacks(it) }
        countJob = object : Runnable {
            override fun run() {
                countLeft--
                if (countLeft <= 0) playNextNow()
                else {
                    b.nextSec.text = countLeft.toString()
                    ui.postDelayed(this, 1000)
                }
            }
        }
        ui.postDelayed(countJob!!, 1000)
    }

    private fun cancelCountdown() {
        countJob?.let { ui.removeCallbacks(it) }
        countJob = null
        if (::b.isInitialized) b.countdown.visibility = View.GONE
    }

    private fun playNextNow() {
        val nxt = nextEps.firstOrNull() ?: run { finish(); return }
        nextEps = nextEps.drop(1)
        cancelCountdown()
        curEp = nxt
        itemJson = nxt.toJson().toString()
        title = "S${nxt.season} E${nxt.episode} · ${nxt.name}"
        img = nxt.thumb
        resumeMs = 0
        paintHeader()
        player?.stop()
        startPlayback()
    }

    /* ---------- clock ---------- */
    private val clockTick = object : Runnable {
        override fun run() {
            b.clock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            ui.postDelayed(this, 30000)
        }
    }

    private fun tickClock() {
        ui.removeCallbacks(clockTick)
        ui.post(clockTick)
    }

    /* ---------- keys ---------- */
    override fun onKeyDown(code: Int, ev: KeyEvent?): Boolean {
        when (code) {
            KeyEvent.KEYCODE_BACK -> {
                if (b.countdown.visibility == View.VISIBLE) {
                    cancelCountdown()
                    finish()
                    return true
                }
                if (b.epgPanel.visibility == View.VISIBLE) {
                    b.epgPanel.visibility = View.GONE
                    return true
                }
                return super.onKeyDown(code, ev)
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (kind == "live" && !controllerVisible) {
                    zap(1); return true
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (kind == "live" && !controllerVisible) {
                    zap(-1); return true
                }
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (!controllerVisible && b.errorPanel.visibility != View.VISIBLE) {
                    b.playerView.showController()
                    return true
                }
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                player?.let { it.playWhenReady = !it.playWhenReady; return true }
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                player?.play(); return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                player?.pause(); return true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                if (kind == "live") {
                    zap(1); return true
                }
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                if (kind == "live") {
                    zap(-1); return true
                }
            }
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                digit(code - KeyEvent.KEYCODE_0); return true
            }
        }
        return super.onKeyDown(code, ev)
    }

    override fun onPause() {
        super.onPause()
        savePos()
        try { player?.pause() } catch (e: Exception) { }
    }

    override fun onDestroy() {
        savePos()
        ui.removeCallbacksAndMessages(null)
        try {
            player?.removeListener(playerListener)
            player?.release()
        } catch (e: Exception) { }
        player = null
        super.onDestroy()
    }
}
