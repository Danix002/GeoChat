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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.unibo.collektive.ui.theme.Purple80
import it.unibo.collektive.ui.theme.Purple40
import it.unibo.collektive.viewmodels.NearbyDevicesViewModel

@Composable
fun UserNameEditPopUp(onDismissClick: ()-> Unit, currentUserName: String, nearbyDevicesViewModel: NearbyDevicesViewModel) {
    var userNameText by remember { mutableStateOf(currentUserName) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ){
        Column(modifier = Modifier
            .width(300.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(color = Purple80)
        ) {
            Text(
                text = "Modify your name",
                fontSize = 24.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                color = Color.Black
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    value = userNameText,
                    onValueChange = { userNameText = it },
                    placeholder = { Text(text = "Modify your name...", color = Color.Black) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Purple40,
                        unfocusedBorderColor = Purple40,
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black
                    )
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Purple40, contentColor = Color.White),
                    onClick = { onDismissClick() }) {
                    Text(text = "Cancel")
                }
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Purple40, contentColor = Color.White),
                    onClick = {
                        nearbyDevicesViewModel.setUserName(userNameText)
                        onDismissClick()
                    }) {
                    Text(text = "Confirm")
                }
            }
        }
    }
}
