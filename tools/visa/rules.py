"""Curation rules that turn the Passport Index matrix into BeyondBorders visa data.

Why this file exists
--------------------
The Passport Index matrix is 199 passports x 199 destinations. Those 199
destinations are sovereign states. BeyondBorders has 252 places, because the whole
point of the place registry (PLAN.md section 3) is that Greenland is not Denmark
and French Guiana is not France.

So 53 of our places have no row in the dataset at all, and they are exactly the
ones a passport app normally gets wrong. Guadeloupe is inside the EU; Greenland
and the Faroes are outside both the EU and Schengen; Jersey now needs a UK ETA
while Gibraltar does not; American Samoa does not accept ESTA even though Guam
does. None of that falls out of any rule — it has to be written down one entry at
a time, which is what OVERRIDES below is.

Guiding rules
-------------
1. The dataset wins wherever it has an answer. An override that shadows a
   dataset row is a CORRECTION and must say so, so that the day upstream fixes
   it, the build tells us the correction is dead instead of quietly preserving a
   stale opinion forever.
2. Every override cites a source, by key into SOURCES. Keys are shared because
   most of these entries rest on the same handful of documents: "EU outermost
   region" settles five places at once, and pretending otherwise would mean five
   invented URLs.
3. Every override carries a CONFIDENCE. This file is hand-written, so some of it
   is worth less than the rest, and the app shows the difference rather than
   presenting all 53 in the same authoritative voice.
4. German passport only. See PASSPORT below for why this is not a shortcut that
   a switcher can undo cheaply.

This is a navigation aid, not a source of truth. Entry rules depend on residence,
purpose, duration and transit; nothing here survives contact with an airline
check-in desk that disagrees.
"""

# ---------------------------------------------------------------------------
# The passport this build resolves.
#
# Not a placeholder for a future switcher. The dataset would happily ship whole
# (~600 kB of CSV, ~400 kB as JSON) and give 199 passports their 199 answers.
# But OVERRIDES below is 53 entries *for a German*, and it does not generalise:
# "Guadeloupe is visa-free" is true because of EU freedom of movement, which a
# Brazilian passport does not have. A switcher therefore needs 199 x 53 curated
# answers, not a bigger download — so shipping the matrix whole would buy a
# passport picker that is correct for 199 places and silent for 53. That is the
# failure this app exists to avoid, so we resolve one row and ship that.
# ---------------------------------------------------------------------------
PASSPORT = "DE"

# ---------------------------------------------------------------------------
# The dataset.
#
# PLAN.md names ilyankou/passport-index-dataset. That repo was archived in
# January 2025 and its README points at this fork for February 2026 onwards.
# The fork is also MIT rather than CC BY-SA, which removes the redistribution
# constraint PLAN.md section 8 flagged as unchecked.
#
# The switch is not cosmetic: the archived data still gave Germans 180 visa-free
# days to GB, but the UK ETA has applied to EU nationals since 2025. This fork
# has GB as `eta`.
# ---------------------------------------------------------------------------
DATASET = {
    "name": "Passport Index Data",
    "url": "https://github.com/imorte/passport-index-data",
    "file": "passport-index-tidy-iso2.csv",
    "licence": "MIT",
    # From the fork's README ("Last updated: 17 February 2026"), not from the
    # file's mtime, which would change on every clone.
    "as_of": "2026-02-17",
}

# ---------------------------------------------------------------------------
# CATEGORIES, ordered.
#
# The order is the ramp the map and the list both sort by, and it is monotonic in
# one specific thing: how much can go wrong between deciding to go and standing
# at the border.
#
# VISA_ON_ARRIVAL sits after ETA rather than before it despite needing no advance
# paperwork, because an ETA is a near-instant online yes/no and a visa on arrival
# is a queue, a fee in cash and an official's discretion.
#
# PERMIT_REQUIRED is ours, not the dataset's. The seven uninhabited places need
# permission from a research programme or a nature-reserve authority; calling
# that "visa required" would send a user looking for an embassy that has no
# counter for it.
# ---------------------------------------------------------------------------
CATEGORIES = [
    "HOME",             # the passport's own country
    "VISA_FREE",
    "ETA",              # ESTA, Canadian eTA, Australian eVisitor, UK ETA
    "VISA_ON_ARRIVAL",
    "E_VISA",
    "VISA_REQUIRED",
    "PERMIT_REQUIRED",  # ours: research/reserve permission, not a visa
    "NO_ADMISSION",
]

