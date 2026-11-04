package io.github.ajrnesto.networkowl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ajrnesto.networkowl.HttpTransaction
import io.github.ajrnesto.networkowl.R
import io.github.ajrnesto.networkowl.formatBytes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionListScreen(
    transactions: List<HttpTransaction>,
    query: String,
    onQueryChange: (String) -> Unit,
    onSelect: (Long) -> Unit,
    onClear: () -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val shown = remember(transactions, query) { transactions.filter { it.matches(query) } }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        bottomBar = bottomBar,
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.network_owl_list_title)) },
                actions = {
                    IconButton(onClick = onClear, enabled = transactions.isNotEmpty()) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.network_owl_clear))
                    }
                },
                colors = inspectorTopBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        if (transactions.isEmpty()) {
            EmptyMessage(stringResource(R.string.network_owl_list_empty), Modifier.padding(padding))
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {
                SearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                )
                if (shown.isEmpty()) {
                    EmptyMessage(stringResource(R.string.network_owl_list_no_matches, query.trim()))
                } else {
                    LazyColumn {
                        itemsIndexed(shown, key = { _, transaction -> transaction.id }) { index, transaction ->
                            SegmentedItem(index, shown.size, onClick = { onSelect(transaction.id) }) {
                                TransactionRow(transaction)
                            }
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.network_owl_search_hint)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.network_owl_search_clear))
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

@Composable
private fun EmptyMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}

@Composable
private fun TransactionRow(transaction: HttpTransaction) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusBadge(transaction)
        Column(Modifier.weight(1f)) {
            Text(
                text = "${transaction.method} ${transaction.path}",
                style = Inspector.codeStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = transaction.host,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = clockTime.format(Instant.ofEpochMilli(transaction.requestedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = rowMeta(transaction),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun StatusBadge(transaction: HttpTransaction) {
    val color = Inspector.statusColor(transaction)
    Box(
        modifier = Modifier
            .widthIn(min = 52.dp)
            .heightIn(min = 28.dp)
            .background(color.copy(alpha = 0.14f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        val badge = transaction.badge
        if (badge == null) {
            val pending = stringResource(R.string.network_owl_status_pending)
            CircularProgressIndicator(
                color = color,
                strokeWidth = 2.dp,
                modifier = Modifier
                    .size(14.dp)
                    .semantics { contentDescription = pending },
            )
        } else {
            Text(
                text = badge,
                style = Inspector.codeStyle,
                fontWeight = FontWeight.SemiBold,
                color = color,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun rowMeta(transaction: HttpTransaction): String {
    val response = transaction.response ?: return ""
    val size = response.body?.byteCount?.let(::formatBytes)
    return listOfNotNull("${response.tookMs} ms", size).joinToString(" · ")
}

private val clockTime = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
