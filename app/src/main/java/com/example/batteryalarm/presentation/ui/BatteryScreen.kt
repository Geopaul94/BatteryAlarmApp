package com.example.batteryalarm.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.batteryalarm.domain.model.BatteryState
import com.example.batteryalarm.presentation.viewmodel.BatteryViewModel

/**
 * Main UI screen for battery monitoring
 * Displays current battery status and controls
 */
@Composable
fun BatteryScreen(viewModel: BatteryViewModel) {
    val batteryState = viewModel.batteryState.collectAsState()
    val isMonitoring = viewModel.isMonitoring.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Title
            Text(
                text = "Battery Monitor",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Battery Circle Display
            BatteryCircle(batteryState.value)

            // Battery Info Card
            BatteryInfoCard(batteryState.value)

            // Status Cards
            StatusCardsSection(batteryState.value)

            Spacer(modifier = Modifier.height(20.dp))

            // Monitoring Toggle
            MonitoringToggleCard(
                isMonitoring = isMonitoring.value,
                onToggle = { viewModel.toggleMonitoring() }
            )
        }
    }
}

@Composable
private fun BatteryCircle(batteryState: BatteryState) {
    val backgroundColor = when {
        batteryState.batteryPercentage >= 80 -> Color(0xFF4CAF50) // Green
        batteryState.batteryPercentage >= 50 -> Color(0xFF2196F3) // Blue
        batteryState.batteryPercentage >= 20 -> Color(0xFFFFC107) // Amber
        else -> Color(0xFFF44336) // Red
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(CircleShape)
            .background(backgroundColor)
            .size(200.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "${batteryState.batteryPercentage}%",
            fontSize = 64.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = if (batteryState.isCharging) "Charging 🔌" else "Discharging",
            fontSize = 16.sp,
            color = Color.White
        )
    }
}

@Composable
private fun BatteryInfoCard(batteryState: BatteryState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfoRow(label = "Battery Level:", value = "${batteryState.batteryPercentage}%")
            InfoRow(label = "Status:", value = if (batteryState.isCharging) "Charging ✓" else "Not Charging")
            InfoRow(label = "Plugged:", value = if (batteryState.isPlugged) "Yes" else "No")
            InfoRow(label = "Temperature:", value = "${batteryState.temperature}°C")
        }
    }
}

@Composable
private fun StatusCardsSection(batteryState: BatteryState) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (batteryState.isHighBattery) {
            AlertCard(
                title = "Battery Full 🔋",
                message = "Your device has reached 80%",
                backgroundColor = Color(0xFFC8E6C9)
            )
        }

        if (batteryState.isLowBattery) {
            AlertCard(
                title = "Low Battery ⚠️",
                message = "Battery is at 20% or below",
                backgroundColor = Color(0xFFFFCDD2)
            )
        }

        if (batteryState.isChargerConnectedButNotCharging) {
            AlertCard(
                title = "Check Charger 🔌",
                message = "Plugged but not charging. Check switch",
                backgroundColor = Color(0xFFFFE0B2)
            )
        }
    }
}

@Composable
private fun MonitoringToggleCard(isMonitoring: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Battery Monitoring",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = if (isMonitoring) "Active ✓" else "Inactive",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            Switch(
                checked = isMonitoring,
                onCheckedChange = { onToggle() }
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontWeight = FontWeight.Medium)
        Text(text = value, color = Color.Gray)
    }
}

@Composable
private fun AlertCard(title: String, message: String, backgroundColor: Color) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(text = message, fontSize = 12.sp, color = Color.Gray)
        }
    }
}
