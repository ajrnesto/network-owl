package io.github.ajrnesto.networkowl

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import io.github.ajrnesto.networkowl.JsonTokenKind.Key
import io.github.ajrnesto.networkowl.JsonTokenKind.Literal
import io.github.ajrnesto.networkowl.JsonTokenKind.Number
import io.github.ajrnesto.networkowl.JsonTokenKind.Punctuation
import io.github.ajrnesto.networkowl.JsonTokenKind.Text
import org.junit.jupiter.api.Test

class JsonLayoutTest {
    private val sample = JsonLayout.parse("""{"id":7,"name":"O\"Neil","paid":true,"seat":null,"legs":[{"port":"Cebu"}],"tags":[]}""")!!

    @Test
    fun `parse accepts objects and arrays only`() {
        assertThat(JsonLayout.parse("""[1,2]""")).isNotNull()
        assertThat(JsonLayout.parse("\"just a string\"")).isNull()
        assertThat(JsonLayout.parse("{broken")).isNull()
    }

    @Test
    fun `code lines indent nested values and classify every token`() {
        val lines = JsonLayout.codeLines(sample).map { line -> line.joinToString("") { it.text } }

        assertThat(lines).containsExactly(
            "{",
            "  \"id\": 7,",
            "  \"name\": \"O\\\"Neil\",",
            "  \"paid\": true,",
            "  \"seat\": null,",
            "  \"legs\": [",
            "    {",
            "      \"port\": \"Cebu\"",
            "    }",
            "  ],",
            "  \"tags\": []",
            "}",
        )
        val idLine = JsonLayout.codeLines(sample)[1].filter { it.text.isNotBlank() }
        assertThat(idLine.map { it.kind }).containsExactly(Key, Punctuation, Number, Punctuation)
        assertThat(JsonLayout.codeLines(sample)[3].last { it.kind != Punctuation }.kind).isEqualTo(Literal)
        assertThat(JsonLayout.codeLines(sample)[2].last { it.kind != Punctuation }.kind).isEqualTo(Text)
    }

    @Test
    fun `table rows flatten to dotted and indexed paths`() {
        val rows = JsonLayout.tableRows(sample).map { it.path to it.value.text }

        assertThat(rows).containsExactly(
            "id" to "7",
            "name" to "\"O\\\"Neil\"",
            "paid" to "true",
            "seat" to "null",
            "legs[0].port" to "\"Cebu\"",
            "tags" to "[]",
        )
    }

    @Test
    fun `tree shows children only for expanded branches`() {
        val collapsed = JsonLayout.treeRows(sample, expanded = setOf("$"))
        val legs = collapsed.filterIsInstance<JsonTreeRow.Branch>().single { it.label == JsonLabel.Key("legs") }
        assertThat(legs.expanded).isEqualTo(false)
        assertThat(collapsed.size).isEqualTo(8)

        val opened = JsonLayout.treeRows(sample, expanded = setOf("$", "$.legs", "$.legs[0]"))
        val port = opened.filterIsInstance<JsonTreeRow.Leaf>().single { it.label == JsonLabel.Key("port") }
        assertThat(port.depth).isEqualTo(3)
        assertThat(opened.last()).isEqualTo(JsonTreeRow.End("$/end", depth = 0, isObject = true))
    }

    @Test
    fun `default expansion opens the first two levels`() {
        assertThat(JsonLayout.defaultExpanded(sample)).isEqualTo(setOf("$", "$.legs", "$.tags"))
    }
}
