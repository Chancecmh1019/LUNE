package com.lune.app.ui.pages.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lune.app.R

@Composable
internal fun ClearDataDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_clear_title)) },
        text = { Text(stringResource(R.string.settings_clear_body)) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onConfirm() }) {
                Text(stringResource(R.string.dialog_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}
