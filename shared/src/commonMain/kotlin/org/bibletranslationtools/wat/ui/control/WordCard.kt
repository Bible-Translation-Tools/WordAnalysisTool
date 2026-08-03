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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
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
    expandable: Boolean = enabled,
    onVote: (Boolean) -> Unit = {},
    onNext: () -> Unit = {},
    /** Starts on the whole-verse view. For previews and rendering tests. */
    initiallyReading: Boolean = false,
    modifier: Modifier = Modifier
) {
    val correct = word.correct

    val borderColor = when (correct) {
        true -> MaterialTheme.colorScheme.tertiary
        false -> MaterialTheme.colorScheme.error
        null -> MaterialTheme.colorScheme.outline
    }

    // Reading the whole verse replaces the card content until the user goes back.
    var readingVerse by remember(word.word) { mutableStateOf(initiallyReading) }

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, borderColor),
        modifier = modifier
    ) {
        // Everything is sized off the card, which shrinks with the window.
        BoxWithConstraints {
            val scale = minOf(
                maxWidth / CARD_REFERENCE_WIDTH,
                maxHeight / CARD_REFERENCE_HEIGHT
            ).coerceIn(MIN_CARD_SCALE, 1f)
            val padding = CARD_PADDING * scale
            val metrics = cardMetrics(
                cardWidth = maxWidth,
                cardHeight = maxHeight,
                padding = padding,
                scale = scale,
                word = word.word,
                reference = referenceOf(word)
            )
            val spacing = metrics.spacing

            if (readingVerse) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(spacing),
                    modifier = Modifier.fillMaxSize().padding(padding)
                ) {
                    // Reference and verse center together, or the verse would sit
                    // in the middle of the card with the reference left at the top.
                    BoxWithConstraints(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.weight(1f)
                    ) {
                        val height = with(LocalDensity.current) { maxHeight.toPx() }
                        // The whole verse is on show here, so it is set at the
                        // largest size that fits; past the floor it scrolls.
                        val fitted = verseFitScale(
                            word = word,
                            reference = referenceOf(word),
                            available = DpSize(maxWidth, maxHeight),
                            spacing = spacing,
                            scale = scale
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(spacing),
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        ) {
                            VerseReference(word = word, scale = fitted)

                            VerseText(
                                word = word,
                                availableHeightPx = height,
                                expanded = true,
                                scale = fitted
                            )
                        }
                    }

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
                return@BoxWithConstraints
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing),
                modifier = Modifier.fillMaxSize()
                    .padding(padding)
                    .padding(bottom = CARD_BOTTOM_PADDING * scale)
            ) {
                // Both this row and the footer keep their height whether or not
                // they have anything in them, so the verse below always has the
                // same room and a reviewed card reads like an unreviewed one.
                Box(
                    contentAlignment = Alignment.CenterEnd,
                    modifier = Modifier.fillMaxWidth().height(BADGE_ROW_HEIGHT)
                ) {
                    correct?.let { StatusBadge(it, scale) }
                }

                WordTitle(word = word.word, fontSize = metrics.wordFontSize)

                VerseReference(word = word, scale = scale)

                // The verse is the only part that gives up room, so the thumbs and
                // the footer always keep their size.
                BoxWithConstraints(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    VerseText(
                        word = word,
                        availableHeightPx = with(LocalDensity.current) {
                            maxHeight.toPx()
                        },
                        canExpand = expandable,
                        onExpand = { readingVerse = true },
                        scale = scale
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp * scale)) {
                    ThumbButton(
                        up = true,
                        selected = correct == true,
                        enabled = enabled,
                        scale = scale,
                        onClick = { onVote(true) }
                    )
                    ThumbButton(
                        up = false,
                        selected = correct == false,
                        enabled = enabled,
                        scale = scale,
                        onClick = { onVote(false) }
                    )
                }

            }

            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(end = padding, bottom = padding)
                    .height(FOOTER_HEIGHT)
            ) {
                when (footer) {
                    CardFooter.SAVING -> CardStatus(
                        text = stringResource(Res.string.saving),
                        color = MaterialTheme.colorScheme.primary,
                        icon = Icons.Default.Sync,
                        scale = scale,
                        spinning = true
                    )
                    CardFooter.SAVED -> CardStatus(
                        text = stringResource(Res.string.saved),
                        color = MaterialTheme.colorScheme.tertiary,
                        icon = Icons.Default.CloudDone,
                        scale = scale
                    )
                    CardFooter.NEXT -> Button(
                        onClick = onNext,
                        enabled = enabled,
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier.fillMaxHeight()
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

@Composable
private fun WordTitle(word: String, fontSize: TextUnit) {
    SelectionContainer {
        Text(
            text = word,
            style = wordTitleStyle(word).copy(fontSize = fontSize),
            maxLines = 1,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun wordTitleStyle(word: String) =
    MaterialTheme.typography.headlineLarge.copy(
        textDirection = TextDirection.ContentOrLtr,
        fontFamily = getFontFamilyForText(word),
        fontWeight = FontWeight.W700,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface
    )

/** Sizes the card decides on, so that a full verse fits without a taller card. */
private data class CardMetrics(
    val spacing: Dp,
    val wordFontSize: TextUnit
)

/**
 * Works out the row spacing and word size this card can afford.
 *
 * The verse is the point of the card, so [VERSE_MAX_LINES] lines of it are booked
 * first and the rows around it give up the difference: spacing tightens, and if
 * that is not enough the word is set smaller. Both stop at a floor, below which
 * the verse simply shows fewer lines.
 */
@Composable
private fun cardMetrics(
    cardWidth: Dp,
    cardHeight: Dp,
    padding: Dp,
    scale: Float,
    word: String,
    reference: String
): CardMetrics {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val titleStyle = wordTitleStyle(word)
    val referenceStyle = referenceStyle(reference, scale)
    val verseLineHeight = VERSE_LINE_HEIGHT * scale

    return remember(
        cardWidth, cardHeight, padding, scale, word, reference,
        titleStyle, referenceStyle, density, textMeasurer
    ) {
        with(density) {
            val availableWidth = cardWidth.roundToPx() - (padding * 2).roundToPx()
            // A hair over the lines themselves: booking them exactly leaves the
            // verse a fraction short after rounding, which costs it a whole line.
            val verse = verseLineHeight.toDp() * VERSE_MAX_LINES + VERSE_ROOM_SLACK
            val referenceHeight = textMeasurer
                .measure(reference, referenceStyle)
                .size.height.toDp()
            // The badge and the button keep their size on a small card, so they
            // stay legible; the footer floats, so the column does not book it.
            val fixedRows = BADGE_ROW_HEIGHT + THUMB_SIZE * scale
            val room = cardHeight - padding * 2 - CARD_BOTTOM_PADDING * scale -
                    fixedRows - referenceHeight - verse

            var wordFontSize = WORD_FONT_SIZE * scale
            var spacing = CARD_SPACING * scale

            while (true) {
                val measured = textMeasurer.measure(
                    text = word,
                    style = titleStyle.copy(fontSize = wordFontSize),
                    maxLines = 1,
                    softWrap = false
                ).size

                // One line only: a word too wide for the card is set smaller.
                if (measured.width > availableWidth &&
                    wordFontSize > WORD_MIN_FONT_SIZE
                ) {
                    wordFontSize = (wordFontSize.value - 2f).sp
                    continue
                }

                spacing = ((room - measured.height.toDp()) / CARD_ROW_GAPS)
                    .coerceAtMost(CARD_SPACING * scale)

                if (spacing >= MIN_CARD_SPACING ||
                    wordFontSize <= WORD_MIN_FONT_SIZE
                ) {
                    break
                }
                wordFontSize = (wordFontSize.value - 2f).sp
            }

            CardMetrics(
                spacing = spacing.coerceAtLeast(MIN_CARD_SPACING),
                wordFontSize = wordFontSize
            )
        }
    }
}

/**
 * The verse of [word], with the reviewed word as a chip.
 *
 * Collapsed it shows as many lines as [availableHeightPx] holds, up to
 * [VERSE_MAX_LINES], with the content chosen by [planVerse] from
 * a real measurement of the whole verse: the head is dropped when needed to keep
 * the reviewed word visible, the tail is cut to the last line, and whatever was
 * left out is reachable through a "view more" button.
 */
@Composable
private fun VerseText(
    word: ReviewWord,
    availableHeightPx: Float,
    expanded: Boolean = false,
    canExpand: Boolean = false,
    onExpand: () -> Unit = {},
    scale: Float = 1f
) {
    val highlight = MaterialTheme.colorScheme.primary
    val highlightBackground = MaterialTheme.colorScheme.primaryContainer
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    val fullVerse = word.ref.text
    val wordRegex = remember(word.word) { wordRegexFor(word.word) }

    val style = TextStyle.Default.copy(
        color = onSurfaceVariant,
        fontSize = VERSE_FONT_SIZE * scale,
        lineHeight = VERSE_LINE_HEIGHT * scale,
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

    // Measured in em, as multiples of the font size, so the chips keep their
    // proportions when the text is set smaller to fit.
    val chipWidth = chipWidthFor(matchedWord, chipStyle, textMeasurer, density)
    val chipWidthEm = chipWidth.value / (VERSE_FONT_SIZE * scale).value
    val chipPlaceholder = Placeholder(
        width = chipWidthEm.em,
        height = CHIP_HEIGHT_EM.em,
        placeholderVerticalAlign = PlaceholderVerticalAlign.Center
    )

    val viewMoreLabel = stringResource(Res.string.view_more)
    val viewMoreStyle = style.copy(
        color = MaterialTheme.colorScheme.onPrimary,
        fontSize = VIEW_MORE_FONT_SIZE * scale,
        lineHeight = VIEW_MORE_FONT_SIZE * scale * 1.25f,
        fontWeight = FontWeight.W600
    )
    val viewMorePlaceholder = Placeholder(
        width = CHIP_HEIGHT_EM.em,
        height = CHIP_HEIGHT_EM.em,
        placeholderVerticalAlign = PlaceholderVerticalAlign.Center
    )

    // The plan needs the laid out width, which the first pass reports. It is the
    // card width, so it does not depend on what the plan decides to show.
    var widthPx by remember(word) { mutableStateOf(0) }

    // Lines are capped by [VERSE_MAX_LINES], but a short card holds fewer, and a
    // plan made for more lines than the card shows would hide its own tail.
    val lineHeightPx = with(density) { style.lineHeight.toPx() }
    val lines = if (expanded) {
        Int.MAX_VALUE
    } else {
        val room = (availableHeightPx / lineHeightPx).toInt()
        room.coerceIn(1, VERSE_MAX_LINES)
    }

    val expandable = canExpand && viewMoreLabel.isNotEmpty()

    val plan = remember(
        fullVerse, wordRegex, style, chipPlaceholder,
        viewMorePlaceholder, widthPx, lines, expanded, expandable
    ) {
        if (expanded || widthPx == 0 || !expandable) {
            VersePlan()
        } else planVerse(
            fullVerse = fullVerse,
            wordRegex = wordRegex,
            maxLines = lines,
            style = style,
            chipPlaceholder = chipPlaceholder,
            viewMorePlaceholder = viewMorePlaceholder,
            widthPx = widthPx,
            textMeasurer = textMeasurer
        )
    }

    val verseText = if (plan.headTrim == 0) {
        fullVerse
    } else "$ELLIPSIS${fullVerse.substring(plan.headTrim)}"
    val match = remember(verseText, wordRegex) { wordRegex.find(verseText) }

    val text = verseAnnotated(verseText = verseText, match = match)

    val chip = InlineTextContent(placeholder = chipPlaceholder) {
        BoxWithConstraints(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
                .background(
                    color = highlightBackground,
                    shape = MaterialTheme.shapes.small
                )
                .padding(horizontal = CHIP_HORIZONTAL_PADDING)
        ) {
            ChipLabel(
                text = match?.value ?: matchedWord,
                style = chipStyle.copy(fontSize = resolvedFontSize(maxHeight))
            )
        }
    }

    val viewMore = InlineTextContent(placeholder = viewMorePlaceholder) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
                .background(
                    color = highlight,
                    shape = MaterialTheme.shapes.small
                )
                // The label sits inside the verse's selection container, which
                // would otherwise give it a text cursor and let it be selected.
                .pointerHoverIcon(PointerIcon.Hand, overrideDescendants = true)
                .clickable(onClick = onExpand)
        ) {
            DisableSelection {
                ChipLabel(text = viewMoreLabel, style = viewMoreStyle)
            }
        }
    }

    val linked = !expanded && expandable && plan.truncated
    val displayed = remember(text, linked, plan, viewMoreLabel) {
        if (!linked) {
            text
        } else buildAnnotatedString {
            plan.cutAt?.let { cut ->
                append(text.subSequence(0, cut.coerceIn(0, text.length)))
            } ?: append(text)
            append(BUTTON_GAP)
            appendInlineContent(VIEW_MORE_TAG, VIEW_MORE_ALT)
        }
    }

    SelectionContainer {
        Text(
            text = displayed,
            style = style,
            maxLines = lines,
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

/**
 * The largest text scale at which the whole verse fits [available].
 *
 * Compose's autoSize cannot be used for this: with no line limit it does not
 * report the height overflow, so it settles on a size and ellipsizes the rest.
 * Measuring each candidate is what the collapsed card already relies on.
 */
@Composable
private fun verseFitScale(
    word: ReviewWord,
    reference: String,
    available: DpSize,
    spacing: Dp,
    scale: Float
): Float {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val verse = word.ref.text
    val match = remember(verse, word.word) { wordRegexFor(word.word).find(verse) }
    val verseFont = getFontFamilyForText(verse)
    val chipFont = getFontFamilyForText(match?.value ?: word.word)
    val referenceFont = getFontFamilyForText(reference)

    return remember(
        verse, match, reference, available, spacing, scale,
        verseFont, chipFont, referenceFont, density, textMeasurer
    ) {
        with(density) {
            val text = verseAnnotated(verse, match)
            val constraints = Constraints(maxWidth = available.width.roundToPx())
            val room = available.height.toPx() - spacing.toPx()

            // Chip proportions do not change with the size, so one measurement
            // of the word at the base size gives the em width for every candidate.
            val chipWidthEm = match?.let {
                val measured = textMeasurer.measure(
                    text = it.value,
                    style = TextStyle(
                        fontSize = VERSE_FONT_SIZE,
                        fontWeight = FontWeight.W600,
                        fontFamily = chipFont
                    )
                ).size.width
                val padded = measured * CHIP_WIDTH_SLACK +
                        (CHIP_HORIZONTAL_PADDING * 2).toPx()
                padded / VERSE_FONT_SIZE.toPx()
            }

            var candidate = scale
            while (candidate > MIN_VERSE_FIT_SCALE) {
                val verseStyle = TextStyle(
                    fontSize = VERSE_FONT_SIZE * candidate,
                    lineHeight = VERSE_LINE_HEIGHT * candidate,
                    textAlign = TextAlign.Center,
                    textDirection = TextDirection.ContentOrLtr,
                    fontFamily = verseFont
                )
                val placeholders = match?.let {
                    listOf(
                        AnnotatedString.Range(
                            item = Placeholder(
                                width = (chipWidthEm ?: 1f).em,
                                height = CHIP_HEIGHT_EM.em,
                                placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                            ),
                            start = it.range.first,
                            end = it.range.last + 1
                        )
                    )
                } ?: emptyList()

                val verseHeight = textMeasurer.measure(
                    text = text,
                    style = verseStyle,
                    maxLines = Int.MAX_VALUE,
                    placeholders = placeholders,
                    constraints = constraints
                ).size.height
                val referenceHeight = textMeasurer.measure(
                    text = reference,
                    style = TextStyle(
                        fontSize = VERSE_FONT_SIZE * candidate * REFERENCE_FONT_RATIO,
                        fontWeight = FontWeight.Bold,
                        fontFamily = referenceFont
                    )
                ).size.height

                if (verseHeight + referenceHeight <= room) break
                candidate -= VERSE_FIT_STEP
            }

            candidate.coerceAtLeast(MIN_VERSE_FIT_SCALE)
        }
    }
}

/** Where the verse is from, on its own row above it. */
@Composable
private fun VerseReference(word: ReviewWord, scale: Float) {
    val reference = referenceOf(word)

    Text(
        text = reference,
        style = referenceStyle(reference, scale),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** Matches the reviewed word on its own, not inside a longer word. */
private fun wordRegexFor(word: String) = Regex(
    pattern = "(?<!\\p{L})${Regex.escape(word)}(?!\\p{L})",
    option = RegexOption.IGNORE_CASE
)

private fun referenceOf(word: ReviewWord) =
    "${word.ref.book.uppercase()} ${word.ref.chapter}:${word.ref.verse}"

@Composable
private fun referenceStyle(reference: String, scale: Float) = TextStyle.Default.copy(
    color = MaterialTheme.colorScheme.onSurface,
    fontSize = VERSE_FONT_SIZE * scale * REFERENCE_FONT_RATIO,
    fontWeight = FontWeight.Bold,
    textAlign = TextAlign.Center,
    textDirection = TextDirection.ContentOrLtr,
    fontFamily = getFontFamilyForText(reference)
)

/** How much of a verse to leave out, and whether anything was left out at all. */
internal data class VersePlan(
    /** Characters dropped from the head of the verse. */
    val headTrim: Int = 0,
    /** Offset in the annotated text to cut the tail at, or null to keep it. */
    val cutAt: Int? = null,
    val truncated: Boolean = false
)

/**
 * Measures the whole verse at [widthPx] to decide what fits in
 * [VERSE_MAX_LINES]. As much of the head is kept as those lines hold while both
 * the reviewed word and the "view more" button stay visible; the head is never
 * dropped past the word itself.
 */
internal fun planVerse(
    fullVerse: String,
    wordRegex: Regex,
    maxLines: Int,
    style: TextStyle,
    chipPlaceholder: Placeholder,
    viewMorePlaceholder: Placeholder,
    widthPx: Int,
    textMeasurer: TextMeasurer
): VersePlan {
    val constraints = Constraints(maxWidth = widthPx)

    fun placeholders(
        match: MatchResult?,
        link: IntRange? = null
    ) = buildList {
        match?.let {
            add(
                AnnotatedString.Range(
                    item = chipPlaceholder,
                    start = it.range.first,
                    end = it.range.last + 1
                )
            )
        }
        link?.let {
            add(
                AnnotatedString.Range(
                    item = viewMorePlaceholder,
                    start = it.first,
                    end = it.last + 1
                )
            )
        }
    }

    /** The verse followed by the button, as the card shows it when truncated. */
    fun withButton(body: AnnotatedString) = buildAnnotatedString {
        append(body)
        if (!body.text.endsWith(BUTTON_GAP)) append(BUTTON_GAP)
        appendInlineContent(VIEW_MORE_TAG, VIEW_MORE_ALT)
    }

    /**
     * Line layout, measured unclamped: the true line count and line indices,
     * with none of the ellipsis semantics of a maxLines limited layout.
     */
    fun layoutOf(text: AnnotatedString, match: MatchResult?, link: IntRange? = null) =
        textMeasurer.measure(
            text = text,
            style = style,
            maxLines = Int.MAX_VALUE,
            placeholders = placeholders(match, link),
            constraints = constraints
        )

    fun linkRangeOf(text: AnnotatedString) =
        (text.length - VIEW_MORE_ALT.length)..text.lastIndex

    fun verseFrom(headTrim: Int) = if (headTrim == 0) {
        fullVerse
    } else "$ELLIPSIS${fullVerse.substring(headTrim)}"

    /** Line of the reviewed word, and how many lines verse plus button take. */
    fun measureHead(headTrim: Int): Triple<TextLayoutResult, MatchResult?, Int> {
        val verse = verseFrom(headTrim)
        val match = wordRegex.find(verse)
        val body = verseAnnotated(verse, match)
        val linked = withButton(body)
        return Triple(
            layoutOf(linked, match, linkRangeOf(linked)),
            match,
            match?.let { it.range.last + 1 } ?: 0
        )
    }

    fun fitsWith(headTrim: Int): Boolean {
        val (layout, _, wordEnd) = measureHead(headTrim)
        val wordLineThere = layout.getLineForOffset((wordEnd - 1).coerceAtLeast(0))
        return layout.lineCount <= maxLines && wordLineThere <= maxLines - 1
    }

    /**
     * The head is dropped a line at a time, which overshoots by up to a line:
     * give whole words back while everything still fits.
     */
    fun grownHead(headTrim: Int): Int {
        var kept = headTrim
        while (kept > 0) {
            val richer = fullVerse.wordStartBefore(kept)
            if (richer == kept || !fitsWith(richer)) return kept
            kept = richer
        }
        return kept
    }

    // The button belongs to every measurement from here on: a verse is only left
    // whole when it fits without one.
    val wholeMatch = wordRegex.find(fullVerse)
    val whole = verseAnnotated(fullVerse, wholeMatch)
    val wholeLayout = layoutOf(whole, wholeMatch)

    if (wholeLayout.lineCount <= maxLines) return VersePlan()

    val wordStart = wholeMatch?.range?.first ?: 0
    val wordLine = wholeMatch?.let {
        wholeLayout.getLineForOffset(it.range.first)
    } ?: 0

    // Start from the line that would put the word on the last visible line and
    // give up head only while it does not fit.
    var candidateLine = (wordLine - (maxLines - 1)).coerceAtLeast(0)

    while (true) {
        val headTrim = if (candidateLine == 0) {
            0
        } else fullVerse.wordStartAfter(
            offset = wholeLayout.getLineStart(candidateLine),
            limit = wordStart
        )

        val verse = if (headTrim == 0) {
            fullVerse
        } else "$ELLIPSIS${fullVerse.substring(headTrim)}"
        val match = wordRegex.find(verse)
        val body = verseAnnotated(verse, match)

        val linked = withButton(body)
        val layout = layoutOf(linked, match, linkRangeOf(linked))

        val wordEnd = match?.let { it.range.last + 1 } ?: 0
        val lastLine = maxLines - 1

        // Verse and button both fit: the button stays, nothing else to leave out.
        if (layout.lineCount <= maxLines) {
            return VersePlan(headTrim = grownHead(headTrim), cutAt = null, truncated = true)
        }

        val wordVisible = layout.getLineForOffset((wordEnd - 1).coerceAtLeast(0)) <= lastLine
        val lastResort = candidateLine >= wordLine || headTrim >= wordStart

        if (wordVisible || lastResort) {
            return VersePlan(
                headTrim = headTrim,
                cutAt = largestCutThatFits(
                    body = body,
                    from = layout.getLineEnd(lastLine).coerceAtMost(body.length),
                    maxLines = maxLines,
                    keepAtLeast = wordEnd,
                    withButton = ::withButton,
                    layoutOf = { text -> layoutOf(text, match, linkRangeOf(text)) }
                ),
                truncated = true
            )
        }

        candidateLine++
    }
}

/**
 * Walks back from [from], a word at a time, to the longest cut of [body] whose
 * text, ellipsis and button still fit [maxLines] lines. Never cuts before
 * [keepAtLeast], which is where the reviewed word ends.
 */
private fun largestCutThatFits(
    body: AnnotatedString,
    from: Int,
    maxLines: Int,
    keepAtLeast: Int,
    withButton: (AnnotatedString) -> AnnotatedString,
    layoutOf: (AnnotatedString) -> TextLayoutResult
): Int {
    val floor = keepAtLeast.coerceIn(0, body.length)
    var cut = from.coerceIn(floor, body.length)

    while (true) {
        val candidate = withButton(
            buildAnnotatedString {
                append(body.subSequence(0, cut))
                append(BUTTON_GAP)
            }
        )
        if (layoutOf(candidate).lineCount <= maxLines || cut <= floor) return cut

        val boundary = body.text.lastIndexOf(' ', cut - 1)
        cut = if (boundary <= floor) floor else boundary
    }
}

/** The verse, with the reviewed word replaced by its chip. */
internal fun verseAnnotated(
    verseText: String,
    match: MatchResult?
) = buildAnnotatedString {
    if (match != null && match.value.isNotEmpty()) {
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

/** The font size a chip of [height] was laid out at, from its em proportions. */
@Composable
private fun resolvedFontSize(height: Dp): TextUnit =
    with(LocalDensity.current) { height.toSp() } / CHIP_HEIGHT_EM

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

/** Start of the word before [offset], or 0 when there is none. */
private fun String.wordStartBefore(offset: Int): Int {
    if (offset <= 0) return 0
    return lastIndexOf(' ', (offset - 2).coerceAtLeast(0)) + 1
}

/** Start of the first whole word at or after [offset], never beyond [limit]. */
private fun String.wordStartAfter(offset: Int, limit: Int): Int {
    if (offset >= limit) return limit
    val space = indexOf(' ', offset.coerceAtLeast(0))
    return if (space < 0 || space + 1 > limit) limit else space + 1
}

@Composable
private fun StatusBadge(correct: Boolean, scale: Float) {
    val color = if (correct) {
        MaterialTheme.colorScheme.tertiary
    } else MaterialTheme.colorScheme.error

    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.15f),
        modifier = Modifier.fillMaxHeight()
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp * scale)
        ) {
            Icon(
                imageVector = if (correct) Icons.Default.ThumbUp else Icons.Default.ThumbDown,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp * scale)
            )
            Text(
                text = stringResource(
                    if (correct) Res.string.correct else Res.string.incorrect
                ),
                color = color,
                fontSize = 15.sp * scale,
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
    scale: Float,
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
        shape = MaterialTheme.shapes.extraLarge,
        color = background,
        border = BorderStroke(1.dp, border),
        modifier = Modifier.size(THUMB_SIZE * scale)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (up) Icons.Default.ThumbUp else Icons.Default.ThumbDown,
                contentDescription = stringResource(
                    if (up) Res.string.correct else Res.string.incorrect
                ),
                tint = tint,
                modifier = Modifier.size(THUMB_ICON_SIZE * scale)
            )
        }
    }
}

@Composable
private fun CardStatus(
    text: String,
    color: Color,
    icon: ImageVector,
    scale: Float,
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
            modifier = Modifier.size(20.dp * scale).rotate(rotation)
        )
        Text(
            text = text,
            color = color,
            fontSize = 17.sp * scale,
            fontWeight = FontWeight.W500
        )
    }
}

internal const val WORD_CHIP_TAG = "reviewedWord"
private const val VIEW_MORE_TAG = "viewMore"

/**
 * Stands in for the button while text is measured. A constant, because the label
 * is a string resource, and an unloaded resource is empty — which inline content
 * does not allow.
 */
private const val VIEW_MORE_ALT = "\u2026"
internal const val ELLIPSIS = "... "

/** Keeps the button off the last word of the verse. */
private const val BUTTON_GAP = " "
private const val CHIP_WIDTH_SLACK = 1.08f

/** Card width the sizes below are meant for; smaller cards scale down to fit. */
private val CARD_REFERENCE_WIDTH = 600.dp
private val CARD_REFERENCE_HEIGHT = 500.dp
private const val MIN_CARD_SCALE = 0.55f
private val CARD_PADDING = 24.dp

/** Extra room under the thumbs, so they do not sit on the card's edge. */
private val CARD_BOTTOM_PADDING = 16.dp
private val CARD_SPACING = 20.dp
private val MIN_CARD_SPACING = 6.dp

/** Gaps between the card's rows: badge, word, reference, verse, thumbs. */
private const val CARD_ROW_GAPS = 4
private val BADGE_ROW_HEIGHT = 32.dp
private val FOOTER_HEIGHT = 40.dp
private val THUMB_SIZE = 96.dp
private val THUMB_ICON_SIZE = 45.dp
private val WORD_FONT_SIZE = 70.sp
private val WORD_MIN_FONT_SIZE = 24.sp
private val VERSE_FONT_SIZE = 24.sp
private val VERSE_LINE_HEIGHT = 46.sp
private const val MIN_VERSE_FIT_SCALE = 0.55f
private const val VERSE_FIT_STEP = 0.05f
private val VERSE_ROOM_SLACK = 2.dp

/** Chip height as a multiple of the verse font size, so chips scale with it. */
private const val CHIP_HEIGHT_EM = 46f * 0.85f / 24f
private const val REFERENCE_FONT_RATIO = 0.85f
private val VIEW_MORE_FONT_SIZE = 16.sp
internal const val VERSE_MAX_LINES = 3
private val CHIP_HORIZONTAL_PADDING = 8.dp
