package io.github.ajrnesto.networkowl.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

// Reads and writes go through the host's own instance: a second DataStore on the same file
// throws, and a separate copy would race the app's writes.
internal class DataStoreSource(
    override val name: String,
    private val dataStore: DataStore<Preferences>,
    private val redactedKeys: () -> Set<String>,
) : StorageSource {
    override val id = "datastore:$name"

    override val entries: Flow<StorageSnapshot> = dataStore.data
        .map<Preferences, StorageSnapshot> { preferences ->
            val redacted = redactedKeys()
            StorageSnapshot.Loaded(
                preferences.asMap()
                    .mapNotNull { (key, value) -> StorageEntry.of(key.name, value, redacted) }
                    .sortedBy { it.key },
            )
        }
        .catch { emit(StorageSnapshot.Failed(it.message ?: it.toString())) }

    override suspend fun put(key: String, type: ValueType, value: Any) {
        dataStore.edit { preferences ->
            when (type) {
                ValueType.Str -> preferences[stringPreferencesKey(key)] = value as String
                ValueType.Int32 -> preferences[intPreferencesKey(key)] = value as Int
                ValueType.Int64 -> preferences[longPreferencesKey(key)] = value as Long
                ValueType.Float32 -> preferences[floatPreferencesKey(key)] = value as Float
                ValueType.Float64 -> preferences[doublePreferencesKey(key)] = value as Double
                ValueType.Bool -> preferences[booleanPreferencesKey(key)] = value as Boolean
                ValueType.StringSet, ValueType.Bytes -> error("${type.label} values are not editable")
            }
        }
    }

    override suspend fun remove(key: String) {
        dataStore.edit { preferences ->
            preferences.asMap().keys.firstOrNull { it.name == key }?.let { preferences -= it }
        }
    }
}
