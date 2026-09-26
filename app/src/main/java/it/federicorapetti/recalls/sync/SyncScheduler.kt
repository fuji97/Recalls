package it.federicorapetti.recalls.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class SyncScheduler(private val context: Context) {

    fun schedulePeriodic(hours: Int) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<RecallSyncWorker>(hours.toLong(), TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun syncNow() {
        val request = OneTimeWorkRequestBuilder<RecallSyncWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            NOW_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "recall-sync"
        private const val NOW_WORK_NAME = "recall-sync-now"
    }
}
