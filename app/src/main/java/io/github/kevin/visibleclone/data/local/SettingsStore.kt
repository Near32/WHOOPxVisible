package io.github.kevin.visibleclone.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.kevin.visibleclone.domain.PacingConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Persisted settings: the pacing configuration plus the WHOOP token bundle. */
class SettingsStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val configKey = stringPreferencesKey("pacing_config")
    private val tokensKey = stringPreferencesKey("whoop_tokens")
    private val profileKey = stringPreferencesKey("whoop_profile_name")
    private val bodyKey = stringPreferencesKey("whoop_body")

    val config: Flow<PacingConfig> = context.dataStore.data.map { prefs ->
        prefs[configKey]?.let { runCatching { json.decodeFromString<PacingConfig>(it) }.getOrNull() }
            ?: PacingConfig()
    }

    suspend fun currentConfig(): PacingConfig = config.first()

    suspend fun saveConfig(config: PacingConfig) {
        context.dataStore.edit { it[configKey] = json.encodeToString(config) }
    }

    val tokens: Flow<WhoopTokens?> = context.dataStore.data.map { prefs ->
        prefs[tokensKey]?.let { runCatching { json.decodeFromString<WhoopTokens>(it) }.getOrNull() }
    }

    suspend fun currentTokens(): WhoopTokens? = tokens.first()

    suspend fun saveTokens(tokens: WhoopTokens) {
        context.dataStore.edit { it[tokensKey] = json.encodeToString(tokens) }
    }

    suspend fun clearTokens() {
        context.dataStore.edit { it.remove(tokensKey); it.remove(profileKey); it.remove(bodyKey) }
    }

    val profileName: Flow<String?> = context.dataStore.data.map { it[profileKey] }

    suspend fun saveProfile(name: String) {
        context.dataStore.edit { it[profileKey] = name }
    }

    val body: Flow<BodyMeasurement?> = context.dataStore.data.map { prefs ->
        prefs[bodyKey]?.let { runCatching { json.decodeFromString<BodyMeasurement>(it) }.getOrNull() }
    }

    suspend fun currentBody(): BodyMeasurement? = body.first()

    suspend fun saveBody(body: BodyMeasurement) {
        context.dataStore.edit { it[bodyKey] = json.encodeToString(body) }
    }
}
