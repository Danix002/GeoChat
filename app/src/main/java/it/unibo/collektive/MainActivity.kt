@file:Suppress("FunctionNaming")

package it.unibo.collektive

import android.Manifest
import android.annotation.SuppressLint
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
                startApp(onRequestPermissions = { permissionManager() })
            } else {
                accessDenied()
            }
        }
        permissionManager()
    }

    /**
     * TODO: doc
     */
    private fun permissionManager(){
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions()
        }else{
            requestGeolocalization()
        }
    }

    /**
     * TODO: doc
     */
    private fun requestPermissions() {
        val locationPermissionRequest = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            when {
                permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                    requestGeolocalization()
                }
                permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                    requestGeolocalization()
                }
                else -> {
                    accessDenied()
                }
            }
        }
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
    @SuppressLint("MissingPermission")
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
            startApp(onRequestPermissions = { permissionManager() })
        }
        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    startLocationUpdates(locationRequest)
                    val intentSenderRequest = IntentSenderRequest.Builder(exception.resolution).build()
                    locationSettingsLauncher.launch(intentSenderRequest)
                } catch (sendEx: IntentSender.SendIntentException) {
                    accessDenied()
                }
            } else {
                accessDenied()
            }
        }
    }

    /**
     * TODO: doc
     */
    @SuppressLint("MissingPermission")
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

    /**
     * TODO: doc
     */
    private fun accessDenied(){
        setContent {
            CollektiveExampleAndroidTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(
                        modifier = Modifier.padding(innerPadding).fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ){
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
                                        permissionManager()
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
                                        startApp(onRequestPermissions = { permissionManager() })
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Purple40, contentColor = Color.White)
                                ) {
                                    Text(text = "Continue")
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
