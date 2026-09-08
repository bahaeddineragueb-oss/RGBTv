/* RGBTv — virtual list / grid renderer.
 * Renders only the visible window (+ overscan) so 20 000-item lists stay fluid on TV CPUs.
 * Focus and remote navigation work on a logical index, not on DOM elements. */
function VList(opt) {
  this.container = opt.container;            // element with fixed height (overflow hidden)
  this.itemH = opt.itemH;                    // row height in px
  this.cols = opt.cols || 1;                 // columns (grid)
  this.itemW = opt.itemW || 0; this.gap = opt.gap || 0;
  this.render = opt.render;                  // function(item, index) -> element (must have class focusable)
  this.onFocus = opt.onFocus || null;        // function(item, index)
  this.onSelect = opt.onSelect || null;      // function(item, index)
  this.nav = opt.nav || 'vlist';
  this.overscan = opt.overscan || 2;
  this.items = []; this.index = 0; this.first = -1; this.last = -1; this.selectedId = null;
  this.inner = document.createElement('div'); this.inner.className = 'vl-inner'; this.inner.style.position = 'relative';
  this.container.innerHTML = ''; this.container.appendChild(this.inner);
  this.container._vlist = this;
}
VList.prototype = {
  setItems: function (items, keepIndex) {
    this.items = items || []; if (!keepIndex) this.index = 0; else this.index = Math.min(this.index, Math.max(0, this.items.length - 1));
    this.first = -1; this.last = -1; this.inner.innerHTML = '';
    this.inner.style.height = (Math.ceil(this.items.length / this.cols) * this.itemH) + 'px';
    this.update(true);
  },
  viewRows: function () { return Math.max(1, Math.floor(this.container.clientHeight / this.itemH)); },
  update: function (force) {
    var rows = this.viewRows(), total = Math.ceil(this.items.length / this.cols);
    var focusRow = Math.floor(this.index / this.cols);
    var startRow = Math.max(0, Math.min(focusRow - Math.floor(rows / 2), total - rows)); if (startRow < 0) startRow = 0;
    this.px = startRow * this.itemH; this.inner.style.transition = '';
    this.renderWindow(startRow, force);
  },
  /* ---- pixel scrolling (touch): keeps the rendered window around an arbitrary offset, focus untouched ---- */
  maxScroll: function () { return Math.max(0, Math.ceil(this.items.length / this.cols) * this.itemH - this.container.clientHeight); },
  scrollPos: function () { return this.px || 0; },
  scrollToPx: function (px) {
    px = Math.max(0, Math.min(this.maxScroll(), px)); this.px = px;
    this.inner.style.transition = 'none';
    this.renderWindow(Math.floor(px / this.itemH), false, px);
  },
  renderWindow: function (startRow, force, px) {
    var rows = this.viewRows() + 1;
    var f = Math.max(0, startRow - this.overscan) * this.cols, l = Math.min(this.items.length, (startRow + rows + this.overscan) * this.cols);
    this.inner.style.webkitTransform = this.inner.style.transform = 'translateY(-' + (px != null ? px : startRow * this.itemH) + 'px)';
    if (!force && f === this.first && l === this.last) return;
    // rebuild window (cheap: <= ~40 nodes)
    var frag = document.createDocumentFragment(), self = this;
    for (var i = f; i < l; i++) {
      var el = this.render(this.items[i], i);
      el.style.position = 'absolute'; el.style.top = (Math.floor(i / this.cols) * this.itemH) + 'px';
      if (this.cols > 1) el.style.left = ((i % this.cols) * (this.itemW + this.gap)) + 'px'; else { el.style.left = 0; el.style.right = 0; }
      el.setAttribute('data-nav', this.nav); el.setAttribute('data-i', i); el._item = this.items[i]; el._vlist = this;
      if (this.selectedId != null && String(this.items[i].id) === String(this.selectedId)) el.classList.add('selected');
      if (i === this.index && Nav.current() && Nav.current()._vlist === this) { el.classList.add('focus'); Nav.focus(el, true); }
      (function (idx) { el.onclick = function () { self.index = idx; if (self.onSelect) self.onSelect(self.items[idx], idx); }; })(i);
      frag.appendChild(el);
    }
    this.inner.innerHTML = ''; this.inner.appendChild(frag); this.first = f; this.last = l;
  },
  elementAt: function (i) { return this.inner.querySelector('[data-i="' + i + '"]'); },
  focusIndex: function (i, silent) {
    if (!this.items.length) return false;
    this.index = Math.max(0, Math.min(this.items.length - 1, i)); this.update();
    var el = this.elementAt(this.index); if (el) Nav.focus(el, true);
    if (!silent && this.onFocus) this.onFocus(this.items[this.index], this.index);
    return true;
  },
  focus: function () { return this.focusIndex(this.index); },
  /* returns true if handled */
  move: function (dir) {
    var n = this.items.length; if (!n) return false; var i = this.index, c = this.cols;
    if (dir === 'up') { if (i - c < 0) return false; i -= c; }
    else if (dir === 'down') { if (i + c >= n) { if (Math.floor(i / c) === Math.floor((n - 1) / c)) return false; i = n - 1; } else i += c; }
    else if (dir === 'left') { if (c === 1 || i % c === 0) return false; i -= 1; }
    else if (dir === 'right') { if (c === 1 || i % c === c - 1 || i + 1 >= n) return false; i += 1; }
    else if (dir === 'pgup') i = Math.max(0, i - this.viewRows() * c);
    else if (dir === 'pgdown') i = Math.min(n - 1, i + this.viewRows() * c);
    this.focusIndex(i); return true;
  },
  setSelected: function (id) { this.selectedId = id; var self = this; U.$$('.selected', this.inner).forEach(function (e) { e.classList.remove('selected'); }); U.$$('[data-i]', this.inner).forEach(function (e) { if (String(e._item.id) === String(id)) e.classList.add('selected'); }); },
  refreshItem: function (i) { var el = this.elementAt(i); if (!el) return; var n = this.render(this.items[i], i); el.innerHTML = n.innerHTML; el.className = n.className + (el.classList.contains('focus') ? ' focus' : '') + (el.classList.contains('selected') ? ' selected' : ''); if (window.UI && UI.lazyBg) U.$$('[data-src]', el).forEach(function (x) { UI.lazyBg(x); }); }
};

/* Global key hook: arrows inside a VList are handled by the list itself */
Nav.onKey(function (name) {
  if (App.isScreen && App.isScreen('player')) return false;
  var c = Nav.current(); if (!c || !c._vlist || !document.body.contains(c)) return false;
  var vl = c._vlist, map = { UP: 'up', DOWN: 'down', LEFT: 'left', RIGHT: 'right', CH_UP: 'pgup', CH_DOWN: 'pgdown', REW: 'pgup', FF: 'pgdown' };
  if (!map[name]) return false;
  if (vl.move(map[name])) return true;
  return false; // edge: let spatial nav leave the list
});
