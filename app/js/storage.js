/* RGBTv — persistent storage: accounts, settings, favorites, watch history, cache */
var Store = (function () {
  var PREFIX = 'rgbtv:';
  function get(k, def) { try { var v = localStorage.getItem(PREFIX + k); return v == null ? def : JSON.parse(v); } catch (e) { return def; } }
  function set(k, v) { try { var str = JSON.stringify(v); if (str.length > 1500000) return false; localStorage.setItem(PREFIX + k, str); return true; } catch (e) { return false; /* quota */ } }
  function del(k) { try { localStorage.removeItem(PREFIX + k); } catch (e) { } }

  /* ---- device identity (used for Stalker) ---- */
  function device() {
    var d = get('device');
    if (!d) {
      d = { mac: U.randomMac(), sn: U.sha1(U.uuid()).substr(0, 13), deviceId: U.sha1(U.uuid()), deviceId2: U.sha1(U.uuid()) };
      set('device', d);
    }
    return d;
  }

  /* ---- accounts ---- */
  function accounts() { return get('accounts', []); }
  function saveAccounts(list) { set('accounts', list); }
  function addAccount(acc) { var l = accounts(); acc.id = acc.id || U.uuid(); acc.createdAt = Date.now(); l.push(acc); saveAccounts(l); return acc; }
  function updateAccount(acc) { var l = accounts().map(function (a) { return a.id === acc.id ? acc : a; }); saveAccounts(l); }
  function removeAccount(id) {
    saveAccounts(accounts().filter(function (a) { return a.id !== id; }));
    Object.keys(localStorage).forEach(function (k) { if (k.indexOf(PREFIX + 'acc:' + id + ':') === 0) localStorage.removeItem(k); });
    if (get('lastAccount') === id) del('lastAccount');
  }
  function getAccount(id) { return accounts().filter(function (a) { return a.id === id; })[0] || null; }
  function lastAccount() { return get('lastAccount', null); }
  function setLastAccount(id) { set('lastAccount', id); }

  /* ---- settings ---- */
  var DEFAULTS = { liveFormat: 'ts', engine: 'auto', parental: true, autostart: false, theme: 'aurora', tmdbKey: '', preview: 'auto', lang: 'en', refreshHours: 6, layout: 'classic', focusStyle: 'glow', largeUi: false, liveGrid: false, ambient: true, weather: true, autoNext: true, accent: 'auto', pointer: 'click', corners: 'round', glow: true, wxMode: 'auto', wxUnit: 'c', adhan: true, adhanMethod: 'algeria' };
  var THEME_MIGRATE = { dark: 'aurora' }, LAYOUT_MIGRATE = { viu: 'spotlight', ibo: 'trio' };
  /* webOS plays raw MPEG-TS natively and starts it in ~1 s; HLS (m3u8) from Xtream panels needs the server to cut
     2-3 segments first (10-30 s) and stalls on every late playlist refresh. Chromium shells (Android/Windows/Xbox)
     cannot play TS in <video>, so they keep HLS. */
  var IS_WEBOS = typeof window.PalmServiceBridge !== 'undefined' || /Web0S|webOS|NetCast|SmartTV/i.test(navigator.userAgent || '');
  function platformLiveFormat() { return IS_WEBOS ? 'ts' : 'm3u8'; }
  function settings() { var s = get('settings', {}); for (var k in DEFAULTS) if (!(k in s)) s[k] = DEFAULTS[k];
    if (!s.liveFormatV2) { s.liveFormat = platformLiveFormat(); s.liveFormatV2 = 1; set('settings', s); }
    if (s.preview === true) s.preview = 'auto'; else if (s.preview === false) s.preview = 'off'; if (THEME_MIGRATE[s.theme]) s.theme = THEME_MIGRATE[s.theme]; if (LAYOUT_MIGRATE[s.layout]) s.layout = LAYOUT_MIGRATE[s.layout]; return s; }
  /* per-account locked channels (ids) */
  function lockedIds(accId) { return get(accKey(accId, 'locked'), {}); }
  function isLocked(accId, id) { return !!lockedIds(accId)[String(id)]; }
  function toggleLock(accId, id) { var l = lockedIds(accId); if (l[String(id)]) delete l[String(id)]; else l[String(id)] = 1; set(accKey(accId, 'locked'), l); return !!l[String(id)]; }
  /* content snapshot for change detection (auto refresh) */
  function snapshot(accId) { return get(accKey(accId, 'snapshot'), null); }
  function setSnapshot(accId, snap) { set(accKey(accId, 'snapshot'), snap); }
  function setSetting(k, v) { var s = settings(); s[k] = v; set('settings', s); }

  /* ---- per-account: favorites / history / cache ---- */
  function accKey(accId, k) { return 'acc:' + accId + ':' + k; }
  function favorites(accId) { return get(accKey(accId, 'favs'), []); }
  function isFav(accId, type, id) { return favorites(accId).some(function (f) { return f.type === type && String(f.id) === String(id); }); }
  function toggleFav(accId, item) {
    var l = favorites(accId), idx = -1;
    l.forEach(function (f, i) { if (f.type === item.type && String(f.id) === String(item.id)) idx = i; });
    if (idx >= 0) l.splice(idx, 1); else l.unshift(item);
    set(accKey(accId, 'favs'), l.slice(0, 300));
    return idx < 0;
  }
  function history(accId) { return get(accKey(accId, 'history'), []); }
  function pushHistory(accId, item) {
    var l = history(accId).filter(function (h) { return !(h.type === item.type && String(h.id) === String(item.id)); });
    item.at = Date.now(); l.unshift(item); set(accKey(accId, 'history'), l.slice(0, 60));
  }
  function positions(accId) { return get(accKey(accId, 'pos'), {}); }
  function getPos(accId, key) { return positions(accId)[key] || null; }
  function setPos(accId, key, pos, dur) {
    var p = positions(accId);
    if (dur && pos / dur > 0.96) delete p[key]; else p[key] = { pos: pos, dur: dur, at: Date.now() };
    var keys = Object.keys(p); if (keys.length > 200) { keys.sort(function (a, b) { return p[a].at - p[b].at; }); delete p[keys[0]]; }
    set(accKey(accId, 'pos'), p);
  }
  function cacheGet(accId, k, maxAgeMs) {
    var c = get(accKey(accId, 'cache:' + k)); if (!c) return null;
    if (maxAgeMs && Date.now() - c.at > maxAgeMs) return null; return c.data;
  }
  function cacheSet(accId, k, data) { set(accKey(accId, 'cache:' + k), { at: Date.now(), data: data }); }
  function clearCache(accId) { Object.keys(localStorage).forEach(function (k) { if (k.indexOf(PREFIX + accKey(accId, 'cache:')) === 0 || k.indexOf(PREFIX + 'tmdb:') === 0) localStorage.removeItem(k); }); }

  return { platformLiveFormat: platformLiveFormat, isWebOS: IS_WEBOS, get: get, set: set, del: del, device: device, accounts: accounts, addAccount: addAccount, updateAccount: updateAccount, removeAccount: removeAccount, getAccount: getAccount, lastAccount: lastAccount, setLastAccount: setLastAccount, settings: settings, setSetting: setSetting, favorites: favorites, isFav: isFav, toggleFav: toggleFav, history: history, pushHistory: pushHistory, getPos: getPos, setPos: setPos, cacheGet: cacheGet, cacheSet: cacheSet, clearCache: clearCache, isLocked: isLocked, toggleLock: toggleLock, lockedIds: lockedIds, snapshot: snapshot, setSnapshot: setSnapshot };
})();
