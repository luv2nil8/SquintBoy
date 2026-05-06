package com.anaglych.squintboyadvance.presentation.screens.emulator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speed
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.anaglych.squintboyadvance.presentation.theme.SurfaceMedium
import com.anaglych.squintboyadvance.shared.model.BindableAction
import com.anaglych.squintboyadvance.shared.model.GamepadMapping
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private val ACTIONS = BindableAction.values().toList()
private val FLOW_GREEN   = Color(0xFF9BBC0F)
private val FLOW_CRIMSON = Color(0xFFEC1358)
private val FLOW_YELLOW  = Color(0xFFFFD700)

private fun actionIcon(action: BindableAction): ImageVector? = when (action) {
    BindableAction.DPAD_UP    -> Icons.Default.ArrowUpward
    BindableAction.DPAD_DOWN  -> Icons.Default.ArrowDownward
    BindableAction.DPAD_LEFT  -> Icons.AutoMirrored.Filled.ArrowBack
    BindableAction.DPAD_RIGHT -> Icons.AutoMirrored.Filled.ArrowForward
    BindableAction.FF_TOGGLE  -> Icons.Default.FastForward
    BindableAction.FF_SPEED   -> Icons.Default.Speed
    BindableAction.SAVE_STATE -> Icons.Default.Save
    BindableAction.LOAD_STATE -> Icons.AutoMirrored.Filled.Undo
    else                      -> null
}

private const val SPINNER_INERTIA       = 0.88f
private const val SPINNER_SNAP_STRENGTH = 0.22f
private const val SPINNER_VIRTUAL_RADIUS = 4.0f
private const val SPINNER_FADE_OPACITY  = 0.30f
private const val SPINNER_VISIBLE_ENTRIES = 3

