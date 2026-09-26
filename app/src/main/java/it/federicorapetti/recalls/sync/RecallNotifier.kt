package it.federicorapetti.recalls.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import it.federicorapetti.recalls.MainActivity
import it.federicorapetti.recalls.R
import it.federicorapetti.recalls.data.local.RecallEntity
import it.federicorapetti.recalls.data.model.RecallSource
import it.federicorapetti.recalls.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first

class RecallNotifier(private val context: Context) {

    fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_EU, context.getString(R.string.channel_eu), NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_IT, context.getString(R.string.channel_it), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    suspend fun notifyNew(newItems: Map<RecallSource, List<RecallEntity>>, settings: SettingsRepository) {
        if (!hasPermission() || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val euItems = newItems[RecallSource.SAFETY_GATE].orEmpty()
        val itItems = (newItems[RecallSource.IT_OPERATOR].orEmpty() + newItems[RecallSource.IT_MINISTRY].orEmpty())
            .sortedByDescending { it.publishedAt }

        if (euItems.isNotEmpty() && settings.notifyEu.first()) {
            notifyGroup(
                channelId = CHANNEL_EU,
                notificationId = NOTIF_ID_EU,
                requestCode = NOTIF_ID_EU,
                items = euItems,
                singleTitleRes = R.string.notif_new_eu,
                manyTitle = context.resources.getQuantityString(R.plurals.notif_many_eu, euItems.size, euItems.size),
                filterExtra = FILTER_EU
            )
        }
        if (itItems.isNotEmpty() && settings.notifyIt.first()) {
            notifyGroup(
                channelId = CHANNEL_IT,
                notificationId = NOTIF_ID_IT,
                requestCode = NOTIF_ID_IT,
                items = itItems,
                singleTitleRes = R.string.notif_new_it,
                manyTitle = context.resources.getQuantityString(R.plurals.notif_many_it, itItems.size, itItems.size),
                filterExtra = FILTER_IT
            )
        }
    }

    private fun notifyGroup(
        channelId: String,
        notificationId: Int,
        requestCode: Int,
        items: List<RecallEntity>,
        singleTitleRes: Int,
        manyTitle: String,
        filterExtra: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (items.size == 1) {
                putExtra(EXTRA_RECALL_ID, items.first().id)
            } else {
                putExtra(EXTRA_SOURCE_FILTER, filterExtra)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_recall)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (items.size == 1) {
            val item = items.first()
            builder.setContentTitle(context.getString(singleTitleRes))
                .setContentText(item.title)
                .setSubText(item.brand)
        } else {
            builder.setContentTitle(manyTitle)
            val style = NotificationCompat.InboxStyle()
            items.take(5).forEach { style.addLine(it.title) }
            if (items.size > 5) {
                style.setSummaryText(context.getString(R.string.notif_more, items.size - 5))
            }
            builder.setStyle(style)
        }

        if (!hasPermission()) return
        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    private fun hasPermission(): Boolean =
        ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        const val CHANNEL_EU = "recalls_eu"
        const val CHANNEL_IT = "recalls_it"
        const val EXTRA_RECALL_ID = "recall_id"
        const val EXTRA_SOURCE_FILTER = "source_filter"
        const val FILTER_EU = "EU"
        const val FILTER_IT = "IT"
        private const val NOTIF_ID_EU = 1001
        private const val NOTIF_ID_IT = 1002
    }
}
