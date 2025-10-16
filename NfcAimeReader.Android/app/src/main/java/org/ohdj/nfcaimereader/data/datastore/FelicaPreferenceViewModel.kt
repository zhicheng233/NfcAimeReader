package org.ohdj.nfcaimereader.data.datastore

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class FelicaState(
    val felicaCompatibilityMode: Boolean = true
)

val Context.felicaDataStore by preferencesDataStore(name = "felica_settings")

class FelicaPreferenceViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val dataStore = application.applicationContext.felicaDataStore

    companion object {
        private val FELICA_COMPATIBILITY_MODE = booleanPreferencesKey("felica_compatibility_mode")
    }

    private val _felicaState: MutableStateFlow<FelicaState> = MutableStateFlow(FelicaState())
    val felicaState: StateFlow<FelicaState> = _felicaState

    init {
        viewModelScope.launch {
            dataStore.data
                .map { prefs ->
                    FelicaState(
                        felicaCompatibilityMode = prefs[FELICA_COMPATIBILITY_MODE] ?: true
                    )
                }
                .collect { state ->
                    _felicaState.value = state
                }
        }
    }

    fun updateFelicaCompatibility(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[FELICA_COMPATIBILITY_MODE] = enabled
            }
            _felicaState.value = _felicaState.value.copy(
                felicaCompatibilityMode = enabled
            )
        }
    }

    suspend fun getFelicaCompatibility(): Boolean {
        val prefs = dataStore.data.first()
        return prefs[booleanPreferencesKey("felica_compatibility_mode")] ?: true
    }
}
