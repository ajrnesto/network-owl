package io.github.ajrnesto.networkowl.ui

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ajrnesto.networkowl.Body
import io.github.ajrnesto.networkowl.Header
import io.github.ajrnesto.networkowl.HttpTransaction
import io.github.ajrnesto.networkowl.JsonLayout
import io.github.ajrnesto.networkowl.ValueLayout
import io.github.ajrnesto.networkowl.R
import io.github.ajrnesto.networkowl.TransactionExport
import io.github.ajrnesto.networkowl.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionDetailScreen(
    transaction: HttpTransaction,
    jsonOptions: JsonViewOptions,
    onJsonOptionsChange: (JsonViewOptions) -> Unit,
    onBack: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(R.string.network_owl_tab_overview, R.string.network_owl_tab_request, R.string.network_owl_tab_response)

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.network_owl_back))
                        }
                    },
                    title = {
                        Text(
                            text = "${transaction.method} ${transaction.path}",
                            style = Inspector.codeStyle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    actions = { ExportMenu(transaction) },
                    colors = inspectorTopBarColors(),
                )
                PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                    tabs.forEachIndexed { index, label ->
                        Tab(selected = tab == index, onClick = { tab = index }, text = { Text(stringResource(label)) })
                    }
                }
            }
        },
    ) { padding ->
        when (tab) {
            0 -> OverviewTab(transaction, padding)
            1 -> MessageTab(
                headers = transaction.requestHeaders,
                body = transaction.requestBody,
                bodyLoading = false,
                jsonOptions = jsonOptions,
                onJsonOptionsChange = onJsonOptionsChange,
                padding = padding,
            )
            else -> ResponseTab(transaction, jsonOptions, onJsonOptionsChange, padding)
        }
    }
}

@Composable
private fun ExportMenu(transaction: HttpTransaction) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.network_owl_more))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.network_owl_share_text)) },
            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
            onClick = {
                expanded = false
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, TransactionExport.text(transaction))
                context.startActivity(Intent.createChooser(send, null))
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.network_owl_copy_curl)) },
            onClick = {
                expanded = false
                val curl = ClipData.newPlainText("cURL", TransactionExport.curl(transaction))
                scope.launch { clipboard.setClipEntry(ClipEntry(curl)) }
            },
        )
    }
}

