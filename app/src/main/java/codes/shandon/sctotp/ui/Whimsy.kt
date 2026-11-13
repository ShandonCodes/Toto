package codes.shandon.sctotp.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import codes.shandon.sctotp.R
import codes.shandon.sctotp.TOTPRepository
import codes.shandon.sctotp.ui.theme.AccountColors
import codes.shandon.sctotp.ui.theme.LocalWhimsyColors
import codes.shandon.sctotp.ui.theme.PlexMono
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

// Same issuer always gets the same accent
fun accountColor(issuer: String): Color =
    AccountColors[abs(issuer.lowercase().hashCode()) % AccountColors.size]

// Pink text over an offset cyan copy, like the Bitbase.sh logo
@Composable
fun OffsetTitle(text: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.headlineMedium) {
    Box(modifier) {
        Text(text, style = style, color = LocalWhimsyColors.current.shadow, modifier = Modifier.offset(2.dp, 2.dp))
        Text(text, style = style, color = MaterialTheme.colorScheme.primary)
    }
}

// Button with a hard offset shadow that it squashes into when pressed
@Composable
fun ChunkyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    content: @Composable RowScope.() -> Unit
) {
    val whimsy = LocalWhimsyColors.current
    val shape = MaterialTheme.shapes.medium
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val depth = 4.dp
    val press by animateDpAsState(if (pressed) depth else 0.dp, tween(80), label = "press")

    Box(modifier) {
        Box(
            Modifier
                .matchParentSize()
                .padding(start = depth, top = depth)
                .background(whimsy.shadow, shape)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(end = depth, bottom = depth)
                .offset(press, press)
                .background(containerColor, shape)
                .border(1.5.dp, whimsy.ink, shape)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            CompositionLocalProvider(LocalContentColor provides whimsy.ink) {
                content()
            }
        }
    }
}

// Blurred stand-in for a secret value. Draws random filler rather than the real text, so nothing
// real reaches the screen, screenshots or accessibility services; only the blur's shape shows.
@Composable
fun Redacted(
    length: Int,
    style: TextStyle,
    color: Color,
    description: String,
    modifier: Modifier = Modifier,
    charset: String = "abcdefghijklmnopqrstuvwxyz",
    format: (String) -> String = { it },
    blurRadius: Dp = 8.dp
) {
    val filler = remember(length, charset) { format(String(CharArray(length) { charset.random() })) }
    Text(
        text = filler,
        style = style,
        color = color,
        maxLines = 1,
        modifier = modifier
            .blur(blurRadius, BlurredEdgeTreatment.Unbounded)
            .clearAndSetSemantics { contentDescription = description }
    )
}

// Little rounded label, like a lipgloss-styled tag
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = LocalWhimsyColors.current.ink,
        maxLines = 1,
        modifier = modifier
            .background(color, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

// Shell-prompt search box: "> search codes…", with a clear button once there's a query
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PromptSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    // Hiding the keyboard (back / collapse button) doesn't drop focus on its own, which leaves the
    // cursor blinking. Clear focus when the IME goes from shown to hidden while we're focused.
    val imeVisible = WindowInsets.isImeVisible
    var imeWasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        if (focused && imeWasVisible && !imeVisible) focusManager.clearFocus()
        imeWasVisible = imeVisible
    }
    val shape = MaterialTheme.shapes.medium
    val textStyle = MaterialTheme.typography.bodyLarge
    val borderColor by animateColorAsState(
        if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        label = "searchBorder"
    )

    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        interactionSource = interactionSource,
        modifier = modifier.fillMaxWidth(),
        decorationBox = { innerTextField ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainer, shape)
                    .border(1.5.dp, borderColor, shape)
                    .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp)
            ) {
                Text(">", style = textStyle, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            stringResource(R.string.search_placeholder),
                            style = textStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    innerTextField()
                }
                if (query.isNotEmpty()) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = stringResource(R.string.clear_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { onQueryChange("") }
                            .padding(4.dp)
                    )
                }
            }
        }
    )
}

// Bubble Tea-style key help: "tap copy • hold remove"
@Composable
fun HelpLine(items: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    val key = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val separator = MaterialTheme.colorScheme.outline
    Text(
        text = buildAnnotatedString {
            items.forEachIndexed { index, (action, description) ->
                if (index > 0) withStyle(SpanStyle(color = separator)) { append("  •  ") }
                withStyle(SpanStyle(color = key, fontWeight = FontWeight.Bold)) { append(action) }
                withStyle(SpanStyle(color = muted)) { append(" $description") }
            }
        },
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier
    )
}

// Terminal progress bar ("██████░░░░") that drains over the current 30s window. Reads the clock
// only while drawing so the smooth animation doesn't recompose anything.
@Composable
fun BlockCountdown(
    clockMillis: () -> Long,
    urgent: Boolean,
    modifier: Modifier = Modifier,
    segments: Int = 15
) {
    val whimsy = LocalWhimsyColors.current
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val urgentColor = MaterialTheme.colorScheme.error

    Canvas(modifier) {
        val periodMillis = TOTPRepository.PERIOD_SECONDS * 1000L
        val millis = clockMillis()
        val remaining = 1f - (millis % periodMillis) / periodMillis.toFloat()
        val filled = remaining * segments
        val gap = 3.dp.toPx()
        val segmentWidth = (size.width - gap * (segments - 1)) / segments
        val corner = CornerRadius(2.dp.toPx())
        val brush = if (urgent) {
            Brush.linearGradient(listOf(urgentColor, urgentColor))
        } else {
            Brush.horizontalGradient(listOf(whimsy.gradientStart, whimsy.gradientEnd), endX = size.width)
        }
        // Pulse in the last few seconds
        val pulse = if (urgent) 0.6f + 0.4f * abs(sin(millis / 180.0)).toFloat() else 1f

        for (i in 0 until segments) {
            val topLeft = Offset(i * (segmentWidth + gap), 0f)
            val segmentSize = Size(segmentWidth, size.height)
            drawRoundRect(track, topLeft, segmentSize, corner)
            val fill = when {
                i < floor(filled) -> 1f
                i == floor(filled).toInt() -> filled - floor(filled)
                else -> 0f
            }
            if (fill > 0f) drawRoundRect(brush, topLeft, segmentSize, corner, alpha = fill * pulse)
        }
    }
}

