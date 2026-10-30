package com.bismopower.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bismopower.app.audio.CUSTOM_PRESET
import com.bismopower.app.audio.EQ_PRESETS
import com.bismopower.app.audio.EqBand
import com.bismopower.app.audio.EqSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Archivo aparte para el ecualizador, así sus cambios no despiertan al resto de ajustes. */
private val Context.eqStore: DataStore<Preferences> by preferencesDataStore(name = "equalizer")

/**
 * Guarda los ajustes del ecualizador y se los pasa al motor de audio con [apply].
 *
 * La pantalla cambia el estado al instante (para que se sienta fluida) y el guardado en disco
 * espera a que dejes de mover el dedo unos 400 ms.
 */
class EqRepository(
    private val context: Context,
    scope: CoroutineScope,
    private val apply: (EqSettings) -> Unit,
) {
    private val key = stringPreferencesKey("eq")
    private val _state = MutableStateFlow(EqSettings())
    val state: StateFlow<EqSettings> = _state.asStateFlow()

    init {
        scope.launch {
            val saved = context.eqStore.data.first()[key]?.let { EqSettings.decode(it) }
            if (saved != null) {
                _state.value = saved
                apply(saved)
            }
            _state.collectLatest { s ->
                delay(400)
                context.eqStore.edit { it[key] = s.encode() }
            }
        }
    }

    private fun set(s: EqSettings) {
        _state.value = s
        apply(s)
    }

    fun setEnabled(on: Boolean) = set(_state.value.copy(enabled = on))

    fun setLimiter(on: Boolean) = set(_state.value.copy(limiter = on))

    fun setPreamp(db: Float) = set(_state.value.copy(preampDb = db))

    fun setBass(db: Float) = set(_state.value.copy(bassDb = db))

    fun setTreble(db: Float) = set(_state.value.copy(trebleDb = db))

    /** Cambia una banda; cualquier cambio a mano convierte el ajuste en "Personalizado". */
    fun setBand(
        index: Int,
        transform: (EqBand) -> EqBand,
    ) {
        val s = _state.value
        if (index !in s.bands.indices) return
        val bands = s.bands.toMutableList()
        bands[index] = transform(bands[index])
        set(s.copy(bands = bands, preset = CUSTOM_PRESET))
    }

    /** Aplica un ajuste de fábrica: vuelve las bandas a sus frecuencias normales con esas ganancias. */
    fun applyPreset(name: String) {
        val gains = EQ_PRESETS.firstOrNull { it.first == name }?.second ?: return
        set(_state.value.copy(bands = EqSettings.defaultBands(gains), preset = name))
    }

    /** Todo plano: bandas, preamplificador, graves y agudos en 0. */
    fun reset() = set(EqSettings(enabled = _state.value.enabled, limiter = _state.value.limiter))
}
