package io.music_assistant.client.ui.compose.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.model.client.ItemKind
import io.music_assistant.client.settings.configurableContextMenuKinds
import io.music_assistant.client.settings.reconcileContextMenuActions
import io.music_assistant.client.ui.compose.common.ReorderableEnabledList
import io.music_assistant.client.ui.compose.common.items.labelRes
import io.music_assistant.client.ui.compose.common.items.title
import io.music_assistant.client.ui.compose.common.items.toDisplayItemAction
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.common_back
import musicassistantclient.composeapp.generated.resources.common_done
import musicassistantclient.composeapp.generated.resources.settings_context_menu_configure
import musicassistantclient.composeapp.generated.resources.settings_context_menus
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

sealed interface ContextMenuSettingsRoute {
    data object List : ContextMenuSettingsRoute
    data class Edit(val kind: ItemKind) : ContextMenuSettingsRoute
}

@Composable
fun ContextMenusSection(onOpen: () -> Unit) {
    SectionCard {
        SectionTitle(stringResource(Res.string.settings_context_menus))
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpen,
        ) {
            Text(stringResource(Res.string.settings_context_menu_configure))
        }
    }
}

@Composable
fun ContextMenuSettingsPage(
    route: ContextMenuSettingsRoute,
    onNavigate: (ContextMenuSettingsRoute?) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (route) {
        ContextMenuSettingsRoute.List -> ContextMenusListPage(
            modifier = modifier,
            onBack = { onNavigate(null) },
            onKindSelected = { onNavigate(ContextMenuSettingsRoute.Edit(it)) },
        )
        is ContextMenuSettingsRoute.Edit -> ContextMenuKindEditPage(
            modifier = modifier,
            kind = route.kind,
            onBack = { onNavigate(ContextMenuSettingsRoute.List) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContextMenusListPage(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onKindSelected: (ItemKind) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings_context_menus)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(configurableContextMenuKinds, key = { it.name }) { kind ->
                ContextMenuKindRow(
                    label = stringResource(kind.labelRes()),
                    onClick = { onKindSelected(kind) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContextMenuKindEditPage(
    modifier: Modifier = Modifier,
    kind: ItemKind,
    onBack: () -> Unit,
) {
    val viewModel = koinViewModel<ContextMenuActionsViewModel>()
    val stored by viewModel.actions.collectAsStateWithLifecycle()
    val initial = remember(kind, stored) { reconcileContextMenuActions(stored[kind], kind) }
    var working by remember(kind) { mutableStateOf(initial) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(kind.labelRes())) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.common_back))
                    }
                },
                actions = {
                    TextButton(onClick = {
                        viewModel.save(kind, working.filter { it.second }.map { it.first })
                        onBack()
                    }) {
                        Text(stringResource(Res.string.common_done))
                    }
                },
            )
        },
    ) { padding ->
        ReorderableEnabledList(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            fillMaxHeight = true,
            initialItems = initial,
            key = { it.name },
            label = { stringResource(it.toDisplayItemAction().title(null)) },
            onItemsChange = { working = it },
        )
    }
}

@Composable
private fun ContextMenuKindRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