@Composable
private fun OverviewTab(transaction: HttpTransaction, padding: PaddingValues) {
    val response = transaction.response
    val status = when (transaction.status) {
        HttpTransaction.Status.Pending -> stringResource(R.string.network_owl_status_pending)
        HttpTransaction.Status.Failed -> stringResource(R.string.network_owl_status_failed)
        HttpTransaction.Status.Complete -> "${response?.code} ${response?.message}".trim()
    }
    val fields = listOfNotNull(
        R.string.network_owl_field_url to transaction.url,
        R.string.network_owl_field_method to transaction.method,
        R.string.network_owl_field_status to status,
        response?.let { R.string.network_owl_field_protocol to it.protocol },
        R.string.network_owl_field_requested_at to fullTime.format(Instant.ofEpochMilli(transaction.requestedAt)),
        response?.let { R.string.network_owl_field_duration to "${it.tookMs} ms" },
        transaction.requestBody?.let { R.string.network_owl_field_request_size to formatBytes(it.byteCount) },
        response?.body?.let { R.string.network_owl_field_response_size to formatBytes(it.byteCount) },
        transaction.error?.let { R.string.network_owl_field_error to it },
    )

    SelectionContainer {
        LazyColumn(contentPadding = padding) {
            item { Spacer(Modifier.height(12.dp)) }
            itemsIndexed(fields) { index, (label, value) ->
                SegmentedItem(index, fields.size, onClick = null) {
                    Text(
                        text = stringResource(label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = value,
                        style = Inspector.codeStyle,
                        color = if (label == R.string.network_owl_field_status) Inspector.statusColor(transaction) else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ResponseTab(
    transaction: HttpTransaction,
    jsonOptions: JsonViewOptions,
    onJsonOptionsChange: (JsonViewOptions) -> Unit,
    padding: PaddingValues,
) {
    val response = transaction.response
    when {
        response != null -> MessageTab(
            headers = response.headers,
            body = response.body,
            bodyLoading = !response.bodyComplete,
            jsonOptions = jsonOptions,
            onJsonOptionsChange = onJsonOptionsChange,
            padding = padding,
        )
        transaction.error != null -> Notice(transaction.error, padding)
        else -> Notice(stringResource(R.string.network_owl_awaiting_response), padding)
    }
}

@Composable
internal fun Notice(text: String, padding: PaddingValues) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(padding).padding(16.dp),
    )
}

@Composable
private fun MessageTab(
    headers: List<Header>,
    body: Body?,
    bodyLoading: Boolean,
    jsonOptions: JsonViewOptions,
    onJsonOptionsChange: (JsonViewOptions) -> Unit,
    padding: PaddingValues,
) {
    val content by produceState<TextContent?>(null, body) {
        value = if (body is Body.Text) withContext(Dispatchers.Default) { TextContent.of(body) } else null
    }
    val json = (content as? TextContent.Json)?.body
    var expanded by remember(json) { mutableStateOf(json?.let { JsonLayout.defaultExpanded(it.element) }.orEmpty()) }
    val treeRows = remember(json, expanded, jsonOptions.mode) {
        if (json != null && jsonOptions.mode == JsonViewMode.Tree) JsonLayout.treeRows(json.element, expanded) else emptyList()
    }
    val headerItems = remember(headers) { headers.map { KeyValueItem(it.name, ValueLayout.header(it.value)) } }

    SelectionContainer {
        LazyColumn(contentPadding = padding) {
            item { SectionTitle(stringResource(R.string.network_owl_section_headers_count, headers.size)) }
            if (headers.isEmpty()) item { MutedLine(stringResource(R.string.network_owl_no_headers)) }
            keyValueRows(headerItems)

            item { SectionTitle(stringResource(R.string.network_owl_section_body)) }
            when {
                bodyLoading -> item { MutedLine(stringResource(R.string.network_owl_body_loading)) }
                body == null || (body is Body.Text && body.text.isEmpty()) ->
                    item { MutedLine(stringResource(R.string.network_owl_body_empty)) }
                body is Body.Binary ->
                    item { MutedLine(stringResource(R.string.network_owl_body_binary, formatBytes(body.byteCount))) }
                body is Body.NotCaptured -> item { MutedLine(stringResource(R.string.network_owl_body_not_captured)) }
                body is Body.Text -> {
                    if (body.truncated) {
                        item {
                            MutedLine(
                                stringResource(R.string.network_owl_body_truncated, formatBytes(body.byteCount)),
                            )
                        }
                    }
                    when (val text = content) {
                        is TextContent.Json -> {
                            jsonModeSelector(
                                mode = jsonOptions.mode,
                                onModeChange = { onJsonOptionsChange(jsonOptions.copy(mode = it)) },
                                wrap = jsonOptions.wrap,
                                onWrapChange = { onJsonOptionsChange(jsonOptions.copy(wrap = it)) },
                            )
                            when (jsonOptions.mode) {
                                JsonViewMode.Code -> jsonCode(text.body.codeLines, jsonOptions.wrap)
                                JsonViewMode.Tree -> jsonTree(treeRows) { id ->
                                    expanded = if (id in expanded) expanded - id else expanded + id
                                }
                                JsonViewMode.Table -> jsonTable(text.body.tableRows)
                            }
                        }
                        is TextContent.Plain -> items(text.lines) { line ->
                            Text(
                                text = line,
                                style = Inspector.codeStyle,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        null -> Unit
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
internal fun MutedLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp),
    )
}

private sealed interface TextContent {
    data class Plain(val lines: List<String>) : TextContent
    class Json(val body: JsonBody) : TextContent

    companion object {
        fun of(body: Body.Text): TextContent {
            val looksLikeJson = body.contentType?.contains("json") == true ||
                body.text.trimStart().let { it.startsWith("{") || it.startsWith("[") }
            val json = if (body.truncated || !looksLikeJson) null else JsonBody.parse(body.text)
            return json?.let(::Json) ?: Plain(body.text.lines())
        }
    }
}

private val fullTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
