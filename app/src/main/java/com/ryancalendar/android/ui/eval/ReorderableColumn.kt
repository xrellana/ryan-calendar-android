package com.ryancalendar.android.ui.eval

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
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

/** Order shown right after a drop, valid only while the upstream list is still [base]. */
private data class PendingOrder(val base: List<String>, val order: List<String>)

/**
 * A small, non-lazy column whose rows can be dragged into a new order by a handle.
 *
 * Meant for short lists such as the travel rows of one day: rows are measured as they are laid
 * out, the dragged row follows the finger while the others slide out of its way, and on release
 * [onReorder] receives the new key order. The new order is shown immediately and kept until the
 * caller's [items] change (the server answered) or the caller invokes the failure callback.
 *
 * [itemContent] gets a `handle` modifier to put on the drag handle. The handle consumes the
 * press, so the row's own click / long-press (multi-select) never fires from it.
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    key: (T) -> String,
    enabled: Boolean,
    onReorder: (newKeys: List<String>, onFailed: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = 0.dp,
    itemContent: @Composable (item: T, handle: Modifier, dragging: Boolean) -> Unit
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
                val isDragging = k == draggingKey
                val draggedSlot = draggingKey?.let { slotHeight(it) } ?: 0f
                val shiftTarget = when {
                    from < 0 || isDragging -> 0f
                    index in (from + 1)..target -> -draggedSlot
                    index in target until from -> draggedSlot
                    else -> 0f
                }
                // Recreated whenever the order changes, so after a drop the neighbours sit in
                // their new slots at once instead of sliding back from their old displacement.
                val shift = remember(order) { Animatable(0f) }
                LaunchedEffect(shift, shiftTarget) { shift.animateTo(shiftTarget) }

                val handle = if (!enabled) Modifier else Modifier.pointerInput(k) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val slop = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                            ?: return@awaitEachGesture

                        val ord = currentOrder
                        val start = ord.indexOf(k)
                        if (start < 0) return@awaitEachGesture
                        val minOffset = -(0 until start).sumOf { slotHeight(ord[it]).toDouble() }.toFloat()
                        val maxOffset = (start + 1 until ord.size).sumOf { slotHeight(ord[it]).toDouble() }.toFloat()

                        draggingKey = k
                        dragOffset = (slop.position.y - down.position.y).coerceIn(minOffset, maxOffset)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        var released = false
                        try {
                            released = verticalDrag(slop.id) { change ->
                                dragOffset = (dragOffset + change.positionChange().y).coerceIn(minOffset, maxOffset)
                                change.consume()
                            }
                        } finally {
                            val to = targetIndex(ord, k, dragOffset)
                            draggingKey = null
                            dragOffset = 0f
                            if (released && to >= 0 && to != start) {
                                val newOrder = ord.toMutableList().apply { removeAt(start); add(to, k) }
                                val entry = PendingOrder(currentUpstream, newOrder)
                                pending = entry
                                currentOnReorder(newOrder) { if (pending === entry) pending = null }
                            }
                        }
                    }
                }

                Box(
                    Modifier
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer { translationY = if (isDragging) dragOffset else shift.value }
                        .onSizeChanged { heights[k] = it.height }
                ) {
                    itemContent(item, handle, isDragging)
                }
            }
        }
    }
}