# The dataset's vocabulary, mapped onto the above. A bare integer in the
# Requirement column means visa-free for that many days and is handled in the
# build script; -1 is the diagonal (a passport against its own country).
REQUIREMENT_CATEGORY = {
    "visa free": "VISA_FREE",
    "eta": "ETA",
    "visa on arrival": "VISA_ON_ARRIVAL",
    "e-visa": "E_VISA",
    "visa required": "VISA_REQUIRED",
    "no admission": "NO_ADMISSION",
    "-1": "HOME",
}

CONFIDENCE = ("high", "medium", "low")

# ---------------------------------------------------------------------------
# The Auswärtiges Amt link-out, from their open data rather than from guesswork.
#
# PLAN.md wants every destination to link to the German Federal Foreign Office
# page for that country. Their country pages live at
# /de/service/laender/<german-slug>-node/..., and the slugs are not derivable:
# Türkei is `tuerkei` but the United States is not `vereinigtestaaten`. Guessing
# 252 of them would ship a pile of 404s.
#
# But they publish https://www.auswaertiges-amt.de/opendata/travelwarning as
# JSON, keyed by content id, with an ISO 3166-1 alpha-2 code and the German name
# for each of 200 countries. And /de/-/<contentId> redirects to the real page.
# So the whole link table falls out of a join on a code we already have.
#
# We bake the /de/-/<id> form rather than the resolved slug: it is short, it is
# official, and resolving 200 redirects at build time to save the browser one hop
# is not a trade worth making. If the Foreign Office ever renumbers, re-running
# this script fixes every link at once.
#
# The endpoint is the travel-advice ("Reise- und Sicherheitshinweise") page,
# which is the right target anyway — its "Einreise und Zoll" section is exactly
# the authority this app defers to.
# ---------------------------------------------------------------------------
AA_OPENDATA = {
    "url": "https://www.auswaertiges-amt.de/opendata/travelwarning",
    "file": "aa-travelwarning.json",
    "link": "https://www.auswaertiges-amt.de/de/-/{content_id}",
}

# The 200 codes in that feed are all places in our registry, but 52 of our places
# are not in it — the territories, plus Germany itself. This table says whose page
# covers them. Mostly the sovereign, but three are judgement calls and are marked.
AA_PAGE = {
    # --- France ------------------------------------------------------------
    "GF": "FR", "GP": "FR", "MQ": "FR", "RE": "FR", "YT": "FR",
    "BL": "FR", "MF": "FR", "PM": "FR", "WF": "FR", "PF": "FR",
    "NC": "FR", "TF": "FR",
    # --- United Kingdom ----------------------------------------------------
    "JE": "GB", "GG": "GB", "IM": "GB", "GI": "GB", "BM": "GB",
    "KY": "GB", "TC": "GB", "VG": "GB", "AI": "GB", "MS": "GB",
    "FK": "GB", "SH": "GB", "PN": "GB", "IO": "GB", "GS": "GB",
    # --- Netherlands -------------------------------------------------------
    "AW": "NL", "CW": "NL", "SX": "NL", "BQ": "NL",
    # --- United States -----------------------------------------------------
    "PR": "US", "VI": "US", "GU": "US", "MP": "US", "AS": "US", "UM": "US",
    # --- Australia ---------------------------------------------------------
    "CX": "AU", "CC": "AU", "NF": "AU", "HM": "AU",
    # --- Nordics -----------------------------------------------------------
    "GL": "DK", "FO": "DK", "AX": "FI", "SJ": "NO", "BV": "NO",
    # --- New Zealand -------------------------------------------------------
    # The Cook Islands and Niue have their own Foreign Office pages, so they are
    # not listed here. Tokelau does not.
    "TK": "NZ",
    # --- The judgement calls ----------------------------------------------
    # The Foreign Office has no page for any of these three; each is covered in a
    # section of the neighbour's page, which is where a German traveller would
    # actually be sent.
    "XN": "CY",  # Northern Cyprus is covered on the Zypern page
    "XS": "SO",  # Somaliland is covered on the Somalia page
    "EH": "MA",  # Westsahara is covered on the Marokko page
}

