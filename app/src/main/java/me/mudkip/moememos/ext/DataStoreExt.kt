package me.mudkip.moememos.ext

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.mudkip.moememos.data.model.Settings
import me.mudkip.moememos.data.model.UserSettings
import me.mudkip.moememos.util.SettingsSerializer

val Context.settingsDataStore: DataStore<Settings> by dataStore(
    fileName = "settings_v3.json",
    serializer = SettingsSerializer
)

/** Settings of the current account (defaults when there is none). */
val Context.currentUserSettings: Flow<UserSettings>
    get() = settingsDataStore.data.map { settings ->
        settings.usersList.firstOrNull { it.accountKey == settings.currentUser }?.settings ?: UserSettings()
    }

/** Updates the current account's settings; does nothing when there is no current account. */
suspend fun Context.updateCurrentUserSettings(transform: (UserSettings) -> UserSettings) {
    settingsDataStore.updateData { settings ->
        val index = settings.usersList.indexOfFirst { it.accountKey == settings.currentUser }
        if (index == -1) {
            return@updateData settings
        }
        val users = settings.usersList.toMutableList()
        users[index] = users[index].copy(settings = transform(users[index].settings))
        settings.copy(usersList = users)
    }
}
