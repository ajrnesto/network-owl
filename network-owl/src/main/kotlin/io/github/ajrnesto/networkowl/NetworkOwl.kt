package io.github.ajrnesto.networkowl

import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import io.github.ajrnesto.networkowl.storage.DataStoreSource
import io.github.ajrnesto.networkowl.ui.NetworkOwlActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.Interceptor

object NetworkOwl {
    val isEnabled: Boolean = true

    @Volatile
    internal var config: NetworkOwlConfig = NetworkOwlConfig()
        private set

    internal val store = TransactionStore(capacity = { config.capacity })

    val interceptor: Interceptor = NetworkOwlInterceptor(config = { config }, store = store)

    fun install(context: Context, config: NetworkOwlConfig = NetworkOwlConfig()) {
        val app = context.applicationContext
        this.config = config
        store.onChange = if (config.showNotification) TransactionNotifier(app, store)::schedule else null
        if (config.showShortcut) InspectorShortcut.publish(app)
    }

    private val _dataStores = MutableStateFlow<List<DataStoreSource>>(emptyList())
    internal val dataStores: StateFlow<List<DataStoreSource>> = _dataStores.asStateFlow()

    fun register(name: String, dataStore: DataStore<Preferences>) {
        val source = DataStoreSource(name, dataStore, redactedKeys = { config.redactedStorageKeys })
        _dataStores.update { sources -> sources.filterNot { it.name == name } + source }
    }

    fun launchIntent(context: Context): Intent? = inspectorIntent(context)

    internal fun inspectorIntent(context: Context): Intent =
        Intent(context, NetworkOwlActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