# Places with no Foreign Office link at all, stated rather than left to fall
# through. The build fails on any place that is neither in the feed, nor in
# AA_PAGE, nor here.
AA_NONE = {
    "DE",  # the passport's own country: there is no advisory for going home
    "AQ",  # no country page exists; the Antarctic permit source covers it
}

# The other link PLAN.md asks for. No deep link exists — the IATA Travel Centre
# is a form you fill in — so this is a hub, and the app says so rather than
# implying it lands on the destination.
IATA_TRAVEL_CENTRE = (
    "https://www.iatatravelcentre.com/"
    "passport-visa-health-travel-document-requirements.htm"
)

# ---------------------------------------------------------------------------
# SOURCES: what the overrides rest on.
#
# Deliberately few. Most of the 53 are decided by one structural fact — this is
# an EU outermost region, this is inside the Australian migration zone — and one
# good link for the family beats 46 guessed URLs, most of which would be dead
# within a year.
#
# `aa` is the honest fallback: the Auswärtiges Amt hub, which is where the app
# sends the user anyway and where a low-confidence entry should be checked.
# ---------------------------------------------------------------------------
SOURCES = {
    "aa": (
        "https://www.auswaertiges-amt.de/de/service/laender",
        "Auswärtiges Amt country and travel information (German, authoritative "
        "for German nationals)",
    ),
    "eu_omr": (
        "https://ec.europa.eu/regional_policy/policy/themes/outermost-regions_en",
        "EU outermost regions: part of the Union, so freedom of movement "
        "applies, but outside the Schengen area",
    ),
    "eu_oct": (
        "https://international-partnerships.ec.europa.eu/countries/"
        "overseas-countries-and-territories_en",
        "Overseas countries and territories: associated with the EU, not part "
        "of it, each with its own entry rules",
    ),
    "uk_eta": (
        "https://www.gov.uk/guidance/"
        "apply-for-an-electronic-travel-authorisation-eta",
        "UK Electronic Travel Authorisation — covers the UK and the Crown "
        "Dependencies, not the Overseas Territories",
    ),
    "esta": (
        "https://esta.cbp.dhs.gov/",
        "US Electronic System for Travel Authorization",
    ),
    "evisitor": (
        "https://immi.homeaffairs.gov.au/visas/getting-a-visa/visa-listing/"
        "evisitor-651",
        "Australian eVisitor (subclass 651), free for EU nationals and required "
        "everywhere in the Australian migration zone",
    ),
    "nl_caribbean": (
        "https://www.netherlandsworldwide.nl/visa-caribbean",
        "Entry to the Caribbean parts of the Kingdom of the Netherlands",
    ),
    "dk_realm": (
        "https://um.dk/en/travel-and-residence/travel-to-greenland-and-the-faroe-islands",
        "Greenland and the Faroe Islands are outside the EU and Schengen; a "
        "Schengen visa is not valid for either",
    ),
    "svalbard": (
        "https://www.sysselmesteren.no/en/travel-and-visit-svalbard/",
        "Svalbard Treaty: visa-free for every nationality, with no immigration "
        "control on the archipelago itself",
    ),
    "gsgssi": (
        "https://www.gov.gs/visitors/",
        "South Georgia and the South Sandwich Islands: visit permit required in "
        "advance",
    ),
    "antarctic": (
        "https://www.umweltbundesamt.de/en/topics/antarctic",
        "Antarctic Treaty and its Environmental Protocol: German expeditions "
        "need an Umweltbundesamt permit, not a visa",
    ),
}

