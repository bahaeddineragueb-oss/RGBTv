/* RGBTv — remote-control spatial navigation
 * Geometry-based focus movement among visible `.focusable` elements inside the active container.
 * Key codes cover webOS magic remote + standard TV keys. */
var Nav = (function () {
  var KEYS = {
    LEFT: 37, UP: 38, RIGHT: 39, DOWN: 40, ENTER: 13, BACK: 461, BACK2: 27, BACK3: 8,
    PLAY: 415, PAUSE: 19, PLAYPAUSE: 10252, STOP: 413, REW: 412, FF: 417, NEXT: 418, PREV: 419,
    RED: 403, GREEN: 404, YELLOW: 405, BLUE: 406, INFO: 457, CH_UP: 33, CH_DOWN: 34, EXIT: 1001
  };
  var current = null, container = document, handlers = [], keyHandlers = [], lastFocusByScope = {}, locked = false;

  function visible(e) {
    if (!e || e.offsetParent === null && getComputedStyle(e).position !== 'fixed') return false;
    var r = e.getBoundingClientRect(); if (r.width === 0 && r.height === 0) return false;
    for (var a = e.parentNode; a && a !== document; a = a.parentNode) { if (a.classList && a.classList.contains('hero') && a.parentNode && a.parentNode.classList.contains('rows-mode')) return false; }
    var vw = window.innerWidth || 1920, vh = window.innerHeight || 1080; if (r.bottom < -400 || r.top > vh + 400 || r.right < -400 || r.left > vw + 400) return false;
    return true;
  }
  function candidates() { return U.$$('.focusable', container).filter(visible); }
  function setContainer(c) { container = c || document; }
  function focus(e, silent) {
    if (!e) return;
    if (current && current !== e) current.classList.remove('focus');
    current = e; e.classList.add('focus');
    // v1.7: moving the highlight onto an input no longer opens the keyboard — OK (Enter) does. Leaving a field closes it.
    if (document.activeElement && document.activeElement.tagName === 'INPUT' && document.activeElement !== e) { try { document.activeElement.blur(); } catch (x) { } }
    var scope = e.getAttribute('data-nav'); if (scope) lastFocusByScope[scope] = e;
    if (!silent) handlers.forEach(function (h) { try { h(e); } catch (x) { console.error(x); } });
  }
  function getCurrent() { return current; }
  function blur() { if (current) current.classList.remove('focus'); current = null; }
  function onFocus(h) { handlers.push(h); }
  function onKey(h) { keyHandlers.push(h); }
  function lock(v) { locked = v; }

  function center(r) { return { x: r.left + r.width / 2, y: r.top + r.height / 2 }; }
  function move(dir) {
    var list = candidates();
    if (!current || !visible(current) || list.indexOf(current) < 0) { if (list.length) focus(list[0]); return; }
    var cr = current.getBoundingClientRect(), cc = center(cr), best = null, bestScore = Infinity;
    var sameScope = current.getAttribute('data-nav');
    list.forEach(function (e) {
      if (e === current) return;
      var r = e.getBoundingClientRect(), c = center(r), dx = c.x - cc.x, dy = c.y - cc.y, primary, secondary, ok;
      switch (dir) {
        case 'left': ok = r.right <= cr.left + 1 || (c.x < cc.x && r.left < cr.left - 2); primary = -dx; secondary = Math.abs(dy); break;
        case 'right': ok = r.left >= cr.right - 1 || (c.x > cc.x && r.right > cr.right + 2); primary = dx; secondary = Math.abs(dy); break;
        case 'up': ok = r.bottom <= cr.top + 1 || (c.y < cc.y && r.top < cr.top - 2); primary = -dy; secondary = Math.abs(dx); break;
        case 'down': ok = r.top >= cr.bottom - 1 || (c.y > cc.y && r.bottom > cr.bottom + 2); primary = dy; secondary = Math.abs(dx); break;
      }
      if (!ok || primary <= 0) return;
      // overlap on secondary axis reduces penalty (prefer aligned elements)
      var overlap = (dir === 'left' || dir === 'right') ? Math.max(0, Math.min(r.bottom, cr.bottom) - Math.max(r.top, cr.top)) : Math.max(0, Math.min(r.right, cr.right) - Math.max(r.left, cr.left));
      var score = primary + secondary * (overlap > 0 ? 0.6 : 2.2);
      if (e.getAttribute('data-nav') === sameScope) score *= 0.9;
      if (score < bestScore) { bestScore = score; best = e; }
    });
    if (best) { if (best._vlist) best._vlist.focusIndex(Number(best.getAttribute('data-i'))); else focus(best); }
    else handlers.forEach(function (h) { try { h(current, dir); } catch (x) { } }); // edge notification
  }

  function keyName(code) { for (var k in KEYS) if (KEYS[k] === code) return k; return null; }
  /* v1.7: some webOS firmwares deliver BOTH a keydown(13) and a pointer click for one press of the Magic Remote OK
     button. Whichever arrives second within 600ms for the same element is dropped so nothing activates twice. */
  var lastAct = { el: null, t: 0, src: '' };
  function within(t, el) { while (t && t !== document) { if (t === el) return true; t = t.parentNode; } return false; }
  document.addEventListener('click', function (ev) {
    if (ev.isTrusted === false) return; // our own el.click()
    var now = Date.now();
    if (lastAct.src === 'key' && now - lastAct.t < 500 && lastAct.el && within(ev.target, lastAct.el)) { ev.stopPropagation(); ev.preventDefault(); return; } // ghost click after OK-key on the same element
    if (pointerMode === 'click') { var f = ev.target; while (f && f !== document && !(f.classList && f.classList.contains('focusable'))) f = f.parentNode; if (f && f !== document && f !== current && !locked) { if (f._vlist) f._vlist.focusIndex(Number(f.getAttribute('data-i'))); else focus(f); } }
    var t = ev.target; while (t && t !== document && !(t.classList && t.classList.contains('focusable'))) t = t.parentNode;
    lastAct = { el: t && t !== document ? t : ev.target, t: now, src: 'click' };
  }, true);
  function onKeyDown(ev) {
    var code = ev.keyCode, name = keyName(code);
    if (locked && name !== 'BACK' && name !== 'BACK2') { ev.preventDefault(); return; }
    if (name === 'ENTER' && lastAct.src === 'click' && Date.now() - lastAct.t < 500 && current && lastAct.el && within(lastAct.el, current)) { ev.preventDefault(); return; } // ghost key after pointer click
    // typing inside an input
    var typing = document.activeElement && document.activeElement.tagName === 'INPUT';
    if (typing && (name === 'LEFT' || name === 'RIGHT' || name === 'BACK3' || !name)) return; // let the input handle it
    for (var i = keyHandlers.length - 1; i >= 0; i--) { if (keyHandlers[i](name, code, ev) === true) { ev.preventDefault(); return; } }
    switch (name) {
      case 'LEFT': move('left'); break; case 'RIGHT': move('right'); break;
      case 'UP': move('up'); break; case 'DOWN': move('down'); break;
      case 'ENTER': if (current) { lastAct = { el: current, t: Date.now(), src: 'key' }; if (current.tagName === 'INPUT') { if (typing) { current.blur(); } else current.focus(); } else current.click(); } break;
      default: return;
    }
    ev.preventDefault();
  }
  document.addEventListener('keydown', onKeyDown);
  /* host shells (Android back button, Electron menu) inject remote keys here */
  function press(code) { onKeyDown({ keyCode: code, preventDefault: function () { }, stopPropagation: function () { } }); return true; }
  // mouse / magic-remote pointer support
  // Only real pointer movement may steal focus: layout changes under a parked pointer fire mouseover too.
  var lastMouse = { x: -1, y: -1 }, pointer = false, pointerMode = 'hover'; // 'hover' = pointer moves highlight, OK opens · 'click' = pointer ignored until OK
  function setPointerMode(m) { pointerMode = m === 'click' ? 'click' : 'hover'; }
  document.addEventListener('mouseover', function (ev) {
    if (pointerMode === 'click') return;
    if (ev.screenX === lastMouse.x && ev.screenY === lastMouse.y) return; lastMouse.x = ev.screenX; lastMouse.y = ev.screenY;
    var t = ev.target; while (t && t !== document && !(t.classList && t.classList.contains('focusable'))) t = t.parentNode;
    if (t && t !== document && t !== current && visible(t) && !locked) {
      // pointer hover only moves the highlight — it never starts playback/preview; OK (click) does that
      pointer = true; try { if (t._vlist) t._vlist.focusIndex(Number(t.getAttribute('data-i'))); else focus(t); } finally { pointer = false; }
    }
  });
  function byPointer() { return pointer; }
  /* touch engine: move highlight to an element as if the pointer hovered it (no activation) */
  function focusByPointer(el) { if (!el || el === current || locked) return; pointer = true; try { if (el._vlist) el._vlist.focusIndex(Number(el.getAttribute('data-i'))); else focus(el); } finally { pointer = false; } }
  document.addEventListener('wheel', function (ev) { if (!locked) move(ev.deltaY > 0 ? 'down' : 'up'); }, { passive: true });

  function focusScope(scope, fallbackSel) {
    var e = lastFocusByScope[scope];
    if (e && visible(e) && document.body.contains(e)) { focus(e); return true; }
    var list = U.$$('.focusable[data-nav="' + scope + '"]', container).filter(visible);
    if (list.length) { focus(list[0]); return true; }
    if (fallbackSel) { var f = U.$(fallbackSel); if (f) { focus(f); return true; } }
    return false;
  }
  function focusFirst(sel) { var list = U.$$(sel || '.focusable', container).filter(visible); if (list.length) focus(list[0]); }

  return { KEYS: KEYS, press: press, byPointer: byPointer, focusByPointer: focusByPointer, setPointerMode: setPointerMode, focus: focus, blur: blur, current: getCurrent, move: move, onFocus: onFocus, onKey: onKey, setContainer: setContainer, focusScope: focusScope, focusFirst: focusFirst, lock: lock, visible: visible };
})();
