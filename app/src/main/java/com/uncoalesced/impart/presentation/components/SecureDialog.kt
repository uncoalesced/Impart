// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.components

import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.uncoalesced.impart.core.util.WindowHardening

// A Compose dialog is its own window, and FLAG_SECURE on the activity does not reach it.
// These wrappers are the only way a dialog is opened in Impart; WindowHardeningSourceTest
// rejects a bare Dialog or AlertDialog anywhere else.
@Composable
fun SecureDialogWindow() {
    val view = LocalView.current
    SideEffect {
        (view.parent as? DialogWindowProvider)?.window?.let(WindowHardening::harden)
    }
}

@Composable
fun SecureDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        SecureDialogWindow()
        content()
    }
}

@Composable
fun SecureAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    containerColor: Color,
    titleContentColor: Color,
    textContentColor: Color,
    properties: DialogProperties = DialogProperties()
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            SecureDialogWindow()
            confirmButton()
        },
        modifier = modifier,
        dismissButton = dismissButton,
        title = title,
        text = text,
        containerColor = containerColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        properties = properties
    )
}
