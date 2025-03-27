package it.unibo.collektive.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import it.unibo.collektive.ui.theme.Pink40
import it.unibo.collektive.ui.theme.Pink80
import it.unibo.collektive.ui.theme.Purple80
import it.unibo.collektive.ui.theme.Purple40

@Composable
fun EchoLogo() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = "e", fontSize = 32.sp, color = Purple80, fontWeight = FontWeight.Bold)
        Text(text = "c", fontSize = 32.sp, color = Pink40, fontWeight = FontWeight.Bold)
        Text(text = "h", fontSize = 32.sp, color = Pink80, fontWeight = FontWeight.Bold)
        Text(text = "o", fontSize = 32.sp, color = Purple40, fontWeight = FontWeight.Bold)
    }
}
