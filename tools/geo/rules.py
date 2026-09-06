"""Curation rules that turn Natural Earth map units into BeyondBorders places.

Why this file exists
--------------------
Natural Earth's `admin_0_map_units` layer is the only admin-0 layer that keeps
Greenland separate from Denmark and French Guiana separate from France, which is
the entire reason BeyondBorders uses it (see PLAN.md section 3).

But it also splits things we do NOT want split: England/Scotland/Wales/Northern
Ireland, Belgium's three regions, the Azores and Madeira, Zanzibar, Republika
Srpska. And it contains overlay polygons (the Korean DMZ, UNDOF) that would draw
on top of real countries.

So the raw layer is not usable as-is in either direction. These tables are the
curation. Every entry is a deliberate decision, not a default.

Guiding rules
-------------
1. A place is somewhere you can meaningfully say "I have been there" — normally
   because it has its own entry/exit formalities or its own ISO code.
2. No holes in the map. Every land polygon must end up owned by some place, so
   leftovers get merged into their geographic neighbour rather than dropped.
3. Only uninhabited specks and overlay polygons may be dropped outright.
"""

# ---------------------------------------------------------------------------
# MERGE: map unit GU_A3 -> GU_A3 of the place that absorbs it.
# The target keeps its own name and code; the merged geometry is appended.
# ---------------------------------------------------------------------------
MERGE = {
    # --- United Kingdom: one place, per the granularity decision -----------
    # Natural Earth has no parent unit for the UK at all, only the four home
    # nations, so England is used as the anchor and renamed. Same pattern for
    # Belgium (BFR) and Bosnia (BHF) below.
    "SCT": "ENG",  # Scotland
    "WLS": "ENG",  # Wales
    "NIR": "ENG",  # Northern Ireland
    # UK Sovereign Base Areas on Cyprus. Legally British, but they are enclaves
    # inside Cyprus and Natural Earth cuts them out of the Cyprus polygon.
    # Merged into Cyprus so the island renders without holes; nobody tracks a
    # visit to Akrotiri as distinct from Cyprus.
    "WSB": "CYP",  # Akrotiri Sovereign Base Area
    "ESB": "CYP",  # Dhekelia Sovereign Base Area
    # --- Sub-national splits we do not want -------------------------------
    "BCR": "BFR",  # Brussels Capital Region -> Belgium (BFR is the anchor)
    "BWR": "BFR",  # Walloon Region
    "BHB": "BHF",  # Brcko District          -> Bosnia and Herzegovina
    "BIS": "BHF",  # Republika Srpska
    "PAZ": "PRX",  # Azores        - part of Portugal, EU and Schengen
    "PMD": "PRX",  # Madeira       - same
    "GEA": "GEG",  # Ajaria        - autonomous republic of Georgia
    "IRK": "IRR",  # Iraqi Kurdistan
    "SOP": "SOX",  # Puntland      - Somaliland stays separate, see KEEP below
    "SRV": "SRS",  # Vojvodina
    "TZZ": "TZA",  # Zanzibar      - same visa regime as mainland Tanzania
    "PNB": "PNX",  # Bougainville
    "ACB": "ACA",  # Barbuda       - Antigua and Barbuda is one country (AG)
    # --- Palestine: Natural Earth splits it, ISO does not ------------------
    "WEB": "GAZ",  # West Bank + Gaza -> Palestine (PS)
    # --- Uninhabited dependencies with no ISO code of their own ------------
    "ATC": "AUS",  # Ashmore and Cartier Islands
    "CSI": "AUS",  # Coral Sea Islands
    "NJM": "NSV",  # Jan Mayen -> ISO SJ is "Svalbard and Jan Mayen"
    "PFA": "CHN",  # Paracel Islands (disputed; NE assigns sovereignty to China)
    # --- Leases: territory of the host country for travel purposes --------
    "USG": "CUB",  # US Naval Base Guantanamo Bay
    "KAB": "KAZ",  # Baykonur Cosmodrome
    # --- Undefined / unclaimed land, merged into a neighbour to avoid holes -
    "KAS": "IND",  # Siachen Glacier      - administered by India
    "SPI": "CHL",  # Southern Patagonian Ice Field - CL/AR boundary undefined
    "BRT": "SDN",  # Bir Tawil            - claimed by neither Egypt nor Sudan
    "CNM": "CYP",  # Cyprus No Mans Area  - UN buffer zone
    "BRI": "BRA",  # Brazilian Island (Isla Brasilera) - uninhabited islet
                   # disputed with Uruguay; Natural Earth already tags it BR
    # --- US Minor Outlying Islands: nine specks, one ISO code (UM) ---------
    "DQI": "BQI",  # Jarvis Island     -> Navassa becomes the UM anchor
    "FQI": "BQI",  # Baker Island
    "HQI": "BQI",  # Howland Island
    "JQI": "BQI",  # Johnston Atoll
    "KQI": "BQI",  # Kingman Reef
    "LQI": "BQI",  # Palmyra Atoll
    "MQI": "BQI",  # Midway Islands
    "WQI": "BQI",  # Wake Atoll
}

