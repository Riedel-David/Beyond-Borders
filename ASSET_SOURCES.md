# Bundled asset provenance

The app is offline-first: every reference-data asset under `app/src/main/assets/`
is bundled at build time rather than downloaded, so there is no source or checksum
recorded anywhere a rebuild could check against. This file is that record. SHA-256
and byte size were computed directly from the files currently in the repo
(`shasum -a 256`); "source" is what the generator scripts in `tools/` say they were
built from, not a guess.

The `glyphs/` directory is covered in its own section below; the full third-party
licence texts are in `THIRD_PARTY_NOTICES.md`.

## Map geometry — `tools/geo/build_geo.py`

Built from Natural Earth's `ne_10m_admin_0_map_units` layer, downloaded per the
script's own instructions as:

```
curl -sL -o tools/geo/cache/ne_10m_admin_0_map_units.geojson \
  https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_10m_admin_0_map_units.geojson
```

Natural Earth data is public domain. The raw layer is then curated by
`tools/geo/rules.py` (merges, splits, and hand-written overrides — see that file
for why) before `build_geo.py` writes the three outputs below. The script
downloads from the `master` branch and the exact `natural-earth-vector` commit used
for the currently-committed assets is not recorded anywhere, so the source is
unpinned; only the upstream URL and the curation step are traceable.

| filename | sha256 | bytes | source |
|---|---|---|---|
| places.geojson | c315e8b08909f1e23dfda270f1f81dd8722905da73845e78a519417ab1ccf3ab | 2669172 | Natural Earth `ne_10m_admin_0_map_units`, curated by `tools/geo/rules.py`, built by `tools/geo/build_geo.py` |
| places.json | 86db954005e6a032623cccd46ec3490606890d5fbfa833f4022af0cf65098aaa | 61861 | same |
| places_labels.geojson | c1185582b7fad64f36b91cf303c617e5adcec52b402b5194258ea1d678fc9c86 | 39798 | same |

## Visa requirements — `tools/visa/build_visa.py`

Built from the Visa-requirements dataset,
<https://github.com/Riedel-David/Visa-requirements>, published at
<https://riedel-david.github.io/Visa-requirements/> (`index.json` and
`passports/<ISO2>.json`). It is itself derived from Wikipedia's "Visa requirements for
... citizens" articles by Wikipedia contributors and is licensed **CC BY-SA 4.0**
(<https://creativecommons.org/licenses/by-sa/4.0/>); the `visa*.json` assets are an
adaptation of it and are shared under the same licence, see
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md). To cite: "Wikipedia contributors,
*Visa requirements for <nationality> citizens*, Wikipedia, The Free Encyclopedia,
revision <id>" — the article URL and revision of each passport are in its asset
(`dataset.sourceUrl`, `dataset.sourceRevision`), and the article's page history lists
the contributors.

`build_visa.py` always downloads the latest publication into `tools/visa/cache/`
(gitignored), so a rebuild is not reproducible from this repository alone. What the
committed assets were built from is therefore recorded instead:

