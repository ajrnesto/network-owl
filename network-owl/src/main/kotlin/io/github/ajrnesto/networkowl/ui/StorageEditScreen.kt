package io.github.ajrnesto.networkowl.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.ajrnesto.networkowl.JsonLayout
import io.github.ajrnesto.networkowl.R
import io.github.ajrnesto.networkowl.storage.JsonEditing
import io.github.ajrnesto.networkowl.storage.JsonFormRow
import io.github.ajrnesto.networkowl.storage.StorageEntry
import io.github.ajrnesto.networkowl.storage.StorageSnapshot
import io.github.ajrnesto.networkowl.storage.StorageSource
import io.github.ajrnesto.networkowl.storage.ValueType
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

private enum class EditMode { Fields, Raw }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StorageEditScreen(source: StorageSource, key: String, onClose: () -> Unit) {
    val snapshot by remember(source) { source.entries }.collectAsState(initial = null)
    val entry = (snapshot as? StorageSnapshot.Loaded)?.entries?.firstOrNull { it.key == key }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val failedMessage = stringResource(R.string.network_owl_storage_write_failed)

    var draft by remember(entry?.value) { mutableStateOf(entry?.let { EditorDraft.of(it) }) }

    fun write(action: suspend () -> Unit) = scope.launch {
        runCatching { action() }
            .onSuccess { onClose() }
            .onFailure { snackbar.showSnackbar("$failedMessage: ${it.message}") }
    }

    val current = draft
    val output = current?.output()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.network_owl_storage_cancel))
                    }
                },
                title = {
                    Column {
                        Text(text = key, style = Inspector.codeStyle)
                        if (entry != null) {
                            Text(
                                text = entry.type.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = Inspector.colors.onBackgroundMuted,
                            )
                        }
                    }
                },
                actions = {
                    if (entry?.editable == true) {
                        TextButton(
                            onClick = { output?.let { value -> write { source.put(key, entry.type, value) } } },
                            enabled = output != null,
                        ) {
                            Text(stringResource(R.string.network_owl_storage_save))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            entry == null -> Notice(stringResource(R.string.network_owl_storage_loading), padding)
            current == null || !entry.editable -> ReadOnlyEntry(entry, padding, onDelete = { write { source.remove(key) } })
            else -> EditorBody(
                draft = current,
                onDraftChange = { draft = it },
                padding = padding,
                onDelete = { write { source.remove(key) } },
            )
        }
    }
}

private sealed interface EditorDraft {
    fun output(): Any?

    data class Scalar(val type: ValueType, val text: String) : EditorDraft {
        override fun output() = type.parse(text)
    }

    data class Json(
        val root: JsonElement,
        val mode: EditMode,
        val edits: Map<String, String>,
        val raw: String,
    ) : EditorDraft {
        val rows: List<JsonFormRow> by lazy { JsonEditing.rows(root) }

        fun parsedEdits(): Map<String, JsonElement>? {
            val kinds = rows.filterIsInstance<JsonFormRow.Field>().associate { it.id to it.kind }
            return edits.mapValues { (id, text) -> JsonEditing.parse(kinds.getValue(id), text) ?: return null }
        }

        fun committed(): JsonElement? = when (mode) {
            EditMode.Fields -> parsedEdits()?.let { JsonEditing.apply(root, it) }
            EditMode.Raw -> JsonLayout.parse(raw)
        }

        override fun output() = committed()?.let(JsonEditing::compact)

        fun switchTo(target: EditMode): Json? {
            if (target == mode) return this
            val element = committed() ?: return null
            return copy(root = element, mode = target, edits = emptyMap(), raw = JsonEditing.pretty(element))
        }
    }

    companion object {
        fun of(entry: StorageEntry): EditorDraft? {
            if (!entry.editable) return null
            val json = if (entry.type == ValueType.Str) JsonLayout.parse(entry.value) else null
            return json?.let { Json(it, EditMode.Fields, emptyMap(), JsonEditing.pretty(it)) }
                ?: Scalar(entry.type, entry.value)
        }
    }
}

@Composable
private fun EditorBody(
    draft: EditorDraft,
    onDraftChange: (EditorDraft) -> Unit,
    padding: PaddingValues,
    onDelete: () -> Unit,
) {
    when (draft) {
        is EditorDraft.Scalar -> Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScalarInput(draft, onDraftChange)
            DeleteButton(onDelete)
        }
        is EditorDraft.Json -> JsonEditor(draft, onDraftChange, padding, onDelete)
    }
}

@Composable
private fun ScalarInput(draft: EditorDraft.Scalar, onDraftChange: (EditorDraft) -> Unit) {
    if (draft.type == ValueType.Bool) {
        LabelledSwitch(
            label = draft.text,
            checked = draft.text == "true",
            onCheckedChange = { onDraftChange(draft.copy(text = it.toString())) },
        )
        return
    }
    val isText = draft.type == ValueType.Str
    CodeField(
        value = draft.text,
        onValueChange = { onDraftChange(draft.copy(text = it)) },
        isError = draft.output() == null,
        keyboardType = if (isText) KeyboardType.Text else KeyboardType.Decimal,
        singleLine = !isText,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isText) Modifier.heightIn(min = 160.dp) else Modifier),
    )
}

