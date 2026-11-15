package org.mbnavigator.android.ui.settings

import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mbnavigator.android.core.voice.AndroidTextToSpeechService
import org.mbnavigator.android.core.voice.VoiceVerbosityLevel
import java.util.Locale

private val ColorAsphaltBg = Color(0xFF090E17)
private val ColorSlateSurface = Color(0xFF131D2E)
private val ColorNeonGreen = Color(0xFF00E676)
private val ColorNeonAmber = Color(0xFFFF9100)
private val ColorCardBorder = Color(0xFF1E293B)

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ttsService = remember { AndroidTextToSpeechService(context) }

    DisposableEffect(Unit) {
        onDispose {
            ttsService.shutdown()
        }
    }

    var voiceVerbosity by remember { mutableStateOf(VoiceVerbosityLevel.STANDARD) }
    var speechSpeedRate by remember { mutableFloatStateOf(1.0f) }
    var selectedLanguageName by remember { mutableStateOf("Español (España)") }
    var dgtAlertsEnabled by remember { mutableStateOf(true) }
    var speedCameraAlertsEnabled by remember { mutableStateOf(true) }
    var geminiAiEnabled by remember { mutableStateOf(false) }
    var geminiApiKey by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ColorAsphaltBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        // Cabecera
        Text(
            text = "AJUSTES Y PREFERENCIAS",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
        Text(
            text = "Configuración de audio para casco, DGT 3.0, mapas y asistente IA",
            color = Color.Gray,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // -----------------------------------------------------------------
        // 1. SÍNTESIS DE VOZ (TTS) Y VOCES PARA CASCO / INTERCOMUNICADOR
        // -----------------------------------------------------------------
        Text("GUÍA POR VOZ Y VOCES DESCARGABLES (TTS)", color = ColorNeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Black)
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ColorSlateSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, ColorCardBorder),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Nivel de Verbosidad de Indicaciones", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BigVoiceOption("Mínima", voiceVerbosity == VoiceVerbosityLevel.MINIMAL, Modifier.weight(1f)) {
                        voiceVerbosity = VoiceVerbosityLevel.MINIMAL
                    }
                    BigVoiceOption("Estándar", voiceVerbosity == VoiceVerbosityLevel.STANDARD, Modifier.weight(1f)) {
                        voiceVerbosity = VoiceVerbosityLevel.STANDARD
                    }
                    BigVoiceOption("Detallada", voiceVerbosity == VoiceVerbosityLevel.DETAILED, Modifier.weight(1f)) {
                        voiceVerbosity = VoiceVerbosityLevel.DETAILED
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = when (voiceVerbosity) {
                        VoiceVerbosityLevel.MINIMAL -> "• Solo giros a menos de 100 m y alertas de peligro crítico."
                        VoiceVerbosityLevel.STANDARD -> "• Aviso previo a 500 m y confirmación de giro."
                        VoiceVerbosityLevel.DETAILED -> "• Carril recomendado, nombre de vía y advertencias meteorológicas."
                    },
                    color = Color.LightGray,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Idioma y Motor de Voz
                Text("Voz del Sistema", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BigVoiceOption("Español (ES)", selectedLanguageName == "Español (España)", Modifier.weight(1f)) {
                        selectedLanguageName = "Español (España)"
                        ttsService.setLanguage(Locale("es", "ES"))
                    }
                    BigVoiceOption("Español (LATAM)", selectedLanguageName == "Español (Latinoamérica)", Modifier.weight(1f)) {
                        selectedLanguageName = "Español (Latinoamérica)"
                        ttsService.setLanguage(Locale("es", "US"))
                    }
                    BigVoiceOption("English", selectedLanguageName == "English (US)", Modifier.weight(1f)) {
                        selectedLanguageName = "English (US)"
                        ttsService.setLanguage(Locale.US)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Velocidad de Habla
                Text("Velocidad de Habla de las Indicaciones", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BigVoiceOption("Pausada (0.85x)", speechSpeedRate == 0.85f, Modifier.weight(1f)) {
                        speechSpeedRate = 0.85f
                        ttsService.setSpeechRate(0.85f)
                    }
                    BigVoiceOption("Normal (1.0x)", speechSpeedRate == 1.0f, Modifier.weight(1f)) {
                        speechSpeedRate = 1.0f
                        ttsService.setSpeechRate(1.0f)
                    }
                    BigVoiceOption("Ágil (1.15x)", speechSpeedRate == 1.15f, Modifier.weight(1f)) {
                        speechSpeedRate = 1.15f
                        ttsService.setSpeechRate(1.15f)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Botón Probar Voz de Navegación
                Button(
                    onClick = {
                        ttsService.speak("En trescientos cincuenta metros, curva cerrada a la derecha en la M 601 hacia Puerto de Navacerrada")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ColorNeonGreen),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text("🔊 PROBAR VOZ DE NAVEGACIÓN", color = ColorNeonGreen, fontWeight = FontWeight.Black, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Botón Descargar Voces Adicionales de Sistema
                Button(
                    onClick = {
                        try {
                            val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            // Fallback al menú de ajustes de accesibilidad / TTS
                            val fallbackIntent = Intent("com.android.settings.TTS_SETTINGS").apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(fallbackIntent)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ColorCardBorder),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text("⬇️ DESCARGAR / INSTALAR VOCES EN EL MÓVIL", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // -----------------------------------------------------------------
        // 2. COPILOTO INTELIGENTE (GOOGLE GEMINI) Y OBTENCIÓN DE API KEY
        // -----------------------------------------------------------------
        Text("COPILOTO CON INTELIGENCIA ARTIFICIAL", color = ColorNeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Black)
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
                    subtitle = if (geminiAiEnabled) "Modo Nube activo con clave personal" else "Modo 100% Offline Determinista (Sin costes ni consumo externo)",
                    checked = geminiAiEnabled,
                    onCheckedChange = { geminiAiEnabled = it }
                )

                if (geminiAiEnabled) {
                    Spacer(modifier = Modifier.height(14.dp))

                    // Botón para Obtener la API Key en Google AI Studio
                    Button(
                        onClick = {
                            val browserIntent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://aistudio.google.com/app/apikey")
                            ).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(browserIntent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorNeonAmber, contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text("🔑 OBTENER CLAVE API EN GOOGLE AI STUDIO", fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "1. Pulsa el botón arriba para abrir Google AI Studio con tu cuenta Google.\n" +
                        "2. Pulsa 'Create API Key' y copia tu clave gratuita.\n" +
                        "3. Pégala en el campo a continuación:",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = geminiApiKey,
                        onValueChange = { geminiApiKey = it },
                        label = { Text("Clave de API de Gemini") },
                        placeholder = { Text("AIzaSy...") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = ColorNeonGreen,
                            unfocusedBorderColor = ColorCardBorder
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // -----------------------------------------------------------------
        // 3. SEGURIDAD VIAL, TRÁFICO Y DGT 3.0
        // -----------------------------------------------------------------
        Text("SEGURIDAD VIAL Y TRÁFICO", color = ColorNeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Black)
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
                    subtitle = "Balizas V16 activas, vehículos detenidos y cortes de vía en tu sentido",
                    checked = dgtAlertsEnabled,
                    onCheckedChange = { dgtAlertsEnabled = it }
                )
                Spacer(modifier = Modifier.height(14.dp))
                SettingsToggleRow(
                    title = "Aviso de Radares y Límites",
                    subtitle = "Radares fijos, de tramo y alerta sonora de exceso de velocidad",
                    checked = speedCameraAlertsEnabled,
                    onCheckedChange = { speedCameraAlertsEnabled = it }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun BigVoiceOption(
    title: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(46.dp)
            .background(if (isSelected) ColorNeonGreen else Color(0xFF1E293B), RoundedCornerShape(10.dp))
            .border(1.dp, if (isSelected) ColorNeonGreen else ColorCardBorder, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (isSelected) Color.Black else Color.White,
            fontWeight = FontWeight.Black,
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
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(subtitle, color = Color.Gray, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.width(10.dp))
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
