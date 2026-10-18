package io.github.fianna76.speedread.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fianna76.speedread.R
import io.github.fianna76.speedread.SpeedReadApplication
import io.github.fianna76.speedread.data.news.ArticleError
import io.github.fianna76.speedread.data.news.SharedLinkReader
import io.github.fianna76.speedread.news.ArticleUrls
import io.github.fianna76.speedread.ui.news.openInBrowser

/** Progress and errors for a link shared to the app; opens the reader when the article is ready. */
@Composable
fun SharedLinkHost(onOpenBook: (Long) -> Unit) {
    val context = LocalContext.current
    val reader = (context.applicationContext as SpeedReadApplication).container.sharedLinkReader
    val state by reader.state.collectAsStateWithLifecycle()
    SharedLinkDialogs(
        state = state,
        onCancel = reader::cancel,
        onOpenOnline = { url ->
            reader.cancel()
            context.openInBrowser(url)
        },
        onReady = { bookId ->
            reader.onOpened()
            onOpenBook(bookId)
        },
    )
}

@Composable
fun SharedLinkDialogs(
    state: SharedLinkReader.State,
    onCancel: () -> Unit,
    onOpenOnline: (String) -> Unit,
    onReady: (Long) -> Unit,
) {
    when (state) {
        SharedLinkReader.State.Idle -> Unit
        is SharedLinkReader.State.Ready -> LaunchedEffect(state.bookId) { onReady(state.bookId) }
        // Plain text is saved in a moment: no progress dialog.
        is SharedLinkReader.State.Preparing -> if (state.url != null) AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(stringResource(R.string.shared_link_preparing)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Text(ArticleUrls.displayHost(state.url))
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) } },
        )
        is SharedLinkReader.State.Failed -> AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(stringResource(R.string.shared_link_failed_title)) },
            text = {
                Text(
                    stringResource(
                        when (state.error) {
                            ArticleError.NETWORK -> R.string.news_error_network
                            ArticleError.NO_TEXT -> R.string.shared_link_no_text
                            ArticleError.PAYWALLED -> R.string.news_error_paywalled
                            ArticleError.STORAGE -> R.string.news_error_storage
                        },
                    ),
                )
            },
            confirmButton = {
                // Plain text has no page to open.
                if (state.url.isNotEmpty()) {
                    TextButton(onClick = { onOpenOnline(state.url) }) { Text(stringResource(R.string.news_open_online)) }
                }
            },
            dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.action_close)) } },
        )
    }
}
