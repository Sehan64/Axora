package frb.axeron.manager.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import frb.axeron.manager.R

/**
 * Update prompt, now built on the shared [MaterialDialog] scaffold.
 *
 * It used to hand-roll a `BasicAlertDialog` + `Surface` with its own 20dp corner radius, 320dp
 * width, 24dp padding, `headlineSmall` title and - most visibly - two `TextButton`s laid out with
 * no gap between them, so its actions were flush against each other unlike every other dialog in
 * the app. Delegating to the shared container fixes all of that at once and makes the update
 * prompt honour the Appearance corner-style setting.
 */
@Composable
fun UpdateDialog(
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) {
    MaterialDialog(
        onDismissRequest = { },
        title = {
            Text(
                text = stringResource(R.string.update_available_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(text = stringResource(R.string.update_available_message))
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.update_close))
            }
        },
        confirmButton = {
            TextButton(onClick = onUpdate) {
                Text(text = stringResource(R.string.update_action))
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    )
}
