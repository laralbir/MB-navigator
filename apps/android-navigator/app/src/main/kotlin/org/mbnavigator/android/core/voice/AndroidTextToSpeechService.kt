package org.mbnavigator.android.core.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Servicio de síntesis de voz (TTS) optimizado para cascos e intercomunicadores de moto.
 */
class AndroidTextToSpeechService(
    private val context: Context
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<Voice>>(emptyList())
    val availableVoices: StateFlow<List<Voice>> = _availableVoices.asStateFlow()

    private val _selectedLanguage = MutableStateFlow(Locale("es", "ES"))
    val selectedLanguage: StateFlow<Locale> = _selectedLanguage.asStateFlow()

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { engine ->
                // Configurar canal de audio de Navegación para atenuar música en intercomunicador
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                engine.setAudioAttributes(audioAttributes)

                val result = engine.setLanguage(_selectedLanguage.value)
                if (result == TextToSpeech.LANG_AVAILABLE || result == TextToSpeech.LANG_COUNTRY_AVAILABLE) {
                    _isInitialized.value = true
                } else {
                    engine.setLanguage(Locale.getDefault())
                    _isInitialized.value = true
                }

                // Cargar voces instaladas
                try {
                    val voices = engine.voices?.filter { it.locale.language == "es" || it.locale.language == "en" }
                    if (!voices.isNullOrEmpty()) {
                        _availableVoices.value = voices.sortedBy { it.name }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun speak(text: String, flushQueue: Boolean = true) {
        if (!_isInitialized.value || tts == null) return

        val queueMode = if (flushQueue) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        tts?.speak(text, queueMode, params, "MB_NAV_${System.currentTimeMillis()}")
    }

    fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate)
    }

    fun setPitch(pitch: Float) {
        tts?.setPitch(pitch)
    }

    fun setLanguage(locale: Locale) {
        _selectedLanguage.value = locale
        tts?.setLanguage(locale)
    }

    /**
     * Lanza la pantalla de configuración del sistema para descargar e instalar paquetes de voz de alta calidad.
     */
    fun openVoiceDownloadSettings(): Intent {
        return Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
