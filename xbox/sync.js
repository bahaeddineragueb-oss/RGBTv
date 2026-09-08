// copies ../app → RGBTv.Xbox/www so the Xbox package ships the same web app as the TV / Android / Windows builds
const fs = require('fs'), path = require('path');
const src = path.join(__dirname, '..', 'app'), dst = path.join(__dirname, 'RGBTv.Xbox', 'www');
fs.rmSync(dst, { recursive: true, force: true });
fs.cpSync(src, dst, { recursive: true });
fs.rmSync(path.join(dst, 'appinfo.json'), { force: true });
console.log('synced app → xbox/RGBTv.Xbox/www');
