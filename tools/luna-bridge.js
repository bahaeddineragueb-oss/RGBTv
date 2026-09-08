// Dev helper: runs the real services/com.rgbtv.app.service/service.js handlers behind HTTP :8099 so a browser test can
// emulate PalmServiceBridge (see tools/conn_test.py). Not shipped in the ipk.
var Module = require('module'), path = require('path'), handlers = {}, origLoad = Module._load;
Module._load = function (req) { if (req === 'webos-service') return function () { return { register: function (n, f) { handlers[n] = f; }, activityManager: {} }; }; return origLoad.apply(this, arguments); };
require(path.resolve(__dirname, '../services/com.rgbtv.app.service/service.js'));
require('http').createServer(function (req, res) {
  var b = ''; req.on('data', function (c) { b += c; }); req.on('end', function () {
    var m = req.url.slice(1), payload = JSON.parse(b || '{}');
    if (!handlers[m]) { res.writeHead(200); return res.end(JSON.stringify({ returnValue: false, errorText: 'Unknown method "' + m + '" for category "/"' })); }
    handlers[m]({ payload: payload, respond: function (r) { res.writeHead(200, { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' }); res.end(JSON.stringify(r)); } });
  });
}).listen(8099, function () { console.log('bridge on 8099'); });
