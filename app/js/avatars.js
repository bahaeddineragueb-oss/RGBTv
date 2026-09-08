/* RGBTv — built-in profile avatars (inline SVG → data URI, no network needed) */
var Avatars = (function () {
  function face(bg, skin, eyes, extra) {
    return '<rect width="200" height="200" rx="28" fill="' + bg + '"/>' +
      '<circle cx="100" cy="95" r="52" fill="' + skin + '"/>' +
      '<circle cx="82" cy="88" r="7" fill="' + eyes + '"/><circle cx="118" cy="88" r="7" fill="' + eyes + '"/>' +
      '<path d="M78 115 q22 20 44 0" stroke="' + eyes + '" stroke-width="6" fill="none" stroke-linecap="round"/>' + (extra || '');
  }
  var defs = [
    { id: 'red', bg: '#e50914', svg: face('#e50914', '#f8d9c4', '#1b1b1b', '<rect x="60" y="40" width="80" height="22" rx="10" fill="#1b1b1b"/>') },
    { id: 'blue', bg: '#2563eb', svg: face('#2563eb', '#fde68a', '#1e293b', '<circle cx="100" cy="150" r="10" fill="#fff"/>') },
    { id: 'green', bg: '#16a34a', svg: face('#16a34a', '#c7f9cc', '#052e16', '<path d="M60 60 q40 -40 80 0" stroke="#052e16" stroke-width="8" fill="none"/>') },
    { id: 'yellow', bg: '#f59e0b', svg: face('#f59e0b', '#fff7ed', '#3f2a00', '<rect x="66" y="80" width="26" height="14" rx="4" fill="#3f2a00"/><rect x="108" y="80" width="26" height="14" rx="4" fill="#3f2a00"/>') },
    { id: 'purple', bg: '#7c3aed', svg: face('#7c3aed', '#ede9fe', '#2e1065', '<path d="M55 70 l90 0" stroke="#2e1065" stroke-width="8"/>') },
    { id: 'pink', bg: '#db2777', svg: face('#db2777', '#fce7f3', '#500724', '<circle cx="62" cy="56" r="12" fill="#fff"/><circle cx="138" cy="56" r="12" fill="#fff"/>') },
    { id: 'teal', bg: '#0d9488', svg: face('#0d9488', '#ccfbf1', '#042f2e', '<path d="M60 44 h80 v14 h-80z" fill="#042f2e"/>') },
    { id: 'orange', bg: '#ea580c', svg: face('#ea580c', '#ffedd5', '#431407', '<path d="M100 40 l14 24 h-28z" fill="#431407"/>') },
    { id: 'robot', bg: '#334155', svg: '<rect width="200" height="200" rx="28" fill="#334155"/><rect x="50" y="55" width="100" height="90" rx="16" fill="#cbd5e1"/><rect x="66" y="80" width="24" height="24" rx="6" fill="#0ea5e9"/><rect x="110" y="80" width="24" height="24" rx="6" fill="#0ea5e9"/><rect x="74" y="118" width="52" height="10" rx="5" fill="#1e293b"/><rect x="95" y="30" width="10" height="25" fill="#cbd5e1"/><circle cx="100" cy="28" r="8" fill="#f43f5e"/>' },
    { id: 'cat', bg: '#a16207', svg: '<rect width="200" height="200" rx="28" fill="#a16207"/><path d="M55 60 l10 -35 l30 25z M145 60 l-10 -35 l-30 25z" fill="#fde68a"/><circle cx="100" cy="100" r="50" fill="#fde68a"/><ellipse cx="82" cy="92" rx="7" ry="10" fill="#1c1917"/><ellipse cx="118" cy="92" rx="7" ry="10" fill="#1c1917"/><path d="M92 112 l8 8 l8 -8z" fill="#f43f5e"/><path d="M40 105 h40 M40 120 h40 M120 105 h40 M120 120 h40" stroke="#1c1917" stroke-width="3"/>' },
    { id: 'kids', bg: '#22c55e', svg: '<rect width="200" height="200" rx="28" fill="#22c55e"/><circle cx="100" cy="100" r="55" fill="#fef08a"/><circle cx="80" cy="90" r="8" fill="#1a2e05"/><circle cx="120" cy="90" r="8" fill="#1a2e05"/><path d="M70 112 q30 30 60 0" stroke="#1a2e05" stroke-width="7" fill="none" stroke-linecap="round"/><circle cx="62" cy="112" r="8" fill="#fb7185" opacity=".7"/><circle cx="138" cy="112" r="8" fill="#fb7185" opacity=".7"/>' },
    { id: 'alien', bg: '#0f172a', svg: '<rect width="200" height="200" rx="28" fill="#0f172a"/><ellipse cx="100" cy="95" rx="48" ry="58" fill="#4ade80"/><ellipse cx="80" cy="90" rx="14" ry="20" fill="#022c22" transform="rotate(-15 80 90)"/><ellipse cx="120" cy="90" rx="14" ry="20" fill="#022c22" transform="rotate(15 120 90)"/><path d="M90 130 h20" stroke="#022c22" stroke-width="5" stroke-linecap="round"/>' }
  ];
  var cache = {};
  function url(id) {
    var d = defs.filter(function (x) { return x.id === id; })[0] || defs[0];
    if (!cache[d.id]) cache[d.id] = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200">' + d.svg + '</svg>');
    return cache[d.id];
  }
  function color(id) { var d = defs.filter(function (x) { return x.id === id; })[0]; return d ? d.bg : '#3b82f6'; }
  function list() { return defs.map(function (d) { return d.id; }); }
  return { url: url, color: color, list: list };
})();
