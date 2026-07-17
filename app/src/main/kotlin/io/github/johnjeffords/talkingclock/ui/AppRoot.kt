package io.github.johnjeffords.talkingclock.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import io.github.johnjeffords.talkingclock.R
import io.github.johnjeffords.talkingclock.ui.clock.ClockRoute
import io.github.johnjeffords.talkingclock.ui.stopwatch.StopwatchRoute
import io.github.johnjeffords.talkingclock.ui.timer.TimerRoute
import kotlinx.coroutines.launch

/**
 * The app shell: the navigation drawer, the top app bar, and the bottom
 * navigation bar that stay on screen while you switch between the Clock,
 * Timer, and Stopwatch tools. Each tool's own screen fills the content area.
 *
 * (Alarm is a fourth tool in the design but is deliberately not here yet — it
 * ships as the last feature milestone so the core keeps its minimal
 * permission set; see docs/DECISIONS.md D-020.)
 *
 * For now we switch tabs with simple state rather than a Navigation graph —
 * three top-level tabs don't need one. A NavHost comes in when Settings and
 * its sub-screens arrive (M6) and real back-stack navigation earns its keep.
 */

/** The bottom-navigation tools. `labelRes` is the tab label; `icon` its glyph. */
enum class HomeTab(val labelRes: Int, val icon: ImageVector) {
    Clock(R.string.nav_clock, Icons.Outlined.AccessTime),
    Timer(R.string.nav_timer, Icons.Outlined.Timer),
    Stopwatch(R.string.nav_stopwatch, Icons.Outlined.HourglassEmpty),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TalkingClockRoot() {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.Clock) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                selectedTab = selectedTab,
                onSelectTab = { tab ->
                    selectedTab = tab
                    scope.launch { drawerState.close() }
                },
            )
        },
    ) {
        Scaffold(
            topBar = {
                // No title text — the design's top bar is just the menu
                // button and (on the Clock tab) the nightstand toggle.
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = stringResource(R.string.clock_open_menu),
                            )
                        }
                    },
                    actions = {
                        // Nightstand mode lives on the Clock tab only. It's a
                        // dimmed presentation mode built in a later milestone;
                        // the button is a placeholder for now.
                        if (selectedTab == HomeTab.Clock) {
                            IconButton(onClick = { /* Nightstand mode — later milestone */ }) {
                                Icon(
                                    imageVector = Icons.Outlined.Bedtime,
                                    contentDescription = "Nightstand mode",
                                )
                            }
                        }
                    },
                )
            },
            bottomBar = {
                NavigationBar {
                    HomeTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == selectedTab,
                            onClick = { selectedTab = tab },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            },
        ) { innerPadding ->
            Box(Modifier.fillMaxSize().padding(innerPadding)) {
                when (selectedTab) {
                    HomeTab.Clock -> ClockRoute()
                    HomeTab.Timer -> TimerRoute()
                    HomeTab.Stopwatch -> StopwatchRoute()
                }
            }
        }
    }
}
