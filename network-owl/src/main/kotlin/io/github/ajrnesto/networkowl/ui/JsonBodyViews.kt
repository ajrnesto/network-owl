package io.github.ajrnesto.networkowl.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.ajrnesto.networkowl.JsonLabel
import io.github.ajrnesto.networkowl.JsonLayout
import io.github.ajrnesto.networkowl.JsonTableRow
import io.github.ajrnesto.networkowl.JsonToken
import io.github.ajrnesto.networkowl.JsonTokenKind
import io.github.ajrnesto.networkowl.JsonTreeRow
import io.github.ajrnesto.networkowl.NetworkOwlColors
import io.github.ajrnesto.networkowl.R
import kotlinx.serialization.json.JsonElement

internal enum class JsonViewMode(@StringRes val label: Int) {
    Code(R.string.network_owl_view_code),
    Tree(R.string.network_owl_view_tree),
    Table(R.string.network_owl_view_table),
}

internal data class JsonViewOptions(val mode: JsonViewMode, val wrap: Boolean)

internal class JsonBody(
    val element: JsonElement,
    val codeLines: List<List<JsonToken>>,
    val tableRows: List<JsonTableRow>,
) {
    companion object {
        fun parse(text: String): JsonBody? = JsonLayout.parse(text)?.let { element ->
            JsonBody(element, JsonLayout.codeLines(element), JsonLayout.tableRows(element))
        }
    }
}

