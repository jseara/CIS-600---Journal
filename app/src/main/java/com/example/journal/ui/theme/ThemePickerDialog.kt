package com.example.journal.ui.theme

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.zIndex
import com.example.journal.R
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * How much the screen behind the dialog is darkened (0 = not at all, 1 = black). Android's
 * default is about 0.6; a lighter dim lets the user see the live preview of the app's colors.
 */
private const val DIALOG_DIM_AMOUNT = 0.3f

/** Each step away from the centered circle shrinks a circle by this fraction of its size... */
private const val SCALE_STEP_PER_PAGE = 0.15f

/** ...but never below this scale, so far-away circles stay visible. */
private const val MIN_CIRCLE_SCALE = 0.55f

/**
 * Lets the user pick a theme seed color from a swipeable row of overlapping circles.
 *
 * The first circle is "Default" (no seed); the other ten are [ThemeSeeds]. The circle in the
 * center is the current choice. While the user swipes, [onPreview] is called with the centered
 * seed so the whole app recolors live behind the dialog; nothing is saved until Apply. However
 * the dialog closes (Apply, Cancel, tapping outside, back), [onPreviewEnd] is called.
 *
 * @param selectedSeed The seed currently applied, or null for Default. The picker starts on it.
 * @param isDark Whether the app is in dark mode; used to draw the Default circle in the color
 *   the app would really use.
 * @param onPreview Called with the centered seed while open; null means "preview Default".
 * @param onPreviewEnd Called once when the dialog closes, to drop the preview.
 * @param onApply Called with the chosen seed (null for Default) when Apply is tapped. The dialog
 *   then calls [onDismiss].
 * @param onDismiss Called to close the dialog.
 */
@Composable
fun ThemePickerDialog(
    selectedSeed: Color?,
    isDark: Boolean,
    onPreview: (Color?) -> Unit,
    onPreviewEnd: () -> Unit,
    onApply: (Color?) -> Unit,
    onDismiss: () -> Unit,
) {
    val pageArgbs = remember { ThemePickerPages.map { it?.color?.toArgb() } }
    // rememberPagerState is saveable, so the swiped-to page survives rotation.
    val pagerState = rememberPagerState(
        initialPage = initialSeedPage(pageArgbs, selectedSeed?.toArgb()),
    ) { ThemePickerPages.size }
    val scope = rememberCoroutineScope()

    // The Default circle shows the primary color the app uses with no seed, in the current
    // light/dark mode. We read it from defaultColorScheme rather than MaterialTheme.colorScheme,
    // because the theme around us is the *previewed* one and would change as the user swipes.
    val defaultCircleColor = defaultColorScheme(isDark).primary

    // currentPage changes once a circle passes the halfway point, so the preview follows the swipe.
    LaunchedEffect(pagerState.currentPage) {
        onPreview(ThemePickerPages[pagerState.currentPage]?.color)
    }
    // onDispose runs when the dialog leaves the composition, whichever way it was closed.
    DisposableEffect(Unit) {
        onDispose { onPreviewEnd() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.theme_dialog_title)) },
        text = {
            // AlertDialog lives in its own window. Its view's parent gives us that window so we
            // can lighten the dim; SideEffect re-applies it after every successful composition.
            val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
            SideEffect { dialogWindow?.setDimAmount(DIALOG_DIM_AMOUNT) }

            val itemSize = dimensionResource(R.dimen.seed_circle_size)
            val overlap = dimensionResource(R.dimen.seed_circle_overlap)
            val selectedBorder = dimensionResource(R.dimen.seed_border_selected)
            val unselectedBorder = dimensionResource(R.dimen.seed_border_unselected)

            // BoxWithConstraints tells us the available width (maxWidth), needed to center circles.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                HorizontalPager(
                    state = pagerState,
                    pageSize = PageSize.Fixed(itemSize),
                    // Negative spacing makes neighbouring circles overlap.
                    pageSpacing = -overlap,
                    // Half the leftover width on each side lets the first and last circle reach the center.
                    contentPadding = PaddingValues(horizontal = (maxWidth - itemSize) / 2),
                    // selectableGroup makes TalkBack treat the circles as one group of N options.
                    modifier = Modifier.fillMaxWidth().height(itemSize).selectableGroup(),
                ) { page ->
                    val seed = ThemePickerPages[page]
                    // A null page is Default: its own label, drawn in the no-seed primary color.
                    val name = stringResource(seed?.nameRes ?: R.string.seed_default)
                    val circleColor = seed?.color ?: defaultCircleColor
                    val selected = page == pagerState.currentPage
                    // 0 at the center, 1 for each neighbour, and so on (fractional while scrolling).
                    val distance = abs(pagerState.getOffsetDistanceInPages(page))
                    val borderWidth = if (selected) selectedBorder else unselectedBorder
                    // onSurface rather than primary: once a seed is applied, primary matches the
                    // circle's own color and the border would disappear.
                    val borderColor =
                        if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                    Box(
                        Modifier
                            .fillMaxSize()
                            // Circles closer to the center draw on top of the ones behind them.
                            .zIndex(-distance)
                            // graphicsLayer scales at draw time, so scrolling doesn't re-run layout.
                            .graphicsLayer {
                                val scale = (1f - SCALE_STEP_PER_PAGE * distance).coerceAtLeast(MIN_CIRCLE_SCALE)
                                scaleX = scale
                                scaleY = scale
                            }
                            .drawBehind {
                                val radius = size.minDimension / 2
                                drawCircle(circleColor, radius)
                                drawCircle(
                                    color = borderColor,
                                    radius = radius - borderWidth.toPx() / 2,
                                    style = Stroke(borderWidth.toPx()),
                                )
                            }
                            .clip(CircleShape)
                            // Tapping a circle scrolls it to the center. Role.RadioButton plus the
                            // color name tells TalkBack this is "Teal, radio button, selected".
                            .selectable(
                                selected = selected,
                                onClick = { scope.launch { pagerState.animateScrollToPage(page) } },
                                role = Role.RadioButton,
                            )
                            .semantics { contentDescription = name },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(ThemePickerPages[pagerState.currentPage]?.color)
                onDismiss()
            }) { Text(stringResource(R.string.action_apply)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
