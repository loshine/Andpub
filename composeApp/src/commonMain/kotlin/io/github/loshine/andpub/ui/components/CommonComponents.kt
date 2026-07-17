package io.github.loshine.andpub.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.loshine.andpub.domain.model.FieldKind
import io.github.loshine.andpub.domain.model.FieldSchema
import io.github.loshine.andpub.presentation.UiMessage

/** FilterChip with all elevation zeroed out for stable rendering inside cards. */
@Composable
fun StableFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        elevation = FilterChipDefaults.filterChipElevation(
            elevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            draggedElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
    )
}

/** OutlinedTextField pre-configured from a [FieldSchema]. */
@Composable
fun SchemaTextField(
    field: FieldSchema,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(if (field.required) "${field.label} *" else field.label) },
        singleLine = field.kind != FieldKind.Multiline,
        minLines = if (field.kind == FieldKind.Multiline) 4 else 1,
        maxLines = if (field.kind == FieldKind.Multiline) 4 else 1,
        visualTransformation = if (field.kind == FieldKind.Password) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        modifier = modifier,
    )
}

/** Centered empty-state placeholder with an icon, title, helper text, and optional action. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn(animationSpec = tween(400)),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(32.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp),
                )
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (actionLabel != null && onAction != null) {
                    Button(onClick = onAction) {
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}

/**
 * Global snackbar host driven by [UiMessage]. Shows until duration elapses,
 * then reports [onDismiss] with the message id so the ViewModel can clear
 * only that message (avoids wiping a newer toast).
 */
@Composable
fun AndpubMessageEffect(
    message: UiMessage?,
    onDismiss: (messageId: Long) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
): SnackbarHostState {
    LaunchedEffect(message?.id) {
        val current = message ?: return@LaunchedEffect
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(
            message = current.text,
            duration = if (current.isError) SnackbarDuration.Long else SnackbarDuration.Short,
        )
        onDismiss(current.id)
    }
    return snackbarHostState
}

@Composable
fun AndpubSnackbarHost(
    hostState: SnackbarHostState,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier,
    ) { data ->
        Snackbar(
            snackbarData = data,
            containerColor = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.inverseSurface
            },
            contentColor = if (isError) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.inverseOnSurface
            },
            actionColor = if (isError) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.inversePrimary
            },
        )
    }
}