# ---------------------------------------------------------------------------
# OVERRIDES: the 53 places the dataset has no row for.
#
# code: (category, days, confidence, source key, note)
#
# `days` is None where the limit is unstated, not applicable (freedom of
# movement) or genuinely unknown — never a guess dressed as a number.
# `note` is shown in the app verbatim, so it is written for a traveller.
# ---------------------------------------------------------------------------
OVERRIDES = {
    # --- EU outermost regions: inside the Union, outside Schengen -----------
    # PLAN.md's named test case is here. These are *not* "France, roughly" — an
    # ID card gets you in, but there is a border check on arrival from Paris,
    # because the Schengen area stops at the Atlantic.
    "GF": ("VISA_FREE", None, "high", "eu_omr",
           "EU outermost region. Freedom of movement, no visa, no time limit — "
           "but outside Schengen, so expect a passport check on arrival even on "
           "a domestic flight from mainland France."),
    "GP": ("VISA_FREE", None, "high", "eu_omr",
           "EU outermost region. Freedom of movement, no visa — outside "
           "Schengen, so there is a border check on arrival."),
    "MQ": ("VISA_FREE", None, "high", "eu_omr",
           "EU outermost region. Freedom of movement, no visa — outside "
           "Schengen, so there is a border check on arrival."),
    "RE": ("VISA_FREE", None, "high", "eu_omr",
           "EU outermost region. Freedom of movement, no visa — outside "
           "Schengen, so there is a border check on arrival."),
    "YT": ("VISA_FREE", None, "high", "eu_omr",
           "EU outermost region. Freedom of movement, no visa — outside "
           "Schengen, so there is a border check on arrival."),

    # --- French overseas collectivities: EU-associated, not EU territory ----
    "PF": ("VISA_FREE", 90, "high", "eu_oct",
           "French collectivity outside the EU. EU nationals enter without a "
           "visa for up to three months."),
    "NC": ("VISA_FREE", 90, "high", "eu_oct",
           "French collectivity outside the EU. EU nationals enter without a "
           "visa for up to three months."),
    "BL": ("VISA_FREE", 90, "medium", "eu_oct",
           "French collectivity outside the EU. EU nationals enter without a "
           "visa; three months is the usual limit."),
    "MF": ("VISA_FREE", 90, "medium", "eu_oct",
           "French side of the island, a collectivity outside the EU. EU "
           "nationals enter without a visa. Sint Maarten next door is Dutch and "
           "listed separately; the land border is open."),
    "PM": ("VISA_FREE", 90, "medium", "eu_oct",
           "French collectivity off Newfoundland, outside the EU. EU nationals "
           "enter without a visa. Most routes go through Canada, which needs "
           "its own eTA."),
    "WF": ("VISA_FREE", 90, "medium", "eu_oct",
           "French collectivity outside the EU. EU nationals enter without a "
           "visa. Reached only via New Caledonia or Fiji."),

    # --- Kingdom of the Netherlands, Caribbean: outside the EU -------------
    # The day limits differ island to island and are the shakiest numbers in
    # this block, hence medium throughout.
    "AW": ("VISA_FREE", 30, "medium", "nl_caribbean",
           "Outside the EU. No visa for German nationals; entry is normally "
           "granted for 30 days and can be extended on the island."),
    "CW": ("VISA_FREE", 30, "medium", "nl_caribbean",
           "Outside the EU. No visa for German nationals; entry is normally "
           "granted for 30 days."),
    "SX": ("VISA_FREE", 90, "medium", "nl_caribbean",
           "Dutch side of the island, outside the EU. No visa for German "
           "nationals. Saint Martin next door is French and listed separately."),
    "BQ": ("VISA_FREE", 90, "medium", "nl_caribbean",
           "Bonaire, Saba and Sint Eustatius — Dutch special municipalities, "
           "outside the EU. No visa for German nationals."),

    # --- Kingdom of Denmark: outside the EU and outside Schengen -----------
    # The other PLAN.md test case. The trap is the reverse of the usual one:
    # people assume a Schengen visa covers Greenland. It does not.
    "GL": ("VISA_FREE", 90, "high", "dk_realm",
           "Outside the EU and outside Schengen — a Schengen visa is not valid "
           "here. German nationals need no visa; take a passport, not an ID "
           "card."),
    "FO": ("VISA_FREE", 90, "high", "dk_realm",
           "Outside the EU and outside Schengen — a Schengen visa is not valid "
           "here. German nationals need no visa; take a passport."),

    # --- Nordic oddities ---------------------------------------------------
    "AX": ("VISA_FREE", None, "high", "eu_omr",
           "Part of Finland: EU and Schengen, so freedom of movement. The "
           "islands' own right of domicile affects buying property and working, "
           "not visiting."),
    "SJ": ("VISA_FREE", None, "high", "svalbard",
           "The Svalbard Treaty makes the archipelago visa-free for everyone, "
           "with no immigration control and no time limit. But you fly via "
           "mainland Norway, so you leave and re-enter Schengen — carry a "
           "passport. Jan Mayen shares the ISO code and is a manned station "
           "with no visitor access."),

    # --- UK Crown Dependencies: inside the Common Travel Area, so ETA ------
    # The distinction people get wrong: the UK ETA covers these three, but none
    # of the Overseas Territories below.
    "JE": ("ETA", None, "high", "uk_eta",
           "Inside the Common Travel Area, so the UK ETA applies to German "
           "nationals here just as it does in Britain."),
    "GG": ("ETA", None, "high", "uk_eta",
           "Inside the Common Travel Area, so the UK ETA applies to German "
           "nationals here just as it does in Britain. Alderney and Sark are "
           "part of the Bailiwick."),
    "IM": ("ETA", None, "high", "uk_eta",
           "Inside the Common Travel Area, so the UK ETA applies to German "
           "nationals here just as it does in Britain."),

    # --- British Overseas Territories: own immigration, no UK ETA ----------
    "GI": ("VISA_FREE", 90, "medium", "aa",
           "British Overseas Territory with its own border control. No visa for "
           "German nationals, and the UK ETA does not apply. The land border "
           "with Spain is a Schengen external border."),
    "BM": ("VISA_FREE", 90, "medium", "aa",
           "British Overseas Territory with its own immigration rules. No visa "
           "for German nationals; the UK ETA does not apply."),
    "KY": ("VISA_FREE", 30, "medium", "aa",
           "British Overseas Territory with its own immigration rules. No visa "
           "for German nationals; entry is normally granted for 30 days and can "
           "be extended locally."),
    "TC": ("VISA_FREE", 90, "medium", "aa",
           "British Overseas Territory with its own immigration rules. No visa "
           "for German nationals."),
    "VG": ("VISA_FREE", 30, "medium", "aa",
           "British Overseas Territory with its own immigration rules. No visa "
           "for German nationals. Not the same place as the US Virgin Islands, "
           "which need an ESTA."),
    "AI": ("VISA_FREE", 90, "medium", "aa",
           "British Overseas Territory with its own immigration rules. No visa "
           "for German nationals."),
    "MS": ("VISA_FREE", 180, "medium", "aa",
           "British Overseas Territory with its own immigration rules. No visa "
           "for German nationals; 180 days matches the UK government's own "
           "Montserrat travel advice. Half the island is an exclusion zone "
           "around the volcano."),
    "FK": ("VISA_FREE", 30, "medium", "aa",
           "British Overseas Territory with its own immigration ordinance. No "
           "visa for German nationals; a visitor permit for one month is "
           "issued on arrival, matching the UK government's own Falklands "
           "travel advice."),
    "SH": ("VISA_FREE", 183, "medium", "aa",
           "Saint Helena's short-term entry permit is issued on arrival for a "
           "fee and is valid up to 183 days, per the UK government's own "
           "travel advice — you must show accommodation and a return booking. "
           "Ascension is separate and needs a 3-month visa arranged in "
           "advance; Tristan da Cunha needs the Island Council's consent and "
           "a ship, with no fixed day limit."),
    "PN": ("VISA_FREE", 14, "medium", "aa",
           "No visa for a short visit, capped at 14 days if you arrive and "
           "depart on the same ship (matches the UK government's own Pitcairn "
           "travel advice); a longer stay needs a Long-Term Visit Visa. "
           "Landing also needs a licence from the Island Council and there is "
           "no airstrip — the only access is by ship from Mangareva."),

    # --- US territories: mostly US immigration, with one exception ---------
    "PR": ("ETA", 90, "high", "esta",
           "US immigration applies in full, so German nationals need an ESTA "
           "exactly as for the mainland."),
    "VI": ("ETA", 90, "high", "esta",
           "US immigration applies in full, so German nationals need an ESTA. "
           "Not the same place as the British Virgin Islands."),
    "GU": ("ETA", 90, "high", "esta",
           "US immigration applies. Germany is a Visa Waiver Program country, "
           "so an ESTA is the route in — the separate Guam-CNMI waiver is for "
           "other nationalities."),
    "MP": ("ETA", 90, "high", "esta",
           "US immigration applies. Germany is a Visa Waiver Program country, "
           "so an ESTA is the route in."),
    # The one everybody gets wrong, including most visa maps.
    "AS": ("VISA_FREE", 30, "low", "aa",
           "American Samoa runs its own immigration and an ESTA is NOT valid "
           "here. Visa-waiver nationals are normally admitted for 30 days with "
           "a passport, onward ticket and proof of funds, but the rules are "
           "administered locally and change without notice. Verify before "
           "booking — this entry is the least certain in the app."),

    # --- New Zealand realm -------------------------------------------------
    "CK": ("VISA_FREE", 31, "medium", "aa",
           "Self-governing in free association with New Zealand, with its own "
           "entry rules. No visa for German nationals; 31 days is the usual "
           "grant. A New Zealand NZeTA is needed for the transit through "
           "Auckland."),
    "NU": ("VISA_FREE", 30, "medium", "aa",
           "Self-governing in free association with New Zealand. No visa for "
           "German nationals. Reached only via Auckland, which needs an NZeTA."),
    "TK": ("VISA_FREE", None, "low", "aa",
           "No airport: access is by boat from Samoa, and visitors need a "
           "travel permit from the Tokelau Liaison Office in Apia, which can "
           "take a month or two to process. No fixed day limit could be "
           "confirmed for non-NZ visitors, and access has been reported closed "
           "to visitors at times — check current status before planning any "
           "trip here."),

    # --- Australian external territories: inside the migration zone -------
    "CX": ("ETA", 90, "medium", "evisitor",
           "Inside the Australian migration zone, so German nationals need an "
           "eVisitor (subclass 651) just as for the mainland."),
    "CC": ("ETA", 90, "medium", "evisitor",
           "Inside the Australian migration zone, so German nationals need an "
           "eVisitor (subclass 651) just as for the mainland."),
    "NF": ("ETA", 90, "high", "evisitor",
           "Inside the Australian migration zone since 2016, so German "
           "nationals need an eVisitor (subclass 651) just as for the "
           "mainland."),

    # --- Contested and de facto ------------------------------------------
    "EH": ("VISA_FREE", 90, "low", "aa",
           "Entry follows Morocco in practice, since Morocco controls the "
           "populated coast including Laâyoune and Dakhla — so no visa for "
           "German nationals, on Moroccan terms. The Polisario-administered "
           "interior is a different matter and not somewhere you arrive as a "
           "tourist."),
    "XN": ("VISA_FREE", 90, "medium", "aa",
           "Northern Cyprus runs its own entry control and normally stamps a "
           "separate slip rather than the passport. No visa for German "
           "nationals. Arriving directly (via Türkiye) counts as an illegal "
           "entry in the Republic of Cyprus's view; crossing at Nicosia does "
           "not."),
    "XS": ("VISA_ON_ARRIVAL", 30, "medium", "aa",
           "Somaliland issues its own visas and does not recognise Somali "
           "ones. A visa on arrival at Hargeisa is the usual route, or one of "
           "the liaison offices in advance. Somali visas are not valid here and "
           "vice versa."),

    # --- Uninhabited: permission, not a visa ------------------------------
    "AQ": ("PERMIT_REQUIRED", None, "high", "antarctic",
           "No visa and no immigration control — nobody's sovereign territory. "
           "But German nationals organising any activity here need an "
           "Umweltbundesamt permit under the Antarctic Treaty's Environmental "
           "Protocol. Joining a licensed cruise means the operator holds it."),
    "GS": ("PERMIT_REQUIRED", None, "high", "gsgssi",
           "A visit permit from the Commissioner is required in advance, and is "
           "normally arranged by an expedition-cruise operator. There is no "
           "scheduled transport and no visa."),
    "TF": ("PERMIT_REQUIRED", None, "high", "aa",
           "Crozet, Kerguelen, Amsterdam, Saint-Paul and Adélie Land. Access "
           "needs the prefect's authorisation and a berth on the supply ship "
           "Marion Dufresne. Not a visa question."),
    "HM": ("PERMIT_REQUIRED", None, "high", "aa",
           "An Australian external territory and a strict nature reserve: entry "
           "needs an Australian Antarctic Division permit, granted essentially "
           "only for research."),
    "BV": ("PERMIT_REQUIRED", None, "high", "aa",
           "A Norwegian nature reserve with no harbour and no airstrip. Landing "
           "needs Norwegian Polar Institute permission and happens only for "
           "research."),
    "IO": ("PERMIT_REQUIRED", None, "high", "aa",
           "Diego Garcia is a joint UK-US military base and the rest of the "
           "Chagos Archipelago is a marine protected area. A permit from the "
           "British Indian Ocean Territory administration is required and "
           "tourism is not permitted. Sovereignty is being transferred to "
           "Mauritius, which may change all of this."),
    "UM": ("PERMIT_REQUIRED", None, "high", "aa",
           "Nine scattered islands and atolls — Midway, Wake, Johnston, "
           "Palmyra and the rest. Most are National Wildlife Refuges needing a "
           "US Fish and Wildlife Service special-use permit; Wake is an air "
           "force installation. Not reachable as a visitor."),
}

