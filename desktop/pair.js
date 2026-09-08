/* RGBTv desktop — "Add from phone" server (same page + protocol as the webOS Luna service).
 * Starts a tiny HTTP server on the LAN (port 8765): the phone opens http://<pc-ip>:8765/, fills the form,
 * POSTs to /add and the app polls pair('pairPoll') to receive the profile. */
const http = require('http'), os = require('os'), url = require('url');
const PAIR_PORT = 8765;
let server = null, queue = [], keepAlive = null;

function lanIPs() {
  const out = [], ifs = os.networkInterfaces();
  Object.keys(ifs).forEach((n) => (ifs[n] || []).forEach((a) => { if ((a.family === 'IPv4' || a.family === 4) && !a.internal && !/^169\.254\./.test(a.address)) out.push(a.address); }));
  // prefer private LAN ranges first
  out.sort((a, b) => (/^192\.168\./.test(b) ? 1 : 0) - (/^192\.168\./.test(a) ? 1 : 0));
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

function start(lang) {
  return new Promise((resolve, reject) => {
    const done = () => { if (keepAlive) clearTimeout(keepAlive); keepAlive = setTimeout(stop, 10 * 60000); resolve({ returnValue: true, ips: lanIPs(), port: PAIR_PORT }); };
    if (server) return done();
    server = http.createServer((req, res) => {
      const u = url.parse(req.url, true);
      if (req.method === 'GET' && (u.pathname === '/' || u.pathname === '/index.html')) { res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' }); res.end(pairPage(u.query.lang || lang)); return; }
      if (req.method === 'POST' && u.pathname === '/add') {
        let body = ''; req.on('data', (c) => { body += c; if (body.length > 65536) req.destroy(); });
        req.on('end', () => { try { const d = JSON.parse(body || '{}'); if (!d.name || !d.url) throw new Error('bad'); d.at = Date.now(); queue.push(d); res.writeHead(200, { 'Content-Type': 'application/json' }); res.end('{"ok":true}'); } catch (e) { res.writeHead(400); res.end('{"ok":false}'); } });
        return;
      }
      res.writeHead(404); res.end();
    });
    server.on('error', (e) => { server = null; reject(new Error(e.code === 'EADDRINUSE' ? 'Port ' + PAIR_PORT + ' is already in use' : e.message)); });
    server.listen(PAIR_PORT, '0.0.0.0', done);
  });
}
function poll() { const q = queue; queue = []; if (keepAlive) { clearTimeout(keepAlive); keepAlive = setTimeout(stop, 10 * 60000); } return { returnValue: true, items: q, running: !!server }; }
function stop() { if (server) { try { server.close(); } catch (e) { } server = null; } queue = []; if (keepAlive) { clearTimeout(keepAlive); keepAlive = null; } return { returnValue: true }; }

module.exports = { start, poll, stop, lanIPs };