// toto, the lock that guards your codes. Bobs and blinks when awake, snoozes with z's when asleep.
@Composable
fun Toto(sleeping: Boolean, modifier: Modifier = Modifier) {
    val whimsy = LocalWhimsyColors.current
    val body = MaterialTheme.colorScheme.primaryContainer
    val shackle = MaterialTheme.colorScheme.secondary
    val zColor = MaterialTheme.colorScheme.secondary
    val textMeasurer = rememberTextMeasurer()

    val transition = rememberInfiniteTransition(label = "toto")
    val bob by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (sleeping) 2400 else 1400), RepeatMode.Reverse),
        label = "bob"
    )
    val blink by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 3800
                1f at 3500
                0.1f at 3600
                1f at 3700
            }
        ),
        label = "blink"
    )
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "zzz"
    )

    Canvas(modifier) {
        val s = size.minDimension
        translate(top = bob * s * 0.025f) {
            drawLock(s, body, shackle, whimsy.shadow, whimsy.ink)
            if (sleeping) drawSleepyFace(s, whimsy.ink) else drawAwakeFace(s, whimsy.ink, blink)
        }
        if (sleeping) {
            for ((phase, letter) in listOf(0f to "z", 0.5f to "Z")) {
                val p = (drift + phase) % 1f
                val layout = textMeasurer.measure(
                    letter,
                    TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Bold, fontSize = (14 + p * 10).sp)
                )
                drawText(
                    layout,
                    color = zColor,
                    topLeft = Offset(s * (0.76f + p * 0.1f), s * (0.36f - p * 0.3f)),
                    alpha = 1f - p
                )
            }
        }
    }
}

private fun DrawScope.drawLock(s: Float, body: Color, shackle: Color, shadow: Color, ink: Color) {
    val bodyTopLeft = Offset(s * 0.2f, s * 0.42f)
    val bodySize = Size(s * 0.6f, s * 0.46f)
    val corner = CornerRadius(s * 0.12f)
    val outline = Stroke(width = s * 0.018f)

    // Shackle
    val shackleStroke = Stroke(width = s * 0.075f, cap = StrokeCap.Round)
    drawArc(shackle, 180f, 180f, false, Offset(s * 0.31f, s * 0.14f), Size(s * 0.38f, s * 0.38f), style = shackleStroke)
    drawLine(shackle, Offset(s * 0.31f, s * 0.33f), Offset(s * 0.31f, s * 0.46f), s * 0.075f, StrokeCap.Round)
    drawLine(shackle, Offset(s * 0.69f, s * 0.33f), Offset(s * 0.69f, s * 0.46f), s * 0.075f, StrokeCap.Round)

    // Hard offset shadow, then the body
    drawRoundRect(shadow, bodyTopLeft + Offset(s * 0.035f, s * 0.035f), bodySize, corner)
    drawRoundRect(body, bodyTopLeft, bodySize, corner)
    drawRoundRect(ink, bodyTopLeft, bodySize, corner, style = outline)

    // Blush
    drawOval(Color.White.copy(alpha = 0.35f), Offset(s * 0.27f, s * 0.68f), Size(s * 0.09f, s * 0.05f))
    drawOval(Color.White.copy(alpha = 0.35f), Offset(s * 0.64f, s * 0.68f), Size(s * 0.09f, s * 0.05f))
}

private fun DrawScope.drawAwakeFace(s: Float, ink: Color, blink: Float) {
    val eyeWidth = s * 0.07f
    val eyeHeight = s * 0.1f * blink
    for (x in listOf(0.38f, 0.62f)) {
        val center = Offset(s * x, s * 0.6f)
        drawOval(ink, center - Offset(eyeWidth / 2, eyeHeight / 2), Size(eyeWidth, eyeHeight))
        if (blink > 0.5f) drawCircle(Color.White, s * 0.014f, center - Offset(s * 0.01f, s * 0.02f))
    }
    // Smile
    drawArc(
        ink, 20f, 140f, false, Offset(s * 0.45f, s * 0.64f), Size(s * 0.1f, s * 0.08f),
        style = Stroke(width = s * 0.018f, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawSleepyFace(s: Float, ink: Color) {
    val stroke = Stroke(width = s * 0.02f, cap = StrokeCap.Round)
    // Closed eyes
    for (x in listOf(0.38f, 0.62f)) {
        drawArc(ink, 0f, 180f, false, Offset(s * (x - 0.045f), s * 0.57f), Size(s * 0.09f, s * 0.06f), style = stroke)
    }
    // Little "o" snore mouth
    drawCircle(ink, s * 0.025f, Offset(s * 0.5f, s * 0.73f), style = Stroke(width = s * 0.016f))
}
