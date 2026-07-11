package io.music_assistant.client.ui.compose.common.items

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.music_assistant.client.settings.SwipeActionOption
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.swipe_action_nothing
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeActionDropdown(
    options: List<SwipeActionOption>,
    selected: SwipeActionOption,
    onSelect: (SwipeActionOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(4.dp)
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (selected != SwipeActionOption.NOTHING) {
                val itemAction = selected.toItemAction()
                if (itemAction != null) {
                    Icon(itemAction.icon(null), contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            Text(
                text = swipeActionLabel(selected),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            ExposedDropdownMenuDefaults.TrailingIcon(expanded)
        }
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                val itemAction = option.toItemAction()
                DropdownMenuItem(
                    text = {
                        Text(
                            swipeActionLabel(option),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = itemAction?.let { action ->
                        {
                            Icon(
                                action.icon(null),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun swipeActionLabel(option: SwipeActionOption): String = when (option) {
    SwipeActionOption.NOTHING -> stringResource(Res.string.swipe_action_nothing)
    else -> {
        val action = option.toItemAction()
        if (action != null) stringResource(action.title(null)) else option.name
    }
}
