package org.mbnavigator.android.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mbnavigator.android.core.routing.MotorcycleRoutingProfile
import org.mbnavigator.android.core.voice.VoiceVerbosityLevel

private val ColorAsphaltBg = Color(0xFF090E17)
private val ColorSlateSurface = Color(0xFF131D2E)
private val ColorNeonGreen = Color(0xFF00E676)
private val ColorNeonAmber = Color(0xFFFF9100)
private val ColorCardBorder = Color(0xFF1E293B)

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier
) {
    var voiceVerbosity by remember { mutableStateOf(VoiceVerbosityLevel.STANDARD) }
    var dgtAlertsEnabled by remember { mutableStateOf(true) }
    var speedCameraAlertsEnabled by remember { mutableStateOf(true) }
    var gloveModeEnabled by remember { mutableStateOf(true) }
    var defaultMotoProfile by remember { mutableStateOf(MotorcycleRoutingProfile.CURVY) }
    var geminiAiEnabled by remember { mutableStateOf(false) }
    var geminiApiKey by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ColorAsphaltBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // Cabecera
        Text(
            text = "AJUSTES Y PREFERENCIAS",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
        Text(
            text = "Personaliza la navegación, intercomunicador de casco y asistentes",
            color = Color.Gray,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 1. GUIADO POR VOZ E INTERCOMUNICADOR
        Text("VOZ E INTERCOMUNICADOR DE CASCO", color = ColorNeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ColorSlateSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, ColorCardBorder),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Nivel de Verbosidad de Indicaciones", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VoiceVerbosityOption("Mínima", voiceVerbosity == VoiceVerbosityLevel.MINIMAL) { voiceVerbosity = VoiceVerbosityLevel.MINIMAL }
                    VoiceVerbosityOption("Estándar", voiceVerbosity == VoiceVerbosityLevel.STANDARD) { voiceVerbosity = VoiceVerbosityLevel.STANDARD }
                    VoiceVerbosityOption("Detallada", voiceVerbosity == VoiceVerbosityLevel.DETAILED) { voiceVerbosity = VoiceVerbosityLevel.DETAILED }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when (voiceVerbosity) {
                        VoiceVerbosityLevel.MINIMAL -> "Solo giros inminentes (< 100 m) y alertas críticas de peligro."
                        VoiceVerbosityLevel.STANDARD -> "Avisos anticipados (500 m) y confirmación de giro."
                        VoiceVerbosityLevel.DETAILED -> "Carril sugerido, advertencias meteorológicas y nombres de vía."
                    },
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. SEGURIDAD Y DGT 3.0
        Text("SEGURIDAD VIAL Y TRÁFICO", color = ColorNeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ColorSlateSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, ColorCardBorder),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                SettingsToggleRow(
                    title = "Alertas DGT 3.0 en Tiempo Real",
                    subtitle = "Balizas V16 activas, vehículos detenidos y cortes de vía",
                    checked = dgtAlertsEnabled,
                    onCheckedChange = { dgtAlertsEnabled = it }
                )
                Spacer(modifier = Modifier.height(12.dp))
                SettingsToggleRow(
                    title = "Aviso de Radares y Límites",
                    subtitle = "Radares fijos, de tramo y alerta de exceso de velocidad",
                    checked = speedCameraAlertsEnabled,
                    onCheckedChange = { speedCameraAlertsEnabled = it }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. INTELIGENCIA ARTIFICIAL (GEMINI - OPCIONAL)
        Text("COPILOTO CON INTELIGENCIA ARTIFICIAL", color = ColorNeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ColorSlateSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, ColorCardBorder),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                SettingsToggleRow(
                    title = "Habilitar Asistente Google Gemini",
                    subtitle = if (geminiAiEnabled) "Modo Nube activo con clave personal" else "Modo 100% Offline y Privado (Determinista)",
                    checked = geminiAiEnabled,
                    onCheckedChange = { geminiAiEnabled = it }
                )

                if (geminiAiEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = geminiApiKey,
                        onValueChange = { geminiApiKey = it },
                        label = { Text("Clave de API Gemini (opcional)") },
                        placeholder = { Text("AIzaSy...") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = ColorNeonGreen,
                            unfocusedBorderColor = ColorCardBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Si no tienes clave, el sistema funcionará automáticamente en modo offline.",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. ERGONOMÍA EN MOTO
        Text("ERGONOMÍA Y SOPORTE DE MANILLAR", color = ColorNeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ColorSlateSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, ColorCardBorder),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                SettingsToggleRow(
                    title = "Modo Guantes de Moto",
                    subtitle = "Botones y controles sobredimensionados para toques con guantes",
                    checked = gloveModeEnabled,
                    onCheckedChange = { gloveModeEnabled = it }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun VoiceVerbosityOption(title: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(if (isSelected) ColorNeonGreen else Color(0xFF1E293B), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            color = if (isSelected) Color.Black else Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(subtitle, color = Color.Gray, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ColorNeonGreen,
                checkedTrackColor = ColorSlateSurface
            )
        )
    }
}
