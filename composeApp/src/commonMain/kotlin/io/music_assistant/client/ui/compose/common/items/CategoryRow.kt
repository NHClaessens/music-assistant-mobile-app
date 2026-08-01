package io.music_assistant.client.ui.compose.common.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.Podcast
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.items.RadioStation
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.DisplayString
import io.music_assistant.client.ui.compose.item.ItemList
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_view_all
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

data class ItemCategory<T>(
    val id: String,
    val title: DisplayString,
    val items: List<AppMediaItem>,
    val list: ItemList? = null,
    val filter: Filter<T>? = null,
    val lazyListKey: String = id,
    val tag: String? = null,
) {
    data class Filter<T>(
        val label: DisplayString,
        val options: List<T>,
        val labelTransform: (T) -> DisplayString,
        val contentDescription: StringResource,
    )
}

@Composable
fun <T, U> CategoryRow(
    data: DataState<T>,
    itemCategoryProvider: (T) -> ItemCategory<U>,
    onNavigateClick: (AppMediaItem) -> Unit,
    onNavigateToList: (String, ItemList) -> Unit = { _, _ -> },
    onOptionSelected: (U) -> Unit = {},
    onPlayClick: PlayHandler<AppMediaItem>,
    playlistActions: PlaylistActions,
    libraryActions: LibraryActions,
    progressActions: ProgressActions? = null,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit),
) {
    if (data is DataState.Data) {
        CategoryRow(
            itemCategory = itemCategoryProvider(data.data),
            onNavigateClick = onNavigateClick,
            onNavigateToList = onNavigateToList,
            onOptionSelected = onOptionSelected,
            onPlayClick = onPlayClick,
            playlistActions = playlistActions,
            libraryActions = libraryActions,
            progressActions = progressActions,
            providerIconFetcher = providerIconFetcher,
        )
    }
}

@Composable
fun <T> CategoryRow(
    itemCategory: ItemCategory<T>,
    onNavigateClick: (AppMediaItem) -> Unit,
    onNavigateToList: (String, ItemList) -> Unit = { _, _ -> },
    onOptionSelected: (T) -> Unit = {},
    onPlayClick: PlayHandler<AppMediaItem>,
    playlistActions: PlaylistActions,
    libraryActions: LibraryActions,
    progressActions: ProgressActions? = null,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit),
) {
    val title = itemCategory.title.string()
    CategoryRow(
        title = title,
        actions = {
            if (itemCategory.filter != null) {
                FilterSelector(
                    label = itemCategory.filter.label.string(),
                    rowTitle = title,
                    onOptionSelected = onOptionSelected,
                    options = itemCategory.filter.options,
                    optionLabels = { itemCategory.filter.labelTransform(it).string() },
                    contentDescriptionResource = itemCategory.filter.contentDescription,
                )
            }

            if (itemCategory.list != null) {
                ViewAllButton(
                    rowTitle = title,
                    onNavigateToList = onNavigateToList,
                    itemList = itemCategory.list,
                )
            }
        },
        onNavigateClick = onNavigateClick,
        onPlayClick = onPlayClick,
        mediaItems = itemCategory.items,
        playlistActions = playlistActions,
        libraryActions = libraryActions,
        progressActions = progressActions,
        providerIconFetcher = providerIconFetcher,
        rowTag = itemCategory.tag,
    )
}

