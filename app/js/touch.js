/* RGBTv — Touch engine (Android phone / tablet).
 * The TV UI positions everything with translate() driven by the remote's focus. On a touch screen we instead let the
 * finger drive those same transforms 1:1 (with inertia), and turn a clean tap into a focus + activation instantly.
 * Panes: settings, home/favorites/search rows (vertical) + card rows (horizontal), virtual lists (categories,
 * channels, poster grids), episodes, 10-day forecast, hub strips.  Remote/keyboard use still works unchanged. */
var Touch = (function () {
  var on = false, S = 1, TAP = 10, drag = null, anim = null;
  function scale() { return U.scale || 1; }
  function has(el, c) { return el && el.classList && el.classList.contains(c); }
  function up(t, fn) { while (t && t !== document) { if (fn(t)) return t; t = t.parentNode; } return null; }
  function rtl() { return document.body.classList.contains('rtl'); }

  /* ---- pane detection: returns {el, axis, get(), set(px), max} or null ---- */
  function paneFor(target, axis) {
    var el, vl;
    if (axis === 'x') {
      // horizontal card rows / hub strips
      el = up(target, function (n) { return has(n, 'row-items') || has(n, 'hub-items'); });
      if (!el) return null;
      var view = el.parentNode.clientWidth, mx = Math.max(0, el.scrollWidth - view), sign = rtl() ? 1 : -1;
      if (mx <= 0) return null;
      return { el: el, axis: 'x', max: mx, get: function () { return Math.abs(cur(el, 'x')); }, set: function (v) { setT(el, sign * v, 0); } };
    }
    // virtual lists (categories / channels / grids): scroll by pixel through the VList
    el = up(target, function (n) { return !!n._vlist; });
    if (el) { vl = el._vlist; if (vl.maxScroll() <= 0) return null; return { el: el, axis: 'y', vl: vl, get: function () { return vl.scrollPos(); }, set: function (p) { vl.scrollToPx(p); }, max: vl.maxScroll() }; }
    // vertical panes
    el = up(target, function (n) { return has(n, 'settings-wrap') || has(n, 'rows') || has(n, 'ep-inner') || has(n, 'wx-days'); });
    if (!el) return null;
    var max = 0, extra = null;
    if (has(el, 'settings-wrap')) max = Math.max(0, el.offsetTop + el.scrollHeight - 1080 + 60);
    else if (has(el, 'rows')) {
      max = Math.max(0, el.offsetTop + el.offsetHeight - 400 - 1080 + 60);
      var sec = el.parentNode; if (sec && sec.id === 'sec-home' && !sec.classList.contains('rows-mode')) extra = U.$('.hero', sec); // hero glides with the rows
    }
    else if (has(el, 'ep-inner') || has(el, 'wx-days')) max = Math.max(0, el.scrollHeight - el.parentNode.clientHeight);
    else return null;
    if (max <= 0) return null;
    return { el: el, axis: 'y', max: max, extra: extra, get: function () { return -cur(el, 'y'); }, set: function (v) { setT(el, 0, -v); if (extra) setT(extra, 0, -v); } };
  }
  function cur(el, axis) { var m = /translate(?:X|Y|3d)?\(([-\d.]+)px(?:,\s*([-\d.]+)px)?/.exec(el.style.transform || ''); if (!m) return 0; if (/translateY/.test(el.style.transform)) return axis === 'y' ? Number(m[1]) : 0; if (/translateX/.test(el.style.transform)) return axis === 'x' ? Number(m[1]) : 0; return Number(axis === 'x' ? m[1] : (m[2] || 0)); }
  function setT(el, x, y) { el.style.transition = 'none'; el.style.webkitTransform = el.style.transform = (x ? 'translateX(' + x + 'px)' : 'translateY(' + y + 'px)'); }
  function restore(p) { if (!p) return; p.el.style.transition = ''; if (p.extra) p.extra.style.transition = ''; }

  /* ---- gesture ---- */
  function start(ev) {
    if (!on || ev.touches.length !== 1) { drag = null; return; }
    stopAnim();
    var t = ev.touches[0];
    drag = { x: t.clientX, y: t.clientY, x0: t.clientX, y0: t.clientY, t: Date.now(), target: ev.target, pane: null, moved: false, v: 0, hist: [] };
  }
  function move(ev) {
    if (!drag || ev.touches.length !== 1) return;
    var t = ev.touches[0], dx = t.clientX - drag.x, dy = t.clientY - drag.y, now = Date.now();
    if (!drag.moved) {
      var ax = Math.abs(t.clientX - drag.x0), ay = Math.abs(t.clientY - drag.y0); if (ax < TAP && ay < TAP) return;
      // decide axis once, then look for a pane that scrolls on that axis (a horizontal row inside a vertical page)
      var axis = ax > ay ? 'x' : 'y', pane = paneFor(drag.target, axis);
      drag.moved = true; drag.pane = pane; drag.axis = axis; drag.pos = pane ? pane.get() : 0;
      if (document.activeElement && document.activeElement.tagName === 'INPUT') try { document.activeElement.blur(); } catch (e) { }
    }
    drag.x = t.clientX; drag.y = t.clientY;
    if (ev.cancelable) ev.preventDefault();
    if (!drag.pane) return;
    var d = (drag.axis === 'x' ? dx : dy) / scale(); if (drag.axis === 'x' && rtl()) d = -d;
    drag.pos = clamp(drag.pos - d, 0, drag.pane.max); drag.pane.set(drag.pos);
    drag.hist.push({ t: now, p: drag.pos }); if (drag.hist.length > 6) drag.hist.shift();
  }
  function end(ev) {
    if (!drag) return; var d = drag; drag = null;
    if (!d.moved) { tap(d.target, ev); return; }
    if (ev.cancelable) ev.preventDefault(); // a drag never turns into a click
    if (!d.pane) return;
    // inertia: velocity from the last ~100 ms
    var h = d.hist, v = 0; if (h.length >= 2) { var a = h[0], b = h[h.length - 1], dt = b.t - a.t; if (dt > 0 && Date.now() - b.t < 80) v = (b.p - a.p) / dt; }
    if (Math.abs(v) < 0.08) { restore(d.pane); return; }
    fling(d.pane, d.pos, v * 1000);
  }
  function fling(pane, pos, v) {
    var last = Date.now();
    function step() {
      var now = Date.now(), dt = Math.min(48, now - last); last = now;
      pos += v * dt / 1000; v *= Math.pow(0.0025, dt / 1000); // exponential friction (~2.5‰ left after 1 s)
      if (pos <= 0 || pos >= pane.max) { pos = clamp(pos, 0, pane.max); v = 0; }
      pane.set(pos);
      if (Math.abs(v) > 12) anim = requestAnimationFrame(step); else { anim = null; restore(pane); }
    }
    anim = requestAnimationFrame(step);
  }
  function stopAnim() { if (anim) { cancelAnimationFrame(anim); anim = null; } }
  function clamp(v, a, b) { return v < a ? a : v > b ? b : v; }

  /* ---- tap: focus + activate immediately, no browser click heuristics ---- */
  function tap(target, ev) {
    var f = up(target, function (n) { return has(n, 'focusable'); });
    if (!f) return; // let non-focusable taps (e.g. modal backdrop) behave natively
    if (f.tagName === 'INPUT') { Nav.focusByPointer(f); try { f.focus(); } catch (e) { } return; } // keyboard needs the native tap
    if (ev.cancelable) ev.preventDefault();
    Nav.focusByPointer(f);
    try { f.click(); } catch (e) { }
  }

  function init() {
    on = true;
    document.addEventListener('touchstart', start, { passive: true });
    document.addEventListener('touchmove', move, { passive: false });
    document.addEventListener('touchend', end, { passive: false });
    document.addEventListener('touchcancel', function () { if (drag && drag.pane) restore(drag.pane); drag = null; }, { passive: true });
    // remote / keyboard takes over again: drop the hero offset so the focus-driven layout is consistent
    document.addEventListener('keydown', function () { var hero = U.$('#sec-home .hero'); if (hero && hero.style.transform) { hero.style.transition = ''; hero.style.transform = ''; } }, true);
  }
  return { init: init, active: function () { return on; } };
})();
