/* RGBTv webOS JS Service — performs HTTP requests with arbitrary headers
 * (Cookie / Authorization / X-User-Agent) that a browser context cannot set.
 * Method: luna://com.rgbtv.app.service/fetch  { url, method, headers, body, timeout } -> { status, body, headers } */
var Service = require('webos-service');
var http = require('http'), https = require('https'), url = require('url');
var service = new Service('com.rgbtv.app.service');
/* webOS 3.x ships Node 0.12 (no Object.assign) — without this the service crashed on its first request and the app
   silently fell back to plain XHR with the browser User-Agent (=> HTTP 444 / 512 from IPTV panels). */
/* webOS unloads a JS service 5 s after its last message; restarting Node on an old TV costs 1-3 s and that delay landed on
   every Stalker create_link / blocked-panel request (= slow channel start). Keep the process warm for 10 min of idle. */
try { if (service.activityManager) service.activityManager.idleTimeout = 600; } catch (e) { }
if (typeof Object.assign !== 'function') Object.assign = function (t) { for (var i = 1; i < arguments.length; i++) { var s = arguments[i]; if (s) for (var k in s) if (Object.prototype.hasOwnProperty.call(s, k)) t[k] = s[k]; } return t; };

function doFetch(opts, cb, redirects) {
  redirects = redirects || 0;
  var u = url.parse(opts.url), mod = u.protocol === 'https:' ? https : http;
  var req = mod.request({
    hostname: u.hostname, port: u.port, path: u.path, method: opts.method || 'GET',
    headers: Object.assign({ 'User-Agent': 'RGBTv/2.1 (webOS Smart TV) IPTVSmarters/3.1 ExoPlayerLib/2.18', 'Accept': '*/*', 'Accept-Encoding': 'identity', 'Connection': 'close' }, opts.headers || {}),
    rejectUnauthorized: false, timeout: opts.timeout || 20000
  }, function (res) {
    if ([301, 302, 303, 307, 308].indexOf(res.statusCode) >= 0 && res.headers.location && redirects < 5) {
      res.resume(); opts.url = url.resolve(opts.url, res.headers.location); return doFetch(opts, cb, redirects + 1);
    }
    var chunks = [];
    res.on('data', function (c) { chunks.push(c); });
    res.on('end', function () { cb(null, { status: res.statusCode, headers: res.headers, body: Buffer.concat(chunks).toString('utf8') }); });
  });
  req.on('timeout', function () { req.abort(); cb(new Error('Timeout')); });
  req.on('error', function (e) { if (/socket hang up|ECONNRESET/i.test(String(e && e.message))) cb(null, { status: 444, headers: {}, body: '' }); else cb(e); });
  if (opts.body) req.write(opts.body);
  req.end();
}

/* Big bodies (M3U playlists are often 5-40 MB) are streamed to the app in 1 MB chunks: the Luna bus rejects huge
   single messages. fetch -> { ..., chunked:true, id, total, body:<first chunk> } then fetchMore { id, offset }. */
var CHUNK = 1024 * 1024, bigBodies = {}, bigSeq = 0;
function sweepBig() { var now = Date.now(); for (var k in bigBodies) if (now - bigBodies[k].at > 180000) delete bigBodies[k]; }
service.register('fetch', function (message) {
  var p = message.payload || {};
  if (!p.url) { message.respond({ returnValue: false, errorText: 'url required' }); return; }
  doFetch(p, function (err, r) {
    if (err) { message.respond({ returnValue: false, errorText: String(err.message || err) }); return; }
    if (r.body.length > CHUNK) {
      sweepBig(); var id = 'b' + (++bigSeq); bigBodies[id] = { at: Date.now(), body: r.body };
      message.respond({ returnValue: true, status: r.status, headers: r.headers, chunked: true, id: id, total: r.body.length, body: r.body.slice(0, CHUNK) });
    } else message.respond({ returnValue: true, status: r.status, headers: r.headers, body: r.body });
  });
});
service.register('fetchMore', function (message) {
  var p = message.payload || {}, b = bigBodies[p.id];
  if (!b) { message.respond({ returnValue: false, errorText: 'chunk expired' }); return; }
  var off = p.offset | 0, part = b.body.slice(off, off + CHUNK), done = off + part.length >= b.body.length;
  if (done) delete bigBodies[p.id]; else b.at = Date.now();
  message.respond({ returnValue: true, body: part, done: done });
});

service.register('ping', function (message) { message.respond({ returnValue: true, pong: true, time: Date.now() }); });

