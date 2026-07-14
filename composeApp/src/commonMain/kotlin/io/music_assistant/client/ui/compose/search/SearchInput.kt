package io.music_assistant.client.ui.compose.search

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchInput(
    modifier: Modifier = Modifier,
    onSearch: (String) -> Unit,
    focusManager: FocusManager = LocalFocusManager.current,
    placeHolder: String,
) {
    val textFieldState = rememberTextFieldState()

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (textFieldState.text.isEmpty()) {
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
            modifier = modifier.focusRequester(focusRequester),
            state = textFieldState,
            onSearch = {
                onSearch(it)
                focusManager.clearFocus()
            },
            expanded = false,
            onExpandedChange = {},
            placeholder = {
                Text(placeHolder)
            },
            trailingIcon = if (textFieldState.text.isNotEmpty()) {
                {
                    IconButton(
                        onClick = {
                            textFieldState.clearText()
                            onSearch("")
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
