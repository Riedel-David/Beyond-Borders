package com.riedel.beyondborders.ui.places

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.riedel.beyondborders.R

private const val ADVICE_BASE = "https://www.gov.uk/foreign-travel-advice/"

/**
 * ISO code to the UK FCDO travel-advice page slug. FCDO is the one source with a stable,
 * public, per-country URL that needs no account and is not blocked for scripted checks;
 * every slug below was fetched (HTTP 200) when this was written. Slugs are not always the
 * plain country name (`usa`, `the-gambia`, `cape-verde`), which is why this is a table
 * and not a formula. Places FCDO does not cover (the UK itself, the Holy See, Northern
 * Cyprus, Somaliland, most uninhabited territories) are absent on purpose: no row is
 * shown rather than a guessed link. Several French/Pacific territories share one page.
 */
private val ADVICE_SLUGS: Map<String, String> = mapOf(
        "AD" to "andorra", "AE" to "united-arab-emirates", "AF" to "afghanistan",
        "AG" to "antigua-and-barbuda", "AI" to "anguilla", "AL" to "albania", "AM" to "armenia",
        "AO" to "angola", "AQ" to "antarctica-british-antarctic-territory", "AR" to "argentina",
        "AT" to "austria", "AU" to "australia", "AW" to "aruba", "AZ" to "azerbaijan",
        "BA" to "bosnia-and-herzegovina", "BB" to "barbados", "BD" to "bangladesh", "BE" to "belgium",
        "BF" to "burkina-faso", "BG" to "bulgaria", "BH" to "bahrain", "BI" to "burundi", "BJ" to "benin",
        "BL" to "st-martin-and-st-barthelemy", "BM" to "bermuda", "BN" to "brunei", "BO" to "bolivia",
        "BQ" to "bonaire-st-eustatius-saba", "BR" to "brazil", "BS" to "bahamas", "BT" to "bhutan",
        "BW" to "botswana", "BY" to "belarus", "BZ" to "belize", "CA" to "canada",
        "CD" to "democratic-republic-of-the-congo", "CF" to "central-african-republic", "CG" to "congo",
        "CH" to "switzerland", "CI" to "cote-d-ivoire", "CK" to "cook-islands-tokelau-and-niue",
        "CL" to "chile", "CM" to "cameroon", "CN" to "china", "CO" to "colombia", "CR" to "costa-rica",
        "CU" to "cuba", "CV" to "cape-verde", "CW" to "curacao", "CY" to "cyprus", "CZ" to "czechia",
        "DE" to "germany", "DJ" to "djibouti", "DK" to "denmark", "DM" to "dominica",
        "DO" to "dominican-republic", "DZ" to "algeria", "EC" to "ecuador", "EE" to "estonia",
        "EG" to "egypt", "EH" to "western-sahara", "ER" to "eritrea", "ES" to "spain", "ET" to "ethiopia",
        "FI" to "finland", "FJ" to "fiji", "FK" to "falkland-islands",
        "FM" to "federated-states-of-micronesia", "FR" to "france", "GA" to "gabon", "GD" to "grenada",
        "GE" to "georgia", "GF" to "french-guiana", "GH" to "ghana", "GI" to "gibraltar",
        "GM" to "the-gambia", "GN" to "guinea", "GP" to "guadeloupe", "GQ" to "equatorial-guinea",
        "GR" to "greece", "GS" to "south-georgia-and-south-sandwich-islands", "GT" to "guatemala",
        "GW" to "guinea-bissau", "GY" to "guyana", "HK" to "hong-kong", "HN" to "honduras",
        "HR" to "croatia", "HT" to "haiti", "HU" to "hungary", "ID" to "indonesia", "IE" to "ireland",
        "IL" to "israel", "IN" to "india", "IO" to "british-indian-ocean-territory", "IQ" to "iraq",
        "IR" to "iran", "IS" to "iceland", "IT" to "italy", "JM" to "jamaica", "JO" to "jordan",
        "JP" to "japan", "KE" to "kenya", "KG" to "kyrgyzstan", "KH" to "cambodia", "KI" to "kiribati",
        "KM" to "comoros", "KN" to "st-kitts-and-nevis", "KP" to "north-korea", "KR" to "south-korea",
        "KW" to "kuwait", "KY" to "cayman-islands", "KZ" to "kazakhstan", "LA" to "laos",
        "LB" to "lebanon", "LC" to "st-lucia", "LI" to "liechtenstein", "LK" to "sri-lanka",
        "LR" to "liberia", "LS" to "lesotho", "LT" to "lithuania", "LU" to "luxembourg", "LV" to "latvia",
        "LY" to "libya", "MA" to "morocco", "MC" to "monaco", "MD" to "moldova", "ME" to "montenegro",
        "MF" to "st-martin-and-st-barthelemy", "MG" to "madagascar", "MH" to "marshall-islands",
        "MK" to "north-macedonia", "ML" to "mali", "MM" to "myanmar", "MN" to "mongolia", "MO" to "macao",
        "MQ" to "martinique", "MR" to "mauritania", "MS" to "montserrat", "MT" to "malta",
        "MU" to "mauritius", "MV" to "maldives", "MW" to "malawi", "MX" to "mexico", "MY" to "malaysia",
        "MZ" to "mozambique", "NA" to "namibia", "NC" to "new-caledonia", "NE" to "niger",
        "NG" to "nigeria", "NI" to "nicaragua", "NL" to "netherlands", "NO" to "norway", "NP" to "nepal",
        "NR" to "nauru", "NU" to "cook-islands-tokelau-and-niue", "NZ" to "new-zealand", "OM" to "oman",
        "PA" to "panama", "PE" to "peru", "PF" to "french-polynesia", "PG" to "papua-new-guinea",
        "PH" to "philippines", "PK" to "pakistan", "PL" to "poland", "PM" to "st-pierre-and-miquelon",
        "PN" to "pitcairn-island", "PS" to "palestine", "PT" to "portugal", "PW" to "palau",
        "PY" to "paraguay", "QA" to "qatar", "RE" to "reunion", "RO" to "romania", "RS" to "serbia",
        "RU" to "russia", "RW" to "rwanda", "SA" to "saudi-arabia", "SB" to "solomon-islands",
        "SC" to "seychelles", "SD" to "sudan", "SE" to "sweden", "SG" to "singapore",
        "SH" to "st-helena-ascension-and-tristan-da-cunha", "SI" to "slovenia", "SK" to "slovakia",
        "SL" to "sierra-leone", "SM" to "san-marino", "SN" to "senegal", "SO" to "somalia",
        "SR" to "suriname", "SS" to "south-sudan", "ST" to "sao-tome-and-principe", "SV" to "el-salvador",
        "SY" to "syria", "SZ" to "eswatini", "TC" to "turks-and-caicos-islands", "TD" to "chad",
        "TG" to "togo", "TH" to "thailand", "TJ" to "tajikistan", "TK" to "cook-islands-tokelau-and-niue",
        "TL" to "timor-leste", "TM" to "turkmenistan", "TN" to "tunisia", "TO" to "tonga",
        "TR" to "turkey", "TT" to "trinidad-and-tobago", "TV" to "tuvalu", "TW" to "taiwan",
        "TZ" to "tanzania", "UA" to "ukraine", "UG" to "uganda", "US" to "usa", "UY" to "uruguay",
        "UZ" to "uzbekistan", "VC" to "st-vincent-and-the-grenadines", "VE" to "venezuela",
        "VG" to "british-virgin-islands", "VN" to "vietnam", "VU" to "vanuatu",
        "WF" to "wallis-and-futuna", "WS" to "samoa", "XK" to "kosovo", "YE" to "yemen", "YT" to "mayotte",
        "ZA" to "south-africa", "ZM" to "zambia", "ZW" to "zimbabwe",
)

/**
 * The advice page for [countryCode] (upper or lower case), or null where there is none.
 * Pure: builds a string, makes no network call.
 */
internal fun travelAdviceUrl(countryCode: String): String? =
    ADVICE_SLUGS[countryCode.uppercase()]?.let { ADVICE_BASE + it }

/**
 * A link-out to current travel advice. Opens the browser; the app stores nothing and has
 * no INTERNET permission of its own. Renders nothing when there is no page for the place.
 */
@Composable
fun TravelAdviceRow(countryCode: String, modifier: Modifier = Modifier) {
    val url = travelAdviceUrl(countryCode) ?: return
    val context = LocalContext.current
    var failed by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        TextButton(onClick = {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // No browser installed is a real state on a locked-down device; say so
            // instead of crashing.
            failed = try {
                context.startActivity(intent)
                false
            } catch (_: ActivityNotFoundException) {
                true
            }
        }) {
            Text(stringResource(R.string.advice_link))
        }
        Text(
            stringResource(if (failed) R.string.advice_no_browser else R.string.advice_note),
            style = MaterialTheme.typography.bodySmall,
            color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}
