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
| places_labels.geojson | 3690485a7567e9807f2da30f16f0b320f0846b82be0e552fe344efb1ad51ea4b | 39801 | same |

## Visa requirements — `tools/visa/build_visa.py`

Built from the "Passport Index Data" fork at
`https://github.com/imorte/passport-index-data` (file
`passport-index-tidy-iso2.csv`, MIT licence), as of that fork's README date
2026-02-17 (recorded in `tools/visa/rules.py`, `DATASET["as_of"]`, deliberately not
the file's mtime since that changes on every clone). `visa.json` is the DE
(default) passport; each `visa_<iso2>.json` is the same build for that passport
code. The DE build additionally folds in the German Foreign Office's travel-warning
open data (`https://www.auswaertiges-amt.de/opendata/travelwarning`). On top of
both feeds, `tools/visa/rules.py` applies hand-written corrections and curated
entries (see that file for individual citations) — the exact commit of the
upstream CSV used for the currently-committed assets is not recorded, so, as with
the geo assets, only the upstream URL and the as-of date are traceable, not an
exact snapshot.

| filename | sha256 | bytes |
|---|---|---|
| visa.json | 3b73676ae20cca7d01b0df9cef5a0c8428ef21428b4342ed7386278ea9d56d10 | 45909 |
| visa_ad.json | 18dfa004a77050592bb72e190a00de5e0124d73a7351d481b6ea8bf31d7d2622 | 25384 |
| visa_ae.json | e79d49d173721a6ce27672947a9e2ece9434f0fec8daf97574f2b12bf99f6355 | 25638 |
| visa_af.json | 8a337659daa835ef692887e21ab64f0df733fc033fe5e511b2334e9341fe5b9e | 24698 |
| visa_ag.json | 60e5a56b6551cfc20b26dcc8a8a1444262f3881f2b0520082022e6e972227408 | 25371 |
| visa_al.json | 79d4eea8d4cd907f88b0e35c38b9f42760da42a7ebc93768dff48ce906c354ae | 25287 |
| visa_am.json | e7044e3fea61ae3f9c66e106025b1802047344220ed817a43f04dd0a15607b63 | 24967 |
| visa_ao.json | caad8cd8ba3aa4c8d84e8aad2b3052e2d46959d8df8187fa2c7031442ff81594 | 24856 |
| visa_ar.json | b8de3784fe80be5e34ba6bf6b69501017db3f961fe83aafd2a60459b0431f941 | 25517 |
| visa_at.json | 0923cb0415fb65373c26ade0f46ea849f937b2661a0ed6d5d3e5e9de5735e6f1 | 26441 |
| visa_au.json | a14fd3c8952130db563f7daf83fa05e9dc3578fd16e52c06f25ca7d0255faf76 | 25475 |
| visa_az.json | 04ede81aa1556a6cf50e1c1379df568b1cc944388fe42b3f88d914800ec80ce2 | 24993 |
| visa_ba.json | 5032505e68981d21a51b8d6357d75db82dcc320eed8bd0e7674479d486a00b49 | 25298 |
| visa_bb.json | 47938e0f44bc28b8c56a2f2a6c52765207bd62d54e5c69c2477cabefa6fda27c | 25436 |
| visa_bd.json | 5d57f73d8d59a0996c29b97eb105d3bc8f8c1edc52fdd3cf3c8b9656abdd23b7 | 24771 |
| visa_be.json | e94fa02a4b1dbf41337ad953f8e1af55d776be328bf96815beb6e4cbbbb77171 | 26474 |
| visa_bf.json | 01bc600bb7f67846d8652c073f1272aa7e00c0e0130ea2c44484406baf6ff588 | 24851 |
| visa_bg.json | b5629d76f6d5075caedfefd540c73e7d18feca94e308a1df844069c84cae68df | 26491 |
| visa_bh.json | 19310ce058dc0a785f51d99c040d29b150565addb2954c0eff8c9f6df3c98add | 25038 |
| visa_bi.json | 4979af47a8b14d3b78ca15ffcb23a2eb9ec53346ca945c71cff468b324903c89 | 24864 |
| visa_bj.json | 2035cc54d98d00c0731ee41164a8d7ff0a5b1c18f7c6aa4ab5e95c36e086856f | 24900 |
| visa_bn.json | 9da859cb23b9cb1ca32ecea3d11ea84dd2cae1063482ec944d857fc547a72894 | 25372 |
| visa_bo.json | cac8733fb1ddb20ac7c6b9d41c3df9210d43699e874fe6cf12a6ff289104708a | 24955 |
| visa_br.json | b0b93524cc0e5475644235516e67bf56eb260e7c8688ae174cfa817a8a0ad1ba | 25538 |
| visa_bs.json | 2263457d10cc13598a6a076d6b05a978a032bc6fd4d0bf20f51c9c57f78c17de | 25436 |
| visa_bt.json | c129c3826b7ec78ee9025fdb59d213665cbec0d067dce1f3e32c468f756dd54a | 24805 |
| visa_bw.json | b175169ac4c7a9c7c566c7ccb1981654012d94787352f7b980b9aca741fd9975 | 25019 |
| visa_by.json | 1ffe73f9a8423de15cc0a28f40bb25f85e7d64a3aedf3053e9614b107ab23d66 | 25050 |
| visa_bz.json | 02ec7d53b05fa6542238e94e3e715e2b04c8e47bcac24258b6f398152eb6f5cf | 25081 |
| visa_ca.json | e39b1e3e987ac701ccb35c6d1c5b966a2aa0dfa2ddd7c45089530503ced88535 | 25564 |
| visa_cd.json | b061abc048f9b83979920549106570f38936abb8e898c3876471014f7939bfb3 | 24846 |
| visa_cf.json | b6b65884fb51116f19a4a6824d7227edf3258d37b8342cc8302d8a703c601ac2 | 24881 |
| visa_cg.json | 27e5134121c85a25951c3be849fcc18cfd3730a4c0c120c2aa2361edb5eda45b | 24866 |
| visa_ch.json | ecafa5ceb65ced9ecd143dadbcd882049deb80f01c9633740b321de244fb8c72 | 25080 |
| visa_ci.json | f3c724555af842ae32f4b32848cc3b7f69e67a95279f3ffea8ba874b89491fd2 | 24833 |
| visa_cl.json | 740414658c5df0fb99f3353439664f79ad10510ca10abacb83c7a80b33b2d9be | 25445 |
| visa_cm.json | 66512852fa01fae0dc6e48da6522540f26bcd103a6488bad5e1ef451f58963ac | 24889 |
| visa_cn.json | d47363ce082d65884b5f5392c2310147439f1ba639e1317685f69a6932cccb0e | 25094 |
| visa_co.json | c0c04a3db60a4c4b059d96d796d4656ea5c3611335f1fdc21519b1a2415a6b55 | 25344 |
| visa_cr.json | 05cff17a7114c871f92ff713190c5a333bb79f4e57f39944925c998cf6f5fff6 | 25347 |
| visa_cu.json | 6c3f9a173b6874587168145135c999554b5ab7252485fe7c7031ea3bd824948a | 24896 |
| visa_cv.json | 0732f5456bcefb3cf0755251f8d313d56c32d6140fa9b15ee6dab47504d01f6b | 24927 |
| visa_cy.json | dcf9bd339ffcde0f3d4a9da191ca72958d39b9c96a2bb135027db948ef8479fe | 26490 |
| visa_cz.json | df6a8550eac8efac7ec7d9d1e3bd5722c95aaeed3661d5815bcc161b819360fa | 26435 |
| visa_dj.json | df9c70c9e426c021ca8ef28dbe3d36f6172e534e0639e6b1352de40cac7da406 | 24829 |
| visa_dk.json | e94cc8af4a51529ea10a14f096bcd043f75aee9b600b2d7750a751defdacf9f6 | 26449 |
| visa_dm.json | 1d57c67f24ad23de1cba8f8786485fa26fbd2d1adf9efc9b071a07647c1ef20a | 25324 |
| visa_do.json | 0d6d13f4062f4cac49f3a1aeb15fae1a2b91e97e7bfb5b1ecd1d32f0df57f4b4 | 24950 |
| visa_dz.json | dc06bbdec6eefff6c75ffadbb7ced267ae9cf6d405f3085c3be785fdef954a39 | 24832 |
| visa_ec.json | 136e54af17c94c4c720c72347d6d5c8d13bf55e804fac99beeb70d6d590c9197 | 25074 |
| visa_ee.json | cf071b2ecf50cf559bda474c34b47b6d590b3568053904f406a30264e4f17a71 | 26418 |
| visa_eg.json | 94b8b50b55fef3a3dea6663f0a09056ffb3848f2d79175bd4bec81eaa2f6db8a | 24830 |
| visa_er.json | a76f9bdacaab16912a3f3d915b4fac30e3ab23b0910a0983e6f0c10ed8a3ba89 | 24770 |
| visa_es.json | 45dde3eef8906fde9aca7c5637f1960093e5eaa8ac02987b948a7c92065b84b9 | 26464 |
| visa_et.json | 741cf0ca07b640002ba72e660c2dedde015a38b1a1947f5afb1b4170d6a84d6c | 24810 |
| visa_fi.json | d665c2739bd5b01c46b6feb09d6a6d0b2d38c5c5241e16dadf0f7de60a98cefb | 26462 |
| visa_fj.json | e271caf03a14d6863548f2567f493b41ddc5eea8542cb7fe2b39f758772c7bbe | 25031 |
| visa_fm.json | 67fe0f64f099fac798abacb377d87531076ae628db99768207f866a77ddb2614 | 25148 |
| visa_fr.json | 88526dbda0eee6b26930ed6a3e7e60ecc0c709e45fd5d3ae8fd3513dbfdf7e0a | 26443 |
| visa_ga.json | 2a22d0bdcb3b902acc9feccafbc2ab7f135160e60dec032d8b9b59a8f8f0cf01 | 24933 |
| visa_gb.json | a3d0ba97342301c9d849afc8a16fb2b3383b89db271cf2144e55151c6457407a | 24978 |
| visa_gd.json | 9e82a68a64c11b47a8ead80850f9465ea6a4ba6696566398fe883abbb8637fdc | 25344 |
| visa_ge.json | 9d2b3b64dfe5ace9c6cc26ac50e1d5bc510a586ba5446759a7ee09651f02116b | 25362 |
| visa_gh.json | 8297c801cab294da1f803be485d051850a1ad9bdc65899f15c9af1ed31d48017 | 24915 |
| visa_gm.json | eed564f2aedbbf72cbbf0ff9b6d162c9bcadf24a0c4a4f72a201e90d70118d0a | 24921 |
| visa_gn.json | 91a580ea2e5ad1eeab02af88b9ce558959dad20adde293a1c48fd0100fdca8f0 | 24834 |
| visa_gq.json | 2a688fa013d31285dc7feaaf81504a686062a854743867f9cf30fe2db803684d | 24909 |
| visa_gr.json | dfc3de284825d152979ac9583549632b2e3a1a571a677a70107b73472a97b9ba | 26447 |
| visa_gt.json | b364d4b92793905160c9871d9e2f2608f94960c6cb2247d00d3f3a4a4731f600 | 25219 |
| visa_gw.json | b53404d071ace4d7b7fb8a06af9f03fa327b1e66d2d9b75539a925d122652599 | 24824 |
| visa_gy.json | 5ad6806506fe4946cd482922840e9aa739c506fb54e1be77f36904f58efe69dc | 25014 |
| visa_hk.json | 3d7754f0ff2fca16b2f7c8c1424c79cb32985fc57f288c50c46333edc679695e | 25537 |
| visa_hn.json | d1e0124936be34aa3e924fcb561d81b1e2da228b6ff69701147b656a87940af4 | 25275 |
| visa_hr.json | 03255b46a1fbaec20bea2d3d40c4a43b6a4be28295d1de15ba8fbb2a031b212d | 26441 |
| visa_ht.json | cc28c8d308fec901db98fb20b1071659dcafe69c0baf06629da1999df080dc17 | 24787 |
| visa_hu.json | ddecef2711f0162936914c4f794b7763eb4ece10d6062f8878f4fed02449035e | 26442 |
| visa_id.json | 6ba1b83b459e8161318ce0a9be86c5ae629c90111ded9fa2957bbca72f9a014a | 25015 |
| visa_ie.json | 4a3694e235e0a42ede89d7dc03ffa09b6a90ac135d6307d5be82ed66b0113ec7 | 25921 |
| visa_il.json | 089a2328155edc7b6e3c76f99d3de36522387481c3fa821f5f78ad96f57971c4 | 25442 |
| visa_in.json | 260dabb0e8ed7c75ad66fd7630074af4aa075f34d5d7c0db1df1d7e6488cd24e | 24827 |
| visa_iq.json | d4fea6ccee09a418cf7f0882b91dd2810f3d200f7bfd1a61e86414b5324249c1 | 24728 |
| visa_ir.json | 7e8721fbfe546be902529ebcd6d26bc4ce5708aa34ba1f4acbe0aeecee1dd0e4 | 24803 |
| visa_is.json | b584562ae6b9a9a0eef0c14399864ba1aeacf8a1360f1fbf6072a17ecbba4a09 | 24997 |
| visa_it.json | 69dc683e521fe1a2cc10b31b9237765c4429f76126c43c74476f6cdcb2e84698 | 26470 |
| visa_jm.json | fb5a0e48fe4031f5e56a0b2038c90c2a8e6c8084dfef1d35d972a7b909cab778 | 25080 |
| visa_jo.json | e7e96ce2ac093a077ba46fbb5cc326a183c03778beba5dcdc50671467a2ab92b | 24831 |
| visa_jp.json | 482c0941c52213fc37b64bebcfa0c70fa9fa99d7d69a99ad04b936c4b202360b | 25547 |
| visa_ke.json | d49fafc71bf7e785283b6e52bedb3174c635737a227a37fb7c74004966fffea1 | 24994 |
| visa_kg.json | 4e2876a58634d95edda5935bb1a661140453e3ecdcb6946f1b4a1741be2ae725 | 24880 |
| visa_kh.json | 62f79b9c40be3cec335ac1d36c23254deca5770f31d401915d95021ceff47473 | 24803 |
| visa_ki.json | f74235ec38d48fe88b1c58d5b5b9f94e60f2729e4aa7850110d0a7eb6b967cbd | 25244 |
| visa_km.json | 321623317882d1219ad4721cc0e0e4f13e2fc32a21fbb1668ea611aaca167099 | 24891 |
| visa_kn.json | 5974f4a1e734873800004f576d46d3476e26a2330ceeeff7bc3ba722fb1fef96 | 25417 |
| visa_kp.json | d90baea18e0d187505a76fb577ef6cef40815815ead20aca1a44e4b885995750 | 24743 |
| visa_kr.json | 822f1ea236986707467e4ad80521465d299492413322794775da1451af62bdf4 | 25549 |
| visa_kw.json | 376f843fe86b567e064d926b70f4ce0bdef2b74fdf6ef90f0e8fd1b8b305a305 | 25097 |
| visa_kz.json | ec7a506537fd3e1eca658432b88fcc812f64043c77abc3be817cf47098500f4e | 25020 |
| visa_la.json | 250551040f8c956a7e5099e89112b0536d6af187918687d92d086538dffb771b | 24793 |
| visa_lb.json | 336c412cc30a666cf6f7416840eae4335f331cd7dc6d1fb76ae72a010a6900e8 | 24811 |
| visa_lc.json | 27217b21c91ce7254fe6516e300e77b5a3bf470fb830134dfb2ce22fc94fe3fd | 25313 |
| visa_li.json | 8ce7ffe1ac8ff851e36c6e3858ed8dcf6b7aaa829ce9be778131c31747acf4f9 | 25022 |
| visa_lk.json | abc32b258badde4332580714ab981d8530d3492296d49927a4de7b3944cadc11 | 24795 |
| visa_lr.json | 6f9608642152d13a8ae196006f8a0a7818b270080be1477b8d6f81dd3d346d1c | 24837 |
| visa_ls.json | 78a72e062df5745d99b25c286451ba1853a54255d880141d85e6efa6e2aa0929 | 24994 |
| visa_lt.json | 3324728d8728514350de37bd260a08ddd0aa8a4d852e206b4f2b813b50c4eff6 | 26422 |
| visa_lu.json | ed42a4817f6a84b64bf67a7ed24f726ee23c7a0b9333b35d93bb0168b15e4e2e | 26463 |
| visa_lv.json | 69f4399b4673b11081cd02541f90af1fdd79d4d9316daf16d30151c75e91a4b5 | 26428 |
| visa_ly.json | 92091ce4ae81dab1390f507ddc466586c3bbb59bf743ffa235587eea4a1b05dc | 24794 |
| visa_ma.json | 85e4acc95da444672fc65af0ea3f0489a1f91b3142d609625a976258fcd0d584 | 25026 |
| visa_mc.json | d0b00063d1f9a1bcdd8772db16a467051cebdfe435cc1228e3ecbd7c59902de8 | 25431 |
| visa_md.json | 6a37ed7c6b172f25e9a36b9518dab0876b487a56de30258a5a4fd0bfb1c87a32 | 25240 |
| visa_me.json | 2e24b808d8a99d20129baa062416830db854def963f534698f43b0a64e11795f | 25328 |
| visa_mg.json | 6f6524f558b3ca8f3d0707713234ca94e5c4b4ebe84a05523a2365ca7ed7c317 | 24952 |
| visa_mh.json | 8a0b4bc9b6f0c2d1959ad6ac59298f7d59ae2d38ac1a37095add73a844e278ba | 25215 |
| visa_mk.json | f8e6644df1d258fa7af80becd29734a7d106ef44239ae70be3b03b12f65cfe78 | 25342 |
| visa_ml.json | d9ddc0e1306ac6b4491688dd74e56808b3c8cd4bbd6c449a288df92b653842df | 24827 |
| visa_mm.json | 0d9793da29a5fa94015e2c875f5b3e75daf8b5c39aa5c3f5ceaa5f3b70d683a0 | 24762 |
| visa_mn.json | fe5e5afd94f88d806aedc39c8e9d1eae7bb77d69ee755dc8ad1f2d2e3d79dd07 | 24943 |
| visa_mo.json | a37548c7e0d1a6434fbff7109dd175169171c9bd3bd42b8c0ff2b26425b8c3f1 | 25374 |
| visa_mr.json | 256fdfdaeba506121ae4b901019ca51baf52b40bb86a36fef308808ed24989b2 | 24897 |
| visa_mt.json | a4f71473271c407cd139b96532ebafdbff21f70a4db00597bc8d9ee654f573d4 | 26447 |
| visa_mu.json | 6383c3834d118caa6aea586845237a695b4feef976388de5c013e1c3b6d171c6 | 25333 |
| visa_mv.json | d74e205610fe252c8d96e78861148839c712fdf476ca7175fcfb055e46e0e6e9 | 25063 |
| visa_mw.json | 23d9f12b0bf270f7ff516c7ee85ea7e3ed43a0f9bb6d0950af1ee664756b7aa2 | 24977 |
| visa_mx.json | 01cb1fada0cee088535ee1746f2aa53035e9090ed0c67e1bc09eb023008959b9 | 25390 |
| visa_my.json | 2bf0b4cc49f60a81818533710164c9aa607ceb9ff45f8b236b08e3743e7d20fb | 25619 |
| visa_mz.json | 50c72746c543fde49f4ff4d6cc40adde2778b4d17f8548a44f06e624b67f0782 | 24949 |
| visa_na.json | d8e83b6aef63e14292413c2974534494d08ee337a2af527a3b34316464231c69 | 24996 |
| visa_ne.json | dcf4593c64da2c611722c1cf47b2a0f28623b17d22f64f7e771e2b751140567e | 24853 |
| visa_ng.json | a5c19f034c9e98e75597b782ebc35897f6490629e11ea1be3eeac37b340be5e6 | 24825 |
| visa_ni.json | fad13be6cf2e32b336b13185279f73f5e0c85f00a2a5df48bf36671d843de544 | 25221 |
| visa_nl.json | a590acc45850b5ccf5cd9a9db799440cbc75762a1056971903db608a878bb70a | 26442 |
| visa_no.json | f1fa89bc0a8731a7e63781af8fb265fba0879efaf9d16b0aa92d96882f2a475e | 25061 |
| visa_np.json | 77f2e94901a362428f0df9667b67f622ba2167c0f24d36ff7fbb6c97ce6d32dd | 24722 |
| visa_nr.json | ebc612bf48013673ee1290577b5399590f340fd753d6cbe2d9fb92f4757edcef | 24999 |
| visa_nz.json | 218ad270015de90f1878db84365f03fdd0c8818052d6f66f78fa8c386792a8c2 | 25515 |
| visa_om.json | 786f723c4c5fe5a5d52670c3e72a1d6cb9f9f3ebbf3bc4adb6ba91269fed499d | 25009 |
| visa_pa.json | 89d1c0059db644dcc1ccce1a1d7c9c49af6a95cfde9582919e9f37efbf15ee5c | 25351 |
| visa_pe.json | 767afc60fabb68ca1ed22960d6a8b0b19dec41e56c5b0c7f6cd73a3fbc939619 | 25393 |
| visa_pg.json | 64fc606bbc0f23085e5083ef4652e0f56a4066e0d2c66a3417e61894d4aa9048 | 24923 |
| visa_ph.json | 6a81a5ab5f649c21efdd903a302330d36e247ef54a120e5418db397615b96e34 | 24953 |
| visa_pk.json | 38e35fdbd762a2dff18328df3c43d01d0cbe4747386aeb80ba1d91e786e78f90 | 24732 |
| visa_pl.json | 1fa9a264bdb8d1cebf73248fca60f830ab9b40625d13838207988b9f20a73fcf | 26445 |
| visa_ps.json | fff375ff77388728f9a73a69e241ec54df7f77836e4e052bf988dd57ffd63d23 | 24775 |
| visa_pt.json | 40fcc558e11198695dcde78a07515ed18ab0e5e64312cf0c931a244a2d60ccd1 | 26449 |
| visa_pw.json | 2908d534bcfe8e7848cf335f0e2e481b02441c8f14d3e6454e6c37e21a172b0d | 25129 |
| visa_py.json | 780e814217851b6afe759e63553fe730076a872ff5a75e2a2c76daa11f1ae609 | 25344 |
| visa_qa.json | 8ecad4b27ee15111e944e4936c64734e65b4457de875d3a639d1875342babc02 | 25085 |
| visa_ro.json | 3aea840027b9b1945ae39f9b92881f001088be68bae92a7c82c6ce9098c17209 | 26496 |
| visa_rs.json | bdb1d83a3bbd246699d46a8ce8fff252a7437634c1febae1296fe6deab0ac67e | 25397 |
| visa_ru.json | 865905edfbdf8f5f139664e8754aea6f9281c52be090315cbccbf07841900ace | 25309 |
| visa_rw.json | c829ea346dbc094572d6add2fc73aff7d452047c0bb394fcf36f353d38e7d46b | 24963 |
| visa_sa.json | 194cb98b349e0157b6cb28b8c10bfde71dcfd04238b5cd83f8b668d41218dff1 | 25009 |
| visa_sb.json | e95f3f0c9a43609804d12bf33966ebb8b5d09ce045b19c9097854c3413adfd68 | 25245 |
| visa_sc.json | 414a8cd120132a8154c8a072c9be3daa9554362cdb3000f64f487c5f67871e5f | 25415 |
| visa_sd.json | eebbe9029e6329983dc4f1a4933b0c329f1de157eba94be9114a6c5276e0fee6 | 24813 |
| visa_se.json | 432cdd3788bc4007cfeee4d08c48d379ee88d08f1d8ac56208f74dba488c1021 | 26462 |
| visa_sg.json | 3e4d66d1022ff88f20def63a55fc39be120799288bfb43baaa908170c2416ce1 | 25599 |
| visa_si.json | 6693ac9ca641775a2e8f13eba09c9c398009820e076a685ca62d0814d46bc667 | 26441 |
| visa_sk.json | 680dcb84dd89dd854e38e4426578e9571346fa8f4a87412d5232bcf966fe2ed4 | 26441 |
| visa_sl.json | 566dc59fc0701d4b6aa6e6eda05919ee173c37f72d2a1522e98f457e0b832cb0 | 24913 |
| visa_sm.json | faaaf13b7c752744c27a842405ae48768aec541d8b8606747b79d3702e43b975 | 25414 |
| visa_sn.json | b11e1b8d4f2b19cc61a96585f2fffec177147f8c7c164a71d3c3c4d44b81d2fa | 24841 |
| visa_so.json | 73c90a4b3e196cc34f98bc745460db382e107dce1f8b07980fe420423249833d | 24806 |
| visa_sr.json | e130d4d6f75929556ab548d3489d4c825b69553278d47acb39f0c9bfa31909ab | 24946 |
| visa_ss.json | 6fd53204c0460d2258d6bebb16c7a9f62b19b0472c826747ed4498d7b0d0ff6f | 24842 |
| visa_st.json | 027e64661fde7cb533a72ca72a98927c3d464f499016d98b8ce7836bf8754eda | 24955 |
| visa_sv.json | 03f0ac087231e2fea5d3f0115c1fb135fbf854141cee07698fe5bb875334ed73 | 25298 |
| visa_sy.json | d106eede47b167ec31f0ccb4b80d0558a8dbf51746126128af2abfcc2d996753 | 24747 |
| visa_sz.json | 03f753f0074cc1e31d1e2e59fa969668bc5f0a9d6145b24bea404fe717f5cb30 | 24957 |
| visa_td.json | 98c788f6e720ad949a5b8d090ae74f0142ded0b3a28ebff5636b3b7ab95231e6 | 24923 |
| visa_tg.json | d7a88d68a6b066c6b05597a6d87f1f5978cd9cb2ee27e44fedd4c2c7ddb3f938 | 24879 |
| visa_th.json | 5f9097a02d50109f3f0b3058dff5cf289b4065c1e912ab6b47372de3e6251bbf | 25039 |
| visa_tj.json | e75cedbd8e4d620631465b81cc485e15a24d986c6f5d7b6614323dc9a0b7351d | 24855 |
| visa_tl.json | b327c5e4334c099c6cf52ee8bc5db4da873130b4c2c2a04262f8f3f88e46357f | 25114 |
| visa_tm.json | 892d203df9e957d4baf8b8abee9aa3225f76d48b187804a219ad6e037cd15729 | 24778 |
| visa_tn.json | e64de62f599f3ffcca1a09cc05ccce81624efd988bd58474ff26c967d45b15e8 | 24984 |
| visa_to.json | 30b51b6bc8d412fe402231719e0b6d8d5fe603ca7979de7b8f847a69b0ae96d9 | 25260 |
| visa_tr.json | 9ff5000cbb484fa935894d6bd76250e3274baea39c62fce4ff604e8739e7e4f5 | 25292 |
| visa_tt.json | e8e4ba650ec30339625d735aba736606ea35ed2ae743bca6ca0b6c974c75c065 | 25422 |
| visa_tv.json | 0883ffa9f77ae1462e9c36423fae6573d23acae88803b4aaaa92b9b87bfb4a21 | 25272 |
| visa_tw.json | 173b8fa82fb2366d08bfe07281539e7e2db9d8dde708f3789ee6de749bec0f6c | 25099 |
| visa_tz.json | fdcde734050f8250bbd1d6ed85cbb376b5b4934811552d0ccf5e2f95dfc6ff9a | 25031 |
| visa_ua.json | bef2fb58ad1ee7f074483ffe423266c14fd3ff3e93f8b59c0b07da3b1e476d34 | 25431 |
| visa_ug.json | bb4a0852fca4e362d44d4af7f955912f77517712f01fe2867495c3ad54e02670 | 24970 |
| visa_us.json | 21505ff85b81f1eaacc0a166de30137d1b4bf314971058949cae1f25827753a7 | 25496 |
| visa_uy.json | 6cfd0bd778075a38059cb8171175e29c70401cf73b63bf65e8c8287c02cfa89f | 25387 |
| visa_uz.json | eee6eccaf9cf0b7c51687175cd07c53d41f4796b72e194cf5401a8d04214523f | 24917 |
| visa_va.json | 44d89f868387dcf3b3bec582823de53adc61985c9d882ad39c57658a62c572eb | 25382 |
| visa_vc.json | 7417564cc8300c6d9c2e146462690300b6a0f42dffe87d8fa361508d9952690e | 25402 |
| visa_ve.json | e0c89b1e8bfb44c19ed81c128cc13c7c97885814b844ce93e4b201d1f08d8a01 | 25301 |
| visa_vn.json | f8dce3c1975b62ccdc00aa4ee891a6cd8e1667571430ec21ed9d15206cd71a92 | 24832 |
| visa_vu.json | 9a69173f9621ec9e45ded158969d47963fcd2d9ce9352444ac541bb32680477b | 25034 |
| visa_ws.json | a7573e76bac883b36daa3a58583858573300acace006db80763e0e8ea93ae085 | 25271 |
| visa_xk.json | a3d0d8e1b2f7d0f5f8790cf6d875bd2c4e90c2c668e5ac8958f61d9550298f90 | 25118 |
| visa_ye.json | 348a3f11efc8e31d57b6251876c952935421d250634c5ce50d41f08d11ddf2c9 | 24742 |
| visa_za.json | e9f405e0ff49c307ec15fcc8f2069ab357d7577ba7fdce5737dc3739beb82382 | 25155 |
| visa_zm.json | d17675a1264a145f25bc323ec88428a16d8cac3e4c138f7077a0687fd6779c30 | 24984 |
| visa_zw.json | e52fe209977870d16b62cc3cc1f3fffe7bd39f83cfec7e969c38e0e3f0a10f94 | 24944 |

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
