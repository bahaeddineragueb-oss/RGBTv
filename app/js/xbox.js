/* RGBTv — Xbox host adapter (UWP + WebView2 shell in /xbox).
 * Active only when the native shell injected window.__RGBTV_XBOX before the page scripts ran.
 *  - exposes window.RGBTvHost { platform:'xbox', fetchAsync, exit } → util.js routes API calls through the native
 *    HttpClient (custom headers, no CORS, player User-Agent) exactly like the Android shell
 *  - gamepad → the app's remote key codes (A=OK, B=Back, X=Red, Y=Yellow, LB/RB=CH-/CH+, LT/RT=REW/FF,
 *    Menu=Info, View=Green, LS=Play/Pause, RS=Blue, D-pad / left stick = arrows)
 *  - reports playback state to the shell (System Media Transport Controls + keep-screen-on) */
(function () {
  if (!window.__RGBTV_XBOX || !window.chrome || !chrome.webview) return;
  var wv = chrome.webview, device = String(window.__RGBTV_XBOX.device || 'Xbox');
  function send(o) { try { wv.postMessage(JSON.stringify(o)); } catch (e) { } }

  window.RGBTvHost = {
    platform: function () { return 'xbox'; },
    isTv: function () { return true; },
    device: function () { return device; },
    exit: function () { send({ type: 'exit' }); },
    fetchAsync: function (id, url, method, headersJson, body, timeoutMs) {
      var h = {}; try { h = JSON.parse(headersJson || '{}'); } catch (e) { }
      send({ type: 'fetch', id: id, url: url, method: method || 'GET', headers: h, body: body || '', timeout: timeoutMs || 20000 });
    }
  };

  /* ---- gamepad keys. WebView2 on Xbox delivers controller input as keydown events with these codes ---- */
  var MAP = { 195: 13, 196: 461, 197: 403, 198: 405, 199: 33, 200: 34, 201: 412, 202: 417, 203: 38, 204: 40, 205: 37, 206: 39, 207: 457, 208: 404, 209: 10252, 210: 406, 211: 38, 212: 40, 213: 39, 214: 37, 215: 38, 216: 40, 217: 39, 218: 37 };
  var lastBack = 0, keyEventsSeen = false, lastAct = { code: 0, t: 0 };
  function fire(code) {
    var now = Date.now();
    if (code === 461) { if (now - lastBack < 400) return; lastBack = now; }
    if (code === lastAct.code && now - lastAct.t < 40) return; // same press delivered twice (key event + poll)
    lastAct = { code: code, t: now };
    if (window.Nav) Nav.press(code);
  }
  document.addEventListener('keydown', function (ev) {
    var m = MAP[ev.keyCode]; if (!m) return;
    keyEventsSeen = true;
    if (ev.keyCode === 195 && document.activeElement && document.activeElement.tagName === 'INPUT') { m = 13; } // A inside a text box = Enter
    ev.preventDefault(); ev.stopImmediatePropagation(); fire(m);
  }, true);

  /* native BackRequested (B button handled by the system) → same as a BACK key; de-duplicated against keydown 196 */
  window.RGBTvXbox = { back: function () { fire(461); }, device: device };

  /* ---- Gamepad API fallback (only used if the shell does not translate the controller into key events) ---- */
  var BTN = { 0: 13, 1: 461, 2: 403, 3: 405, 4: 34, 5: 33, 6: 412, 7: 417, 8: 404, 9: 457, 10: 10252, 11: 406, 12: 38, 13: 40, 14: 37, 15: 39 };
  var prev = {}, hold = {}, REPEAT0 = 420, REPEAT = 130;
  function poll() {
    if (keyEventsSeen) return; // real key events work → stop polling
    var pads = navigator.getGamepads ? navigator.getGamepads() : null, now = Date.now();
    if (pads) for (var p = 0; p < pads.length; p++) {
      var g = pads[p]; if (!g) continue;
      var st = {}; for (var b = 0; b < g.buttons.length && b < 16; b++) st[b] = !!(g.buttons[b] && g.buttons[b].pressed);
      var ax = g.axes || [];
      if (ax[0] < -0.6) st[14] = true; if (ax[0] > 0.6) st[15] = true; if (ax[1] < -0.6) st[12] = true; if (ax[1] > 0.6) st[13] = true;
      for (var k in BTN) {
        var down = !!st[k], was = !!prev[k];
        if (down && !was) { fire(BTN[k]); hold[k] = now + REPEAT0; }
        else if (down && was && k >= 12 && now > hold[k]) { fire(BTN[k]); hold[k] = now + REPEAT; }
      }
      prev = st; break; // first connected pad only
    }
    (window.requestAnimationFrame || function (f) { setTimeout(f, 16); })(poll);
  }
  window.addEventListener('gamepadconnected', function () { if (!keyEventsSeen) poll(); });
  setTimeout(function () { if (!keyEventsSeen) poll(); }, 1500);

  /* ---- playback state → shell (SMTC / keep display on) ---- */
  function playbackState(state) {
    var cur = (window.Player && Player.current) ? Player.current() : null;
    send({ type: 'playback', state: state, title: cur ? (cur.title || cur.name || '') : '', subtitle: cur ? (cur.subtitle || '') : '' });
  }
  document.addEventListener('DOMContentLoaded', function () {
    var v = document.getElementById('video'); if (!v) return;
    v.addEventListener('playing', function () { playbackState('playing'); });
    v.addEventListener('pause', function () { playbackState('paused'); });
    v.addEventListener('emptied', function () { playbackState('stopped'); });
    v.addEventListener('ended', function () { playbackState('stopped'); });
    /* Chromium cannot play raw MPEG-TS in <video>; keep Xtream live streams on HLS */
    try { if (window.Store && Store.settings().liveFormat === 'ts') Store.setSetting('liveFormat', 'm3u8'); } catch (e) { }
    /* one-time controller legend */
    setTimeout(function () {
      try {
        if (localStorage.getItem('rgbtv_xbox_hint') || !window.UI) return;
        localStorage.setItem('rgbtv_xbox_hint', '1');
        UI.toast('A = OK · B = Back · X = ★ · Y = Search · LB/RB = Channels · ☰ = Info', 8000, '🎮');
      } catch (e) { }
    }, 5000);
  });
})();
