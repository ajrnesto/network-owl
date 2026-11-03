package io.github.ajrnesto.networkowl.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ajrnesto.networkowl.FormattedValue
import io.github.ajrnesto.networkowl.NetworkOwlColors
import io.github.ajrnesto.networkowl.R

private const val FOLDED_LINES = 3

internal data class KeyValueItem(val key: String, val value: FormattedValue, val meta: String? = null)

internal fun LazyListScope.keyValueRows(items: List<KeyValueItem>, onClick: ((Int) -> Unit)? = null) {
    itemsIndexed(items) { index, item ->
        val colors = Inspector.colors
        val value = remember(item.value, colors) { valueText(item.value, colors) }
        var expanded by rememberSaveable { mutableStateOf(false) }
        var overflows by remember(value) { mutableStateOf(false) }

        Column(
            Modifier
                .codeBlockSegment(index, items.size, colors.codeBackground)
                .then(if (onClick != null) Modifier.clickable { onClick(index) } else Modifier),
        ) {
            if (index > 0) HorizontalDivider(color = colors.divider)
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.key,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.jsonKey,
                    modifier = Modifier.weight(1f),
                )
                if (item.meta != null) {
                    Text(
                        text = item.meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onBackgroundMuted,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            val placeholder = when (item.value) {
                FormattedValue.Redacted -> R.string.network_owl_value_redacted
                FormattedValue.Empty -> R.string.network_owl_value_empty
                else -> null
            }
            if (placeholder != null) {
                Text(
                    text = stringResource(placeholder),
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = colors.onBackgroundMuted,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                )
                return@Column
            }
            Text(
                text = value,
                style = Inspector.codeStyle,
                color = colors.onBackground,
                maxLines = if (expanded) Int.MAX_VALUE else FOLDED_LINES,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
                modifier = Modifier.padding(top = 2.dp, bottom = if (expanded || overflows) 0.dp else 8.dp),
            )
            if (expanded || overflows) {
                DisableSelection {
                    Text(
                        text = stringResource(if (expanded) R.string.network_owl_show_less else R.string.network_owl_show_all),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.primary,
                        modifier = Modifier
                            .clickable { expanded = !expanded }
                            .padding(top = 4.dp, bottom = 8.dp),
                    )
                }
            }
        }
    }
}

private fun valueText(value: FormattedValue, colors: NetworkOwlColors): AnnotatedString = when (value) {
    FormattedValue.Redacted, FormattedValue.Empty -> AnnotatedString("")
    is FormattedValue.Lines -> AnnotatedString(value.lines.joinToString("\n"))
    is FormattedValue.Json -> buildAnnotatedString {
        value.lines.forEachIndexed { index, tokens ->
            if (index > 0) append('\n')
            append(highlight(tokens, colors))
        }
    }
}
