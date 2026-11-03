package io.github.ajrnesto.networkowl

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.ajrnesto.networkowl.HttpTransaction.Status

internal class TransactionNotifier(
    private val context: Context,
    private val store: TransactionStore,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val postRunnable = Runnable { post() }
    private val manager = NotificationManagerCompat.from(context)

    init {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.network_owl_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    // Android silently drops notification updates posted faster than its rate limit,
    // which would leave the last state of a burst unshown.
    fun schedule() {
        handler.removeCallbacks(postRunnable)
        handler.postDelayed(postRunnable, DEBOUNCE_MS)
    }

    @SuppressLint("MissingPermission")
    private fun post() {
        if (!canPost()) return
        val transactions = store.transactions.value
        if (transactions.isEmpty()) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        val lines = transactions.take(MAX_LINES).map(::summary)
        val style = NotificationCompat.InboxStyle().also { inbox -> lines.forEach(inbox::addLine) }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.network_owl_ic_notification)
            .setContentTitle(context.getString(R.string.network_owl_notification_title, transactions.size))
            .setContentText(lines.first())
            .setStyle(style)
            .setContentIntent(contentIntent())
            .setOnlyAlertOnce(true)
            .setLocalOnly(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun canPost(): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        NOTIFICATION_ID,
        NetworkOwl.inspectorIntent(context),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun summary(transaction: HttpTransaction): String {
        val status = when (transaction.status) {
            Status.Pending -> "…"
            Status.Failed -> "!!!"
            Status.Complete -> transaction.response?.code.toString()
        }
        return "$status ${transaction.method} ${transaction.path}"
    }

    private companion object {
        const val CHANNEL_ID = "network_owl_activity"
        const val NOTIFICATION_ID = 0x6E6C
        const val MAX_LINES = 5
        const val DEBOUNCE_MS = 300L
    }
}