@Composable
internal fun BindingFlowContent(
    initialMapping: GamepadMapping,
    heldKeys: Set<Int>,
    onConfirm: (GamepadMapping) -> Unit,
    onCancel: () -> Unit,
) {
    var draft by remember { mutableStateOf(initialMapping) }

    var rawOffset  by remember { mutableFloatStateOf(0f) }
    var velocity   by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var isSnapped  by remember { mutableStateOf(true) }

    val selectedIndex  = rawOffset.roundToInt().mod(ACTIONS.size)
    val selectedAction = ACTIONS[selectedIndex]

    var isListening  by remember { mutableStateOf(false) }
    var pendingCombo by remember { mutableStateOf<Set<Int>?>(null) }

    // Alternating duplicate warning text (every 2 s)
    var altToggle by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) { delay(2000L); altToggle = !altToggle }
    }

    // Physics loop (~60 fps via delay)
    LaunchedEffect(Unit) {
        val frameMs = 16L
        val dt = frameMs / 1000f
        while (true) {
            delay(frameMs)
            if (isDragging) continue

            velocity *= SPINNER_INERTIA.pow(dt * 60f)
            val target = rawOffset.roundToInt().toFloat()
            velocity += SPINNER_SNAP_STRENGTH * (target - rawOffset) * 60f * dt
            rawOffset += velocity * dt

            val settled = kotlin.math.abs(rawOffset - target) < 0.005f &&
                          kotlin.math.abs(velocity) < 0.02f
            if (settled) {
                rawOffset = target; velocity = 0f
                if (!isSnapped) isSnapped = true
            } else {
                isSnapped = false
            }
        }
    }

    // Cancel listening when the user navigates to a different action
    LaunchedEffect(selectedIndex) {
        if (isListening) {
            pendingCombo = null
            isListening = false
        }
    }

    // Auto-listen: when spinner settles on an unbound action and nothing else is listening
    LaunchedEffect(selectedIndex, isSnapped) {
        if (isSnapped && !isListening && draft.forAction(selectedAction).isEmpty()) {
            pendingCombo = null
            isListening = true
        }
    }

    // Commit combo when all held keys are released.
    // pendingCombo only grows (captures the peak held set); once any key releases we
    // don't shrink it.  A 50 ms settle window guards against staggered key-up events
    // and axis spring-back noise committing a smaller-than-intended combo.
    LaunchedEffect(heldKeys) {
        if (!isListening) return@LaunchedEffect
        if (heldKeys.isNotEmpty()) {
            if (pendingCombo == null || heldKeys.size >= pendingCombo!!.size) {
                pendingCombo = heldKeys
            }
        } else if (pendingCombo != null) {
            delay(50L)
            val combo = pendingCombo ?: return@LaunchedEffect
            pendingCombo = null
            val wasEmpty = draft.forAction(selectedAction).isEmpty()
            draft = draft.withCombo(selectedAction, combo)
            isListening = false
            if (wasEmpty) {
                rawOffset += 1f
                velocity = 0f; isSnapped = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
            RadialSpinner(
                actions         = ACTIONS,
                rawOffset       = rawOffset,
                selectedIndex   = selectedIndex,
                onDragStart     = { isDragging = true; isSnapped = false },
                onDragDelta     = { delta -> rawOffset += delta },
                onDragEnd       = { v -> velocity = v; isDragging = false },
                onTap           = { delta ->
                    rawOffset = rawOffset.roundToInt().toFloat() + delta
                    velocity = 0f; isSnapped = true
                    val idx = rawOffset.roundToInt().mod(ACTIONS.size)
                    if (!isListening && draft.forAction(ACTIONS[idx]).isEmpty()) isListening = true
                },
            )

            BindingLabelColumn(
                combos      = draft.forAction(selectedAction),
                isListening = isListening,
                livePending = if (heldKeys.isNotEmpty()) heldKeys else (pendingCombo ?: emptySet()),
                altToggle   = altToggle,
                dupFor      = { combo ->
                    ACTIONS.firstOrNull { a -> a != selectedAction && draft.forAction(a).any { it == combo } }
                },
                onRemove         = { idx -> draft = draft.withoutComboAt(selectedAction, idx) },
                onStartListening = { isListening = true; pendingCombo = null },
                onAddCombo       = {
                    val combo = if (heldKeys.isNotEmpty()) heldKeys else pendingCombo
                    if (!combo.isNullOrEmpty()) {
                        val wasEmpty = draft.forAction(selectedAction).isEmpty()
                        draft = draft.withCombo(selectedAction, combo)
                        pendingCombo = null
                        isListening = false
                        if (wasEmpty) { rawOffset += 1f; velocity = 0f; isSnapped = false }
                    }
                },
                onRedoListening  = { pendingCombo = null; isListening = false },
            )

            Spacer(Modifier.height(2.dp))

            var iconsOnly by remember { mutableStateOf(false) }
            if (iconsOnly) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f))
                            .clickable { onCancel() },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Close, null, Modifier.size(18.dp)) }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(FLOW_GREEN.copy(alpha = 0.80f))
                            .clickable { onConfirm(draft) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Chip(
                        modifier = Modifier.weight(1f).height(36.dp),
                        onClick  = onCancel,
                        colors   = ChipDefaults.chipColors(backgroundColor = Color.White.copy(alpha = 0.10f)),
                        label    = {
                            Text(
                                "Cancel",
                                style = MaterialTheme.typography.caption2,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                onTextLayout = { if (it.hasVisualOverflow) iconsOnly = true },
                            )
                        },
                        icon = { Icon(Icons.Default.Close, null, Modifier.size(14.dp)) },
                    )
                    Chip(
                        modifier = Modifier.weight(1f).height(36.dp),
                        onClick  = { onConfirm(draft) },
                        colors   = ChipDefaults.chipColors(backgroundColor = FLOW_GREEN.copy(alpha = 0.80f)),
                        label    = {
                            Text(
                                "Confirm",
                                style = MaterialTheme.typography.caption2,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                onTextLayout = { if (it.hasVisualOverflow) iconsOnly = true },
                            )
                        },
                        icon = { Icon(Icons.Default.Check, null, Modifier.size(14.dp)) },
                    )
                }
            }
        }
}

