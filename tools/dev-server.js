/* RGBTv dev server: serves ./app and a mock Xtream Codes API for testing in a desktop browser.
 * Usage: node tools/dev-server.js [port]
 * Mock account:  URL http://localhost:PORT   user: demo   pass: demo */
var http = require('http'), fs = require('fs'), path = require('path'), url = require('url');
var PORT = Number(process.argv[2]) || 8080, ROOT = path.join(__dirname, '..', 'app');
var MIME = { '.html': 'text/html', '.js': 'application/javascript', '.css': 'text/css', '.png': 'image/png', '.jpg': 'image/jpeg', '.json': 'application/json', '.svg': 'image/svg+xml', '.mp3': 'audio/mpeg', '.m3u8': 'application/vnd.apple.mpegurl' };

var SAMPLE = 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8';
var MP4 = 'https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/720/Big_Buck_Bunny_720_10s_5MB.mp4';
var liveCats = [{ category_id: '1', category_name: 'News' }, { category_id: '2', category_name: 'Sports' }, { category_id: '3', category_name: 'Movies' }, { category_id: '9', category_name: 'Adult XXX' }];
var vodCats = [{ category_id: '10', category_name: 'Action' }, { category_id: '11', category_name: 'Comedy' }];
var serCats = [{ category_id: '20', category_name: 'Drama' }];
var N = Number(process.env.STRESS) || 1;
var live = []; for (var i = 1; i <= 60 * N; i++) live.push({ num: i, name: 'Channel ' + i + (i % 7 === 0 ? ' HD' : ''), stream_id: 1000 + i, stream_icon: 'https://picsum.photos/seed/ch' + i + '/160/100', category_id: String(liveCats[i % 4].category_id), epg_channel_id: 'ch' + i, tv_archive: i % 3 === 0 ? 1 : 0 });
var vod = []; for (var j = 1; j <= 80 * N; j++) vod.push({ num: j, name: 'Movie ' + j, stream_id: 2000 + j, stream_icon: 'https://picsum.photos/seed/m' + j + '/200/300', category_id: String(vodCats[j % 2].category_id), rating: (5 + (j % 5)).toString(), container_extension: 'mp4', added: String(1700000000 + j * 3600) });
var series = []; for (var k = 1; k <= 30 * N; k++) series.push({ num: k, name: 'Series ' + k, series_id: 3000 + k, cover: 'https://picsum.photos/seed/s' + k + '/200/300', category_id: '20', rating: '7.' + k % 10, plot: 'A gripping drama about series number ' + k + '.', releaseDate: '2023-01-0' + (k % 9 + 1), genre: 'Drama', last_modified: String(1700000000 + k * 7200) });

