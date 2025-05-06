@file:Suppress("FunctionNaming")

package it.unibo.collektive

import android.Manifest
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.LocationOn
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.Priority
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.Task
import it.unibo.collektive.navigation.NavigationInitializer
import it.unibo.collektive.navigation.Pages
import androidx.compose.ui.unit.dp
import it.unibo.collektive.ui.theme.CollektiveExampleAndroidTheme
import it.unibo.collektive.ui.theme.Purple40
import it.unibo.collektive.viewmodels.NearbyDevicesViewModel
import it.unibo.collektive.viewmodels.CommunicationSettingViewModel
import it.unibo.collektive.viewmodels.MessagesViewModel

/**
 * Main entry point for the Android app.
 */
class MainActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationSettingsLauncher: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var locationPermissionRequest: ActivityResultLauncher<Array<String>>
    private lateinit var locationCallback: LocationCallback

    /**
     * TODO: doc
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) { }
        }
        locationSettingsLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                startApp(onRequestPermissions = { permissionManager(start = false) })
            } else {
                accessDenied(this)
            }
        }
        locationPermissionRequest = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            when {
                permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                    permissionManager(start = true)
                }
                permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                    permissionManager(start = true)
                }
                else -> {
                    accessDenied(this)
                }
            }
        }
        permissionManager(start = true)
    }

    /**
     * TODO: doc
     */
    private fun permissionManager(start: Boolean){
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if(start) {
                requestPermissions()
            } else {
                requestPermissionOutOfApp()
            }
        }else{
            requestGeolocalization()
        }
    }

    /**
     * TODO: doc
     */
    private fun requestPermissions() {
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    /**
     * TODO: doc
     */
    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun requestGeolocalization() {
        val locationRequest = LocationRequest
            .Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMinUpdateIntervalMillis(5000)
            .build()
        val builder = LocationSettingsRequest
            .Builder()
            .addLocationRequest(locationRequest)
        val client: SettingsClient = LocationServices
            .getSettingsClient(this)
        val task: Task<LocationSettingsResponse> = client
            .checkLocationSettings(
                builder.build()
            )
        task.addOnSuccessListener {
            startApp(onRequestPermissions = { permissionManager(start = false) })
        }
        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    startLocationUpdates(locationRequest)
                    val intentSenderRequest = IntentSenderRequest.Builder(exception.resolution).build()
                    locationSettingsLauncher.launch(intentSenderRequest)
                } catch (sendEx: IntentSender.SendIntentException) {
                    accessDenied(this)
                }
            } else {
                accessDenied(this)
            }
        }
    }

    /**
     * TODO: doc
     */
    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun startLocationUpdates(locationRequest: LocationRequest) {
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    /**
     * TODO: doc
     */
    private fun startApp(onRequestPermissions: () -> Unit){
        setContent {
            CollektiveExampleAndroidTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Initialization(Modifier.padding(innerPadding), fusedLocationClient, onRequestPermissions)
                }
            }
        }
    }

    private fun requestPermissionOutOfApp(){
        setContent {
            CollektiveExampleAndroidTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(
                        modifier = Modifier.padding(innerPadding).fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ){
                        Column(
                            modifier = Modifier.width(300.dp),
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            Text(
                                text = "1. Please go to settings",
                                textAlign = TextAlign.Start
                            )
                            Text(
                                text = "2. Give location access permission",
                                textAlign = TextAlign.Start
                            )
                            Text(
                                text = "3. Close and reopen the app",
                                textAlign = TextAlign.Start
                            )
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.TwoTone.LocationOn,
                                    contentDescription = "Access denied",
                                    tint = Purple40
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * TODO: doc
     */
    private fun accessDenied(context: Context){
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            setContent {
                CollektiveExampleAndroidTheme {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        Box(
                            modifier = Modifier.padding(innerPadding).fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "This app needs access to your location to work",
                                    textAlign = TextAlign.Center
                                )
                                Icon(
                                    imageVector = Icons.TwoTone.Warning,
                                    contentDescription = "Access denied",
                                    tint = Purple40
                                )
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Button(
                                        onClick = {
                                            permissionManager(start = false)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Purple40,
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Text(text = "Request Permission")
                                    }
                                }
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Button(
                                        onClick = {
                                            startApp(onRequestPermissions = { permissionManager(start = false) })
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Purple40,
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Text(text = "Continue")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }else{
            requestPermissionOutOfApp()
        }
    }
}

@Composable
private fun Initialization(
    modifier: Modifier,
    fusedLocationProviderClient: FusedLocationProviderClient,
    onRequestPermissions: () -> Unit,
    nearbyDevicesViewModel: NearbyDevicesViewModel = viewModel(),
    communicationSettingViewModel: CommunicationSettingViewModel = viewModel(),
    messagesViewModel: MessagesViewModel = viewModel()
) {
    NavigationInitializer(
        communicationSettingViewModel,
        nearbyDevicesViewModel,
        messagesViewModel,
        Pages.Home.route,
        modifier,
        fusedLocationProviderClient,
        onRequestPermissions
    )
}
