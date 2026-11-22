package com.flue.launcher.ui.store

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.flue.launcher.ui.theme.LauncherTheme
import com.flue.launcher.watchface.store.ui.R

/** Never inferred from possession of an archive or an installed market download. */
@Composable
internal fun StoreUploadRightsConfirmation(checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange, enabled = enabled)
        Text(stringResource(R.string.watch_store_upload_rights_confirmation), color = LauncherTheme.style.bodyColor)
    }
}
