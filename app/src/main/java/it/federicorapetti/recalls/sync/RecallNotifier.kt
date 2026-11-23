package it.federicorapetti.recalls.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import it.federicorapetti.recalls.MainActivity
import it.federicorapetti.recalls.R
import it.federicorapetti.recalls.data.local.RecallEntity
import it.federicorapetti.recalls.data.model.RecallSource
import it.federicorapetti.recalls.data.previewImage
import it.federicorapetti.recalls.data.settings.SettingsRepository
import it.federicorapetti.recalls.ui.list.RegionFilter
import it.federicorapetti.recalls.ui.navigation.LaunchRequest
import it.federicorapetti.recalls.ui.common.recallReason
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
            notifyRegion(
                channelId = CHANNEL_EU,
                summaryId = NOTIF_ID_EU,
                items = euItems,
                manyTitle = context.resources.getQuantityString(R.plurals.notif_many_eu, euItems.size, euItems.size),
                filter = RegionFilter.EU
            )
        }
        if (itItems.isNotEmpty() && settings.notifyIt.first()) {
            notifyRegion(
                channelId = CHANNEL_IT,
                summaryId = NOTIF_ID_IT,
                items = itItems,
                manyTitle = context.resources.getQuantityString(R.plurals.notif_many_it, itItems.size, itItems.size),
                filter = RegionFilter.ITALY
            )
        }
    }

    /** One notification per Recall (at most [MAX_RECALL_NOTIFICATIONS]), grouped under a summary when there are several. */
    private suspend fun notifyRegion(
        channelId: String,
        summaryId: Int,
        items: List<RecallEntity>,
        manyTitle: String,
        filter: RegionFilter
    ) {
        items.take(MAX_RECALL_NOTIFICATIONS).forEach { postRecall(channelId, it) }
        if (items.size < 2) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            LaunchRequest.Filter(filter).writeTo(this)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, summaryId, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val style = NotificationCompat.InboxStyle()
        items.take(MAX_RECALL_NOTIFICATIONS).forEach { style.addLine(it.title) }
        if (items.size > MAX_RECALL_NOTIFICATIONS) {
            style.setSummaryText(context.getString(R.string.notif_more, items.size - MAX_RECALL_NOTIFICATIONS))
        }
        val summary = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_recall)
            .setContentTitle(manyTitle)
            .setStyle(style)
            .setGroup(channelId)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
        if (hasPermission()) NotificationManagerCompat.from(context).notify(summaryId, summary.build())
    }

    private suspend fun postRecall(channelId: String, item: RecallEntity) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            LaunchRequest.Detail(item.id).writeTo(this)
        }
        // A distinct requestCode per recall keeps each notification's extras separate.
        val pendingIntent = PendingIntent.getActivity(
            context, item.id.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = listOfNotNull(item.brand?.takeIf { it.isNotBlank() }, recallReason(context, item))
            .joinToString(" · ")
        val sourceLabel = context.getString(
            when (item.source) {
                RecallSource.SAFETY_GATE -> R.string.source_eu
                RecallSource.IT_OPERATOR -> R.string.source_it_operator
                RecallSource.IT_MINISTRY -> R.string.source_it_ministry
            }
        )
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_recall)
            .setContentTitle(item.title)
            .setContentText(text)
            .setSubText(sourceLabel)
            .setGroup(channelId)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
        val image = loadPreview(item)
        if (image != null) {
            builder.setLargeIcon(image)
                .setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(image)
                        .bigLargeIcon(null as Bitmap?)
                        .setSummaryText(text)
                )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(text))
        }
        if (hasPermission()) NotificationManagerCompat.from(context).notify(item.id, NOTIF_ID_RECALL, builder.build())
    }

    /** GLOSSARY: Preview image, as a bitmap; null when there is none or it can't be loaded. */
    private suspend fun loadPreview(item: RecallEntity): Bitmap? {
        val model = item.previewImage(context.filesDir) ?: return null
        val request = ImageRequest.Builder(context)
            .data(model)
            .size(PREVIEW_SIZE_PX)
            .allowHardware(false)
            .build()
        return (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
    }

    private fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        const val CHANNEL_EU = "recalls_eu"
        const val CHANNEL_IT = "recalls_it"
        private const val NOTIF_ID_EU = 1001
        private const val NOTIF_ID_IT = 1002
        private const val NOTIF_ID_RECALL = 1003
        private const val MAX_RECALL_NOTIFICATIONS = 5
        private const val PREVIEW_SIZE_PX = 512
    }
}
