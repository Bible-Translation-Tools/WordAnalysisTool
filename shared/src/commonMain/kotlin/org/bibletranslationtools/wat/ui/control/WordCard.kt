package org.bibletranslationtools.wat.ui.control

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.bibletranslationtools.wat.data.ReviewWord
import org.bibletranslationtools.wat.ui.theme.getFontFamilyForText
import org.jetbrains.compose.resources.stringResource
import wordanalysistool.shared.generated.resources.Res
import wordanalysistool.shared.generated.resources.back
import wordanalysistool.shared.generated.resources.correct
import wordanalysistool.shared.generated.resources.incorrect
import wordanalysistool.shared.generated.resources.next
import wordanalysistool.shared.generated.resources.saved
import wordanalysistool.shared.generated.resources.saving
import wordanalysistool.shared.generated.resources.view_more

/** What the bottom-right corner of a card shows. */
enum class CardFooter {
    NONE,
    SAVING,
    SAVED,
    NEXT
}

@Composable
fun WordCard(
    word: ReviewWord,
    footer: CardFooter = CardFooter.NONE,
    enabled: Boolean = true,
    onVote: (Boolean) -> Unit = {},
    onNext: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val correct = word.correct

    val borderColor = when (correct) {
        true -> MaterialTheme.colorScheme.tertiary
        false -> MaterialTheme.colorScheme.error
        null -> MaterialTheme.colorScheme.outline
    }

    // Reading the whole verse replaces the card content until the user goes back.
    var readingVerse by remember(word.word) { mutableStateOf(false) }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, borderColor),
        modifier = modifier
    ) {
        if (readingVerse) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
                modifier = Modifier.padding(24.dp)
            ) {
                VerseText(word = word, expanded = true)

                Button(
                    onClick = { readingVerse = false },
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(end = 16.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.padding(horizontal = 8.dp).size(18.dp)
                    )
                    Text(stringResource(Res.string.back))
                }
            }
            return@Surface
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier.fillMaxWidth().height(32.dp)
            ) {
                correct?.let { StatusBadge(it) }
            }

            SelectionContainer {
                Text(
                    text = word.word,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        textDirection = TextDirection.ContentOrLtr,
                        fontFamily = getFontFamilyForText(word.word),
                        fontSize = 70.sp,
                        fontWeight = FontWeight.W700,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }

            VerseText(
                word = word,
                canExpand = enabled,
                onExpand = { readingVerse = true }
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                ThumbButton(
                    up = true,
                    selected = correct == true,
                    enabled = enabled,
                    onClick = { onVote(true) }
                )
                ThumbButton(
                    up = false,
                    selected = correct == false,
                    enabled = enabled,
                    onClick = { onVote(false) }
                )
            }

            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier.fillMaxWidth().height(40.dp)
            ) {
                when (footer) {
                    CardFooter.SAVING -> CardStatus(
                        text = stringResource(Res.string.saving),
                        color = MaterialTheme.colorScheme.primary,
                        icon = Icons.Default.Sync,
                        spinning = true
                    )
                    CardFooter.SAVED -> CardStatus(
                        text = stringResource(Res.string.saved),
                        color = MaterialTheme.colorScheme.tertiary,
                        icon = Icons.Default.CloudDone
                    )
                    CardFooter.NEXT -> Button(
                        onClick = onNext,
                        enabled = enabled,
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Text(stringResource(Res.string.next))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.padding(start = 8.dp).size(18.dp)
                        )
                    }
                    CardFooter.NONE -> Unit
                }
            }
        }
    }
}

/**
 * The verse of [word] with its reference and the reviewed word as a chip.
 *
 * Collapsed it shows at most [VERSE_MAX_LINES] lines, chosen by [planVerse] from
 * a real measurement of the whole verse: the head is dropped when needed to keep
 * the reviewed word visible, the tail is cut to the last line, and whatever was
 * left out is reachable through a "view more" button.
 */
