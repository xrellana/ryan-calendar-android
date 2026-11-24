package com.ryancalendar.android.ui.eval

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.abs

/** Order shown right after a drop, valid only while the upstream list is still [base]. */
private data class PendingOrder(val base: List<String>, val order: List<String>)

/**
 * A small, non-lazy column whose rows are reordered by long-press-and-drag.
 *
 * Meant for short lists such as the travel rows of one day. A long press lifts the row; moving
 * it then drags it while the others slide out of its way, and on release [onReorder] receives
 * the new key order. Releasing a lifted row without moving it calls [onLongPress] instead, so
 * the usual long-press action (multi-select) keeps working on the same rows.
 *
 * The new order is shown immediately and kept until the caller's [items] change (the server
 * answered) or the caller invokes the failure callback.
 *
 * [itemContent] gets a `gesture` modifier to put on the row *after* its clickable, so the row
 * keeps its tap; it is null while [enabled] is false, and the row should then fall back to its
 * normal long-press handling.
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    key: (T) -> String,
    enabled: Boolean,
    onReorder: (newKeys: List<String>, onFailed: () -> Unit) -> Unit,
    onLongPress: (key: String) -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = 0.dp,
    itemContent: @Composable (item: T, gesture: Modifier?, lifted: Boolean) -> Unit
) {
    val upstreamKeys = items.map(key)
    val byKey = items.associateBy(key)

    // All gesture-side state lives in holders that survive upstream changes, because the
    // pointerInput coroutines below are keyed only by the row key and are not restarted.
    var pending by remember { mutableStateOf<PendingOrder?>(null) }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<String, Int>() }

    val order = pending?.takeIf { it.base == upstreamKeys }?.order ?: upstreamKeys
    val currentOrder by rememberUpdatedState(order)
    val currentUpstream by rememberUpdatedState(upstreamKeys)
    val currentOnReorder by rememberUpdatedState(onReorder)
    val currentOnLongPress by rememberUpdatedState(onLongPress)

    val spacingPx = with(LocalDensity.current) { spacing.toPx() }
    val haptic = LocalHapticFeedback.current

    fun slotHeight(k: String): Float = (heights[k] ?: 0) + spacingPx

    /** Index the dragged row would land on: it passes a neighbour once it covers half of it. */
    fun targetIndex(ord: List<String>, dk: String, offset: Float): Int {
        val from = ord.indexOf(dk)
        if (from < 0) return -1
        var idx = from
        var acc = 0f
        if (offset > 0) {
            for (i in from + 1 until ord.size) {
                val h = slotHeight(ord[i])
                if (offset > acc + h / 2) { idx = i; acc += h } else break
            }
        } else if (offset < 0) {
            for (i in from - 1 downTo 0) {
                val h = slotHeight(ord[i])
                if (-offset > acc + h / 2) { idx = i; acc += h } else break
            }
        }
        return idx
    }

    val from = draggingKey?.let { order.indexOf(it) } ?: -1
    val target = draggingKey?.let { targetIndex(order, it, dragOffset) } ?: -1

    var lastTarget by remember { mutableIntStateOf(-1) }
    LaunchedEffect(draggingKey, target) {
        if (draggingKey == null) {
            lastTarget = -1
        } else {
            if (lastTarget != -1 && lastTarget != target) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            lastTarget = target
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        order.forEachIndexed { index, k ->
            val item = byKey[k] ?: return@forEachIndexed
            key(k) {
                val isLifted = k == draggingKey
                val draggedSlot = draggingKey?.let { slotHeight(it) } ?: 0f
                val shiftTarget = when {
                    from < 0 || isLifted -> 0f
                    index in (from + 1)..target -> -draggedSlot
                    index in target until from -> draggedSlot
                    else -> 0f
                }
                // Recreated whenever the order changes, so after a drop the neighbours sit in
                // their new slots at once instead of sliding back from their old displacement.
                val shift = remember(order) { Animatable(0f) }
                LaunchedEffect(shift, shiftTarget) { shift.animateTo(shiftTarget) }
                val scale by animateFloatAsState(if (isLifted) 1.02f else 1f, label = "liftScale")

                val gesture = if (!enabled) null else Modifier.pointerInput(k) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // Null when the finger lifts early or the list starts scrolling first.
                        val press = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture

                        val ord = currentOrder
                        val start = ord.indexOf(k)
                        if (start < 0) return@awaitEachGesture
                        val minOffset = -(0 until start).sumOf { slotHeight(ord[it]).toDouble() }.toFloat()
                        val maxOffset = (start + 1 until ord.size).sumOf { slotHeight(ord[it]).toDouble() }.toFloat()

                        draggingKey = k
                        dragOffset = 0f
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        var travelled = 0f
                        var released = false
                        try {
                            released = drag(press.id) { change ->
                                val dy = change.positionChange().y
                                travelled += abs(dy)
                                dragOffset = (dragOffset + dy).coerceIn(minOffset, maxOffset)
                                change.consume()
                            }
                            // Swallow the release so the row's click does not fire as well.
                            currentEvent.changes.forEach { it.consume() }
                        } finally {
                            val to = targetIndex(ord, k, dragOffset)
                            draggingKey = null
                            dragOffset = 0f
                            when {
                                !released -> Unit
                                to >= 0 && to != start -> {
                                    val newOrder = ord.toMutableList().apply { removeAt(start); add(to, k) }
                                    val entry = PendingOrder(currentUpstream, newOrder)
                                    pending = entry
                                    currentOnReorder(newOrder) { if (pending === entry) pending = null }
                                }
                                travelled < viewConfiguration.touchSlop -> currentOnLongPress(k)
                            }
                        }
                    }
                }

                Box(
                    Modifier
                        .zIndex(if (isLifted) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (isLifted) dragOffset else shift.value
                            scaleX = scale
                            scaleY = scale
                        }
                        .onSizeChanged { heights[k] = it.height }
                ) {
                    itemContent(item, gesture, isLifted)
                }
            }
        }
    }
}
