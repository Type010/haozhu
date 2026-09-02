package com.jmzs.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jmzs.app.data.api.ApiClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "jmzs_settings")

/**
 * 本地持久化：账号、token、项目收藏、历史记录、各项设置。
 * 全部通过 DataStore Preferences 存储，复杂结构序列化为 JSON 字符串。
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val SERVER = stringPreferencesKey("server")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
        val TOKEN = stringPreferencesKey("token")
        val POLL_INTERVAL = intPreferencesKey("poll_interval")
        val BACKGROUND = booleanPreferencesKey("background_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PROJECTS = stringPreferencesKey("projects")
        val LAST_SID = stringPreferencesKey("last_sid")
        val HISTORY = stringPreferencesKey("history")
    }

    private val json = ApiClient.json

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            server = prefs[Keys.SERVER] ?: AppSettings.DEFAULT_SERVER,
            username = prefs[Keys.USERNAME].orEmpty(),
            password = prefs[Keys.PASSWORD].orEmpty(),
            token = prefs[Keys.TOKEN].orEmpty(),
            pollIntervalSec = prefs[Keys.POLL_INTERVAL] ?: 15,
            backgroundEnabled = prefs[Keys.BACKGROUND] ?: true,
            themeMode = when (prefs[Keys.THEME_MODE]) {
                "light" -> ThemeMode.LIGHT
                "dark" -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            },
            projects = decodeList(prefs[Keys.PROJECTS], Project.serializer()),
            lastSid = prefs[Keys.LAST_SID].orEmpty(),
            history = decodeList(prefs[Keys.HISTORY], NumberRecord.serializer()),
        )
    }

    suspend fun saveLogin(server: String, username: String, password: String, token: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SERVER] = server.trim()
            prefs[Keys.USERNAME] = username
            prefs[Keys.PASSWORD] = password
            prefs[Keys.TOKEN] = token
        }
    }

    /** 退出登录：清除令牌与账号密码（服务器地址、项目收藏等保留） */
    suspend fun clearLogin() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.TOKEN)
            prefs.remove(Keys.USERNAME)
            prefs.remove(Keys.PASSWORD)
        }
    }

    suspend fun setServer(server: String) {
        context.dataStore.edit { it[Keys.SERVER] = server.trim() }
    }

    suspend fun setPollInterval(seconds: Int) {
        context.dataStore.edit { it[Keys.POLL_INTERVAL] = seconds }
    }

    suspend fun setBackgroundEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BACKGROUND] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit {
            it[Keys.THEME_MODE] = when (mode) {
                ThemeMode.LIGHT -> "light"
                ThemeMode.DARK -> "dark"
                ThemeMode.SYSTEM -> "system"
            }
        }
    }

    suspend fun addProject(project: Project) {
        context.dataStore.edit { prefs ->
            val current = decodeList(prefs[Keys.PROJECTS], Project.serializer())
            val merged = listOf(project) + current.filter { it.sid != project.sid }
            prefs[Keys.PROJECTS] = json.encodeToString(ListSerializer(Project.serializer()), merged)
        }
    }

    suspend fun removeProject(sid: String) {
        context.dataStore.edit { prefs ->
            val current = decodeList(prefs[Keys.PROJECTS], Project.serializer())
            val merged = current.filter { it.sid != sid }
            prefs[Keys.PROJECTS] = json.encodeToString(ListSerializer(Project.serializer()), merged)
            if (prefs[Keys.LAST_SID] == sid) prefs.remove(Keys.LAST_SID)
        }
    }

    suspend fun setLastSid(sid: String) {
        context.dataStore.edit { it[Keys.LAST_SID] = sid }
    }

    suspend fun addRecord(record: NumberRecord) {
        context.dataStore.edit { prefs ->
            val current = decodeList(prefs[Keys.HISTORY], NumberRecord.serializer())
            val merged = (listOf(record) + current).take(AppSettings.MAX_HISTORY)
            prefs[Keys.HISTORY] = json.encodeToString(ListSerializer(NumberRecord.serializer()), merged)
        }
    }

    suspend fun clearHistory() {
        context.dataStore.edit { it.remove(Keys.HISTORY) }
    }

    private fun <T> decodeList(raw: String?, serializer: kotlinx.serialization.KSerializer<T>): List<T> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(ListSerializer(serializer), raw)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
