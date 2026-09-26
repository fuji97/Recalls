package it.federicorapetti.recalls.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import it.federicorapetti.recalls.AppContainer
import it.federicorapetti.recalls.ui.detail.RecallDetailScreen
import it.federicorapetti.recalls.ui.detail.RecallDetailViewModel
import it.federicorapetti.recalls.ui.list.RecallListScreen
import it.federicorapetti.recalls.ui.list.RecallListViewModel
import it.federicorapetti.recalls.ui.settings.SettingsScreen
import it.federicorapetti.recalls.ui.settings.SettingsViewModel
import kotlinx.coroutines.flow.StateFlow

@Composable
fun RecallsNavHost(
    container: AppContainer,
    launchRequest: StateFlow<LaunchRequest?>,
    onLaunchConsumed: () -> Unit
) {
    val backStack = rememberNavBackStack(RecallsListKey)
    val listViewModel = viewModel { RecallListViewModel(container.repository, container.settings) }

    val request by launchRequest.collectAsStateWithLifecycle()
    LaunchedEffect(request) {
        val current = request ?: return@LaunchedEffect
        backStack.clear()
        backStack.add(RecallsListKey)
        when (current) {
            is LaunchRequest.Detail -> backStack.add(RecallDetailKey(current.id))
            is LaunchRequest.Filter -> listViewModel.setFilter(current.filter)
        }
        onLaunchConsumed()
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            entry<RecallsListKey> {
                RecallListScreen(
                    viewModel = listViewModel,
                    onOpenDetail = { id -> backStack.add(RecallDetailKey(id)) },
                    onOpenSettings = { backStack.add(SettingsKey) }
                )
            }
            entry<RecallDetailKey> { key ->
                val detailViewModel = viewModel {
                    RecallDetailViewModel(key.id, container.repository)
                }
                RecallDetailScreen(
                    viewModel = detailViewModel,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<SettingsKey> {
                val settingsViewModel = viewModel {
                    SettingsViewModel(container.settings, container.scheduler, container.repository)
                }
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
        }
    )
}