@Composable
private fun VerseText(
    word: ReviewWord,
    expanded: Boolean = false,
    canExpand: Boolean = false,
    onExpand: () -> Unit = {}
) {
    val reference = "${word.ref.book.uppercase()} ${word.ref.chapter}:${word.ref.verse}"
    val highlight = MaterialTheme.colorScheme.primary
    val highlightBackground = MaterialTheme.colorScheme.primaryContainer
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    val fullVerse = word.ref.text
    val wordRegex = remember(word.word) {
        Regex(
            pattern = "(?<!\\p{L})${Regex.escape(word.word)}(?!\\p{L})",
            option = RegexOption.IGNORE_CASE
        )
    }

    val style = TextStyle.Default.copy(
        color = onSurfaceVariant,
        fontSize = 24.sp,
        lineHeight = 46.sp,
        textAlign = TextAlign.Center,
        textDirection = TextDirection.ContentOrLtr,
        fontFamily = getFontFamilyForText(fullVerse)
    )

    val matchedWord = remember(fullVerse, wordRegex) {
        wordRegex.find(fullVerse)?.value ?: word.word
    }
    val chipStyle = style.copy(
        color = highlight,
        fontWeight = FontWeight.W600,
        fontFamily = getFontFamilyForText(matchedWord)
    )

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val chipWidth = chipWidthFor(matchedWord, chipStyle, textMeasurer, density)
    val chipPlaceholder = Placeholder(
        width = chipWidth,
        height = 40.sp,
        placeholderVerticalAlign = PlaceholderVerticalAlign.Center
    )

    val viewMoreLabel = stringResource(Res.string.view_more)
    val viewMoreStyle = style.copy(
        color = MaterialTheme.colorScheme.onPrimary,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.W600
    )
    val viewMoreWidth = chipWidthFor(viewMoreLabel, viewMoreStyle, textMeasurer, density)

    // The plan needs the laid out width, which the first pass reports. It is the
    // card width, so it does not depend on what the plan decides to show.
    var widthPx by remember(word) { mutableStateOf(0) }

    val plan = remember(fullVerse, wordRegex, reference, style, chipPlaceholder, widthPx, expanded) {
        if (expanded || widthPx == 0) {
            VersePlan()
        } else planVerse(
            fullVerse = fullVerse,
            wordRegex = wordRegex,
            reference = reference,
            style = style,
            chipPlaceholder = chipPlaceholder,
            reservedChars = viewMoreLabel.length + ELLIPSIS.length,
            widthPx = widthPx,
            textMeasurer = textMeasurer
        )
    }

    val verseText = if (plan.headTrim == 0) {
        fullVerse
    } else "$ELLIPSIS${fullVerse.substring(plan.headTrim)}"
    val match = remember(verseText, wordRegex) { wordRegex.find(verseText) }

    val text = verseAnnotated(
        reference = reference,
        verseText = verseText,
        match = match,
        referenceColor = onSurface
    )

    val chip = InlineTextContent(placeholder = chipPlaceholder) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
                .background(
                    color = highlightBackground,
                    shape = MaterialTheme.shapes.small
                )
                .padding(horizontal = CHIP_HORIZONTAL_PADDING)
        ) {
            ChipLabel(text = match?.value ?: matchedWord, style = chipStyle)
        }
    }

    val viewMore = InlineTextContent(
        placeholder = Placeholder(
            width = viewMoreWidth,
            height = 26.sp,
            placeholderVerticalAlign = PlaceholderVerticalAlign.Center
        )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
                .background(
                    color = highlight,
                    shape = MaterialTheme.shapes.small
                )
                .clickable(onClick = onExpand)
                .padding(horizontal = CHIP_HORIZONTAL_PADDING)
        ) {
            ChipLabel(text = viewMoreLabel, style = viewMoreStyle)
        }
    }

    val linked = !expanded && canExpand && plan.truncated
    val displayed = remember(text, linked, plan, viewMoreLabel) {
        if (!linked) {
            text
        } else buildAnnotatedString {
            plan.cutAt?.let { cut ->
                append(text.subSequence(0, cut.coerceIn(0, text.length)))
                append(ELLIPSIS)
            } ?: run {
                append(text)
                append(" ")
            }
            appendInlineContent(VIEW_MORE_TAG, viewMoreLabel)
        }
    }

    SelectionContainer {
        Text(
            text = displayed,
            style = style,
            maxLines = if (expanded) Int.MAX_VALUE else VERSE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            inlineContent = mapOf(
                WORD_CHIP_TAG to chip,
                VIEW_MORE_TAG to viewMore
            ),
            onTextLayout = { layout ->
                if (layout.size.width != widthPx) widthPx = layout.size.width
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** How much of a verse to leave out, and whether anything was left out at all. */
private data class VersePlan(
    /** Characters dropped from the head of the verse. */
    val headTrim: Int = 0,
    /** Offset in the annotated text to cut the tail at, or null to keep it. */
    val cutAt: Int? = null,
    val truncated: Boolean = false
)

/**
 * Measures the whole verse at [widthPx] to decide what fits in
 * [VERSE_MAX_LINES]. The reviewed word is placed on the second to last visible
 * line at the latest, and the head is never dropped past the word itself.
 */
private fun planVerse(
    fullVerse: String,
    wordRegex: Regex,
    reference: String,
    style: TextStyle,
    chipPlaceholder: Placeholder,
    reservedChars: Int,
    widthPx: Int,
    textMeasurer: TextMeasurer
): VersePlan {
    val constraints = Constraints(maxWidth = widthPx)
    val referenceLength = reference.length + REFERENCE_SEPARATOR.length

    fun annotate(headTrim: Int): Pair<AnnotatedString, MatchResult?> {
        val verse = if (headTrim == 0) {
            fullVerse
        } else "$ELLIPSIS${fullVerse.substring(headTrim)}"
        val match = wordRegex.find(verse)
        return verseAnnotated(reference, verse, match, Color.Unspecified) to match
    }

    fun placeholders(match: MatchResult?) = match?.let {
        listOf(
            AnnotatedString.Range(
                item = chipPlaceholder,
                start = referenceLength + it.range.first,
                end = referenceLength + it.range.last + 1
            )
        )
    } ?: emptyList()

    val (whole, wholeMatch) = annotate(0)
    val wholeLayout = textMeasurer.measure(
        text = whole,
        style = style,
        maxLines = Int.MAX_VALUE,
        placeholders = placeholders(wholeMatch),
        constraints = constraints
    )

    if (wholeLayout.lineCount <= VERSE_MAX_LINES) return VersePlan()

    val headTrim = wholeMatch?.let { match ->
        val wordLine = wholeLayout.getLineForOffset(referenceLength + match.range.first)
        val firstKeptLine = wordLine - (VERSE_MAX_LINES - 2)
        if (firstKeptLine <= 0) {
            0
        } else {
            val lineStart = wholeLayout.getLineStart(firstKeptLine) - referenceLength
            fullVerse.wordStartAfter(offset = lineStart, limit = match.range.first)
        }
    } ?: 0

    val (trimmed, trimmedMatch) = annotate(headTrim)
    val trimmedLayout = textMeasurer.measure(
        text = trimmed,
        style = style,
        overflow = TextOverflow.Ellipsis,
        maxLines = VERSE_MAX_LINES,
        placeholders = placeholders(trimmedMatch),
        constraints = constraints
    )

    val cutAt = if (!trimmedLayout.hasVisualOverflow) {
        null
    } else {
        val lastLine = minOf(VERSE_MAX_LINES, trimmedLayout.lineCount) - 1
        trimmed.cutBefore(
            offset = trimmedLayout.getLineEnd(lastLine, visibleEnd = true) - reservedChars,
            keepAtLeast = trimmedMatch?.let { referenceLength + it.range.last + 1 } ?: 0
        )
    }

    return VersePlan(headTrim = headTrim, cutAt = cutAt, truncated = true)
}

/** The reference, then the verse with the reviewed word replaced by its chip. */
private fun verseAnnotated(
    reference: String,
    verseText: String,
    match: MatchResult?,
    referenceColor: Color
) = buildAnnotatedString {
    withStyle(SpanStyle(color = referenceColor, fontWeight = FontWeight.Bold)) {
        append(reference)
    }
    append(REFERENCE_SEPARATOR)

    if (match != null) {
        append(verseText.take(match.range.first))
        appendInlineContent(WORD_CHIP_TAG, match.value)
        append(verseText.substring(match.range.last + 1))
    } else {
        append(verseText)
    }
}

/**
 * Width of an inline chip holding [label]. Measuring is the only way to size an
 * inline placeholder, but the measurement can fall short — the font may still be
 * loading — so it gets some slack, and [ChipLabel] absorbs the rest.
 */
@Composable
private fun chipWidthFor(
    label: String,
    style: TextStyle,
    textMeasurer: TextMeasurer,
    density: Density
): TextUnit = remember(label, style, textMeasurer, density) {
    val textWidth = textMeasurer.measure(label, style).size.width
    with(density) {
        (textWidth * CHIP_WIDTH_SLACK + (CHIP_HORIZONTAL_PADDING * 2).toPx()).toSp()
    }
}

/** Chip label that overflows its chip rather than losing characters. */
@Composable
private fun ChipLabel(text: String, style: TextStyle) {
    Text(
        text = text,
        style = style,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Visible,
        modifier = Modifier.wrapContentWidth(unbounded = true)
    )
}

/** Start of the first whole word at or after [offset], never beyond [limit]. */
private fun String.wordStartAfter(offset: Int, limit: Int): Int {
    if (offset >= limit) return limit
    val space = indexOf(' ', offset.coerceAtLeast(0))
    return if (space < 0 || space + 1 > limit) limit else space + 1
}

/** Largest offset at or before [offset] that ends a word, never below [keepAtLeast]. */
private fun AnnotatedString.cutBefore(offset: Int, keepAtLeast: Int): Int {
    val target = offset.coerceIn(0, length)
    val boundary = text.lastIndexOf(' ', target)
    return (if (boundary > 0) boundary else target).coerceIn(
        minimumValue = keepAtLeast.coerceAtMost(length),
        maximumValue = length
    )
}

@Composable
private fun StatusBadge(correct: Boolean) {
    val color = if (correct) {
        MaterialTheme.colorScheme.tertiary
    } else MaterialTheme.colorScheme.error

    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.15f)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = if (correct) Icons.Default.ThumbUp else Icons.Default.ThumbDown,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = stringResource(
                    if (correct) Res.string.correct else Res.string.incorrect
                ),
                color = color,
                fontSize = 15.sp,
                fontWeight = FontWeight.W600
            )
        }
    }
}

