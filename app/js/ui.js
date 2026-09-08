/* RGBTv — UI rendering helpers */
var UI = (function () {
  var toastTimer;
  function toast(msg, ms, icon) {
    var t = U.$('#toast'), bar = U.$('#toast-bar'); ms = ms || 2500;
    U.$('#toast-ico').textContent = icon || ''; U.$('#toast-msg').textContent = msg;
    t.classList.remove('show'); if (bar) { bar.style.transition = 'none'; bar.style.transform = 'scaleX(1)'; }
    void t.offsetWidth; // restart transitions
    t.classList.add('show'); if (bar) { bar.style.transition = 'transform ' + ms + 'ms linear'; bar.style.transform = 'scaleX(0)'; }
    clearTimeout(toastTimer); toastTimer = setTimeout(function () { t.classList.remove('show'); }, ms);
  }

  function modal(title, text, buttons) {
    var m = U.$('#modal'); U.$('#modal-title').textContent = title; U.$('#modal-text').innerHTML = text;
    var acts = U.$('#modal-actions'); acts.innerHTML = '';
    var prevFocus = Nav.current();
    return new Promise(function (resolve) {
      function close(v) { m.classList.remove('show'); Nav.setContainer(App.activeScreen()); if (prevFocus) Nav.focus(prevFocus); resolve(v); }
      (buttons || [{ label: 'OK', value: true }]).forEach(function (b) {
        var btn = U.el('button', 'btn focusable' + (b.danger ? ' danger' : b.ghost ? ' ghost' : ''), U.esc(b.label)); btn.setAttribute('data-nav', 'modal');
        btn.onclick = function () { close(b.value); }; acts.appendChild(btn);
      });
      m._close = close;
      m.classList.add('show'); Nav.setContainer(m); Nav.focusFirst('.focusable[data-nav="modal"]');
    });
  }
  function modalOpen() { return U.$('#modal').classList.contains('show'); }
  function closeModal() { var m = U.$('#modal'); if (m._close) m._close(null); }

  function avatarColor(s) { var cols = ['#ef4444', '#22c55e', '#3b82f6', '#f59e0b', '#a855f7', '#14b8a6', '#ec4899']; var h = 0; for (var i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) & 0xffff; return cols[h % cols.length]; }

  /* ---- profiles (Netflix style) ---- */
  function renderAccounts(list, manage) {
    var wrap = U.$('#acc-list'); wrap.innerHTML = ''; wrap.classList.toggle('manage', !!manage);
    U.$('#profiles-title').textContent = manage ? I18n.t('acc.manage') : (list.length ? I18n.t('acc.who') : I18n.t('acc.first'));
    list.forEach(function (a) {
      var c = U.el('div', 'profile focusable'); c.setAttribute('data-nav', 'acc'); c.setAttribute('data-id', a.id);
      var hist = Store.history(a.id), lastW = hist[0], favN = Store.favorites(a.id).length;
      var lastHtml = lastW ? '<div class="last" title="' + U.esc(lastW.name || '') + '">' + ((lastW.poster || lastW.logo) ? '<i style="background-image:url(\'' + U.esc(lastW.poster || lastW.logo) + '\')"></i>' : '') + '<span>' + U.esc(I18n.t('last.watched', { t: lastW.name || '' })) + '</span></div>' : '';
      c.innerHTML = '<div class="pav"><img src="' + Avatars.url(a.avatar) + '" alt="">' + (a.pin ? '<span class="lock">🔒</span>' : '') + (a.kids ? '<span class="kids">KIDS</span>' : '') + (favN ? '<span class="badge-n">★ ' + favN + '</span>' : '') + '<div class="edit">✎</div></div><div class="name">' + U.esc(a.name) + '</div><div class="type">' + a.type + '</div>' + lastHtml;
      c.onclick = function () { if (manage) App.accountMenu(a.id); else App.openAccount(a.id); };
      wrap.appendChild(c);
    });
    var add = U.el('div', 'profile add focusable'); add.setAttribute('data-nav', 'acc');
    add.innerHTML = '<div class="pav"><svg viewBox="0 0 24 24"><path d="M11 5h2v6h6v2h-6v6h-2v-6H5v-2h6z"/></svg></div><div class="name">' + U.esc(I18n.t('acc.add')) + '</div><div class="type">&nbsp;</div>';
    add.onclick = function () { App.showAddForm(null); }; wrap.appendChild(add);
  }
  function renderAvatarPicker(selected, onPick) {
    var el = U.$('#avatar-picker'); el.innerHTML = '';
    Avatars.list().forEach(function (id) {
      var d = U.el('div', 'avatar-opt focusable' + (id === selected ? ' selected' : '')); d.setAttribute('data-nav', 'avatars'); d.setAttribute('data-id', id);
      d.innerHTML = '<img src="' + Avatars.url(id) + '" alt="">';
      d.onclick = function () { U.$$('.avatar-opt', el).forEach(function (x) { x.classList.remove('selected'); }); d.classList.add('selected'); onPick(id); };
      el.appendChild(d);
    });
  }
  /* skeleton loaders */
  function skeletonRows(container, n) {
    container.innerHTML = '';
    for (var i = 0; i < (n || 2); i++) { var r = U.el('div', 'row'); r.innerHTML = '<div class="skeleton sk-line"></div>'; var items = U.el('div', 'sk-row'); for (var j = 0; j < 8; j++) items.appendChild(U.el('div', 'skeleton sk-card')); r.appendChild(items); container.appendChild(r); }
  }
  function skeletonList(container, n) { container.innerHTML = ''; container.classList.add('sk-list'); for (var i = 0; i < (n || 8); i++) container.appendChild(U.el('div', 'skeleton sk-item')); }
  function skeletonGrid(container, n) { container.innerHTML = ''; for (var i = 0; i < (n || 14); i++) { var d = U.el('div', 'skeleton'); d.style.width = '200px'; d.style.height = '340px'; container.appendChild(d); } }

  /* ---- cards / rows ---- */
  function toPlayable(it) {
    var p = {}; for (var k in it) p[k] = it[k];
    p.title = it.name; p.subtitle = it.type === 'live' ? (it.catName || '') : (it.year ? String(it.year) : '');
    return p;
  }
  function card(it, opts) {
    opts = opts || {};
    var wide = it.type === 'live';
    var c = U.el('div', 'card focusable' + (wide ? ' wide' : '') + (opts.mini ? ' mini' : '')); c.setAttribute('data-nav', opts.nav || 'row');
    var img = wide ? it.logo : (it.poster || it.logo);
    var prog = '', badge = '', fav = '';
    if (it.type === 'movie' || it.type === 'episode') { var p = Store.getPos(App.account.id, it.type + ':' + it.id); if (p && p.dur) prog = '<div class="progress"><i style="width:' + Math.round(p.pos / p.dur * 100) + '%"></i></div>'; }
    if (it.rating && Number(it.rating) > 0) badge = '<div class="badge">★ ' + Number(it.rating).toFixed(1) + '</div>';
    if (Store.isFav(App.account.id, it.type, it.id)) fav = '<div class="fav">★</div>';
    if (it.type === 'live' && Store.isLocked(App.account.id, it.id)) fav += '<div class="lock">🔒</div>';
    c.innerHTML = '<div class="thumb" data-src="' + U.esc(img || '') + '">' + badge + fav + prog + '</div><div class="title">' + U.esc(it.name) + '</div>';
    c._item = it; lazyBg(c.querySelector('.thumb'));
    if (!opts.noClick) c.onclick = function () { App.openItem(it, opts.list); };
    return c;
  }
  function row(title, items, opts) {
    opts = opts || {};
    var r = U.el('div', 'row'); r.innerHTML = '<div class="row-title">' + U.esc(title) + ' <span class="count">' + items.length + '</span></div>';
    var inner = U.el('div', 'row-items'); items.slice(0, opts.max || 40).forEach(function (it) { inner.appendChild(card(it, { list: items, nav: opts.nav || 'row' })); });
    r.appendChild(inner); r._inner = inner; return r;
  }
  /* Horizontal scroll of a row so focused card stays in view; vertical scroll of the rows container */
  function scrollRowsTo(card) {
    var inner = card.parentNode, rowEl = inner.parentNode, rows = rowEl.parentNode;
    var visibleW = 1920 - 120, w = card.offsetWidth + 24, rtl = I18n.isRTL();
    var total = 0; for (var j = 0; j < inner.children.length; j++) total += inner.children[j].offsetWidth + 24;
    var pos = rtl ? (inner.offsetWidth - card.offsetLeft - card.offsetWidth) : card.offsetLeft; // distance from the row start edge
    var off = Math.max(0, pos - (visibleW - w) / 2); off = Math.min(off, Math.max(0, total - visibleW));
    inner.style.transform = 'translateX(' + (rtl ? off : -off) + 'px)';
    var rIdx = Array.prototype.indexOf.call(rows.children, rowEl), top = 0; for (var i = 0; i < rIdx; i++) top += rows.children[i].offsetHeight + 34;
    // Home: while browsing rows below the first one, collapse the hero so rows never slide over it
    var sec = rows.parentNode; if (sec && sec.id === 'sec-home') sec.classList.toggle('rows-mode', rIdx > 0);
    rows.style.transform = 'translateY(-' + top + 'px)';
  }
  function scrollList(listEl, itemEl, itemH) {
    var idx = Array.prototype.indexOf.call(listEl.children, itemEl), viewH = listEl.offsetHeight;
    var off = Math.max(0, idx * itemH - viewH / 2 + itemH / 2); off = Math.min(off, Math.max(0, listEl.children.length * itemH - viewH));
    listEl.style.transform = 'translateY(-' + off + 'px)';
  }
  function scrollGrid(grid, card) { if (grid._vlist) return;
    var perRow = Math.floor((grid.offsetWidth - 20) / 222) || 1, idx = Array.prototype.indexOf.call(grid.children, card), rowN = Math.floor(idx / perRow), rowH = card.offsetHeight + 22;
    var viewH = grid.parentNode.offsetHeight - 70, off = Math.max(0, rowN * rowH - viewH / 2 + rowH / 2);
    var totalRows = Math.ceil(grid.children.length / perRow); off = Math.min(off, Math.max(0, totalRows * rowH - viewH));
    grid.style.transform = 'translateY(-' + off + 'px)';
  }

  /* ---- category & channel lists (virtualized) ---- */
  var CAT_ICONS = {
    all: '<svg viewBox="0 0 24 24"><path d="M4 5h16v3H4zm0 5.5h16v3H4zM4 16h16v3H4z"/></svg>',
    sport: '<svg viewBox="0 0 24 24"><path d="M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm0 2c1.6 0 3.1.5 4.3 1.3L12 8.6 7.7 5.3A8 8 0 0 1 12 4zM4 12c0-1.7.5-3.2 1.4-4.5L9 10l-1.6 5.3-3-.6A8 8 0 0 1 4 12zm8 8a8 8 0 0 1-5.4-2.1l2.9-1.5H14.5l2.9 1.5A8 8 0 0 1 12 20zm4.6-4.7L15 10l3.6-2.5A8 8 0 0 1 20 12c0 1 0 1.7-.4 2.7l-3 .6z"/></svg>',
    news: '<svg viewBox="0 0 24 24"><path d="M4 4h13v14a2 2 0 0 0 2 2H6a2 2 0 0 1-2-2V4zm2 3v4h5V7H6zm0 6v2h9v-2H6zm7-6v4h2V7h-2zm7 3v8a2 2 0 0 1-2 2v-8h2z"/></svg>',
    movie: '<svg viewBox="0 0 24 24"><path d="M4 4h16v16H4zm2 2v2h2V6zm0 4v2h2v-2zm0 4v2h2v-2zm10-8v2h2V6zm0 4v2h2v-2zm0 4v2h2v-2zM10 6v12h4V6z"/></svg>',
    kids: '<svg viewBox="0 0 24 24"><path d="M12 3a5 5 0 0 1 5 5c0 1.5-.7 2.9-1.8 3.8A7 7 0 0 1 19 18v3H5v-3a7 7 0 0 1 3.8-6.2A5 5 0 0 1 12 3zm-2 5a1 1 0 1 0 0 .01zm4 0a1 1 0 1 0 0 .01z"/></svg>',
    music: '<svg viewBox="0 0 24 24"><path d="M9 3v12.3A3.5 3.5 0 1 0 11 18V7h7v6.3A3.5 3.5 0 1 0 20 16V3z"/></svg>',
    doc: '<svg viewBox="0 0 24 24"><path d="M12 3 2 8l10 5 8-4v6h2V8zM6 12.5V17c0 1.7 2.7 3 6 3s6-1.3 6-3v-4.5l-6 3z"/></svg>',
    religion: '<svg viewBox="0 0 24 24"><path d="M12 2a9 9 0 1 0 6.4 15.3A7 7 0 0 1 9.2 4.4 9 9 0 0 0 12 2zm5 2 1 2.3 2.5.3-1.8 1.7.5 2.5L17 9.6l-2.2 1.2.5-2.5L13.5 6.6l2.5-.3z"/></svg>',
    tv: '<svg viewBox="0 0 24 24"><path d="M3 5h18v12H3zm5 14h8v2H8z"/></svg>',
    adult: '<svg viewBox="0 0 24 24"><path d="M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm0 4a2 2 0 1 1 0 4 2 2 0 0 1 0-4zm-1 6h2v6h-2z"/></svg>',
    world: '<svg viewBox="0 0 24 24"><path d="M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm7.9 9h-3a15 15 0 0 0-1.2-5.2A8 8 0 0 1 19.9 11zM12 4c1 1.3 1.9 3.6 2.1 7H9.9C10.1 7.6 11 5.3 12 4zM4.1 13h3c.1 1.9.5 3.7 1.2 5.2A8 8 0 0 1 4.1 13zm3-2h-3a8 8 0 0 1 4.2-5.2C7.6 7.3 7.2 9.1 7.1 11zM12 20c-1-1.3-1.9-3.6-2.1-7h4.2c-.2 3.4-1.1 5.7-2.1 7zm3.7-1.8c.7-1.5 1.1-3.3 1.2-5.2h3a8 8 0 0 1-4.2 5.2z"/></svg>',
    star: '<svg viewBox="0 0 24 24"><path d="m12 2 3 6.5 7 .8-5.2 4.8 1.4 7L12 17.5 5.8 21l1.4-7L2 9.3l7-.8z"/></svg>'
  };
  function catIcon(name, isAll) {
    if (isAll) return CAT_ICONS.all; var n = String(name || '').toLowerCase();
    if (/sport|foot|soccer|bein|nba|f1|ufc|wwe|golf|tennis|رياض|كرة/.test(n)) return CAT_ICONS.sport;
    if (/news|أخبار|اخبار|info/.test(n)) return CAT_ICONS.news;
    if (/kid|cartoon|child|junior|أطفال|اطفال|anim/.test(n)) return CAT_ICONS.kids;
    if (/music|radio|موسيق|أغان/.test(n)) return CAT_ICONS.music;
    if (/doc|nat ?geo|discovery|history|وثائق/.test(n)) return CAT_ICONS.doc;
    if (/relig|islam|quran|coran|christ|دين|قرآن/.test(n)) return CAT_ICONS.religion;
    if (/adult|xxx|18\+|porn/.test(n)) return CAT_ICONS.adult;
    if (/movie|cinema|film|أفلام|افلام|vod|box ?office|series|مسلسل/.test(n)) return CAT_ICONS.movie;
    if (/top|best|premium|4k|uhd|vip/.test(n)) return CAT_ICONS.star;
    if (/^(fr|uk|us|usa|de|es|it|ar|arab|tr|pt|nl|be|ch|ca|ma|dz|tn|eg|sa|ae|lb|qa|kw)\b|france|english|german|spain|ital|arab|turk|maroc|alger|tunis|egypt|saudi|emirat|world|international|عرب|فرنس|مغرب|جزائر|تونس|مصر/.test(n)) return CAT_ICONS.world;
    return CAT_ICONS.tv;
  }
  function catEl(c) {
    var d = U.el('div', 'cat-item focusable');
    d.innerHTML = '<span class="cn"><span class="ci">' + catIcon(c.name, c.id == null) + '</span><span>' + U.esc(c.name) + '</span></span>' + (c.count != null ? '<span class="n">' + c.count + '</span>' : '');
    return d;
  }
  function renderCats(el, cats, selectedId, nav, onSelect) {
    el.classList.remove('sk-list');
    var vl = el._vlist && el._vlist.nav === nav ? el._vlist : new VList({ container: el, itemH: 60, nav: nav, render: catEl, onSelect: function (c) { vl.setSelected(c.id); onSelect(c); } });
    vl.selectedId = selectedId; vl.setItems(cats); return vl;
  }
  function chEl(c, i) {
    var locked = Store.isLocked(App.account.id, c.id);
    var d = U.el('div', 'ch-item focusable' + (locked ? ' locked' : ''));
    d.innerHTML = '<span class="num">' + (c.num || i + 1) + '</span><div class="logo-img" data-src="' + U.esc(c.logo || '') + '"></div><div class="info"><div class="name">' + U.esc(c.name) + '</div><div class="now"></div></div>' + (locked ? '<span class="lock">🔒</span>' : '') + (Store.isFav(App.account.id, 'live', c.id) ? '<span class="fav">★</span>' : '');
    lazyBg(d.querySelector('.logo-img'));
    return d;
  }
  function chTile(c, i) {
    var locked = Store.isLocked(App.account.id, c.id);
    var d = U.el('div', 'ch-tile focusable' + (locked ? ' locked' : '')); var tw = U.$('#live-channels') && U.$('#live-channels').getAttribute('data-tw'); if (tw) d.style.width = tw + 'px';
    d.innerHTML = '<span class="num">' + (c.num || i + 1) + '</span><div class="logo-img" data-src="' + U.esc(c.logo || '') + '"></div><div class="name">' + U.esc(c.name) + '</div>' + (locked ? '<span class="lock">🔒</span>' : '') + (Store.isFav(App.account.id, 'live', c.id) ? '<span class="fav">★</span>' : '');
    lazyBg(d.querySelector('.logo-img'));
    return d;
  }
  function renderChannels(el, list, selectedId, onFocus, onPlay) {
    el.classList.remove('sk-list');
    var grid = !!Store.settings().liveGrid; el.classList.toggle('grid-mode', grid);
    if (el._vlist && !!el._vlist._grid !== grid) el._vlist = null;
    var cols = grid ? Math.max(1, Math.floor((el.clientWidth - 28) / 170)) : 1, tw = grid ? Math.floor((el.clientWidth - 28 - (cols - 1) * 12) / cols) : 0;
    if (grid) { el.setAttribute('data-tw', tw); }
    var vl = el._vlist || new VList({ container: el, itemH: grid ? 166 : 88, itemW: tw, gap: 12, cols: cols, nav: 'chl', render: grid ? chTile : chEl, onFocus: function (c) { if (onFocus) onFocus(c); }, onSelect: function (c, i) { onPlay(c, i); } });
    vl._grid = grid;
    vl.onFocus = function (c) { if (onFocus) onFocus(c); }; vl.onSelect = function (c, i) { onPlay(c, i); };
    vl.selectedId = selectedId; vl.setItems(list); return vl;
  }
  function renderGrid(el, list, nav, onOpen) {
    var large = document.body.classList.contains('large'), iw = large ? 230 : 200, ih = large ? 418 : 372;
    var cols = Math.max(1, Math.floor((el.clientWidth - 20) / (iw + 22)));
    if (el._vlist && el._vlist.itemW !== iw) el._vlist = null;
    var vl = el._vlist || new VList({ container: el, itemH: ih, itemW: iw, gap: 22, cols: cols, nav: nav, render: function (it) { var c = card(it, { list: list, nav: nav, noClick: true }); return c; }, onSelect: function (it) { onOpen(it, list); } });
    vl.cols = cols; vl.render = function (it) { return card(it, { list: list, nav: nav, noClick: true }); }; vl.onSelect = function (it) { onOpen(it, list); };
    vl.setItems(list); return vl;
  }
  /* lazy background images: only load when the element is created (visible window) with a tiny delay to skip fast scrolling */
  var lazyQueue = [], lazyTimer = null;
  function lazyBg(el) {
    var src = el.getAttribute('data-src');
    // v1.5: always reset first so a reused node never shows the previous item's artwork
    el.style.backgroundImage = ''; el.classList.remove('loaded');
    if (!src) return;
    lazyQueue.push(el); if (lazyTimer) return;
    lazyTimer = setTimeout(function () {
      lazyTimer = null; var q = lazyQueue; lazyQueue = [];
      q.forEach(function (e) {
        if (!document.body.contains(e)) return;
        var cur = e.getAttribute('data-src'); if (!cur) { e.style.backgroundImage = ''; return; }
        e.style.backgroundImage = 'url("' + cur.replace(/"/g, '%22') + '")'; e.classList.add('loaded');
      });
    }, 120);
  }
  function renderEpg(list) {
    var now = Date.now() / 1000, cur = null, nxt = null;
    list.forEach(function (e) { if (e.start <= now && e.end > now) cur = e; else if (e.start > now && !nxt) nxt = e; });
    U.$('#epg-now-title').textContent = cur ? (U.hm(cur.start) + ' – ' + U.hm(cur.end) + '  ' + cur.title) : (list.length ? '—' : I18n.t('noEpg'));
    U.$('#epg-next-title').textContent = nxt ? (U.hm(nxt.start) + '  ' + nxt.title) : '—';
    U.$('#epg-progress-bar').style.width = cur ? Math.round((now - cur.start) / (cur.end - cur.start) * 100) + '%' : '0%';
    var el = U.$('#epg-list'); el.innerHTML = '';
    list.slice(0, 12).forEach(function (e) { el.appendChild(U.el('div', 'epg-row' + (e === cur ? ' live' : ''), '<span class="t">' + U.hm(e.start) + ' – ' + U.hm(e.end) + '</span><span>' + U.esc(e.title) + '</span>')); });
  }

  /* ---- details (v1.5 cinematic) ---- */
  function initials(n) { return String(n || '?').split(' ').slice(0, 2).map(function (w) { return w.charAt(0).toUpperCase(); }).join(''); }
  function renderDetails(info, base) {
    var poster = info.poster || base.poster || '', bg = info.backdrop || base.backdrop || poster;
    U.$('#details-bg').style.backgroundImage = bg ? 'url("' + bg + '")' : 'none';
    var img = U.$('#details-poster'); if (img.getAttribute('src') !== (poster || 'img/largeIcon.png')) { img.src = poster || 'img/largeIcon.png'; img.onerror = function () { img.src = 'img/largeIcon.png'; }; }
    var type = info.type || base.type;
    U.$('#details-type').textContent = I18n.t(type === 'series' ? 'det.series' : 'det.movie');
    U.$('#details-title').textContent = TMDB.enabled() && info.tmdbId ? TMDB.cleanTitle(info.name || base.name) : (info.name || base.name);
    var kick = []; if (info.genre) kick.push(String(info.genre).split(',')[0].trim()); if (info.year) kick.push(String(info.year).substr(0, 4)); if (info.seasons && info.seasons.length) kick.push(I18n.t('det.seasons', { n: info.seasons.length }));
    U.$('#details-kicker').textContent = kick.join('  ·  ');
    var chips = [];
    if (info.rating && Number(info.rating) > 0) chips.push('<span class="imdb">★ ' + Number(info.rating).toFixed(1) + '</span>');
    if (info.year) chips.push('<span>' + U.esc(String(info.year).substr(0, 4)) + '</span>');
    if (info.duration) chips.push('<span>' + U.esc(info.duration) + '</span>');
    if (info.genre) String(info.genre).split(/[,\/|]/).slice(0, 3).forEach(function (g) { g = g.trim(); if (g) chips.push('<span class="genre">' + U.esc(g) + '</span>'); });
    var nm = String(info.name || base.name || '');
    if (/\b(4k|uhd|2160p)\b/i.test(nm)) chips.push('<span class="q">4K UHD</span>'); else if (/\b(1080p|fhd)\b/i.test(nm)) chips.push('<span class="q">FHD</span>'); else if (/\b(720p|hd)\b/i.test(nm)) chips.push('<span class="q">HD</span>');
    if (/\b(hdr|dolby ?vision|dv)\b/i.test(nm)) chips.push('<span class="hdr">HDR</span>');
    if (/\b(multi|dual)\b/i.test(nm)) chips.push('<span>MULTI</span>');
    if (/\b(vostfr|sub|subbed|مترجم)\b/i.test(nm)) chips.push('<span>SUB</span>');
    if (info.director) chips.push('<span>🎬 ' + U.esc(info.director) + '</span>');
    U.$('#details-meta').innerHTML = chips.join('');
    U.$('#details-tagline').textContent = info.tagline || '';
    U.$('#details-plot').textContent = info.plot || '';
    // cast avatars (TMDB) or names from the playlist
    var castEl = U.$('#details-cast'); castEl.innerHTML = '';
    var cl = info.castList || (info.cast ? String(info.cast).split(',').slice(0, 8).map(function (n) { return { name: n.trim(), role: '', img: '' }; }) : []);
    cl.filter(function (c) { return c.name; }).slice(0, 8).forEach(function (c) {
      var d = U.el('div', 'cast'); d.innerHTML = '<div class="cav" ' + (c.img ? 'style="background-image:url(\'' + U.esc(c.img) + '\')"' : '') + '>' + (c.img ? '' : U.esc(initials(c.name))) + '</div><div class="cn">' + U.esc(c.name) + '</div><div class="cr">' + U.esc(c.role || '') + '</div>'; castEl.appendChild(d);
    });
    U.$('#details-trailer').style.display = info.trailer ? '' : 'none';
    var favOn = Store.isFav(App.account.id, type, base.id);
    U.$('[data-action="details-fav"]').textContent = favOn ? I18n.t('favorited') : I18n.t('favorite');
  }
  function renderSimilar(items, onOpen) {
    var el = U.$('#details-similar'); el.innerHTML = ''; if (!items || !items.length) return;
    var r = U.el('div', 'row'); r.innerHTML = '<div class="row-title">' + U.esc(I18n.t('det.similar')) + '</div>';
    var inner = U.el('div', 'row-items'); items.slice(0, 20).forEach(function (it) { var c = card(it, { nav: 'similar', noClick: true, list: items }); c.onclick = function () { onOpen(it, items); }; inner.appendChild(c); });
    r.appendChild(inner); r._inner = inner; el.appendChild(r);
  }
  function renderSeasons(seasons, activeNum, onPick) {
    var el = U.$('#details-seasons'); el.innerHTML = '';
    seasons.forEach(function (s) {
      var b = U.el('button', 'season-btn focusable' + (s.num === activeNum ? ' active' : ''), U.esc(s.name)); b.setAttribute('data-nav', 'seasons');
      b.onclick = function () { U.$$('.season-btn', el).forEach(function (x) { x.classList.remove('active'); }); b.classList.add('active'); onPick(s); }; el.appendChild(b);
    });
  }
  function renderEpisodes(eps, onPlay) {
    var el = U.$('#details-episodes'); el.innerHTML = ''; var inner = U.el('div', 'ep-inner');
    eps.forEach(function (e, i) {
      var d = U.el('div', 'ep-item focusable'); d.setAttribute('data-nav', 'episodes'); d.setAttribute('data-i', i);
      var p = Store.getPos(App.account.id, 'episode:' + e.id), prog = p && p.dur ? '<div class="progress"><i style="width:' + Math.round(p.pos / p.dur * 100) + '%"></i></div>' : '';
      d.innerHTML = '<div class="ep-thumb" style="' + (e.thumb ? 'background-image:url(\'' + U.esc(e.thumb) + '\')' : '') + '"><span class="epn">E' + U.pad(e.episode) + '</span>' + prog + '</div><div class="ep-text"><div class="ep-title">' + U.esc(e.name) + '</div><div class="ep-sub">' + (e.plot ? U.esc(e.plot.substr(0, 140)) : '') + '</div></div><div class="ep-dur">' + U.esc(e.duration || '') + '</div>';
      d.onclick = function () { onPlay(e, i); }; inner.appendChild(d);
    });
    el.appendChild(inner);
  }
  function scrollEpisodes(item) {
    var inner = item.parentNode, idx = Number(item.getAttribute('data-i')), h = item.offsetHeight + 10, viewH = inner.parentNode.offsetHeight;
    var off = Math.max(0, idx * h - viewH / 2 + h / 2); off = Math.min(off, Math.max(0, inner.children.length * h - viewH)); inner.style.transform = 'translateY(-' + off + 'px)';
  }

  return { toast: toast, modal: modal, modalOpen: modalOpen, closeModal: closeModal, avatarColor: avatarColor, renderAccounts: renderAccounts, renderAvatarPicker: renderAvatarPicker, skeletonRows: skeletonRows, skeletonList: skeletonList, skeletonGrid: skeletonGrid, card: card, row: row, toPlayable: toPlayable, scrollRowsTo: scrollRowsTo, scrollList: scrollList, scrollGrid: scrollGrid, renderCats: renderCats, renderChannels: renderChannels, renderGrid: renderGrid, renderEpg: renderEpg, renderDetails: renderDetails, renderSimilar: renderSimilar, lazyBg: lazyBg, catIcon: catIcon, renderSeasons: renderSeasons, renderEpisodes: renderEpisodes, scrollEpisodes: scrollEpisodes };
})();