# ---------------------------------------------------------------------------
# CORRECTIONS: overrides that shadow a row the dataset *does* have.
#
# Empty, and that is the goal. The one correction this file was written to hold
# — GB as `eta` rather than 180 visa-free days — became unnecessary when we moved
# off the archived dataset, which is the clearest possible argument for the move.
#
# Anything added here is a running disagreement with upstream, so the build
# fails once upstream agrees, forcing the entry out again rather than letting our
# opinion outlive the reason for it. Same shape as OVERRIDES.
# ---------------------------------------------------------------------------
CORRECTIONS = {}

# ---------------------------------------------------------------------------
# ASSERTIONS: PLAN.md's "done when" clause, made executable.
#
# "You see, offline, where your German passport gets you without a visa — with
# correct answers for Greenland and French Guiana." Those two are here by name.
# The rest are the traps that a naive parent-inherits rule would fall into: the
# pairs below sit next to each other geographically and differ in the answer.
# ---------------------------------------------------------------------------
EXPECTED = {
    # PLAN.md's named cases.
    "GL": ("VISA_FREE", "not Schengen, but no visa for a German"),
    "GF": ("VISA_FREE", "EU territory, so freedom of movement"),
    # Same island, two administrations, and the dataset covers neither.
    "MF": ("VISA_FREE", "French half"),
    "SX": ("VISA_FREE", "Dutch half"),
    # Same name, opposite answers.
    "VG": ("VISA_FREE", "British Virgin Islands need nothing"),
    "VI": ("ETA", "US Virgin Islands need an ESTA"),
    # Both American, and only one of them takes an ESTA.
    "GU": ("ETA", "Guam is US immigration"),
    "AS": ("VISA_FREE", "American Samoa is not"),
    # Both British, and only one of them is inside the ETA scheme.
    "JE": ("ETA", "Crown Dependency, so the UK ETA applies"),
    "GI": ("VISA_FREE", "Overseas Territory, so it does not"),
    # The dataset row that made us change dataset.
    "GB": ("ETA", "the UK ETA has applied to EU nationals since 2025"),
    # Sanity anchors from the dataset itself.
    "DE": ("HOME", "the passport's own country"),
    "FR": ("VISA_FREE", "Schengen"),
    "US": ("ETA", "ESTA"),
    "CN": ("VISA_FREE", "unilateral visa-free entry for German nationals"),
    "RU": ("E_VISA", "e-visa since 2023"),
    "AQ": ("PERMIT_REQUIRED", "a treaty permit, not a visa"),
}
