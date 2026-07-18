package family.tvlink.core

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

object SettingsStore {
    private val KEY_SENDER_NAME = stringPreferencesKey("sender_name")

    fun senderName(context: Context, default: String): Flow<String> =
        context.dataStore.data.map { prefs -> prefs[KEY_SENDER_NAME]?.ifBlank { null } ?: default }

    suspend fun setSenderName(context: Context, name: String) {
        context.dataStore.edit { prefs -> prefs[KEY_SENDER_NAME] = name.trim() }
    }
}