@Composable
fun CategoryRow(
    title: String,
    actions: @Composable () -> Unit = {},
    onNavigateClick: (AppMediaItem) -> Unit,
    onPlayClick: PlayHandler<AppMediaItem>,
    mediaItems: List<AppMediaItem>,
    playlistActions: PlaylistActions,
    libraryActions: LibraryActions,
    progressActions: ProgressActions? = null,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit),
    rowTag: String? = null,
) {
    val rowListState = rememberLazyListState()

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row {
                actions()
            }
        }

        val modifier = if (rowTag != null) {
            Modifier.testTag(rowTag)
        } else {
            Modifier
        }

        // Recommendation rows are server-curated and can repeat canonical item
        // Key by occurrence to avoid Compose's duplicate-key crash
        val itemKeys = remember(mediaItems) { mediaItems.lazyListOccurrenceKeys() }

        LazyRow(
            modifier = modifier,
            state = rowListState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(
                items = mediaItems,
                key = { index, _ -> itemKeys[index] },
                contentType = { _, item ->
                    when (item) {
                        is Track -> "Track"
                        is Artist -> "Artist"
                        is Album -> "Album"
                        is Playlist -> "Playlist"
                        is Audiobook -> "Audiobook"
                        is Podcast -> "Podcast"
                        is PodcastEpisode -> "Episode"
                        is RadioStation -> "RadioStation"
                        is Genre -> "Genre"
                        else -> "Unknown"
                    }
                },
            ) { _, item ->
                when (item) {
                    is Artist -> ArtistWithMenu(
                        item = item,
                        onNavigateClick = onNavigateClick,
                        onPlayOption = onPlayClick,
                        libraryActions = libraryActions,
                        providerIconFetcher = providerIconFetcher,
                    )

                    is Album -> AlbumWithMenu(
                        item = item,
                        onNavigateClick = onNavigateClick,
                        onPlayOption = onPlayClick,
                        playlistActions = playlistActions,
                        libraryActions = libraryActions,
                        providerIconFetcher = providerIconFetcher,
                    )

                    is Playlist -> PlaylistWithMenu(
                        item = item,
                        onNavigateClick = onNavigateClick,
                        onPlayOption = onPlayClick,
                        libraryActions = libraryActions,
                        providerIconFetcher = providerIconFetcher,
                    )

                    is Podcast -> PodcastWithMenu(
                        item = item,
                        onNavigateClick = onNavigateClick,
                        onPlayOption = onPlayClick,
                        libraryActions = libraryActions,
                        providerIconFetcher = providerIconFetcher,
                    )

                    is Track -> TrackWithMenu(
                        item = item,
                        onPlayOption = onPlayClick,
                        playlistActions = playlistActions,
                        libraryActions = libraryActions,
                        providerIconFetcher = providerIconFetcher,
                    )

                    is PodcastEpisode -> PodcastEpisodeWithMenu(
                        item = item,
                        onPlayOption = onPlayClick,
                        playlistActions = playlistActions,
                        libraryActions = libraryActions,
                        progressActions = progressActions,
                        providerIconFetcher = providerIconFetcher,
                    )

                    is Audiobook -> AudiobookWithMenu(
                        item = item,
                        onNavigateClick = onNavigateClick,
                        onPlayOption = onPlayClick,
                        playlistActions = playlistActions,
                        libraryActions = libraryActions,
                        progressActions = progressActions,
                        providerIconFetcher = providerIconFetcher,
                    )

                    is RadioStation -> RadioWithMenu(
                        item = item,
                        onPlayOption = onPlayClick,
                        playlistActions = playlistActions,
                        libraryActions = libraryActions,
                        providerIconFetcher = providerIconFetcher,
                    )

                    is Genre -> GenreWithMenu(
                        item = item,
                        onNavigateClick = onNavigateClick,
                        onPlayOption = onPlayClick,
                        libraryActions = libraryActions,
                        providerIconFetcher = providerIconFetcher,
                    )

                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun ViewAllButton(
    rowTitle: String,
    onNavigateToList: (String, ItemList) -> Unit,
    itemList: ItemList,
) {
    val viewAllContentDescription = stringResource(Res.string.cd_view_all, rowTitle)
    TextButton(
        modifier = Modifier.semantics {
            contentDescription = viewAllContentDescription
        },
        onClick = {
            onNavigateToList(rowTitle, itemList)
        },
    ) {
        Text("View all")
    }
}

@Composable
private fun <T> FilterSelector(
    label: String,
    rowTitle: String,
    onOptionSelected: (T) -> Unit,
    options: List<T>,
    optionLabels: @Composable (T) -> String,
    contentDescriptionResource: StringResource,
) {
    Box {
        var expanded by remember { mutableStateOf(false) }

        val chipContentDescription = stringResource(
            contentDescriptionResource,
            rowTitle,
            label,
        )

        FilterChip(
            modifier = Modifier
                .semantics {
                    contentDescription = chipContentDescription
                },
            selected = true,
            onClick = { expanded = true },
            label = {
                Text(label)
            },
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach {
                DropdownMenuItem(
                    text = { Text(optionLabels(it)) },
                    onClick = {
                        expanded = false
                        onOptionSelected(it)
                    },
                )
            }
        }
    }
}
