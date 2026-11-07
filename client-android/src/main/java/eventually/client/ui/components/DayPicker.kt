package eventually.client.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eventually.client.R
import java.time.DayOfWeek
import java.time.format.TextStyle

@Composable
fun DayPicker(
    selected: Set<DayOfWeek>,
    onSelectedChange: (Set<DayOfWeek>) -> Unit,
    firstDayOfWeek: DayOfWeek,
    modifier: Modifier = Modifier
) {
    val locale = LocalConfiguration.current.locales[0]

    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        orderedDays(firstDayOfWeek).forEach { day ->
            FilterChip(
                selected = day in selected,
                onClick = { onSelectedChange(if (day in selected) selected - day else selected + day) },
                label = {
                    Text(
                        text = day.getDisplayName(TextStyle.NARROW, locale),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colorResource(R.color.daySelected),
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

fun orderedDays(firstDayOfWeek: DayOfWeek): List<DayOfWeek> =
    (0L until DayOfWeek.entries.size).map { firstDayOfWeek.plus(it) }
