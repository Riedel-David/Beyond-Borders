package com.riedel.beyondborders.ui.passport

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riedel.beyondborders.R
import com.riedel.beyondborders.data.place.Place
import com.riedel.beyondborders.data.place.PlaceStatus
import com.riedel.beyondborders.ui.MAX_CONTENT_WIDTH_DP
import com.riedel.beyondborders.ui.asString
import com.riedel.beyondborders.ui.places.PlacesViewModel
import com.riedel.beyondborders.ui.places.geoNameText
import com.riedel.beyondborders.ui.places.isQuickAddLocked
import com.riedel.beyondborders.ui.places.quickAddChecked
import com.riedel.beyondborders.ui.places.quickAddRows
import com.riedel.beyondborders.ui.places.quickAddSections
import com.riedel.beyondborders.ui.places.quickAddTarget
import com.riedel.beyondborders.ui.places.sortSectionsByLabel
import com.riedel.beyondborders.ui.places.Grouping

/**
 * A full-screen list of the UN member states with one "Been" checkbox each, for
 * marking many places in one sitting. Every tap writes straight through
 * [PlacesViewModel.setStatus] — there is no Save, so nothing is lost if the process
 * dies mid-list, and closing the dialog has nothing to confirm.
 *
 * Only that one place is written: Denmark does not tick Greenland. The search text is
 * local rather than the Passport tab's shared query, so opening this does not leave a
 * filter behind on the list underneath.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickAddDialog(viewModel: PlacesViewModel, onDismiss: () -> Unit) {
    val places by viewModel.places.collectAsStateWithLifecycle()
    val statuses by viewModel.statuses.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    // Keyed without statuses: a tick must not rebuild or re-sort the list under the
    // finger. Each row reads its own status below.
    val sections = remember(places, query, locale) {
        val rows = sortSectionsByLabel(quickAddRows(places, query), Grouping.CONTINENT) {
            geoNameText(it).asString(context)
        }
        quickAddSections(rows)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = MAX_CONTENT_WIDTH_DP.dp).fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.quick_add_title),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.quick_add_close))
                        }
                    }
                    SearchField(
                        query = query,
                        onQuery = { query = it },
                        label = stringResource(R.string.quick_add_search),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    LazyColumn(Modifier.fillMaxSize()) {
                        sections.forEach { (continent, rows) ->
                            stickyHeader(key = "header-$continent", contentType = "header") {
                                Text(
                                    geoNameText(continent).asString(),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceContainer)
                                        .heightIn(min = 48.dp)
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                        .wrapContentHeight(Alignment.CenterVertically),
                                )
                            }
                            items(rows, key = { it.place.code }, contentType = { "row" }) { row ->
                                QuickAddRow(row.place, statuses[row.place.code] ?: PlaceStatus.NONE) { target ->
                                    viewModel.vibrate()
                                    viewModel.setStatus(row.place.code, target)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAddRow(place: Place, status: PlaceStatus, onSet: (PlaceStatus) -> Unit) {
    val name = place.name
    val locked = isQuickAddLocked(status)
    val target = quickAddTarget(status)
    val statusText = statusLabelRes(status)?.let { stringResource(it) }
    // A locked row has no "Been to" claim to make; it reads as name plus its status.
    val description = if (locked && statusText != null) {
        stringResource(R.string.passport_status_chip_desc, name, statusText)
    } else {
        stringResource(R.string.quick_add_been_desc, name)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(
                value = quickAddChecked(status),
                enabled = target != null,
                role = Role.Checkbox,
                onValueChange = { target?.let(onSet) },
            )
            .semantics { contentDescription = description }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FlagThumb(place.code)
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        // Lived and Want both show their status; only Lived is inert.
        if (statusText != null) {
            Text(
                statusText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Non-interactive: the row is the single tap target, like the passport picker's
        // radio buttons, so TalkBack gets one control per country.
        Checkbox(
            checked = quickAddChecked(status),
            onCheckedChange = null,
            enabled = !locked,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}
