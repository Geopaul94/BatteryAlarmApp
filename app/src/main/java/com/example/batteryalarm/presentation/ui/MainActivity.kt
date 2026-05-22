package com.example.batteryalarm.presentation.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.batteryalarm.presentation.viewmodel.BatteryViewModel
import com.example.batteryalarm.ui.theme.BatteryAlarmTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main Activity
 * Responsibility: Setup UI and request runtime permissions
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private lateinit var viewModel: BatteryViewModel

    // Request notification permission for Android 13+
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Handle permission result
        if (isGranted) {
            // Notifications are enabled
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        // Get ViewModel (Hilt provides it)
        viewModel = ViewModelProvider(this).get(BatteryViewModel::class.java)

        setContent {
            BatteryAlarmTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BatteryScreen(viewModel)
                }
            }
        }
    }
}
