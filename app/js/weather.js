/* RGBTv — Weather (Open-Meteo, no API key).
 * v1.8: full Weather section (current + hourly + 10-day), auto location (IP) or manual (city search),
 * °C/°F, plus the compact topbar chip. Everything fails silently when offline. */
var Weather = (function () {
  // browser dev: the dev server proxies these APIs under /__wx/ so tests work without CORS/network
  var DEV = typeof window.PalmServiceBridge === 'undefined' && (location.hostname === 'localhost' || location.hostname === '127.0.0.1');
  var GEO_URL = DEV ? '/__wx/geo' : 'https://get.geojs.io/v1/ip/geo.json', GEOCODE_URL = DEV ? '/__wx/search' : 'https://geocoding-api.open-meteo.com/v1/search', FORECAST_URL = DEV ? '/__wx/forecast' : 'https://api.open-meteo.com/v1/forecast';
  var ICONS = {
    sun: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="5"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M4.9 19.1L7 17M17 7l2.1-2.1" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round"/></svg>',
    moon: '<svg viewBox="0 0 24 24"><path d="M20 15.5A8.5 8.5 0 0 1 8.5 4a8.5 8.5 0 1 0 11.5 11.5z"/></svg>',
    psun: '<svg viewBox="0 0 24 24"><circle cx="9" cy="9" r="4"/><path d="M9 1v2M9 15v2M1 9h2M15 9h2M3.3 3.3l1.4 1.4M13.3 13.3l1.4 1.4M3.3 14.7l1.4-1.4M13.3 4.7l1.4-1.4" stroke="currentColor" stroke-width="1.6" fill="none" stroke-linecap="round"/><path d="M9 21a3.5 3.5 0 0 1-.4-6.98A5 5 0 0 1 18.3 12.6 3.8 3.8 0 0 1 18.5 21H9z"/></svg>',
    cloud: '<svg viewBox="0 0 24 24"><path d="M6 19a4 4 0 0 1-.5-7.97A6 6 0 0 1 17.3 9.1 4.5 4.5 0 0 1 17.5 18H6z"/></svg>',
    rain: '<svg viewBox="0 0 24 24"><path d="M6 15a4 4 0 0 1-.5-7.97A6 6 0 0 1 17.3 5.1 4.5 4.5 0 0 1 17.5 14H6z"/><path d="M8 17l-1.5 3M12 17l-1.5 3M16 17l-1.5 3" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>',
    drizzle: '<svg viewBox="0 0 24 24"><path d="M6 15a4 4 0 0 1-.5-7.97A6 6 0 0 1 17.3 5.1 4.5 4.5 0 0 1 17.5 14H6z"/><circle cx="8" cy="18.5" r="1.2"/><circle cx="12" cy="20" r="1.2"/><circle cx="16" cy="18.5" r="1.2"/></svg>',
    snow: '<svg viewBox="0 0 24 24"><path d="M6 15a4 4 0 0 1-.5-7.97A6 6 0 0 1 17.3 5.1 4.5 4.5 0 0 1 17.5 14H6z"/><circle cx="8" cy="19" r="1.4"/><circle cx="12" cy="21" r="1.4"/><circle cx="16" cy="19" r="1.4"/></svg>',
    storm: '<svg viewBox="0 0 24 24"><path d="M6 14a4 4 0 0 1-.5-7.97A6 6 0 0 1 17.3 4.1 4.5 4.5 0 0 1 17.5 13H6z"/><path d="M13 13l-3 5h3l-1 4 4-6h-3z"/></svg>',
    fog: '<svg viewBox="0 0 24 24"><path d="M4 10h16M3 14h18M5 18h14" stroke="currentColor" stroke-width="2.2" fill="none" stroke-linecap="round"/></svg>',
    wind: '<svg viewBox="0 0 24 24"><path d="M3 8h11a3 3 0 1 0-3-3M3 12h15a3 3 0 1 1-3 3M3 16h8a2 2 0 1 1-2 2" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round"/></svg>',
    drop: '<svg viewBox="0 0 24 24"><path d="M12 2s6 7 6 12a6 6 0 0 1-12 0c0-5 6-12 6-12z"/></svg>',
    pin: '<svg viewBox="0 0 24 24"><path d="M12 2a7 7 0 0 0-7 7c0 5.2 7 13 7 13s7-7.8 7-13a7 7 0 0 0-7-7zm0 9.5A2.5 2.5 0 1 1 12 6.5a2.5 2.5 0 0 1 0 5z"/></svg>',
    sunrise: '<svg viewBox="0 0 24 24"><path d="M3 18h18M5 14a7 7 0 0 1 14 0M12 3v4M8 6l1.5 1.5M16 6l-1.5 1.5" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round"/></svg>'
  };
  var CODES = { 0: 'clear', 1: 'mclear', 2: 'pcloudy', 3: 'overcast', 45: 'fog', 48: 'fog', 51: 'drizzle', 53: 'drizzle', 55: 'drizzle', 56: 'drizzle', 57: 'drizzle', 61: 'rain', 63: 'rain', 65: 'hrain', 66: 'rain', 67: 'hrain', 71: 'snow', 73: 'snow', 75: 'hsnow', 77: 'snow', 80: 'showers', 81: 'showers', 82: 'hrain', 85: 'snow', 86: 'hsnow', 95: 'storm', 96: 'hail', 99: 'hail' };
  function iconKey(code, isDay) {
    if (code === 0) return isDay === false ? 'moon' : 'sun';
    if (code === 1) return isDay === false ? 'moon' : 'psun';
    if (code === 2) return 'psun';
    if (code === 3) return 'cloud';
    if (code <= 48) return 'fog';
    if (code <= 57) return 'drizzle';
    if (code <= 67 || (code >= 80 && code <= 82)) return 'rain';
    if (code <= 77 || code === 85 || code === 86) return 'snow';
    return 'storm';
  }
  function icon(code, isDay) { return ICONS[iconKey(code, isDay)]; }
  function descr(code) { return I18n.t('wx.' + (CODES[code] || 'overcast')); }
  function unit() { return Store.settings().wxUnit === 'f' ? 'f' : 'c'; }
  function fmtT(c) { return Math.round(unit() === 'f' ? c * 9 / 5 + 32 : c) + '°'; }
  function fmtW(kmh) { return unit() === 'f' ? Math.round(kmh / 1.609) + ' mph' : Math.round(kmh) + ' km/h'; }

  /* ---- location ---- */
  function getLocation() { var man = Store.get('geoManual'); if (Store.settings().wxMode === 'manual' && man) return Promise.resolve(man); return autoLocation(); }
  function autoLocation() {
    var cached = Store.get('geo'); if (cached && cached.lat && Date.now() - (cached.at || 0) < 24 * 3600e3) return Promise.resolve(cached);
    return U.getJSON(GEO_URL).then(function (g) {
      var o = { lat: Number(g.latitude), lon: Number(g.longitude), city: g.city || '', country: g.country || '', at: Date.now() }; Store.set('geo', o); return o;
    }).catch(function () { if (cached && cached.lat) return cached; throw new Error('no location'); });
  }
  function searchCity(q) {
    return U.getJSON(GEOCODE_URL + '?name=' + encodeURIComponent(q) + '&count=8&language=' + (I18n.get() === 'ar' ? 'ar' : 'en')).then(function (r) {
      return (r && r.results || []).map(function (x) { return { lat: x.latitude, lon: x.longitude, city: x.name, region: x.admin1 || '', country: x.country || '', tz: x.timezone }; });
    });
  }
  function setManual(loc) { Store.set('geoManual', loc); Store.setSetting('wxMode', 'manual'); invalidate(); if (window.Adhan) Adhan.invalidate(); }
  function setAuto() { Store.setSetting('wxMode', 'auto'); invalidate(); if (window.Adhan) Adhan.invalidate(); }
  function invalidate() { Store.del('wx'); last = 0; }

  /* ---- data ---- */
  var last = 0, pending = null;
  function fetchAll() {
    var cached = Store.get('wx'); if (cached && Date.now() - cached.at < 30 * 60000) return Promise.resolve(cached);
    if (pending) return pending;
    pending = getLocation().then(function (g) {
      var url = FORECAST_URL + '?latitude=' + g.lat + '&longitude=' + g.lon + '&timezone=auto&forecast_days=10' +
        '&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,weather_code,wind_speed_10m,precipitation' +
        '&hourly=temperature_2m,weather_code,precipitation_probability,is_day' +
        '&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset,wind_speed_10m_max,uv_index_max';
      return U.getJSON(url).then(function (w) {
        if (!w || !w.current) throw new Error('bad weather');
        var d = { at: Date.now(), loc: g, cur: w.current, hourly: w.hourly, daily: w.daily, tz: w.timezone };
        Store.set('wx', d); return d;
      });
    });
    pending.then(function () { pending = null; }, function () { pending = null; });
    return pending;
  }

  /* ---- topbar chip ---- */
  function refresh() {
    var el = U.$('#weather'); if (!el) return;
    if (!Store.settings().weather) { el.className = 'weather'; return; }
    if (Date.now() - last < 20 * 60000) return; last = Date.now();
    fetchAll().then(paintChip).catch(function () { last = Date.now() - 15 * 60000; /* retry in 5 min */ });
  }
  function paintChip(d) {
    var el = U.$('#weather'); if (!el || !d) return;
    el.innerHTML = icon(Number(d.cur.weather_code), d.cur.is_day !== 0) + '<span class="wt-t">' + fmtT(d.cur.temperature_2m) + '</span><span class="wt-city">' + U.esc(d.loc.city || '') + '</span>';
    el.className = 'weather show';
  }

  /* ---- full section ---- */
  function open() {
    var root = U.$('#sec-weather'); root.innerHTML = '<div class="wx-loading"><div class="loader"></div></div>';
    fetchAll().then(render).catch(function (e) {
      root.innerHTML = '<div class="wx-empty"><div class="wx-empty-ico">' + ICONS.cloud + '</div><h2>' + U.esc(I18n.t('wx.unavailable')) + '</h2><p>' + U.esc(I18n.t('wx.unavailable.d')) + '</p><div class="wx-actions"><button class="btn focusable" data-nav="wx" data-action="wx-location">' + ICONS.pin + '<span>' + U.esc(I18n.t('wx.setLocation')) + '</span></button><button class="btn ghost focusable" data-nav="wx" data-action="wx-retry">' + U.esc(I18n.t('retry')) + '</button></div></div>';
      Nav.focus(U.$('[data-action="wx-location"]', root));
    });
  }
  function dayName(iso, long) { var d = new Date(iso + 'T12:00:00'); try { return d.toLocaleDateString(I18n.get() === 'ar' ? 'ar' : 'en-GB', { weekday: long ? 'long' : 'short' }); } catch (e) { return d.toDateString().slice(0, 3); } }
  function dayNum(iso) { var d = new Date(iso + 'T12:00:00'); try { return d.toLocaleDateString(I18n.get() === 'ar' ? 'ar' : 'en-GB', { day: 'numeric', month: 'short' }); } catch (e) { return iso.slice(5); } }
  function hm(iso) { return iso ? iso.slice(11, 16) : '--:--'; }
  function render(d) {
    var root = U.$('#sec-weather'), cur = d.cur, dl = d.daily, h = d.hourly, i;
    var code = Number(cur.weather_code), isDay = cur.is_day !== 0, k = iconKey(code, isDay);
    root.innerHTML = ''; root.setAttribute('data-wx', k);
    var wrap = U.el('div', 'wx-wrap');
    // header row: location + actions
    var head = U.el('div', 'wx-head');
    head.innerHTML = '<div class="wx-loc">' + ICONS.pin + '<span class="wx-city">' + U.esc(d.loc.city || '—') + '</span><span class="wx-country">' + U.esc([d.loc.region, d.loc.country].filter(Boolean).join(', ')) + '</span><span class="wx-mode">' + U.esc(Store.settings().wxMode === 'manual' ? I18n.t('wx.manual') : I18n.t('wx.auto')) + '</span></div>' +
      '<div class="wx-actions"><button class="btn ghost focusable" data-nav="wx" data-action="wx-location">' + ICONS.pin + '<span>' + U.esc(I18n.t('wx.changeLocation')) + '</span></button><button class="btn ghost focusable" data-nav="wx" data-action="wx-unit">°' + (unit() === 'f' ? 'F' : 'C') + '</button><button class="btn ghost focusable" data-nav="wx" data-action="wx-refresh">↻</button></div>';
    wrap.appendChild(head);
    // now card + details
    var now = U.el('div', 'wx-now');
    var today = dl && dl.time ? 0 : -1;
    now.innerHTML = '<div class="wx-now-ico">' + icon(code, isDay) + '</div>' +
      '<div class="wx-now-main"><div class="wx-temp">' + fmtT(cur.temperature_2m) + '</div><div class="wx-desc">' + U.esc(descr(code)) + '</div>' +
      (today >= 0 ? '<div class="wx-hl"><span>↑ ' + fmtT(dl.temperature_2m_max[0]) + '</span><span>↓ ' + fmtT(dl.temperature_2m_min[0]) + '</span><span>' + U.esc(I18n.t('wx.feels')) + ' ' + fmtT(cur.apparent_temperature) + '</span></div>' : '') + '</div>' +
      '<div class="wx-stats">' +
      stat(ICONS.drop, I18n.t('wx.humidity'), Math.round(cur.relative_humidity_2m) + '%') +
      stat(ICONS.wind, I18n.t('wx.wind'), fmtW(cur.wind_speed_10m)) +
      stat(ICONS.rain, I18n.t('wx.rainChance'), (today >= 0 && dl.precipitation_probability_max ? Math.round(dl.precipitation_probability_max[0] || 0) : 0) + '%') +
      stat(ICONS.sun, I18n.t('wx.uv'), today >= 0 && dl.uv_index_max ? String(Math.round(dl.uv_index_max[0] || 0)) : '—') +
      stat(ICONS.sunrise, I18n.t('wx.sunrise'), today >= 0 ? hm(dl.sunrise[0]) : '—') +
      stat(ICONS.moon, I18n.t('wx.sunset'), today >= 0 ? hm(dl.sunset[0]) : '—') +
      '</div>';
    wrap.appendChild(now);
    // hourly strip (next 24h from now)
    if (h && h.time) {
      var strip = U.el('div', 'wx-hourly'); var title = U.el('div', 'wx-title', U.esc(I18n.t('wx.hourly'))); strip.appendChild(title);
      var row = U.el('div', 'wx-hours'); var nowIso = (cur.time || '').slice(0, 13), start = 0;
      for (i = 0; i < h.time.length; i++) if (h.time[i].slice(0, 13) >= nowIso) { start = i; break; }
      for (i = start; i < Math.min(start + 24, h.time.length); i += 2) {
        var c = U.el('div', 'wx-hour' + (i === start ? ' now' : ''));
        c.innerHTML = '<div class="wh-t">' + (i === start ? U.esc(I18n.t('wx.now')) : hm(h.time[i])) + '</div><div class="wh-i">' + icon(Number(h.weather_code[i]), h.is_day ? h.is_day[i] !== 0 : true) + '</div><div class="wh-v">' + fmtT(h.temperature_2m[i]) + '</div>' + (h.precipitation_probability && h.precipitation_probability[i] >= 20 ? '<div class="wh-p">' + h.precipitation_probability[i] + '%</div>' : '<div class="wh-p">&nbsp;</div>');
        row.appendChild(c);
      }
      strip.appendChild(row); wrap.appendChild(strip);
    }
    // daily list (10 days)
    if (dl && dl.time) {
      var days = U.el('div', 'wx-daily'); days.appendChild(U.el('div', 'wx-title', U.esc(I18n.t('wx.daily', { n: dl.time.length }))));
      var list = U.el('div', 'wx-days');
      var gmin = Math.min.apply(null, dl.temperature_2m_min), gmax = Math.max.apply(null, dl.temperature_2m_max), span = Math.max(1, gmax - gmin);
      for (i = 0; i < dl.time.length; i++) {
        var r = U.el('div', 'wx-day focusable' + (i === 0 ? ' today' : '')); r.setAttribute('data-nav', 'wx'); r.setAttribute('data-i', i);
        var lo = dl.temperature_2m_min[i], hi = dl.temperature_2m_max[i], left = (lo - gmin) / span * 100, width = (hi - lo) / span * 100;
        r.innerHTML = '<div class="wd-name">' + (i === 0 ? U.esc(I18n.t('wx.today')) : U.esc(dayName(dl.time[i], true))) + '<span class="wd-date">' + U.esc(dayNum(dl.time[i])) + '</span></div>' +
          '<div class="wd-ico">' + icon(Number(dl.weather_code[i]), true) + '</div><div class="wd-desc">' + U.esc(descr(Number(dl.weather_code[i]))) + '</div>' +
          '<div class="wd-rain">' + (dl.precipitation_probability_max && dl.precipitation_probability_max[i] >= 10 ? ICONS.drop + (dl.precipitation_probability_max[i]) + '%' : '') + '</div>' +
          '<div class="wd-lo">' + fmtT(lo) + '</div><div class="wd-bar"><i style="left:' + left.toFixed(1) + '%;width:' + Math.max(6, width).toFixed(1) + '%"></i></div><div class="wd-hi">' + fmtT(hi) + '</div>' +
          '<div class="wd-wind">' + ICONS.wind + fmtW(dl.wind_speed_10m_max[i]) + '</div>';
        list.appendChild(r);
      }
      days.appendChild(list); wrap.appendChild(days);
    }
    var upd = U.el('div', 'wx-updated', U.esc(I18n.t('wx.updated', { t: U.clock(new Date(d.at)) })) + ' · Open-Meteo'); wrap.appendChild(upd);
    root.appendChild(wrap);
    paintChip(d);
    if (!Nav.current() || !Nav.visible(Nav.current())) Nav.focus(U.$('.wx-day', root) || U.$('[data-action="wx-location"]', root));
  }
  function stat(ic, label, val) { return '<div class="wx-stat"><span class="ws-i">' + ic + '</span><span class="ws-l">' + U.esc(label) + '</span><b class="ws-v">' + U.esc(val) + '</b></div>'; }
  function scrollDays(el) {
    var list = el.parentNode, idx = Number(el.getAttribute('data-i')), h = el.offsetHeight + 8, viewH = list.parentNode.offsetHeight - 70;
    var off = Math.max(0, (idx + 1) * h - viewH); list.style.transform = 'translateY(-' + off + 'px)';
  }

  /* ---- location picker (modal with OSK-less input: uses the remote keyboard / phone-friendly text field) ---- */
  function pickLocation() {
    var m = U.$('#modal'); U.$('#modal-title').textContent = I18n.t('wx.setLocation');
    var auto = Store.get('geo');
    U.$('#modal-text').innerHTML = '<div class="wx-pick">' +
      '<button class="wx-opt focusable' + (Store.settings().wxMode !== 'manual' ? ' on' : '') + '" data-nav="modal" data-wx-auto="1">' + ICONS.pin + '<span><b>' + U.esc(I18n.t('wx.auto')) + '</b><small>' + U.esc(auto && auto.city ? auto.city + (auto.country ? ', ' + auto.country : '') : I18n.t('wx.auto.d')) + '</small></span></button>' +
      '<div class="wx-search"><input class="focusable" data-nav="modal" id="wx-q" placeholder="' + U.esc(I18n.t('wx.searchCity')) + '"><button class="btn focusable" data-nav="modal" id="wx-go">' + U.esc(I18n.t('search')) + '</button></div>' +
      '<div class="wx-results" id="wx-results"></div></div>';
    var acts = U.$('#modal-actions'); acts.innerHTML = '';
    var prev = Nav.current();
    var fromAdhan = U.$('#sec-adhan') && U.$('#sec-adhan').classList.contains('active');
    function close() { m.classList.remove('show'); Nav.setContainer(App.activeScreen()); m._close = null; if (fromAdhan) Adhan.open(); else open(); }
    var cancel = U.el('button', 'btn ghost focusable', U.esc(I18n.t('cancel'))); cancel.setAttribute('data-nav', 'modal'); cancel.onclick = function () { m.classList.remove('show'); Nav.setContainer(App.activeScreen()); m._close = null; if (prev) Nav.focus(prev); }; acts.appendChild(cancel);
    m._close = cancel.onclick;
    U.$('[data-wx-auto]', m).onclick = function () { setAuto(); close(); };
    var q = U.$('#wx-q'), go = U.$('#wx-go'), res = U.$('#wx-results');
    function doSearch() {
      var v = q.value.trim(); if (v.length < 2) return; res.innerHTML = '<div class="loader"></div>';
      searchCity(v).then(function (list) {
        res.innerHTML = ''; if (!list.length) { res.innerHTML = '<div class="muted">' + U.esc(I18n.t('wx.noResults')) + '</div>'; return; }
        list.forEach(function (l) {
          var b = U.el('button', 'wx-opt focusable'); b.setAttribute('data-nav', 'modal');
          b.innerHTML = ICONS.pin + '<span><b>' + U.esc(l.city) + '</b><small>' + U.esc([l.region, l.country].filter(Boolean).join(', ')) + '</small></span>';
          b.onclick = function () { setManual(l); close(); }; res.appendChild(b);
        });
        Nav.focus(res.firstChild);
      }).catch(function () { res.innerHTML = '<div class="muted">' + U.esc(I18n.t('wx.unavailable')) + '</div>'; });
    }
    go.onclick = doSearch; q.onkeydown = function (ev) { if (ev.keyCode === 13) { q.blur(); doSearch(); ev.preventDefault(); ev.stopPropagation(); } };
    m.classList.add('show'); Nav.setContainer(m); Nav.focus(q); try { q.focus(); } catch (e) { }
  }
  function action(a) {
    if (a === 'wx-location') pickLocation();
    else if (a === 'wx-unit') { Store.setSetting('wxUnit', unit() === 'f' ? 'c' : 'f'); var d = Store.get('wx'); if (d) render(d); else open(); Nav.focus(U.$('[data-action="wx-unit"]')); }
    else if (a === 'wx-refresh' || a === 'wx-retry') { invalidate(); open(); }
  }
  return { refresh: refresh, open: open, action: action, scrollDays: scrollDays, icons: ICONS, invalidate: invalidate, locate: autoLocation };
})();
