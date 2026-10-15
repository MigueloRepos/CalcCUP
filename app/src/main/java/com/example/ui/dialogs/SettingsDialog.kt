package com.example.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.BuildConfig
import com.example.ui.theme.CubanBluePrimary
import com.example.ui.theme.CubanRed

@Composable
fun SettingsDialog(
    initialGeminiKey: String,
    initialOpenAiKey: String,
    initialProvider: String,
    hapticsEnabled: Boolean,
    showRareDenominations: Boolean,
    onSaveApiSettings: (geminiKey: String, openAiKey: String, provider: String) -> Unit,
    onToggleHaptics: (Boolean) -> Unit,
    onToggleRareDenominations: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit
) {
    var geminiKey by remember { mutableStateOf(initialGeminiKey) }
    var openAiKey by remember { mutableStateOf(initialOpenAiKey) }
    var selectedProvider by remember { mutableStateOf(initialProvider) }

    val hasBuiltInGemini = BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY"

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(24.dp))
                .testTag("settings_dialog"),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = CubanBluePrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Ajustes y Configuración",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Provider selector
                    Text(
                        text = "Motor de IA para reconocimiento",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedProvider = "GEMINI" },
                            color = if (selectedProvider == "GEMINI") CubanBluePrimary else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Google Gemini",
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedProvider == "GEMINI") androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Recomendado",
                                    fontSize = 11.sp,
                                    color = if (selectedProvider == "GEMINI") androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedProvider = "OPENAI" },
                            color = if (selectedProvider == "OPENAI") CubanBluePrimary else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "OpenAI GPT",
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedProvider == "OPENAI") androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Clave personalizada",
                                    fontSize = 11.sp,
                                    color = if (selectedProvider == "OPENAI") androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    if (selectedProvider == "GEMINI") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = if (hasBuiltInGemini) "✓ Clave Gemini integrada en la app" else "ℹ️ Gemini API",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (hasBuiltInGemini) CubanBluePrimary else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Si deseas usar tu propia clave de Google AI Studio, ingrésala a continuación:",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = geminiKey,
                                    onValueChange = { geminiKey = it },
                                    label = { Text("Clave API de Gemini (opcional)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("gemini_key_input")
                                )
                            }
                        }
                    } else {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Clave API de OpenAI",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Introduce tu API key (sk-...) para analizar con modelos de visión de OpenAI:",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = openAiKey,
                                    onValueChange = { openAiKey = it },
                                    label = { Text("OpenAI API Key") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("openai_key_input")
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    // Preferences
                    Text(
                        text = "Preferencias",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    // Haptic feedback switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Vibración al contar", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(text = "Respuesta táctil al pulsar los botones", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        Switch(
                            checked = hapticsEnabled,
                            onCheckedChange = onToggleHaptics
                        )
                    }

                    // Rare denominations switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Mostrar billetes de $3 y $1 CUP", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(text = "Billetes conmemorativos o históricos (Che Guevara y Martí)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        Switch(
                            checked = showRareDenominations,
                            onCheckedChange = onToggleRareDenominations
                        )
                    }

                    HorizontalDivider()

                    // Clear history button
                    OutlinedButton(
                        onClick = onClearHistory,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CubanRed)
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Borrar historial de movimientos")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        onSaveApiSettings(geminiKey, openAiKey, selectedProvider)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_settings_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CubanBluePrimary)
                ) {
                    Text("Guardar ajustes", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
