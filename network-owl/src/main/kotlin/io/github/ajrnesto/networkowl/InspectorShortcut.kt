package io.github.ajrnesto.networkowl

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

internal object InspectorShortcut {
    private const val ID = "network_owl_inspector"

    fun publish(context: Context) {
        val intent = NetworkOwl.inspectorIntent(context).setAction(Intent.ACTION_VIEW)
        val label = context.getString(R.string.network_owl_shortcut_label)
        val shortcut = ShortcutInfoCompat.Builder(context, ID)
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(IconCompat.createWithResource(context, R.drawable.network_owl_ic_shortcut))
            .setIntent(intent)
            .build()
        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
    }
}
