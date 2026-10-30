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

`flags/<iso2>.webp` (250 files, 773732 bytes in total, 320 px wide) are the 4:3 SVG flags of
[lipis/flag-icons](https://github.com/lipis/flag-icons) release v7.6.0, fetched from
`https://api.github.com/repos/lipis/flag-icons/tarball/v7.6.0` (tarball SHA-256
`e6718c0e6cb97362c33c086f8083e46d0e770d644cf046b3bbf8988408ef7250`), rasterised with
resvg and encoded with `cwebp -q 80`. The code list is read from `places.json`;
`xn` and `xs` have no upstream flag and the app falls back to a plain dark banner.
Licence: MIT (Copyright (c) 2013 Panayiotis Lipiridis), shipped as
`licenses/flag-icons-LICENSE.txt`.

| filename | sha256 | bytes |
|---|---|---|
| ad.webp | 9b14846aefb33826f49490e1c45735203a454f68e95ad49d0341ea73a5334dae | 3408 |
| ae.webp | e3d621d0b2ec0784965fde03aecbc7d165a8f2b0c77199d0dc04df6233b55e57 | 402 |
| af.webp | ede97ba837c1e12aef0860b294e042b4afb4e7277f2c0d4912fce79e98d433b2 | 4698 |
| ag.webp | a240921f3f9171f6f4638386d04372290455e126acfb2f083e6810b258738e0c | 3866 |
| ai.webp | 1444968a2c2fca0af099d33afa13471995a4008319429337cf95ff5e6490dda0 | 5462 |
| al.webp | 27ccdc32ccb4e02d8c08f5fd04aca82afeb02971d1971d685a3f67fc26b1c2fe | 4452 |
| am.webp | 0468aba6cc025fa0253128efd43d6b57c8be4ae29738a0ff732ecdc8b66ce56e | 328 |
| ao.webp | 970ee9c7428999d9ac245f2cf9070891e8b8a3ded49ce24be04d8a79dd524acc | 3322 |
| aq.webp | af50d710387ba79aa1235a40ac4d9cefc9128839bc4a370333d2c5ea36b961be | 2460 |
| ar.webp | 7a3755d8285f5a398bff891cad68563f0e734ae0de9faeba1d4c5b0780d492c0 | 2232 |
| as.webp | 87d099fcbe181c1bdf21e28d1437518a9f64e5c1f37ea3741382c0414afc347d | 7650 |
| at.webp | 7af22ea506ac58f296433e52de5d3524d4faf7d8c822833c7ddadff7ad9dd2f5 | 362 |
| au.webp | 954e16d89de6de568e4195eea5416b6b4fc42d3e5c1d2d841035642fea0e127b | 6190 |
| aw.webp | 4e15d444c8409b0a4c85e639d03bf74c64e0b37055b0a01e6437ae778b5e96e1 | 1772 |
| ax.webp | f9c7eed3fae3551de8b6a6173678c8d2828ebfec88914c5075af326bcffa87ac | 1028 |
| az.webp | e8a55d8d57ccf6fe4f2cc099b1ada569b329ef5eab1ae8d64c4d4e4e657439e0 | 1746 |
| ba.webp | 5f787008a7b2e2156c8955e8325a16ffadad54d17aa5168ebe60604165be035e | 5084 |
| bb.webp | d0bd04aaa3ed87af10e53a3446ef3150db1e9de47b496cdf290bb4dcea00a701 | 1862 |
| bd.webp | f123741c5b48aca5e92c8ceaba8e750aa68882904823ccac7df945636ac7fc95 | 1346 |
| be.webp | abdca1f004a0ae88c549000c9f3ccf4b74ab3ab67175ed8ca7f7fb786cce5635 | 386 |
| bf.webp | 2ebf52f69f0c82996fc00ff8d986c414ddde1af4f3e8b6a85259ca7e64b532e4 | 1268 |
| bg.webp | 2418efc74e4c9a161d9353ce095f71ffeabd6563d996b94731c592ca5750e115 | 364 |
| bh.webp | b2a60acf3a884519c3fadd05e9f56a1fefd643c52da7a9a01be9f107c42cab00 | 1944 |
| bi.webp | e2733309da0ba99c2b04a27223bbcc8da7e549aa20cfb5b45b55bedaffed486e | 5040 |
| bj.webp | a358ac07ff75cdcc297158cda2f7b7a44c348c7f9a8eae816c975fd13caafb7d | 428 |
| bl.webp | 5548bdbfb5f8a97fd15a9f109f9e83f1df928def6098beec4bc7fc8682a5a97b | 368 |
| bm.webp | 7eeea9863bbe099578955ee2736a331e5b7c009adc1b5a36effc215e214eb5fc | 6472 |
| bn.webp | d65e716306df4d1aed74e0cb792c1689dbb0b1869136a076c51cda65e6b050e5 | 6912 |
| bo.webp | 3df400348bf34f6dcca6b4edf44e6662a2ca9ad097f53d6c26874c4aa16fc02c | 2430 |
| bq.webp | 594d93aa7332d8583278970e1afc1599a75115634777ac65a1fab40a36a9b207 | 386 |
| br.webp | 0e15b9ee7e70a00fba704a928cf446bd0d139d5a6f0e97238fe753cafcf395e2 | 5768 |
| bs.webp | 9b15ab990ff9c2460157afa82b84e1f9a575357e49e128ec7ce1c352746150a5 | 2118 |
| bt.webp | 157d040c1850086dbbe2713b66ded2e17e64645b6574e0e284a74a2b6268b6c7 | 8426 |
| bv.webp | 07621c4861f6b3a5d4a7c7a5a085c82a30c77a26a55f226a2a800e9f3bd39be1 | 892 |
| bw.webp | aaee025fe2968cc92f076163011c510355634762934f0706f1ee2e76c9957772 | 446 |
| by.webp | 7ff6f94442470f7c05e1c9b112752a1f8f4d9e2e3354acbbf854d407acb4f8c1 | 5824 |
| bz.webp | c098f33081e116ed24a1e0d1671ea130772df35a35d02ab24cfc1c287b7d9ada | 11712 |
| ca.webp | 202ed9d53d8e21fc8e0ed0c89f318e889bb56c3bdfb585bb23267c268716ff77 | 2020 |
| cc.webp | 8927d459623fadc029928ae7bb705d9f05c09b1ec1c67014f8de766ec69bb100 | 3552 |
| cd.webp | ab8d1f69cdae20aaff8edc9f06de82042803c993c889b66c1f81156271df24c2 | 6130 |
| cf.webp | 9d7bbedc7d3350a1c10a59c959905f86e1686c7a98a01310f8192421f6b20d24 | 1630 |
| cg.webp | 188a1ae2adfae6935a59015140276f5648d5b6bec4381cbc5435c525d43b5e11 | 1984 |
| ch.webp | 8654191c72241208d7b09a926fad92d2b65c17c882ddcee4bd5dac4c69a64260 | 772 |
| ci.webp | 568abb990e01e6dfc86449da702314e806284fc22d2d1b6fae86daebe5d61f4f | 442 |
| ck.webp | 99e39e8c755304aaf0dde6e639712f95e09201c47c34fe908b0e1975923d6fbb | 6482 |
| cl.webp | bad4b9ca898fc41076f058bbfbb6789ff5c347438e70f7bf647db081234c8d0e | 1028 |
| cm.webp | ff2f66ab2745a12764628517cd4dcd28eca3887f69a5d2637c6511a315030892 | 1052 |
| cn.webp | 18d1294077850b8dee8c47c5e4dbb2cdf170c0b3039a2d57ff8067fb7908c9ae | 2006 |
| co.webp | 70ee0b39ffd3d047113ef61e3974cd838a355b9b36a5dc2d9efd79d71e2b641d | 372 |
| cr.webp | a7838da94ae0907fda63134fcdf0b54e23e59209d593fa89203822cd3ebf42e0 | 592 |
| cu.webp | 9b9766efde05088d101e62313b621644154a0ec1557e1ece1a4a5ad33ea720d8 | 2576 |
| cv.webp | 61c401da7ea1cc26d54f3998436743c7b7a937c1f83ac4fc83a5ebe95332ba83 | 3526 |
| cw.webp | 0dc8c185333474704f93e7fb40d1c0597abf02d5250a3550cea1bbe6870e7384 | 1314 |
| cx.webp | 6b923ab60673fee3b8ebea5a8fbc62e0befbbda86f00f8ee55d1739fffe4c457 | 3798 |
| cy.webp | 28dbad83e2cbe782f2d458f22f0e4ef3122a88d606b1591bba4281e4887b7c0a | 3506 |
| cz.webp | dc1345fb25abddc23c4f0da436badd70893f1e2ee7d9a74b1439ec4669c7d47d | 1404 |
| de.webp | 416f29d99f0bc45f17e65361699d5d27ca8d1c8b2efa4317147deeaad86d83c1 | 342 |
| dj.webp | ca988ef129e586b95ac8cefacd4fed1014fd801f49db03eeaf56229e2dd03237 | 2248 |
| dk.webp | 28cc0226cf3f04b0c3c5751be8c0926cc9144115b7676d148c99547b55c93b9e | 636 |
| dm.webp | d78aee481cbd9207a906418f9ea1d2648ab608c7f71f513c8cb598ca76dfe480 | 4594 |
| do.webp | 997b4ea52a6b6de7cc78ac028e30ccfb5e8be9d9d9bc1de83449cb28c20cc933 | 1894 |
| dz.webp | 13d82c0cbec8228a674cdc6c617569a84e7ce34c399fd3ff21e53c453279f804 | 1994 |
| ec.webp | 1738217e23a6fc2ca4f7d22f6d331a83ad50a65399d3af55058758660a6d24a4 | 7882 |
| ee.webp | 135785447aa078ab789212fe72d8e6aa4192e101a3e021065368fe3d62692591 | 304 |
| eg.webp | 0a6e3412a143921f62cb0d5a41efd3a32feed7667a394186f05b32d5de3fc094 | 1886 |
| eh.webp | da4c09942ee641d2a8d0d67a1fff590b8555c6804100d18229fd33e16e5db47a | 2422 |
| er.webp | d111a1e2513388126c7a00f05f8f97485e2daba78bd773613b7829cf0d8ca6fd | 6900 |
| es.webp | 8ce4c41e23ae721f9a5661047d6cb392cc9872b514fe639477dfdc2a318ab668 | 3368 |
| et.webp | e1e3b3b024435fc36594f11440f3ec5c21b0b06732e495a17eb463d6c6d46784 | 3342 |
| fi.webp | 1b9be11eae0ca80ecbf947189b4385fb4ae29bfdf4f0426719c8fec32097a3f1 | 522 |
| fj.webp | 7dc6847f9e6458da29e2ab68f152d793ec06273eca026f7c0e81595ed1298007 | 6552 |
| fk.webp | b772e606e69a5432dfac3a131b752f7fd6bb493d6633bcbb04a4662e6e591eb7 | 8348 |
| fm.webp | d14989ed6be7e7aec45c6f267433e622158de93a1b3002ac2fc481544e187234 | 1728 |
| fo.webp | 8f8215e6c7742af4bfec22a7cbecc019755670113c07149fcdaa3f8367753973 | 998 |
| fr.webp | 3310950f7c656d297695a8aea947996c09560c7cec5301b48f68b533dbc7b8b3 | 384 |
| ga.webp | 963fbd3b91c2891033f5a56d985f1a54b419bbd301782d9081ec6347ec05c3af | 352 |
| gb.webp | 9db49926b09f14b46e55e6391d6cd9e076969e55486e3aa26cf164f12ccd6484 | 6096 |
| gd.webp | 25c7e7d46ee5319c7637546e5e36a48471624b977a44cb2f353444b6a0856d41 | 5174 |
| ge.webp | 686e075c4e4f18ffbf097aba145e8b82d1c0713822afa14a13f19cac32234d10 | 2390 |
| gf.webp | 5548bdbfb5f8a97fd15a9f109f9e83f1df928def6098beec4bc7fc8682a5a97b | 368 |
| gg.webp | 1c96063c02f81c83d192fe54e7f979f52b51b81f6375962fecd78978455045e8 | 1382 |
| gh.webp | a1ec0f26697f7bbf4c911df735c161ddf5f7138fdded13360e1b4d9a4952dee5 | 1430 |
| gi.webp | 5ca6a0aac5bab909282232d9b3c86d311c2051e5f95053211418130475343bd7 | 4500 |
| gl.webp | d591aff2bbb59f6b0e8004d11e5e37bf9d316da5d34c924c84debd16a439dce5 | 1680 |
| gm.webp | af327c1879b0392bbe633c036155a13125eef01421089d7bde26559b2e377137 | 586 |
| gn.webp | b2e0ec6f98ebdb75fc355e41d92a78288c6d1d1353aa7291191eb1af0bc327b3 | 438 |
| gp.webp | 5548bdbfb5f8a97fd15a9f109f9e83f1df928def6098beec4bc7fc8682a5a97b | 368 |
| gq.webp | b784cb41e68815c546951f2b605622a4f1dadae644a9edb35f54c7ad83789a11 | 2566 |
| gr.webp | a0e042af34e2bcb55fa4261595e8116556e905c80d314c94a667380031eb80f9 | 1080 |
| gs.webp | 90b3c707fd03cb2fa69c732350d06d181d96c1b11ed132262d4b6b6d33c3932d | 9962 |
| gt.webp | d42d0986fe14f24e00204481fd645c8e867ca80cfe4fc0f3af08924a841bebfa | 2716 |
| gu.webp | 73c3d7ba8a9c6320d0e9cf1fabd72a2c58bf9f6651a169db9020b9c5a4b294cc | 4476 |
| gw.webp | a19a74d91406fbd33e6c79c1057c3e44f403d838d1ddc6ad7950255a0bd47122 | 1012 |
| gy.webp | 518c3f40df435acf70da6e9a9f54f3258406501ec0e1a610bf6676f1ba808b2a | 5278 |
| hk.webp | d5937ebf58a6c62e60714f3d4f87309d6cbbbe2acfaf5cf03ad1b337ba66cfc1 | 3696 |
| hm.webp | 954e16d89de6de568e4195eea5416b6b4fc42d3e5c1d2d841035642fea0e127b | 6190 |
| hn.webp | 7537dcde34f7aa36584697c15e04a9c2df2f47f0de18850a06ae6736075db30e | 1646 |
| hr.webp | 470b5b6af7796e759c008943f8b9fc777497951c16b7a328ca52caccd7f1287e | 4276 |
| ht.webp | 4b324ac07dd4a5c705d1d00ed4ce2efece666beda3590602b47ea4235ed8ff0f | 2480 |
| hu.webp | 3f4f661175e9d947d70f3c46b405753276c1740bfbc81192bb2c957b86664add | 356 |
| id.webp | 0f77a8f54757611a5295f89341c8271a6fbae3d458d9f1abf273daa7dab406dd | 278 |
| ie.webp | b48e6a926cf6772c57267b3724ba03b449d3f50f6f713595049ef46af4c5dcf2 | 348 |
| il.webp | 095b26bc8b45f9abd06ca008fcebaff7c741740221b6a9bba4bb2a9c773fe9ed | 2752 |
| im.webp | c111324851a3c31399d3566f31e2be7c3fba6ac57f0dc950cd5506e3a717b755 | 5688 |
| in.webp | 185a8b91d0e24f81beafdd93291c3bc57f3be6a261bc8860c44d38380600b371 | 2508 |
| io.webp | 5408faf02fee263753c52da630efb15fbf7e8fdd6bbf0ec890ef598649cf2c06 | 16458 |
| iq.webp | 65ad6900e5cb1a485fbef2888dee7002b71ab3a0769f6041ff21cee7594d345d | 1522 |
| ir.webp | 1155438137accd6a732096ef3a280b7eafbece87d0bc99560d00372bb61eacd9 | 7450 |
| is.webp | c263801e32012539ed27c723e39959634f46f885514525222d397a71a426cae2 | 928 |
| it.webp | 87f282decd8655062e4a6c6260ffc94c4c28c017f4f372b87c16b34f2f4a5804 | 356 |
| je.webp | 94593d479ea13d32c84d2d5a1004474ff8a4be9339471b55bcdc8ae089135104 | 6912 |
| jm.webp | 1bfe24dfd38539024e54ea774eded1b676c431596db8faf7288c3fe77b2f03d7 | 4828 |
| jo.webp | 6116fb68dafee9f6cd338df18cef4c1dae8dcf351db0b666c0583ac82c03fd45 | 2098 |
| jp.webp | 476f857dfd5b87f994de3ab5c1d720146d5549be70a6ac2b1051d0060ff21ab9 | 1540 |
| ke.webp | 6e671e465b480f58dfc2ffc3f9a7e48d92376e2ea2e25ec57d33bff71f8f0b5b | 3076 |
| kg.webp | 019717e678eca274e61b03a8b909c7f2046ca107c3d6220d8fb88d68f31793b4 | 8122 |
| kh.webp | fd7df08a3e0be9c6ac010554ed168c6b670a861891aca9fe7c1e58cdee857f9d | 4140 |
| ki.webp | 142d7b9014b141a20e00e57f3126253284fd7ded5f985960b119caa5cf27b518 | 7424 |
| km.webp | 2f87b19c471f407be40c0bd795bf03d6c2535db8e510dcaf7818673d73e93bdb | 2840 |
| kn.webp | 89a74b22cac5609bdc4ab234e4bcadabcfb0f7dbcb4a6f26ee768c062e038221 | 5866 |
| kp.webp | 85b50babe9022d89af9f1986e0db21e6f1e71d50ef4a59ffa3c145ebd24404b8 | 2636 |
| kr.webp | 8e3c2308343b8c05a9f7cf7c59aa5576c6c1ae85c7168f1b1ae606698273ae7e | 5626 |
| kw.webp | 9781eedecf87c001c7ac0cc92299399419cddbf686f0db549353817ea9741a3e | 1250 |
| ky.webp | a04d5ffdef4d3f7e8ac08a40e6ed2963a2d3d6a2f3b8ba00f319ceeffe3149cd | 10264 |
| kz.webp | 89bebbf73483be720a0929a225a7faa0b1c83c04bde9f0fbf8af3f8c545b5481 | 8724 |
| la.webp | 3529bd9c516cc97fb2821e897e86a80592e37e6eb573d749f66a5613d6c39b10 | 1314 |
| lb.webp | 270892b1bd78415e1168b0957e99b54ad0602399f1f6f292fe0d06f89602fce0 | 3270 |
| lc.webp | 2952b97de2bb6faeffbc9660d781790712454c8d0d828dd03e9404edbfa476cc | 2744 |
| li.webp | 6a7f15ca7818607a070e02aa52284f7930eaf65a23ff91c14188aa8919c1ea56 | 2580 |
| lk.webp | 386b83cc71c2e9787e4bd038b47cf7e926fb6acc7acca1f1e8e93122b9ad6de0 | 5550 |
| lr.webp | 8cacf570835494ea42505cfad715ddc73c0b52127caf81215f610ba9df5c41d7 | 1788 |
| ls.webp | 4c72de7c71a404e2a86fc8e6a6bb210c20cb6e648fb06dd0eede3fb4c17ab33d | 1592 |
| lt.webp | e81276e47d8f04bc7764b7a039e7262b3ade0a86ff66f8dfa7d9f73110744d92 | 420 |
| lu.webp | 32de233d3330d9cecc6f94dbe1030630824a5f5ebcb1907176d9a73e909b3c28 | 366 |
| lv.webp | 7673d4a5d8e4019943f6afb742fa7409cd19ffc50449e8d4225a4dee2f527dc0 | 326 |
| ly.webp | be79dcf4e6029cf5b48bca7e9ac28ba0a0cb175c2aa193e303e88bae1a3d25b3 | 1274 |
| ma.webp | d65a9c5923d83dbc9cfbcc4f0fcbc6f5e50bae858e1235df6fe6b8442a6350dd | 882 |
| mc.webp | 48b0b46aebb8e2ff64926a2438bf5afa8027b3dee67811e0dc7fc718d9f9eaed | 296 |
| md.webp | 3be50539aba452a7884bfeeac28dd49be35740afbee9ee522605a1daeda11047 | 6350 |
| me.webp | 607edb90b8bf22361e24f361d7b3a3a622b3aa276aedd059e1d6e65770ebe0c9 | 5662 |
| mf.webp | 5548bdbfb5f8a97fd15a9f109f9e83f1df928def6098beec4bc7fc8682a5a97b | 368 |
| mg.webp | fbeede7dbb8aa4c779d3d32008b851307850589df25ff19c4aa805626748f473 | 390 |
| mh.webp | ab35dddf4349735530e96d1ddb61e76c2cd8fa2ff5d4dd6e4f4acce13243e609 | 6852 |
| mk.webp | ccaa9a2d2ddb59b0ffd4512d5801dc0c655c1830fe2da2af3ea9ade1415009a9 | 6986 |
| ml.webp | 377eda9008f9559b910391c005fe47fbb009f0733bedd4888599c5871f499334 | 456 |
| mm.webp | d1b5684479b09411da6d47fc2bf06ef2252adecbedf0431616a931a41c9d0445 | 2072 |
| mn.webp | 1aba40da157e08e9461fb958c1368a0c1da0b19ba62609865c835ebdc028571c | 2372 |
| mo.webp | 8743b2a020b9329fcd64593cd5758e9311e6f3bd7a9b623fb5d9df5e48d3c21c | 3528 |
| mp.webp | 9c91f7ad13c26834b75c5ac098f73fc2034771922d225713195a24c7f072149e | 11140 |
| mq.webp | b5b6b124d839690a668d13e99755e86f490de61cea81a457e9639002b333e550 | 1222 |
| mr.webp | 2516bf3d30f9571eea417b2c27c34f2a7ebce49318a634303dfb69a60f0fe466 | 2578 |
| ms.webp | 431c65be7a17e2853443cc1e6ae1ecbb0dce1bd5c413a3f93e16585a05ad3043 | 5660 |
| mt.webp | 71c5fd0aa30edbcfc8f585528bbfc183442429c636c335f267db81b6eda4e504 | 2286 |
| mu.webp | 795ebca293b577ecf5971b29ff89d1058f299e5f27ce80e1cfc65992f7528749 | 424 |
| mv.webp | cafcbeb1b1dadb3399041a825baaf329ef1aa1e6f05dde6618eae8bc228693b4 | 1006 |
| mw.webp | a2fef5bbf6657310156b5fd8407d41b15bee46a8b47aee44f718948acb7b6fbd | 3552 |
| mx.webp | 5da5614ca6e9f437e78185a33e41a37a0a8b34ab933d480ffbf78206617a5d3e | 2654 |
| my.webp | e85397a82d24d58950995cecf4df2f855719472f95fc14da516b8ae87890603d | 3414 |
| mz.webp | 1f1ad932e02ed069509030e6e3445a20520797b40d32817502ecb256005eaf51 | 3372 |
| na.webp | 8a8cd4957f11272ce06b164eaf45943f6e6a8204b9480ef782925808283eff5e | 6086 |
| nc.webp | b12841f5f204d7817f59f280cc5c17dd8201defebebd328a6ea8a599d9e62dc5 | 3690 |
| ne.webp | 38aedda4e2800dd18667de7483be3adfbefc3573ae16b2359901964338262903 | 1034 |
| nf.webp | 24ae1107a290651e8b36fcaf0ad5636b47c2db3bd3409916577814834342b984 | 4206 |
| ng.webp | 9343a8d7801f0b05890c8d00404804f2ebc855344ed9cd79f3afa5654e505d9d | 344 |
| ni.webp | 1ea84caac4da70dcde731b186f815d77ac80725bc3024a36b22163a8f6a3cf1c | 1872 |
| nl.webp | 594d93aa7332d8583278970e1afc1599a75115634777ac65a1fab40a36a9b207 | 386 |
| no.webp | 4a3f57a827ce3f1ee425f6304bb165723e74a9c7ac0790749e6d054f20a04d43 | 986 |
| np.webp | bb94bd3f1894a94fd06e07af5a3f3adfc3c10bf7bd75f022e61345c9b50b2746 | 4188 |
| nr.webp | 60e1bb63ca20b9fc29dea70da871b45ec1539e30a53e1d4e839294cdc9c180cf | 1802 |
| nu.webp | af7ea302225b567c50be7b2c3126225426c3fd22332ab156e76f087cee5e2c8f | 4890 |
| nz.webp | b027a1d3f324d872968ebcb76454ade4b36c441ecf4e50bc1d579b3b462b1ad3 | 4826 |
| om.webp | d1ce63c49b3960cb1e3ba8b2d409c57454737107866e43eaaf57ec00fc89c7d5 | 1662 |
| pa.webp | 94ba328cdda9031a9aa134743038c7717cc54a4e7d180e9a423069e0b2349c63 | 1930 |
| pe.webp | 9ddfca06f0487412d195caf0b05ed7ccbf44ca6f461811f4f71bc5cc291a0c21 | 364 |
| pf.webp | eb04b6c9771b666e4e89b123b03701f225804b17b285b9270b4888cc4da9e52d | 4642 |
| pg.webp | 6896b0817713c6156d93f2eb34802289e87d346d2596cd66e93000a06fd97a04 | 4644 |
| ph.webp | 9f244e0aad76a39662b49bafb6c1b0c459f11c83da3e2f7ac98ed8a4943ad859 | 4102 |
| pk.webp | c954d74e356a6ba83402c324f12ac10b4bc6aa57fbb585288c9a3a041500cc02 | 1666 |
| pl.webp | 1e5a215e9a637b9c5784b19238b2f258085a2fbb7f4233b75a3541a61d417f1c | 288 |
| pm.webp | 5548bdbfb5f8a97fd15a9f109f9e83f1df928def6098beec4bc7fc8682a5a97b | 368 |
| pn.webp | 3234d4f23e018444442af7cc03b7d87b69496d1ce808f234cea8204e51ee68c4 | 8636 |
| pr.webp | 9faaa0b576702cecf2318677172302cd2387d1c430be40335a319ab9dc6afd92 | 2592 |
| ps.webp | d9318dbbae83ad068e9bce522415bee37c5715f2f03262c1524acfe9946f8a7f | 1426 |
| pt.webp | f8f69aafaf81163a95170a57f0c4ea8a69f21c7464a19f58bb0863b347de5b0c | 4526 |
| pw.webp | bcc21008dc1849a24d7f33397300055a40fda8cbd3622cdaf4192a5afba91036 | 1480 |
| py.webp | 945777e6cc588662627907deec6be43c8a8dc10a8647d9a6427cf4e671a8a4a3 | 1984 |
| qa.webp | 6a859df84430ca101014df01e2c988976c5a897d7b477f4c08a7bbfe2c5e055e | 2082 |
| re.webp | 5548bdbfb5f8a97fd15a9f109f9e83f1df928def6098beec4bc7fc8682a5a97b | 368 |
| ro.webp | e85e35f2c97aa73b1242deca6157f8dfd7f1aae7bf0e54159f16ed7dfd774333 | 442 |
| rs.webp | cf96860eddc28a19278dc61421a4150afd959daeb11dca1e2afb84bc31a7e320 | 6260 |
| ru.webp | edd38b011c2738ea616c9c3b4bf112a996e791c25429a4db5499a773964032a7 | 280 |
| rw.webp | 65f2e13ce216d80a85a2ed4bdb6b68aa473ee39f10100b62656f51e30ef9a59e | 2118 |
| sa.webp | 86251eb8ca133c2148b35f62e2f00a1deb61449d605b17ae741e8635c6950034 | 5610 |
| sb.webp | 33d1cbac2a325f7ce99f94a4c6fe869d273f6383b3be005a30ee37b1f63d9df8 | 5138 |
| sc.webp | f73cd0fb50b49013afd9da43b32e2a801220e5265c23b13ab20b9818dfe5e631 | 3932 |
| sd.webp | b7f5d8bc83bed11006c391a71abbd0e5b5272ec5923203a579adf59c4904a7e4 | 1556 |
| se.webp | eb78da9ee23578fbb802706270e5f01d6328e84487e716a68c4a9dc7d9db34fc | 364 |
| sg.webp | 59aa3bf0e8df0afe3455a247b5c5eb403df2a45d43478c599eaba4582f7e7dd6 | 2254 |
| sh.webp | 9db49926b09f14b46e55e6391d6cd9e076969e55486e3aa26cf164f12ccd6484 | 6096 |
| si.webp | 18f0a2fa1c438738a138d1ce94d27221cf7fa1f72ec6df07c2e49b87540e239f | 2274 |
| sj.webp | 412618750b9169ba5f71138e526cb197476c2d8795eab3476588ccaba8928ece | 978 |
| sk.webp | aaa8f044fc6ed76e0d8e2d2c8b55d143c3c8494d1650564af8f8c8e336745c03 | 2552 |
| sl.webp | fab280daa095e02c7783aaa7239b838275fe47411117e39b9896afad5b2634a0 | 454 |
| sm.webp | 37965fdfe7a0f15938559716811f80e8df5b0b32c1f09c6dc5ef65aa9b4fa094 | 6502 |
| sn.webp | a071d6ea2d95f7c568cda728e1e477228fdc3c43be39b2ed4bb2b8f2b5a42aff | 1574 |
| so.webp | eae077190eb969362360c6b159238f0c60a4f991efd25619cb8e19df288f9140 | 1302 |
| sr.webp | 693d2fa8676543c7a10c1e0a8aceefe577b81e9d987a9d8e2fdd6999105b32e9 | 1466 |
| ss.webp | 073c101dfdbd10d53d5448522445581c5b7789e7205adf6428ff17b6da2427bd | 2322 |
| st.webp | cf6d344c4126addf9243664b0f4214c48f873b2c3af16f80f3d5da2cc43ce02f | 2742 |
| sv.webp | e2e39b807371cafa17251937fb650f6a541b06c081f2f5c64ed057eca7d9b718 | 2694 |
| sx.webp | 386a89eb3674bd30ed0b1dba221fecb0ae329b29117b44b1153426cecac7d044 | 5032 |
| sy.webp | 58ea4efca914bbbe8357ebd904599e0c5716cffeb7ab17d7051eb7e3148050b3 | 2316 |
| sz.webp | ed7683874e728f77c1f70c537f2bc79c40d98889c3824d350728f23e8c227d75 | 4540 |
| tc.webp | 1cc0d4f15c80ff02237e7be29585b19790ba94ee67af03702a1e2d7b15dbf269 | 6526 |
| td.webp | 0479f26aa13d378c386b3efe7ecb730252ea3ed2445ec4afce4c1eac7abb50fd | 362 |
| tf.webp | 4f1b2fea4a0e7d1a980461c191eb503c9710d00c89dce9b65ec7b3426ccb4a19 | 2900 |
| tg.webp | 55afe03655257f1b1de6d98cc42cbf6759fa211255d7dc30d3a0b0fa3acec14d | 1590 |
| th.webp | 2c9a43829e7af9bf10db693903d6cd308e16281274045adcc7ba84740e2fe3fa | 488 |
| tj.webp | d0fc2118829c0fdaed0a2d014c0f77094c4ff8827c490978d1d29b99c56fea6b | 2474 |
| tk.webp | a38764348497530665e789d6d574859be7d73f0f341d7e63db6d32612cf7ed48 | 3190 |
| tl.webp | b6e788c216b76a446de75444219d010979b588295b458cef6fcbe5a1a6a7c346 | 3570 |
| tm.webp | ce12b99df64aa1a2936c53350f956b48cfb0e45e15b519e9c8e6b39a1a9fa544 | 8280 |
| tn.webp | df08e04e92b0e6f966b91aff72184e8384a2a920eb266e420fc1febfc67e44bb | 3064 |
| to.webp | 025c026ce866c7e0b5a932f7ca3df43700d71c2a03ca5d466b9f81add0611494 | 728 |
| tr.webp | 4a0d528a9d34d33f6f727e482c13960a638bf735bccc4533d3a2d8be73d6bc9c | 2490 |
| tt.webp | 412304439c53df0b6c22818b6b4c49ad50f5f3a45fa0e120e8b42edd075cea79 | 3496 |
| tv.webp | e1a6bb7478f0e8b6182ea634ef755627fd813355252175ebb13176c6d3090e1d | 6764 |
| tw.webp | 07bad61ee26b1f0bb6f2c4cd3c1c6ed251aa67d49df3d55e7b67ff6bd8869ee7 | 2306 |
| tz.webp | 345b00cfee636ab753ab3fa5ee8973b37705d06319776acb1255db96ab148557 | 5154 |
| ua.webp | 70da6cd7dd86ce9b0e48e0a4362b18107ec2b338440ffd449218a695542b6902 | 264 |
| ug.webp | 16c9a763715ede033ba2034e1438e5decd1cab4d8b860039623640c36dc7888c | 2114 |
| um.webp | dfbe2d09d60f625e64ff76f786c01e0abdc65136cc845a26d259cce67c749309 | 6548 |
| us.webp | dfbe2d09d60f625e64ff76f786c01e0abdc65136cc845a26d259cce67c749309 | 6548 |
| uy.webp | d18ced1f12f0a2be6042407c7e3e3c2f6cc8e59a16768a5bd1051e89f732fe3a | 4182 |
| uz.webp | 5e999fa74fc11a4b970996bc674da14c190918bf4067d545cafa914410fe2632 | 2184 |
| va.webp | 8f2ac5a8e0e830270448fa88b682609a2c5bf4bdcd7d266d4f9076917ff059d2 | 3724 |
| vc.webp | 22616b845a5555fe116fd4da7c58460071331e7eb9df39e26be307d486866d8c | 1862 |
| ve.webp | 366dda8c9d7a12541e7fe6fa05d2eb77eef93b604a8bca48eb7a3555d12d358c | 1968 |
| vg.webp | c0c975d68667de31d6a26ebd9fbe7a66e8f4b9f68d7bd652579d45eb8c3dbce0 | 8684 |
| vi.webp | f7d3bb7ee3414b305f1f1aa55d6a6fc688f705b820b999b5cb4107c5c4e2b36e | 10972 |
| vn.webp | 5a525928d5a8067fc1b12ce65697734281032301f884dc7793ca66426e7cb222 | 1792 |
| vu.webp | b010820bddbe4c3d587be75b80b715aac2d01861792867b3827e36b6d521c713 | 5224 |
| wf.webp | 5548bdbfb5f8a97fd15a9f109f9e83f1df928def6098beec4bc7fc8682a5a97b | 368 |
| ws.webp | 2b1c5b4346fe4dffce7957296d59e8922ce6ea239de742e47658c7e3550bfcda | 1698 |
| xk.webp | f473d42a9d449c60af81c30a21705a7a16aed89b74138c9984c9f1fd62fc7ae1 | 3236 |
| ye.webp | 82ad0c896b48af12481afcbedd068054452ced177dbdbcc2c80bb420661af138 | 324 |
| yt.webp | 5548bdbfb5f8a97fd15a9f109f9e83f1df928def6098beec4bc7fc8682a5a97b | 368 |
| za.webp | 34f981cb34ac099925e253c0ffa1dee6181136ab8f5210ef5a05afd5896717c3 | 5312 |
| zm.webp | 58bc9533b4605d4dafc8b754c1665cfbb7f00064f95e76d1ecbeadc2205207f6 | 2226 |
| zw.webp | 7d40bfe140b42b06437008fe7bc4beac525ea041788d469371369242b792a95d | 4622 |

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
