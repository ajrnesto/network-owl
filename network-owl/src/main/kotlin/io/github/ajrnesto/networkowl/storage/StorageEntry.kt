package io.github.ajrnesto.networkowl.storage

import io.github.ajrnesto.networkowl.REDACTED_VALUE
import kotlinx.coroutines.flow.Flow

internal enum class ValueType(val label: String) {
    Str("String"),
    Int32("Int"),
    Int64("Long"),
    Float32("Float"),
    Float64("Double"),
    Bool("Boolean"),
    StringSet("Set<String>"),
    Bytes("ByteArray");

    val editable: Boolean get() = this != StringSet && this != Bytes

    fun parse(raw: String): Any? = when (this) {
        Str -> raw
        Int32 -> raw.trim().toIntOrNull()
        Int64 -> raw.trim().toLongOrNull()
        Float32 -> raw.trim().toFloatOrNull()
        Float64 -> raw.trim().toDoubleOrNull()
        Bool -> raw.trim().lowercase().toBooleanStrictOrNull()
        StringSet, Bytes -> null
    }

    companion object {
        fun of(value: Any?): ValueType? = when (value) {
            is String -> Str
            is Int -> Int32
            is Long -> Int64
            is Float -> Float32
            is Double -> Float64
            is Boolean -> Bool
            is Set<*> -> StringSet
            is ByteArray -> Bytes
            else -> null
        }
    }
}

internal data class StorageEntry(
    val key: String,
    val type: ValueType,
    val value: String,
    val redacted: Boolean,
) {
    val editable: Boolean get() = type.editable && !redacted

    companion object {
        fun of(key: String, value: Any?, redactedKeys: Set<String>): StorageEntry? {
            val type = ValueType.of(value) ?: return null
            val redacted = key in redactedKeys
            val display = when {
                redacted -> REDACTED_VALUE
                value is Set<*> -> value.map(Any?::toString).sorted().joinToString("\n")
                value is ByteArray -> "${value.size} bytes"
                else -> value.toString()
            }
            return StorageEntry(key, type, display, redacted)
        }
    }
}

internal sealed interface StorageSnapshot {
    data class Loaded(val entries: List<StorageEntry>) : StorageSnapshot
    data class Failed(val message: String) : StorageSnapshot
}

internal interface StorageSource {
    val id: String
    val name: String
    val entries: Flow<StorageSnapshot>

    suspend fun put(key: String, type: ValueType, value: Any)

    suspend fun remove(key: String)
}
