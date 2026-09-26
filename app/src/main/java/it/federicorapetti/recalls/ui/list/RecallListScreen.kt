package it.federicorapetti.recalls.ui.list

import android.Manifest
import android.content.pm.PackageManager
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.FloatingToolbarScrollBehavior
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.federicorapetti.recalls.R
import it.federicorapetti.recalls.data.remote.salute.ROME
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun RecallListScreen(
    viewModel: RecallListViewModel,
    onOpenDetail: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionAsked by viewModel.notifPermissionAsked.collectAsStateWithLifecycle(initialValue = true)
    val context = LocalContext.current
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val floatingToolbarScrollBehavior =
        FloatingToolbarDefaults.exitAlwaysScrollBehavior(FloatingToolbarExitDirection.Bottom)
    val snackbarHostState = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        viewModel.onNotifPermissionAsked()
    }
    LaunchedEffect(permissionAsked) {
        if (!permissionAsked) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                viewModel.onNotifPermissionAsked()
            } else {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(uiState.error) {
        val message = uiState.error
        if (message != null) {
            snackbarHostState.showSnackbar(context.getString(R.string.error_sync_failed, message))
            viewModel.consumeError()
        }
    }

    Scaffold(
        modifier = Modifier
            .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
            .nestedScroll(floatingToolbarScrollBehavior),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.list_title)) },
                subtitle = { Text(stringResource(R.string.list_subtitle_updated, formatLastSync(uiState.lastSync))) },
                actions = {
                    IconButton(onClick = viewModel::markAllRead) {
                        Icon(
                            painterResource(R.drawable.ic_done_all),
                            contentDescription = stringResource(R.string.action_mark_all_read)
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            painterResource(R.drawable.ic_settings),
                            contentDescription = stringResource(R.string.action_settings)
                        )
                    }
                },
                scrollBehavior = topAppBarScrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            RecallListToolbar(
                filter = uiState.filter,
                onFilterChange = viewModel::setFilter,
                unreadOnly = uiState.unreadOnly,
                onUnreadOnlyChange = viewModel::setUnreadOnly,
                scrollBehavior = floatingToolbarScrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            SearchBarDefaults.InputField(
                query = uiState.query,
                onQueryChange = viewModel::setQuery,
                onSearch = {},
                expanded = false,
                onExpandedChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = {
                    Icon(painterResource(R.drawable.ic_search), contentDescription = null)
                },
                trailingIcon = {
                    if (uiState.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(
                                painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.action_clear_search)
                            )
                        }
                    }
                }
            )

            val pullState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh,
                state = pullState,
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        state = pullState,
                        isRefreshing = uiState.isRefreshing,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                },
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    uiState.groups.isEmpty() && uiState.isRefreshing -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            ContainedLoadingIndicator()
                        }
                    }

                    uiState.groups.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.empty))
                        }
                    }

                    else -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            uiState.groups.forEach { group ->
                                stickyHeader(key = group.date.toString()) {
                                    DayHeader(group.date)
                                }
                                items(group.items, key = { it.id }) { item ->
                                    RecallCard(
                                        item = item,
                                        onClick = { onOpenDetail(item.id) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecallListToolbar(
    filter: SourceFilter,
    onFilterChange: (SourceFilter) -> Unit,
    unreadOnly: Boolean,
    onUnreadOnlyChange: (Boolean) -> Unit,
    scrollBehavior: FloatingToolbarScrollBehavior
) {
    HorizontalFloatingToolbar(
        expanded = true,
        scrollBehavior = scrollBehavior
    ) {
        ToggleButton(
            checked = filter == SourceFilter.ALL,
            onCheckedChange = { if (it) onFilterChange(SourceFilter.ALL) },
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes()
        ) { Text(stringResource(R.string.filter_all)) }
        ToggleButton(
            checked = filter == SourceFilter.EU,
            onCheckedChange = { if (it) onFilterChange(SourceFilter.EU) },
            shapes = ButtonGroupDefaults.connectedMiddleButtonShapes()
        ) { Text(stringResource(R.string.filter_eu)) }
        ToggleButton(
            checked = filter == SourceFilter.IT,
            onCheckedChange = { if (it) onFilterChange(SourceFilter.IT) },
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes()
        ) { Text(stringResource(R.string.filter_it)) }
        Spacer(Modifier.width(8.dp))
        IconToggleButton(checked = unreadOnly, onCheckedChange = onUnreadOnlyChange) {
            Icon(
                painterResource(R.drawable.ic_notifications),
                contentDescription = stringResource(R.string.filter_unread)
            )
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Text(
            text = formatDayHeader(date),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun formatDayHeader(date: LocalDate): String {
    val today = LocalDate.now(ROME)
    return when (date) {
        today -> stringResource(R.string.today)
        today.minusDays(1) -> stringResource(R.string.yesterday)
        else -> {
            val locale = LocalConfiguration.current.locales[0]
            date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))
        }
    }
}

@Composable
private fun formatLastSync(lastSync: Long?): String {
    if (lastSync == null) return stringResource(R.string.settings_never)
    val now = System.currentTimeMillis()
    return DateUtils.getRelativeTimeSpanString(lastSync, now, DateUtils.MINUTE_IN_MILLIS).toString()
}
