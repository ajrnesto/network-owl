package io.github.ajrnesto.networkowl.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ajrnesto.networkowl.NetworkOwl
import io.github.ajrnesto.networkowl.R
import io.github.ajrnesto.networkowl.storage.SharedPreferencesSource

internal class NetworkOwlActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            InspectorTheme(NetworkOwl.config.theme) {
                InspectorApp()
            }
        }
    }
}

private enum class Section(@StringRes val label: Int, @DrawableRes val icon: Int) {
    Network(R.string.network_owl_section_network, R.drawable.network_owl_ic_notification),
    Storage(R.string.network_owl_section_storage, R.drawable.network_owl_ic_storage),
}

@Composable
private fun InspectorApp() {
    var section by rememberSaveable { mutableStateOf(Section.Network) }
    val sectionState = rememberSaveableStateHolder()
    val bottomBar: @Composable () -> Unit = { SectionBar(section, onSelect = { section = it }) }

    sectionState.SaveableStateProvider(section.name) {
        when (section) {
            Section.Network -> NetworkSection(bottomBar)
            Section.Storage -> StorageSection(bottomBar)
        }
    }
}

@Composable
private fun SectionBar(selected: Section, onSelect: (Section) -> Unit) {
    NavigationBar(containerColor = Inspector.colors.background) {
        Section.entries.forEach { section ->
            NavigationBarItem(
                selected = section == selected,
                onClick = { onSelect(section) },
                icon = { Icon(painterResource(section.icon), contentDescription = null) },
                label = { Text(stringResource(section.label)) },
            )
        }
    }
}

@Composable
private fun NetworkSection(bottomBar: @Composable () -> Unit) {
    val store = NetworkOwl.store
    val transactions by store.transactions.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var jsonMode by rememberSaveable { mutableStateOf(JsonViewMode.Code) }
    var wrapCode by rememberSaveable { mutableStateOf(true) }
    val selected = selectedId?.let { id -> transactions.firstOrNull { it.id == id } }

    if (selected == null) {
        TransactionListScreen(
            transactions = transactions,
            onSelect = { selectedId = it },
            onClear = store::clear,
            bottomBar = bottomBar,
        )
    } else {
        BackHandler { selectedId = null }
        TransactionDetailScreen(
            transaction = selected,
            jsonOptions = JsonViewOptions(jsonMode, wrapCode),
            onJsonOptionsChange = { options ->
                jsonMode = options.mode
                wrapCode = options.wrap
            },
            onBack = { selectedId = null },
        )
    }
}

@Composable
private fun StorageSection(bottomBar: @Composable () -> Unit) {
    val context = LocalContext.current
    val dataStores by NetworkOwl.dataStores.collectAsStateWithLifecycle()
    val sharedPreferences = remember {
        SharedPreferencesSource.discover(context).map { name ->
            SharedPreferencesSource(context.applicationContext, name, redactedKeys = { NetworkOwl.config.redactedStorageKeys })
        }
    }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = selectedId?.let { id -> (dataStores + sharedPreferences).firstOrNull { it.id == id } }
    val editing = editingKey

    when {
        selected == null -> StorageListScreen(
            dataStores = dataStores,
            sharedPreferences = sharedPreferences,
            onSelect = { selectedId = it },
            bottomBar = bottomBar,
        )
        editing != null -> {
            BackHandler { editingKey = null }
            StorageEditScreen(source = selected, key = editing, onClose = { editingKey = null })
        }
        else -> {
            BackHandler { selectedId = null }
            StorageDetailScreen(source = selected, onEdit = { editingKey = it }, onBack = { selectedId = null })
        }
    }
}
