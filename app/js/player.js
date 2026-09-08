/* RGBTv — Player: native <video> (webOS handles HLS/TS/MP4/MKV natively) with hls.js fallback */
var Player = (function () {
  var video, hls = null, osdTimer = null, current = null, playlist = [], index = -1, ratioMode = 0, RATIOS = ['Fit', 'Fill', 'Stretch'];
  var onEnded = null, posKey = null, posTimer = null, seekAccum = 0, seekTimer = null, numBuf = '', numTimer = null, zapOpen = false, trackMenuOpen = false;
  var els = {};
  /* auto-reconnect state */
  var rc = { attempts: 0, timer: null, stallTimer: null, lastTime: -1, lastProgress: 0, lastOpt: null, active: false };
  function T(k, v) { return I18n.t(k, v); }
  /* Live: a real IPTV stall almost never recovers by itself after ~6 s of no frames -> reconnect fast (IBO/TiviMate do ~5 s).
     Start: if the first frame is not there after 12 s (live) the link is dead or the panel is overloaded -> retry now instead of
     showing "Loading…" for a minute. First retries are quick; back-off only after several failures. */
  var RC_MAX = 10, RC_DELAYS = [400, 800, 1500, 2500, 4000, 6000, 8000, 10000, 15000, 20000], STALL_LIVE = 6000, STALL_VOD = 20000, START_LIVE = 12000, START_VOD = 45000;
  var ICON_PLAY = '<svg viewBox="0 0 24 24" width="36" height="36" fill="currentColor"><path d="M7 4v16l14-8z"/></svg>', ICON_PAUSE = '<svg viewBox="0 0 24 24" width="36" height="36" fill="currentColor"><path d="M6 5h4v14H6zm8 0h4v14h-4z"/></svg>';

  function init() {
    video = U.$('#video'); try { video.preload = 'auto'; } catch (e) { }
    ['osd', 'osd-title', 'osd-sub', 'osd-logo', 'osd-clock', 'osd-played', 'osd-buffer', 'osd-cur', 'osd-dur', 'osd-play', 'player-loading', 'player-loading-text', 'player-error', 'zap-list', 'channel-number', 'track-menu', 'osd-fav', 'osd-ratio', 'osd-list-btn', 'osd-audio', 'osd-subs', 'osd-quality', 'stats-box', 'zap-preview', 'osd-stats', 'osd-epg', 'autonext', 'an-bar', 'an-count', 'an-title'].forEach(function (id) { els[id] = document.getElementById(id); });
    /* Buffering indicator: webOS fires 'waiting'/'stalled' very often on live TS/HLS even while the picture keeps
       moving, and sometimes never fires 'playing' afterwards -> spinner stuck in the middle. So: show it only if the
       stall lasts > 800ms, and hide it as soon as currentTime advances again (real progress), not only on 'playing'. */
    var bufTimer = null;
    function bufferingSoon() { if (bufTimer || !current) return; bufTimer = setTimeout(function () { bufTimer = null; if (current && !video.paused && Date.now() - rc.lastProgress > 700) loading(true, T('buffering')); }, 800); }
    function bufferingDone() { clearTimeout(bufTimer); bufTimer = null; if (els['player-loading'].classList.contains('show') && /Buffering|Loading|Opening/.test(els['player-loading-text'].textContent)) loading(false); }
    video.addEventListener('waiting', bufferingSoon);
    video.addEventListener('stalled', bufferingSoon);
    video.addEventListener('playing', function () { bufferingDone(); error(null); els['osd-play'].innerHTML = ICON_PAUSE; });
    video.addEventListener('canplay', bufferingDone);
    video.addEventListener('timeupdate', function () { if (video.currentTime !== rc.lastTime && video.currentTime > 0) bufferingDone(); });
    video.addEventListener('loadedmetadata', updateQualityBadge);
    video.addEventListener('resize', updateQualityBadge);
    video.addEventListener('pause', function () { els['osd-play'].innerHTML = ICON_PLAY; });
    video.addEventListener('timeupdate', updateProgress);
    video.addEventListener('progress', updateProgress);
    video.addEventListener('ended', function () { if (onEnded) onEnded(); });
    video.addEventListener('error', function () {
      var code = video.error && video.error.code;
      if (hls) return; // hls.js reports its own errors
      // Try hls.js fallback if native failed with an m3u8
      if (current && /\.m3u8(\?|$)/i.test(current.url) && window.Hls && Hls.isSupported() && !current._triedHls) { current._triedHls = true; current._engine = 'hls'; startHls(current.url); return; }
      // code 4 = SRC_NOT_SUPPORTED (often a dead link / 404) ; 2 = NETWORK ; 3 = DECODE
      scheduleReconnect(T('p.error') + (code ? ' (code ' + code + ')' : ''));
    });
    video.addEventListener('playing', function () { rc.attempts = 0; rc.lastProgress = Date.now(); });
    video.addEventListener('timeupdate', function () { if (video.currentTime !== rc.lastTime) { rc.lastTime = video.currentTime; rc.lastProgress = Date.now(); } });
    // bytes still arriving (buffer growing) also counts as progress — big MKV/MP4 files can take a while before first frame
    video.addEventListener('progress', function () { try { var b = video.buffered, end = b.length ? b.end(b.length - 1) : 0; if (end !== rc.lastBuf) { rc.lastBuf = end; rc.lastProgress = Date.now(); } } catch (e) { } });
    video.addEventListener('loadedmetadata', function () { rc.lastProgress = Date.now(); rc.started = true; });
    // stall watchdog: no progress for N seconds while supposed to be playing -> reconnect
    setInterval(function () {
      if (!current || !rc.active || video.ended || rc.timer || rc.userPaused) return;
      var isLive = current.type === 'live';
      if (!rc.started && els['player-loading'].classList.contains('show')) { var secs = Math.round((Date.now() - rc.lastStart) / 1000); if (secs >= 4) loading(true, T('opening', { s: secs })); }
      var limit = isLive ? (rc.started ? STALL_LIVE : START_LIVE) : (rc.started ? STALL_VOD : START_VOD);
      // paused by the user is fine; paused because nothing ever loaded is a stall
      if (video.paused && video.readyState >= 3) return;
      if (Date.now() - rc.lastProgress > limit && video.readyState < 3) scheduleReconnect(T('p.stalled'));
      // picture frozen although the element claims readyState 4 (webOS TS pipeline quirk): no currentTime advance for 2x limit
      else if (rc.started && !video.paused && Date.now() - rc.lastProgress > limit * 2) scheduleReconnect(T('p.stalled'));
    }, 1500);
    // network back -> immediate retry
    window.addEventListener('online', function () { if (rc.timer && current) { clearTimeout(rc.timer); rc.timer = null; doReconnect(); } });
    setInterval(function () { if (els['osd-clock']) els['osd-clock'].textContent = U.clock(); }, 1000);
  }

  /* Resolution badge in OSD (4K / FHD / HD / SD) from the decoded video size */
  function updateQualityBadge() {
    var w = video.videoWidth, h = video.videoHeight, el = els['osd-quality']; if (!el) return;
    var b = [], nm = current ? String((current.title || '') + ' ' + (current.subtitle || '') + ' ' + (current.name || '')) : '';
    if (w && h) { var uhd = (w >= 3800 || h >= 2100); b.push('<span class="osd-badge' + (uhd ? ' uhd' : '') + '">' + (uhd ? '4K UHD' : (h >= 1000 || w >= 1900) ? 'FHD' : (h >= 700 || w >= 1200) ? 'HD' : 'SD') + '</span>'); b.push('<span class="osd-badge">' + w + '×' + h + '</span>'); }
    else if (/\b(4k|uhd|2160p)\b/i.test(nm)) b.push('<span class="osd-badge uhd">4K</span>');
    if (/\b(hdr|dolby ?vision)\b/i.test(nm)) b.push('<span class="osd-badge hdr">HDR</span>');
    var subs = 0, auds = 0; try { subs = (hls && hls.subtitleTracks ? hls.subtitleTracks.length : 0) || (video.textTracks ? video.textTracks.length : 0); auds = (hls && hls.audioTracks ? hls.audioTracks.length : 0) || (video.audioTracks ? video.audioTracks.length : 0); } catch (e) { }
    if (subs) b.push('<span class="osd-badge">CC ' + subs + '</span>'); if (auds > 1) b.push('<span class="osd-badge">♪ ' + auds + '</span>');
    if (!b.length) { el.innerHTML = ''; el.style.display = 'none'; return; }
    el.className = 'osd-badges'; el.innerHTML = b.join(''); el.style.display = '';
  }
  function loading(on, txt) { els['player-loading'].classList.toggle('show', !!on); if (txt) els['player-loading-text'].textContent = txt; }
  function error(msg) { els['player-error'].classList.toggle('show', !!msg); if (msg) els['player-error'].textContent = msg; }

  function destroyHls() { if (hls) { try { hls.destroy(); } catch (e) { } hls = null; } }
  function startHls(url) {
    destroyHls();
    var isLive = current && current.type === 'live';
    hls = new Hls({
      enableWorker: false, lowLatencyMode: false, backBufferLength: 30,
      maxBufferLength: isLive ? 20 : 30, maxMaxBufferLength: isLive ? 40 : 60, maxBufferHole: 1, nudgeMaxRetry: 8,
      liveSyncDurationCount: 3, liveMaxLatencyDurationCount: 8, liveDurationInfinity: true,
      manifestLoadingTimeOut: 8000, manifestLoadingMaxRetry: 2, manifestLoadingRetryDelay: 500,
      levelLoadingTimeOut: 8000, levelLoadingMaxRetry: 4, levelLoadingRetryDelay: 500,
      fragLoadingTimeOut: isLive ? 8000 : 20000, fragLoadingMaxRetry: 4, fragLoadingRetryDelay: 500, fragLoadingMaxRetryTimeout: 4000,
      startFragPrefetch: true, testBandwidth: false, abrEwmaDefaultEstimate: 5e6, startLevel: -1
    });
    hls.loadSource(url); hls.attachMedia(video);
    hls.on(Hls.Events.MANIFEST_PARSED, function () { video.play().catch(function () { }); setTimeout(updateQualityBadge, 1500); });
    hls.on(Hls.Events.ERROR, function (ev, data) {
      if (!data.fatal) return;
      if (data.type === Hls.ErrorTypes.NETWORK_ERROR) {
        if (/manifest/i.test(data.details)) scheduleReconnect(T('p.cannotLoad') + ' (' + data.details + ')');
        else { hls._softRestarts = (hls._softRestarts || 0) + 1; if (hls._softRestarts > 2) scheduleReconnect(T('p.stalled')); else hls.startLoad(-1); }
      }
      else if (data.type === Hls.ErrorTypes.MEDIA_ERROR) { if (!hls._recovered) { hls._recovered = true; hls.recoverMediaError(); } else scheduleReconnect('Media error'); }
      else scheduleReconnect('Stream error: ' + data.details);
    });
  }

  /* ---- auto-reconnect ---- */
  function scheduleReconnect(reason) {
    if (!current || !rc.active || rc.timer) return;
    if (rc.attempts >= RC_MAX) { rc.active = false; loading(false); error(reason + ' ' + T('p.retryHint', { max: RC_MAX })); return; }
    var delay = RC_DELAYS[Math.min(rc.attempts, RC_DELAYS.length - 1)]; rc.attempts++;
    error(null); loading(true, T('reconnecting', { n: rc.attempts, max: RC_MAX }));
    if (rc.attempts > 1) UI.toast(T('p.interrupted', { n: rc.attempts, max: RC_MAX }), 2500, '↻');
    rc.timer = setTimeout(function () { rc.timer = null; doReconnect(); }, delay);
  }
  function doReconnect() {
    if (!current) return;
    var item = current, opt = rc.lastOpt || {}, resumeAt = (item.type !== 'live' && item.type !== 'catchup' && video.currentTime > 5) ? video.currentTime : 0;
    var keepAttempts = rc.attempts;
    rc.started = false; rc.lastBuf = -1; rc.lastStart = Date.now();
    destroyHls(); try { video.pause(); video.removeAttribute('src'); video.load(); } catch (e) { }
    // re-resolve the URL (Stalker links expire; Xtream tokens may rotate)
    // 1st/2nd retry: reuse the same URL (a Stalker create_link or Xtream round-trip costs 0.5-3 s on a busy panel and is
    // rarely the problem); from the 3rd attempt on re-resolve because portal links expire / tokens rotate.
    var reuse = item.url && (keepAttempts <= 2 || (opt.url && item.type === 'catchup'));
    var p = reuse ? Promise.resolve(item.url) : App.provider.streamUrl(item);
    p.then(function (url) {
      if (current !== item) return; item.url = url; startSource(url, item);
      if (resumeAt) { var once = function () { video.removeEventListener('loadedmetadata', once); try { video.currentTime = resumeAt; } catch (e) { } }; video.addEventListener('loadedmetadata', once); }
      rc.attempts = keepAttempts; rc.lastProgress = Date.now();
    }).catch(function () { rc.attempts = keepAttempts; scheduleReconnect('Cannot resolve stream'); });
  }
  function startSource(url, item) {
    var eng = Store.settings().engine, isHlsUrl = /\.m3u8(\?|$)/i.test(url), hlsOk = isHlsUrl && window.Hls && Hls.isSupported();
    var useHls = hlsOk && (eng === 'hlsjs' || (eng === 'auto' && !video.canPlayType('application/vnd.apple.mpegurl')) || item._engine === 'hls');
    if (useHls) { item._triedHls = true; item._engine = 'hls'; startHls(url); }
    else { item._engine = 'native'; video.src = url; video.load(); var pp = video.play(); if (pp && pp.catch) pp.catch(function () { }); }
  }
  function showLoading(txt) { error(null); loading(true, txt || T('loading')); }
  function cancelReconnect() { clearTimeout(rc.timer); rc.timer = null; rc.attempts = 0; }

  /* play(item, {list, index, url, resume}) */
  function play(item, opt) {
    opt = opt || {};
    cancelReconnect(); rc.active = true; rc.userPaused = false; rc.lastOpt = opt; rc.lastProgress = Date.now(); rc.lastTime = -1;
    current = item; current._triedHls = false; current._engine = null;
    if (opt.list) { playlist = opt.list; index = opt.index != null ? opt.index : playlist.indexOf(item); }
    error(null); loading(true, T('loading'));
    stop(false);
    rc.active = true; rc.attempts = 0; rc.lastProgress = Date.now(); rc.started = false; rc.lastBuf = -1; rc.lastStart = Date.now();
    els['osd-title'].textContent = item.title || item.name || '';
    els['osd-sub'].textContent = item.subtitle || ''; if (els['osd-quality']) { els['osd-quality'].innerHTML = ''; els['osd-quality'].style.display = 'none'; }
    els['osd-epg'].classList.remove('show'); els['osd-epg'].innerHTML = ''; hideAutoNext();
    els['osd-logo'].style.backgroundImage = item.logo ? 'url("' + item.logo + '")' : 'none';
    els['osd-list-btn'].style.display = item.type === 'live' ? '' : 'none';
    updateFavBtn();
    var isLive = item.type === 'live';
    U.$('.osd-progress').style.visibility = isLive ? 'hidden' : 'visible';
    U.$('.osd-times').style.visibility = isLive ? 'hidden' : 'visible';
    posKey = (isLive || item.type === 'catchup') ? null : (item.type + ':' + item.id);
    showOsd();

    var p = opt.url ? Promise.resolve(opt.url) : App.provider.streamUrl(item);
    return p.then(function (url) {
      if (!url) throw new Error('No stream URL');
      item.url = url; current.url = url;
      startSource(url, item);
      if (opt.resume && posKey) {
        var saved = Store.getPos(App.account.id, posKey);
        if (saved && saved.pos > 10) { var once = function () { video.removeEventListener('loadedmetadata', once); try { video.currentTime = saved.pos; } catch (e) { } }; video.addEventListener('loadedmetadata', once); }
      }
      if (posKey) { clearInterval(posTimer); posTimer = setInterval(savePos, 5000); }
      if (item.type !== 'catchup') Store.pushHistory(App.account.id, { type: item.type, id: item.id, name: item.name, logo: item.logo, poster: item.poster, seriesId: item.seriesId, ext: item.ext, cmd: item.cmd, url: item.type === 'm3u' ? url : undefined, catId: item.catId, season: item.season, episode: item.episode });
    }).catch(function (e) { scheduleReconnect('Cannot start stream: ' + e.message); });
  }
  function savePos() { if (posKey && video.duration && !isNaN(video.duration)) Store.setPos(App.account.id, posKey, video.currentTime, video.duration); }
  function stop(clearCurrent, keepPipeline) {
    cancelReconnect(); rc.active = false;
    savePos(); clearInterval(posTimer);
    destroyHls();
    // keepPipeline: a new source follows immediately — setting src replaces the pipeline anyway; an extra load() here
    // made webOS destroy and rebuild the media pipeline twice per channel change (≈0.5-1.5 s lost on older TVs)
    try { video.pause(); if (!keepPipeline) { video.removeAttribute('src'); while (video.firstChild) video.removeChild(video.firstChild); video.load(); } } catch (e) { }
    if (clearCurrent !== false) current = null;
    loading(false);
  }
  function togglePlay() { if (!rc.active && current && els['player-error'].classList.contains('show')) { rc.active = true; rc.attempts = 0; error(null); loading(true, T('retrying')); doReconnect(); return; } if (video.paused) { rc.userPaused = false; video.play().catch(function () { }); } else { rc.userPaused = true; video.pause(); } showOsd(); }
  function seek(delta) {
    if (!current || current.type === 'live' || !isFinite(video.duration)) return;
    seekAccum += delta; showOsd();
    var target = U.clamp(video.currentTime + seekAccum, 0, video.duration - 1);
    els['osd-cur'].textContent = U.fmtTime(target) + (seekAccum ? ' (' + (seekAccum > 0 ? '+' : '') + seekAccum + 's)' : '');
    els['osd-played'].style.width = (target / video.duration * 100) + '%';
    clearTimeout(seekTimer); seekTimer = setTimeout(function () { video.currentTime = target; seekAccum = 0; }, 500);
  }
  function updateProgress() {
    if (!video.duration || !isFinite(video.duration)) return;
    if (!seekAccum) { els['osd-cur'].textContent = U.fmtTime(video.currentTime); els['osd-played'].style.width = (video.currentTime / video.duration * 100) + '%'; }
    els['osd-dur'].innerHTML = U.fmtTime(video.duration) + '<span class="osd-rem">−' + U.fmtTime(Math.max(0, video.duration - video.currentTime)) + '</span>';
    try { if (video.buffered.length) els['osd-buffer'].style.width = (video.buffered.end(video.buffered.length - 1) / video.duration * 100) + '%'; } catch (e) { }
  }
  function showOsd(persist) {
    els.osd.classList.add('show'); clearTimeout(osdTimer);
    if (!persist) osdTimer = setTimeout(hideOsd, 5000);
    if (current && current.type === 'live') fillMiniEpg();
  }
  /* mini EPG inside the OSD (live): NOW with progress + NEXT */
  var epgCache = {};
  function fillMiniEpg() {
    var ch = current, box = els['osd-epg']; if (!ch || !App.provider || !App.provider.shortEPG) return;
    var paint = function (list) {
      if (current !== ch) return; var now = Date.now() / 1000, cur = null, nxt = null;
      list.forEach(function (e) { if (e.start <= now && e.end > now) cur = e; else if (e.start > now && !nxt) nxt = e; });
      if (!cur && !nxt) { box.classList.remove('show'); return; }
      box.innerHTML = '<div class="oe-now"><span class="lbl">' + U.esc(T('nowLbl')) + '</span><span>' + U.esc(cur ? cur.title : '—') + '</span>' + (cur ? '<span class="oe-time">' + U.hm(cur.start) + ' – ' + U.hm(cur.end) + ' · ' + Math.max(0, Math.round((cur.end - now) / 60)) + ' ' + U.esc(T('home.min')) + '</span>' : '') + '</div><div class="oe-bar"><i style="width:' + (cur ? Math.round((now - cur.start) / (cur.end - cur.start) * 100) : 0) + '%"></i></div><div class="oe-next"><span class="lbl">' + U.esc(T('next')) + '</span>' + (nxt ? U.hm(nxt.start) + '  ' + U.esc(nxt.title) : '—') + '</div>';
      box.classList.add('show');
    };
    var c = epgCache[ch.id]; if (c && Date.now() - c.at < 5 * 60000) { paint(c.list); return; }
    App.provider.shortEPG(ch.epgId || ch.id, 4).then(function (list) { epgCache[ch.id] = { at: Date.now(), list: list || [] }; paint(list || []); }).catch(function () { });
  }
  /* auto-play next episode: 10 s circular countdown */
  var an = { timer: null, left: 0, onPlay: null, onCancel: null };
  function autoNext(title, onPlay, onCancel) {
    hideAutoNext(); an.left = 10; an.onPlay = onPlay; an.onCancel = onCancel;
    els['an-title'].textContent = title || ''; els['an-count'].textContent = '10'; els['an-bar'].style.transition = 'none'; els['an-bar'].style.strokeDashoffset = '0';
    els.autonext.classList.add('show'); showOsd(true);
    setTimeout(function () { els['an-bar'].style.transition = 'stroke-dashoffset 10s linear'; els['an-bar'].style.strokeDashoffset = '327'; }, 50);
    an.timer = setInterval(function () { an.left--; els['an-count'].textContent = String(Math.max(0, an.left)); if (an.left <= 0) { var f = an.onPlay; hideAutoNext(); if (f) f(); } }, 1000);
  }
  function hideAutoNext() { clearInterval(an.timer); an.timer = null; if (els.autonext) els.autonext.classList.remove('show'); }
  function autoNextOpen() { return !!an.timer; }
  function hideOsd() { els.osd.classList.remove('show'); if (Nav.current() && Nav.current().getAttribute('data-nav') === 'osd') Nav.blur(); }
  function osdVisible() { return els.osd.classList.contains('show'); }
  function ratioName(i) { return T(['p.fit', 'p.fill', 'p.stretch'][i]); }
  function cycleRatio() { ratioMode = (ratioMode + 1) % 3; video.className = ['', 'fill', 'stretch'][ratioMode]; els['osd-ratio'].textContent = ratioName(ratioMode); UI.toast(T('p.aspect', { m: ratioName(ratioMode) })); }
  function updateFavBtn() { if (!current) return; els['osd-fav'].textContent = Store.isFav(App.account.id, current.type === 'episode' ? 'series' : current.type, current.type === 'episode' ? current.seriesId : current.id) ? '★' : '☆'; }

  /* ---- channel zapping ---- */
  function next() { if (playlist.length && current && current.type === 'live') zapTo(index + 1); else if (playlist.length) playIndex(index + 1); }
  function prev() { if (playlist.length && current && current.type === 'live') zapTo(index - 1); else if (playlist.length) playIndex(index - 1); }
  function zapTo(i) { if (!playlist.length) return; i = (i + playlist.length) % playlist.length; playIndex(i); }
  function playIndex(i) {
    if (i < 0 || i >= playlist.length) return; var it = playlist[i]; index = i;
    play(UI.toPlayable(it), { list: playlist, index: i });
  }
  function numberKey(d) {
    if (!current || current.type !== 'live') return;
    numBuf = (numBuf + d).slice(-4);
    var n = Number(numBuf), idx = -1; playlist.forEach(function (c, i) { if (Number(c.num) === n) idx = i; });
    if (idx < 0 && n >= 1 && n <= playlist.length) idx = n - 1;
    showZapPreview(numBuf, idx >= 0 ? playlist[idx] : null);
    clearTimeout(numTimer); numTimer = setTimeout(function () { commitZap(idx, n); }, 2500);
    zapPending = { idx: idx, n: n };
  }
  var zapPending = null;
  function commitZap(idx, n) {
    clearTimeout(numTimer); numBuf = ''; zapPending = null; els['zap-preview'].classList.remove('show');
    if (idx >= 0) playIndex(idx); else UI.toast(T('p.noChannel', { n: n }));
  }
  function cancelZap() { clearTimeout(numTimer); numBuf = ''; zapPending = null; els['zap-preview'].classList.remove('show'); }
  function showZapPreview(num, ch) {
    var box = els['zap-preview'];
    if (!ch) { box.innerHTML = '<div class="zp-num">' + num + '</div><div class="zp-none">' + U.esc(T('p.noChannel', { n: num })) + '</div>'; box.classList.add('show'); return; }
    box.innerHTML = '<div class="zp-num">' + num + '</div><div class="zp-head"><div class="zp-logo" style="' + (ch.logo ? 'background-image:url(\'' + U.esc(ch.logo) + '\')' : '') + '"></div><div><div class="zp-name">' + U.esc(ch.name) + '</div><div class="zp-cat">' + U.esc(ch.catName || '') + '</div></div></div>' +
      '<div class="zp-now"><span class="lbl">' + U.esc(T('nowLbl')) + '</span><span class="zp-now-t">…</span></div><div class="zp-bar"><i style="width:0"></i></div><div class="zp-next"><span class="lbl">' + U.esc(T('next')) + '</span><span class="zp-next-t">—</span></div><div class="zp-hint">' + U.esc(T('p.zapHint')) + '</div>';
    box.classList.add('show');
    var me = ch;
    App.provider.shortEPG(ch.epgId || ch.id, 3).then(function (list) {
      if (!box.classList.contains('show') || !zapPending || playlist[zapPending.idx] !== me) return;
      var now = Date.now() / 1000, cur = null, nxt = null; list.forEach(function (e) { if (e.start <= now && e.end > now) cur = e; else if (e.start > now && !nxt) nxt = e; });
      U.$('.zp-now-t', box).textContent = cur ? U.hm(cur.start) + ' – ' + U.hm(cur.end) + '  ' + cur.title : (list.length ? '—' : T('noEpg'));
      U.$('.zp-bar i', box).style.width = cur ? Math.round((now - cur.start) / (cur.end - cur.start) * 100) + '%' : '0';
      U.$('.zp-next-t', box).textContent = nxt ? U.hm(nxt.start) + '  ' + nxt.title : '—';
    });
  }
  function toggleZapList() {
    zapOpen = !zapOpen; els['zap-list'].classList.toggle('show', zapOpen);
    if (zapOpen) {
      hideOsd(); var inner = U.el('div', 'zap-inner'); els['zap-list'].innerHTML = '';
      playlist.forEach(function (c, i) {
        var d = U.el('div', 'ch-item focusable' + (i === index ? ' selected' : ''));
        d.setAttribute('data-nav', 'zap'); d.setAttribute('data-i', i);
        d.innerHTML = '<span class="num">' + (c.num || i + 1) + '</span><div class="logo-img" style="background-image:url(\'' + U.esc(c.logo || '') + '\')"></div><div class="info"><div class="name">' + U.esc(c.name) + '</div></div>';
        d.onclick = function () { playIndex(i); toggleZapList(); };
        inner.appendChild(d);
      });
      els['zap-list'].appendChild(inner);
      var target = inner.children[index >= 0 ? index : 0]; if (target) { scrollZap(target); Nav.focus(target); }
    } else Nav.blur();
  }
  function scrollZap(elm) {
    var inner = els['zap-list'].firstChild; if (!inner) return; var i = Number(elm.getAttribute('data-i')), h = 80, viewH = 1080 - 60;
    var off = Math.max(0, i * h - viewH / 2 + h / 2); inner.style.transform = 'translateY(-' + off + 'px)';
  }

  /* ---- audio / subtitle tracks ---- */
  function openTrackMenu(kind) {
    var menu = els['track-menu'], list = [];
    menu.innerHTML = '<div class="tm-title">' + (kind === 'audio' ? 'Audio tracks' : 'Subtitles') + '</div>';
    if (hls) {
      if (kind === 'audio') hls.audioTracks.forEach(function (t, i) { list.push({ label: t.name || t.lang || ('Track ' + (i + 1)), active: hls.audioTrack === i, act: function () { hls.audioTrack = i; } }); });
      else { list.push({ label: 'Off', active: hls.subtitleTrack === -1, act: function () { hls.subtitleTrack = -1; } }); hls.subtitleTracks.forEach(function (t, i) { list.push({ label: t.name || t.lang || ('Sub ' + (i + 1)), active: hls.subtitleTrack === i, act: function () { hls.subtitleTrack = i; } }); }); }
    } else {
      if (kind === 'audio' && video.audioTracks) for (var i = 0; i < video.audioTracks.length; i++) (function (t, i) { list.push({ label: t.label || t.language || ('Track ' + (i + 1)), active: t.enabled, act: function () { for (var j = 0; j < video.audioTracks.length; j++) video.audioTracks[j].enabled = j === i; } }); })(video.audioTracks[i], i);
      if (kind === 'subs' && video.textTracks) { list.push({ label: 'Off', active: true, act: function () { for (var j = 0; j < video.textTracks.length; j++) video.textTracks[j].mode = 'disabled'; } }); for (var k = 0; k < video.textTracks.length; k++) (function (t, k) { list.push({ label: t.label || t.language || ('Sub ' + (k + 1)), active: t.mode === 'showing', act: function () { for (var j = 0; j < video.textTracks.length; j++) video.textTracks[j].mode = j === k ? 'showing' : 'disabled'; } }); })(video.textTracks[k], k); }
    }
    if (!list.length) { UI.toast(kind === 'audio' ? 'No alternate audio tracks' : 'No subtitles available'); return; }
    list.forEach(function (t) {
      var d = U.el('div', 'track-item focusable' + (t.active ? ' active' : ''), U.esc(t.label)); d.setAttribute('data-nav', 'track');
      d.onclick = function () { t.act(); closeTrackMenu(); UI.toast(t.label); }; menu.appendChild(d);
    });
    menu.classList.add('show'); trackMenuOpen = true; showOsd(true); Nav.focusScope('track');
  }
  function closeTrackMenu() { els['track-menu'].classList.remove('show'); trackMenuOpen = false; showOsd(); Nav.focus(els['osd-play']); }

  /* ---- stream statistics overlay (INFO / BLUE) ---- */
  var statsOpen = false, statsTimer = null, statsPrev = null, brHist = [];
  function toggleStats(force) {
    statsOpen = force == null ? !statsOpen : !!force; els['stats-box'].classList.toggle('show', statsOpen);
    clearInterval(statsTimer); statsPrev = null; brHist = [];
    if (statsOpen) { renderStats(); statsTimer = setInterval(renderStats, 1000); }
  }
  function fmtBits(bps) { if (!bps || !isFinite(bps)) return T('stats.unknown'); return bps >= 1e6 ? (bps / 1e6).toFixed(2) + ' Mbps' : Math.round(bps / 1e3) + ' kbps'; }
  function bufferAhead() { try { var b = video.buffered, t = video.currentTime; for (var i = 0; i < b.length; i++) if (b.start(i) <= t && b.end(i) >= t) return b.end(i) - t; } catch (e) { } return 0; }
  function playbackQuality() {
    var q = null; try { if (video.getVideoPlaybackQuality) q = video.getVideoPlaybackQuality(); } catch (e) { }
    if (q) return { decoded: q.totalVideoFrames, dropped: q.droppedVideoFrames };
    if (video.webkitDecodedFrameCount != null) return { decoded: video.webkitDecodedFrameCount, dropped: video.webkitDroppedFrameCount || 0 };
    return null;
  }
  function renderStats() {
    if (!current) return; var rows = [], w = video.videoWidth, h = video.videoHeight;
    // bitrate: hls.js gives per-level bitrate + measured bandwidth; native: estimate from buffered bytes via webkitVideoDecodedByteCount when available
    var br = null, bw = null, codec = null, lat = null;
    if (hls) {
      var lv = hls.levels && hls.levels[hls.currentLevel]; if (lv) { br = lv.bitrate; codec = [lv.videoCodec, lv.audioCodec].filter(Boolean).join(' / '); }
      bw = hls.bandwidthEstimate; if (hls.latency != null && isFinite(hls.latency)) lat = hls.latency;
    } else {
      var bytes = (video.webkitVideoDecodedByteCount || 0) + (video.webkitAudioDecodedByteCount || 0), now = Date.now();
      if (statsPrev && bytes > statsPrev.bytes) br = (bytes - statsPrev.bytes) * 8 / ((now - statsPrev.t) / 1000);
      statsPrev = { bytes: bytes, t: now };
    }
    if (br) { brHist.push(br); if (brHist.length > 40) brHist.shift(); }
    var q = playbackQuality(), dropPct = q && q.decoded ? (q.dropped / q.decoded * 100) : 0;
    var ext = (current.url || '').split('?')[0].split('.').pop().toLowerCase(); if (ext.length > 5) ext = '';
    var container = { m3u8: 'HLS', ts: 'MPEG-TS', mp4: 'MP4', mkv: 'Matroska', avi: 'AVI', mpd: 'DASH' }[ext] || ext.toUpperCase();
    var res = w && h ? (w + '×' + h + (h >= 2100 ? ' (4K)' : h >= 1000 ? ' (FHD)' : h >= 700 ? ' (HD)' : ' (SD)')) : T('stats.unknown');
    rows.push([T('stats.res'), res]);
    rows.push([T('stats.bitrate'), fmtBits(br) + (bw ? ' · ↓ ' + fmtBits(bw) : ''), br ? (br > 12e6 ? 'good' : '') : '']);
    rows.push([T('stats.buffer'), bufferAhead().toFixed(1) + ' s', bufferAhead() < 2 ? 'warn' : 'good']);
    if (q) rows.push([T('stats.dropped'), q.dropped + ' / ' + q.decoded + ' (' + dropPct.toFixed(2) + '%)', dropPct > 5 ? 'bad' : dropPct > 1 ? 'warn' : 'good']);
    rows.push([T('stats.codec'), (codec ? codec + ' · ' : '') + (container || T('stats.unknown'))]);
    rows.push([T('stats.engine'), hls ? T('stats.hlsjs') : T('stats.native')]);
    if (lat != null && lat < 90) rows.push([T('stats.latency'), lat.toFixed(1) + ' s']);
    if (rc.attempts) rows.push(['Reconnects', String(rc.attempts)]);
    rows.push([T('stats.url'), (current.url || '').replace(/\/\/[^@/]+@/, '//').replace(/^https?:\/\//, '')]);
    var html = '<h4>' + U.esc(T('stats.title')) + '<span class="live-dot"></span></h4>' + rows.map(function (r) { return '<div class="sr"><span>' + U.esc(r[0]) + '</span><b class="' + (r[2] || '') + '">' + U.esc(r[1]) + '</b></div>'; }).join('');
    if (brHist.length > 1) { var mx = Math.max.apply(null, brHist), bars = []; for (var bi = 0; bi < 40; bi++) { var v = brHist[brHist.length - 40 + bi]; bars.push('<i style="height:' + (v == null ? 2 : Math.max(3, Math.round(v / mx * 100))) + '%;' + (v == null ? 'opacity:.15' : '') + '"></i>'); } html += '<div class="graph">' + bars.join('') + '</div>'; }
    els['stats-box'].innerHTML = html;
  }

  /* ---- key handling while player screen is active ---- */
  function handleKey(name, code) {
    if (!App.isScreen('player')) return false;
    var K = Nav.KEYS;
    if (trackMenuOpen) { if (name === 'BACK' || name === 'BACK2') { closeTrackMenu(); return true; } if (name === 'UP' || name === 'DOWN' || name === 'ENTER') return false; return true; }
    if (zapOpen) {
      if (name === 'BACK' || name === 'BACK2' || name === 'LEFT') { toggleZapList(); return true; }
      if (name === 'UP' || name === 'DOWN') { Nav.move(name.toLowerCase()); var c = Nav.current(); if (c && c.getAttribute('data-nav') === 'zap') scrollZap(c); return true; }
      if (name === 'ENTER') return false;
      return true;
    }
    if (code >= 48 && code <= 57) { numberKey(String(code - 48)); return true; }
    if (zapPending) { if (name === 'ENTER') { commitZap(zapPending.idx, zapPending.n); return true; } if (name === 'BACK' || name === 'BACK2') { cancelZap(); return true; } }
    if (statsOpen && (name === 'BACK' || name === 'BACK2')) { toggleStats(false); return true; }
    if (autoNextOpen()) {
      if (name === 'ENTER' || name === 'PLAY' || name === 'PLAYPAUSE') { var pf = an.onPlay; hideAutoNext(); if (pf) pf(); return true; }
      if (name === 'BACK' || name === 'BACK2' || name === 'STOP') { var cf = an.onCancel; hideAutoNext(); if (cf) cf(); return true; }
    }
    switch (name) {
      case 'BACK': case 'BACK2': if (osdVisible() && Nav.current() && Nav.current().getAttribute('data-nav') === 'osd') { hideOsd(); return true; } App.closePlayer(); return true;
      case 'PLAY': rc.userPaused = false; video.play(); showOsd(); return true;
      case 'PAUSE': rc.userPaused = true; video.pause(); showOsd(); return true;
      case 'PLAYPAUSE': togglePlay(); return true;
      case 'STOP': App.closePlayer(); return true;
      case 'REW': seek(-30); return true;
      case 'FF': seek(30); return true;
      case 'NEXT': case 'CH_UP': next(); return true;
      case 'PREV': case 'CH_DOWN': prev(); return true;
      case 'INFO': toggleStats(); return true;
      case 'BLUE': toggleStats(); return true;
      case 'GREEN': cycleRatio(); return true;
      case 'ENTER':
        if (!rc.active && current && els['player-error'].classList.contains('show')) { rc.active = true; rc.attempts = 0; error(null); loading(true, T('retrying')); doReconnect(); return true; }
        if (!osdVisible()) { showOsd(); Nav.focus(els['osd-play']); return true; }
        return false;
      case 'UP': if (!osdVisible()) { if (current && current.type === 'live') next(); else { showOsd(); Nav.focus(els['osd-play']); } return true; } showOsd(); return false;
      case 'DOWN': if (!osdVisible()) { showOsd(); Nav.focus(els['osd-play']); return true; } showOsd(); return false;
      case 'LEFT': if (!osdVisible()) { if (current && current.type !== 'live') seek(-30); else if (current) toggleZapList(); return true; } showOsd(); return false;
      case 'RIGHT': if (!osdVisible()) { if (current && current.type !== 'live') seek(30); else showOsd(); return true; } showOsd(); return false;
    }
    return false;
  }
  function action(a) {
    switch (a) {
      case 'p-play': togglePlay(); break; case 'p-rew': seek(-30); break; case 'p-ffw': seek(30); break;
      case 'p-next': next(); break; case 'p-prev': prev(); break; case 'p-ratio': cycleRatio(); break;
      case 'p-audio': openTrackMenu('audio'); break; case 'p-subs': openTrackMenu('subs'); break;
      case 'p-list': toggleZapList(); break; case 'p-stats': toggleStats(); break;
      case 'p-fav': if (current && current.type !== 'catchup') { var t = current.type === 'episode' ? 'series' : current.type; var id = current.type === 'episode' ? current.seriesId : current.id; var on = Store.toggleFav(App.account.id, { type: t, id: id, name: current.seriesName || current.name, logo: current.logo, poster: current.poster, ext: current.ext, cmd: current.cmd, url: current.url, catId: current.catId, num: current.num, epgId: current.epgId }); UI.toast(on ? 'Added to favorites' : 'Removed from favorites'); updateFavBtn(); } break;
    }
    showOsd();
  }
  function setOnEnded(fn) { onEnded = fn; }
  function getCurrent() { return current; }
  function reset() { zapOpen = false; trackMenuOpen = false; toggleStats(false); cancelZap(); hideAutoNext(); els['zap-list'].classList.remove('show'); els['track-menu'].classList.remove('show'); hideOsd(); }

  return { init: init, showLoading: showLoading, play: play, stop: stop, handleKey: handleKey, action: action, setOnEnded: setOnEnded, current: getCurrent, showOsd: showOsd, reset: reset, autoNext: autoNext, hideAutoNext: hideAutoNext, video: function () { return video; } };
})();
