package com.explorerpro.core.designsystem.progress

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.explorerpro.domain.model.OperationStatus
import com.explorerpro.ui.theme.FluentAmber400
import com.explorerpro.ui.theme.FluentEmerald400
import com.explorerpro.ui.theme.FluentRose400
import kotlin.math.roundToInt

/**
 * Label placement modes for [ExplorerProgressIndicator], modeled natively in Jetpack Compose
 * after the reference ProgressBar (`labelPosition="top-floating"`, `min=0`, `max=100`, `value=40`).
 */
enum class ProgressLabelPosition {
    TOP_FLOATING,
    INLINE,
    BOTTOM,
    NONE
}

@Immutable
data class ProgressThemeColors(
    val indicatorColor: Color,
    val indicatorSecondaryColor: Color,
    val trackColor: Color,
    val badgeBackgroundColor: Color,
    val badgeContentColor: Color,
    val badgeBorderColor: Color
)

object ExplorerProgressDefaults {
    @Composable
    fun colorsForStatus(
        status: OperationStatus,
        completed: Boolean = false,
        customIndicatorColor: Color = Color.Unspecified,
        customTrackColor: Color = Color.Unspecified
    ): ProgressThemeColors {
        val scheme = MaterialTheme.colorScheme
        val baseAccent = when {
            customIndicatorColor != Color.Unspecified -> customIndicatorColor
            completed || status == OperationStatus.COMPLETED -> FluentEmerald400
            status == OperationStatus.FAILED || status == OperationStatus.INTERRUPTED -> FluentRose400
            status == OperationStatus.PAUSED || status == OperationStatus.CONFLICT_PENDING -> FluentAmber400
            status == OperationStatus.CANCELLED || status == OperationStatus.CANCELLING -> scheme.outline
            else -> scheme.primary
        }

        val secondaryAccent = when {
            completed || status == OperationStatus.COMPLETED -> FluentEmerald400.copy(alpha = 0.82f)
            status == OperationStatus.FAILED || status == OperationStatus.INTERRUPTED -> FluentRose400.copy(alpha = 0.82f)
            status == OperationStatus.PAUSED -> FluentAmber400.copy(alpha = 0.82f)
            else -> scheme.tertiary
        }

        val resolvedTrack = if (customTrackColor != Color.Unspecified) {
            customTrackColor
        } else {
            scheme.surfaceVariant.copy(alpha = 0.85f)
        }

        return ProgressThemeColors(
            indicatorColor = baseAccent,
            indicatorSecondaryColor = secondaryAccent,
            trackColor = resolvedTrack,
            badgeBackgroundColor = scheme.surfaceContainerHigh,
            badgeContentColor = baseAccent,
            badgeBorderColor = baseAccent.copy(alpha = 0.45f)
        )
    }
}

/**
 * Universal native Jetpack Compose Progress Component (`ExplorerProgressIndicator`).
 *
 * Recreates the visual and behavioral specification of:
 * `<ProgressBar labelPosition="top-floating" min={0} max={100} value={40} />`
 * using 100% native Kotlin & Material 3 Compose with dynamic color & accessibility support.
 */
