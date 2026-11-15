package com.riedel.beyondborders.ui.visa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.riedel.beyondborders.R
import com.riedel.beyondborders.ui.asString
import com.riedel.beyondborders.ui.places.PartnerBlocker
import com.riedel.beyondborders.ui.places.PartnerComparison

/**
 * Partner mode's two answers for one place, the one that limits the pair, and the caveat.
 *
 * Shared by the map's card and the visa list's detail so the two cannot word the same
 * pair differently. The caveat is not decoration: the data is per passport only, and a
 * couple with a residence permit or an EU family-member right has better odds than this
 * says. Saying so next to the answer is what keeps "both can go" from reading as a
 * promise.
 */
@Composable
fun PartnerRows(comparison: PartnerComparison, modifier: Modifier = Modifier, showCaveat: Boolean = true) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            stringResource(R.string.visa_partner_you, requirementSummaryText(comparison.you).asString()),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (comparison.blocker == PartnerBlocker.YOU) FontWeight.SemiBold else null,
        )
        Text(
            stringResource(R.string.visa_partner_partner, requirementSummaryText(comparison.partner).asString()),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (comparison.blocker == PartnerBlocker.PARTNER) FontWeight.SemiBold else null,
        )
        Text(
            stringResource(
                when (comparison.blocker) {
                    PartnerBlocker.YOU -> R.string.visa_partner_blocker_you
                    PartnerBlocker.PARTNER -> R.string.visa_partner_blocker_partner
                    PartnerBlocker.NEITHER -> R.string.visa_partner_blocker_none
                }
            ),
            style = MaterialTheme.typography.labelMedium,
        )
        // The visa list says it once under the switch; repeating it on every opened row is noise.
        if (showCaveat) {
            Text(
                stringResource(R.string.visa_partner_caveat),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
