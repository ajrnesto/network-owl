package io.github.ajrnesto.networkowl

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

internal enum class JsonTokenKind { Key, Text, Number, Literal, Punctuation }

internal data class JsonToken(val text: String, val kind: JsonTokenKind)

internal data class JsonTableRow(val path: String, val value: JsonToken)

internal sealed interface JsonTreeRow {
    val id: String
    val depth: Int

    data class Leaf(override val id: String, override val depth: Int, val label: JsonLabel?, val value: JsonToken) :
        JsonTreeRow

    data class Branch(
        override val id: String,
        override val depth: Int,
        val label: JsonLabel?,
        val isObject: Boolean,
        val size: Int,
        val expanded: Boolean,
    ) : JsonTreeRow

    data class End(override val id: String, override val depth: Int, val isObject: Boolean) : JsonTreeRow
}

internal sealed interface JsonLabel {
    data class Key(val name: String) : JsonLabel
    data class Index(val index: Int) : JsonLabel
}

internal object JsonLayout {
    private const val ROOT = "$"

    fun parse(text: String): JsonElement? =
        runCatching { Json.parseToJsonElement(text) }.getOrNull()?.takeIf { it is JsonObject || it is JsonArray }

    fun codeLines(root: JsonElement): List<List<JsonToken>> {
        val lines = mutableListOf<List<JsonToken>>()

        fun emit(element: JsonElement, depth: Int, prefix: List<JsonToken>, comma: Boolean) {
            val indent = JsonToken("  ".repeat(depth), JsonTokenKind.Punctuation)
            val suffix = if (comma) listOf(punctuation(",")) else emptyList()
            val children = element.children()
            when {
                children == null -> lines += listOf(indent) + prefix + primitive(element as JsonPrimitive) + suffix
                children.isEmpty() -> lines += listOf(indent) + prefix + punctuation(element.brackets()) + suffix
                else -> {
                    lines += listOf(indent) + prefix + punctuation(element.brackets().take(1))
                    children.forEachIndexed { index, (label, child) ->
                        val childPrefix = (label as? JsonLabel.Key)?.let { listOf(key(it.name), punctuation(": ")) }.orEmpty()
                        emit(child, depth + 1, childPrefix, comma = index < children.lastIndex)
                    }
                    lines += listOf(indent, punctuation(element.brackets().takeLast(1))) + suffix
                }
            }
        }

        emit(root, depth = 0, prefix = emptyList(), comma = false)
        return lines
    }

    fun tableRows(root: JsonElement): List<JsonTableRow> {
        val rows = mutableListOf<JsonTableRow>()

        fun walk(element: JsonElement, path: String) {
            val children = element.children()
            when {
                children == null -> rows += JsonTableRow(path, primitive(element as JsonPrimitive))
                children.isEmpty() -> rows += JsonTableRow(path, punctuation(element.brackets()))
                else -> children.forEach { (label, child) ->
                    val childPath = when (label) {
                        is JsonLabel.Key -> if (path.isEmpty()) label.name else "$path.${label.name}"
                        is JsonLabel.Index -> "$path[${label.index}]"
                    }
                    walk(child, childPath)
                }
            }
        }

        walk(root, path = "")
        return rows
    }

    fun treeRows(root: JsonElement, expanded: Set<String>): List<JsonTreeRow> {
        val rows = mutableListOf<JsonTreeRow>()

        fun walk(element: JsonElement, id: String, depth: Int, label: JsonLabel?) {
            val children = element.children()
            if (children == null) {
                rows += JsonTreeRow.Leaf(id, depth, label, primitive(element as JsonPrimitive))
                return
            }
            val isOpen = id in expanded && children.isNotEmpty()
            rows += JsonTreeRow.Branch(id, depth, label, element is JsonObject, children.size, isOpen)
            if (!isOpen) return
            children.forEach { (childLabel, child) -> walk(child, childId(id, childLabel), depth + 1, childLabel) }
            rows += JsonTreeRow.End("$id/end", depth, element is JsonObject)
        }

        walk(root, ROOT, depth = 0, label = null)
        return rows
    }

    fun defaultExpanded(root: JsonElement, depth: Int = 2): Set<String> {
        val ids = mutableSetOf<String>()

        fun walk(element: JsonElement, id: String, level: Int) {
            val children = element.children() ?: return
            if (level >= depth) return
            ids += id
            children.forEach { (label, child) -> walk(child, childId(id, label), level + 1) }
        }

        walk(root, ROOT, level = 0)
        return ids
    }

    private fun childId(parent: String, label: JsonLabel) = when (label) {
        is JsonLabel.Key -> "$parent.${label.name}"
        is JsonLabel.Index -> "$parent[${label.index}]"
    }

    private fun JsonElement.children(): List<Pair<JsonLabel, JsonElement>>? = when (this) {
        is JsonObject -> entries.map { (name, value) -> JsonLabel.Key(name) to value }
        is JsonArray -> mapIndexed { index, value -> JsonLabel.Index(index) to value }
        is JsonPrimitive -> null
    }

    private fun JsonElement.brackets() = if (this is JsonObject) "{}" else "[]"

    private fun key(name: String) = JsonToken(JsonPrimitive(name).toString(), JsonTokenKind.Key)

    private fun punctuation(text: String) = JsonToken(text, JsonTokenKind.Punctuation)

    private fun primitive(value: JsonPrimitive) = JsonToken(
        text = value.toString(),
        kind = when {
            value is JsonNull || value.booleanOrNull != null && !value.isString -> JsonTokenKind.Literal
            value.isString -> JsonTokenKind.Text
            else -> JsonTokenKind.Number
        },
    )
}
