/* RGBTv — optional TMDB enrichment (backdrops, posters, plots, ratings, trailers).
 * Only active when the user pastes an API key in Settings. Results cached per title. */
var TMDB = (function () {
  var BASE = 'https://api.themoviedb.org/3', IMG = 'https://image.tmdb.org/t/p/';
  var mem = {};
  function key() { return (Store.settings().tmdbKey || '').trim(); }
  function enabled() { return !!key(); }
  function cleanTitle(name) {
    return String(name || '')
      .replace(/\[[^\]]*\]|\([^)]*\)|\{[^}]*\}/g, ' ')
      .replace(/\b(4k|uhd|fhd|hd|sd|1080p|720p|2160p|hevc|x264|x265|h264|h265|web-?dl|bluray|multi|vostfr|vf|truefrench|french|arabic|ar|en|fr|dual|latino|dubbed|sub|subbed)\b/ig, ' ')
      .replace(/^[A-Z]{2,4}\s*[-:|]\s*/, '')
      .replace(/\s*[-:|]\s*S\d+.*$/i, '')
      .replace(/[._]/g, ' ').replace(/\s+/g, ' ').trim();
  }
  function norm(t) { return String(t || '').toLowerCase().replace(/&/g, 'and').replace(/[^a-z0-9\u0600-\u06ff]+/g, ' ').trim(); }
  function titleMatch(q, cands) {
    var a = norm(q); if (!a) return false;
    for (var i = 0; i < cands.length; i++) {
      var b = norm(cands[i]); if (!b) continue;
      if (a === b || a.indexOf(b) === 0 || b.indexOf(a) === 0) return true;
      var wa = a.split(' '), wb = b.split(' '), common = 0; wa.forEach(function (w) { if (w.length > 2 && wb.indexOf(w) >= 0) common++; });
      if (common >= Math.max(1, Math.ceil(Math.min(wa.length, wb.length) * 0.6))) return true;
    }
    return false;
  }
  function yearOf(s) { var m = String(s || '').match(/(19|20)\d{2}/); return m ? m[0] : ''; }
  function search(type, name, year) {
    if (!enabled()) return Promise.resolve(null);
    var q = cleanTitle(name); if (!q) return Promise.resolve(null);
    var ck = type + '|' + q.toLowerCase() + '|' + (year || '');
    if (mem[ck]) return Promise.resolve(mem[ck]);
    var cached = Store.get('tmdb:' + ck); if (cached) { mem[ck] = cached; return Promise.resolve(cached); }
    var params = { api_key: key(), query: q, include_adult: false };
    if (year) params[type === 'tv' ? 'first_air_date_year' : 'year'] = year;
    return U.getJSON(BASE + '/search/' + type + '?' + U.qs(params)).then(function (r) {
      var hit = r && r.results && r.results[0];
      if (!hit && year) { delete params[type === 'tv' ? 'first_air_date_year' : 'year']; return U.getJSON(BASE + '/search/' + type + '?' + U.qs(params)).then(function (r2) { return r2 && r2.results && r2.results[0]; }); }
      return hit;
    }).then(function (hit) {
      // v1.5: never accept a hit whose title does not resemble the playlist title (avoids posters of other films)
      if (hit && !titleMatch(q, [hit.title, hit.name, hit.original_title, hit.original_name])) hit = null;
      var out = hit ? { tmdbId: hit.id, backdrop: hit.backdrop_path ? IMG + 'w1280' + hit.backdrop_path : '', poster: hit.poster_path ? IMG + 'w500' + hit.poster_path : '', plot: hit.overview || '', rating: hit.vote_average || 0, year: (hit.release_date || hit.first_air_date || '').substr(0, 4), title: hit.title || hit.name } : { none: true };
      mem[ck] = out; Store.set('tmdb:' + ck, out); return out.none ? null : out;
    }).catch(function () { return null; });
  }
  function details(type, tmdbId) {
    if (!enabled() || !tmdbId) return Promise.resolve(null);
    var ck = 'd|' + type + '|' + tmdbId; if (mem[ck]) return Promise.resolve(mem[ck]);
    return U.getJSON(BASE + '/' + type + '/' + tmdbId + '?' + U.qs({ api_key: key(), append_to_response: 'videos,credits' })).then(function (d) {
      var trailer = ((d.videos && d.videos.results) || []).filter(function (v) { return v.site === 'YouTube' && /Trailer|Teaser/i.test(v.type); })[0];
      var out = { genre: (d.genres || []).map(function (g) { return g.name; }).join(', '), runtime: d.runtime || (d.episode_run_time && d.episode_run_time[0]) || 0, cast: ((d.credits && d.credits.cast) || []).slice(0, 6).map(function (c) { return c.name; }).join(', '), castList: ((d.credits && d.credits.cast) || []).slice(0, 8).map(function (c) { return { name: c.name, role: c.character || '', img: c.profile_path ? IMG + 'w185' + c.profile_path : '' }; }), director: ((d.credits && d.credits.crew) || []).filter(function (c) { return c.job === 'Director'; }).map(function (c) { return c.name; }).join(', '), trailer: trailer ? 'https://www.youtube.com/watch?v=' + trailer.key : '', tagline: d.tagline || '' };
      mem[ck] = out; return out;
    }).catch(function () { return null; });
  }
  /* enrich(item) -> Promise<item merged with tmdb info> (mutates & returns item) */
  function enrich(item) {
    if (!enabled() || item._tmdb) return Promise.resolve(item);
    var type = item.type === 'series' ? 'tv' : 'movie';
    return search(type, item.name, yearOf(item.year)).then(function (r) {
      item._tmdb = true; if (!r) return item;
      if (!item.backdrop) item.backdrop = r.backdrop;
      if (!item.poster || /^data:|placeholder|no[-_]?image/i.test(item.poster)) item.poster = r.poster;
      if (!item.plot || item.plot.length < 20) item.plot = r.plot;
      if ((!item.rating || Number(item.rating) === 0) && r.rating) item.rating = r.rating;
      if (!item.year && r.year) item.year = r.year;
      item.tmdbId = r.tmdbId;
      return details(type, r.tmdbId).then(function (d) { if (d) { if (!item.genre) item.genre = d.genre; if (!item.cast) item.cast = d.cast; item.castList = d.castList; if (!item.director) item.director = d.director; if (!item.duration && d.runtime) item.duration = Math.floor(d.runtime / 60) + 'h ' + (d.runtime % 60) + 'm'; item.trailer = d.trailer; item.tagline = d.tagline; } return item; });
    });
  }
  return { enabled: enabled, enrich: enrich, cleanTitle: cleanTitle };
})();