# ---------------------------------------------------------------------------
# DROP: removed entirely. Only overlay polygons and uninhabited disputed reefs.
# Dropping these creates no holes because they are isolated features.
# ---------------------------------------------------------------------------
DROP = {
    "KNZ",  # Korean Demilitarized Zone (north) - overlay, would double-draw
    "KNX",  # Korean Demilitarized Zone (south) - overlay
    "SYU",  # UNDOF buffer zone (Golan)         - overlay
    "CLP",  # Clipperton Island    - uninhabited French islet 5000 km from
            # France. Merging it into FR would stretch France's bounding box to
            # 109 deg W and break "zoom to France".
    "PGA",  # Spratly Islands      - uninhabited disputed reefs
    "SCR",  # Scarborough Reef     - uninhabited disputed reef
    "BJN",  # Bajo Nuevo Bank      - uninhabited disputed reef
    "SER",  # Serranilla Bank      - uninhabited disputed reef
}

# ---------------------------------------------------------------------------
# CODES: places with no usable ISO 3166-1 alpha-2 code in Natural Earth.
# ISO reserves XA-XZ for user assignment, so these are legitimate private codes.
# ---------------------------------------------------------------------------
CODE_OVERRIDE = {
    "SOL": "XS",  # Somaliland      - de facto state, own visa and entry stamps
    "CYN": "XN",  # Northern Cyprus - separate entry regime from Cyprus
    "BQI": "UM",  # US Minor Outlying Islands anchor (see MERGE above)
    "GAZ": "PS",  # Palestine
}

# ---------------------------------------------------------------------------
# MOVE_POLYGON: cut specific polygons out of a map unit's own geometry and
# fold them into a different place, identified by bounding box since Natural
# Earth gives no stable id for one polygon within a unit's MultiPolygon.
#
# Crimea (with Sevastopol) ships as an isolated polygon inside Russia's
# admin-0 shape - Natural Earth's ISO_A2_EH there reflects de facto control,
# and there is no separate map unit to redirect with CODE_OVERRIDE. Moving the
# polygon itself, rather than overlaying Ukraine's claim on top unchanged,
# means selecting Russia on the map no longer highlights Crimea too.
# ---------------------------------------------------------------------------
MOVE_POLYGON = {
    "RUS": [
        # Crimean peninsula + Sevastopol; bbox padded past the polygon's own
        # (32.48, 44.38)-(36.64, 46.22) so a future Natural Earth update
        # nudging its coastline slightly still matches.
        {"target": "UKR", "bbox": (31.5, 44.0, 37.2, 46.7)},
    ],
}

# ---------------------------------------------------------------------------
# CONTINENTS: Natural Earth files nine island places under the pseudo-continent
# "Seven seas (open ocean)". That is fine for a cartographer and useless for a
# traveller, who wants Seychelles counted somewhere they can be proud of. Keyed by
# the final 2-letter place code, not GU_A3, so the intent stays readable.
# ---------------------------------------------------------------------------
CONTINENT_OVERRIDE = {
    "MU": "Africa",       # Mauritius
    "RE": "Africa",       # Réunion
    "SC": "Africa",       # Seychelles
    "SH": "Africa",       # Saint Helena, Ascension and Tristan da Cunha
    "IO": "Africa",       # British Indian Ocean Territory (Chagos)
    "MV": "Asia",         # Maldives
    "TF": "Antarctica",   # French Southern Territories - subantarctic
    "HM": "Antarctica",   # Heard Island and McDonald Islands - subantarctic
    "GS": "Antarctica",   # South Georgia and South Sandwich Islands
}