@Composable
private fun JsonEditor(
    draft: EditorDraft.Json,
    onDraftChange: (EditorDraft) -> Unit,
    padding: PaddingValues,
    onDelete: () -> Unit,
) {
    val colors = Inspector.colors
    Column(
        Modifier
            .padding(padding)
            .imePadding(),
    ) {
        SingleChoiceSegmentedButtonRow(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            EditMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = draft.mode == mode,
                    onClick = { draft.switchTo(mode)?.let(onDraftChange) },
                    enabled = draft.mode == mode || draft.committed() != null,
                    shape = SegmentedButtonDefaults.itemShape(index, EditMode.entries.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = colors.primary.copy(alpha = 0.12f),
                        activeContentColor = colors.primary,
                        activeBorderColor = colors.divider,
                        inactiveContainerColor = colors.background,
                        inactiveContentColor = colors.onBackgroundMuted,
                        inactiveBorderColor = colors.divider,
                    ),
                    label = {
                        Text(stringResource(if (mode == EditMode.Fields) R.string.network_owl_edit_fields else R.string.network_owl_edit_raw))
                    },
                )
            }
        }

        when (draft.mode) {
            EditMode.Fields -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            ) {
                items(draft.rows, key = { it.id }) { row ->
                    Column(Modifier.padding(start = (row.depth * 16).dp)) {
                        when (row) {
                            is JsonFormRow.Group -> GroupHeader(row)
                            is JsonFormRow.Field -> FieldInput(
                                row = row,
                                text = draft.edits[row.id] ?: row.initial,
                                onTextChange = { onDraftChange(draft.copy(edits = draft.edits + (row.id to it))) },
                            )
                        }
                    }
                }
                item { DeleteButton(onDelete, Modifier.padding(top = 16.dp)) }
            }
            EditMode.Raw -> Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CodeField(
                    value = draft.raw,
                    onValueChange = { onDraftChange(draft.copy(raw = it)) },
                    isError = draft.committed() == null,
                    keyboardType = KeyboardType.Text,
                    singleLine = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                DeleteButton(onDelete, Modifier.padding(bottom = 16.dp))
            }
        }
    }
}

@Composable
private fun GroupHeader(row: JsonFormRow.Group) {
    val colors = Inspector.colors
    val summary = if (row.isArray) {
        pluralStringResource(R.plurals.network_owl_json_items, row.size, row.size)
    } else {
        pluralStringResource(R.plurals.network_owl_json_keys, row.size, row.size)
    }
    Row(Modifier.padding(top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = row.label, style = MaterialTheme.typography.titleSmall, color = colors.jsonKey)
        Text(
            text = "  ·  $summary",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onBackgroundMuted,
        )
    }
}

@Composable
private fun FieldInput(row: JsonFormRow.Field, text: String, onTextChange: (String) -> Unit) {
    val colors = Inspector.colors
    Column(Modifier.padding(top = 10.dp)) {
        Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.label,
                style = MaterialTheme.typography.labelMedium,
                color = colors.jsonKey,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(
                    when (row.kind) {
                        JsonFormRow.Kind.Text -> R.string.network_owl_kind_text
                        JsonFormRow.Kind.Number -> R.string.network_owl_kind_number
                        JsonFormRow.Kind.Bool -> R.string.network_owl_kind_boolean
                        JsonFormRow.Kind.Null -> R.string.network_owl_kind_null
                    },
                ),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onBackgroundMuted,
            )
        }
        if (row.kind == JsonFormRow.Kind.Bool) {
            LabelledSwitch(label = text, checked = text == "true", onCheckedChange = { onTextChange(it.toString()) })
            return@Column
        }
        CodeField(
            value = text,
            onValueChange = onTextChange,
            isError = JsonEditing.parse(row.kind, text) == null,
            keyboardType = if (row.kind == JsonFormRow.Kind.Number) KeyboardType.Decimal else KeyboardType.Text,
            singleLine = row.kind == JsonFormRow.Kind.Number,
            placeholder = if (row.kind == JsonFormRow.Kind.Null) "null" else null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CodeField(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    keyboardType: KeyboardType,
    singleLine: Boolean,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
) {
    val colors = Inspector.colors
    val interactionSource = remember { MutableInteractionSource() }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.primary,
        unfocusedBorderColor = colors.divider,
        errorBorderColor = colors.serverError,
        cursorColor = colors.primary,
        errorCursorColor = colors.serverError,
        focusedContainerColor = colors.background,
        unfocusedContainerColor = colors.background,
        errorContainerColor = colors.background,
    )
    // OutlinedTextField pins a 56dp minimum height; a form of many short values needs it denser.
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = Inspector.codeStyle.copy(color = colors.onBackground),
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        cursorBrush = SolidColor(if (isError) colors.serverError else colors.primary),
        interactionSource = interactionSource,
        modifier = modifier,
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField,
                enabled = true,
                singleLine = singleLine,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                isError = isError,
                placeholder = placeholder?.let { { Text(it, style = Inspector.codeStyle, color = colors.onBackgroundMuted) } },
                colors = fieldColors,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = true,
                        isError = isError,
                        interactionSource = interactionSource,
                        colors = fieldColors,
                        shape = RoundedCornerShape(8.dp),
                    )
                },
            )
        },
    )
}

@Composable
private fun LabelledSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = Inspector.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = Inspector.codeStyle, color = colors.onBackground, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.primary, checkedThumbColor = colors.onPrimary),
        )
    }
}

@Composable
private fun ReadOnlyEntry(entry: StorageEntry, padding: PaddingValues, onDelete: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(if (entry.redacted) R.string.network_owl_storage_redacted else R.string.network_owl_storage_read_only),
            style = MaterialTheme.typography.bodyMedium,
            color = Inspector.colors.onBackgroundMuted,
        )
        DeleteButton(onDelete)
    }
}

@Composable
private fun DeleteButton(onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Inspector.colors
    OutlinedButton(
        onClick = onDelete,
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, colors.serverError.copy(alpha = 0.5f)),
    ) {
        Text(stringResource(R.string.network_owl_storage_delete_key), color = colors.serverError)
    }
}