@Composable
fun ExplorerProgressIndicator(
    value: Float = 0f,
    min: Float = 0f,
    max: Float = 100f,
    labelPosition: ProgressLabelPosition = ProgressLabelPosition.TOP_FLOATING,
    showPercentage: Boolean = true,
    showStatus: Boolean = true,
    animated: Boolean = true,
    trackHeight: Dp = 8.dp,
    indicatorColor: Color = Color.Unspecified,
    trackColor: Color = Color.Unspecified,
    indeterminate: Boolean = false,
    completed: Boolean = false,
    status: OperationStatus = OperationStatus.RUNNING,
    compact: Boolean = false,
    operationLabel: String? = null,
    statusDescription: String? = null,
    modifier: Modifier = Modifier
) {
    val safeRange = (max - min).takeIf { it > 0.0001f } ?: 100f
    val clampedValue = value.coerceIn(min, max)
    val rawFraction = if (completed || status == OperationStatus.COMPLETED) {
        1f
    } else {
        ((clampedValue - min) / safeRange).coerceIn(0f, 1f)
    }

    val animatedFraction by animateFloatAsState(
        targetValue = rawFraction,
        animationSpec = if (animated) {
            tween(durationMillis = 220, easing = FastOutSlowInEasing)
        } else {
            tween(durationMillis = 0)
        },
        label = "explorer_progress_fraction"
    )

    val displayFraction = if (animated) animatedFraction else rawFraction
    val percentageInt = (displayFraction * 100f).roundToInt().coerceIn(0, 100)

    val themeColors = ExplorerProgressDefaults.colorsForStatus(
        status = status,
        completed = completed,
        customIndicatorColor = indicatorColor,
        customTrackColor = trackColor
    )

    val effectiveHeight = if (compact && trackHeight == 8.dp) 5.dp else trackHeight

    val a11yDescription = buildString {
        if (!operationLabel.isNullOrBlank()) {
            append(operationLabel)
            append(", ")
        }
        append(status.label)
        if (!indeterminate) {
            append(", $percentageInt percent")
        }
        if (!statusDescription.isNullOrBlank()) {
            append(", $statusDescription")
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("explorer_progress_indicator")
            .semantics(mergeDescendants = true) {
                contentDescription = a11yDescription
                progressBarRangeInfo = if (indeterminate) {
                    ProgressBarRangeInfo.Indeterminate
                } else {
                    ProgressBarRangeInfo(
                        current = clampedValue,
                        range = min..max,
                        steps = 0
                    )
                }
            }
    ) {
        // Optional header row for operation label + inline percentage or status pill
        if (!operationLabel.isNullOrBlank() || (showStatus && !compact && labelPosition != ProgressLabelPosition.INLINE)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!operationLabel.isNullOrBlank()) {
                    Text(
                        text = operationLabel,
                        style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                if (showStatus) {
                    Spacer(modifier = Modifier.width(8.dp))
                    ProgressStatusBadge(
                        status = if (completed) OperationStatus.COMPLETED else status,
                        accentColor = themeColors.indicatorColor
                    )
                }
            }
            Spacer(modifier = Modifier.height(if (compact) 3.dp else 6.dp))
        }

        // Variant A: Top-Floating Percentage Label (`labelPosition = "top-floating"`)
        if (labelPosition == ProgressLabelPosition.TOP_FLOATING && showPercentage && !indeterminate) {
            FloatingProgressLabel(
                fraction = displayFraction,
                percentageInt = percentageInt,
                themeColors = themeColors,
                compact = compact
            )
            Spacer(modifier = Modifier.height(3.dp))
        }

        // Variant B: Inline layout or standard track
        if (labelPosition == ProgressLabelPosition.INLINE) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (indeterminate) {
                        IndeterminateProgressTrack(
                            trackHeight = effectiveHeight,
                            themeColors = themeColors
                        )
                    } else {
                        DeterminateProgressTrack(
                            fraction = displayFraction,
                            trackHeight = effectiveHeight,
                            themeColors = themeColors
                        )
                    }
                }
                if (showPercentage && !indeterminate) {
                    Text(
                        text = "$percentageInt%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = themeColors.indicatorColor,
                        modifier = Modifier.testTag("inline_percentage_text")
                    )
                }
            }
        } else {
            if (indeterminate) {
                IndeterminateProgressTrack(
                    trackHeight = effectiveHeight,
                    themeColors = themeColors
                )
            } else {
                DeterminateProgressTrack(
                    fraction = displayFraction,
                    trackHeight = effectiveHeight,
                    themeColors = themeColors
                )
            }
        }

        // Bottom label row: optional status description + bottom percentage
        if (!statusDescription.isNullOrBlank() || (labelPosition == ProgressLabelPosition.BOTTOM && showPercentage && !indeterminate)) {
            Spacer(modifier = Modifier.height(if (compact) 3.dp else 5.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!statusDescription.isNullOrBlank()) {
                    Text(
                        text = statusDescription,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                if (labelPosition == ProgressLabelPosition.BOTTOM && showPercentage && !indeterminate) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$percentageInt%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = themeColors.indicatorColor
                    )
                }
            }
        }
    }
}

