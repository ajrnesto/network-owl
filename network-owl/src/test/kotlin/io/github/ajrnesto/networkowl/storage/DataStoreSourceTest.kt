package io.github.ajrnesto.networkowl.storage

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DataStoreSourceTest {
    @TempDir
    lateinit var dir: File

    private fun TestScope.hostDataStore() =
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File(dir, "host.preferences_pb") }

    private suspend fun DataStoreSource.loaded() = (entries.first() as StorageSnapshot.Loaded).entries

    @Test
    fun `lists every entry sorted by key with its type and redaction`() = runTest {
        val host = hostDataStore()
        host.edit {
            it[stringPreferencesKey("auth_token")] = "secret"
            it[intPreferencesKey("launches")] = 3
            it[stringSetPreferencesKey("paid")] = setOf("B2", "A1")
        }
        val source = DataStoreSource("prefs", host, redactedKeys = { setOf("auth_token") })

        assertThat(source.loaded()).containsExactly(
            StorageEntry("auth_token", ValueType.Str, "██", redacted = true),
            StorageEntry("launches", ValueType.Int32, "3", redacted = false),
            StorageEntry("paid", ValueType.StringSet, "A1\nB2", redacted = false),
        )
    }

    @Test
    fun `writes land in the host's own instance with the right type`() = runTest {
        val host = hostDataStore()
        val source = DataStoreSource("prefs", host, redactedKeys = { emptySet() })

        source.put("onboarded", ValueType.Bool, false)
        source.put("launches", ValueType.Int32, 7)

        val preferences: Preferences = host.data.first()
        assertThat(preferences[booleanPreferencesKey("onboarded")]).isEqualTo(false)
        assertThat(preferences[intPreferencesKey("launches")]).isEqualTo(7)
    }

    @Test
    fun `remove deletes the key whatever its type`() = runTest {
        val host = hostDataStore()
        host.edit { it[stringSetPreferencesKey("paid")] = setOf("A1") }
        val source = DataStoreSource("prefs", host, redactedKeys = { emptySet() })

        source.remove("paid")

        assertThat(host.data.first()[stringSetPreferencesKey("paid")]).isNull()
    }

    @Test
    fun `parse rejects values that do not fit the type`() {
        assertThat(ValueType.Int32.parse("12a")).isNull()
        assertThat(ValueType.Bool.parse("yes")).isNull()
        assertThat(ValueType.Float64.parse(" 1.5 ")).isEqualTo(1.5)
        assertThat(ValueType.StringSet.editable).isFalse()
    }
}
