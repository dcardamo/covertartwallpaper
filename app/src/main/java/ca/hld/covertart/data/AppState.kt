package ca.hld.covertart.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "covertart")

/**
 * DataStore-backed persistent app state: master toggle, last-applied track,
 * and the current status string shown on the MainActivity screen.
 */
class AppState(context: Context) {

    private val appContext = context.applicationContext

    private object Keys {
        val MASTER_ENABLED = booleanPreferencesKey("master_enabled")
        val LAST_TRACK = stringPreferencesKey("last_track")
        val STATUS = stringPreferencesKey("status")
    }

    val masterEnabled: Flow<Boolean> =
        appContext.dataStore.data.map { it[Keys.MASTER_ENABLED] ?: true }.distinctUntilChanged()

    val lastTrack: Flow<String> =
        appContext.dataStore.data.map { it[Keys.LAST_TRACK] ?: "" }.distinctUntilChanged()

    val status: Flow<String> =
        appContext.dataStore.data.map { it[Keys.STATUS] ?: "Not started" }.distinctUntilChanged()

    suspend fun setMasterEnabled(enabled: Boolean) {
        appContext.dataStore.edit { it[Keys.MASTER_ENABLED] = enabled }
    }

    suspend fun setLastTrack(track: String) {
        appContext.dataStore.edit { it[Keys.LAST_TRACK] = track }
    }

    suspend fun setStatus(status: String) {
        appContext.dataStore.edit { it[Keys.STATUS] = status }
    }
}