@Composable
private fun ThumbButton(
    up: Boolean,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val accent = if (up) {
        MaterialTheme.colorScheme.tertiary
    } else MaterialTheme.colorScheme.error

    val background = if (selected) {
        accent.copy(alpha = 0.15f)
    } else MaterialTheme.colorScheme.surface

    val border = if (selected) Color.Transparent else MaterialTheme.colorScheme.outline

    val tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = background,
        border = BorderStroke(1.dp, border),
        modifier = Modifier.size(96.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (up) Icons.Default.ThumbUp else Icons.Default.ThumbDown,
                contentDescription = stringResource(
                    if (up) Res.string.correct else Res.string.incorrect
                ),
                tint = tint,
                modifier = Modifier.size(45.dp)
            )
        }
    }
}

@Composable
private fun CardStatus(
    text: String,
    color: Color,
    icon: ImageVector,
    spinning: Boolean = false
) {
    val rotation = if (spinning) {
        rememberInfiniteTransition().animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = LinearEasing)
            )
        ).value
    } else 0f

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp).rotate(rotation)
        )
        Text(
            text = text,
            color = color,
            fontSize = 17.sp,
            fontWeight = FontWeight.W500
        )
    }
}

private const val WORD_CHIP_TAG = "reviewedWord"
private const val VIEW_MORE_TAG = "viewMore"
private const val ELLIPSIS = "... "
private const val REFERENCE_SEPARATOR = " - "
private const val MIN_TRIM_STEP = 12
private const val CHIP_WIDTH_SLACK = 1.08f
private const val VERSE_MAX_LINES = 5
private val CHIP_HORIZONTAL_PADDING = 8.dp
