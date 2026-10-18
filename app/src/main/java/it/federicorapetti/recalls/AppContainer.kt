package it.federicorapetti.recalls

import android.content.Context
import androidx.room.Room
import it.federicorapetti.recalls.data.BarcodeIndex
import it.federicorapetti.recalls.data.RecallRepository
import it.federicorapetti.recalls.data.RecallStore
import it.federicorapetti.recalls.data.local.RecallDatabase
import it.federicorapetti.recalls.data.remote.buildHttpClient
import it.federicorapetti.recalls.data.remote.safetygate.SafetyGateApi
import it.federicorapetti.recalls.data.remote.salute.OperatorPdfLoader
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
    ).addMigrations(RecallDatabase.MIGRATION_1_2, RecallDatabase.MIGRATION_2_3).build()

    val store = RecallStore(database)

    val settings = SettingsRepository(context)

    val safetyGateApi = SafetyGateApi(httpClient)

    val saluteApi = SaluteApi(httpClient)

    val barcodeIndex = BarcodeIndex(database, safetyGateApi)

    val repository = RecallRepository(store, barcodeIndex, safetyGateApi, saluteApi)

    val operatorPdfLoader = OperatorPdfLoader(appContext, httpClient)

    val notifier = RecallNotifier(context)

    val scheduler = SyncScheduler(context)
}
