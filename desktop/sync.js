// copies ../app → ./www so the desktop build always ships the same web app as the TV/Android builds
const fs = require('fs'), path = require('path');
const src = path.join(__dirname, '..', 'app'), dst = path.join(__dirname, 'www');
fs.rmSync(dst, { recursive: true, force: true });
fs.cpSync(src, dst, { recursive: true });
fs.rmSync(path.join(dst, 'appinfo.json'), { force: true });
console.log('synced app → desktop/www');