- index.json `generated_at` (also each asset's `asOf`, as a date) and each passport's
  Wikipedia `source_revision`: both are written by the build into
  [`docs/visa-source-revisions.txt`](docs/visa-source-revisions.txt), which is the one
  place to read them. Each asset also carries its own `source_revision`.

`python3 tools/visa/build_visa.py --source DIR` builds offline from a directory holding
`index.json` and `passports/` (a checkout's `data/`, or the cached download). Only the
whole-country record (`area` null) of each passport is read, plus four places Wikipedia
files under an area name (see `AREA_IS_WHOLE_PLACE` in `tools/visa/rules.py`).
A passport whose article yields fewer than 150 answers (today only Northern Cyprus) is not
shipped and is named in the revisions file. `visa.json` is the DE (default) passport; each `visa_<iso2>.json` is the same build for
that passport code. The DE build additionally folds in the German Foreign Office's
travel-warning open data (`https://www.auswaertiges-amt.de/opendata/travelwarning`). On
top of both feeds, `tools/visa/rules.py` applies hand-written corrections and curated
entries (see that file for individual citations). The Foreign Office JSON is likewise
downloaded fresh and not pinned.

The SHA-256 of every `visa*.json` is recorded in
[`docs/visa-checksums.txt`](docs/visa-checksums.txt), regenerated with:

```sh
cd app/src/main/assets && LC_ALL=C shasum -a 256 visa*.json > ../../../../docs/visa-checksums.txt
```

Verify a checkout with `cd app/src/main/assets && shasum -a 256 -c ../../../../docs/visa-checksums.txt`.

## Country flags — `tools/flags/build_flags.py`

`flags/<iso2>.webp` (250 files, 418882 bytes in total) are the SVG flags of
[hampusborgos/country-flags](https://github.com/hampusborgos/country-flags) at commit
`c09927e63705529bbf59ca6684cd9b23225dddad`, fetched from
`https://api.github.com/repos/hampusborgos/country-flags/tarball/c09927e63705529bbf59ca6684cd9b23225dddad`
(tarball SHA-256 `fc34d6635d21a63579268bb37eb5449c4971667cde308cfcfb44104cd961a285`),
rasterised with resvg at a fixed height of 120 px (the width follows each flag's own
aspect ratio, so none is stretched to 4:3) and encoded with `cwebp -q 85`, alpha kept.
The code list is read from `places.json`; `xn` and `xs` have no upstream flag and the app
falls back to a plain dark banner.
Licence: upstream ships no LICENSE file; its README states the flags come from Wikimedia
Commons and are public domain. No licence text is bundled.

| filename | sha256 | bytes |
|---|---|---|
| ad.webp | f7626be003bc81947609de7d0530ff8466da6b47dc9104dc20be172b6e9126c6 | 1512 |
| ae.webp | dd8b6c56731324890daf1f48dbe7bb4a989602b21551cc7c181f3ab6c7dba5e1 | 226 |
| af.webp | dceb50a2041dce2941e055d30efaadd2d860a62da2021195990949f21910acfa | 4196 |
| ag.webp | 4cfc72390229b27819123a7636ec5ccc88767bf46478ffa3316d3368081b9bb5 | 2316 |
| ai.webp | fadb873d08cedd2ebc3ab6b0213408bcba8c606f0d80c6685ad92161c8bf863e | 3370 |
| al.webp | 2b7bcab31d2da7655dfbcfe8e38a7690e8a93af3b512a366f3d3913b13a09e8c | 2236 |
| am.webp | 8dfe97690575e7e8cdc9153ee03e98f010d30b6c87618d23232925f00d93491f | 220 |
| ao.webp | 3198a891a54e31a5777f9eb0b17414851ecfb771a8e14f9e33c5433b9b7e4c32 | 1684 |
| aq.webp | 2ee89bc8c9a7e3b0a62817105e61c551dc520a77dd152ea9036f301df780f928 | 5688 |
| ar.webp | 4cd81a6ac63fbc125b8cacf67d74db67eebc9885142a647f4983b28df1276a5b | 880 |
| as.webp | b61cf98a265dd604e0cd0d2f555c1e1703b3b25daf583412b7949aa3cdd70f85 | 4646 |
| at.webp | e14aebce9e51ec4c8a5b5399e1936125def545e9b6a6d8b43e20ecff35d43d96 | 184 |
| au.webp | 3be3136dd759ed0bc33b6d7946fdde51064443c6c80f208647b838d13ea5b55f | 3652 |
| aw.webp | 40671e44031114e69a59144c5d2c4094de6ea0409e46582dad63f3098fc70936 | 798 |
| ax.webp | 5b54b5a19416b332a53e8ae1ec8be3a4deafad21ed63ddb9159574d1b8b5378f | 804 |
| az.webp | bbb369db6f2eb47800a6d0d72fe11e758557b46cf83dd198b8f89660a5a9a52d | 1002 |
| ba.webp | 740a1b1eea6058a03f7cb0952c831875bd9f24362509cfddf7f464a920b98b39 | 2668 |
| bb.webp | 581375c4bd4a7933564a3655c249d1fd65ea9ad6cd1d373b4eb48f43f9bfecfd | 1160 |
| bd.webp | e5f49a2cae304f27efcdca705d4577aeda9d36d71837fa0f19f2cd30ec47a061 | 820 |
| be.webp | 4ffefbff620531e49a4eb2a5d945ce07ec2e90990bdcf5de0086c7997668cf8f | 208 |
| bf.webp | 10d2ee7b0203f96dcc202859294320000f7b6ee92d57cf023fa58c20555d3fb3 | 704 |
| bg.webp | bfc6563c1a728a55379ade22239cf7f123582586c247e8b5c3371675175288ab | 196 |
| bh.webp | 1bc0fefee6d3903a223724af91056d88a47c5c31beb67e6c3493f3251d934245 | 1156 |
| bi.webp | 4d49804245513200a5475326d1d0a182fd7e821a1993f260acb80a6101e35d27 | 3276 |
| bj.webp | 054630abc8037adf1810f8f531d5f40fa62a23cc93c9ba6d5254ad9b150ce1a2 | 184 |
| bl.webp | 8392c576119b102573dd811f74fe3cfbcd8f93900a4b59fa5227a20d5386b2c6 | 210 |
| bm.webp | b498d80209d6edba36bd102f52cbbfc45915ac154c33714665b1d2120ea72d4f | 3796 |
| bn.webp | 5ec777e1f4fc95683efdf6b43753b229b9fb6ef4e6ee30edc3d585591a11c73c | 3782 |
| bo.webp | 3c6c8445acd548f21b544825741e5a1f0d3b889bca705e6e981098c15c90619c | 1434 |
| bq.webp | 3122310945713d2fec3e47cb0902bf2ba96aed62ca0f1a8275bfc0b22fc43dce | 184 |
| br.webp | 3835768c702f6d8e0ce47c82e22d4d9db1cf23f1fcb9f827f1d987ee665c2b0f | 2760 |
| bs.webp | e903f3cf46e2edb39b64c8e4153d813dc86c496d7eec3bbbfdf361b4d73e8578 | 1028 |
| bt.webp | 9ae452a8da2bc4bfd1b86fb7b6de0d6685fb6197319131d34fda74ed2c2e5e28 | 3304 |
| bv.webp | ef8d2f2ae3223fb0ce316b28621f04d332fea3f6346e661a4c03b61a58216cd4 | 728 |
| bw.webp | 08e3ed32158c3d3db02b4697ddc190457b644e256bc882d9783d89df5f147c8f | 298 |
| by.webp | cd3f4a993108f32f4651a62039f1b45de2eb4d00090906b134fd649b51f72b95 | 2690 |
| bz.webp | 6e6fc60ad434889e05bd1b6c94cfb2771c70f34a9d1265a8d5279b2ef57f8be7 | 3014 |
| ca.webp | aaf54be68bfd6f1676736b59b53b2321dac499a0a7802148e69e4af13050bb0b | 1692 |
| cc.webp | f640f8ec5a59313f979526f29ddeea33e11516a481ce63aed15c54438d045f15 | 2858 |
| cd.webp | f1aba34610782e988af492e33e0214ce8995d56ba951e94cdc595d5648f744f6 | 3300 |
| cf.webp | 35223793400b560ae301d3ac7141752ba4ca12012f3fa8ceb56ed232d564de9e | 1028 |
| cg.webp | ceb5255d1f6b071a73a656ef1ce6dd0fb3a70a31cf88cbdfa94b4e5af3f06030 | 1174 |
| ch.webp | 4c2cda08011205781e5babab38da390115bef1a32ad882913b25d66442090811 | 522 |
| ci.webp | dda6897620fc1ccd8ca63e7157944d6d9871a5a46ca2e8d6e5b6114dcaafe629 | 220 |
| ck.webp | ba53286a14444c2278f856d04bcbc5fbe50a6a1fa824ce5523f86dc19a380e2b | 4794 |
| cl.webp | 01432b659466b6288e1a77e9e1ae7c81360d3329270d23915af8e01c43768fcb | 636 |
| cm.webp | 368d715e321f6182529a53c2042284aa786e5a62ee8fb83520bb196113a96e42 | 568 |
| cn.webp | e290c6b797df9f0229d52d21015d46856e64bbe8607bac2e96bf33683fd871cc | 1134 |
| co.webp | 4bd128e00d265e9c76d78880786b8291b6be671dbfa5d66d961b57d218ebbffd | 270 |
| cr.webp | 467b609fb18aca8f75d790bbdb6feec3a09eafd031dace796a1f4e50812dde70 | 1072 |
| cu.webp | 9d56e13846afb368f0b2eccea6931d4c841c1cf6b779ba700d5a35a24d82a3f7 | 1534 |
| cv.webp | 7c19387bf79f99649ede718a6d003c76a569b2e6414d2a6af372977d20230719 | 1628 |
| cw.webp | 3c65df112edd5380f16c71ff9b11df165223ed95ea38089389a28aa57299cf5f | 826 |
| cx.webp | 2c024bcd77b82d915ccbecd5388062b0054ae43cdb74f564db97b1a681a9d63e | 3128 |
| cy.webp | a6df8000dca150322439ed75f0fdd356ed3fa51de853785983906d23c1dde0e2 | 1772 |
| cz.webp | 7b6b1a6148147b5e357c2f4600a81f3a38e2836abc6cacc21f39a01308caf903 | 908 |
| de.webp | ce214c77f9b849f06fda42f8551cd627f451b8473ada95eae57bdbeb0309b4a5 | 184 |
| dj.webp | b8c0428d8f8d97a37ddfcbb97ced5edf3dd1a3089cdea5866bf92bc885e9b2a0 | 1394 |
| dk.webp | 0b157e75ce83c73adc8ce93ed40902c2d99c6e7a07ddf0592b116b4f4d8d73d0 | 450 |
| dm.webp | 866f91b19c876a208701727db45122fae48aa57f450de252232120bf986a7113 | 1862 |
| do.webp | 2866bd9408922e6a959d1dff52f9762e0409285fbc1bcbbeea80b053b14ba000 | 946 |
| dz.webp | 6706101b993251c6ec661d9cd3aa7e9ec676ddb324d97cb48aa410f3d8630a13 | 1236 |
| ec.webp | 736f6a3e92ffbf517c5be69d3c59a622ed31ebca2785003192c90a787a9d83b8 | 1970 |
| ee.webp | c8fadc6a94e086a443fcd97eeb271b66aaa8e42aa8db10331b115e2da9dd3e46 | 172 |
| eg.webp | b8a036e038000369e27fcb0007f9361cb86141aa5033f3ae772fbbc2a2103e56 | 754 |
| eh.webp | 8bebee7bdb43b18b127867c0f9b1432bcef79cb3991d8cb41ab783da13d0625f | 1394 |
| er.webp | fb346442bc8163329684522f7dcb7afc52f73be7df6762de8b88c38a3da8236f | 3510 |
| es.webp | e1d9fddc319b3e003258cf65c8b005998810f374bdb02ccf719b4068f50365ed | 1496 |
| et.webp | 3d0c3b596081b0d0124e34172c1dcb08ace032d0083df1702620f680d51a7f87 | 2584 |
| fi.webp | bf096446ce35296fd03740baf9fed30af91caa9171d677b17b9c69c5a2c7834f | 432 |
| fj.webp | 0b0c48d7cf7f2e72275f6c8f9d18bd3e121b12be116a9b63f0959430cb1ad34c | 4046 |
| fk.webp | 91e091018013b6ae4a7be5ebde3430956d80aa11e8d2ce9c02fd240d7c20e1a3 | 4414 |
| fm.webp | 49cf744bd781a0a787e558a88b59f084e0ae8e5a3ad5b512c4f0cdf524bd5b33 | 928 |
| fo.webp | 9121cebb29177155394d03539cd9f9538db9232758844ac0e8f6d32605185011 | 752 |
| fr.webp | 8392c576119b102573dd811f74fe3cfbcd8f93900a4b59fa5227a20d5386b2c6 | 210 |
| ga.webp | 39e580211992804bbdd9ae94ab1dafef5a6d372285c008250e2933a8d69e7e79 | 202 |
| gb.webp | 361cf81eb95d551fcb825275f6e35cdbbea6111456ef4e08b422ee36795d0910 | 4042 |
| gd.webp | a84a35f1bbd407c31dbace7abb108800168d44caefc8113bbffbb8895ae7100c | 3066 |
| ge.webp | 1fa5754b37feb22d97b36f61e1afd42f381aa4863848ea144a3787c9d14e397c | 1764 |
| gf.webp | 8392c576119b102573dd811f74fe3cfbcd8f93900a4b59fa5227a20d5386b2c6 | 210 |
| gg.webp | 11e8c895be64710ea12d37b0424dcb8ad34cfa54ee4567c831db05f2845c1bc6 | 1198 |
| gh.webp | 45dea922619485b308f6781bf40bf6a6f8851396423f4ba7015effb3f0074008 | 778 |
| gi.webp | 2d9828ddf366a19434aae3fd6aca4944f0cb6adf63aa2f3c9b624c1f93497e8c | 2406 |
| gl.webp | 3ebc6ba2942c6b0e51d1fbaad78b8b1c3c00c65d149d3cdf90f59e716f2e53f0 | 1096 |
| gm.webp | a996a6f64d2063533e1b924b6f8cf4f0fbb6e4c3adbdf140e9a83eaf9103368d | 306 |
| gn.webp | f3a43894d05f3b44cfa23762da5c2f134de5d0ad81e6f4c900e3053ec199fed7 | 192 |
| gp.webp | 8392c576119b102573dd811f74fe3cfbcd8f93900a4b59fa5227a20d5386b2c6 | 210 |
| gq.webp | b3a11566b5a175d4ca207d818b45a8164f3512c494ae170ef43e8c2a6be76da9 | 1284 |
| gr.webp | dee0271e25a331c3a288589f18f709cb5b2475d5080e0b87a277394758ccd8e5 | 648 |
| gs.webp | 7638c8f61b73f64f722a4b335cf845a31308e11280209d51b4c0d10df3bb8063 | 4920 |
| gt.webp | fcdd616687617de7bc5e4d62942af2584677b44065e0abfa561f6bf8cbf2fc84 | 1118 |
| gu.webp | fe88efa94ae88b71ccbe8089f8dcd8cf10ef10a5d3229ac0339754e855f56bd6 | 1608 |
| gw.webp | d419a8b9aad1ae14f51e52669c84498a3a5c49b277415fb9379ac7a4bdf577b8 | 646 |
| gy.webp | 81a2cc8a7578794e34322fe63464568dd540611088340bf7f17afa26f77a6c1c | 3242 |
| hk.webp | dd90467e7d9a0ed6a7b37b978054025066334739cb1dd6a39f434309233e6b86 | 1896 |
| hm.webp | 3be3136dd759ed0bc33b6d7946fdde51064443c6c80f208647b838d13ea5b55f | 3652 |
| hn.webp | c01e5d68c8d192ce0af3a70619bf9b81aebd2de63fa115260168f9c4d24bb655 | 912 |
| hr.webp | b6ae21dac389106e4e123befbaaa5f6876f4f867bc7402e8f43ba3051c2df2de | 2130 |
| ht.webp | 5bc8aeadf85f1d049f4279de9173a411da5f522739ec22c4789b10ab060a374c | 1054 |
| hu.webp | 76f58b6efd354c856f62ed362d7c4f5fee30714d47aaa6d919369fb636fe67f8 | 234 |
| id.webp | fb3e35f4bb6367fa30fb443216be134306f58d30afb73a444e0632b38355bc1e | 206 |
| ie.webp | 2e07f53220db6d191fdf31f07b13e44211c3a99902731b4b049e2d521eaefa79 | 200 |
| il.webp | 47da0b108b5c65f1b2069097f26ed0b969be25143d7207c54e75d8c66e986ab4 | 1388 |
| im.webp | 16c4caf3e46f0b0e64a39cfb0f7f4286a2c49117d67f11afea4f798caf908976 | 1942 |
| in.webp | 4edad8683bb21ed6627cbf9566bf41818853dbf5b7b630651950fdddf45fbf8e | 1254 |
| io.webp | 0ce89d6487c369045299477af6698cf24893668523cfeb48362cca599bbc87b5 | 9876 |
| iq.webp | 1dec27b1d86e1a6b8b685e4d534d9b26df18c0ad9c4d584791e83b7154bde8fe | 862 |
| ir.webp | ddca4a53a77bb7ca58d49e10f535f8cb6c293b384d84c0ba64199d2934c16fb9 | 2880 |
| is.webp | c8ce672141ef04ac1e1b93e04c140bbf170fb670aa9e9f716915d14d52192d3e | 688 |
| it.webp | ade60dcdd01c4b45d7aed19a2f5930d6e3aeab8be577fe37bc12df7cccf854f3 | 182 |
| je.webp | c8b2a3ae712de973d26e583fd484a05708bae615fa3aa386552a73265186bc41 | 3676 |
| jm.webp | 0a065157378b1704f4354644bea785f2754946d7100628599bb9cd92d901891a | 3064 |
| jo.webp | 7934a1b2c6681afacbcf03150ee26b716d7155da7659c1c0ab4a59a8d4213b02 | 1114 |
| jp.webp | f88fc335cc8cbc6d28f53151b7169112d90b95080b876be4145f9b176cee0b94 | 868 |
| ke.webp | f340cbeaae069a2ba4de17136a99ae62c9b2bfe2c6f242aa275ef07fc3253e61 | 1652 |
| kg.webp | b08c0f48e6a454f93fb889dba1e1d97b9b6c81ec884ea5d9c4b7cd58b69c99d1 | 2698 |
| kh.webp | f40f7b943d19496fa931252ed407e71d48138f5140762548699bf4b9ff2fa05e | 1776 |
| ki.webp | 2bfae04770283652f2362cb83ee0e692a3a0f92d1bb40ff4d18828341abf8643 | 5392 |
| km.webp | 0da05c35d72002cabbba4503e8bb4b306070aa6e243513350ccb285129e45c23 | 1632 |
| kn.webp | f17301d35dcf209aa8b5d6fc821fe916ab00c436e4e5b3ef8933d41185470e52 | 3366 |
| kp.webp | e8b3534844c1ddefd953798ccf1cd0ea43f6d08c55bcbe5f4a2c89c07ee6f76b | 1460 |
| kr.webp | 0bc7690f9a2dbe32618fa5bb8abef3bbc48defaeca6d8db54b0e2ab01593d58e | 2944 |
| kw.webp | b649faf25162f2014af42d3f5e26c0f95013933f911c8cafd258388a2d68e311 | 642 |
| ky.webp | 3efc8fbed54a39eb4b2704ae858a766e5bffb5797b48bcc181978a21ad356916 | 4538 |
| kz.webp | 417f262799eb327c9c386a6a338230cf131c473896a93578e5c573bb73a92465 | 3238 |
| la.webp | e610f483400022820baa94be6918998a626208954858869a72c3eaae32a87a42 | 784 |
| lb.webp | aa75774bd05768d519e0f0e3ef7d5ccb3af0650567423bdb1b581f25680f90b7 | 1670 |
| lc.webp | ef9d0f2440499a9e7216041a12e7988cb060b69a675c18547939b5ee6d7519a7 | 1588 |
| li.webp | ad858a621fac57d2be764029d4e99ecb36224372ee8c45dc51558ad9dd4721d9 | 1046 |
| lk.webp | 3760ccfb53bac4790a511af9716adf098bec48e087544869a403f7284173bc6e | 4076 |
| lr.webp | afc26bd2a7118ddfa602ab15ff594a32e3723d013af1e1807bd967ace92cad89 | 1116 |
| ls.webp | d9c6f448b0e54e834cdab141989734cf3e9a45523a1488ba99c777e45353b1d2 | 934 |
| lt.webp | 7839a75e7b57e1121a62a60d1ce9c1526b62bbcdf109a2bb975f518148b91632 | 224 |
| lu.webp | 77c585eaa2c2fc449169558284458652f0989ea58ab972f655723bc9354758c9 | 190 |
| lv.webp | 22c44f69c4e34a0561e67237a326b685edfaf20fb8915f6512503159e6402e0e | 190 |
| ly.webp | 83bbd135f7d9c177712d4db5450debfc41317493a8b70ed4aa29f0a6c3a98c62 | 776 |
| ma.webp | 1046ea254e5743de8f91697438f094a215e60af03f8d192f37543e57a99088a9 | 754 |
| mc.webp | bc876aaa7bfa31b9c199680081144ec830db4613b3ccf995b4f057a5d342742a | 178 |
| md.webp | 622634a2d5b3eebca28d6707ae081b51e00d5d16b009fd3c7f2f87e59a6e6e7c | 2258 |
| me.webp | a7e3722ab02a501b5b6b31750c1a041658c4b8bafeb0f9c7287253d1d37dccec | 2514 |
| mf.webp | 8392c576119b102573dd811f74fe3cfbcd8f93900a4b59fa5227a20d5386b2c6 | 210 |
| mg.webp | 5f3bb85047481b3ca8874b3bc14431b25159b276493f4aa76ea5c2ae5b9ec7be | 222 |
| mh.webp | cbfb9339d34ab467e9e2486953adcfd33b71919d86253cb67a3629ff8f5fabe4 | 4188 |
| mk.webp | 0a4c07e77b87001968c8edcba5b394b15089a364cf85c768ad0af064d9332647 | 4682 |
| ml.webp | 98850de334bc1cf783596e6ed1870eaa8c75105e10d2f86357f6782ffcc5a62e | 210 |
| mm.webp | e8ecd081d1bc59407f12a1038c19e86a765753ff428fc9f88f17d04a1b7b6e8b | 1146 |
| mn.webp | 928e4d6e4113b81878ec4a25a94d3ead6b5eb978683133867b8524fa8660f0c2 | 1570 |
| mo.webp | 6a51a04bf435a1d1082617580ff213becff4726d66500e7577dcff3c0dd7dfe5 | 1726 |
| mp.webp | 26fe7f66cd828e450652847a4a5f051bd3994701fa3ca2d7fa93bf8e160507fc | 4378 |
| mq.webp | 8392c576119b102573dd811f74fe3cfbcd8f93900a4b59fa5227a20d5386b2c6 | 210 |
| mr.webp | 3417a0ba84eaee8033c8a636e389d834aa1ba11f78702544b7cd7b429c06eb5a | 1314 |
| ms.webp | aebd76655b81875f91d12601468e0d2e1a62384a7373abdbcd43d66a64748639 | 3418 |
| mt.webp | 68ae53a1049ffe6bde9110a59bef50b95d8244e232cbba9e696a6f15ff6d73f1 | 600 |
| mu.webp | f5786b837b7c644fc611e6a27be44d32f950f5958be78bea96aa5a03a4448403 | 298 |
| mv.webp | d6f8f483c45083ebe2d139ac7b2c2a510512848f9e2fa6fb359d415e65a045d4 | 626 |
| mw.webp | d1ee280ef7fb24971363a8678967d6294af1ee71cb5f5587253cf160ad0f71e8 | 1256 |
| mx.webp | 05b6d88b7dc15ea693d0de3d93bba59256855e77782ce5d635cc087694055ff4 | 1570 |
| my.webp | 9a758b92e6e9b99e5e024f663bc4606bb75cab246ef63921960a68130baa6f58 | 2278 |
| mz.webp | 240805c3ccabfd63c1a3e7eba68ab05357fa32eb33588f4dfc41a1b9e3e3f8f7 | 2000 |
| na.webp | f91c99b9d699a001d25fefd9f2782cd24ee23d72f06c39c4e3ff1a2a1e7a24a2 | 3660 |
| nc.webp | 8392c576119b102573dd811f74fe3cfbcd8f93900a4b59fa5227a20d5386b2c6 | 210 |
| ne.webp | 29aae1d32fcd5d4c7a2da4b0f7c7049f2b37137e17a06e9e8860761c73b59f61 | 608 |
| nf.webp | a26f4158dedfbf181fdb0ef58fb188a0c440deada895e7a3a1f538b2afdd8d67 | 2486 |
| ng.webp | 29796337ba2ae749e1847103f159bf533133c7c45e0284e605c9662232e55b5c | 190 |
| ni.webp | 493bb9a71299ca6434cdb5c58ff92c0f61103f7a36f32de3eaf1761ce4cce306 | 794 |
| nl.webp | 3122310945713d2fec3e47cb0902bf2ba96aed62ca0f1a8275bfc0b22fc43dce | 184 |
| no.webp | ef8d2f2ae3223fb0ce316b28621f04d332fea3f6346e661a4c03b61a58216cd4 | 728 |
| np.webp | a3f268d6f3ac962fea2c6bcc434e960dd38e69bee9cb6cc5917606cfbc6cc2a0 | 2140 |
| nr.webp | 8bc5152afd613f24d465f5935427557c0fa4cce40ece098652b13f86baa9960d | 1090 |
| nu.webp | 064d9582c7e54d8c90a5d491394548d588bfb0020a9f2bbd544d0dc5d7c68740 | 2962 |
| nz.webp | 4bf1151d942bac05440a2dfe4b190090a09b24a1364e4d94fb8ac71f800baabd | 3210 |
| om.webp | 52630dcd9da530772bb36142ebd64969c633d6b5fbb95492b57c166878d3c176 | 1038 |
| pa.webp | 4bac31128a7c74de978775bcd09c2325dfb6ec32f5935ff3a40bfc7dd1fb4f6d | 948 |
| pe.webp | 5bd2c176965d7894288610b9594d9f50972f73939c6553bb5a7c555deee340da | 1654 |
| pf.webp | 592c9b99e6083fd3da9fc6c42a1e057d7692b4873cfd7226c1915e6be5c92157 | 1988 |
| pg.webp | 6a5f515a81d67e13a30680fcae4ad8131bbea93e748b4b616eb65ab8823e8fe0 | 2372 |
| ph.webp | 98b344c7c472e495facbd976e8fd120675468299ebfefbd1a41cd255a36c43f2 | 2172 |
| pk.webp | 59ced0eae19da0bf3876cfc2a1bb8e7ff898cd26082579cfac2c5a5652158739 | 1084 |
| pl.webp | f68229fda39cd4b4834cf4630547fe9667b2b4a04be760406af8045373f89966 | 182 |
| pm.webp | 8392c576119b102573dd811f74fe3cfbcd8f93900a4b59fa5227a20d5386b2c6 | 210 |
| pn.webp | c442eadb1804220f7a4b2f339dad527b60a142997581bfe1359816e566e97c6d | 5224 |
| pr.webp | e0492b0660962f5fa7eba8c1657186f2c1ba196c67eaa6164c48891901bc44e0 | 1658 |
| ps.webp | c29d17905a997e5c967f112079417991c4a53c5f04cf22c13a1f9df3b6881406 | 854 |
| pt.webp | 49869076150179bf5426e8b076c808fbe0ce9300750a8625f29d7a72ffce544f | 2332 |
| pw.webp | 6756565ee09cafc37d073986a6aa0c1a6114c9b3831cd8a8f0a75b5807de6ef4 | 1046 |
| py.webp | 9c7d5074df1313047861be0b48a9b371e1ace1bf48c7ccd12d3d894843ca4c1f | 816 |
| qa.webp | a547c9bafb04f51f425fe14d76b79a756bd7ed3ad1db541f3a0ba3619b98542f | 1130 |
| re.webp | 8392c576119b102573dd811f74fe3cfbcd8f93900a4b59fa5227a20d5386b2c6 | 210 |
| ro.webp | 367aff52d222f5ccb7dad67984dfbee2a1fa2c6575dcdfe26b93fe5882e4f5e4 | 210 |
| rs.webp | 0c8d27a3f244958e360fb74dac8846ea2e29731dee1d389574d5c2222b969d8a | 2564 |
| ru.webp | c9a8ab9abec239d94d5e0f81ef59f3aa10521f9c5b9656088b7ea34e721b0b39 | 176 |
| rw.webp | b73f408963d01ca5304193ba358ba79ff5be194080ca1f314e0f41c9d2e5a388 | 890 |
| sa.webp | 8e80f71e1e2d08a0b27aaa429144fcc7a2894ec5ef92695d7ae931bf6bb93b2a | 2302 |
| sb.webp | 068d9b97d5f51f0a4f627438db374aceef2d255314b359e051a82b80dd171868 | 3378 |
| sc.webp | 075d9a26a39acc38e11299960122b670b294e0e03482ffbea6b81722eeb8b4c8 | 2962 |
| sd.webp | 0d911a15416e1ca6fecdc23f10c87c22487fc11fbaf094225f9eebe9cc9340a7 | 828 |
| se.webp | 6227ffccca034f236ea1cf59ad8b80f56c139dc5e84673fdc88166683cc42bcb | 318 |
| sg.webp | 3343a1acd8350aa6a4279f214d6388d32e490d3e8c52738b5c4f6373ab825ed3 | 1132 |
| sh.webp | 361cf81eb95d551fcb825275f6e35cdbbea6111456ef4e08b422ee36795d0910 | 4042 |
| si.webp | ee33011d388d1a3b989b10e2cdd79813f763122fd420bce248655bd742d072ac | 1042 |
| sj.webp | ef8d2f2ae3223fb0ce316b28621f04d332fea3f6346e661a4c03b61a58216cd4 | 728 |
| sk.webp | 73843e5381fa848bcab8821912237e143d5f9039596935d65bee545f4a2b5a40 | 1386 |
| sl.webp | 2943debec9e6c36e9e5652971c06e2a59d9cc61f7353289a80934191a762e349 | 198 |
| sm.webp | 2e99cb8521120e49c6bb11972304b00b23ced2eadb2a3f2a78e538d9c9fac18e | 2358 |
| sn.webp | 7468f0a8a36320290eca45923c9923d08f44d0526eefd73f197eafb79758a9d4 | 724 |
| so.webp | 4052a3144f5018d3a1a640031cade968b439e584d0eb76a314a6b9c0fbd8c62b | 768 |
| sr.webp | d6ce0e8bf03f2d1e3121499a20581f7c5295e80f0ecd156d5a75c7c3b727a7d3 | 872 |
| ss.webp | bfec876b88f0133b93fcca53ff623f727c3ea3a837bc0af22e2ff4291a7cef02 | 1530 |
| st.webp | f0ed0819523e573d02f72c9855091ab6152dd7f8adddb433aabb98ef50e8c352 | 1692 |
| sv.webp | c2d72896e450461813b1c30897c0bb79a15caa0a586ba7200ffd8497bc9b32cc | 1068 |
| sx.webp | d378602b94b0f793d91f19de946af3d978a0e909f6f3e2c0b64e8707d84800b0 | 2514 |
| sy.webp | 86535fb70f1f119c0dea119581ef1076533681636b5fb330196a6a0cdac1b625 | 1336 |
| sz.webp | 2f239251721e68ddfbe3c38925788f5a5891470fbf1ae160ed23a5c5312690c9 | 2522 |
| tc.webp | 59a3f447ca5ca1b6b8573216837a18185bad9f829784bb6422bd0587ee881a3f | 3290 |
| td.webp | 9ea1f8597957d735808ce8a179e69a83f721b253ec5f1dcb01e54f6c058796ad | 204 |
| tf.webp | e4ffd563436d68b3d931e450535fb8734a7a3101ae01b5356c358b97f77ee85f | 1664 |
| tg.webp | 6db2e3510f82d280f55b3d301f7846889974678c764aa61212d3b3a3fa2f17df | 888 |
| th.webp | d274324a06a839c5ae57fb7bf01a8ce8e7bff996e5447b426fa50187fa00463a | 272 |
| tj.webp | 5f1ec8a6343b629031d0965b50add899fcadcea1164f0307248e9a5fe052714c | 1284 |
| tk.webp | 9e16fb1d7ae1fb5b3fe8d4731a71145449a69f0dbf6f75625fcdc8f00cb34cc3 | 2410 |
| tl.webp | 9a6423fa2e8595251498aded3f1ce21a9caa7885624a65a0efef0e4902c14826 | 1980 |
| tm.webp | dea8f03e2f7a218cf989d85c9aa8906b5e362ec6b79f0a1b90554a2ba88359f5 | 3172 |
| tn.webp | 3ba1355ae690b1b3ca74c4f9d6ab53c580875db56b11898b45546a9c22b97ea4 | 1670 |
| to.webp | 5e1deed3784d83eced41782c214f876ad781112c2285b385b534f1da63097c33 | 682 |
| tr.webp | 4e182e1420fe4c8617996a6aae9641d32593933f7d92bfe4a3adc1df7e5d5c5b | 1398 |
| tt.webp | 16e394642765df5cbb03f50ff3f93d5ce97e335632a668488e1867c4cb754673 | 2244 |
| tv.webp | dae5ccfd0e3049fdcd67c9bba74176a99bd40e0a0c244ab3f6ca8c7d42b65b1d | 4154 |
| tw.webp | 31358f287b3c2c1c8ced8677b74187ee3017c570feac9d61fecd2996297a115a | 1224 |
| tz.webp | 90099f0ae5d6be4e02b8be8e9c5fe6e21cba24e6362de3f41430a01aff38d16e | 2918 |
| ua.webp | d24f95acbbf14ffeb2e2af43d9d1699ae95ffd5ba88fc06a1d3a89768bc37438 | 210 |
| ug.webp | 4ce927f35eb98380d47c2f1f66b889091811b43eec259ae5f0f9149384d00226 | 1142 |
| um.webp | 3e4d583d69d357edc3f8d7d809be22084f04248518298b816dfd9e953ae88958 | 3018 |
| us.webp | 3e4d583d69d357edc3f8d7d809be22084f04248518298b816dfd9e953ae88958 | 3018 |
| uy.webp | dd2201f34f42b929451c88e2e5200b043d57d1d9cd60766382ec19492f7d2917 | 1684 |
| uz.webp | 7932c2f913e77214b4d4c7d32a5b5c341137b9bd633048a602a7417021b85919 | 1258 |
| va.webp | dc783b17271c1c6e7d4761544b75186bbb72e3dc7eee5de40dc520b9e7ad1519 | 1438 |
| vc.webp | a511a111c755dffb2ef61eeb091f1a73fa057e7170fdc8377512d06b0b0ea834 | 1228 |
| ve.webp | 7894f81f103bc40c2603a8b43c7f5b787b2f047f3c4891052a3a3122070cc11f | 1052 |
| vg.webp | bb2ec8445191106137c5b68b176fe20c849c483296bd8aef3075e5011f4f8fb6 | 4384 |
| vi.webp | 194d58603d6dc32fa0c33305a239bf0846c52a97eb344f9fc17f283726d999fb | 5148 |
| vn.webp | 58f038e37b2767b42647456d9ce14352a0d10ac336b444ed4581e3dd015ec9b2 | 1014 |
| vu.webp | d382be8d8b527a2d20fe711b71c89b5dc9dab5f5ae357bfd1d29fcdfdcfd9893 | 2888 |
| wf.webp | 6554079403a4f42617e8b6992d39cf4711c3b3216b24a604c82e6886bc6445eb | 1036 |
| ws.webp | b4238085ac1388c8c7ddc1bdc2674d5fd6bf950f9dc1cdb0cf98d29da144ef4a | 968 |
| xk.webp | 18bea6b608550e0295fdaf1bf9de57872213197f8e77140d3661dd7a5b5ac5fc | 1834 |
| ye.webp | 28b6ce06c321c1d6651553a99802f5411dfed886cc7f1c15591afb9d5e5b4112 | 168 |
| yt.webp | 8392c576119b102573dd811f74fe3cfbcd8f93900a4b59fa5227a20d5386b2c6 | 210 |
| za.webp | 5828271159e7be8dbf3bae20a5eb994f95242325d30c91016bb3cfd8fd4800ee | 2420 |
| zm.webp | 11a52b9899aa3c80ec92f3e20c26d56a33370a0670ed179b7040430fb8f22fe0 | 808 |
| zw.webp | 110abf66bdb6336862fcf0ecc1daaf13522eb83f8ca72f408d556b7bd071e0d2 | 2418 |

## Map label glyphs — `glyphs/Noto Sans Regular/`

`0-255.pbf` and `256-511.pbf` are signed-distance-field glyph atlases (Mapbox/MapLibre
PBF format) for Noto Sans Regular, covering code points U+0000 to U+01FF. They are
bundled so the map's country-name `SymbolLayer` works with no glyph server. The
source TTF and the generator tool (fontnik, font-maker or similar) are not recorded
in the repo or its history, so the exact build is unknown; `OFL.txt` beside the files
gives the copyright as "The Noto Project Authors"
(<https://github.com/notofonts/latin-greek-cyrillic>). Licence: SIL Open Font License
1.1, which permits bundling and conversion; the licence text ships with the glyphs
and in `THIRD_PARTY_NOTICES.md`.

## Icons

`ic_settings` and `ic_tab_*` under `app/src/main/res/drawable/` are hand-drawn vector
paths, not copied from Material Icons or another library (the source comments say so).
The launcher and widget drawables are also project-made vectors.
