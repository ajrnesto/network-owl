package io.github.ajrnesto.networkowl.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

private val SegmentOuterCorner = 20.dp
private val SegmentInnerCorner = 4.dp

/**
 * One item of an expressive segmented list: items sit 2dp apart, the group rounds off at its ends,
 * and a pressed item morphs to fully rounded.
 */
@Composable
internal fun SegmentedItem(
    index: Int,
    count: Int,
    onClick: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val top by animateDpAsState(
        targetValue = if (index == 0 || pressed) SegmentOuterCorner else SegmentInnerCorner,
        animationSpec = InspectorMotion.fastSpatial(),
    )
    val bottom by animateDpAsState(
        targetValue = if (index == count - 1 || pressed) SegmentOuterCorner else SegmentInnerCorner,
        animationSpec = InspectorMotion.fastSpatial(),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = if (index == 0) 0.dp else 2.dp)
            .clip(RoundedCornerShape(top, top, bottom, bottom))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interactionSource, indication = ripple(), onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        content = content,
    )
}

/**
 * A single-choice connected button group: the selected option fills and morphs to a pill, the others
 * keep tight inner corners.
 */
@Composable
internal fun <T> ConnectedToggleGroup(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: (T) -> Boolean = { true },
) {
    val colors = Inspector.colors
    Row(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { index, option ->
            val isSelected = option == selected
            val isEnabled = enabled(option)
            val start by animateDpAsState(
                targetValue = if (isSelected || index == 0) SegmentOuterCorner else SegmentInnerCorner,
                animationSpec = InspectorMotion.fastSpatial(),
            )
            val end by animateDpAsState(
                targetValue = if (isSelected || index == options.lastIndex) SegmentOuterCorner else SegmentInnerCorner,
                animationSpec = InspectorMotion.fastSpatial(),
            )
            val container by animateColorAsState(
                targetValue = if (isSelected) colors.primary else MaterialTheme.colorScheme.surfaceContainer,
                animationSpec = InspectorMotion.defaultEffects(),
            )
            val content by animateColorAsState(
                targetValue = if (isSelected) colors.onPrimary else colors.onBackground,
                animationSpec = InspectorMotion.defaultEffects(),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(start, end, end, start))
                    .background(container)
                    .selectable(
                        selected = isSelected,
                        enabled = isEnabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(option) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isEnabled) content else content.copy(alpha = 0.38f),
                )
            }
        }
    }
}

/** Top bars sit flat on the background and lift onto a container once content scrolls under them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun inspectorTopBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.background,
    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
)

/** Swaps screens with a spring slide, forward when [depth] grows and back when it shrinks. */
@Composable
internal fun <T> DrillIn(target: T, depth: (T) -> Int, content: @Composable (T) -> Unit) {
    AnimatedContent(
        targetState = target,
        transitionSpec = {
            val direction = if (depth(targetState) >= depth(initialState)) 1 else -1
            val enter = slideInHorizontally(InspectorMotion.defaultSpatial()) { direction * it / 5 } +
                fadeIn(InspectorMotion.defaultEffects())
            val exit = slideOutHorizontally(InspectorMotion.defaultSpatial()) { -direction * it / 5 } +
                fadeOut(InspectorMotion.defaultEffects())
            enter togetherWith exit
        },
        label = "DrillIn",
    ) { content(it) }
}
