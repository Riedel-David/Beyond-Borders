// usage: node gen.js <fontnik-module-dir> <ttf> <start> <end> <out.pbf>
const fontnik = require(process.argv[2]);
const fs = require('fs');
const [,, , ttf, s, e, out] = process.argv;
fontnik.range({ font: fs.readFileSync(ttf), start: +s, end: +e }, (err, data) => {
  if (err) throw err;
  fs.writeFileSync(out, data);
  console.log(out, data.length);
});
