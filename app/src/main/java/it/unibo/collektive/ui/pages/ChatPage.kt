package it.unibo.collektive.ui.pages

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import it.unibo.collektive.viewmodels.CommunicationSettingViewModel
import it.unibo.collektive.ui.components.CommunicationSettingSelector
import it.unibo.collektive.ui.components.EchoLogo
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import it.unibo.collektive.navigation.Pages
import it.unibo.collektive.ui.theme.Purple40

@Composable
fun ChatPage(communicationSettingViewModel: CommunicationSettingViewModel,
             navigationController: NavHostController,
             modifier: Modifier) {
    Column(modifier = modifier.then(Modifier.padding(20.dp)), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(modifier = Modifier
            .fillMaxWidth()
            .height(235.dp)
            .border(5.dp, Purple40, shape = RoundedCornerShape(16.dp))
            .padding(7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                EchoLogo()
                CommunicationSettingSelector(communicationSettingViewModel)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(50.dp)
                .border(1.dp, Purple40, shape = RoundedCornerShape(16.dp))
                .padding(7.dp)
        ) {
            IconButton(onClick = { navigationController.navigate(Pages.Home.route) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Home",
                    tint = Purple40
                )
            }
            Text(text = "Back to Home")
        }
    }
}
