package io.music_assistant.client.ui.compose.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.settings.SwipeActionOption
import io.music_assistant.client.settings.SwipeActionPrefs
import io.music_assistant.client.ui.compose.common.items.SwipeActionDropdown
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.common_cancel
import musicassistantclient.composeapp.generated.resources.default_click_dialog_save
import musicassistantclient.composeapp.generated.resources.settings_swipe_actions
import musicassistantclient.composeapp.generated.resources.settings_swipe_actions_dialog_title
import musicassistantclient.composeapp.generated.resources.settings_swipe_left
import musicassistantclient.composeapp.generated.resources.settings_swipe_right
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private val LABEL_WIDTH = 110.dp

private val swipeActionOptions = SwipeActionOption.entries

@Composable
fun SwipeActionsSection() {
    var showDialog by remember { mutableStateOf(false) }

    SectionCard {
        SectionTitle(stringResource(Res.string.settings_swipe_actions))
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { showDialog = true },
        ) { Text(stringResource(Res.string.settings_swipe_actions)) }
    }

    if (showDialog) {
        SwipeActionsDialog(onDismiss = { showDialog = false })
    }
}

@Composable
private fun SwipeActionsDialog(onDismiss: () -> Unit) {
    val viewModel = koinViewModel<SwipeActionsViewModel>()
    val stored by viewModel.prefs.collectAsStateWithLifecycle()
    var onSwipeLeft by remember(stored) { mutableStateOf(stored.onSwipeLeft) }
    var onSwipeRight by remember(stored) { mutableStateOf(stored.onSwipeRight) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_swipe_actions_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SwipeActionRow(
                    label = stringResource(Res.string.settings_swipe_left),
                    selected = onSwipeLeft,
                    onSelect = { onSwipeLeft = it },
                )
                SwipeActionRow(
                    label = stringResource(Res.string.settings_swipe_right),
                    selected = onSwipeRight,
                    onSelect = { onSwipeRight = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.save(SwipeActionPrefs(onSwipeLeft = onSwipeLeft, onSwipeRight = onSwipeRight))
                onDismiss()
            }) { Text(stringResource(Res.string.default_click_dialog_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
        },
    )
}

@Composable
private fun SwipeActionRow(
    label: String,
    selected: SwipeActionOption,
    onSelect: (SwipeActionOption) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            modifier = Modifier.width(LABEL_WIDTH),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        SwipeActionDropdown(
            options = swipeActionOptions,
            selected = selected,
            onSelect = onSelect,
            modifier = Modifier.weight(1f),
        )
    }
}
