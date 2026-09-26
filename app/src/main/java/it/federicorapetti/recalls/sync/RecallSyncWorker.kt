package it.federicorapetti.recalls.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import it.federicorapetti.recalls.RecallsApp
import java.io.IOException

class RecallSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as RecallsApp).container
        val result = container.repository.sync()
        if (result.errors.size == 3 && result.errors.values.all { it is IOException }) {
            return Result.retry()
        }
        container.notifier.notifyNew(result.newItems, container.settings)
        return Result.success()
    }
}
