package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.photo.PhotoResolutionMode
import com.example.ui.theme.TelecomCyan
import com.example.ui.theme.TelecomNavy

@Composable
fun PhotoResolutionDialog(
    currentMode: PhotoResolutionMode,
    currentProject: String = "projet01",
    onSelectMode: (PhotoResolutionMode) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp)
                .testTag("photo_resolution_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = TelecomNavy),
            elevation = CardDefaults.cardElevation(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(TelecomCyan.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = null,
                            tint = TelecomCyan,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Taille des photos enregistrées",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Configuration de la résolution terrain",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Dossier cible info
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF1E293B),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = TelecomCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Dossier : releve-terrain/$currentProject/photos/",
                            fontSize = 11.sp,
                            color = Color(0xFFCBD5E1),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Les 3 options de configuration demandées
                PhotoResolutionOptionCard(
                    mode = PhotoResolutionMode.NATIVE,
                    isSelected = currentMode == PhotoResolutionMode.NATIVE,
                    title = "Résolution native",
                    badge = "Plein capteur (~7+ Mo)",
                    description = "Capture d'origine sans compression. Détail maximal du capteur haute définition.",
                    icon = Icons.Default.CameraAlt,
                    onClick = { onSelectMode(PhotoResolutionMode.NATIVE) }
                )

                Spacer(modifier = Modifier.height(10.dp))

                PhotoResolutionOptionCard(
                    mode = PhotoResolutionMode.LIMIT_2MB,
                    isSelected = currentMode == PhotoResolutionMode.LIMIT_2MB,
                    title = "2 Mo",
                    badge = "Qualité optimale (max ~2 Mo)",
                    description = "Idéal pour le piquetage FTTH : lisibilité parfaite des étiquettes et câbles.",
                    icon = Icons.Default.HighQuality,
                    onClick = { onSelectMode(PhotoResolutionMode.LIMIT_2MB) }
                )

                Spacer(modifier = Modifier.height(10.dp))

                PhotoResolutionOptionCard(
                    mode = PhotoResolutionMode.LIMIT_1MB,
                    isSelected = currentMode == PhotoResolutionMode.LIMIT_1MB,
                    title = "1 Mo",
                    badge = "Standard allégé (max ~1 Mo)",
                    description = "Photos compactes. Idéal pour transfert mobile rapide et économie d'espace.",
                    icon = Icons.Default.Compress,
                    onClick = { onSelectMode(PhotoResolutionMode.LIMIT_1MB) }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Bouton Fermer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TelecomCyan,
                            contentColor = TelecomNavy
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("close_photo_resolution_dialog_button")
                    ) {
                        Text("Appliquer & Fermer", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoResolutionOptionCard(
    mode: PhotoResolutionMode,
    isSelected: Boolean,
    title: String,
    badge: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("photo_mode_${mode.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF1E293B) else Color(0xFF0F172A)
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) TelecomCyan else Color(0xFF334155)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = TelecomCyan,
                    unselectedColor = Color(0xFF64748B)
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (isSelected) TelecomCyan else Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) TelecomCyan.copy(alpha = 0.2f) else Color(0xFF334155)
                    ) {
                        Text(
                            text = badge,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) TelecomCyan else Color(0xFF94A3B8),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8),
                    lineHeight = 16.sp
                )
            }

            if (isSelected) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Sélectionné",
                    tint = TelecomCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
