package io.github.ajrnesto.networkowl

internal const val REDACTED_VALUE = "██"

internal sealed interface FormattedValue {
    data class Json(val lines: List<List<JsonToken>>) : FormattedValue
    data class Lines(val lines: List<String>) : FormattedValue
    data object Redacted : FormattedValue
    data object Empty : FormattedValue
}

internal object ValueLayout {
    private const val MIN_LIST_ENTRIES = 3

    fun header(value: String): FormattedValue =
        placeholder(value) ?: json(value) ?: FormattedValue.Lines(directives(value) ?: listOf(value))

    fun stored(value: String): FormattedValue =
        placeholder(value) ?: json(value) ?: FormattedValue.Lines(value.lines())

    private fun placeholder(value: String): FormattedValue? = when {
        value == REDACTED_VALUE -> FormattedValue.Redacted
        value.isEmpty() -> FormattedValue.Empty
        else -> null
    }

    private fun json(value: String): FormattedValue? =
        JsonLayout.parse(value)?.let { FormattedValue.Json(JsonLayout.codeLines(it)) }

    // Short pairs like "1; mode=block", "no-cache, private" or an HTTP date read better inline,
    // so only real directive lists get one entry per line.
    private fun directives(value: String): List<String>? =
        listOf(';', ',')
            .map { separator -> value.split(separator).map(String::trim).filter(String::isNotEmpty) }
            .filter { it.size >= MIN_LIST_ENTRIES }
            .maxByOrNull { it.size }
}