# The seven continents any place must end up in. Guards against a future Natural
# Earth release inventing another bucket.
VALID_CONTINENTS = {
    "Africa", "Antarctica", "Asia", "Europe",
    "North America", "Oceania", "South America",
}

# ---------------------------------------------------------------------------
# NAMES: Natural Earth's GEOUNIT name is not always what a traveller expects.
# ---------------------------------------------------------------------------
NAME_OVERRIDE = {
    "ENG": "United Kingdom",  # anchor for the four home nations
    "BFR": "Belgium",         # anchor for the three Belgian regions
    "BHF": "Bosnia and Herzegovina",
    "FXX": "France",
    "NLX": "Netherlands",
    "PRX": "Portugal",
    "GEG": "Georgia",
    "IRR": "Iraq",
    "SOX": "Somalia",
    "SRS": "Serbia",
    "TZA": "Tanzania",
    "PNX": "Papua New Guinea",
    "ACA": "Antigua and Barbuda",
    "GAZ": "Palestine",
    "BQI": "US Minor Outlying Islands",
    "NSV": "Svalbard and Jan Mayen",
    "REU": "Réunion",
    "MAC": "Macao",
    "HKG": "Hong Kong",
    "ALD": "Åland Islands",
    "NOW": "Norway",
    "SOL": "Somaliland",
    "CYN": "Northern Cyprus",
    "CHN": "China",
    "USA": "United States",
    "COD": "DR Congo",
    "COG": "Republic of the Congo",
    "CZE": "Czechia",
    "SWZ": "Eswatini",
    "TUR": "Türkiye",
    "MKD": "North Macedonia",
    "VAT": "Vatican City",
    "BRN": "Brunei",
    "LAO": "Laos",
    "PRK": "North Korea",
    "KOR": "South Korea",
    "SYX": "Syria",
    "VNM": "Vietnam",
    "IRN": "Iran",
    "BOL": "Bolivia",
    "VEN": "Venezuela",
    "TTO": "Trinidad and Tobago",
    "VCT": "Saint Vincent and the Grenadines",
    "KNA": "Saint Kitts and Nevis",
    "FSM": "Micronesia",
    "SGS": "South Georgia and South Sandwich Islands",
    "ATF": "French Southern Territories",
    "VIR": "US Virgin Islands",
    "MNP": "Northern Mariana Islands",
    "BLM": "Saint Barthélemy",
    "MAF": "Saint Martin",
    "SXM": "Sint Maarten",
    "NLY": "Caribbean Netherlands",
    "CUW": "Curaçao",
    "TLS": "Timor-Leste",
    "CIV": "Côte d'Ivoire",
    "CPV": "Cabo Verde",
    "STP": "São Tomé and Príncipe",
    "GNQ": "Equatorial Guinea",
    "CAF": "Central African Republic",
    "BFA": "Burkina Faso",
    "SSD": "South Sudan",
    "DOM": "Dominican Republic",
    "PSX": "Palestine",
    "IOT": "British Indian Ocean Territory",
}

# ---------------------------------------------------------------------------
# UNINHABITED: real places, but nobody plans a holiday there. Kept in the list
# and shown on the map, sorted to the bottom of list views.
# ---------------------------------------------------------------------------
UNINHABITED = {
    "AQ",  # Antarctica
    "BV",  # Bouvet Island
    "HM",  # Heard Island and McDonald Islands
    "TF",  # French Southern Territories
    "UM",  # US Minor Outlying Islands
    "GS",  # South Georgia and South Sandwich Islands
    "IO",  # British Indian Ocean Territory (Diego Garcia, military only)
}

