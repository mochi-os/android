package org.mochios.android.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.android.R
import org.mochios.android.model.PlaceData

@Composable
fun TravellingPicker(
    origin: PlaceData?,
    destination: PlaceData?,
    onOriginSelected: (PlaceData) -> Unit,
    onDestinationSelected: (PlaceData) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.place_origin),
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(modifier = Modifier.height(4.dp))
        PlacePicker(
            place = origin,
            onPlaceSelected = onOriginSelected
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.place_destination),
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(modifier = Modifier.height(4.dp))
        PlacePicker(
            place = destination,
            onPlaceSelected = onDestinationSelected
        )
    }
}
