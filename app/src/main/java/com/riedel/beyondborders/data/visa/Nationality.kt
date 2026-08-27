package com.riedel.beyondborders.data.visa

/**
 * The passport whose visa answers the app shows. The name is also the ISO
 * 3166-1 alpha-2 code and the asset's `passport` field — see [VisaRegistry.assetName].
 *
 * Two today, matching tools/visa/rules.py's PASSPORTS. Adding a third means adding
 * a case here *and* curating that passport's answers in rules.py; the second half
 * is the real work.
 */
enum class Nationality { DE, GB }
