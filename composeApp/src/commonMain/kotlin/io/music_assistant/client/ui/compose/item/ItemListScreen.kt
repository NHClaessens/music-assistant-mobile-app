package io.music_assistant.client.ui.compose.item

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.settings.ViewMode
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import io.music_assistant.client.ui.compose.library.ItemList
import io.music_assistant.client.ui.compose.nav.TopBarLayout

@Composable
fun ItemListScreen(
    title: String,
    itemListViewModel: ItemListViewModel,
    actionsViewModel: ActionsViewModel,
    onNavigateClick: (AppMediaItem) -> Unit,
    contentPadding: PaddingValues,
) {
    val state by itemListViewModel.state.collectAsStateWithLifecycle()

    TopBarLayout(
        topBar = { TopAppBar(title = { Text(title) }) },
    ) {
        ItemList(
            data = state.items,
            onNavigateClick = onNavigateClick,
            onPlayClick = { _, _, _, _ ->  },
            playlistActions = actionsViewModel,
            libraryActions = actionsViewModel,
            progressActions = actionsViewModel,
            contentPadding = contentPadding,
            viewMode = ViewMode.GRID,
        )
    }
}
