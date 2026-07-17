package io.github.johnjeffords.talkingclock.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.johnjeffords.talkingclock.R

/**
 * Contents of the slide-out navigation drawer: a small header, the tool
 * destinations, and (later) Settings / About. Selecting a tool switches the
 * bottom-nav tab and closes the drawer.
 *
 * Settings and About are shown but disabled until their screens exist (M6);
 * wiring them before the destination exists would just navigate to nothing.
 */
@Composable
fun AppDrawer(
    selectedTab: HomeTab,
    onSelectTab: (HomeTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(modifier = modifier) {
        // Header: app name + the "free & open source" tagline that's central
        // to this project's identity.
        Column(Modifier.padding(24.dp)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Free & open source",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // One row per tool.
        HomeTab.entries.forEach { tab ->
            NavigationDrawerItem(
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.labelRes)) },
                selected = tab == selectedTab,
                onClick = { onSelectTab(tab) },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(Modifier.padding(horizontal = 24.dp))
        Spacer(Modifier.height(8.dp))

        // Footer: license line, reinforcing the project's values.
        Text(
            text = "GPL-3.0-or-later",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        )
    }
}
