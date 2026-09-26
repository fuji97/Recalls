package it.federicorapetti.recalls

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import it.federicorapetti.recalls.sync.RecallNotifier
import it.federicorapetti.recalls.ui.list.SourceFilter
import it.federicorapetti.recalls.ui.navigation.LaunchRequest
import it.federicorapetti.recalls.ui.navigation.RecallsNavHost
import it.federicorapetti.recalls.ui.theme.RecallsTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val launchRequest = MutableStateFlow<LaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyIntent(intent)

        val container = (application as RecallsApp).container
        setContent {
            RecallsTheme {
                RecallsNavHost(
                    container = container,
                    launchRequest = launchRequest,
                    onLaunchConsumed = { launchRequest.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIntent(intent)
    }

    private fun applyIntent(intent: Intent) {
        val recallId = intent.getStringExtra(RecallNotifier.EXTRA_RECALL_ID)
        val sourceFilter = intent.getStringExtra(RecallNotifier.EXTRA_SOURCE_FILTER)
        launchRequest.value = when {
            recallId != null -> LaunchRequest.Detail(recallId)
            sourceFilter == RecallNotifier.FILTER_EU -> LaunchRequest.Filter(SourceFilter.EU)
            sourceFilter == RecallNotifier.FILTER_IT -> LaunchRequest.Filter(SourceFilter.IT)
            else -> null
        }
    }
}