/**
 * Variant A: Top-Floating Percentage Label that tracks the horizontal progress fill position
 * while clamping its pill bounds within `[0, trackWidth - badgeWidth]` to prevent edge clipping.
 */
@Composable
fun FloatingProgressLabel(
    fraction: Float,
    percentageInt: Int,
    themeColors: ProgressThemeColors,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    var containerWidthPx by remember { mutableIntStateOf(0) }
    var badgeWidthPx by remember { mutableIntStateOf(0) }

    val clampedFraction = fraction.coerceIn(0f, 1f)
    val anchorPx = containerWidthPx * clampedFraction
    val maxBadgeOffsetPx = (containerWidthPx - badgeWidthPx).coerceAtLeast(0)
    val badgeOffsetX = (anchorPx - badgeWidthPx / 2f).roundToInt().coerceIn(0, maxBadgeOffsetPx)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { containerWidthPx = it.width }
            .testTag("top_floating_progress_label_container")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .onSizeChanged { badgeWidthPx = it.width }
                .offset { IntOffset(x = badgeOffsetX, y = 0) }
        ) {
            Surface(
                color = themeColors.badgeBackgroundColor,
                shape = RoundedCornerShape(6.dp),
                tonalElevation = 4.dp,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .border(
                        width = 1.dp,
                        color = themeColors.badgeBorderColor,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .testTag("top_floating_percentage_badge")
            ) {
                Text(
                    text = "$percentageInt%",
                    style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = themeColors.badgeContentColor,
                    modifier = Modifier.padding(
                        horizontal = if (compact) 6.dp else 8.dp,
                        vertical = if (compact) 1.dp else 2.dp
                    )
                )
            }
            // Small downward pointer triangle below the floating badge
            Canvas(modifier = Modifier.size(width = 8.dp, height = 4.dp)) {
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2f, size.height)
                    close()
                }
                drawPath(path = path, color = themeColors.badgeBorderColor)
            }
        }
    }
}

@Composable
private fun DeterminateProgressTrack(
    fraction: Float,
    trackHeight: Dp,
    themeColors: ProgressThemeColors
) {
    val safeFraction = fraction.coerceIn(0f, 1f)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(trackHeight)
            .clip(RoundedCornerShape(50))
            .testTag("determinate_progress_track")
    ) {
        val cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
        // Track background
        drawRoundRect(
            color = themeColors.trackColor,
            size = size,
            cornerRadius = cornerRadius
        )
        // Animated progress fill
        val fillWidth = (size.width * safeFraction).coerceAtLeast(if (safeFraction > 0.005f) size.height else 0f)
            .coerceAtMost(size.width)
        if (fillWidth > 0f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(themeColors.indicatorColor, themeColors.indicatorSecondaryColor),
                    startX = 0f,
                    endX = fillWidth.coerceAtLeast(1f)
                ),
                size = Size(width = fillWidth, height = size.height),
                cornerRadius = cornerRadius
            )
        }
    }
}

/**
 * Variant E: Indeterminate Progress Track (never displays a fabricated percentage).
 */
@Composable
fun IndeterminateProgressTrack(
    trackHeight: Dp = 6.dp,
    themeColors: ProgressThemeColors = ExplorerProgressDefaults.colorsForStatus(OperationStatus.RUNNING),
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "indeterminate_progress_transition")
    val phase by transition.animateFloat(
        initialValue = -0.35f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "indeterminate_phase"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(trackHeight)
            .clip(RoundedCornerShape(50))
            .testTag("indeterminate_progress_track")
    ) {
        val cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(
            color = themeColors.trackColor,
            size = size,
            cornerRadius = cornerRadius
        )

        val segmentWidth = size.width * 0.35f
        val startX = (size.width * phase).coerceIn(0f, size.width)
        val endX = (size.width * phase + segmentWidth).coerceIn(0f, size.width)
        val drawWidth = (endX - startX).coerceAtLeast(0f)

        if (drawWidth > 0f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(themeColors.indicatorColor, themeColors.indicatorSecondaryColor),
                    startX = startX,
                    endX = endX.coerceAtLeast(startX + 1f)
                ),
                topLeft = Offset(startX, 0f),
                size = Size(drawWidth, size.height),
                cornerRadius = cornerRadius
            )
        }
    }
}