var flaky = {};
function json(res, o) { res.writeHead(200, { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' }); res.end(JSON.stringify(o)); }
function b64(s) { return Buffer.from(s).toString('base64'); }

// mock of the Luna pairing endpoints (browser dev only): /__pair/pairStart|pairPoll|pairStop ; POST /__pair/add
var pairQueue = [];
http.createServer(function (req, res) {
  var u = url.parse(req.url, true), p = u.pathname, q = u.query;
  if (p.indexOf('/__wx/') === 0) {
    var https = require('https');
    if (p === '/__wx/geo') { res.writeHead(200, { 'Content-Type': 'application/json' }); return res.end(JSON.stringify({ latitude: '36.72', longitude: '3.09', city: 'Algiers', country: 'Algeria' })); }
    var target = p === '/__wx/search' ? 'https://geocoding-api.open-meteo.com/v1/search' : 'https://api.open-meteo.com/v1/forecast';
    var qs = req.url.indexOf('?') >= 0 ? req.url.slice(req.url.indexOf('?')) : '';
    https.get(target + qs, function (r2) { var b = ''; r2.on('data', function (c) { b += c; }); r2.on('end', function () { res.writeHead(200, { 'Content-Type': 'application/json' }); res.end(b); }); }).on('error', function () { res.writeHead(502); res.end('{}'); });
    return;
  }
  if (p.indexOf('/__adhan/calendar/') === 0) {
    var https2 = require('https'), qs2 = req.url.indexOf('?') >= 0 ? req.url.slice(req.url.indexOf('?')) : '';
    https2.get('https://api.aladhan.com/v1/calendar/' + p.slice('/__adhan/calendar/'.length) + qs2, function (r2) { var b = ''; r2.on('data', function (c) { b += c; }); r2.on('end', function () { res.writeHead(200, { 'Content-Type': 'application/json' }); res.end(b); }); }).on('error', function () { res.writeHead(502); res.end('{}'); });
    return;
  }
  if (p.indexOf('/__pair/') === 0) {
    var m = p.slice(8); res.setHeader('Content-Type', 'application/json');
    if (m === 'pairStart') return res.end(JSON.stringify({ returnValue: true, ips: ['192.168.1.42'], port: 8765 }));
    if (m === 'pairPoll') { var items = pairQueue; pairQueue = []; return res.end(JSON.stringify({ returnValue: true, items: items, running: true })); }
    if (m === 'pairStop') return res.end('{"returnValue":true}');
    if (m === 'add') { var b = ''; req.on('data', function (c) { b += c; }); req.on('end', function () { pairQueue.push(JSON.parse(b)); res.end('{"ok":true}'); }); return; }
  }
  if (p === '/player_api.php') {
    if (q.username !== 'demo' || q.password !== 'demo') return json(res, { user_info: { auth: 0 } });
    switch (q.action) {
      case undefined: return json(res, { user_info: { username: 'demo', auth: 1, status: 'Active', exp_date: String(Math.floor(Date.now() / 1000) + 5 * 86400), max_connections: '2', active_cons: '0', allowed_output_formats: ['m3u8', 'ts'] }, server_info: { url: 'localhost', port: String(PORT), timezone: 'UTC' } });
      case 'get_live_categories': return json(res, liveCats);
      case 'get_vod_categories': return json(res, vodCats);
      case 'get_series_categories': return json(res, serCats);
      case 'get_live_streams': return json(res, q.category_id ? live.filter(function (x) { return x.category_id === q.category_id; }) : live);
      case 'get_vod_streams': return json(res, q.category_id ? vod.filter(function (x) { return x.category_id === q.category_id; }) : vod);
      case 'get_series': return json(res, series);
      case 'get_vod_info': return json(res, { info: { name: 'Movie ' + (q.vod_id - 2000), plot: 'Lorem ipsum dolor sit amet, an exciting adventure about a big rabbit.', movie_image: 'https://picsum.photos/seed/m' + (q.vod_id - 2000) + '/400/600', rating: '7.8', releasedate: '2022-05-01', genre: 'Action, Adventure', duration: '01:36:00', cast: 'John Doe, Jane Roe', director: 'Some Director' }, movie_data: { stream_id: Number(q.vod_id), name: 'Movie ' + (q.vod_id - 2000), container_extension: 'mp4' } });
      case 'get_series_info':
        var eps = {}; for (var s = 1; s <= 3; s++) { eps[String(s)] = []; for (var e = 1; e <= 8; e++) eps[String(s)].push({ id: String(q.series_id * 100 + s * 10 + e), episode_num: e, title: 'Episode ' + e, container_extension: 'mp4', season: s, info: { plot: 'Episode plot ' + e, duration: '00:42:00', movie_image: 'https://picsum.photos/seed/e' + s + e + '/160/90' } }); }
        return json(res, { info: { name: 'Series ' + (q.series_id - 3000), plot: 'A gripping drama.', cover: 'https://picsum.photos/seed/s' + (q.series_id - 3000) + '/400/600', rating: '8.1', genre: 'Drama', releaseDate: '2023', cast: 'Actor A, Actor B' }, episodes: eps });
      case 'get_short_epg':
        var now = Math.floor(Date.now() / 1000), list = []; for (var n = -1; n < 8; n++) list.push({ title: b64('Program ' + (n + 2) + ' on ' + q.stream_id), description: b64('Description'), start_timestamp: String(now + n * 1800 - 600), stop_timestamp: String(now + (n + 1) * 1800 - 600) });
        return json(res, { epg_listings: list });
    }
    return json(res, []);
  }
  // ---- M3U playlist mock (+ UA blocking like real nginx panels when ?strict=1: browser UA -> connection closed/444) ----
  if (p === '/playlist.m3u' || p === '/get.php') {
    var ua = req.headers['user-agent'] || '';
    if ((q.strict === '1' || p === '/get.php') && /Mozilla|Chrome|Safari|HeadlessChrome/i.test(ua) && !/IPTVSmarters|ExoPlayer|MAG|VLC|Lavf/i.test(ua)) { req.socket.destroy(); return; }
    var lines = ['\uFEFF#EXTM3U url-tvg="http://localhost:' + PORT + '/xmltv.xml"'];
    for (var i = 1; i <= (parseInt(q.n, 10) || 40); i++) lines.push('#EXTINF:-1 tvg-id="ch' + i + '" tvg-name="Channel ' + i + '" tvg-logo="http://localhost:' + PORT + '/img/icon.png" group-title="' + (i % 2 ? 'News' : 'Sports') + '",Channel ' + i, 'http://localhost:' + PORT + '/live/demo/demo/' + (1000 + i) + '.ts');
    for (var j = 1; j <= 12; j++) lines.push("#EXTINF:-1 tvg-logo='http://localhost:" + PORT + "/img/icon.png' group-title='Movies',Movie " + j, 'http://localhost:' + PORT + '/movie/demo/demo/' + (2000 + j) + '.mp4');
    lines.push('#EXTINF:-1 group-title="Series",Show S01 E01', '#EXTGRP:Series', 'http://localhost:' + PORT + '/series/demo/demo/3001.mp4', '#EXTINF:-1 group-title="Series",Show S01 E02', 'http://localhost:' + PORT + '/series/demo/demo/3002.mp4');
    res.writeHead(200, { 'Content-Type': 'audio/x-mpegurl' }); return res.end(lines.join('\n'));
  }
  // ---- Stalker portal mock: strict about the MAG headers, 444-style close for browser UAs ----
  if (p === '/stalker_portal/server/load.php' || p === '/server/load.php' || p === '/portal.php' || p === '/c/portal.php' || p === '/stalker_portal/portal.php') {
    var sua = req.headers['user-agent'] || '', cookie = req.headers.cookie || '', auth = req.headers.authorization || '';
    if (/Mozilla|Chrome|Safari|HeadlessChrome/i.test(sua) && !/MAG|stbapp/i.test(sua)) { req.socket.destroy(); return; }
    if (p !== '/stalker_portal/server/load.php') { res.writeHead(404); return res.end('not here'); }
    if (!/mac=/.test(cookie)) { res.writeHead(200); return res.end('Authorization failed.'); }
    if (q.action === 'handshake') { if (auth && auth !== 'Bearer ') { /* fine */ } return json(res, { js: { token: 'TOK123', random: 'x' } }); }
    if (!/Bearer TOK123/.test(auth)) { res.writeHead(401); return res.end('Authorization failed.'); }
    if (q.action === 'get_profile') return json(res, { js: { id: 7, status: 0, mac: q.mac } });
    if (q.action === 'get_main_info') return json(res, { js: { mac: q.mac, phone: '2030-01-01', end_date: 'January 1, 2030', login: 'demo', tariff_plan: 'Full' } });
    if (q.action === 'get_genres') return json(res, { js: [{ id: '*', title: 'All' }, { id: '1', title: 'News' }] });
    if (q.action === 'get_categories') return json(res, { js: [{ id: '10', title: 'Movies' }] });
    if (q.action === 'get_all_channels' || q.action === 'get_ordered_list') { var ch = []; for (var c = 1; c <= 30; c++) ch.push({ id: String(c), name: 'STB Channel ' + c, number: String(c), logo: '', cmd: 'ffmpeg http://localhost:' + PORT + '/live/demo/demo/' + (1000 + c) + '.ts', tv_genre_id: '1' }); return json(res, { js: { data: ch, total_items: ch.length, max_page_items: 30 } }); }
    if (q.action === 'create_link') return json(res, { js: { cmd: (q.cmd || '').replace(/^ffmpeg /, '') } });
    if (q.action === 'get_events') return json(res, { js: { data: [] } });
    return json(res, { js: [] });
  }
  if (p === '/__flaky') { flaky.n = 0; flaky.max = Number(q.n || 0); return json(res, { ok: true, max: flaky.max }); }
  // flaky stream simulation: /live/.../1001.* fails the first FLAKY times (404) then works
  if (/\/live\/[^/]+\/[^/]+\/1001\./.test(p)) { flaky.n = (flaky.n || 0) + 1; if (flaky.n <= (flaky.max != null ? flaky.max : (Number(process.env.FLAKY) || 0))) { res.writeHead(404); return res.end('offline'); } }
  if (p === '/streaming/timeshift.php') { res.writeHead(302, { Location: MP4 }); return res.end(); }
  if (/^\/(live|movie|series)\//.test(p)) { res.writeHead(302, { Location: p.indexOf('/live/') === 0 ? SAMPLE : MP4 }); return res.end(); }
  var file = path.join(ROOT, p === '/' ? 'index.html' : p);
  if (file.indexOf(ROOT) !== 0) { res.writeHead(403); return res.end(); }
  fs.readFile(file, function (err, data) {
    if (err) { res.writeHead(404); return res.end('Not found'); }
    res.writeHead(200, { 'Content-Type': MIME[path.extname(file)] || 'application/octet-stream', 'Cache-Control': 'no-cache' }); res.end(data);
  });
}).listen(PORT, '0.0.0.0', function () { console.log('RGBTv dev server on http://0.0.0.0:' + PORT + '  (mock Xtream: user demo / pass demo)'); });
