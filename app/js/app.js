/* RGBTv — application controller (v1.1) */
var App = (function () {
  var screen = 'splash', section = 'home', account = null, provider = null;
  var live = { cats: [], catId: null, list: [], selected: null, previewTimer: null, epgTimer: null, previewVideo: null, previewHls: null };
  var movies = { cats: [], catId: null, list: [] }, series = { cats: [], catId: null, list: [] };
  var details = { base: null, info: null, season: null, list: null };
  var editingId = null, addType = 'xtream', addAvatar = 'red', playerReturn = null, manageMode = false;
  var pin = { buf: '', acc: null, resolve: null };

  /* ---------- screens ---------- */
  function showScreen(name) {
    screen = name;
    U.$$('.screen').forEach(function (s) { s.classList.toggle('active', s.id === 'screen-' + name); });
    Nav.setContainer(activeScreen());
  }
  function activeScreen() { return U.$('#screen-' + screen); }
  function isScreen(n) { return screen === n; }
  var NAV_ORDER = ['home', 'favorites', 'live', 'movies', 'series', 'search', 'weather', 'adhan', 'settings'];
  function showSection(name) {
    var fromLeft = NAV_ORDER.indexOf(name) < NAV_ORDER.indexOf(section); section = name;
    U.$$('.nav-item').forEach(function (b) { b.classList.toggle('active', b.getAttribute('data-section') === name); });
    U.$$('.section').forEach(function (s) { s.classList.toggle('active', s.id === 'sec-' + name); s.classList.toggle('from-left', s.id === 'sec-' + name && fromLeft); });
    if (name === 'movies' || name === 'series') markSeen(name);
    document.body.classList.toggle('hub-root', name === 'home' && isHub(Store.settings().layout));
    var cur = U.$('#hub-cur'); if (cur) cur.textContent = name === 'home' ? '' : T('nav.' + name);
    stopPreview(); if (name !== 'home') { clearTimeout(hero.timer); clearInterval(hero.clockTimer); }
    switch (name) {
      case 'live': loadLive(); break; case 'movies': loadMovies(); break; case 'series': loadSeries(); break;
      case 'favorites': renderFavorites(); break; case 'settings': renderSettings(); break; case 'home': renderHome(); break;
      case 'search': Nav.focus(U.$('#osk .osk-key')); break;
      case 'weather': Weather.open(); break;
      case 'adhan': Adhan.open(); break;
    }
  }
  var ACCENTS = { violet: '#6d5dfc', blue: '#3b82f6', cyan: '#06b6d4', green: '#22c55e', gold: '#eab308', orange: '#f97316', red: '#ef4444', pink: '#ec4899' };
  function hexRgba(hex, a) { var n = parseInt(hex.slice(1), 16); return 'rgba(' + (n >> 16 & 255) + ',' + (n >> 8 & 255) + ',' + (n & 255) + ',' + a + ')'; }
  function applyTheme() {
    var s = Store.settings(); document.body.setAttribute('data-theme', s.theme || 'aurora');
    // accent override: written as inline custom properties on <body> so it wins over the theme rules
    var st = document.body.style, hex = ACCENTS[s.accent];
    ['--accent', '--fglow', '--glowc', '--glow1'].forEach(function (v) { st.removeProperty(v); });
    if (hex) { st.setProperty('--accent', hex); st.setProperty('--fglow', hexRgba(hex, .6)); st.setProperty('--glowc', hexRgba(hex, .6)); st.setProperty('--glow1', hexRgba(hex, .34)); }
    document.body.setAttribute('data-corners', s.corners || 'round'); document.body.classList.toggle('noglow', s.glow === false);
  }
  function isHub(lay) { return lay === 'spotlight' || lay === 'trio' || lay === 'mosaic' || lay === 'dashboard'; }
  function applyUi() {
    var s = Store.settings();
    document.body.setAttribute('data-focus', s.focusStyle || 'glow'); document.body.classList.toggle('large', !!s.largeUi);
    U.$('#sec-home').setAttribute('data-layout', s.layout || 'classic');
    document.body.classList.toggle('hubmode', isHub(s.layout)); // hub styles: no top menu on Home, breadcrumb "Home ›" elsewhere
    Nav.setPointerMode(s.pointer || 'click');
  }
  function tickClock() {
    var d = new Date(); U.$('#clock').textContent = U.clock(d);
    var de = U.$('#clock-date'); if (de) { try { de.textContent = d.toLocaleDateString(I18n.get() === 'ar' ? 'ar' : 'en-GB', { weekday: 'short', day: 'numeric', month: 'short' }); } catch (e) { de.textContent = d.toDateString(); } }
    if (Weather && Weather.refresh && d.getSeconds() % 30 === 0) Weather.refresh();
  }
  function applyLang() { I18n.set(Store.settings().lang || 'en'); }
  function T(k, v) { return I18n.t(k, v); }

  /* ---------- boot ---------- */
  function init() {
    var hostPlat = window.RGBTvHost && RGBTvHost.platform ? RGBTvHost.platform() : '';
    if (hostPlat === 'xbox') document.body.classList.add('xbox', 'console'); else if (window.RGBTvHost) { document.body.classList.add('mobile', 'touch'); Touch.init(); } else if (window.RGBTvDesktop || /Electron/.test(navigator.userAgent)) document.body.classList.add('desktop');
    if (U.probeLuna) U.probeLuna();
    U.fitScreen(); setTimeout(U.fitScreen, 300); setTimeout(U.fitScreen, 1500); setTimeout(U.fitScreen, 4000);
    applyLang(); applyTheme(); applyUi(); Player.init();
    setInterval(tickClock, 1000); tickClock(); setTimeout(function () { Weather.refresh(); }, 2500); Adhan.start();
    var spot = U.el('div'); spot.id = 'spot'; U.$('#screen-home').insertBefore(spot, U.$('#screen-home .topbar'));
    initAmbient();
    var dev = Store.device(); U.$('#acc-mac').textContent = dev.mac;
    U.$('#acc-device').textContent = hostPlat === 'xbox' ? (RGBTvHost.device ? RGBTvHost.device() : 'Xbox') : window.RGBTvHost ? 'Android' : /Electron/.test(navigator.userAgent) ? 'Desktop' : (window.webOS && webOS.platform && webOS.platform.tv) ? 'LG webOS TV' : 'Browser';
    buildNumpad(); bindEvents();
    OSK.init(U.$('#osk'), U.$('#search-input'), function (val, submit) { doSearch(val); if (submit) { var c = U.$('#search-rows .card'); if (c) Nav.focus(c); } });
    Nav.onFocus(onFocusChange); Nav.onKey(onKey);
    Player.setOnEnded(onPlaybackEnded);
    document.addEventListener('visibilitychange', function () { if (screen !== 'player') return; if (document.hidden) Player.video().pause(); else Player.video().play().catch(function () { }); });
    var s = Store.settings(), last = Store.lastAccount();
    setTimeout(function () { if (s.autostart && last && Store.getAccount(last)) openAccount(last); else showAccounts(); }, 700);
  }

  /* ---------- profiles ---------- */
  function showAccounts(keepManage) {
    if (provider && provider.destroy) provider.destroy();
    provider = null; account = null; App.account = null; App.provider = null;
    if (!keepManage) manageMode = false;
    var list = Store.accounts();
    UI.renderAccounts(list, manageMode); showScreen('accounts');
    U.$('#manage-btn').innerHTML = (manageMode ? '<svg class="ico" viewBox="0 0 24 24"><path d="M9 16.2l-3.5-3.5L4 14.2 9 19.2 20 8.2l-1.4-1.4z"/></svg><span class="lbl">' + T('acc.done') + '</span>' : '<svg class="ico" viewBox="0 0 24 24"><path d="M3 17.25V21h3.75L17.8 9.94l-3.75-3.75L3 17.25zm17.7-10.2a1 1 0 0 0 0-1.41l-2.34-2.34a1 1 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/></svg><span class="lbl">' + T('acc.manage') + '</span>');
    var last = Store.lastAccount(), el = last ? U.$('.profile[data-id="' + last + '"]') : null;
    Nav.focus(el || U.$('.profile'));
  }
  function requirePin(acc) {
    if (!acc.pin) return Promise.resolve(true);
    return new Promise(function (resolve) {
      pin = { buf: '', acc: acc, resolve: resolve };
      U.$('#pin-avatar').src = Avatars.url(acc.avatar); U.$('#pin-title').textContent = acc.name; U.$('#pin-sub').textContent = 'Enter the 4-digit PIN';
      updatePinDots(); showScreen('pin'); Nav.focus(U.$('#numpad button'));
    });
  }
  function buildNumpad() {
    var np = U.$('#numpad'); np.innerHTML = '';
    ['1', '2', '3', '4', '5', '6', '7', '8', '9', '⌫', '0', 'OK'].forEach(function (k) {
      var b = U.el('button', 'focusable', k); b.setAttribute('data-nav', 'pin'); b.onclick = function () { pinKey(k); }; np.appendChild(b);
    });
  }
  function updatePinDots() { U.$$('#pin-dots i').forEach(function (d, i) { d.classList.toggle('on', i < pin.buf.length); }); }
  function pinKey(k) {
    if (k === '⌫') pin.buf = pin.buf.slice(0, -1);
    else if (k === 'OK') { /* handled on 4 digits */ }
    else if (pin.buf.length < 4) pin.buf += k;
    updatePinDots();
    if (pin.buf.length === 4) {
      if (pin.buf === pin.acc.pin) { var r = pin.resolve; pin.resolve = null; r(true); }
      else { var dots = U.$('#pin-dots'); dots.classList.add('shake'); setTimeout(function () { dots.classList.remove('shake'); pin.buf = ''; updatePinDots(); }, 450); U.$('#pin-sub').textContent = 'Wrong PIN, try again'; }
    }
  }
  function cancelPin() { var r = pin.resolve; pin.resolve = null; if (r) r(false); showAccounts(); }

  function connHint(m) {
    if (/HTTP 444|HTTP 512|HTTP 403/.test(m)) return m + ' — ' + T('conn.blocked');
    if (/HTTP 0|Network error|CORS/.test(m)) return m + ' — ' + T('conn.network');
    return m;
  }
  function openAccount(id, skipPin) {
    var acc = Store.getAccount(id); if (!acc) return;
    if (manageMode) { accountMenu(id); return; }
    (skipPin ? Promise.resolve(true) : requirePin(acc)).then(function (ok) {
      if (!ok) return;
      showScreen('splash'); U.$('#splash-status').textContent = 'Connecting to ' + acc.name + '…';
      var p = createProvider(acc);
      return p.login().then(function (info) {
        acc.lastLogin = Date.now(); if (info && info.expires) acc.expires = info.expires; acc.maxConn = info && info.maxConnections != null ? (Number(info.maxConnections) || 0) : (acc.maxConn || 0); Store.updateAccount(acc); Store.setLastAccount(acc.id);
        account = acc; provider = p; App.account = acc; App.provider = p;
        if (!App._warm) App._warm = setInterval(function () { if (account && U.luna) U.luna('ping', { timeout: 3000 }).catch(function () { }); }, 240000);
        live.cats = []; movies.cats = []; series.cats = [];
        U.$('#chip-name').textContent = acc.name; U.$('#chip-avatar').src = Avatars.url(acc.avatar);
        updateExpiry(info); updateNewBadges();
        showScreen('home'); showSection('home'); scheduleRefresh();
        if (info && info.expires && info.expires - Date.now() < 7 * 86400e3) UI.toast(T('toast.expires', { t: expiryText(info.expires).text }), 5000, '⚠');
      }).catch(function (e) {
        UI.renderAccounts(Store.accounts(), false); showScreen('accounts');
        UI.modal(T('conn.failed'), U.esc(acc.name) + ': ' + U.esc(connHint(e.message || 'Unknown error')), [{ label: T('edit'), value: 'edit' }, { label: T('retry'), value: 'retry', ghost: true }, { label: T('remove'), value: 'remove', danger: true }, { label: T('close'), value: null, ghost: true }]).then(function (v) {
          if (v === 'edit') { manageMode = false; showAddForm(Store.getAccount(acc.id) || acc); } else if (v === 'retry') openAccount(acc.id, true); else if (v === 'remove') { Store.removeAccount(acc.id); showAccounts(); }
          else Nav.focus(U.$('.profile[data-id="' + acc.id + '"]') || U.$('.profile'));
        });
      });
    });
  }
  function showAddForm(acc) {
    editingId = acc ? acc.id : null; var f = U.$('#add-form'); f.reset(); U.$('#add-error').textContent = '';
    U.$('#add-title').textContent = acc ? 'Edit Profile' : 'Add Profile';
    addAvatar = acc ? (acc.avatar || 'red') : Avatars.list()[Store.accounts().length % Avatars.list().length];
    UI.renderAvatarPicker(addAvatar, function (id) { addAvatar = id; });
    setAddType(acc ? acc.type : 'xtream');
    U.$('#kids-switch').setAttribute('data-on', acc && acc.kids ? '1' : '0');
    if (acc) { ['name', 'url', 'username', 'password', 'mac', 'sn', 'deviceId', 'epg', 'pin'].forEach(function (k) { if (f[k]) f[k].value = acc[k] || ''; }); }
    else { f.mac.value = Store.device().mac; }
    showScreen('add'); Nav.focus(f.name);
  }
  function setAddType(t) {
    addType = t;
    U.$$('#add-type-tabs .tab').forEach(function (b) { b.classList.toggle('active', b.getAttribute('data-type') === t); });
    U.$$('.type-fields').forEach(function (d) { d.classList.toggle('show', d.getAttribute('data-for').split(' ').indexOf(t) >= 0); });
    U.$('#lbl-url').textContent = t === 'xtream' ? 'Server URL (http://host:port) — or paste a get.php link' : t === 'stalker' ? 'Portal URL (http://host/c/)' : 'Playlist URL (.m3u / .m3u8)';
  }
  function saveAccount(ev) {
    ev.preventDefault(); var f = U.$('#add-form'), err = U.$('#add-error');
    var acc = { id: editingId || undefined, type: addType, name: f.name.value.trim(), url: f.url.value.trim(), avatar: addAvatar, pin: f.pin.value.trim(), kids: U.$('#kids-switch').getAttribute('data-on') === '1' };
    if (!acc.name || !acc.url) { err.textContent = 'Name and URL are required.'; return; }
    if (acc.pin && !/^\d{4}$/.test(acc.pin)) { err.textContent = 'PIN must be exactly 4 digits.'; return; }
    if (addType === 'xtream') {
      var m = acc.url.match(/^(https?:\/\/[^\/]+)\/.*[?&]username=([^&]+)&password=([^&]+)/i);
      if (m) { acc.url = m[1]; f.username.value = decodeURIComponent(m[2]); f.password.value = decodeURIComponent(m[3]); }
      acc.username = f.username.value.trim(); acc.password = f.password.value.trim();
      if (!acc.username || !acc.password) { err.textContent = 'Username and password are required.'; return; }
    } else if (addType === 'stalker') {
      acc.mac = f.mac.value.trim().toUpperCase().replace(/-/g, ':'); acc.sn = f.sn.value.trim(); acc.deviceId = f.deviceId.value.trim();
      if (!/^([0-9A-F]{2}:){5}[0-9A-F]{2}$/.test(acc.mac)) { err.textContent = 'Invalid MAC address (format 00:1A:79:XX:XX:XX).'; return; }
      acc.token = null; acc.endpoint = null;
    } else acc.epg = f.epg.value.trim();
    if (editingId) { var old = Store.getAccount(editingId); for (var k in acc) old[k] = acc[k]; Store.updateAccount(old); Store.clearCache(editingId); acc = old; }
    else acc = Store.addAccount(acc);
    manageMode = false; openAccount(acc.id, true);
  }
  function accountMenu(id) {
    var acc = Store.getAccount(id); if (!acc) return;
    UI.modal(acc.name, acc.type.toUpperCase() + ' · ' + U.esc(acc.url) + (acc.pin ? ' · PIN protected' : '') + (acc.kids ? ' · Kids' : ''), [{ label: 'Connect', value: 'open' }, { label: 'Edit', value: 'edit', ghost: true }, { label: 'Delete', value: 'del', danger: true }, { label: 'Cancel', value: null, ghost: true }]).then(function (v) {
      if (v === 'open') { manageMode = false; openAccount(id); } else if (v === 'edit') requirePin(acc).then(function (ok) { if (ok) showAddForm(acc); });
      else if (v === 'del') UI.modal('Delete profile?', 'This removes "' + U.esc(acc.name) + '" with its favorites and history.', [{ label: 'Delete', value: true, danger: true }, { label: 'Cancel', value: false, ghost: true }]).then(function (ok) { if (ok) { Store.removeAccount(id); showAccounts(true); } });
    });
  }

  /* ---------- Add from phone (QR pairing via the Luna service HTTP server) ---------- */
  var pair = { timer: null, on: false, from: null };
  function pairCall(method, params) {
    // in a desktop browser (dev) the mock server exposes the same endpoints over HTTP
    if (window.RGBTvDesktop && RGBTvDesktop.pair) return RGBTvDesktop.pair(method, params || {}).then(function (r) { if (r && r.returnValue === false) throw new Error(r.errorText || 'pair failed'); return r; });
    if (typeof window.PalmServiceBridge === 'undefined') return U.getJSON('/__pair/' + method + '?' + U.qs(params || {}));
    return U.luna(method, params || {});
  }
  function showPair() {
    pair.from = screen; pair.on = true; showScreen('pair');
    var st = U.$('#pair-status'), txt = U.$('#pair-status-text'), qr = U.$('#pair-qr'), urlEl = U.$('#pair-url');
    st.className = 'pair-status'; txt.textContent = T('pair.starting'); qr.innerHTML = '<div class="qr-wait">' + U.esc(T('pair.starting')) + '</div>'; urlEl.textContent = '—'; U.$('#pair-alt').textContent = '';
    Nav.focus(U.$('[data-action="pair-cancel"]'));
    pairCall('pairStart', { lang: I18n.get() }).then(function (r) {
      if (!pair.on) return;
      var ips = (r.ips || []).filter(function (ip) { return !/^169\.254\./.test(ip); });
      if (!ips.length) { st.className = 'pair-status bad'; txt.textContent = T('pair.noLan'); qr.innerHTML = '<div class="qr-wait">' + U.esc(T('pair.noLan')) + '</div>'; return; }
      var link = 'http://' + ips[0] + ':' + r.port + '/?lang=' + I18n.get();
      urlEl.textContent = ips[0] + ':' + r.port; U.$('#pair-alt').textContent = T('pair.alt') + (ips.length > 1 ? ' · ' + ips.slice(1).map(function (ip) { return ip + ':' + r.port; }).join(' · ') : '');
      if (window.RGBTvDesktop) U.$('#pair-alt').textContent += ' — ' + T('pair.firewall');
      try { var q = qrcode(0, 'M'); q.addData(link); q.make(); qr.innerHTML = q.createSvgTag({ cellSize: 1, margin: 0, scalable: true }); } catch (e) { qr.innerHTML = '<div class="qr-wait">' + U.esc(link) + '</div>'; }
      st.className = 'pair-status'; txt.textContent = T('pair.waiting');
      clearInterval(pair.timer); pair.timer = setInterval(pollPair, 2000);
    }).catch(function (e) { st.className = 'pair-status bad'; txt.textContent = (/no luna/.test(e.message) ? T('pair.noService') : e.message); qr.innerHTML = '<div class="qr-wait">' + U.esc(txt.textContent) + '</div>'; });
  }
  function pollPair() {
    if (!pair.on) { clearInterval(pair.timer); return; }
    pairCall('pairPoll', {}).then(function (r) {
      var items = (r && r.items) || []; if (!items.length) return;
      var d = items[items.length - 1], acc = { type: /^(xtream|stalker|m3u)$/.test(d.type) ? d.type : 'xtream', name: String(d.name || '').trim().slice(0, 40), url: String(d.url || '').trim(), avatar: Avatars.list()[Store.accounts().length % Avatars.list().length], pin: /^\d{4}$/.test(d.pin || '') ? d.pin : '', kids: false };
      if (acc.type === 'xtream') { var m = acc.url.match(/^(https?:\/\/[^\/]+)\/.*[?&]username=([^&]+)&password=([^&]+)/i); if (m) { acc.url = m[1]; acc.username = decodeURIComponent(m[2]); acc.password = decodeURIComponent(m[3]); } else { acc.username = String(d.username || '').trim(); acc.password = String(d.password || '').trim(); } }
      else if (acc.type === 'stalker') { acc.mac = String(d.mac || '').trim().toUpperCase().replace(/-/g, ':') || Store.device().mac; acc.token = null; acc.endpoint = null; }
      else acc.epg = String(d.epg || '').trim();
      if (!/^https?:\/\//i.test(acc.url)) acc.url = 'http://' + acc.url;
      U.$('#pair-status').className = 'pair-status ok'; U.$('#pair-status-text').textContent = T('pair.received', { n: acc.name });
      acc = Store.addAccount(acc); UI.toast(T('pair.received', { n: acc.name }), 3000, '📱');
      setTimeout(function () { stopPair(true); manageMode = false; openAccount(acc.id, true); }, 1200);
    }).catch(function () { });
  }
  function stopPair(silent) { pair.on = false; clearInterval(pair.timer); pair.timer = null; pairCall('pairStop', {}).catch(function () { }); if (!silent) { if (pair.from === 'home' && account) { showScreen('home'); Nav.focusScope('settings') || Nav.focusFirst(); } else if (pair.from === 'add') { showScreen('add'); Nav.focus(U.$('#add-form').name); } else showAccounts(); } }

  /* ---------- subscription expiry badge (top bar) ---------- */
  function expiryText(exp) {
    if (!exp) return null;
    var days = Math.floor((exp - Date.now()) / 86400e3), cls = 'ok', txt;
    if (exp - Date.now() <= 0) { txt = T('exp.expired'); cls = 'bad'; }
    else if (days < 1) { txt = T('exp.today'); cls = 'bad'; }
    else if (days === 1) { txt = T('exp.one'); cls = 'bad'; }
    else { txt = T('exp.days', { d: days }); cls = days <= 7 ? 'bad' : days <= 30 ? 'warn' : 'ok'; }
    return { text: txt, cls: cls, days: days };
  }
  function updateExpiry(info) {
    var el = U.$('#expiry'); if (!el || !account) return;
    var exp = (info && info.expires) || account.expires, unlimited = info && info.loggedIn && !info.expires && account.type === 'xtream';
    if (!exp && !unlimited) { el.className = 'expiry hidden'; el.textContent = ''; return; }
    var e = exp ? expiryText(exp) : { text: T('exp.unlimited'), cls: 'ok' };
    el.textContent = e.text; el.className = 'expiry ' + e.cls; el.title = exp ? new Date(exp).toLocaleDateString() : '';
  }
  setInterval(function () { updateExpiry(null); }, 60000);

  /* ---------- background playlist refresh ---------- */
  var refreshTimer = null;
  function scheduleRefresh() {
    clearInterval(refreshTimer); var h = Store.settings().refreshHours; if (!h || !account) return;
    refreshTimer = setInterval(function () { if (screen === 'home' && !UI.modalOpen()) refreshPlaylists(false); }, h * 3600e3);
    // first snapshot (silent) so later diffs make sense
    if (!Store.snapshot(account.id)) setTimeout(function firstSnap() { if (screen === 'player' || live.previewVideo) { setTimeout(firstSnap, 60000); return; } refreshPlaylists(false, true); }, 180000);
  }
  function ids(list) { var m = {}; for (var i = 0; i < list.length; i++) m[list[i].id] = 1; return m; }
  function refreshPlaylists(manual, silent) {
    if (!account || !provider) return; if (refreshPlaylists._busy) return; if (!manual && screen === 'player') return; refreshPlaylists._busy = true;
    if (manual) UI.toast(T('toast.refreshing'), 2500, '↻');
    var prev = Store.snapshot(account.id), acc = account;
    // drop caches so the provider re-downloads
    if (!(silent && !prev)) { Store.clearCache(acc.id); if (provider._mem) provider._mem = {}; if (provider._pending) provider._pending = {}; }
    Promise.all([provider.liveStreams().catch(function () { return null; }), provider.vodStreams().catch(function () { return null; }), provider.seriesList().catch(function () { return null; })]).then(function (r) {
      refreshPlaylists._busy = false; if (account !== acc) return;
      var lv = r[0], vd = r[1], sr = r[2]; if (!lv && !vd && !sr) return;
      var snap = { at: Date.now(), live: lv ? ids(lv) : (prev && prev.live) || {}, vod: vd ? ids(vd) : (prev && prev.vod) || {}, series: sr ? ids(sr) : (prev && prev.series) || {} };
      Store.setSnapshot(acc.id, snap);
      live.cats = []; movies.cats = []; series.cats = [];
      if (silent || !prev) return;
      var nl = 0, nv = 0, ns = 0, k;
      for (k in snap.live) if (!prev.live[k]) nl++; for (k in snap.vod) if (!prev.vod[k]) nv++; for (k in snap.series) if (!prev.series[k]) ns++;
      var nb = Store.get('newcount:' + acc.id, { movies: 0, series: 0 }); nb.movies += nv; nb.series += ns; Store.set('newcount:' + acc.id, nb); updateNewBadges();
      var parts = []; if (nv) parts.push(T('toast.newMovies', { n: nv })); if (ns) parts.push(T('toast.newSeries', { n: ns })); if (nl) parts.push(T('toast.newLive', { n: nl }));
      if (parts.length) { UI.toast(T('toast.newContent', { n: nl + nv + ns }) + ' · ' + parts.join(' · '), 7000, '✦'); if (section === 'home') renderHome(); }
      else if (manual) UI.toast(T('toast.refreshed'), 3000, '✓');
    }, function () { refreshPlaylists._busy = false; });
  }

  function updateNewBadges() {
    if (!account) return; var nb = Store.get('newcount:' + account.id, { movies: 0, series: 0 });
    [['movies', '#new-movies'], ['series', '#new-series']].forEach(function (x) { var e = U.$(x[1]); if (!e) return; e.textContent = nb[x[0]] ? T('newBadge', { n: nb[x[0]] }) : ''; e.classList.toggle('show', nb[x[0]] > 0); });
  }
  function markSeen(kind) { if (!account) return; var nb = Store.get('newcount:' + account.id, { movies: 0, series: 0 }); if (nb[kind]) { nb[kind] = 0; Store.set('newcount:' + account.id, nb); updateNewBadges(); } }

  /* ---------- home ---------- */
  function parentalOn() { return account && (account.kids || Store.settings().parental); }
  var hero = { items: [], idx: 0, timer: null, clockTimer: null, onNowTimer: null };
  function renderHome() {
    var lay = Store.settings().layout || 'classic'; U.$('#sec-home').setAttribute('data-layout', lay);
    document.body.classList.toggle('hubmode', isHub(lay)); document.body.classList.toggle('hub-root', isHub(lay));
    if (isHub(lay)) { stopHero(); renderHub(lay); return; }
    var rows = U.$('#home-rows'); rows.style.transform = ''; rows.innerHTML = ''; U.$('#sec-home').classList.remove('rows-mode');
    var hist = Store.history(account.id), favs = Store.favorites(account.id);
    var cont = hist.filter(function (h) { return h.type !== 'live' && Store.getPos(account.id, h.type + ':' + h.id); });
    var recentLive = hist.filter(function (h) { return h.type === 'live'; });
    stopHero(); setHeroWelcome();
    var favLive = favs.filter(function (f) { return f.type === 'live'; }), favVod = favs.filter(function (f) { return f.type !== 'live'; });
    if (favLive.length) rows.appendChild(onNowRow(favLive.slice(0, 12)));
    if (cont.length) rows.appendChild(UI.row(T('home.continue'), cont.map(hydrate)));
    if (recentLive.length) rows.appendChild(UI.row(T('home.recentLive'), recentLive.map(hydrate)));
    if (favVod.length) rows.appendChild(UI.row(T('home.mylist'), favVod.map(hydrate)));
    var sk = U.el('div'); UI.skeletonRows(sk, 1); rows.appendChild(sk);
    if (!Nav.current() || !Nav.visible(Nav.current())) Nav.focus(U.$('[data-action="hero-play"]'));
    var token = renderHome._t = (renderHome._t || 0) + 1;
    function alive() { return renderHome._t === token && section === 'home' && account; }
    function byAdded(a, b) { return (b.added || 0) - (a.added || 0); }
    function byRating(a, b) { return (Number(b.rating) || 0) - (Number(a.rating) || 0); }
    var heroPool = [];
    // sequential background loading keeps the TV responsive
    provider.vodStreams().catch(function () { return []; }).then(function (m) {
      if (!alive()) return; var bad = adultCatIds(); m = m.filter(function (x) { return !bad[x.catId]; });
      var latest = m.slice().sort(byAdded).slice(0, 30);
      if (latest.length) { rows.insertBefore(UI.row(T('home.latestMovies'), latest), sk); latest.slice(0, 4).forEach(function (x) { heroPool.push({ it: x, tag: T('home.latestMovie') }); }); }
      var top = m.filter(function (x) { return Number(x.rating) >= 7 && x.poster; }).sort(byRating).slice(0, 30);
      if (top.length >= 6) rows.insertBefore(UI.row(T('home.trending'), top), sk);
      startHero(heroPool);
      return provider.seriesList().catch(function () { return []; });
    }).then(function (sl) {
      if (!alive()) return; var bad = adultCatIds(); sl = (sl || []).filter(function (x) { return !bad[x.catId]; }).slice().sort(byAdded).slice(0, 30);
      if (sl.length) { rows.insertBefore(UI.row(T('home.latestSeries'), sl), sk); sl.slice(0, 3).forEach(function (x) { heroPool.push({ it: x, tag: T('home.latestSeries') }); }); startHero(heroPool); }
      return provider.liveStreams().catch(function () { return []; });
    }).then(function (lv) {
      if (!alive()) return; var bad = adultCatIds(); lv = (lv || []).filter(function (x) { return !bad[x.catId]; }).slice(0, 30);
      if (lv.length) rows.insertBefore(UI.row(T('home.liveChannels'), lv), sk);
      sk.remove();
      if (!rows.children.length) rows.appendChild(U.el('div', 'empty', T('home.empty')));
    });
  }
  /* ---- Hub layouts (VIU-style / IBO-style) ---- */
  var HUB_ICONS = {
    live: '<svg viewBox="0 0 24 24"><path d="M3 5h18v12H3zm5 14h8v2H8z"/><path d="M10 8.5v5l4.5-2.5z" fill="#0b0f19"/></svg>',
    movies: '<svg viewBox="0 0 24 24"><path d="M4 4h16v16H4zm2 2v2h2V6zm0 4v2h2v-2zm0 4v2h2v-2zm10-8v2h2V6zm0 4v2h2v-2zm0 4v2h2v-2zM10 6v12h4V6z"/></svg>',
    series: '<svg viewBox="0 0 24 24"><path d="M4 6h16v11H4zm3 13h10v2H7zM6 3h12v2H6z"/></svg>',
    favorites: '<svg viewBox="0 0 24 24"><path d="m12 2 3 6.5 7 .8-5.2 4.8 1.4 7L12 17.5 5.8 21l1.4-7L2 9.3l7-.8z"/></svg>',
    adhan: Adhan.icon,
    search: '<svg viewBox="0 0 24 24"><path d="M10 2a8 8 0 1 0 4.9 14.3l5.4 5.4 1.4-1.4-5.4-5.4A8 8 0 0 0 10 2zm0 2a6 6 0 1 1 0 12 6 6 0 0 1 0-12z"/></svg>',
    settings: '<svg viewBox="0 0 24 24"><path d="M19.4 13a7.6 7.6 0 0 0 0-2l2.1-1.6-2-3.5-2.5 1a7.4 7.4 0 0 0-1.7-1L15 3H9l-.4 2.7a7.4 7.4 0 0 0-1.7 1l-2.5-1-2 3.5L4.6 11a7.6 7.6 0 0 0 0 2l-2.1 1.6 2 3.5 2.5-1a7.4 7.4 0 0 0 1.7 1L9 21h6l.4-2.7a7.4 7.4 0 0 0 1.7-1l2.5 1 2-3.5zM12 15.5a3.5 3.5 0 1 1 0-7 3.5 3.5 0 0 1 0 7z"/></svg>',
    profiles: '<svg viewBox="0 0 24 24"><path d="M12 12a5 5 0 1 0 0-10 5 5 0 0 0 0 10zm0 2c-4.4 0-8 2.2-8 5v2h16v-2c0-2.8-3.6-5-8-5z"/></svg>',
    refresh: '<svg viewBox="0 0 24 24"><path d="M12 5V2L7 6l5 4V7a5 5 0 1 1-5 5H5a7 7 0 1 0 7-7z"/></svg>',
    weather: '<svg viewBox="0 0 24 24"><path d="M6 19a4 4 0 0 1-.5-7.97A6 6 0 0 1 17.3 9.1 4.5 4.5 0 0 1 17.5 18H6z"/></svg>'
  };
  function hubTile(kind, cls, label, sub, posters) {
    var t = U.el('div', 'tile focusable ' + kind + (cls ? ' ' + cls : '')); t.setAttribute('data-nav', 'hub'); t.setAttribute('data-section', kind === 'favorites' ? 'favorites' : kind);
    var ph = ''; (posters || []).forEach(function (u) { if (u.src) ph += '<i class="' + (u.logo ? 'lg' : '') + '" style="background-image:url(\'' + U.esc(u.src) + '\')"></i>'; });
    t.innerHTML = '<div class="t-bg"></div><div class="t-grad"></div><div class="t-shade"></div><div class="t-ico">' + HUB_ICONS[kind] + '</div><div class="t-lbl">' + U.esc(label) + '</div><div class="t-sub">' + U.esc(sub || '') + '</div><div class="t-posters">' + ph + '</div>';
    return t;
  }
  function hubUtil(kind, label, action) {
    var u = U.el('button', 'util focusable'); u.setAttribute('data-nav', 'hub'); u.innerHTML = HUB_ICONS[kind] + '<span>' + U.esc(label) + '</span>';
    if (action) u.setAttribute('data-action', action); else u.setAttribute('data-section', kind);
    return u;
  }
  function renderHub(lay) {
    var hub = U.$('#hub'); hub.className = 'hub hub-' + lay; hub.innerHTML = '';
    var favs = Store.favorites(account.id), hist = Store.history(account.id), nb = Store.get('newcount:' + account.id, { movies: 0, series: 0 });
    var tLive = hubTile('live', '', T('hub.live'), '…'), tMov = hubTile('movies', '', T('hub.movies'), '…'), tSer = hubTile('series', '', T('hub.series'), '…');
    var tFav = hubTile('favorites', '', T('hub.fav'), T('hub.fav.s', { n: favs.length })), tGuide = hubTile('adhan', '', T('hub.adhan'), ''); tGuide.querySelector('.t-sub').id = 'hub-adhan'; Adhan.tick();
    var tWx = hubTile('weather', '', T('hub.weather'), ''), tSearch = hubTile('search', '', T('hub.search'), ''), tSet = hubTile('settings', '', T('hub.settings'), ''), tProf = hubTile('profiles', '', T('hub.profiles'), account.name);
    tProf.removeAttribute('data-section'); tProf.setAttribute('data-action', 'switch-account');
    if (nb.movies) tMov.appendChild(U.el('div', 't-new', T('newBadge', { n: nb.movies }))); if (nb.series) tSer.appendChild(U.el('div', 't-new', T('newBadge', { n: nb.series })));
    var cont = hist.filter(function (h) { return h.type !== 'live' && Store.getPos(account.id, h.type + ':' + h.id); });
    var recent = hist.slice(0, 12), ex = account.expires ? expiryText(account.expires) : null;
    function utilBar(extra) {
      var util = U.el('div', 'hub-util');
      (extra || []).forEach(function (k) { util.appendChild(hubUtil(k, T('hub.' + (k === 'favorites' ? 'fav' : k)))); });
      util.appendChild(hubUtil('search', T('hub.search'))); util.appendChild(hubUtil('weather', T('hub.weather'))); util.appendChild(hubUtil('settings', T('hub.settings'))); util.appendChild(hubUtil('profiles', T('hub.profiles'), 'switch-account')); util.appendChild(hubUtil('refresh', T('hub.refresh'), 'refresh-now'));
      return util;
    }
    function strip() {
      var src = cont.length ? cont : recent; if (!src.length) return null;
      var st = U.el('div', 'hub-strip'); st.innerHTML = '<div class="row-title">' + U.esc(cont.length ? T('hub.continue') : T('hub.recent')) + '</div>';
      var items = U.el('div', 'hub-items'); src.slice(0, 9).forEach(function (h) { items.appendChild(UI.card(hydrate(h), { nav: 'hub', mini: true, list: src })); }); st.appendChild(items); return st;
    }
    function infoBar() {
      var info = U.el('div', 'hub-info');
      info.innerHTML = '<div><div class="hi-time" id="hub-time">' + U.clock() + '</div><div class="hi-date" id="hub-date"></div></div><div class="hi-acc">' + U.esc(T('hub.account')) + ': <b>' + U.esc(account.name) + '</b> · ' + account.type.toUpperCase() + '<br>' + U.esc(T('hub.expires')) + ': <b class="' + (ex ? ex.cls : 'ok') + '">' + U.esc(ex ? ex.text : T('exp.unlimited')) + '</b>' + (ex && account.expires ? ' (' + new Date(account.expires).toLocaleDateString() + ')' : '') + '</div>';
      tickHubClock(); clearInterval(hero.clockTimer); hero.clockTimer = setInterval(tickHubClock, 15000); return info;
    }
    var main = U.el('div', 'hub-main'), st;
    if (lay === 'spotlight') {            // one big Live tile + 2x2 grid, continue-watching strip
      tLive.classList.add('big'); main.appendChild(tLive); var g = U.el('div', 'tile-grid'); [tMov, tSer, tFav, tGuide].forEach(function (t) { g.appendChild(t); }); main.appendChild(g);
      hub.appendChild(main); hub.appendChild(utilBar()); st = strip(); if (st) hub.appendChild(st);
    } else if (lay === 'trio') {          // three tall tiles + utility bar + info bar
      [tLive, tMov, tSer].forEach(function (t) { main.appendChild(t); }); hub.appendChild(main);
      hub.appendChild(utilBar(['favorites', 'adhan'])); hub.appendChild(infoBar());
    } else if (lay === 'mosaic') {        // 4x2 mosaic of equal tiles — every section one press away
      [tLive, tMov, tSer, tFav, tGuide, tWx, tSearch, tSet].forEach(function (t) { main.appendChild(t); }); hub.appendChild(main);
      var ib = infoBar(); ib.classList.add('slim'); hub.appendChild(ib);
    } else {                              // dashboard: greeting + clock on the left, tiles on the right, strip below
      var side = U.el('div', 'hub-side');
      side.innerHTML = '<div class="hs-hello">' + U.esc(T('hub.hello')) + '</div><div class="hs-name">' + U.esc(account.name) + '</div><div class="hs-time" id="hub-time"></div><div class="hs-date" id="hub-date"></div><div class="hs-exp ' + (ex ? ex.cls : 'ok') + '">' + U.esc(T('hub.expires')) + ': ' + U.esc(ex ? ex.text : T('exp.unlimited')) + '</div>';
      var sideBtns = U.el('div', 'hs-btns'); sideBtns.appendChild(hubUtil('search', T('hub.search'))); sideBtns.appendChild(hubUtil('weather', T('hub.weather'))); sideBtns.appendChild(hubUtil('settings', T('hub.settings'))); sideBtns.appendChild(hubUtil('profiles', T('hub.profiles'), 'switch-account')); sideBtns.appendChild(hubUtil('refresh', T('hub.refresh'), 'refresh-now')); side.classList.add('five'); side.appendChild(sideBtns);
      main.appendChild(side); var g2 = U.el('div', 'tile-grid'); [tLive, tMov, tSer, tFav, tGuide].forEach(function (t) { g2.appendChild(t); }); tLive.classList.add('wide2'); main.appendChild(g2);
      hub.appendChild(main); st = strip(); if (st) hub.appendChild(st);
      tickHubClock(); clearInterval(hero.clockTimer); hero.clockTimer = setInterval(tickHubClock, 15000);
    }
    if (!Nav.current() || !Nav.visible(Nav.current())) Nav.focus(tLive);
    // counts + artwork (cached lists -> cheap)
    var token = renderHub._t = (renderHub._t || 0) + 1; function alive() { return renderHub._t === token && section === 'home' && account && document.body.contains(hub); }
    var bad = adultCatIds();
    provider.liveStreams().catch(function () { return []; }).then(function (lv) {
      if (!alive()) return; lv = (lv || []).filter(function (x) { return !bad[x.catId]; });
      U.$('.t-sub', tLive).textContent = T('hub.live.s', { n: lv.length }); setTilePosters(tLive, lv.filter(function (x) { return x.logo; }).slice(0, 3).map(function (x) { return { src: x.logo, logo: true }; }));
      var favLive = favs.filter(function (f) { return f.type === 'live' && f.logo; }); if (favLive.length) setTilePosters(tFav, favLive.slice(0, 3).map(function (x) { return { src: x.logo, logo: true }; }));
      return provider.vodStreams().catch(function () { return []; });
    }).then(function (m) {
      if (!alive()) return; m = (m || []).filter(function (x) { return !bad[x.catId]; }); var latest = m.slice().sort(function (a, b) { return (b.added || 0) - (a.added || 0); });
      U.$('.t-sub', tMov).textContent = T('hub.movies.s', { n: m.length }); setTilePosters(tMov, latest.filter(function (x) { return x.poster; }).slice(0, 3).map(function (x) { return { src: x.poster }; }));
      var bgM = latest.filter(function (x) { return x.backdrop || x.poster; })[0]; if (bgM) U.$('.t-bg', tMov).style.backgroundImage = 'url("' + (bgM.backdrop || bgM.poster) + '")';
      return provider.seriesList().catch(function () { return []; });
    }).then(function (sl) {
      if (!alive()) return; sl = (sl || []).filter(function (x) { return !bad[x.catId]; }); var latest = sl.slice().sort(function (a, b) { return (b.added || 0) - (a.added || 0); });
      U.$('.t-sub', tSer).textContent = T('hub.series.s', { n: sl.length }); setTilePosters(tSer, latest.filter(function (x) { return x.poster; }).slice(0, 3).map(function (x) { return { src: x.poster }; }));
      var bgS = latest.filter(function (x) { return x.backdrop || x.poster; })[0]; if (bgS) U.$('.t-bg', tSer).style.backgroundImage = 'url("' + (bgS.backdrop || bgS.poster) + '")';
      var favV = favs.filter(function (f) { return f.type !== 'live' && f.poster; }); if (favV.length) setTilePosters(tFav, favV.slice(0, 3).map(function (x) { return { src: x.poster }; }));
    });
  }
  function setTilePosters(tile, list) { var box = U.$('.t-posters', tile); if (!box) return; box.innerHTML = ''; list.forEach(function (u) { var i = U.el('i', u.logo ? 'lg' : ''); i.style.backgroundImage = 'url("' + u.src + '")'; box.appendChild(i); }); }
  function tickHubClock() { var t = U.$('#hub-time'), d = U.$('#hub-date'); if (!t) return; var n = new Date(); t.textContent = U.clock(n); try { d.textContent = n.toLocaleDateString(I18n.get() === 'ar' ? 'ar' : 'en-GB', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }); } catch (e) { d.textContent = n.toDateString(); } }

  /* ---- hero carousel ---- */
  function setHeroWelcome() {
    var titleEl = U.$('#hero-title'); titleEl._item = null; titleEl.textContent = T('home.welcome', { name: account.name });
    U.$('#hero-desc').textContent = T('home.tagline', { src: account.type === 'xtream' ? 'Xtream Codes' : account.type === 'stalker' ? 'Stalker Portal' : 'M3U' });
    U.$('#hero-tag').textContent = account.type.toUpperCase(); U.$('#hero-meta').innerHTML = '';
    U.$('#hero-bg').style.backgroundImage = ''; U.$('#hero').classList.add('plain'); U.$('#hero-poster').classList.remove('show'); U.$('#hero-dots').innerHTML = '';
    tickHeroClock(); clearInterval(hero.clockTimer); hero.clockTimer = setInterval(tickHeroClock, 15000);
  }
  function tickHeroClock() { var d = new Date(); U.$('#hero-clock-time').textContent = U.clock(d); try { U.$('#hero-clock-date').textContent = d.toLocaleDateString(I18n.get() === 'ar' ? 'ar' : 'en-GB', { weekday: 'long', day: 'numeric', month: 'long' }); } catch (e) { U.$('#hero-clock-date').textContent = d.toDateString(); } }
  function stopHero() { clearTimeout(hero.timer); hero.timer = null; hero.items = []; hero.idx = 0; }
  function startHero(pool) {
    if (!pool.length || section !== 'home') return;
    var was = hero.items.length; hero.items = pool.slice(0, 7);
    if (!was) { hero.idx = 0; showHero(0); } else renderHeroDots();
  }
  function renderHeroDots() { var d = U.$('#hero-dots'); d.innerHTML = ''; hero.items.forEach(function (_, i) { d.appendChild(U.el('i', i === hero.idx ? 'on' : '')); }); }
  function showHero(i) {
    if (!hero.items.length) return; hero.idx = i % hero.items.length; var h = hero.items[hero.idx];
    var bg = U.$('#hero-bg'); bg.classList.add('fade');
    setTimeout(function () { if (section !== 'home') return; setHero(h.it, h.tag); bg.classList.remove('fade'); }, 350);
    renderHeroDots();
    clearTimeout(hero.timer); hero.timer = setTimeout(function () { if (section === 'home' && screen === 'home') showHero(hero.idx + 1); else hero.timer = setTimeout(function () { showHero(hero.idx + 1); }, 4000); }, 9000);
  }
  function setHero(h, tag) {
    var titleEl = U.$('#hero-title'); titleEl._item = h; titleEl.textContent = TMDB.enabled() ? TMDB.cleanTitle(h.name) : h.name;
    U.$('#hero').classList.remove('plain');
    U.$('#hero-tag').textContent = tag; U.$('#hero-desc').textContent = h.plot || (T('home.latestAddition') + (h.year ? ' · ' + String(h.year).substr(0, 4) : ''));
    U.$('#hero-bg').style.backgroundImage = (h.backdrop || h.poster) ? 'url("' + (h.backdrop || h.poster) + '")' : '';
    var po = U.$('#hero-poster'); if (h.poster) { po.style.backgroundImage = 'url("' + h.poster + '")'; po.classList.add('show'); } else po.classList.remove('show');
    var meta = []; if (h.year) meta.push(String(h.year).substr(0, 4)); if (h.rating && Number(h.rating) > 0) meta.push('★ ' + Number(h.rating).toFixed(1)); if (h.genre) meta.push(h.genre); meta.push(h.type === 'series' ? T('nav.series') : T('nav.movies'));
    U.$('#hero-meta').innerHTML = meta.map(function (x) { return '<span>' + U.esc(x) + '</span>'; }).join('');
    TMDB.enrich(h).then(function (it) { if (titleEl._item === it && it.tmdbId) setHeroStatic(it, tag); });
  }
  function setHeroStatic(h, tag) { var t = U.$('#hero-title'); t.textContent = TMDB.cleanTitle(h.name); U.$('#hero-desc').textContent = h.plot || ''; if (h.backdrop) U.$('#hero-bg').style.backgroundImage = 'url("' + h.backdrop + '")'; var meta = []; if (h.year) meta.push(String(h.year).substr(0, 4)); if (h.rating) meta.push('★ ' + Number(h.rating).toFixed(1)); if (h.genre) meta.push(h.genre); if (h.duration) meta.push(h.duration); U.$('#hero-meta').innerHTML = meta.map(function (x) { return '<span>' + U.esc(x) + '</span>'; }).join(''); }
  /* ---- "On Now" row: favourite channels with live EPG progress ---- */
  function onNowRow(chs) {
    var r = U.el('div', 'row'); r.innerHTML = '<div class="row-title">' + U.esc(T('home.onNow')) + '<span class="live-dot"></span><span class="count">' + chs.length + '</span></div>';
    var inner = U.el('div', 'row-items'); r.appendChild(inner); r._inner = inner;
    chs.forEach(function (ch) {
      var c = U.el('div', 'card now focusable'); c.setAttribute('data-nav', 'row'); c._item = ch;
      c.innerHTML = '<div class="nw-head"><div class="nw-logo" style="' + (ch.logo ? 'background-image:url(\'' + U.esc(ch.logo) + '\')' : '') + '"></div><div class="nw-name">' + U.esc(ch.name) + '</div><span class="nw-live">' + U.esc(T('home.live')) + '</span></div><div class="nw-prog">…</div><div class="nw-bar"><i></i></div><div class="nw-time"><span></span><span></span></div><div class="nw-next"></div>';
      c.onclick = function () { openItem(ch, chs); };
      inner.appendChild(c);
      fillOnNow(c, ch);
    });
    clearInterval(hero.onNowTimer); hero.onNowTimer = setInterval(function () { if (section !== 'home' || !document.body.contains(r)) { clearInterval(hero.onNowTimer); return; } U.$$('.card.now', r).forEach(function (c) { fillOnNow(c, c._item); }); }, 60000);
    return r;
  }
  function fillOnNow(c, ch) {
    var cache = fillOnNow._c = fillOnNow._c || {}; var e = cache[ch.id];
    var paint = function (list) {
      var now = Date.now() / 1000, cur = null, nxt = null;
      list.forEach(function (x) { if (x.start <= now && x.end > now) cur = x; else if (x.start > now && !nxt) nxt = x; });
      U.$('.nw-prog', c).textContent = cur ? cur.title : (list.length ? '—' : T('noEpg'));
      U.$('.nw-bar i', c).style.width = cur ? Math.round((now - cur.start) / (cur.end - cur.start) * 100) + '%' : '0%';
      var ts = U.$$('.nw-time span', c); ts[0].textContent = cur ? U.hm(cur.start) + ' – ' + U.hm(cur.end) : ''; ts[1].textContent = cur ? Math.max(0, Math.round((cur.end - now) / 60)) + ' ' + T('home.min') + ' ' + T('home.left') : '';
      U.$('.nw-next', c).textContent = nxt ? T('home.upnext') + ': ' + U.hm(nxt.start) + ' ' + nxt.title : '';
    };
    if (e && Date.now() - e.at < 5 * 60000) { paint(e.list); return; }
    provider.shortEPG(ch.epgId || ch.id, 4).then(function (list) { cache[ch.id] = { at: Date.now(), list: list }; if (document.body.contains(c)) paint(list); });
  }
  function hydrate(h) { var it = {}; for (var k in h) it[k] = h[k]; return it; }
  function adultCatIds() { var bad = {}; if (!parentalOn()) return bad; [live.cats, movies.cats, series.cats].forEach(function (l) { l.forEach(function (x) { if (U.isAdult(x.name) || x.censored) bad[x.id] = 1; }); }); return bad; }
  function withAll(cats, name) { return [{ id: null, name: name }].concat(cats.filter(function (c) { return !(parentalOn() && (U.isAdult(c.name) || c.censored)); })); }

  /* ---------- live ---------- */
  function loadLive() {
    if (live.cats.length) { if (!Nav.current() || !Nav.visible(Nav.current())) Nav.focus(U.$('#live-cats .cat-item.selected') || U.$('#live-cats .cat-item')); return; }
    U.$('#live-cats')._vlist = null; U.$('#live-channels')._vlist = null; UI.skeletonList(U.$('#live-cats'), 8); UI.skeletonList(U.$('#live-channels'), 9);
    provider.liveCategories().then(function (cats) {
      live.cats = cats; U.$('#live-cats').classList.remove('sk-list');
      UI.renderCats(U.$('#live-cats'), withAll(cats, T('allChannels')), null, 'lcat', function (c) { selectLiveCat(c); });
      selectLiveCat({ id: null, name: T('allChannels') }); Nav.focus(U.$('#live-cats .cat-item'));
    }).catch(function (e) { UI.toast('Failed to load categories: ' + e.message, 3000, '⚠'); });
  }
  function selectLiveCat(c) {
    live.catId = c.id; U.$('#live-cat-title').textContent = c.name; U.$('#live-channels')._vlist = null; live.vl = null; UI.skeletonList(U.$('#live-channels'), 9);
    provider.liveStreams(c.id).then(function (list) {
      var bad = adultCatIds(); list = list.filter(function (x) { return !bad[x.catId]; });
      live.list = list; list.forEach(function (x) { x.catName = c.name; });
      U.$('#live-channels').classList.remove('sk-list');
      live.vl = UI.renderChannels(U.$('#live-channels'), list, live.selected && live.selected.id, function (ch) { previewChannel(ch); }, function (ch, i) { playLive(ch, i); });
      if (!list.length) U.$('#live-channels').innerHTML = '<div class="empty">' + T('noChannels') + '</div>';
    }).catch(function (e) { U.$('#live-channels').innerHTML = '<div class="empty">' + U.esc(e.message) + '</div>'; });
  }
  function previewChannel(ch) {
    if (!ch || (live.selected && live.selected.id === ch.id)) return;
    live.selected = ch; clearTimeout(live.previewTimer); clearTimeout(live.epgTimer);
    if (live.vl) live.vl.setSelected(ch.id);
    live.epgTimer = setTimeout(function () { provider.shortEPG(ch.id, 12).then(function (l) { if (live.selected === ch) UI.renderEpg(l); }); }, 600);
    if (previewAllowed() && !needsUnlock(ch) && !Nav.byPointer()) live.previewTimer = setTimeout(function () { startPreview(ch); }, 1500);
    else if (provider.prefetchUrl && !needsUnlock(ch)) live.prefetchTimer = (clearTimeout(live.prefetchTimer), setTimeout(function () { if (live.selected === ch) provider.prefetchUrl(UI.toPlayable(ch)); }, 500));
  }
  /* Preview opens a 2nd stream connection on the panel. Most subscriptions allow exactly 1: the preview then blocks the
     real playback ("stream does not start" / immediate kick) and every hovered channel hammers the server.
     auto = only when the account allows 2+ connections (or the provider does not tell us). */
  function previewAllowed() {
    var v = Store.settings().preview; if (v === 'off') return false; if (v === 'on') return true;
    return !(account && account.type === 'xtream' && account.maxConn === 1);
  }
  function startPreview(ch) {
    stopPreview(); var box = U.$('#live-preview'); box.innerHTML = '';
    var v = document.createElement('video'); v.autoplay = true; v.setAttribute('disableRemotePlayback', ''); box.appendChild(v); live.previewVideo = v;
    provider.streamUrl(ch).then(function (url) {
      if (live.selected !== ch) return;
      var eng = Store.settings().engine;
      if (/\.m3u8(\?|$)/i.test(url) && window.Hls && Hls.isSupported() && (eng === 'hlsjs' || (eng === 'auto' && !v.canPlayType('application/vnd.apple.mpegurl')))) { live.previewHls = new Hls({ enableWorker: false, maxBufferLength: 15 }); live.previewHls.loadSource(url); live.previewHls.attachMedia(v); }
      else { v.src = url; v.play().catch(function () { }); }
      v.onerror = function () { if (live.selected === ch && !v._retried) { v._retried = true; setTimeout(function () { if (live.selected === ch) { v.src = url; v.play().catch(function () { }); } }, 3000); } };
    }).catch(function () { });
  }
  function stopPreview() {
    var was = !!live.previewVideo; live.previewStoppedAt = was ? Date.now() : (live.previewStoppedAt || 0);
    clearTimeout(live.previewTimer);
    if (live.previewHls) { try { live.previewHls.destroy(); } catch (e) { } live.previewHls = null; }
    if (live.previewVideo) { try { live.previewVideo.pause(); live.previewVideo.removeAttribute('src'); live.previewVideo.load(); } catch (e) { } live.previewVideo = null; }
    var box = U.$('#live-preview'); if (box) box.innerHTML = '<div class="preview-placeholder">' + U.esc(T('selectChannel')) + '</div>';
  }
  function toggleLockChannel(ch) {
    if (!account.pin) { UI.toast(T('setPinFirst'), 3500, '🔒'); return; }
    var on = Store.toggleLock(account.id, ch.id); UI.toast(on ? T('channelLocked') : T('channelUnlocked'), 2000, on ? '🔒' : '🔓');
    if (live.vl) { var idx = live.vl.items.indexOf(ch); if (idx >= 0) live.vl.refreshItem(idx); }
    if (on && live.selected === ch) stopPreview();
  }
  var unlocked = {}; // channel ids unlocked this session
  function needsUnlock(ch) { return ch && ch.type === 'live' && Store.isLocked(account.id, ch.id) && !unlocked[ch.id]; }
  function askUnlock(ch) {
    return new Promise(function (resolve) {
      var buf = '', ov = U.$('#locked-overlay'), dots = U.$$('#lock-dots i');
      function paint() { dots.forEach(function (d, i) { d.classList.toggle('on', i < buf.length); }); }
      function done(ok) { ov.classList.remove('show'); document.removeEventListener('keydown', kd, true); if (ok) unlocked[ch.id] = 1; resolve(ok); }
      function kd(ev) {
        var c = ev.keyCode; ev.stopPropagation(); ev.preventDefault();
        if (c >= 48 && c <= 57) { buf = (buf + String(c - 48)).slice(0, 4); paint(); if (buf.length === 4) { if (buf === account.pin) done(true); else { buf = ''; paint(); U.$('#lock-dots').classList.add('shake'); setTimeout(function () { U.$('#lock-dots').classList.remove('shake'); }, 450); } } }
        else if (c === 461 || c === 27 || c === 8) done(false);
      }
      buf = ''; paint(); ov.classList.add('show'); document.addEventListener('keydown', kd, true);
    });
  }
  function playLive(ch, i) {
    if (needsUnlock(ch)) { askUnlock(ch).then(function (ok) { if (ok) playLive(ch, i); }); return; }
    stopPreview(); playerReturn = { screen: 'home' }; Player.reset(); showScreen('player');
    var opts = { list: live.list.length ? live.list : [ch], index: i != null ? i : live.list.indexOf(ch) };
    // give the panel a moment to release the preview connection before opening the real one (1-connection accounts)
    var settle = live.previewStoppedAt ? Math.max(0, 700 - (Date.now() - live.previewStoppedAt)) : 0;
    if (settle) { Player.showLoading(); setTimeout(function () { if (screen === 'player') Player.play(UI.toPlayable(ch), opts); }, settle); }
    else Player.play(UI.toPlayable(ch), opts);
  }
  function playLiveFrom(ch, list) { live.list = list; var i = list.indexOf(ch); playLive(ch, i < 0 ? 0 : i); }
  function playCatchup(ch, e) {
    var dur = Math.ceil((e.end - e.start) / 60);
    Promise.resolve(provider.catchupUrl(ch, e.start, dur)).then(function (url) {
      var it = UI.toPlayable(ch); it.type = 'catchup'; it.id = ch.id + ':' + e.start; it.title = e.title; it.subtitle = ch.name + ' · ' + U.hm(e.start) + ' – ' + U.hm(e.end) + ' (catch-up)';
      playerReturn = { screen: 'home' }; Player.reset(); showScreen('player'); Player.play(it, { url: url, list: [it], index: 0 });
    }).catch(function (err) { UI.toast('Catch-up unavailable: ' + err.message, 3000, '⚠'); });
  }

  /* ---------- movies / series ---------- */
  function loadMovies() {
    if (movies.cats.length) { if (!Nav.current() || !Nav.visible(Nav.current())) Nav.focus(U.$('#movies-cats .cat-item.selected') || U.$('#movies-cats .cat-item')); return; }
    U.$('#movies-cats')._vlist = null; UI.skeletonList(U.$('#movies-cats'), 8); U.$('#movies-grid')._vlist = null; UI.skeletonGrid(U.$('#movies-grid'));
    provider.vodCategories().then(function (cats) {
      movies.cats = cats; U.$('#movies-cats').classList.remove('sk-list');
      UI.renderCats(U.$('#movies-cats'), withAll(cats, T('allMovies')), null, 'mcat', function (c) { selectGridCat('movies', c); });
      selectGridCat('movies', { id: null, name: T('allMovies') }); Nav.focus(U.$('#movies-cats .cat-item'));
    }).catch(function (e) { UI.toast('Failed to load: ' + e.message, 3000, '⚠'); });
  }
  function loadSeries() {
    if (series.cats.length) { if (!Nav.current() || !Nav.visible(Nav.current())) Nav.focus(U.$('#series-cats .cat-item.selected') || U.$('#series-cats .cat-item')); return; }
    U.$('#series-cats')._vlist = null; UI.skeletonList(U.$('#series-cats'), 8); U.$('#series-grid')._vlist = null; UI.skeletonGrid(U.$('#series-grid'));
    provider.seriesCategories().then(function (cats) {
      series.cats = cats; U.$('#series-cats').classList.remove('sk-list');
      UI.renderCats(U.$('#series-cats'), withAll(cats, T('allSeries')), null, 'scat', function (c) { selectGridCat('series', c); });
      selectGridCat('series', { id: null, name: T('allSeries') }); Nav.focus(U.$('#series-cats .cat-item'));
    }).catch(function (e) { UI.toast('Failed to load: ' + e.message, 3000, '⚠'); });
  }
  function selectGridCat(kind, c) {
    var st = kind === 'movies' ? movies : series, grid = U.$('#' + kind + '-grid'); st.catId = c.id;
    U.$('#' + kind + '-cat-title').textContent = c.name; grid._vlist = null; UI.skeletonGrid(grid);
    var p = kind === 'movies' ? provider.vodStreams(c.id) : provider.seriesList(c.id);
    p.then(function (list) {
      var bad = adultCatIds(); list = list.filter(function (x) { return !bad[x.catId]; });
      st.list = list; U.$('#' + kind + '-count').textContent = list.length + ' items';
      if (!list.length) { grid._vlist = null; grid.innerHTML = '<div class="empty">No items in this category.</div>'; return; }
      st.vl = UI.renderGrid(grid, list, kind === 'movies' ? 'mgrid' : 'sgrid', function (it, l) { openItem(it, l); });
    }).catch(function (e) { grid.innerHTML = '<div class="empty">' + U.esc(e.message) + '</div>'; });
  }

  /* ---------- favorites / search / settings ---------- */
  function renderFavorites() {
    var rows = U.$('#fav-rows'); rows.innerHTML = ''; rows.style.transform = ''; var f = Store.favorites(account.id);
    var l = f.filter(function (x) { return x.type === 'live'; }), m = f.filter(function (x) { return x.type === 'movie'; }), s = f.filter(function (x) { return x.type === 'series'; });
    if (l.length) rows.appendChild(UI.row(T('fav.channels'), l.map(hydrate))); if (m.length) rows.appendChild(UI.row(T('fav.movies'), m.map(hydrate))); if (s.length) rows.appendChild(UI.row(T('fav.series'), s.map(hydrate)));
    if (!f.length) rows.appendChild(U.el('div', 'empty', T('fav.empty')));
    Nav.focus(U.$('#fav-rows .card') || U.$('.nav-item[data-section="favorites"]'));
  }
  var doSearch = U.debounce(function (q) {
    var rows = U.$('#search-rows'); rows.innerHTML = ''; rows.style.transform = ''; q = q.trim().toLowerCase();
    if (q.length < 2) { rows.innerHTML = '<div class="empty">Type at least 2 characters.</div>'; return; }
    UI.skeletonRows(rows, 1);
    Promise.all([provider.liveStreams().catch(function () { return []; }), provider.vodStreams().catch(function () { return []; }), provider.seriesList().catch(function () { return []; })]).then(function (r) {
      rows.innerHTML = ''; var bad = adultCatIds(); var f = function (x) { return !bad[x.catId] && (x.name || '').toLowerCase().indexOf(q) >= 0; };
      var l = r[0].filter(f).slice(0, 40), m = r[1].filter(f).slice(0, 40), s = r[2].filter(f).slice(0, 40);
      if (l.length) rows.appendChild(UI.row(T('search.channels'), l, { max: 20 })); if (m.length) rows.appendChild(UI.row(T('search.movies'), m, { max: 20 })); if (s.length) rows.appendChild(UI.row(T('search.series'), s, { max: 20 }));
      if (!l.length && !m.length && !s.length) rows.innerHTML = '<div class="empty">' + U.esc(T('search.none', { q: q })) + '</div>';
    });
  }, 350);
  function renderSettings() {
    var s = Store.settings();
    U.$$('#theme-swatches .swatch').forEach(function (b) { b.classList.toggle('active', b.getAttribute('data-theme-pick') === s.theme); });
    U.$('[data-setting="lang"]').textContent = I18n.name(s.lang);
    U.$('[data-setting="refresh"]').textContent = s.refreshHours ? T('refresh.h', { h: s.refreshHours }) : T('refresh.off');
    U.$('[data-setting="liveFormat"]').textContent = s.liveFormat === 'ts' ? 'MPEG-TS' : 'HLS (m3u8)';
    U.$('[data-setting="engine"]').textContent = { auto: 'Auto', native: 'Native', hlsjs: 'hls.js' }[s.engine];
    var pb = U.$('[data-setting="parental"]'); pb.textContent = account.kids ? T('set.lockedKids') : (s.parental ? T('on') : T('off'));
    U.$('[data-setting="autostart"]').textContent = s.autostart ? T('on') : T('off');
    U.$('[data-setting="preview"]').textContent = s.preview === 'on' ? T('on') : s.preview === 'off' ? T('off') : T('auto');
    U.$('#tmdb-key').value = s.tmdbKey || '';
    U.$$('#layout-swatches .swatch').forEach(function (b) { b.classList.toggle('active', b.getAttribute('data-layout-pick') === (s.layout || 'classic')); });
    U.$('[data-setting="focusStyle"]').textContent = T('focus.' + (s.focusStyle || 'glow'));
    U.$$('#accent-swatches .swatch').forEach(function (b) { b.classList.toggle('active', b.getAttribute('data-accent-pick') === (s.accent || 'auto')); });
    U.$('[data-setting="corners"]').textContent = T('corners.' + (s.corners || 'round'));
    U.$('[data-setting="glow"]').textContent = s.glow === false ? T('off') : T('on');
    U.$('[data-setting="pointer"]').textContent = T('pointer.' + (s.pointer || 'click'));
    U.$('[data-setting="largeUi"]').textContent = s.largeUi ? T('on') : T('off');
    U.$('[data-setting="liveGrid"]').textContent = s.liveGrid ? T('live.grid') : T('live.list');
    U.$('[data-setting="ambient"]').textContent = s.ambient ? T('on') : T('off');
    U.$('[data-setting="weather"]').textContent = s.weather ? T('on') : T('off');
    U.$('[data-setting="adhan"]').textContent = s.adhan ? T('on') : T('off');
    U.$('[data-setting="wxUnit"]').textContent = s.wxUnit === 'f' ? '°F' : '°C';
    U.$('[data-setting="autoNext"]').textContent = s.autoNext ? T('on') : T('off');
    U.$('#settings-account-info').textContent = account.name + ' · ' + account.type.toUpperCase() + (account.expires ? ' · ' + (expiryText(account.expires).text) + ' (' + new Date(account.expires).toLocaleDateString() + ')' : '') + (account.kids ? ' · Kids profile' : '');
    if (!Nav.current() || !Nav.visible(Nav.current())) Nav.focus(U.$('[data-setting="lang"]'));
  }
  function toggleSetting(k) {
    var s = Store.settings();
    if (k === 'lang') { Store.setSetting(k, I18n.next()); applyLang(); updateExpiry(null); live.cats = []; movies.cats = []; series.cats = []; if (section === 'home') renderHome(); }
    else if (k === 'refresh') { var steps = [0, 3, 6, 12, 24], i = steps.indexOf(s.refreshHours); Store.setSetting('refreshHours', steps[(i + 1) % steps.length]); scheduleRefresh(); }
    else if (k === 'theme') { var th = ['aurora', 'midnight', 'oled', 'ocean', 'crimson', 'emerald', 'sunset', 'royal']; Store.setSetting(k, th[(th.indexOf(s.theme) + 1) % th.length]); applyTheme(); }
    else if (k === 'preview') Store.setSetting(k, { auto: 'on', on: 'off', off: 'auto' }[s.preview] || 'auto');
    else if (k === 'liveFormat') Store.setSetting(k, s.liveFormat === 'ts' ? 'm3u8' : 'ts');
    else if (k === 'engine') Store.setSetting(k, { auto: 'native', native: 'hlsjs', hlsjs: 'auto' }[s.engine]);
    else if (k === 'parental') { if (account.kids) { UI.toast(T('kids.locked'), 2500, '🔒'); return; } Store.setSetting(k, !s[k]); }
    else if (k === 'corners') { Store.setSetting(k, s.corners === 'square' ? 'round' : 'square'); applyTheme(); }
    else if (k === 'glow') { Store.setSetting(k, s.glow === false); applyTheme(); }
    else if (k === 'pointer') { Store.setSetting(k, s.pointer === 'hover' ? 'click' : 'hover'); applyUi(); UI.toast(T('pointer.' + Store.settings().pointer), 2500, '🖱'); }
    else if (k === 'focusStyle') { Store.setSetting(k, { glow: 'ring', ring: 'zoom', zoom: 'glow' }[s.focusStyle] || 'glow'); applyUi(); }
    else if (k === 'largeUi') { Store.setSetting(k, !s.largeUi); applyUi(); live.cats = []; movies.cats = []; series.cats = []; }
    else if (k === 'liveGrid') { Store.setSetting(k, !s.liveGrid); live.cats = []; U.$('#live-channels')._vlist = null; }
    else if (k === 'weather') { Store.setSetting(k, !s.weather); Weather.refresh(); if (!Store.settings().weather) U.$('#weather').className = 'weather focusable'; }
    else if (k === 'adhan') { Store.setSetting(k, !s.adhan); Adhan.invalidate(); }
    else if (k === 'wxUnit') { Store.setSetting(k, s.wxUnit === 'f' ? 'c' : 'f'); Weather.invalidate(); Weather.refresh(); }
    else Store.setSetting(k, !s[k]);
    if (k === 'parental') { live.cats = []; movies.cats = []; series.cats = []; }
    renderSettings();
  }

  /* ---------- details ---------- */
  function openItem(it, list) {
    if (it.type === 'live') { var idx = -1; if (list) list.forEach(function (x, i) { if (x.id === it.id) idx = i; }); if (list && idx >= 0) live.list = list; playLive(it, idx >= 0 ? idx : 0); return; }
    if (it.type === 'episode') { playEpisode(it, [it]); return; }
    details.base = it; details.list = list || null; details.season = null;
    showScreen('details'); U.$('#screen-details').classList.remove('scrolled');
    U.$('#details-seasons').innerHTML = ''; U.$('#details-episodes').innerHTML = ''; U.$('#details-similar').innerHTML = '';
    U.$('#details-episodes').style.display = it.type === 'series' ? '' : 'none';
    UI.renderDetails(it, it);
    var resume = Store.getPos(account.id, 'movie:' + it.id); U.$('#details-resume').style.display = (it.type === 'movie' && resume) ? '' : 'none';
    if (resume) U.$('#details-resume').textContent = T('det.resume', { t: U.fmtTime(resume.pos) });
    // "More like this": same category (or same list), excluding the item itself
    var simSrc = (it.type === 'movie' ? provider.vodStreams(it.catId || null) : provider.seriesList(it.catId || null));
    Promise.resolve(simSrc).catch(function () { return list || []; }).then(function (l) {
      if (details.base !== it) return; var bad = adultCatIds(); l = (l || []).filter(function (x) { return x.id !== it.id && !bad[x.catId] && x.poster; });
      if (l.length < 4 && list) l = list.filter(function (x) { return x.id !== it.id; });
      var pick = []; var seed = String(it.id).length; for (var k = 0; k < l.length && pick.length < 20; k++) pick.push(l[(k * 7 + seed) % l.length]);
      var seen = {}; pick = pick.filter(function (x) { if (seen[x.id]) return false; seen[x.id] = 1; return true; });
      UI.renderSimilar(pick, function (x, ll) { openItem(x, ll); });
    });
    U.$('[data-action="details-play"]').style.display = it.type === 'movie' ? '' : 'none';
    Nav.focus(it.type === 'movie' ? U.$('[data-action="details-play"]') : U.$('[data-action="details-fav"]'));
    var p = it.type === 'movie' ? provider.vodInfo(it.id, it) : provider.seriesInfo(it.id, it);
    p.then(function (info) {
      if (details.base !== it) return; details.info = info;
      if (!info.poster) info.poster = it.poster; if (!info.name) info.name = it.name;
      UI.renderDetails(info, it);
      TMDB.enrich(info).then(function (i2) { if (details.base === it && i2.tmdbId) UI.renderDetails(i2, it); });
      if (it.type === 'series') {
        var seasons = info.seasons || [];
        if (!seasons.length) { U.$('#details-episodes').innerHTML = '<div class="empty">' + T('noEpisodes') + '</div>'; return; }
        var lastEp = Store.history(account.id).filter(function (h) { return h.type === 'episode' && String(h.seriesId) === String(it.id); })[0];
        var s0 = seasons.filter(function (s) { return lastEp && s.num === lastEp.season; })[0] || seasons[0];
        UI.renderSeasons(seasons, s0.num, function (s) { showSeason(s); });
        showSeason(s0);
        Nav.focus(U.$('.season-btn.active') || U.$('[data-action="details-fav"]'));
      }
    }).catch(function (e) { UI.toast('Details unavailable: ' + e.message, 3000, '⚠'); });
  }
  function showSeason(s) { details.season = s; UI.renderEpisodes(s.episodes, function (e) { playEpisode(e, s.episodes); }); }
  function playEpisode(e, list) {
    var it = UI.toPlayable(e); it.title = details.base ? details.base.name : e.name; it.subtitle = 'S' + U.pad(e.season) + 'E' + U.pad(e.episode) + ' · ' + e.name;
    it.poster = details.base && details.base.poster; it.seriesName = details.base && details.base.name;
    playerReturn = { screen: 'details' }; Player.reset(); showScreen('player');
    Player.play(it, { list: list, index: list.indexOf(e), resume: true });
  }
  function playMovie(resume) {
    var it = UI.toPlayable(details.base); if (details.info && details.info.ext) it.ext = details.info.ext;
    playerReturn = { screen: 'details' }; Player.reset(); showScreen('player');
    Player.play(it, { list: [details.base], index: 0, resume: !!resume });
  }
  function playTrailer() {
    var url = details.info && details.info.trailer; if (!url) return;
    // webOS cannot play YouTube pages inside <video>; open with the system YouTube app via Luna if available.
    if (window.PalmServiceBridge) {
      var b = new PalmServiceBridge(); b.onservicecallback = function () { }; b.call('luna://com.webos.applicationManager/launch', JSON.stringify({ id: 'youtube.leanback.v4', params: { contentTarget: url } }));
    } else window.open(url, '_blank');
  }
  function onPlaybackEnded() {
    var cur = Player.current();
    if (cur && cur.type === 'episode' && details.season) {
      var eps = details.season.episodes, i = -1; eps.forEach(function (e, k) { if (e.id === cur.id) i = k; });
      if (i >= 0 && i < eps.length - 1) {
        var nx = eps[i + 1];
        if (Store.settings().autoNext === false) { closePlayer(); return; }
        Player.autoNext('S' + U.pad(nx.season) + 'E' + U.pad(nx.episode) + ' · ' + nx.name, function () { playEpisode(nx, eps); }, function () { closePlayer(); });
        return;
      }
    }
    closePlayer();
  }
  function closePlayer() {
    Player.stop(); Player.reset();
    var r = playerReturn || { screen: 'home' }; playerReturn = null;
    showScreen(r.screen);
    if (r.screen === 'home') {
      if (section === 'home') renderHome(); else if (section === 'live') { if (live.vl && live.vl.items.length) live.vl.focus(); else Nav.focus(U.$('#live-cats .cat-item')); }
      else if (section === 'favorites') renderFavorites(); else Nav.focusScope('row') || Nav.focusFirst();
    } else if (r.screen === 'details') {
      if (details.season) UI.renderEpisodes(details.season.episodes, function (e) { playEpisode(e, details.season.episodes); });
      var rs = Store.getPos(account.id, 'movie:' + details.base.id); U.$('#details-resume').style.display = (details.base.type === 'movie' && rs) ? '' : 'none'; if (rs) U.$('#details-resume').textContent = T('det.resume', { t: U.fmtTime(rs.pos) });
      Nav.focusScope('episodes') || Nav.focus(U.$('[data-action="details-play"]'));
    }
  }

  /* ---------- focus & keys ---------- */
  function moveSpot(el) {
    var sp = U.$('#spot'); if (!sp || screen !== 'home') return; var r = el.getBoundingClientRect(), sc = U.scale || 1;
    var x = (r.left + r.width / 2) / sc - (parseFloat(U.$('#app').style.left) || 0) / sc, y = (r.top + r.height / 2) / sc - (parseFloat(U.$('#app').style.top) || 0) / sc;
    sp.style.transform = 'translate(' + Math.round(x) + 'px,' + Math.round(y) + 'px)';
    var fx = U.$('#bg-fx'); if (fx) fx.style.transform = 'translate(' + Math.round((x - 960) / 40) + 'px,' + Math.round((y - 540) / 40) + 'px)';
  }
  function onFocusChange(el, edgeDir) {
    if (!el || edgeDir) return;
    var scope = el.getAttribute('data-nav');
    moveSpot(el); noteActivity();
    if (screen === 'details') { var sd = U.$('#screen-details'); sd.classList.toggle('scrolled', scope === 'seasons' || scope === 'episodes' || scope === 'similar'); if (scope === 'similar' && el.classList.contains('card')) UI.scrollRowsTo(el); }
    if (el.classList.contains('card') && el.parentNode.classList.contains('row-items') && scope !== 'similar') UI.scrollRowsTo(el);
    else if (el._vlist) { /* virtual lists manage their own scrolling */ }
    else if (scope === 'episodes') UI.scrollEpisodes(el);
    else if (scope === 'osd') Player.showOsd();
    else if (scope === 'wx' && el.classList.contains('wx-day')) Weather.scrollDays(el);
    else if (scope === 'settings') { var wrap = U.$('.settings-wrap'), row = el; while (row && row !== wrap && !(row.classList && row.classList.contains('setting-row'))) row = row.parentNode; if (row && row !== wrap) { var viewH = 1080 - 176 - 40, top = row.offsetTop, h = row.offsetHeight, off = Math.max(0, top + h - viewH + 40); off = Math.min(off, Math.max(0, wrap.scrollHeight - viewH + 40)); if (top < 260) off = 0; wrap.style.transform = 'translateY(-' + off + 'px)'; } }
    if (scope === 'topnav' || scope === 'hero') { var rows = U.$('#sec-' + section + ' .rows'); if (rows) rows.style.transform = ''; U.$('#sec-home').classList.remove('rows-mode'); }
  }
  function onKey(name, code) {
    if (Adhan.handleKey(name)) return true;
    if (amb.on) { hideAmbient(); return true; }
    if (UI.modalOpen()) { if (name === 'BACK' || name === 'BACK2') { UI.closeModal(); return true; } return false; }
    if (screen === 'player') return Player.handleKey(name, code);
    if (screen === 'pin') {
      if (code >= 48 && code <= 57) { pinKey(String(code - 48)); return true; }
      if (name === 'BACK' || name === 'BACK2') { if (pin.buf) { pinKey('⌫'); } else cancelPin(); return true; }
      return false;
    }
    var typing = document.activeElement && document.activeElement.tagName === 'INPUT';
    if (name === 'BACK' || name === 'BACK2') {
      if (typing) { document.activeElement.blur(); return true; }
      if (screen === 'details') { showScreen('home'); if (section === 'movies') { if (movies.vl) movies.vl.focus(); else Nav.focusScope('mcat'); } else if (section === 'series') { if (series.vl) series.vl.focus(); else Nav.focusScope('scat'); } else { if (section === 'home') renderHome(); if (section === 'favorites') renderFavorites(); Nav.focusScope('row') || Nav.focusFirst(); } return true; }
      if (screen === 'add') { if (account && editingId === account.id) { showScreen('home'); Nav.focus(U.$('[data-action="edit-account"]') || U.$('.nav-item')); } else if (Store.accounts().length) showAccounts(); return true; }
      if (screen === 'pair') { stopPair(); return true; }
      if (screen === 'home') {
        if (section !== 'home') { showSection('home'); Nav.focus(document.body.classList.contains('hubmode') ? U.$('#hub .tile') : U.$('.nav-item[data-section="home"]')); return true; }
        UI.modal(T('exit.title'), T('exit.text'), [{ label: T('exit.switch'), value: 'switch', ghost: true }, { label: T('exit'), value: 'exit', danger: true }, { label: T('cancel'), value: null, ghost: true }]).then(function (v) { if (v === 'switch') showAccounts(); else if (v === 'exit') exitApp(); });
        return true;
      }
      if (screen === 'accounts') { if (manageMode) { manageMode = false; showAccounts(); return true; } UI.modal(T('exit.title'), T('exit.close'), [{ label: T('exit'), value: true, danger: true }, { label: T('cancel'), value: false, ghost: true }]).then(function (v) { if (v) exitApp(); }); return true; }
    }
    if (screen === 'home') {
      // explicit vertical navigation between rows (rows scroll under the hero; geometry alone is unreliable)
      if ((name === 'UP' || name === 'DOWN') && Nav.current() && Nav.current().classList.contains('card') && Nav.current().parentNode.classList.contains('row-items')) {
        var curCard = Nav.current(), curRow = curCard.parentNode.parentNode, rowsEl = curRow.parentNode, rIdx = Array.prototype.indexOf.call(rowsEl.children, curRow);
        var target = null; for (var k = rIdx + (name === 'UP' ? -1 : 1); k >= 0 && k < rowsEl.children.length; k += (name === 'UP' ? -1 : 1)) { if (rowsEl.children[k].classList.contains('row') && rowsEl.children[k].querySelector('.card')) { target = rowsEl.children[k]; break; } }
        if (target) { var cx = curCard.getBoundingClientRect().left + curCard.offsetWidth / 2, best = null, bd = 1e9; U.$$('.card', target).forEach(function (c) { var d = Math.abs(c.getBoundingClientRect().left + c.offsetWidth / 2 - cx); if (d < bd) { bd = d; best = c; } }); if (best) { Nav.focus(best); return true; } }
        if (name === 'UP') { var hp = U.$('[data-action="hero-play"]'); if (section === 'home' && hp) { Nav.focus(hp); return true; } }
      }
      if (name === 'RED' && section === 'live' && live.selected) { toggleFavItem(live.selected); return true; }
      if (name === 'RED' && (section === 'movies' || section === 'series' || section === 'home' || section === 'favorites' || section === 'search')) { var c = Nav.current(); if (c && c._item) { toggleFavItem(c._item); if (c._vlist) c._vlist.refreshItem(Number(c.getAttribute('data-i'))); else { var f = c.querySelector('.fav'); if (f) f.remove(); else if (c.querySelector('.thumb')) c.querySelector('.thumb').appendChild(U.el('div', 'fav', '★')); } return true; } }
      if (name === 'GREEN') { showSection('favorites'); Nav.focus(U.$('.nav-item[data-section="favorites"]')); return true; }
      if (name === 'YELLOW' && section === 'live' && live.selected) { toggleLockChannel(live.selected); return true; }
      if (name === 'YELLOW') { showSection('search'); return true; }
      if ((name === 'CH_UP' || name === 'CH_DOWN') && !(Nav.current() && Nav.current()._vlist)) { var order = NAV_ORDER, i = order.indexOf(section); showSection(order[(i + (name === 'CH_UP' ? 1 : order.length - 1)) % order.length]); Nav.focus(U.$('.nav-item[data-section="' + section + '"]')); return true; }
    }
    if (screen === 'accounts' && (name === 'RED' || name === 'INFO')) { var cc = Nav.current(); if (cc && cc.classList.contains('profile') && cc.getAttribute('data-id')) { accountMenu(cc.getAttribute('data-id')); return true; } }
    if (screen === 'details' && name === 'RED') { toggleFavItem(details.base); return true; }
    return false;
  }
  function toggleFavItem(it) {
    if (!it) return;
    var on = Store.toggleFav(account.id, { type: it.type, id: it.id, name: it.name, logo: it.logo, poster: it.poster, backdrop: it.backdrop, ext: it.ext, cmd: it.cmd, url: it.url, catId: it.catId, num: it.num, epgId: it.epgId, year: it.year, rating: it.rating, plot: it.plot, series: it.series, archive: it.archive });
    UI.toast(on ? T('toast.favAdd') : T('toast.favDel'), 2000, on ? '★' : '☆');
    if (screen === 'details') U.$('[data-action="details-fav"]').textContent = on ? T('favorited') : T('favorite');
    if (live.vl) { var idx = live.vl.items.indexOf(it); if (idx >= 0) live.vl.refreshItem(idx); }
  }
  /* ---------- ambient screensaver (5 min idle outside the player) ---------- */
  var amb = { last: Date.now(), on: false, timer: null, idx: 0, pool: [], flip: 0 };
  function noteActivity() { amb.last = Date.now(); if (amb.on) hideAmbient(); }
  function initAmbient() {
    document.addEventListener('keydown', noteActivity, true); document.addEventListener('mousemove', noteActivity, true); document.addEventListener('click', noteActivity, true);
    document.addEventListener('touchstart', noteActivity, true);
    setInterval(function () { if (amb.on || !Store.settings().ambient || !account || document.body.classList.contains('mobile')) return; if (screen === 'player' || screen === 'splash' || screen === 'pin' || UI.modalOpen()) { amb.last = Date.now(); return; } if (live.previewVideo) { amb.last = Date.now(); return; } if (Date.now() - amb.last > 5 * 60000) showAmbient(); }, 10000);
  }
  function showAmbient() {
    var pool = []; var favs = Store.favorites(account.id), hist = Store.history(account.id);
    hist.concat(favs).forEach(function (x) { if (x.backdrop || x.poster) pool.push(x); }); hero.items.forEach(function (h) { if (h.it.backdrop || h.it.poster) pool.push(h.it); });
    amb.pool = pool; amb.idx = 0; amb.on = true; U.$('#ambient').classList.add('show'); tickAmbient(); amb.timer = setInterval(tickAmbient, 12000);
    if (!pool.length) provider.vodStreams().then(function (m) { if (!amb.on) return; amb.pool = (m || []).filter(function (x) { return x.poster; }).slice(0, 40); tickAmbient(); }).catch(function () { });
  }
  function tickAmbient() {
    var d = new Date(); U.$('#amb-time').textContent = U.clock(d); try { U.$('#amb-date').textContent = d.toLocaleDateString(I18n.get() === 'ar' ? 'ar' : 'en-GB', { weekday: 'long', day: 'numeric', month: 'long' }); } catch (e) { }
    if (!amb.pool.length) return; var it = amb.pool[amb.idx % amb.pool.length]; amb.idx++;
    var a = U.$('#amb-bg1'), b = U.$('#amb-bg2'), show = amb.flip ? a : b, hide = amb.flip ? b : a; amb.flip = !amb.flip;
    show.style.backgroundImage = 'url("' + (it.backdrop || it.poster) + '")'; show.classList.add('on'); hide.classList.remove('on');
    U.$('#amb-title').textContent = TMDB.cleanTitle(it.name || '');
  }
  function hideAmbient() { amb.on = false; clearInterval(amb.timer); U.$('#ambient').classList.remove('show'); U.$('#amb-bg1').classList.remove('on'); U.$('#amb-bg2').classList.remove('on'); }

  function exitApp() { try { if (window.RGBTvHost && RGBTvHost.exit) { RGBTvHost.exit(); return; } } catch (e) { } try { if (window.RGBTvDesktop && RGBTvDesktop.exit) { RGBTvDesktop.exit(); return; } } catch (e) { } try { if (window.webOS && webOS.platformBack) webOS.platformBack(); } catch (e) { } try { window.close(); } catch (e) { } }

  function bindEvents() {
    document.addEventListener('click', function (ev) {
      var t = ev.target; while (t && t !== document && !(t.getAttribute && (t.getAttribute('data-action') || t.getAttribute('data-section') || t.getAttribute('data-type') || t.getAttribute('data-setting') || t.getAttribute('data-theme-pick') || t.getAttribute('data-layout-pick') || t.getAttribute('data-accent-pick') || t.id === 'kids-switch'))) t = t.parentNode;
      if (!t || t === document) return;
      var a = t.getAttribute('data-action'), sec = t.getAttribute('data-section'), typ = t.getAttribute('data-type'), set = t.getAttribute('data-setting');
      if (t.id === 'kids-switch') { t.setAttribute('data-on', t.getAttribute('data-on') === '1' ? '0' : '1'); return; }
      if (sec) { showSection(sec); if ((t.classList.contains('tile') || t.classList.contains('util')) && !document.body.classList.contains('hubmode')) Nav.focus(U.$('.nav-item[data-section="' + sec + '"]')); else if (t.id === 'hub-home') Nav.focus(U.$('#hub .tile')); return; }
      if (typ && t.classList.contains('tab')) { setAddType(typ); return; }
      if (set) { toggleSetting(set); return; }
      var tp = t.getAttribute('data-theme-pick'); if (tp) { Store.setSetting('theme', tp); applyTheme(); renderSettings(); return; }
      var lp = t.getAttribute('data-layout-pick'); if (lp) { Store.setSetting('layout', lp); applyUi(); renderSettings(); UI.toast(T('lay.' + lp), 2000, '✓'); return; }
      var ap = t.getAttribute('data-accent-pick'); if (ap) { Store.setSetting('accent', ap); applyTheme(); renderSettings(); return; }
      switch (a) {
        case 'add-account': showAddForm(null); break;
        case 'manage-profiles': manageMode = !manageMode; showAccounts(true); Nav.focus(U.$('#manage-btn')); break;
        case 'cancel-add': onKey('BACK'); break;
        case 'switch-account': showAccounts(); break;
        case 'edit-account': if (account) { var ea = account; requirePin(ea).then(function (ok) { if (ok) { manageMode = false; showAddForm(ea); } else { showScreen('home'); Nav.focus(U.$('[data-action="edit-account"]')); } }); } break;
        case 'exit-app': exitApp(); break;
        case 'host-back': Nav.press(461); break;
        case 'pin-cancel': cancelPin(); break;
        case 'pair-phone': showPair(); break;
        case 'wx-location': case 'wx-unit': case 'wx-refresh': case 'wx-retry': Weather.action(a); break;
        case 'adhan-enable': case 'adhan-toggle': case 'adhan-method': case 'adhan-test': case 'adhan-refresh': case 'adhan-retry': Adhan.action(a); break;
        case 'pair-cancel': stopPair(); break;
        case 'clear-cache': Store.clearCache(account.id); live.cats = []; movies.cats = []; series.cats = []; UI.toast(T('toast.cache'), 2000, '✓'); break;
        case 'refresh-now': refreshPlaylists(true); break;
        case 'hero-play': var h = U.$('#hero-title')._item; if (h) openItem(h); else showSection('live'); break;
        case 'hero-info': var h2 = U.$('#hero-title')._item; if (h2) openItem(h2); else showSection('movies'); break;
        case 'details-play': playMovie(false); break;
        case 'details-resume': playMovie(true); break;
        case 'details-trailer': playTrailer(); break;
        case 'details-fav': toggleFavItem(details.base); break;
        case 'details-back': onKey('BACK'); break;
        default: if (a && a.indexOf('p-') === 0) Player.action(a);
      }
    });
    U.$('#add-form').addEventListener('submit', saveAccount);
    U.$('#search-input').addEventListener('input', function () { doSearch(this.value); });
    U.$('#search-input').addEventListener('keydown', function (ev) { if (ev.keyCode === 13) { this.blur(); doSearch(this.value); ev.preventDefault(); } });
    U.$('#tmdb-key').addEventListener('change', function () { Store.setSetting('tmdbKey', this.value.trim()); UI.toast(this.value.trim() ? 'TMDB enabled' : 'TMDB disabled', 2000, '✓'); });
    U.$('#tmdb-key').addEventListener('keydown', function (ev) { if (ev.keyCode === 13) { this.blur(); } });
    U.$('#acc-list').addEventListener('contextmenu', function (ev) { ev.preventDefault(); var t = ev.target; while (t && !(t.classList && t.classList.contains('profile'))) t = t.parentNode; if (t && t.getAttribute('data-id')) accountMenu(t.getAttribute('data-id')); });
  }

  window.addEventListener('load', init);
  return { openAccount: openAccount, openItem: openItem, closePlayer: closePlayer, isScreen: isScreen, activeScreen: activeScreen, account: null, provider: null, accountMenu: accountMenu, showAddForm: showAddForm, playLiveFrom: playLiveFrom, playCatchup: playCatchup };
})();
