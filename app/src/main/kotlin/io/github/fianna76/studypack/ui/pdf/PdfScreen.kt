package io.github.fianna76.studypack.ui.pdf

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.fianna76.studypack.R
import io.github.fianna76.studypack.ui.components.BackButton
import io.github.fianna76.studypack.ui.components.EmptyState
import io.github.fianna76.studypack.ui.module.openPdfExternally
import io.github.fianna76.studypack.ui.theme.tabular
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val MAX_ZOOM = 4f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfScreen(
    noteId: Long,
    onBack: () -> Unit,
    onSpeedRead: (page: Int) -> Unit,
    viewModel: PdfViewModel = viewModel(factory = PdfViewModel.factory(noteId)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var jumpDialog by rememberSaveable { mutableStateOf(false) }
    val note = state.note

    val openExternally = {
        val file = viewModel.file()
        if (file != null && note != null) openPdfExternally(context, file, note.displayTitle)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onBack) },
                title = { Text(note?.displayTitle.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    if (!state.failed && state.pageCount > 0) {
                        IconButton(onClick = { onSpeedRead(viewModel.visiblePage) }) {
                            Icon(painterResource(R.drawable.ic_bolt), contentDescription = stringResource(R.string.note_speed_read))
                        }
                    }
                    var menu by remember { mutableStateOf(false) }
                    IconButton(onClick = { menu = true }) {
                        Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (state.pageCount > 0) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.pdf_jump)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_toc), contentDescription = null) },
                                onClick = {
                                    menu = false
                                    jumpDialog = true
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.note_open_external)) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null) },
                            onClick = {
                                menu = false
                                openExternally()
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.failed -> EmptyState(
                    icon = R.drawable.ic_description,
                    title = note?.displayTitle.orEmpty(),
                    body = stringResource(R.string.pdf_failed),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    TextButton(onClick = openExternally) { Text(stringResource(R.string.note_open_external)) }
                }
                else -> PdfPages(
                    state = state,
                    jumpDialog = jumpDialog,
                    onJumpDialog = { jumpDialog = it },
                    loadPage = viewModel::page,
                    onPageVisible = viewModel::onPageVisible,
                )
            }
        }
    }
}

@Composable
private fun PdfPages(
    state: PdfUiState,
    jumpDialog: Boolean,
    onJumpDialog: (Boolean) -> Unit,
    loadPage: suspend (index: Int, widthPx: Int) -> Bitmap?,
    onPageVisible: (Int) -> Unit,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.initialPage)
    val scope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.distinctUntilChanged().collect(onPageVisible)
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        val widthPx = constraints.maxWidth
        val maxOffset = widthPx * (scale - 1f) / 2f
        // Re-render sharper once zoomed in (in steps, so pinching does not re-render every frame).
        val quality = when {
            scale >= 2.5f -> 3
            scale >= 1.5f -> 2
            else -> 1
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Two fingers: zoom and pan (taken before the list sees them). One finger while
                    // zoomed: the list scrolls vertically, and horizontal movement pans the page.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pressed = event.changes.count { it.pressed }
                            if (pressed >= 2) {
                                val zoom = event.calculateZoom()
                                val newScale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                                val centroid = event.calculateCentroid(useCurrent = true)
                                val pan = event.calculatePan()
                                val fromCentre = if (centroid != Offset.Unspecified) centroid.x - size.width / 2f else 0f
                                // Keep the point between the fingers still while zooming.
                                val factor = newScale / scale
                                offsetX = (offsetX * factor - fromCentre * (factor - 1f) + pan.x)
                                    .coerceIn(-size.width * (newScale - 1f) / 2f, size.width * (newScale - 1f) / 2f)
                                scale = newScale
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            } else if (scale > 1f && pressed == 1) {
                                val change = event.changes.first { it.pressed }
                                val dx = change.position.x - change.previousPosition.x
                                offsetX = (offsetX + dx).coerceIn(-size.width * (scale - 1f) / 2f, size.width * (scale - 1f) / 2f)
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offsetX = 0f
                        } else {
                            scale = 2f
                        }
                    })
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX.coerceIn(-maxOffset, maxOffset)
                    transformOrigin = TransformOrigin(0.5f, 0f)
                },
        ) {
            items(state.pageCount, key = { it }) { index ->
                PdfPage(
                    index = index,
                    aspectRatio = state.aspectRatios.getOrElse(index) { 1.414f },
                    renderWidth = widthPx * quality,
                    loadPage = loadPage,
                )
            }
        }

        val current by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex } }
        Surface(
            onClick = { onJumpDialog(true) },
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            Text(
                stringResource(R.string.pdf_page_indicator, current + 1, state.pageCount),
                style = MaterialTheme.typography.labelLarge.tabular,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }

    if (jumpDialog) {
        JumpToPageDialog(
            pageCount = state.pageCount,
            onJump = { page ->
                onJumpDialog(false)
                scope.launch { listState.scrollToItem(page) }
            },
            onDismiss = { onJumpDialog(false) },
        )
    }
}

@Composable
private fun PdfPage(index: Int, aspectRatio: Float, renderWidth: Int, loadPage: suspend (Int, Int) -> Bitmap?) {
    val description = stringResource(R.string.pdf_page_description, index + 1)
    // Keep showing the previous rendering while a sharper one is produced.
    val bitmap by produceState<Bitmap?>(initialValue = null, index, renderWidth) {
        loadPage(index, renderWidth.coerceAtMost(MAX_RENDER_WIDTH))?.let { value = it }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .aspectRatio(1f / aspectRatio)
            .background(Color.White)
            .semantics { contentDescription = description },
    ) {
        bitmap?.let {
            Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Caps a page bitmap at about 3000 × 4200 pixels (~50 MB) however far the user zooms. */
private const val MAX_RENDER_WIDTH = 3000

@Composable
private fun JumpToPageDialog(pageCount: Int, onJump: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val page = text.toIntOrNull()?.takeIf { it in 1..pageCount }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pdf_jump)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { value -> text = value.filter { it.isDigit() }.take(5) },
                label = { Text(stringResource(R.string.pdf_jump_label, pageCount)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = { TextButton(onClick = { page?.let { onJump(it - 1) } }, enabled = page != null) { Text(stringResource(R.string.go)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
