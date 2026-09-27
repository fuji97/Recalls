package it.federicorapetti.recalls

import android.content.Context
import androidx.room.Room
import it.federicorapetti.recalls.data.RecallRepository
import it.federicorapetti.recalls.data.local.RecallDatabase
import it.federicorapetti.recalls.data.remote.buildHttpClient
import it.federicorapetti.recalls.data.remote.safetygate.SafetyGateApi
import it.federicorapetti.recalls.data.remote.salute.SaluteApi
import it.federicorapetti.recalls.data.settings.SettingsRepository
import it.federicorapetti.recalls.sync.RecallNotifier
import it.federicorapetti.recalls.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val httpClient: OkHttpClient = buildHttpClient()

    val database: RecallDatabase = Room.databaseBuilder(
        context,
        RecallDatabase::class.java,
        RecallDatabase.DB_NAME
    ).addMigrations(RecallDatabase.MIGRATION_1_2).build()

    val dao = database.recallDao()

    val settings = SettingsRepository(context)

    val safetyGateApi = SafetyGateApi(httpClient)

    val saluteApi = SaluteApi(httpClient)

    val repository = RecallRepository(database, dao, safetyGateApi, saluteApi)

    val notifier = RecallNotifier(context)

    val scheduler = SyncScheduler(context)
}
