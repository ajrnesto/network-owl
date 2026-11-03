package io.github.ajrnesto.networkowl.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import io.github.ajrnesto.networkowl.FormattedValue
import io.github.ajrnesto.networkowl.R
import io.github.ajrnesto.networkowl.ValueLayout
import io.github.ajrnesto.networkowl.storage.StorageSnapshot
import io.github.ajrnesto.networkowl.storage.StorageSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StorageDetailScreen(source: StorageSource, onEdit: (String) -> Unit, onBack: () -> Unit) {
    val snapshot by remember(source) { source.entries }.collectAsState(initial = null)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.network_owl_back))
                    }
                },
                title = { Text(text = source.name, style = Inspector.codeStyle) },
            )
        },
    ) { padding ->
        when (val current = snapshot) {
            null -> Notice(stringResource(R.string.network_owl_storage_loading), padding)
            is StorageSnapshot.Failed -> Notice(current.message, padding)
            is StorageSnapshot.Loaded -> {
                val entries = current.entries
                val items = remember(entries) {
                    entries.map { entry ->
                        val value = if (entry.redacted) FormattedValue.Redacted else ValueLayout.stored(entry.value)
                        KeyValueItem(entry.key, value, meta = entry.type.label)
                    }
                }
                LazyColumn(contentPadding = padding) {
                    item { SectionTitle(stringResource(R.string.network_owl_storage_keys_title, entries.size)) }
                    if (entries.isEmpty()) item { MutedLine(stringResource(R.string.network_owl_storage_empty)) }
                    keyValueRows(items, onClick = { index -> onEdit(entries[index].key) })
                }
            }
        }
    }
}
