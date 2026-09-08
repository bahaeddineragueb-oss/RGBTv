/* RGBTv — on-screen keyboard for remote controls (QWERTY / symbols / Arabic) */
var OSK = (function () {
  var LAYOUTS = {
    en: ['q w e r t y u i o p', 'a s d f g h j k l', 'z x c v b n m', '1 2 3 4 5 6 7 8 9 0'],
    ar: ['ض ص ث ق ف غ ع ه خ ح ج', 'ش س ي ب ل ا ت ن م ك ط', 'ئ ء ؤ ر لا ى ة و ز ظ د', '1 2 3 4 5 6 7 8 9 0'],
    sym: ['! @ # $ % & * ( ) -', '_ + = / : ; \' "', ', . ? é è à ç ñ ü', '1 2 3 4 5 6 7 8 9 0']
  };
  var layout = 'en', input = null, root = null, onChange = null;
  function init(rootEl, inputEl, cb) { root = rootEl; input = inputEl; onChange = cb; render(); }
  function render() {
    root.innerHTML = '';
    LAYOUTS[layout].forEach(function (row) {
      var r = U.el('div', 'osk-row');
      row.split(' ').forEach(function (k) { r.appendChild(key(k, k, 'osk-key')); });
      root.appendChild(r);
    });
    var bottom = U.el('div', 'osk-row');
    bottom.appendChild(key(layout === 'sym' ? 'ABC' : '#+=', null, 'osk-key wide', function () { layout = layout === 'sym' ? 'en' : 'sym'; render(); Nav.focus(root.querySelector('.osk-key')); }));
    bottom.appendChild(key(layout === 'ar' ? 'EN' : 'عربي', null, 'osk-key wide', function () { layout = layout === 'ar' ? 'en' : 'ar'; render(); Nav.focus(root.querySelector('.osk-key')); }));
    bottom.appendChild(key('Space', ' ', 'osk-key space'));
    bottom.appendChild(key('⌫', null, 'osk-key wide', function () { input.value = input.value.slice(0, -1); fire(); }));
    bottom.appendChild(key('Clear', null, 'osk-key wide', function () { input.value = ''; fire(); }));
    bottom.appendChild(key('Search', null, 'osk-key wide accent', function () { fire(true); }));
    root.appendChild(bottom);
  }
  function key(label, ch, cls, fn) {
    var b = U.el('button', cls + ' focusable', U.esc(label)); b.setAttribute('data-nav', 'osk');
    b.onclick = fn || function () { input.value += ch; fire(); };
    return b;
  }
  function fire(submit) { if (onChange) onChange(input.value, !!submit); }
  return { init: init, render: render };
})();