/* ===================================================================
 * v1.6 — "Add from phone": tiny HTTP server on the TV's LAN address.
 * The phone opens http://<tv-ip>:8765/ , fills the form, the TV app polls
 * luna://com.rgbtv.app.service/pairPoll and receives the new profile.
 * =================================================================== */
var os = require('os'), querystring = require('querystring');
var PAIR_PORT = 8765, pairActivity = null, pairServer = null, pairQueue = [], pairToken = null, pairKeepAlive = null, pairInfo = { app: 'RGBTv', profiles: [] };

function lanIPs() {
  var out = [], ifs = os.networkInterfaces();
  Object.keys(ifs).forEach(function (n) { (ifs[n] || []).forEach(function (a) { if (a.family === 'IPv4' && !a.internal) out.push(a.address); }); });
  return out;
}
function pairPage(lang) {
  var ar = lang === 'ar';
  var t = ar ? { title: 'إضافة سيرفر إلى RGBTv', sub: 'املأ البيانات من هاتفك ثم اضغط إرسال — سيظهر البروفايل على التلفاز فورًا.', name: 'اسم البروفايل', type: 'نوع السيرفر', url: 'رابط السيرفر', user: 'اسم المستخدم', pass: 'كلمة المرور', mac: 'عنوان MAC', epg: 'رابط EPG (اختياري)', pin: 'رمز PIN (اختياري، 4 أرقام)', send: 'إرسال إلى التلفاز', ok: 'تم الإرسال ✓ — انظر إلى التلفاز', err: 'تعذر الإرسال، حاول مجددًا', m3uHint: 'أو ألصق رابط get.php الكامل هنا', dir: 'rtl' }
             : { title: 'Add a server to RGBTv', sub: 'Fill in the details from your phone and tap Send — the profile appears on the TV instantly.', name: 'Profile name', type: 'Server type', url: 'Server URL', user: 'Username', pass: 'Password', mac: 'MAC address', epg: 'EPG URL (optional)', pin: 'PIN (optional, 4 digits)', send: 'Send to TV', ok: 'Sent ✓ — look at your TV', err: 'Could not send, try again', m3uHint: 'or paste a full get.php link here', dir: 'ltr' };
  return '<!DOCTYPE html><html lang="' + (ar ? 'ar' : 'en') + '" dir="' + t.dir + '"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>' + t.title + '</title><style>' +
    'body{margin:0;background:#0a0e1a;color:#f3f4f6;font-family:-apple-system,Segoe UI,Roboto,Arial,sans-serif;padding:20px}h1{font-size:22px;margin:0 0 6px}h1 span{background:linear-gradient(90deg,#ef4444,#22c55e,#3b82f6);-webkit-background-clip:text;color:transparent}p{color:#9aa3b2;font-size:14px;margin:0 0 18px}' +
    'label{display:block;font-size:13px;color:#9aa3b2;margin:12px 0 4px}input,select{width:100%;box-sizing:border-box;padding:13px 14px;border-radius:10px;border:1px solid #2a3350;background:#141b30;color:#fff;font-size:16px}' +
    '.tabs{display:flex;gap:8px;margin-top:8px}.tabs button{flex:1;padding:12px;border-radius:10px;border:1px solid #2a3350;background:#141b30;color:#9aa3b2;font-size:14px}.tabs button.on{background:#6d5dfc;color:#fff;border-color:#6d5dfc}' +
    '.f{display:none}.f.on{display:block}button.send{width:100%;margin-top:22px;padding:16px;border:0;border-radius:12px;background:#6d5dfc;color:#fff;font-size:17px;font-weight:700}#msg{margin-top:14px;font-size:15px;text-align:center;min-height:20px}.ok{color:#4ade80}.bad{color:#f87171}small{color:#6b7280}' +
    '</style></head><body><h1>RGB<span>Tv</span> · ' + t.title + '</h1><p>' + t.sub + '</p>' +
    '<form id="f" onsubmit="return send()"><label>' + t.name + '</label><input name="name" required placeholder="Living room">' +
    '<label>' + t.type + '</label><div class="tabs"><button type="button" class="on" data-t="xtream">Xtream Codes</button><button type="button" data-t="stalker">Stalker</button><button type="button" data-t="m3u">M3U</button></div><input type="hidden" name="type" value="xtream">' +
    '<label>' + t.url + '</label><input name="url" required placeholder="http://host:port" inputmode="url" autocapitalize="off"><small id="hint">' + t.m3uHint + '</small>' +
    '<div class="f on" data-f="xtream"><label>' + t.user + '</label><input name="username" autocapitalize="off"><label>' + t.pass + '</label><input name="password" autocapitalize="off"></div>' +
    '<div class="f" data-f="stalker"><label>' + t.mac + '</label><input name="mac" placeholder="00:1A:79:XX:XX:XX" autocapitalize="characters"></div>' +
    '<div class="f" data-f="m3u"><label>' + t.epg + '</label><input name="epg" inputmode="url" autocapitalize="off"></div>' +
    '<label>' + t.pin + '</label><input name="pin" maxlength="4" inputmode="numeric" pattern="\\d{4}">' +
    '<button class="send" type="submit">' + t.send + '</button><div id="msg"></div></form>' +
    '<script>var tabs=document.querySelectorAll(".tabs button");for(var i=0;i<tabs.length;i++)tabs[i].onclick=function(){for(var j=0;j<tabs.length;j++)tabs[j].className="";this.className="on";var t=this.getAttribute("data-t");document.querySelector("[name=type]").value=t;var fs=document.querySelectorAll(".f");for(var k=0;k<fs.length;k++)fs[k].className="f"+(fs[k].getAttribute("data-f")===t?" on":"");document.getElementById("hint").style.display=t==="xtream"?"":"none";};' +
    'function send(){var f=document.getElementById("f"),d={},els=f.elements;for(var i=0;i<els.length;i++)if(els[i].name)d[els[i].name]=els[i].value;var x=new XMLHttpRequest();x.open("POST","/add",true);x.setRequestHeader("Content-Type","application/json");x.onload=function(){var m=document.getElementById("msg");if(x.status===200){m.className="ok";m.textContent="' + t.ok + '";f.reset();}else{m.className="bad";m.textContent="' + t.err + '";}};x.onerror=function(){document.getElementById("msg").className="bad";document.getElementById("msg").textContent="' + t.err + '";};x.send(JSON.stringify(d));return false;}</script></body></html>';
}
function startPairServer(lang, cb) {
  if (pairServer) { cb(null); return; }
  pairServer = http.createServer(function (req, res) {
    var u = url.parse(req.url, true);
    if (req.method === 'GET' && (u.pathname === '/' || u.pathname === '/index.html')) { res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' }); res.end(pairPage(u.query.lang || lang)); return; }
    if (req.method === 'POST' && u.pathname === '/add') {
      var body = ''; req.on('data', function (c) { body += c; if (body.length > 20000) req.destroy(); });
      req.on('end', function () { try { var d = JSON.parse(body || '{}'); if (!d.name || !d.url) throw new Error('bad'); d.at = Date.now(); pairQueue.push(d); res.writeHead(200, { 'Content-Type': 'application/json' }); res.end('{"ok":true}'); } catch (e) { res.writeHead(400); res.end('{"ok":false}'); } });
      return;
    }
    res.writeHead(404); res.end();
  });
  pairServer.on('error', function (e) { pairServer = null; cb(e); });
  pairServer.listen(PAIR_PORT, '0.0.0.0', function () { cb(null); });
}
function stopPairServer() { if (pairServer) { try { pairServer.close(); } catch (e) { } pairServer = null; } pairQueue = []; if (pairKeepAlive) { clearTimeout(pairKeepAlive); pairKeepAlive = null; } if (pairActivity && service.activityManager && service.activityManager.complete) { try { service.activityManager.complete(pairActivity, function () { }); } catch (e) { } pairActivity = null; } }
/* pairStart {lang} -> {ips:[..], port} ; keeps the service alive (subscription-less) for 10 min or until pairStop */
service.register('pairStart', function (message) {
  var p = message.payload || {};
  startPairServer(p.lang || 'en', function (err) {
    if (err) { message.respond({ returnValue: false, errorText: String(err.message || err) }); return; }
    if (pairKeepAlive) clearTimeout(pairKeepAlive);
    // keep the service process alive while pairing (webOS unloads idle services after a few seconds)
    if (!pairActivity && service.activityManager && service.activityManager.create) { try { service.activityManager.create('rgbtv-pair', function (a) { pairActivity = a; }); } catch (e) { } }
    pairKeepAlive = setTimeout(stopPairServer, 10 * 60000);
    message.respond({ returnValue: true, ips: lanIPs(), port: PAIR_PORT });
  });
});
service.register('pairPoll', function (message) { var q = pairQueue; pairQueue = []; if (pairKeepAlive) { clearTimeout(pairKeepAlive); pairKeepAlive = setTimeout(stopPairServer, 10 * 60000); } message.respond({ returnValue: true, items: q, running: !!pairServer }); });
service.register('pairStop', function (message) { stopPairServer(); message.respond({ returnValue: true }); });
