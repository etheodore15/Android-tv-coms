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

    private val KEY_TARGET_TV = stringPreferencesKey("target_tv")

    /** Phone-side send target: a TV name from [Config.TV_NAMES], or null for all TVs. */
    fun targetTv(context: Context): Flow<String?> =
        context.dataStore.data.map { prefs -> prefs[KEY_TARGET_TV]?.ifBlank { null } }

    suspend fun setTargetTv(context: Context, tvName: String?) {
        context.dataStore.edit { prefs -> prefs[KEY_TARGET_TV] = tvName.orEmpty() }
    }
}
