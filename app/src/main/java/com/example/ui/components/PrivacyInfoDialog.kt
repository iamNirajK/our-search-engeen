package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PrivacyInfoDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = Color(0xFF0284C7),
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = "Privacy & Policy Disclosures",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                SectionItem(
                    title = "Strict No-History Architecture",
                    body = "NK APP is intentionally engineered without any local database, chat log store, or search history recorder. Your AI conversations and visited URLs reside exclusively in volatile memory and are permanently discarded when you exit the app or start a new conversation."
                )

                Spacer(modifier = Modifier.height(12.dp))

                SectionItem(
                    title = "Brave-Style Content Blocking",
                    body = "The in-app browser uses on-device request interception to block known third-party tracking pixels, advertising scripts, and telemetry beacons based on EasyList and EasyPrivacy rules. Note: Some first-party sponsored links and server-side ads served directly by web platforms may not be technically separable."
                )

                Spacer(modifier = Modifier.height(12.dp))

                SectionItem(
                    title = "Third-Party Processing Notice",
                    body = "While NK APP stores nothing on your device, upstream providers (including Google Search, the Gemini API backend, and hosting networks) process technical metadata, IP addresses, and transmitted prompts in accordance with their respective privacy policies and terms of service."
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got It", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun SectionItem(title: String, body: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.sp
        )
    }
}
