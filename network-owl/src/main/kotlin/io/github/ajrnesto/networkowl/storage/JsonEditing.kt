package io.github.ajrnesto.networkowl.storage

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

internal sealed interface JsonFormRow {
    val id: String
    val label: String
    val depth: Int

    data class Group(override val id: String, override val label: String, override val depth: Int, val isArray: Boolean, val size: Int) :
        JsonFormRow

    data class Field(override val id: String, override val label: String, override val depth: Int, val kind: Kind, val initial: String) :
        JsonFormRow

    enum class Kind { Text, Number, Bool, Null }
}

internal object JsonEditing {
    private const val ROOT = "$"
    private val pretty = Json { prettyPrint = true }

    fun rows(root: JsonElement): List<JsonFormRow> {
        val rows = mutableListOf<JsonFormRow>()

        fun walk(element: JsonElement, id: String, label: String, depth: Int) {
            when (element) {
                is JsonObject -> {
                    if (id != ROOT) rows += JsonFormRow.Group(id, label, depth, isArray = false, size = element.size)
                    element.forEach { (key, child) -> walk(child, "$id.$key", key, if (id == ROOT) depth else depth + 1) }
                }
                is JsonArray -> {
                    rows += JsonFormRow.Group(id, label, depth, isArray = true, size = element.size)
                    element.forEachIndexed { index, child -> walk(child, "$id[$index]", "[$index]", depth + 1) }
                }
                is JsonPrimitive -> rows += field(id, label, depth, element)
            }
        }

        walk(root, ROOT, label = "", depth = 0)
        return rows
    }

    fun parse(kind: JsonFormRow.Kind, text: String): JsonElement? = when (kind) {
        JsonFormRow.Kind.Text -> JsonPrimitive(text)
        JsonFormRow.Kind.Number -> text.trim().let { raw ->
            raw.toLongOrNull()?.let(::JsonPrimitive) ?: raw.toDoubleOrNull()?.takeIf { it.isFinite() }?.let(::JsonPrimitive)
        }
        JsonFormRow.Kind.Bool -> text.toBooleanStrictOrNull()?.let(::JsonPrimitive)
        // A null leaf has no type to keep, so the input is read as JSON and falls back to a string.
        JsonFormRow.Kind.Null -> when {
            text.isBlank() -> JsonNull
            else -> runCatching { Json.parseToJsonElement(text) }.getOrNull()?.takeIf(::isWellFormed) ?: JsonPrimitive(text)
        }
    }

    // parseToJsonElement accepts a bare word like Cebu as an unquoted literal, which would
    // serialise back out as invalid JSON.
    private fun isWellFormed(element: JsonElement): Boolean =
        element !is JsonPrimitive || element is JsonNull || element.isString ||
            element.booleanOrNull != null || element.content.toDoubleOrNull() != null

    fun apply(root: JsonElement, edits: Map<String, JsonElement>): JsonElement {
        fun rebuild(element: JsonElement, id: String): JsonElement = edits[id] ?: when (element) {
            is JsonObject -> JsonObject(element.mapValues { (key, child) -> rebuild(child, "$id.$key") })
            is JsonArray -> JsonArray(element.mapIndexed { index, child -> rebuild(child, "$id[$index]") })
            is JsonPrimitive -> element
        }
        return rebuild(root, ROOT)
    }

    fun compact(element: JsonElement): String = Json.encodeToString(JsonElement.serializer(), element)

    fun pretty(element: JsonElement): String = pretty.encodeToString(JsonElement.serializer(), element)

    private fun field(id: String, label: String, depth: Int, value: JsonPrimitive): JsonFormRow.Field {
        val kind = when {
            value is JsonNull -> JsonFormRow.Kind.Null
            value.isString -> JsonFormRow.Kind.Text
            value.booleanOrNull != null -> JsonFormRow.Kind.Bool
            else -> JsonFormRow.Kind.Number
        }
        val initial = if (kind == JsonFormRow.Kind.Null) "" else value.content
        return JsonFormRow.Field(id, label, depth, kind, initial)
    }
}
