package io.github.ajrnesto.networkowl.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ajrnesto.networkowl.R
import io.github.ajrnesto.networkowl.storage.StorageSnapshot
import io.github.ajrnesto.networkowl.storage.StorageSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StorageListScreen(
    dataStores: List<StorageSource>,
    sharedPreferences: List<StorageSource>,
    onSelect: (String) -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.network_owl_storage_title)) }) },
        bottomBar = bottomBar,
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            sources(R.string.network_owl_storage_datastore, R.string.network_owl_storage_no_datastore, dataStores, onSelect)
            sources(R.string.network_owl_storage_prefs, R.string.network_owl_storage_no_prefs, sharedPreferences, onSelect)
        }
    }
}

private fun LazyListScope.sources(
    title: Int,
    empty: Int,
    sources: List<StorageSource>,
    onSelect: (String) -> Unit,
) {
    item { SectionTitle(stringResource(title)) }
    if (sources.isEmpty()) item { MutedLine(stringResource(empty)) }
    itemsIndexed(sources, key = { _, source -> source.id }) { index, source ->
        val colors = Inspector.colors
        val snapshot by remember(source) { source.entries }.collectAsState(initial = null)
        Column(
            Modifier
                .codeBlockSegment(index, sources.size, colors.codeBackground)
                .clickable { onSelect(source.id) },
        ) {
            if (index > 0) HorizontalDivider(color = colors.divider)
            Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(text = source.name, style = Inspector.codeStyle, color = colors.onBackground)
                    val count = (snapshot as? StorageSnapshot.Loaded)?.entries?.size
                    if (count != null) {
                        Text(
                            text = pluralStringResource(R.plurals.network_owl_storage_keys, count, count),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onBackgroundMuted,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.onBackgroundMuted,
                )
            }
        }
    }
}
