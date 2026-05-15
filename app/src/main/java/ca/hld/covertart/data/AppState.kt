package ca.hld.covertart.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "covertart")

/**
 * DataStore-backed persistent app state: master toggle, last-applied track,
 * and the current status string shown on the MainActivity screen.
 */
class AppState(private val context: Context) {

    private object Keys {
        val MASTER_ENABLED = booleanPreferencesKey("master_enabled")
        val LAST_TRACK = stringPreferencesKey("last_track")
        val STATUS = stringPreferencesKey("status")
    }

    val masterEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.MASTER_ENABLED] ?: true }

    val lastTrack: Flow<String> =
        context.dataStore.data.map { it[Keys.LAST_TRACK] ?: "" }

    val status: Flow<String> =
        context.dataStore.data.map { it[Keys.STATUS] ?: "Not started" }

    suspend fun setMasterEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MASTER_ENABLED] = enabled }
    }

    suspend fun setLastTrack(track: String) {
        context.dataStore.edit { it[Keys.LAST_TRACK] = track }
    }

    suspend fun setStatus(status: String) {
        context.dataStore.edit { it[Keys.STATUS] = status }
    }
}