internal fun LazyListScope.jsonModeSelector(
    mode: JsonViewMode,
    onModeChange: (JsonViewMode) -> Unit,
    wrap: Boolean,
    onWrapChange: (Boolean) -> Unit,
) = item {
    val colors = Inspector.colors
    DisableSelection {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModeButtons(mode, onModeChange, colors, Modifier.weight(1f))
            if (mode == JsonViewMode.Code) {
                FilterChip(
                    selected = wrap,
                    onClick = { onWrapChange(!wrap) },
                    label = { Text(stringResource(R.string.network_owl_wrap)) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = colors.background,
                        labelColor = colors.onBackgroundMuted,
                        selectedContainerColor = colors.primary.copy(alpha = 0.12f),
                        selectedLabelColor = colors.primary,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = wrap,
                        borderColor = colors.divider,
                        selectedBorderColor = colors.divider,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ModeButtons(
    mode: JsonViewMode,
    onModeChange: (JsonViewMode) -> Unit,
    colors: NetworkOwlColors,
    modifier: Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier) {
        JsonViewMode.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == mode,
                onClick = { onModeChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index, JsonViewMode.entries.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = colors.primary.copy(alpha = 0.12f),
                    activeContentColor = colors.primary,
                    activeBorderColor = colors.divider,
                    inactiveContainerColor = colors.background,
                    inactiveContentColor = colors.onBackgroundMuted,
                    inactiveBorderColor = colors.divider,
                ),
                label = { Text(stringResource(option.label)) },
            )
        }
    }
}

internal fun LazyListScope.jsonCode(lines: List<List<JsonToken>>, wrap: Boolean) {
    val gutterWidth = lines.size.toString().length
    if (!wrap) {
        unwrappedCode(lines, gutterWidth)
        return
    }
    itemsIndexed(lines) { index, tokens ->
        val colors = Inspector.colors
        Row(Modifier.codeBlockSegment(index, lines.size, colors.codeBackground)) {
            DisableSelection {
                Text(
                    text = (index + 1).toString().padStart(gutterWidth),
                    style = Inspector.codeStyle,
                    color = colors.onBackgroundMuted.copy(alpha = 0.6f),
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
            Text(text = highlight(tokens, colors), style = Inspector.codeStyle)
        }
    }
}

// Unwrapped lines must scroll sideways together, which separate lazy items cannot do, so the
// block is laid out as one text with a fixed gutter beside it.
private fun LazyListScope.unwrappedCode(lines: List<List<JsonToken>>, gutterWidth: Int) = item {
    val colors = Inspector.colors
    val numbers = remember(lines) { lines.indices.joinToString("\n") { (it + 1).toString().padStart(gutterWidth) } }
    val code = remember(lines, colors) {
        buildAnnotatedString {
            lines.forEachIndexed { index, tokens ->
                if (index > 0) append('\n')
                append(highlight(tokens, colors))
            }
        }
    }
    Row(Modifier.codeBlockSegment(0, 1, colors.codeBackground)) {
        DisableSelection {
            Text(
                text = numbers,
                style = Inspector.codeStyle,
                color = colors.onBackgroundMuted.copy(alpha = 0.6f),
                modifier = Modifier.padding(end = 12.dp),
            )
        }
        Text(
            text = code,
            style = Inspector.codeStyle,
            softWrap = false,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
    }
}

internal fun LazyListScope.jsonTree(rows: List<JsonTreeRow>, onToggle: (String) -> Unit) {
    itemsIndexed(rows) { index, row ->
        val colors = Inspector.colors
        val toggleable = row is JsonTreeRow.Branch && row.size > 0
        Row(
            modifier = Modifier
                .codeBlockSegment(index, rows.size, colors.codeBackground)
                .then(if (toggleable) Modifier.clickable { onToggle(row.id) } else Modifier)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Spacer(Modifier.width((row.depth * 16).dp))
            DisableSelection {
                Text(
                    text = when {
                        !toggleable -> ""
                        row.expanded -> "▾"
                        else -> "▸"
                    },
                    style = Inspector.codeStyle,
                    color = colors.onBackgroundMuted,
                    modifier = Modifier.width(16.dp),
                )
            }
            Text(text = treeText(row, colors), style = Inspector.codeStyle)
        }
    }
}

internal fun LazyListScope.jsonTable(rows: List<JsonTableRow>) {
    val count = rows.size + 1
    item {
        val colors = Inspector.colors
        Row(Modifier.codeBlockSegment(0, count, colors.codeBackground).padding(bottom = 6.dp)) {
            TableHeader(stringResource(R.string.network_owl_table_key), Modifier.weight(0.45f))
            TableHeader(stringResource(R.string.network_owl_table_value), Modifier.weight(0.55f))
        }
    }
    itemsIndexed(rows) { index, row ->
        val colors = Inspector.colors
        Column(Modifier.codeBlockSegment(index + 1, count, colors.codeBackground)) {
            HorizontalDivider(color = colors.divider)
            Row(Modifier.padding(vertical = 6.dp)) {
                Text(
                    text = row.path,
                    style = Inspector.codeStyle,
                    color = colors.jsonKey,
                    modifier = Modifier.weight(0.45f).padding(end = 12.dp),
                )
                Text(
                    text = highlight(listOf(row.value), colors),
                    style = Inspector.codeStyle,
                    modifier = Modifier.weight(0.55f),
                )
            }
        }
    }
}

@Composable
private fun TableHeader(text: String, modifier: Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = Inspector.colors.onBackgroundMuted,
        modifier = modifier,
    )
}

@Composable
private fun treeText(row: JsonTreeRow, colors: NetworkOwlColors): AnnotatedString {
    val summary = (row as? JsonTreeRow.Branch)?.takeIf { !it.expanded }?.let { branch ->
        when {
            branch.size == 0 -> null
            branch.isObject -> pluralStringResource(R.plurals.network_owl_json_keys, branch.size, branch.size)
            else -> pluralStringResource(R.plurals.network_owl_json_items, branch.size, branch.size)
        }
    }
    return buildAnnotatedString {
        fun styled(text: String, color: Color) = withStyle(SpanStyle(color = color)) { append(text) }
        fun label(label: JsonLabel?) = when (label) {
            is JsonLabel.Key -> {
                styled("\"${label.name}\"", colors.jsonKey)
                styled(": ", colors.onBackgroundMuted)
            }
            is JsonLabel.Index -> styled("${label.index}: ", colors.onBackgroundMuted)
            null -> Unit
        }
        when (row) {
            is JsonTreeRow.Leaf -> {
                label(row.label)
                styled(row.value.text, colors.colorOf(row.value.kind))
            }
            is JsonTreeRow.Branch -> {
                label(row.label)
                val (open, close) = if (row.isObject) "{" to "}" else "[" to "]"
                when {
                    row.expanded -> styled(open, colors.onBackgroundMuted)
                    summary == null -> styled(open + close, colors.onBackgroundMuted)
                    else -> {
                        styled("$open ", colors.onBackgroundMuted)
                        styled(summary, colors.jsonLiteral)
                        styled(" $close", colors.onBackgroundMuted)
                    }
                }
            }
            is JsonTreeRow.End -> styled(if (row.isObject) "}" else "]", colors.onBackgroundMuted)
        }
    }
}

internal fun highlight(tokens: List<JsonToken>, colors: NetworkOwlColors) = buildAnnotatedString {
    tokens.forEach { token -> withStyle(SpanStyle(color = colors.colorOf(token.kind))) { append(token.text) } }
}

private fun NetworkOwlColors.colorOf(kind: JsonTokenKind) = when (kind) {
    JsonTokenKind.Key -> jsonKey
    JsonTokenKind.Text -> jsonString
    JsonTokenKind.Number -> jsonNumber
    JsonTokenKind.Literal -> jsonLiteral
    JsonTokenKind.Punctuation -> onBackgroundMuted
}

// Each lazy item paints its own slice of one rounded card, so only the first and last round off.
internal fun Modifier.codeBlockSegment(index: Int, count: Int, color: Color): Modifier {
    val first = index == 0
    val last = index == count - 1
    val shape = RoundedCornerShape(
        topStart = if (first) 10.dp else 0.dp,
        topEnd = if (first) 10.dp else 0.dp,
        bottomStart = if (last) 10.dp else 0.dp,
        bottomEnd = if (last) 10.dp else 0.dp,
    )
    return fillMaxWidth()
        .padding(horizontal = 16.dp)
        .background(color, shape)
        .padding(horizontal = 12.dp)
        .padding(top = if (first) 10.dp else 0.dp, bottom = if (last) 10.dp else 0.dp)
}
