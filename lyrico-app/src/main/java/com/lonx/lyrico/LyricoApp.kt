package com.lonx.lyrico

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.rememberNavController
import com.lonx.lyrico.viewmodel.SongListViewModel
import com.moriafly.salt.ui.Surface
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.animations.NavHostAnimatedDestinationStyle
import com.ramcosta.composedestinations.generated.NavGraphs
import org.koin.androidx.compose.koinViewModel

@Composable
fun LyricoApp() {
    val navController = rememberNavController()
    val songListViewModel: SongListViewModel = koinViewModel()

    // When the app's main UI is composed, trigger an initial scan if the database is empty.
    // This runs only once when LyricoApp is first displayed after permissions are granted.
    LaunchedEffect(Unit) {
        songListViewModel.initialScanIfEmpty()
    }


    Surface(
        modifier = Modifier.fillMaxSize(),
    ) {
        DestinationsNavHost(
            navGraph = NavGraphs.root,
            navController = navController,
            dependenciesContainerBuilder = {

            },
            defaultTransitions = object : NavHostAnimatedDestinationStyle() {
                override val enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
                    {
                        slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec = tween(
                                durationMillis = 500,
                                easing = FastOutSlowInEasing
                            )
                        )
                    }

                override val exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
                    {
                        slideOutHorizontally(
                            targetOffsetX = { -it / 5 },
                            animationSpec = tween(
                                durationMillis = 500,
                                easing = FastOutSlowInEasing
                            )
                        )
                    }

                override val popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
                    {
                        slideInHorizontally(
                            initialOffsetX = { -it / 5 },
                            animationSpec = tween(
                                durationMillis = 500,
                                easing = FastOutSlowInEasing
                            )
                        )
                    }

                override val popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
                    {
                        slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = tween(
                                durationMillis = 500,
                                easing = FastOutSlowInEasing
                            )
                        )
                    }
            }
        )
    }

}
