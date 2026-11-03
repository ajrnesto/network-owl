package io.github.ajrnesto.networkowl

import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import okhttp3.Interceptor

object NetworkOwl {
    val isEnabled: Boolean = false

    val interceptor: Interceptor = Interceptor { chain -> chain.proceed(chain.request()) }

    fun install(context: Context, config: NetworkOwlConfig = NetworkOwlConfig()) = Unit

    fun register(name: String, dataStore: DataStore<Preferences>) = Unit

    fun launchIntent(context: Context): Intent? = null
}
