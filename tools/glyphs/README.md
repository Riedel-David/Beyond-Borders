# Map label glyphs

Regenerates the SDF glyph ranges in `app/src/main/assets/glyphs/Noto Sans Regular/`
(MapLibre PBF, 256 code points per file, file name `<start>-<end>.pbf`).

- Font: Noto Sans Regular, v2.015, SIL OFL 1.1
  `https://github.com/notofonts/latin-greek-cyrillic/releases/download/NotoSans-v2.015/NotoSans-v2.015.zip`
  zip sha256 `0c34df072a3fa7efbb7cbf34950e1f971a4447cffe365d3a359e2d4089b958f5`;
  file used: `NotoSans/full/ttf/NotoSans-Regular.ttf`,
  sha256 `f5f552c8c5edb61fe6efb824baf4d4de47b1a8689ab4925ff43f7bd6a4ebece5`
  (fontnik ignores hinting, so the `hinted` and `unhinted` TTFs give the same output).
- Tool: `fontnik` (node-fontnik) 0.7.7 from npm, run with Node 26 on macOS arm64.

The font zip and the npm install are untrusted downloads: keep each in its own empty
scratch directory outside the repo and pass paths as arguments.

```bash
S=$(mktemp -d); mkdir "$S/tool" "$S/font"
curl -sL -o "$S/font/noto.zip" https://github.com/notofonts/latin-greek-cyrillic/releases/download/NotoSans-v2.015/NotoSans-v2.015.zip
shasum -a 256 "$S/font/noto.zip"            # must match the zip hash above
unzip -q "$S/font/noto.zip" -d "$S/font/x"
(cd "$S/tool" && npm init -y >/dev/null && npm install fontnik@0.7.7 && npm rebuild fontnik)  # npm 11 skips the native install script until rebuilt
node tools/glyphs/gen.js "$S/tool/node_modules/fontnik" "$S/font/x/NotoSans/full/ttf/NotoSans-Regular.ttf" 1024 1279 "app/src/main/assets/glyphs/Noto Sans Regular/1024-1279.pbf"
```

`1024-1279.pbf` (U+0400-U+04FF, 125058 bytes, sha256
`ee79f4b586642a8ad5eac78d7a73088ae5ed55815821172e8682efaefa12e9da`) came from exactly
this. Any other range is the same command with its start and end (multiples of 256).

## How well this matches the older files

`0-255.pbf` and `256-511.pbf` predate this directory; their generator was never
recorded. Regenerating 0-255 with the pipeline above gives a file that is not
byte-identical (76021 bytes against the shipped 76580): all 193 glyphs it has share the
shipped baseline conventions (same width/height/left/top/advance for A, g, space and
most letters), but 27 accent and symbol glyphs differ slightly in size, and the shipped
file also carries 30 empty control-character entries and a composite fontstack name
("Noto Sans Regular, Noto Naskh Arabic Regular, ..."). It looks like an older Noto
Sans build run through a fallback stack. MapLibre selects by file path and ignores the
embedded name, so mixing the files is fine; do not regenerate the Latin files
unless the label look is checked on a device.
