package frb.axeron.manager.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * App snackbar, on the stock Material 3 "inverse" treatment.
 *
 * It was painted with `secondaryContainer` / `onSecondaryContainer`, which is the same filled
 * container used by cards elsewhere in the app, so transient messages looked like permanent
 * content instead of something floating above it. M3 specifies `inverseSurface` for snackbars
 * precisely so they read as an overlay: a dark slab that is clearly *above* the surface, with
 * `inversePrimary` reserved for the action so it stays the only accented element.
 */
@Composable
fun AxSnackBarHost(
    hostState: SnackbarHostState
) {

    SnackbarHost(hostState = hostState) { data ->
        return@SnackbarHost Snackbar(
            modifier = Modifier.padding(12.dp),
            shape = MaterialTheme.shapes.extraSmall,
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            actionContentColor = MaterialTheme.colorScheme.inversePrimary,
            action = {
                // The dismiss control lives in the action slot rather than in a dedicated
                // `withDismissAction` slot: material3 1.4.0 (this project's version) has no such
                // parameter, only `action`. M3 renders these adjacent at the trailing edge
                // anyway, so the result is the standard snackbar affordance. A null action label
                // still leaves a dismiss button, so every message can be cleared early instead
                // of being waited out.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    data.visuals.actionLabel?.let {
                        TextButton(onClick = { data.performAction() }) {
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    IconButton(onClick = { data.dismiss() }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.inverseOnSurface
                        )
                    }
                }
            },
            content = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.inverseOnSurface
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = data.visuals.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.inverseOnSurface
                    )
                }
            }
        )
    }
}