@Composable
private fun RadialSpinner(
    actions: List<BindableAction>,
    rawOffset: Float,
    selectedIndex: Int,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: (velocity: Float) -> Unit,
    onTap: (delta: Int) -> Unit,
) {
    val density = LocalDensity.current
    val itemWidthDp = 36.dp
    val itemWidthPx = with(density) { itemWidthDp.toPx() }
    val latestOffset by rememberUpdatedState(rawOffset)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val drag = awaitTouchSlopOrCancellation(down.id) { c, delta ->
                        if (kotlin.math.abs(delta.x) > kotlin.math.abs(delta.y)) c.consume()
                    }
                    if (drag == null) {
                        val halfWPx = size.width / 2f
                        val delta = when {
                            down.position.x < halfWPx * 0.6f -> -1
                            down.position.x > halfWPx * 1.4f ->  1
                            else                              ->  0
                        }
                        onTap(delta)
                    } else {
                        onDragStart()
                        var lastX = drag.position.x
                        var lastTime = System.nanoTime()
                        var dragVel = 0f
                        onDragDelta(-(drag.position.x - down.position.x) / itemWidthPx)
                        horizontalDrag(drag.id) { change ->
                            change.consume()
                            val now = System.nanoTime()
                            val dt = (now - lastTime) / 1_000_000_000f
                            val dx = change.position.x - lastX
                            if (dt > 0f) dragVel = -dx / itemWidthPx / dt
                            onDragDelta(-dx / itemWidthPx)
                            lastX = change.position.x
                            lastTime = now
                        }
                        onDragEnd(dragVel)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val halfW = maxWidth.value / 2f  // dp

        val n = actions.size.toFloat()
        // Shortest circular distance from rawOffset to item i, in (-n/2, n/2]
        fun wrapDist(i: Int): Float {
            val raw = i.toFloat() - rawOffset
            return ((raw + n / 2f).mod(n)) - n / 2f
        }

        actions.indices
            .sortedByDescending { kotlin.math.abs(wrapDist(it)) }
            .forEach { i ->
                val d = wrapDist(i)
                if (kotlin.math.abs(d) > SPINNER_VISIBLE_ENTRIES + 0.5f) return@forEach
                val angle = (d / SPINNER_VIRTUAL_RADIUS) * (Math.PI.toFloat() / 2f)
                val sc = cos(angle).coerceIn(0f, 1f)
                val xOffsetDp = sin(angle) * halfW * 0.88f
                val opacity = (SPINNER_FADE_OPACITY + (1f - SPINNER_FADE_OPACITY) * sc).coerceIn(0f, 1f)
                val isSelected = i == selectedIndex

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = with(density) { xOffsetDp.dp.toPx() }
                            scaleX = sc; scaleY = sc; alpha = opacity
                        }
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (isSelected) FLOW_GREEN else SurfaceMedium,
                        )
                        .padding(horizontal = 5.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val icon = actionIcon(actions[i])
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = actions[i].displayLabel,
                            tint = Color.White,
                            modifier = Modifier.size(27.dp),
                        )
                    } else {
                        Text(
                            text  = actions[i].displayLabel,
                            style = MaterialTheme.typography.caption2,
                            fontSize = 27.sp,
                            color = Color.White,
                        )
                    }
                }
            }
    }
}

