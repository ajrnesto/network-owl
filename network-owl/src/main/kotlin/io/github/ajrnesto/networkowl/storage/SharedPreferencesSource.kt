package io.github.ajrnesto.networkowl.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

// getSharedPreferences returns the process-wide instance the app already holds, so edits
// here reach the app's own listeners.
internal class SharedPreferencesSource(
    context: Context,
    override val name: String,
    private val redactedKeys: () -> Set<String>,
) : StorageSource {
    private val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    override val id = "prefs:$name"

    override val entries: Flow<StorageSnapshot> = callbackFlow {
        // SharedPreferences holds listeners weakly; this local keeps it alive while collected.
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(snapshot()) }
        send(snapshot())
        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.flowOn(Dispatchers.IO)

    override suspend fun put(key: String, type: ValueType, value: Any) = withContext(Dispatchers.IO) {
        preferences.edit(commit = true) {
            when (type) {
                ValueType.Str -> putString(key, value as String)
                ValueType.Int32 -> putInt(key, value as Int)
                ValueType.Int64 -> putLong(key, value as Long)
                ValueType.Float32 -> putFloat(key, value as Float)
                ValueType.Float64, ValueType.StringSet, ValueType.Bytes ->
                    error("${type.label} values are not editable in SharedPreferences")
                ValueType.Bool -> putBoolean(key, value as Boolean)
            }
        }
    }

    override suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        preferences.edit(commit = true) { remove(key) }
    }

    private fun snapshot(): StorageSnapshot {
        val redacted = redactedKeys()
        return StorageSnapshot.Loaded(
            preferences.all
                .mapNotNull { (key, value) -> StorageEntry.of(key, value, redacted) }
                .sortedBy { it.key },
        )
    }

    companion object {
        fun discover(context: Context): List<String> =
            File(context.applicationInfo.dataDir, "shared_prefs")
                .listFiles { file -> file.extension == "xml" }
                ?.map { it.nameWithoutExtension }
                ?.sorted()
                .orEmpty()
    }
}
