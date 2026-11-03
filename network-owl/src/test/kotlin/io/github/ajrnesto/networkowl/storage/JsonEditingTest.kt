package io.github.ajrnesto.networkowl.storage

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import io.github.ajrnesto.networkowl.storage.JsonFormRow.Kind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test

class JsonEditingTest {
    private val draft = Json.parseToJsonElement(
        """{"origin":null,"passengerCount":1,"roundTrip":false,"passengers":[{"id":"c6cf","fullName":null}]}""",
    )

    @Test
    fun `rows flatten nested objects and arrays into labelled fields`() {
        val rows = JsonEditing.rows(draft).map { row ->
            when (row) {
                is JsonFormRow.Group -> "${"  ".repeat(row.depth)}${row.label} (${row.size})"
                is JsonFormRow.Field -> "${"  ".repeat(row.depth)}${row.label}: ${row.kind}=${row.initial}"
            }
        }

        assertThat(rows).containsExactly(
            "origin: Null=",
            "passengerCount: Number=1",
            "roundTrip: Bool=false",
            "passengers (1)",
            "  [0] (2)",
            "    id: Text=c6cf",
            "    fullName: Null=",
        )
    }

    @Test
    fun `apply replaces only edited leaves and keeps key order`() {
        val edited = JsonEditing.apply(
            draft,
            mapOf(
                "$.passengerCount" to JsonPrimitive(2),
                "$.passengers[0].fullName" to JsonPrimitive("Juan Dela Cruz"),
            ),
        )

        assertThat(JsonEditing.compact(edited)).isEqualTo(
            """{"origin":null,"passengerCount":2,"roundTrip":false,"passengers":[{"id":"c6cf","fullName":"Juan Dela Cruz"}]}""",
        )
    }

    @Test
    fun `typed inputs parse strictly and null leaves accept any json`() {
        assertThat(JsonEditing.parse(Kind.Number, "12a")).isNull()
        assertThat(JsonEditing.parse(Kind.Number, " 3 ")).isEqualTo(JsonPrimitive(3L))
        assertThat(JsonEditing.parse(Kind.Number, "2.5")).isEqualTo(JsonPrimitive(2.5))
        assertThat(JsonEditing.parse(Kind.Bool, "yes")).isNull()
        assertThat(JsonEditing.parse(Kind.Null, "")).isEqualTo(JsonNull)
        assertThat(JsonEditing.parse(Kind.Null, "5")).isEqualTo(JsonPrimitive(5))
        assertThat(JsonEditing.parse(Kind.Null, "Cebu")).isEqualTo(JsonPrimitive("Cebu"))
    }
}