/**
 * Variant B: Dedicated Inline Progress Indicator component for file rows, status bars, and lists.
 */
@Composable
fun InlineProgressIndicator(
    value: Float,
    min: Float = 0f,
    max: Float = 100f,
    label: String? = null,
    statusDescription: String? = null,
    indeterminate: Boolean = false,
    status: OperationStatus = OperationStatus.RUNNING,
    modifier: Modifier = Modifier
) {
    ExplorerProgressIndicator(
        value = value,
        min = min,
        max = max,
        labelPosition = ProgressLabelPosition.INLINE,
        showPercentage = !indeterminate,
        showStatus = false,
        trackHeight = 6.dp,
        indeterminate = indeterminate,
        status = status,
        compact = true,
        operationLabel = label,
        statusDescription = statusDescription,
        modifier = modifier
    )
}

/**
 * Variant C: Minimal Progress Indicator — thin progress bar without percentage labels for compact UI.
 */
@Composable
fun MinimalProgressIndicator(
    value: Float = 0f,
    min: Float = 0f,
    max: Float = 100f,
    indeterminate: Boolean = false,
    status: OperationStatus = OperationStatus.RUNNING,
    trackHeight: Dp = 3.dp,
    modifier: Modifier = Modifier
) {
    ExplorerProgressIndicator(
        value = value,
        min = min,
        max = max,
        labelPosition = ProgressLabelPosition.NONE,
        showPercentage = false,
        showStatus = false,
        trackHeight = trackHeight,
        indeterminate = indeterminate,
        status = status,
        compact = true,
        modifier = modifier
    )
}

/**
 * Variant D: Circular Progress Indicator with optional center percentage text.
 * Suitable for thumbnail generation, floating operation badges, and compact status indicators.
 */
@Composable
fun ExplorerCircularProgressIndicator(
    value: Float = 0f,
    min: Float = 0f,
    max: Float = 100f,
    indeterminate: Boolean = false,
    showCenterPercentage: Boolean = true,
    size: Dp = 42.dp,
    strokeWidth: Dp = 4.dp,
    status: OperationStatus = OperationStatus.RUNNING,
    modifier: Modifier = Modifier
) {
    val safeRange = (max - min).takeIf { it > 0.0001f } ?: 100f
    val rawFraction = ((value.coerceIn(min, max) - min) / safeRange).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = rawFraction,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "circular_progress_fraction"
    )
    val percentageInt = (animatedFraction * 100f).roundToInt().coerceIn(0, 100)
    val themeColors = ExplorerProgressDefaults.colorsForStatus(status = status)

    val infiniteTransition = rememberInfiniteTransition(label = "circular_indeterminate")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing)
        ),
        label = "circular_rotation"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .testTag("explorer_circular_progress")
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val strokePx = strokeWidth.toPx()
            val arcDiameter = this.size.minDimension - strokePx
            val topLeft = Offset(strokePx / 2f, strokePx / 2f)
            val arcSize = Size(arcDiameter, arcDiameter)

            drawArc(
                color = themeColors.trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            if (indeterminate) {
                drawArc(
                    color = themeColors.indicatorColor,
                    startAngle = rotationAngle - 90f,
                    sweepAngle = 105f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            } else {
                drawArc(
                    color = themeColors.indicatorColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedFraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }

        if (showCenterPercentage && !indeterminate) {
            Text(
                text = "$percentageInt%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = themeColors.indicatorColor
            )
        }
    }
}

@Composable
fun ProgressStatusBadge(
    status: OperationStatus,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = accentColor.copy(alpha = 0.16f),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Text(
            text = status.label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = accentColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
