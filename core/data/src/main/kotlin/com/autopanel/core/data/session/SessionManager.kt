package com.autopanel.core.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "ql_session")

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_HOST = stringPreferencesKey("host")
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_PASSWORD = stringPreferencesKey("password")
        private val KEY_TOKEN = stringPreferencesKey("token")
        private val KEY_ALIAS = stringPreferencesKey("alias")
        private val KEY_REMEMBER = booleanPreferencesKey("remember_password")
        private val KEY_ACCOUNTS_JSON = stringPreferencesKey("accounts_json")
        private val KEY_CERT_PATH = stringPreferencesKey("cert_path")
        private val KEY_CERT_PASSWORD = stringPreferencesKey("cert_password")
        private val KEY_DARK_MODE = stringPreferencesKey("dark_mode")
    }

    private val json = Json { ignoreUnknownKeys = true }

    val hostFlow: Flow<String?> = context.sessionDataStore.data.map { it[KEY_HOST] }
    val usernameFlow: Flow<String?> = context.sessionDataStore.data.map { it[KEY_USERNAME] }
    val passwordFlow: Flow<String?> = context.sessionDataStore.data.map { it[KEY_PASSWORD] }
    val tokenFlow: Flow<String?> = context.sessionDataStore.data.map { it[KEY_TOKEN] }
    val aliasFlow: Flow<String?> = context.sessionDataStore.data.map { it[KEY_ALIAS] }
    val isLoggedInFlow: Flow<Boolean> = tokenFlow.map { it != null }

    val accountsFlow: Flow<List<StoredAccount>> = context.sessionDataStore.data.map { prefs ->
        val raw = prefs[KEY_ACCOUNTS_JSON] ?: return@map emptyList()
        try { json.decodeFromString<List<StoredAccount>>(raw) }
        catch (_: Exception) { emptyList() }
    }

    /** 深色模式偏好："system" / "light" / "dark" */
    val darkModeFlow: Flow<String> = context.sessionDataStore.data.map { it[KEY_DARK_MODE] ?: "system" }

    val host: String? get() = runBlocking { context.sessionDataStore.data.first()[KEY_HOST] }
    val username: String? get() = runBlocking { context.sessionDataStore.data.first()[KEY_USERNAME] }
    val password: String? get() = runBlocking { context.sessionDataStore.data.first()[KEY_PASSWORD] }
    val token: String? get() = runBlocking { context.sessionDataStore.data.first()[KEY_TOKEN] }
    val alias: String? get() = runBlocking { context.sessionDataStore.data.first()[KEY_ALIAS] }
    val rememberPassword: Boolean get() = runBlocking { context.sessionDataStore.data.first()[KEY_REMEMBER] ?: false }
    val certPath: String? get() = runBlocking { context.sessionDataStore.data.first()[KEY_CERT_PATH] }
    val certPassword: String? get() = runBlocking { context.sessionDataStore.data.first()[KEY_CERT_PASSWORD] }
    val isLoggedIn: Boolean get() = token != null

    suspend fun saveSession(
        host: String,
        username: String,
        password: String,
        token: String,
        alias: String? = null,
        remember: Boolean = false
    ) {
        context.sessionDataStore.edit { prefs ->
            prefs[KEY_HOST] = host
            prefs[KEY_USERNAME] = username
            prefs[KEY_TOKEN] = token
            if (alias != null) prefs[KEY_ALIAS] = alias
            if (remember) prefs[KEY_PASSWORD] = password else prefs.remove(KEY_PASSWORD)
            prefs[KEY_REMEMBER] = remember
            updateHistoryInPrefs(prefs, host, username, alias)
        }
    }

    suspend fun setHost(host: String) {
        context.sessionDataStore.edit { prefs -> prefs[KEY_HOST] = host }
    }

    suspend fun setDarkMode(mode: String) {
        context.sessionDataStore.edit { prefs -> prefs[KEY_DARK_MODE] = mode }
    }

    suspend fun saveCertificate(path: String?, password: String?) {
        context.sessionDataStore.edit { prefs ->
            if (path != null) prefs[KEY_CERT_PATH] = path else prefs.remove(KEY_CERT_PATH)
            if (password != null) prefs[KEY_CERT_PASSWORD] = password else prefs.remove(KEY_CERT_PASSWORD)
        }
    }

    suspend fun clearSession() {
        context.sessionDataStore.edit { prefs ->
            prefs.remove(KEY_TOKEN)
            prefs.remove(KEY_PASSWORD)
        }
    }

    suspend fun clearAll() {
        context.sessionDataStore.edit { it.clear() }
    }

    suspend fun removeFromHistory(host: String) {
        context.sessionDataStore.edit { prefs ->
            val raw = prefs[KEY_ACCOUNTS_JSON] ?: return@edit
            val list = try { json.decodeFromString<MutableList<StoredAccount>>(raw) }
                catch (_: Exception) { return@edit }
            list.removeAll { it.host == host }
            prefs[KEY_ACCOUNTS_JSON] = json.encodeToString(list)
        }
    }

    private fun updateHistoryInPrefs(prefs: MutablePreferences, host: String, username: String, alias: String?) {
        val raw = prefs[KEY_ACCOUNTS_JSON] ?: "[]"
        val list = try { json.decodeFromString<MutableList<StoredAccount>>(raw) }
            catch (_: Exception) { mutableListOf() }
        list.removeAll { it.host == host }
        list.add(0, StoredAccount(host = host, username = username, alias = alias))
        prefs[KEY_ACCOUNTS_JSON] = json.encodeToString(list.take(20))
    }
}

@kotlinx.serialization.Serializable
data class StoredAccount(
    val host: String,
    val username: String,
    val alias: String? = null
)
