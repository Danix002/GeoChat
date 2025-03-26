package it.unibo.collektive.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.sp

@Preview
@Composable
fun RangeSliderBox() {
    var time by remember { mutableFloatStateOf(5f) }
    var distance by remember { mutableFloatStateOf(2000f) }

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.width(50.dp)){
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "for", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(15.dp))
                }
            }
            Text(text = "1 sc")
            Slider(
                value = time,
                onValueChange = { time = it },
                valueRange = 1f..60f,
                modifier = Modifier.weight(1f)
            )
            Text(text = "1 min")
        }
        Text(text = formatTime(time), textAlign = TextAlign.Center)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.width(50.dp)){
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "to", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(15.dp))
                }
            }
            Text(text = "1 mt")
            Slider(
                value = distance,
                onValueChange = { distance = it },
                valueRange = 1f..10_000f,
                modifier = Modifier.weight(1f)
            )
            Text(text = "10 km")
        }
        Text(text = formatDistance(distance), textAlign = TextAlign.Center)
    }
}

/**
 * Function to format distance in meters or kilometers.
 */
fun formatDistance(value: Float): String {
    return if (value >= 1000) {
        "${(value / 1000).toInt()} km"
    } else {
        "${value.toInt()} mt"
    }
}

/**
 * Function to format time in seconds or minutes.
 */
fun formatTime(value: Float): String {
    return if (value.toInt() == 60) {
        "${(value / 60).toInt()} min"
    } else {
        "${value.toInt()} sc"
    }
}
