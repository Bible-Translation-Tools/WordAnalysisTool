package org.bibletranslationtools.wat.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.StringResource
import wordanalysistool.shared.generated.resources.Res
import wordanalysistool.shared.generated.resources.error_toast
import wordanalysistool.shared.generated.resources.info_toast
import wordanalysistool.shared.generated.resources.success_toast

sealed class ToastType(
    val title: StringResource,
    val icon: ImageVector,
    val mainColor: @Composable () -> Color,
    val backgroundColor: @Composable () -> Color
) {
    object Info : ToastType(
        title = Res.string.info_toast,
        icon = Icons.Default.Info,
        mainColor = { MaterialTheme.colorScheme.primary },
        backgroundColor = { MaterialTheme.colorScheme.primaryContainer }
    )
    object Success : ToastType(
        title = Res.string.success_toast,
        icon = Icons.Default.CheckCircle,
        mainColor = { MaterialTheme.colorScheme.tertiary },
        backgroundColor = { MaterialTheme.colorScheme.tertiaryContainer }
    )
    object Error : ToastType(
        title = Res.string.error_toast,
        icon = Icons.Default.Cancel,
        mainColor = { MaterialTheme.colorScheme.error },
        backgroundColor = { MaterialTheme.colorScheme.errorContainer }
    )
}

data class ToastInfo(
    val type: ToastType,
    val message: String,
    val onClose: () -> Unit
)