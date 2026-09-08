/* RGBTv — Stalker / Ministra Portal provider (MAG emulation)
 * Implements the same interface as XtreamProvider. */
function StalkerProvider(acc) {
  this.acc = acc; this.type = 'stalker';
  var base = U.normUrl(acc.url);
  // Accept "http://host", "http://host/c", "http://host/stalker_portal/c", or a direct load.php URL
  base = base.replace(/\/(c|stalker_portal\/c)\/?$/, '').replace(/\/(server\/load\.php|portal\.php).*$/, '');
  this.base = base;
  this.endpoints = [base + '/server/load.php', base + '/stalker_portal/server/load.php', base + '/portal.php', base + '/c/portal.php', base + '/stalker_portal/portal.php'];
  this.endpoint = this.endpoints[0];
  var dev = Store.device();
  this.mac = (acc.mac || dev.mac).toUpperCase();
  this.sn = acc.sn || dev.sn; this.deviceId = acc.deviceId || dev.deviceId; this.deviceId2 = acc.deviceId2 || dev.deviceId2;
  this.sig = U.sha1(this.mac + this.sn);
  this.token = acc.token || null;
  this.profile = null;
  this._genreCache = {}; this._keepalive = null; this._mem = {};
}
StalkerProvider.prototype = {
  _headers: function () {
    var h = {
      'User-Agent': 'Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3',
      'X-User-Agent': 'Model: MAG250; Link: WiFi',
      'Referer': this.base + '/c/',
      'Accept': '*/*',
      'Cookie': 'mac=' + encodeURIComponent(this.mac) + '; stb_lang=en; timezone=' + encodeURIComponent('Europe/Paris')
    };
    if (this.token) h['Authorization'] = 'Bearer ' + this.token; // an empty "Bearer " makes many portals answer 401/444
    return h;
  },
  _url: function (params) {
    var q = { type: params.type, action: params.action };
    for (var k in params) q[k] = params[k];
    q.JsHttpRequest = '1-xml';
    // MAC is also sent as query param — required by portals where the Cookie header cannot be set from a browser context
    if (!q.mac) q.mac = this.mac;
    return this.endpoint + '?' + U.qs(q);
  },
  _call: function (params, noRetry) {
    var self = this;
    return U.getJSON(this._url(params), this._headers()).then(function (r) {
      if (r && typeof r === 'object' && 'js' in r) return r.js;
      if (typeof r === 'string' && /Authorization failed/i.test(r)) throw new Error('AUTH');
      return r;
    }).catch(function (e) {
      if (!noRetry && (/AUTH|HTTP 401|HTTP 403|Invalid JSON/.test(e.message))) {
        return self._handshake().then(function () { return self._call(params, true); });
      }
      throw e;
    });
  },
  _handshake: function () {
    var self = this, i = 0, lastErr = null, seen = {};
    self.token = null; // handshake must go out without an Authorization header
    function tryNext() {
      if (i >= self.endpoints.length) throw new Error(lastErr ? ('Portal: ' + lastErr + ' (' + (self.endpoint || '').replace(self.base, '') + ')') : 'Portal not reachable (no valid endpoint)');
      self.endpoint = self.endpoints[i++];
      if (seen[self.endpoint]) return tryNext(); seen[self.endpoint] = 1;
      return U.getJSON(self._url({ type: 'stb', action: 'handshake', token: '', prehash: '' }), self._headers()).then(function (r) {
        var js = r && r.js;
        if (!js || !js.token) throw new Error(typeof r === 'string' && /Authorization failed/i.test(r) ? 'MAC not authorized' : 'no token in handshake');
        self.token = js.token;
        self.acc.token = js.token; self.acc.endpoint = self.endpoint; Store.updateAccount(self.acc);
        return js;
      }).catch(function (e) { lastErr = e && e.message; return tryNext(); });
    }
    if (this.acc.endpoint) { this.endpoints.unshift(this.acc.endpoint); }
    return tryNext();
  },
  login: function () {
    var self = this;
    return this._handshake().then(function () {
      var now = new Date(), tz = 'Europe/Paris';
      return self._call({
        type: 'stb', action: 'get_profile', hd: 1, ver: 'ImageDescription: 0.2.18-r23-250; ImageDate: Thu Sep 13 11:31:16 EEST 2018; PORTAL version: 5.6.2; API Version: JS API version: 343; STB API version: 146; Player Engine version: 0x58c',
        num_banks: 2, sn: self.sn, stb_type: 'MAG250', client_type: 'STB', image_version: 218, video_out: 'hdmi', device_id: self.deviceId, device_id2: self.deviceId2, signature: self.sig,
        auth_second_step: 1, hw_version: '1.7-BD-00', not_valid_token: 0, metrics: JSON.stringify({ mac: self.mac, sn: self.sn, type: 'STB', model: 'MAG250', uid: '', random: U.uuid() }), hw_version_2: U.sha1(self.mac), timestamp: Math.floor(now / 1000), api_signature: 263, prehash: ''
      }, true);
    }).then(function (p) {
      self.profile = p || {};
      if (p && (p.status === 2 || p.status === '2') && !p.id) throw new Error('Device is not authorized on this portal (MAC not registered)');
      if (p && p.block_msg) throw new Error(p.block_msg);
      return self._call({ type: 'account_info', action: 'get_main_info' }, true).catch(function () { return {}; }).then(function (info) {
        self._startKeepalive();
        var exp = null;
        if (info && info.end_date) { var d = Date.parse(info.end_date); if (!isNaN(d)) exp = d; }
        else if (info && info.phone) { var d2 = Date.parse(info.phone); if (!isNaN(d2)) exp = d2; }
        return { status: 'Active', expires: exp, mac: self.mac, login: info && info.login, tariff: info && info.tariff_plan };
      });
    });
  },
  _startKeepalive: function () {
    var self = this; clearInterval(this._keepalive);
    this._keepalive = setInterval(function () { self._call({ type: 'watchdog', action: 'get_events', init: 0, cur_play_type: 1, event_active_id: 0 }, true).catch(function () { }); }, 120000);
  },
  destroy: function () { clearInterval(this._keepalive); },

  _genres: function (type, cacheKey) {
    var self = this, c = Store.cacheGet(this.acc.id, cacheKey, 6 * 3600e3); if (c) return Promise.resolve(c);
    var p = type === 'itv' ? { type: 'itv', action: 'get_genres' } : { type: type, action: 'get_categories' };
    return this._call(p).then(function (r) {
      var list = (Array.isArray(r) ? r : []).filter(function (g) { return g.id !== '*' && g.id !== undefined; }).map(function (g) { return { id: String(g.id), name: g.title, censored: Number(g.censored) === 1 }; });
      Store.cacheSet(self.acc.id, cacheKey, list); return list;
    });
  },
  liveCategories: function () { return this._genres('itv', 'live_cats'); },
  vodCategories: function () { return this._genres('vod', 'vod_cats'); },
  seriesCategories: function () {
    var self = this;
    return this._genres('series', 'series_cats').then(function (l) { return l.length ? l : self._genres('vod', 'vod_cats'); });
  },

  liveStreams: function (catId) {
    var self = this, key = 'live_all', c = this._mem.live || Store.cacheGet(this.acc.id, key, 3600e3);
    var p = c ? Promise.resolve(c) : this._call({ type: 'itv', action: 'get_all_channels' }).then(function (r) {
      var data = (r && r.data) || [], list = data.map(function (x) {
        return { type: 'live', id: String(x.id), name: x.name, num: Number(x.number), logo: self._logo(x.logo), catId: String(x.tv_genre_id), cmd: x.cmd, epgId: x.xmltv_id || '', archive: Number(x.enable_tv_archive) === 1, archiveDays: Number(x.tv_archive_duration) || 0, useHttpTmp: Number(x.use_http_tmp_link) === 1 };
      });
      self._mem.live = list; Store.cacheSet(self.acc.id, key, list); return list;
    });
    return p.then(function (list) { self._mem.live = list; return catId ? list.filter(function (x) { return x.catId === String(catId); }) : list; });
  },
  _logo: function (l) { if (!l) return ''; if (/^https?:/.test(l)) return l; return this.base + '/stalker_portal/misc/logos/320/' + l; },
  _pageAll: function (params, mapFn, maxPages) {
    var self = this, out = [], page = 1, total = 0, pageSize = 14;
    maxPages = maxPages || 60;
    function next() {
      params.p = page;
      return self._call(params).then(function (r) {
        var data = (r && r.data) || []; total = Number(r && r.total_items) || 0; pageSize = Number(r && r.max_page_items) || data.length || 14;
        data.forEach(function (x) { out.push(mapFn(x)); });
        if (data.length && out.length < total && page < maxPages) { page++; return next(); }
        return out;
      });
    }
    return next();
  },
  vodStreams: function (catId) {
    var self = this, key = 'vod_' + (catId || 'all'), c = Store.cacheGet(this.acc.id, key, 3600e3); if (c) return Promise.resolve(c);
    return this._pageAll({ type: 'vod', action: 'get_ordered_list', category: catId || '*', genre: catId || '*', sortby: 'added', fav: 0, hd: 0, not_ended: 0 }, function (x) {
      return { type: Number(x.is_series) === 1 ? 'series' : 'movie', id: String(x.id), name: x.name, poster: x.screenshot_uri || x.pic || '', catId: String(x.category_id || catId || ''), rating: x.rating_imdb || x.rating_kinopoisk || '', plot: x.description || '', year: x.year || '', genre: x.genres_str || '', cast: x.actors || '', director: x.director || '', cmd: x.cmd, duration: x.time || '', added: Date.parse(x.added) || 0, series: x.series || [] };
    }, catId ? 60 : 20).then(function (list) { Store.cacheSet(self.acc.id, key, list); return list; });
  },
  seriesList: function (catId) {
    var self = this, key = 'series_' + (catId || 'all'), c = Store.cacheGet(this.acc.id, key, 3600e3); if (c) return Promise.resolve(c);
    return this._pageAll({ type: 'series', action: 'get_ordered_list', category: catId || '*', genre: catId || '*', sortby: 'added', fav: 0, hd: 0, not_ended: 0 }, function (x) {
      return { type: 'series', id: String(x.id), name: x.name, poster: x.screenshot_uri || x.pic || '', catId: String(x.category_id || catId || ''), rating: x.rating_imdb || '', plot: x.description || '', year: x.year || '', genre: x.genres_str || '', cast: x.actors || '', director: x.director || '', cmd: x.cmd, added: Date.parse(x.added) || 0 };
    }, catId ? 60 : 20).then(function (list) {
      if (list.length) { Store.cacheSet(self.acc.id, key, list); return list; }
      // fallback: portals that expose series inside VOD (is_series=1)
      return self.vodStreams(catId).then(function (v) { return v.filter(function (x) { return x.type === 'series'; }); });
    }).catch(function () { return self.vodStreams(catId).then(function (v) { return v.filter(function (x) { return x.type === 'series'; }); }); });
  },
  vodInfo: function (id, item) { return Promise.resolve(item || { type: 'movie', id: id }); },
  seriesInfo: function (id, item) {
    var self = this; item = item || {};
    // Stalker "series" module: seasons are items with movie_id parent, episodes listed in `series` array
    return this._call({ type: 'series', action: 'get_ordered_list', movie_id: id, category: '*', p: 1 }).then(function (r) {
      var data = (r && r.data) || [], seasons = [];
      if (data.length) {
        data.forEach(function (s, idx) {
          var eps = (s.series || []).map(function (n) { return { type: 'episode', id: s.id + ':' + n, seriesId: String(id), season: idx + 1, episode: Number(n), name: 'Episode ' + n, cmd: s.cmd, seriesNum: n, thumb: s.screenshot_uri || '' }; });
          seasons.push({ num: idx + 1, name: s.name || ('Season ' + (idx + 1)), episodes: eps });
        });
      } else if (item.series && item.series.length) {
        seasons.push({ num: 1, name: 'Season 1', episodes: item.series.map(function (n) { return { type: 'episode', id: id + ':' + n, seriesId: String(id), season: 1, episode: Number(n), name: 'Episode ' + n, cmd: item.cmd, seriesNum: n }; }) });
      }
      item.seasons = seasons; item.type = 'series'; return item;
    }).catch(function () { item.seasons = []; return item; });
  },
  shortEPG: function (streamId, limit) {
    return this._call({ type: 'itv', action: 'get_short_epg', ch_id: streamId, size: limit || 10 }).then(function (r) {
      return (Array.isArray(r) ? r : []).map(function (e) { return { title: e.name, desc: e.descr || '', start: Number(e.start_timestamp), end: Number(e.stop_timestamp) }; });
    }).catch(function () { return []; });
  },
  /* create_link costs one portal round-trip (0.5-3 s on busy portals). While a channel is highlighted we resolve it in the
     background, so pressing OK starts the pipeline immediately. Links stay valid for a short while -> 25 s cache. */
  prefetchUrl: function (item) {
    var self = this, k = String(item.id), c = this._linkCache || (this._linkCache = {});
    if (c[k] && (c[k].p || Date.now() - c[k].at < 25000)) return;
    var p = this._resolve(item).then(function (u) { c[k] = { at: Date.now(), url: u }; return u; }, function () { delete c[k]; });
    c[k] = { at: Date.now(), p: p };
  },
  streamUrl: function (item) {
    var c = (this._linkCache || {})[String(item.id)];
    if (c) { if (c.p) return c.p.then(function (u) { return u || this._resolve(item); }.bind(this)); if (Date.now() - c.at < 25000) { delete this._linkCache[String(item.id)]; return Promise.resolve(c.url); } }
    return this._resolve(item);
  },
  _resolve: function (item) {
    var self = this, cmd = item.cmd || '';
    var params;
    if (item.type === 'live') params = { type: 'itv', action: 'create_link', cmd: cmd, series: '', forced_storage: '', disable_ad: 0, download: 0, force_ch_link_check: 0 };
    else if (item.type === 'episode') params = { type: 'vod', action: 'create_link', cmd: cmd, series: item.seriesNum || item.episode || '', forced_storage: '', disable_ad: 0, download: 0 };
    else params = { type: 'vod', action: 'create_link', cmd: cmd, series: '', forced_storage: '', disable_ad: 0, download: 0 };
    return this._call(params).then(function (r) {
      var c = (r && r.cmd) || cmd; return self._cleanCmd(c);
    }).catch(function () { return self._cleanCmd(cmd); });
  },
  _cleanCmd: function (c) {
    c = String(c || '').trim();
    c = c.replace(/^(ffmpeg|ffrt\d?|auto)\s+/i, '');
    var m = c.match(/https?:\/\/\S+/); if (m) c = m[0];
    return c;
  },
  catchupUrl: function (item, startTs, durationMin) {
    return this._call({ type: 'tv_archive', action: 'create_link', cmd: item.cmd, series: '', forced_storage: '', disable_ad: 0, download: 0, start: startTs, real_time: 1 }).then(function (r) { return this._cleanCmd(r && r.cmd); }.bind(this));
  }
};
