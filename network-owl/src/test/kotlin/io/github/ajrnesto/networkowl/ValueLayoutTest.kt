package io.github.ajrnesto.networkowl

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import org.junit.jupiter.api.Test

class ValueLayoutTest {

    @Test
    fun `short header pairs and dates stay on one line`() {
        listOf("1; mode=block", "no-cache, private", "Tue, 03 Nov 2026 15:52:48 GMT", "application/json; charset=utf-8")
            .forEach { value ->
                assertThat(ValueLayout.header(value)).isEqualTo(FormattedValue.Lines(listOf(value)))
            }
    }

    @Test
    fun `semicolon directive lists split one per line`() {
        val csp = "base-uri 'self';connect-src 'self';default-src 'self';"

        assertThat(ValueLayout.header(csp)).isEqualTo(
            FormattedValue.Lines(listOf("base-uri 'self'", "connect-src 'self'", "default-src 'self'")),
        )
    }

    @Test
    fun `comma directive lists split one per line`() {
        assertThat(ValueLayout.header("autoplay=(self), camera=(), usb=()")).isEqualTo(
            FormattedValue.Lines(listOf("autoplay=(self)", "camera=()", "usb=()")),
        )
    }

    @Test
    fun `json header values are laid out as code`() {
        val value = """{"report_to":"cf-nel","max_age":604800}"""

        assertThat(ValueLayout.header(value))
            .isInstanceOf<FormattedValue.Json>()
            .prop(FormattedValue.Json::lines)
            .transform { lines -> lines.map { line -> line.joinToString("") { it.text } } }
            .isEqualTo(listOf("{", "  \"report_to\": \"cf-nel\",", "  \"max_age\": 604800", "}"))
    }

    @Test
    fun `stored values keep commas intact and only split on newlines`() {
        assertThat(ValueLayout.stored("Cebu, Bohol, Leyte\nSamar"))
            .isEqualTo(FormattedValue.Lines(listOf("Cebu, Bohol, Leyte", "Samar")))
    }
}
