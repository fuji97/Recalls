package it.federicorapetti.recalls.ui.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.IntOffset
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
        transitionSpec = { slideForward() },
        popTransitionSpec = { slideBack() },
        predictivePopTransitionSpec = { _ -> slideBack(predictiveSlideSpec) },
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
                    RecallDetailViewModel(key.id, container.repository, container.appContext, container.httpClient)
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

private const val SLIDE_DURATION_MS = 350
private val slideSpec: FiniteAnimationSpec<IntOffset> =
    tween(durationMillis = SLIDE_DURATION_MS, easing = FastOutSlowInEasing)

// Linear so the page tracks the finger 1:1 while dragging (NavDisplay maps raw gesture progress
// to this spec's fraction). The *duration* here only governs the release/settle catch-up
// animation (NavDisplay.kt: `remainingDuration = (1 - fraction) * totalDuration`), so it's kept
// short and independent of SLIDE_DURATION_MS to avoid a laggy trailing snap on a quick flick-back.
private const val PREDICTIVE_SLIDE_DURATION_MS = 150
private val predictiveSlideSpec: FiniteAnimationSpec<IntOffset> =
    tween(durationMillis = PREDICTIVE_SLIDE_DURATION_MS, easing = LinearEasing)

private fun slideForward(): ContentTransform =
    slideInHorizontally(slideSpec) { fullWidth -> fullWidth } togetherWith
        slideOutHorizontally(slideSpec) { fullWidth -> -fullWidth }

private fun slideBack(spec: FiniteAnimationSpec<IntOffset> = slideSpec): ContentTransform =
    slideInHorizontally(spec) { fullWidth -> -fullWidth } togetherWith
        slideOutHorizontally(spec) { fullWidth -> fullWidth }