@Composable
private fun BindingLabelColumn(
    combos: List<Set<Int>>,
    isListening: Boolean,
    livePending: Set<Int>,
    altToggle: Boolean,
    dupFor: (Set<Int>) -> BindableAction?,
    onRemove: (Int) -> Unit,
    onStartListening: () -> Unit,
    onAddCombo: () -> Unit,
    onRedoListening: () -> Unit,
) {
    var dotCount by remember { mutableIntStateOf(1) }
    LaunchedEffect(isListening) {
        if (isListening) {
            dotCount = 1
            while (true) { delay(500L); dotCount = dotCount % 3 + 1 }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        combos.forEachIndexed { idx, combo ->
            val isLast = idx == combos.lastIndex && !isListening
            val dup = dupFor(combo)
            BoundComboChipRow(
                combo        = combo,
                dup          = dup,
                altToggle    = altToggle,
                isLast       = isLast,
                onRebind     = { onRemove(idx); onStartListening() },
                onAddAnother = onStartListening,
            )
        }

        if (isListening || combos.isEmpty()) {
            ListeningChipRow(
                isListening       = isListening,
                livePending       = livePending,
                dotCount          = dotCount,
                onStartListening  = onStartListening,
                onAddCombo        = onAddCombo,
                onCancelListening = onRedoListening,
            )
        }
    }
}

@Composable
private fun BoundComboChipRow(
    combo: Set<Int>,
    dup: BindableAction?,
    altToggle: Boolean,
    isLast: Boolean,
    onRebind: () -> Unit,
    onAddAnother: () -> Unit,
) {
    val btnSize = 26.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Combo chip
        Box(
            modifier = Modifier
                .weight(1f)
                .height(26.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Color.White.copy(alpha = if (dup != null) 0.08f else 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (dup != null) Text("⚠", fontSize = 10.sp, color = FLOW_YELLOW)
                Text(
                    text = if (dup != null && altToggle) "→ ${dup.displayLabel}"
                           else GamepadMapping.comboLabel(combo),
                    style = MaterialTheme.typography.caption2,
                    fontSize = 10.sp,
                    color = if (dup != null) FLOW_YELLOW else Color.White,
                    maxLines = 1,
                )
            }
        }

        // Rebind — removes this combo and starts listening for a replacement
        Box(
            modifier = Modifier
                .size(btnSize)
                .clip(CircleShape)
                .background(FLOW_CRIMSON.copy(alpha = 0.75f))
                .clickable { onRebind() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Rebind",
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }

        // + only on last combo when not listening
        if (isLast) {
            Box(
                modifier = Modifier
                    .size(btnSize)
                    .clip(CircleShape)
                    .background(FLOW_GREEN.copy(alpha = 0.80f))
                    .clickable { onAddAnother() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add another",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun ListeningChipRow(
    isListening: Boolean,
    livePending: Set<Int>,
    dotCount: Int,
    onStartListening: () -> Unit,
    onAddCombo: () -> Unit,
    onCancelListening: () -> Unit,
) {
    val hasCapture = livePending.isNotEmpty()
    val chipText = when {
        hasCapture  -> GamepadMapping.comboLabel(livePending)
        isListening -> "Listening" + ".".repeat(dotCount)
        else        -> "Tap to bind"
    }
    val chipColor = when {
        hasCapture  -> FLOW_GREEN
        isListening -> Color.White.copy(alpha = 0.6f)
        else        -> Color.White.copy(alpha = 0.35f)
    }

    val btnSize = 26.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Input chip — fills all space not taken by buttons
        Box(
            modifier = Modifier
                .weight(1f)
                .height(26.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Color.White.copy(alpha = if (hasCapture) 0.15f else 0.10f))
                .then(if (!isListening) Modifier.clickable { onStartListening() } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = chipText,
                style = MaterialTheme.typography.caption2,
                fontSize = 10.sp,
                color = chipColor,
                maxLines = 1,
            )
        }

        // Cancel — dims when not listening; becomes X to stop listening when active
        Box(
            modifier = Modifier
                .size(btnSize)
                .clip(CircleShape)
                .background(FLOW_CRIMSON.copy(alpha = if (isListening) 0.75f else 0.30f))
                .then(if (isListening) Modifier.clickable { onCancelListening() } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isListening) Icons.Default.Close else Icons.Default.Refresh,
                contentDescription = "Cancel",
                tint = Color.White.copy(alpha = if (isListening) 1f else 0.4f),
                modifier = Modifier.size(14.dp),
            )
        }

        // Add (green) — commits the captured combo
        Box(
            modifier = Modifier
                .size(btnSize)
                .clip(CircleShape)
                .background(FLOW_GREEN.copy(alpha = if (hasCapture) 0.80f else 0.30f))
                .then(if (hasCapture) Modifier.clickable { onAddCombo() } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add",
                tint = Color.White.copy(alpha = if (hasCapture) 1f else 0.4f),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
