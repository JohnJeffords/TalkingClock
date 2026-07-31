package io.github.johnjeffords.talkingclock.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.johnjeffords.talkingclock.R
import kotlinx.coroutines.launch

typealias StartBackgroundFeature = (() -> Unit) -> Unit

internal fun needsNotificationPermissionAsk(
    sdkInt: Int,
    alreadyAsked: Boolean,
    permissionGranted: Boolean,
): Boolean = sdkInt >= Build.VERSION_CODES.TIRAMISU && !alreadyAsked && !permissionGranted

/** One permission flow shared by clock, timer, and stopwatch starts. */
@Composable
fun BackgroundFeaturePermissionCoordinator(
    alreadyAsked: Boolean,
    onAsked: () -> Unit,
    content: @Composable (StartBackgroundFeature, SnackbarHostState) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val deniedMessage = stringResource(R.string.notif_permission_denied_banner)
    var askedThisSession by rememberSaveable { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun markAsked() {
        if (!askedThisSession) {
            askedThisSession = true
            onAsked()
        }
    }

    fun finish(granted: Boolean) {
        val action = pendingAction
        pendingAction = null
        action?.invoke()
        if (!granted) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = deniedMessage,
                    duration = SnackbarDuration.Long,
                )
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> finish(granted) }

    content(
        { action ->
            val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            if (needsNotificationPermissionAsk(
                    Build.VERSION.SDK_INT,
                    alreadyAsked || askedThisSession,
                    granted,
                )
            ) {
                pendingAction = action
            } else {
                action()
            }
        },
        snackbarHostState,
    )

    if (pendingAction != null) {
        NotificationExplainerSheet(
            onAllow = {
                markAsked()
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onDeny = {
                markAsked()
                finish(granted = false)
            },
        )
    }
}

/** The one-time explanation shown before Android's notification request. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationExplainerSheet(
    onAllow: () -> Unit,
    onDeny: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDeny) {
        Column(Modifier.padding(horizontal = 26.dp, vertical = 10.dp)) {
            Text(
                text = stringResource(R.string.notif_explainer_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.notif_explainer_body),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.notif_explainer_honest),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDeny, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.notif_explainer_deny))
                }
                Spacer(Modifier.width(12.dp))
                Button(onClick = onAllow, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.notif_explainer_allow))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
