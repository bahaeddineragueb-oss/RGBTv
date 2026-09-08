/* RGBTv — M3U / M3U8 playlist provider (bonus: also auto-detects Xtream get.php links) */
function M3UProvider(acc) {
  this.acc = acc; this.type = 'm3u'; this.url = (acc.url || '').trim();
  this.items = null;
}
M3UProvider.prototype = {
  login: function () {
    var self = this, cached = Store.cacheGet(this.acc.id, 'm3u_items', 6 * 3600e3);
    if (cached) { this.items = cached; return Promise.resolve({ status: 'Loaded (cache)', expires: null, count: cached.length }); }
    /* Xtream get.php links are converted to a real Xtream account by createProvider(); here we only see true playlists.
       Panels: 444/512/403 for browser UAs -> U.http retries through the TV service with a player UA. Playlists are big
       (5-40 MB) -> long timeout, and we explicitly ask for a plain (non-gzip) body the old Node in the TV can handle. */
    return U.http(this.url, { timeout: 120000, headers: { 'Accept': '*/*' } }).then(function (txt) {
      if (typeof txt !== 'string') txt = String(txt || '');
      if (txt.charCodeAt(0) === 0xFEFF) txt = txt.slice(1);
      if (/^\s*</.test(txt) && !/#EXTINF/i.test(txt)) throw new Error('The address returned a web page, not a playlist (check the URL / login data)');
      if (!/#EXTM3U/i.test(txt) && !/#EXTINF/i.test(txt)) throw new Error('Not a valid M3U playlist' + (txt.length < 200 ? ': ' + txt.trim().slice(0, 120) : ''));
      self.items = self._parse(txt);
      if (!self.items.length) throw new Error('Playlist is empty');
      try { Store.cacheSet(self.acc.id, 'm3u_items', self.items); } catch (e) { }
      return { status: 'Loaded', expires: null, count: self.items.length };
    });
  },
  _parse: function (txt) {
    var lines = txt.split(/\r?\n/), out = [], cur = null, n = 0, seriesMap = {};
    for (var i = 0; i < lines.length; i++) {
      var l = lines[i].trim(); if (!l) continue;
      if (l.indexOf('#EXTINF') === 0) {
        var attrs = {}, re = /([a-zA-Z0-9\-_]+)=("([^"]*)"|'([^']*)')/g, m;
        while ((m = re.exec(l))) attrs[m[1].toLowerCase()] = m[3] != null ? m[3] : m[4];
        var ci = l.lastIndexOf(','), name = (ci > 0 ? l.slice(ci + 1) : '').trim() || attrs['tvg-name'] || 'Unknown';
        cur = { name: name, logo: attrs['tvg-logo'] || '', group: attrs['group-title'] || 'Uncategorized', epgId: attrs['tvg-id'] || '' };
      } else if (l.indexOf('#EXTGRP:') === 0) { if (cur) cur.group = l.slice(8).trim() || cur.group; continue; }
      else if (l[0] === '#') continue;
      else if (cur) {
        cur.url = l; n++;
        var type = this._guessType(cur);
        var item = { id: 'm' + n, name: cur.name, logo: cur.logo, poster: cur.logo, catId: cur.group, catName: cur.group, epgId: cur.epgId, url: cur.url, num: n };
        if (type === 'series') {
          var sm = cur.name.match(/^(.*?)[\s\-]*S(\d{1,2})\s*E(\d{1,3})/i);
          var sName = sm ? sm[1].trim() : cur.name, key = cur.group + '|' + sName.toLowerCase();
          if (!seriesMap[key]) { seriesMap[key] = { type: 'series', id: 's' + n, name: sName, poster: cur.logo, catId: cur.group, catName: cur.group, seasons: {} }; out.push(seriesMap[key]); }
          var s = sm ? Number(sm[2]) : 1, e = sm ? Number(sm[3]) : Object.keys(seriesMap[key].seasons).length + 1;
          if (!seriesMap[key].seasons[s]) seriesMap[key].seasons[s] = [];
          seriesMap[key].seasons[s].push({ type: 'episode', id: 'm' + n, seriesId: seriesMap[key].id, season: s, episode: e, name: cur.name, url: cur.url, thumb: cur.logo });
        } else { item.type = type; out.push(item); }
        cur = null;
      }
    }
    return out;
  },
  _guessType: function (c) {
    var u = c.url.toLowerCase(), g = c.group.toLowerCase();
    if (/\/series\//.test(u) || /series|séries/.test(g) || /S\d{1,2}\s*E\d{1,3}/i.test(c.name)) return 'series';
    if (/\/movie\//.test(u) || /\.(mp4|mkv|avi|mov)(\?|$)/.test(u) || /movie|film|vod|cinema/.test(g)) return 'movie';
    return 'live';
  },
  _cats: function (type) {
    var seen = {}, list = [];
    (this.items || []).forEach(function (x) { if (x.type === type && !seen[x.catId]) { seen[x.catId] = 1; list.push({ id: x.catId, name: x.catName }); } });
    return Promise.resolve(list);
  },
  liveCategories: function () { return this._cats('live'); },
  vodCategories: function () { return this._cats('movie'); },
  seriesCategories: function () { return this._cats('series'); },
  _filter: function (type, catId) { return Promise.resolve((this.items || []).filter(function (x) { return x.type === type && (!catId || x.catId === catId); })); },
  liveStreams: function (catId) { return this._filter('live', catId); },
  vodStreams: function (catId) { return this._filter('movie', catId); },
  seriesList: function (catId) { return this._filter('series', catId); },
  vodInfo: function (id, item) { return Promise.resolve(item); },
  seriesInfo: function (id, item) {
    var s = item && item.seasons || {}, seasons = Object.keys(s).sort(function (a, b) { return a - b; }).map(function (k) { return { num: Number(k), name: 'Season ' + k, episodes: s[k].sort(function (a, b) { return a.episode - b.episode; }) }; });
    return Promise.resolve({ type: 'series', id: item.id, name: item.name, poster: item.poster, seasons: seasons });
  },
  shortEPG: function () { return Promise.resolve([]); },
  streamUrl: function (item) { return Promise.resolve(item.url); }
};

function createProvider(acc) {
  if (acc.type === 'stalker') return new StalkerProvider(acc);
  if (acc.type === 'm3u') {
    // "M3U" links that are really an Xtream panel (get.php?username=..&password=..) work far better through the API
    var m = /^(https?:\/\/[^\/?#]+)(?:\/[^?#]*)?\/get\.php\?(?:.*&)?username=([^&]+)&password=([^&#]+)/i.exec(acc.url || '');
    if (m) { var x = {}; for (var k in acc) x[k] = acc[k]; x.type = 'xtream'; x.url = m[1]; x.username = decodeURIComponent(m[2]); x.password = decodeURIComponent(m[3]); return new XtreamProvider(x); }
    return new M3UProvider(acc);
  }
  return new XtreamProvider(acc);
}
