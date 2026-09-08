/* RGBTv — Adhan & prayer times (v1.9).
 * Times from AlAdhan API (monthly calendar, cached per month + location). Location shared with the Weather module
 * (auto by network, or the manual city). Checks every 20s; at prayer time shows a full-width designed banner
 * for 25 s — also on top of the player. Visual only (no audio, keeps the package tiny). */
var Adhan = (function () {
  var DEV = typeof window.PalmServiceBridge === 'undefined' && (location.hostname === 'localhost' || location.hostname === '127.0.0.1');
  var API = DEV ? '/__adhan/calendar' : 'https://api.aladhan.com/v1/calendar';
  var NAMES = ['Fajr', 'Dhuhr', 'Asr', 'Maghrib', 'Isha'];
  var METHODS = [
    { id: 19, key: 'algeria' }, { id: 3, key: 'mwl' }, { id: 5, key: 'egypt' }, { id: 4, key: 'makkah' },
    { id: 2, key: 'isna' }, { id: 12, key: 'france' }, { id: 13, key: 'turkey' }, { id: 21, key: 'morocco' }, { id: 18, key: 'tunisia' }
  ];
  var ICON = '<svg viewBox="0 0 24 24"><path d="M12 2c.6 1.8 1.6 3 3 3.6-1.4.6-2.4 1.8-3 3.6-.6-1.8-1.6-3-3-3.6 1.4-.6 2.4-1.8 3-3.6zM4 22v-9l3-2.5V8h2v2.5l3 2.5v9h-2v-6H6v6H4zm11-13.5c1.6-1 3.8-.9 5.3.4-1.2 0-2.3.5-3.1 1.3-.7.8-1 1.9-1 3 0 1.2.4 2.3 1.2 3.1.8.8 1.9 1.2 3 1.1-1.6 1.3-3.8 1.4-5.4.4-1.9-1.2-2.7-3.6-1.9-5.7.4-1.1 1.1-2 1.9-2.6z"/></svg>';
  var day = null, dayKey = '', monthPending = null, timer = null, banner = null, nextInfo = null, fired = {}, SHOW_MS = 25000;

  function S() { return Store.settings(); }
  function enabled() { return !!S().adhan; }
  function methodId() { var m = S().adhanMethod; for (var i = 0; i < METHODS.length; i++) if (METHODS[i].key === m) return METHODS[i].id; return 19; }
  function pad(n) { return (n < 10 ? '0' : '') + n; }
  function ymd(d) { return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()); }
  function hhmm(s) { var m = /^(\d{1,2}):(\d{2})/.exec(s || ''); return m ? { h: Number(m[1]), m: Number(m[2]), t: pad(Number(m[1])) + ':' + m[2] } : null; }
  function locKey(g) { return (Math.round(g.lat * 20) / 20) + ',' + (Math.round(g.lon * 20) / 20) + ':' + methodId(); }

  /* ---- location: same source as the weather chip ---- */
  function getLocation() {
    var man = Store.get('geoManual'); if (S().wxMode === 'manual' && man) return Promise.resolve(man);
    var cached = Store.get('geo'); if (cached && cached.lat) return Promise.resolve(cached);
    return Weather.locate ? Weather.locate() : Promise.reject(new Error('no location'));
  }

  /* ---- monthly calendar (cached) ---- */
  function fetchMonth(g, y, m) {
    var key = 'adhan:' + y + '-' + m + ':' + locKey(g), cached = Store.get(key);
    if (cached && cached.days) return Promise.resolve(cached);
    if (monthPending && monthPending.key === key) return monthPending.p;
    var p = U.getJSON(API + '/' + y + '/' + m + '?latitude=' + g.lat + '&longitude=' + g.lon + '&method=' + methodId() + '&iso8601=false').then(function (r) {
      if (!r || !r.data || !r.data.length) throw new Error('bad adhan');
      var days = {};
      r.data.forEach(function (x) {
        var gd = x.date.gregorian.date.split('-'); // DD-MM-YYYY
        var t = {}; NAMES.concat(['Sunrise']).forEach(function (n) { var v = hhmm(x.timings[n]); if (v) t[n] = v.t; });
        days[gd[2] + '-' + gd[1] + '-' + gd[0]] = { t: t, hijri: x.date.hijri ? { d: x.date.hijri.day, m: x.date.hijri.month, y: x.date.hijri.year, wd: x.date.hijri.weekday } : null };
      });
      var o = { at: Date.now(), days: days, tz: r.data[0].meta && r.data[0].meta.timezone, method: r.data[0].meta && r.data[0].meta.method && r.data[0].meta.method.name };
      Store.set(key, o);
      // keep storage small: drop calendars older than last month
      try { var pm = new Date(y, m - 3, 1); Store.del('adhan:' + pm.getFullYear() + '-' + (pm.getMonth() + 1) + ':' + locKey(g)); } catch (e) { }
      return o;
    });
    monthPending = { key: key, p: p }; p.then(function () { monthPending = null; }, function () { monthPending = null; });
    return p;
  }
  function today(force) {
    var d = new Date(), k = ymd(d);
    if (!force && day && dayKey === k) return Promise.resolve(day);
    return getLocation().then(function (g) {
      return fetchMonth(g, d.getFullYear(), d.getMonth() + 1).then(function (mo) {
        var t = mo.days[k]; if (!t) throw new Error('no day');
        // tomorrow (for "next: Fajr" after Isha) — may live in the next month
        var tm = new Date(d.getTime() + 86400e3), tk = ymd(tm), tmo = mo.days[tk] ? Promise.resolve(mo) : fetchMonth(g, tm.getFullYear(), tm.getMonth() + 1).catch(function () { return mo; });
        return tmo.then(function (mo2) { day = { key: k, times: t.t, hijri: t.hijri, tomorrow: (mo2.days[tk] || {}).t || null, loc: g, method: mo.method }; dayKey = k; return day; });
      });
    });
  }

  /* ---- next prayer ---- */
  function minutesNow() { var d = new Date(); return d.getHours() * 60 + d.getMinutes() + d.getSeconds() / 60; }
  function toMin(t) { var v = hhmm(t); return v ? v.h * 60 + v.m : null; }
  function computeNext(dd) {
    var now = minutesNow(), best = null;
    for (var i = 0; i < NAMES.length; i++) { var m = toMin(dd.times[NAMES[i]]); if (m != null && m > now) { best = { name: NAMES[i], time: dd.times[NAMES[i]], inMin: m - now, tomorrow: false }; break; } }
    if (!best && dd.tomorrow && dd.tomorrow.Fajr) { var fm = toMin(dd.tomorrow.Fajr); best = { name: 'Fajr', time: dd.tomorrow.Fajr, inMin: 1440 - now + fm, tomorrow: true }; }
    return best;
  }
  function fmtIn(min) {
    min = Math.max(0, Math.round(min)); var h = Math.floor(min / 60), m = min % 60;
    if (h) return I18n.t('adhan.inHM', { h: h, m: m }); return I18n.t('adhan.inM', { m: m });
  }
  function prayerName(n) { return I18n.t('adhan.' + n.toLowerCase()); }

  /* ---- tick: chip + trigger ---- */
  function tick() {
    if (!enabled()) { setChip(null); return; }
    today().then(function (dd) {
      nextInfo = computeNext(dd); setChip(nextInfo);
      // fire: any prayer whose minute just passed (tolerate up to 90s late so a sleeping timer still fires once)
      var now = minutesNow();
      NAMES.forEach(function (n) {
        var m = toMin(dd.times[n]); if (m == null) return;
        var id = dd.key + ':' + n;
        if (now >= m && now - m < 1.5 && !fired[id] && !Store.get('adhanFired:' + id)) { fired[id] = true; Store.set('adhanFired:' + id, 1); announce(n, dd.times[n]); }
      });
    }).catch(function () { setChip(null); });
  }
  function setChip(nx) {
    var el = U.$('#adhan-chip'); if (!el) return;
    if (!nx) { el.className = 'adhan-chip focusable'; el.innerHTML = ''; return; }
    el.className = 'adhan-chip focusable show' + (nx.inMin <= 15 ? ' soon' : '');
    el.innerHTML = ICON + '<span class="ac-n">' + U.esc(prayerName(nx.name)) + '</span><span class="ac-t">' + U.esc(nx.time) + '</span>';
    var hub = U.$('#hub-adhan'); if (hub) hub.textContent = I18n.t('adhan.next') + ': ' + prayerName(nx.name) + ' ' + nx.time;
  }

  /* ---- the notification banner ---- */
  function ensureBanner() {
    if (banner) return banner;
    banner = U.el('div', 'adhan-banner'); banner.id = 'adhan-banner';
    banner.innerHTML = '<div class="ab-glow"></div><div class="ab-pattern"></div><div class="ab-ico">' + ICON + '</div>' +
      '<div class="ab-text"><div class="ab-kicker" id="ab-kicker"></div><div class="ab-title" id="ab-title"></div><div class="ab-sub" id="ab-sub"></div></div>' +
      '<div class="ab-right"><div class="ab-time" id="ab-time"></div><div class="ab-hint" id="ab-hint"></div></div><i class="ab-bar" id="ab-bar"></i>';
    U.$('#app').appendChild(banner);
    return banner;
  }
  var hideTimer = null;
  function announce(name, time, preview) {
    var b = ensureBanner();
    U.$('#ab-kicker').textContent = I18n.t('adhan.kicker');
    U.$('#ab-title').textContent = I18n.t('adhan.itsTime', { p: prayerName(name) });
    U.$('#ab-sub').innerHTML = (day && day.hijri ? '<span>' + U.esc(hijriText(day.hijri)) + '</span>' : '') + (day && day.loc && day.loc.city ? '<span class="ab-sep"> · </span><span dir="auto">' + U.esc(day.loc.city) + '</span>' : '');
    U.$('#ab-time').textContent = time;
    U.$('#ab-hint').textContent = I18n.t('adhan.hintClose');
    b.setAttribute('data-p', name.toLowerCase());
    b.classList.remove('show'); void b.offsetWidth; b.classList.add('show');
    var bar = U.$('#ab-bar'); bar.style.transition = 'none'; bar.style.transform = 'scaleX(1)'; void bar.offsetWidth; bar.style.transition = 'transform ' + SHOW_MS + 'ms linear'; bar.style.transform = 'scaleX(0)';
    clearTimeout(hideTimer); hideTimer = setTimeout(hideBanner, SHOW_MS);
  }
  function hijriText(h) { if (!h) return ''; var ar = I18n.get() === 'ar'; return h.d + ' ' + (ar ? h.m.ar : h.m.en) + ' ' + h.y; }
  function hideBanner() { if (banner) banner.classList.remove('show'); clearTimeout(hideTimer); }
  /* key hook: any key while the banner shows → hide it (key is consumed) */
  function handleKey(name) {
    if (banner && banner.classList.contains('show')) { hideBanner(); return true; }
    return false;
  }

  /* ---- the Adhan page (section) ---- */
  function open() {
    var root = U.$('#sec-adhan'); root.innerHTML = '<div class="wx-loading"><div class="loader"></div></div>';
    if (!enabled()) { renderOff(root); return; }
    today(true).then(function (dd) { render(root, dd); }).catch(function () {
      root.innerHTML = '<div class="wx-empty"><div class="wx-empty-ico">' + ICON + '</div><h2>' + U.esc(I18n.t('adhan.unavailable')) + '</h2><p>' + U.esc(I18n.t('wx.unavailable.d')) + '</p><div class="wx-actions"><button class="btn focusable" data-nav="adhan" data-action="wx-location">' + Weather.icons.pin + '<span>' + U.esc(I18n.t('wx.setLocation')) + '</span></button><button class="btn ghost focusable" data-nav="adhan" data-action="adhan-retry">' + U.esc(I18n.t('retry')) + '</button></div></div>';
      Nav.focus(U.$('[data-action="adhan-retry"]', root));
    });
  }
  function renderOff(root) {
    root.innerHTML = '<div class="wx-empty"><div class="wx-empty-ico">' + ICON + '</div><h2>' + U.esc(I18n.t('adhan.off')) + '</h2><p>' + U.esc(I18n.t('adhan.off.d')) + '</p><div class="wx-actions"><button class="btn focusable" data-nav="adhan" data-action="adhan-enable">' + ICON + '<span>' + U.esc(I18n.t('adhan.enable')) + '</span></button></div></div>';
    Nav.focus(U.$('[data-action="adhan-enable"]', root));
  }
  function render(root, dd) {
    var s = S(), nx = computeNext(dd), wrap = U.el('div', 'ad-wrap'); root.innerHTML = '';
    // header: location + hijri + actions
    var head = U.el('div', 'wx-head');
    head.innerHTML = '<div class="wx-loc">' + Weather.icons.pin + '<span class="wx-city">' + U.esc(dd.loc.city || '—') + '</span><span class="wx-country">' + U.esc([dd.loc.region, dd.loc.country].filter(Boolean).join(', ')) + '</span><span class="wx-mode">' + U.esc(s.wxMode === 'manual' ? I18n.t('wx.manual') : I18n.t('wx.auto')) + '</span></div>' +
      '<div class="wx-actions">' +
      '<button class="btn ghost focusable" data-nav="adhan" data-action="wx-location">' + Weather.icons.pin + '<span>' + U.esc(I18n.t('wx.changeLocation')) + '</span></button>' +
      '<button class="btn ghost focusable" data-nav="adhan" data-action="adhan-test"><svg viewBox="0 0 24 24"><path d="M8 5v14l11-7z"/></svg><span>' + U.esc(I18n.t('adhan.test')) + '</span></button>' +
      '<button class="btn ghost focusable" data-nav="adhan" data-action="adhan-refresh">↻</button></div>';
    wrap.appendChild(head);
    // hero: next prayer
    var hero = U.el('div', 'ad-hero'); hero.setAttribute('data-p', nx ? nx.name.toLowerCase() : 'isha');
    hero.innerHTML = '<div class="ad-hero-ico">' + ICON + '</div><div class="ad-hero-main"><div class="ad-k">' + U.esc(I18n.t('adhan.next')) + '</div><div class="ad-name">' + (nx ? U.esc(prayerName(nx.name)) : '—') + '</div><div class="ad-in">' + (nx ? U.esc(fmtIn(nx.inMin)) + (nx.tomorrow ? ' · ' + U.esc(I18n.t('adhan.tomorrow')) : '') : '') + '</div></div>' +
      '<div class="ad-hero-time"><div class="ad-big">' + (nx ? U.esc(nx.time) : '') + '</div><div class="ad-date">' + U.esc(hijriText(dd.hijri)) + '<br>' + U.esc(new Date().toLocaleDateString(I18n.get() === 'ar' ? 'ar' : 'en-GB', { weekday: 'long', day: 'numeric', month: 'long' })) + '</div></div>';
    wrap.appendChild(hero);
    // today's five (+ sunrise)
    var list = U.el('div', 'ad-list'), order = ['Fajr', 'Sunrise', 'Dhuhr', 'Asr', 'Maghrib', 'Isha'];
    order.forEach(function (n) {
      var c = U.el('div', 'ad-item focusable' + (nx && nx.name === n && !nx.tomorrow ? ' next' : '') + (n === 'Sunrise' ? ' sunrise' : '') + (toMin(dd.times[n]) < minutesNow() ? ' past' : ''));
      c.setAttribute('data-nav', 'adhan'); c.setAttribute('data-p', n.toLowerCase());
      c.innerHTML = '<div class="ai-ico">' + (n === 'Sunrise' ? Weather.icons.sunrise : ICON) + '</div><div class="ai-n">' + U.esc(prayerName(n)) + '</div><div class="ai-t">' + U.esc(dd.times[n] || '—') + '</div>' + (nx && nx.name === n && !nx.tomorrow ? '<div class="ai-tag">' + U.esc(I18n.t('adhan.next')) + '</div>' : '');
      list.appendChild(c);
    });
    wrap.appendChild(list);
    // settings strip
    var st = U.el('div', 'ad-settings');
    st.innerHTML = '<div class="row-title">' + U.esc(I18n.t('adhan.settings')) + '</div>' +
      pill('adhan-method', I18n.t('adhan.method'), I18n.t('adhan.m.' + (s.adhanMethod || 'algeria')), true) +
      pill('adhan-toggle', I18n.t('adhan.enabled'), I18n.t('on'), true);
    wrap.appendChild(st);
    var foot = U.el('p', 'ad-foot muted'); foot.textContent = I18n.t('adhan.source') + (dd.method ? ' · ' + dd.method : ''); wrap.appendChild(foot);
    root.appendChild(wrap);
    var pf = pendingFocus ? U.$('[data-action="' + pendingFocus + '"]', root) : null; pendingFocus = null;
    if (pf) Nav.focus(pf); else { var keep = Nav.current(); if (!keep || !root.contains(keep)) Nav.focus(U.$('.ad-item.next', root) || U.$('.ad-item', root)); }
  }
  function pill(action, label, val, on) { return '<button class="ad-pill focusable' + (on ? ' on' : '') + '" data-nav="adhan" data-action="' + action + '"><span class="ap-l">' + U.esc(label) + '</span><b class="ap-v">' + U.esc(val) + '</b></button>'; }

  function action(a) {
    var s = S();
    if (a === 'adhan-enable' || a === 'adhan-toggle') { Store.setSetting('adhan', !s.adhan); if (Store.settings().adhan) { open(); tick(); } else { setChip(null); open(); } }
    else if (a === 'adhan-method') { var mi = 0; for (var q = 0; q < METHODS.length; q++) if (METHODS[q].key === s.adhanMethod) mi = q; Store.setSetting('adhanMethod', METHODS[(mi + 1) % METHODS.length].key); day = null; refocus(a); tick(); }
    else if (a === 'adhan-test') { var nx = nextInfo || { name: 'Maghrib', time: (day && day.times.Maghrib) || '19:09' }; announce(nx.name, nx.time, true); }
    else if (a === 'adhan-refresh' || a === 'adhan-retry') { day = null; open(); tick(); }
  }
  var pendingFocus = null;
  function refocus(a) { pendingFocus = a; open(); }
  function invalidate() { day = null; dayKey = ''; if (enabled()) tick(); else setChip(null); }
  function start() { clearInterval(timer); timer = setInterval(tick, 20000); setTimeout(tick, 1800); }
  return { start: start, tick: tick, open: open, action: action, handleKey: handleKey, invalidate: invalidate, announce: announce, next: function () { return nextInfo; }, icon: ICON };
})();
