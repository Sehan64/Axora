package frb.axeron.manager.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Single source of truth for dialog and bottom-sheet geometry.
 *
 * These used to be hardcoded independently per container - 8/20/24/28dp corners, 20/24dp
 * padding, a 320dp fixed width on one dialog and full-width on another - so the same app
 * shipped half a dozen subtly different containers. Everything now derives from the theme's
 * `Shapes`, so switching the corner style in Appearance settings updates every dialog and
 * sheet at once instead of leaving stragglers behind.
 */
private val DialogBlockSpacing = 16.dp
private val DialogButtonSpacing = 8.dp
private val DialogContentPadding = PaddingValues(24.dp)
private val DialogElevation = 8.dp

/** Caps dialog width so it stays readable in landscape and on tablets. */
private val DialogMaxWidth = 560.dp

@Composable
fun MaterialDialog(
    onDismissRequest: () -> Unit,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    confirmButton: (@Composable () -> Unit)? = null,
    dismissButton: @Composable (() -> Unit)? = null,
    neutralButton: @Composable (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false)
) {
    Dialog(
        onDismissRequest = { onDismissRequest() },
        properties = properties
    ) {
        ElevatedCard(
            shape = MaterialTheme.shapes.extraLarge,
            elevation = CardDefaults.cardElevation(DialogElevation),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = DialogMaxWidth)
                .padding(32.dp)
        ) {
            DialogBody(
                title = title,
                text = text,
                neutralButton = neutralButton,
                dismissButton = dismissButton,
                confirmButton = confirmButton
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    confirmButton: (@Composable () -> Unit)? = null,
    dismissButton: @Composable (() -> Unit)? = null,
    neutralButton: @Composable (() -> Unit)? = null
) {
    ModalBottomSheet(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        onDismissRequest = { onDismissRequest() },
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge.copy(
            bottomStart = CornerSize(0.dp),
            bottomEnd = CornerSize(0.dp)
        ),
        tonalElevation = DialogElevation,
        dragHandle = dragHandle
    ) {
        DialogBody(
            title = title,
            text = text,
            neutralButton = neutralButton,
            dismissButton = dismissButton,
            confirmButton = confirmButton
        )
    }
}

/** Shared title/content/button layout, identical for dialogs and sheets. */
@Composable
private fun DialogBody(
    title: (@Composable () -> Unit)?,
    text: (@Composable () -> Unit)?,
    neutralButton: (@Composable () -> Unit)?,
    dismissButton: (@Composable () -> Unit)?,
    confirmButton: (@Composable () -> Unit)?,
) {
    Column(modifier = Modifier.padding(DialogContentPadding)) {
        title?.let {
            ProvideTextStyle(MaterialTheme.typography.titleLarge) {
                it()
            }
            Spacer(modifier = Modifier.height(DialogBlockSpacing))
        }

        text?.let {
            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                ProvideTextStyle(
                    value = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    it()
                }
            }
            Spacer(modifier = Modifier.height(DialogBlockSpacing))
        }

        DialogButtons(
            neutralButton = neutralButton,
            dismissButton = dismissButton,
            confirmButton = confirmButton
        )
    }
}

/**
 * Action row shared by every container.
 *
 * `spacedBy(8.dp)` is the Material 3 gap between adjacent buttons, which the action rows here
 * were missing - several dialogs rendered their dismiss and confirm buttons flush against each
 * other. Slots are only added when non-null, so a dialog with no dismiss action does not end up
 * with a stray leading gap. Callers must therefore pass `null` rather than a lambda that renders
 * nothing, or the spacing will be applied around an invisible button.
 */
@Composable
private fun DialogButtons(
    neutralButton: (@Composable () -> Unit)?,
    dismissButton: (@Composable () -> Unit)?,
    confirmButton: (@Composable () -> Unit)?,
) {
    if (neutralButton == null && dismissButton == null && confirmButton == null) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DialogButtonSpacing)
    ) {
        if (neutralButton != null) {
            neutralButton()
            Spacer(modifier = Modifier.weight(1f))
        }
        dismissButton?.invoke()
        confirmButton?.invoke()
    }
}
