package io.music_assistant.client.ui.compose.search

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.common_clear
import musicassistantclient.composeapp.generated.resources.search_query_label
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchInput(
    modifier: Modifier = Modifier,
    query: String,
    onQueryChanged: (String) -> Unit = {},
    onSearch: () -> Unit = {},
    focusManager: FocusManager = LocalFocusManager.current,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (query.isEmpty()) {
            focusRequester.requestFocus()
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = SearchBarDefaults.inputFieldShape,
        color = SearchBarDefaults.colors().containerColor,
        contentColor = contentColorFor(SearchBarDefaults.colors().containerColor),
        tonalElevation = SearchBarDefaults.TonalElevation,
        shadowElevation = SearchBarDefaults.ShadowElevation,
    ) {
        SearchBarDefaults.InputField(
            modifier = Modifier.focusRequester(focusRequester),
            query = query,
            onQueryChange = onQueryChanged,
            onSearch = {
                onSearch()
                focusManager.clearFocus()
            },
            expanded = false,
            onExpandedChange = {},
            placeholder = {
                Text(stringResource(Res.string.search_query_label))
            },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(
                        onClick = {
                            onQueryChanged("")
                            onSearch()
                        },
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = stringResource(Res.string.common_clear),
                        )
                    }
                }
            } else {
                null
            },
        )
    }
}
