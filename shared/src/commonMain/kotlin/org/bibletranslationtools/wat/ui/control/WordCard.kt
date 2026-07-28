package org.bibletranslationtools.wat.ui.control

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.bibletranslationtools.wat.data.ReviewWord
import org.bibletranslationtools.wat.ui.theme.getFontFamilyForText
import org.jetbrains.compose.resources.stringResource
import wordanalysistool.shared.generated.resources.Res
import wordanalysistool.shared.generated.resources.correct
import wordanalysistool.shared.generated.resources.incorrect
import wordanalysistool.shared.generated.resources.next
import wordanalysistool.shared.generated.resources.saved
import wordanalysistool.shared.generated.resources.saving

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

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, borderColor),
        modifier = modifier
    ) {
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

            VerseText(word)

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

@Composable
private fun VerseText(word: ReviewWord) {
    val reference = "${word.ref.book.uppercase()} ${word.ref.chapter}:${word.ref.verse}"
    val highlight = MaterialTheme.colorScheme.primary
    val highlightBackground = MaterialTheme.colorScheme.primaryContainer
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    val verseText = word.ref.text
    val style = TextStyle.Default.copy(
        color = onSurfaceVariant,
        fontSize = 24.sp,
        lineHeight = 46.sp,
        textAlign = TextAlign.Center,
        textDirection = TextDirection.ContentOrLtr,
        fontFamily = getFontFamilyForText(verseText)
    )

    val match = remember(word) {
        Regex(
            pattern = "(?<!\\p{L})${Regex.escape(word.word)}(?!\\p{L})",
            option = RegexOption.IGNORE_CASE
        ).find(verseText)
    }
    val matched = match?.value ?: word.word

    val chipStyle = style.copy(
        color = highlight,
        fontWeight = FontWeight.W600,
        fontFamily = getFontFamilyForText(matched)
    )
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val chipWidth = remember(matched, chipStyle, textMeasurer, density) {
        val textWidth = textMeasurer.measure(matched, chipStyle).size.width
        with(density) {
            (textWidth + (CHIP_HORIZONTAL_PADDING * 2).toPx()).toSp()
        }
    }

    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = onSurface, fontWeight = FontWeight.Bold)) {
            append(reference)
        }
        append(" - ")

        if (match != null) {
            append(verseText.take(match.range.first))
            appendInlineContent(WORD_CHIP_TAG, matched)
            append(verseText.substring(match.range.last + 1))
        } else {
            append(verseText)
        }
    }

    val chip = InlineTextContent(
        placeholder = Placeholder(
            width = chipWidth,
            height = 40.sp,
            placeholderVerticalAlign = PlaceholderVerticalAlign.Center
        )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
                .background(
                    color = highlightBackground,
                    shape = MaterialTheme.shapes.small
                )
                .padding(horizontal = CHIP_HORIZONTAL_PADDING)
        ) {
            Text(text = matched, style = chipStyle)
        }
    }

    SelectionContainer {
        Text(
            text = text,
            style = style,
            inlineContent = mapOf(WORD_CHIP_TAG to chip),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private const val WORD_CHIP_TAG = "reviewedWord"
private val CHIP_HORIZONTAL_PADDING = 8.dp

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