# ---------------------------------------------------------------------------
# UN_MEMBER: the 193 UN member states plus the two permanent observers, Holy
# See and Palestine — the "195" a traveller means when they say that number,
# as opposed to the UN's own 193. Everything else in the app's 252 (Kosovo,
# Taiwan, the dependencies, Somaliland, Northern Cyprus...) is real to travel
# to but not a seat at the UN, which is the whole reason this list exists:
# the passport tab lets a user count against whichever of those two numbers,
# or against all 252, matters to them.
# ---------------------------------------------------------------------------
UN_MEMBER = {
    "AF", "AL", "DZ", "AD", "AO", "AG", "AR", "AM", "AU", "AT", "AZ",
    "BS", "BH", "BD", "BB", "BY", "BE", "BZ", "BJ", "BT", "BO", "BA", "BW", "BR", "BN", "BG", "BF", "BI",
    "CV", "KH", "CM", "CA", "CF", "TD", "CL", "CN", "CO", "KM", "CG", "CD", "CR", "CI", "HR", "CU", "CY", "CZ",
    "DK", "DJ", "DM", "DO",
    "EC", "EG", "SV", "GQ", "ER", "EE", "SZ", "ET",
    "FJ", "FI", "FR",
    "GA", "GM", "GE", "DE", "GH", "GR", "GD", "GT", "GN", "GW", "GY",
    "HT", "HN", "HU",
    "IS", "IN", "ID", "IR", "IQ", "IE", "IL", "IT",
    "JM", "JP", "JO",
    "KZ", "KE", "KI", "KP", "KR", "KW", "KG",
    "LA", "LV", "LB", "LS", "LR", "LY", "LI", "LT", "LU",
    "MG", "MW", "MY", "MV", "ML", "MT", "MH", "MR", "MU", "MX", "FM", "MD", "MC", "MN", "ME", "MA", "MZ", "MM",
    "NA", "NR", "NP", "NL", "NZ", "NI", "NE", "NG", "MK", "NO",
    "OM",
    "PK", "PW", "PA", "PG", "PY", "PE", "PH", "PL", "PT",
    "QA",
    "RO", "RU", "RW",
    "KN", "LC", "VC", "WS", "SM", "ST", "SA", "SN", "RS", "SC", "SL", "SG", "SK", "SI", "SB", "SO",
    "ZA", "SS", "ES", "LK", "SD", "SR", "SE", "CH", "SY",
    "TJ", "TZ", "TH", "TL", "TG", "TO", "TT", "TN", "TR", "TM", "TV",
    "UG", "UA", "AE", "GB", "US", "UY", "UZ",
    "VU", "VE", "VN",
    "YE",
    "ZM", "ZW",
    # Permanent observers, not members, but who a "195" count means to include.
    "VA", "PS",
}

# ---------------------------------------------------------------------------
# SPLIT_ASSERTIONS: the regression test from PLAN.md section 3.
# Each tuple must come out as distinct, independently selectable places.
# If a future Natural Earth release merges any of these, the build fails loudly
# rather than silently shipping the bug this whole app exists to avoid.
# ---------------------------------------------------------------------------
SPLIT_ASSERTIONS = [
    ("DK", "GL", "FO"),
    ("FR", "GF", "GP", "MQ", "RE", "YT", "PM", "BL", "MF", "WF", "PF", "NC", "TF"),
    ("NL", "AW", "CW", "SX", "BQ"),
    ("US", "PR", "VI", "GU", "AS", "MP"),
    ("GB", "GI", "JE", "GG", "IM", "FK", "BM", "KY", "TC", "VG", "SH", "AI", "MS", "PN"),
    ("CN", "HK", "MO", "TW"),
    ("NO", "SJ", "BV"),
    ("NZ", "CK", "NU", "TK"),
    ("AU", "CX", "CC", "NF", "HM"),
    ("FI", "AX"),
    ("CY", "XN"),
    ("SO", "XS"),
    ("RS", "XK"),
    ("IL", "PS"),
]

# ---------------------------------------------------------------------------
# MERGE_ASSERTIONS: codes that must NOT exist as separate places, because the
# granularity decision says the parent is one place. Guards the other direction.
# ---------------------------------------------------------------------------
MERGE_ASSERTIONS = [
    "SCT", "WLS", "NIR",   # UK home nations folded into the England anchor
    "PAZ", "PMD",          # Azores, Madeira
    "TZZ",                 # Zanzibar
    "BCR", "BWR",          # Belgian regions folded into the Flanders anchor
    "BHB", "BIS",          # Brcko, Republika Srpska
]
