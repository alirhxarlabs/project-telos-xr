package com.alixarlabs.telosxr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.alixarlabs.telosxr.model.ConnectionState

/**
 * Connection overlay shown when not connected
 *
 * Displays connection status and instructions
 */
@Composable
fun ConnectionOverlay(
    connectionState: ConnectionState,
    onConnect: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            // Icon
            Text(
                text = "📹",
                style = MaterialTheme.typography.displayLarge
            )

            // Status message
            Text(
                text = when (connectionState) {
                    is ConnectionState.Disconnected -> "Disconnected"
                    is ConnectionState.Connecting -> "Connecting to Thor..."
                    is ConnectionState.Error -> "Connection Error"
                    else -> "Ready"
                },
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )

            if (connectionState is ConnectionState.Error) {
                Text(
                    text = connectionState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Red
                )
            }

            // Info text
            Text(
                text = "Video will auto-connect when surface is ready",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}
