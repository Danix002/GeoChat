package it.unibo.collektive.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.unibo.collektive.ui.theme.Purple40
import it.unibo.collektive.ui.theme.Purple80

@Composable
fun ErrorPositionPopUp(onDismissClick: () -> Unit, onAllowClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ){
        Column(modifier = Modifier
            .width(300.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(color = Purple80)
        ) {
            Text(
                text = "To send a message the app need to access your location",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                color = Color.Black,
                textAlign = TextAlign.Center
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Purple40, contentColor = Color.White),
                    onClick = {
                        onAllowClick()
                    }
                ) {
                    Text(text = "Allow")
                }
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Purple40, contentColor = Color.White),
                    onClick = {
                        onDismissClick()
                    }
                ) {
                    Text(text = "Cancel")
                }
            }
        }
    }
}
