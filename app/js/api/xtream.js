/* RGBTv — Xtream Codes API provider
 * Common provider interface:
 *   login() -> {expires, status, ...}
 *   liveCategories(), liveStreams(catId)
 *   vodCategories(),  vodStreams(catId), vodInfo(id)
 *   seriesCategories(), seriesList(catId), seriesInfo(id)
 *   shortEPG(streamId), streamUrl(item) -> Promise<url>
 */
function XtreamProvider(acc) {
  this.acc = acc;
  this.base = U.normUrl(acc.url);
  this.user = acc.username; this.pass = acc.password;
  this.type = 'xtream';
  this.serverInfo = null; this.userInfo = null;
  this._mem = {}; this._pending = {};
}
XtreamProvider.prototype = {
  _api: function (action, params) {
    var q = { username: this.user, password: this.pass };
    if (action) q.action = action;
    if (params) for (var k in params) q[k] = params[k];
    return U.getJSON(this.base + '/player_api.php?' + U.qs(q));
  },
  login: function () {
    var self = this;
    return this._api().then(function (r) {
      if (!r || !r.user_info) throw new Error('Invalid server response');
      if (r.user_info.auth !== 1 && r.user_info.auth !== '1') throw new Error('Authentication failed (check username/password)');
      self.userInfo = r.user_info; self.serverInfo = r.server_info || {};
      return { loggedIn: true, status: r.user_info.status, expires: r.user_info.exp_date ? Number(r.user_info.exp_date) * 1000 : null, maxConnections: r.user_info.max_connections, activeConnections: r.user_info.active_cons, formats: r.user_info.allowed_output_formats || [] };
    });
  },
  _cats: function (action, cacheKey) {
    var self = this, c = Store.cacheGet(this.acc.id, cacheKey, 6 * 3600e3); if (c) return Promise.resolve(c);
    return this._api(action).then(function (r) {
      var list = (Array.isArray(r) ? r : []).map(function (x) { return { id: String(x.category_id), name: x.category_name, parent: x.parent_id }; });
      Store.cacheSet(self.acc.id, cacheKey, list); return list;
    });
  },
  liveCategories: function () { return this._cats('get_live_categories', 'live_cats'); },
  vodCategories: function () { return this._cats('get_vod_categories', 'vod_cats'); },
  seriesCategories: function () { return this._cats('get_series_categories', 'series_cats'); },

  /* Load a full list once per session (memory), optionally persisted when small enough, then filter locally. */
  _all: function (kind) {
    var self = this;
    if (this._mem[kind]) return Promise.resolve(this._mem[kind]);
    if (this._pending[kind]) return this._pending[kind];
    var cached = Store.cacheGet(this.acc.id, kind + '_all', 3600e3);
    if (cached) { this._mem[kind] = cached; return Promise.resolve(cached); }
    var action = kind === 'live' ? 'get_live_streams' : kind === 'vod' ? 'get_vod_streams' : 'get_series';
    var p = this._api(action).then(function (r) {
      var arr = Array.isArray(r) ? r : [], list = new Array(arr.length), i, x;
      for (i = 0; i < arr.length; i++) {
        x = arr[i];
        if (kind === 'live') list[i] = { type: 'live', id: String(x.stream_id), name: x.name, num: x.num, logo: x.stream_icon || '', catId: String(x.category_id), epgId: x.epg_channel_id || '', archive: Number(x.tv_archive) === 1, archiveDays: Number(x.tv_archive_duration) || 0 };
        else if (kind === 'vod') list[i] = { type: 'movie', id: String(x.stream_id), name: x.name, poster: x.stream_icon || '', catId: String(x.category_id), rating: x.rating || x.rating_5based || '', ext: x.container_extension || 'mp4', added: Number(x.added) || 0, year: x.year || '' };
        else list[i] = { type: 'series', id: String(x.series_id), name: x.name, poster: x.cover || '', catId: String(x.category_id), rating: x.rating || '', plot: x.plot || '', year: x.releaseDate || x.release_date || '', genre: x.genre || '', cast: x.cast || '', backdrop: (x.backdrop_path && x.backdrop_path[0]) || '', added: Number(x.last_modified) || 0 };
      }
      self._mem[kind] = list; delete self._pending[kind];
      Store.cacheSet(self.acc.id, kind + '_all', list); // silently skipped when too large
      return list;
    }, function (e) { delete self._pending[kind]; throw e; });
    this._pending[kind] = p; return p;
  },
  _filtered: function (kind, catId) {
    return this._all(kind).then(function (list) {
      if (!catId) return list; var out = [], c = String(catId);
      for (var i = 0; i < list.length; i++) if (list[i].catId === c) out.push(list[i]);
      return out;
    });
  },
  liveStreams: function (catId) { return this._filtered('live', catId); },
  vodStreams: function (catId) { return this._filtered('vod', catId); },
  seriesList: function (catId) { return this._filtered('series', catId); },
  vodInfo: function (id) {
    return this._api('get_vod_info', { vod_id: id }).then(function (r) {
      var i = (r && r.info) || {}, m = (r && r.movie_data) || {};
      return { type: 'movie', id: String(id), name: m.name || i.name || '', plot: i.plot || i.description || '', poster: i.movie_image || i.cover_big || '', backdrop: (i.backdrop_path && i.backdrop_path[0]) || '', rating: i.rating || '', year: i.releasedate || i.release_date || '', genre: i.genre || '', duration: i.duration || '', cast: i.cast || i.actors || '', director: i.director || '', ext: m.container_extension || 'mp4' };
    });
  },
  seriesInfo: function (id) {
    return this._api('get_series_info', { series_id: id }).then(function (r) {
      var i = (r && r.info) || {}, eps = (r && r.episodes) || {}, seasons = [];
      var keys = Object.keys(eps).sort(function (a, b) { return Number(a) - Number(b); });
      keys.forEach(function (s) {
        seasons.push({ num: Number(s), name: 'Season ' + s, episodes: (eps[s] || []).map(function (e) {
          return { type: 'episode', id: String(e.id), seriesId: String(id), season: Number(s), episode: Number(e.episode_num), name: e.title || ('Episode ' + e.episode_num), ext: e.container_extension || 'mp4', plot: (e.info && e.info.plot) || '', thumb: (e.info && e.info.movie_image) || '', duration: (e.info && e.info.duration) || '' };
        }) });
      });
      return { type: 'series', id: String(id), name: i.name || '', plot: i.plot || '', poster: i.cover || '', backdrop: (i.backdrop_path && i.backdrop_path[0]) || '', rating: i.rating || '', year: i.releaseDate || i.release_date || '', genre: i.genre || '', cast: i.cast || '', director: i.director || '', seasons: seasons };
    });
  },
  shortEPG: function (streamId, limit) {
    return this._api('get_short_epg', { stream_id: streamId, limit: limit || 10 }).then(function (r) {
      return ((r && r.epg_listings) || []).map(function (e) {
        return { title: U.b64dec(e.title), desc: U.b64dec(e.description), start: Number(e.start_timestamp), end: Number(e.stop_timestamp) };
      });
    }).catch(function () { return []; });
  },
  streamUrl: function (item) {
    var s = Store.settings(), url;
    if (item.type === 'live') {
      var fmt = s.liveFormat === 'ts' ? 'ts' : 'm3u8', allowed = (this.userInfo && this.userInfo.allowed_output_formats) || [];
      if (allowed.length && allowed.indexOf(fmt) < 0) fmt = allowed.indexOf('m3u8') >= 0 ? 'm3u8' : 'ts'; // server dictates
      url = fmt === 'm3u8' ? this.base + '/live/' + this.user + '/' + this.pass + '/' + item.id + '.m3u8' : this.base + '/live/' + this.user + '/' + this.pass + '/' + item.id + '.ts';
    } else if (item.type === 'movie') url = this.base + '/movie/' + this.user + '/' + this.pass + '/' + item.id + '.' + (item.ext || 'mp4');
    else url = this.base + '/series/' + this.user + '/' + this.pass + '/' + item.id + '.' + (item.ext || 'mp4');
    return Promise.resolve(url);
  },
  catchupUrl: function (item, startTs, durationMin) {
    var d = new Date(startTs * 1000), s = d.getFullYear() + '-' + U.pad(d.getMonth() + 1) + '-' + U.pad(d.getDate()) + ':' + U.pad(d.getHours()) + '-' + U.pad(d.getMinutes());
    return this.base + '/streaming/timeshift.php?' + U.qs({ username: this.user, password: this.pass, stream: item.id, start: s, duration: durationMin });
  }
};
