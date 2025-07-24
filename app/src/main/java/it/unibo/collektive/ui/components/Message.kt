package it.unibo.collektive.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.unibo.collektive.ui.theme.LightGray
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import it.unibo.collektive.model.Message
import it.unibo.collektive.ui.theme.Purple40
import it.unibo.collektive.viewmodels.CommunicationSettingViewModel
import kotlin.random.Random

@Composable
fun Message(message: Message, isSentByUser: Boolean, communicationSettingViewModel: CommunicationSettingViewModel) {
    val senderColor = Color(Random.nextFloat(), Random.nextFloat(), Random.nextFloat(), 1f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isSentByUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .padding(8.dp)
                .background(LightGray, RoundedCornerShape(20.dp))
                .padding(12.dp)
                .widthIn(max = 215.dp)
        ) {
            Column {
                if(!isSentByUser) {
                    Text(
                        text = message.userName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = senderColor
                    )
                }
                Text(
                    text = message.text,
                    fontSize = 16.sp,
                    color = Color.Black
                )
                Text(
                    text = "sent: ${message.time}, distance: ${communicationSettingViewModel.formatDistance(message.distance)}",
                    fontSize = 12.sp,
                    color = Purple40
                )
            }
        }
    }
}
