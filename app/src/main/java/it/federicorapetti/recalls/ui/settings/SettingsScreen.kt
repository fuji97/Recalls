package it.federicorapetti.recalls.ui.settings

import android.content.Intent
import android.provider.Settings as AndroidSettings
import android.text.format.DateUtils
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.federicorapetti.recalls.R

private val INTERVAL_OPTIONS = listOf(1, 3, 6, 12, 24)

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val intervalHours by viewModel.intervalHours.collectAsStateWithLifecycle(initialValue = 6)
    val notifyEu by viewModel.notifyEu.collectAsStateWithLifecycle(initialValue = true)
    val notifyIt by viewModel.notifyIt.collectAsStateWithLifecycle(initialValue = true)
    val lastSync by viewModel.lastSync.collectAsStateWithLifecycle(initialValue = null)
    val context = LocalContext.current
    var showIntervalDialog by remember { mutableStateOf(false) }

    var notificationsEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_check_interval_title)) },
                supportingContent = { Text(stringResource(R.string.settings_check_interval_summary, intervalHours)) },
                modifier = Modifier.clickable { showIntervalDialog = true }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_notify_eu_title)) },
                trailingContent = { Switch(checked = notifyEu, onCheckedChange = viewModel::setNotifyEu) }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_notify_it_title)) },
                trailingContent = { Switch(checked = notifyIt, onCheckedChange = viewModel::setNotifyIt) }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_system_notifications_title)) },
                supportingContent = if (!notificationsEnabled) {
                    { Text(stringResource(R.string.settings_notifications_blocked)) }
                } else {
                    null
                },
                modifier = Modifier.clickable {
                    val intent = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                    context.startActivity(intent)
                }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_last_update_title)) },
                supportingContent = { Text(formatLastSync(lastSync)) },
                trailingContent = {
                    FilledTonalButton(onClick = viewModel::checkNow) {
                        Text(stringResource(R.string.settings_check_now))
                    }
                }
            )
            Text(
                text = stringResource(R.string.settings_data_sources_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_source_eu)) },
                supportingContent = { Text(stringResource(R.string.settings_source_licence_note)) },
                modifier = Modifier.clickable {
                    CustomTabsIntent.Builder().build()
                        .launchUrl(context, "https://ec.europa.eu/safety-gate-alerts".toUri())
                }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_source_it)) },
                supportingContent = { Text(stringResource(R.string.settings_source_licence_note)) },
                modifier = Modifier.clickable {
                    CustomTabsIntent.Builder().build().launchUrl(
                        context,
                        "https://www.salute.gov.it/new/it/avvisi/avvisi-e-richiami-di-prodotti-alimentari/".toUri()
                    )
                }
            )
        }

        if (showIntervalDialog) {
            AlertDialog(
                onDismissRequest = { showIntervalDialog = false },
                title = { Text(stringResource(R.string.settings_interval_dialog_title)) },
                text = {
                    Column {
                        INTERVAL_OPTIONS.forEach { hours ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setIntervalHours(hours)
                                        showIntervalDialog = false
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                RadioButton(
                                    selected = hours == intervalHours,
                                    onClick = {
                                        viewModel.setIntervalHours(hours)
                                        showIntervalDialog = false
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.hours_format, hours))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showIntervalDialog = false }) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            )
        }
    }
}

@Composable
private fun formatLastSync(lastSync: Long?): String {
    if (lastSync == null) return stringResource(R.string.settings_never)
    val now = System.currentTimeMillis()
    return DateUtils.getRelativeTimeSpanString(lastSync, now, DateUtils.MINUTE_IN_MILLIS).toString()
}
