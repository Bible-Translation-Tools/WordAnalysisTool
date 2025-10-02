package org.bibletranslationtools.wat.ui.control

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.bibletranslationtools.wat.data.ReviewWord
import org.bibletranslationtools.wat.ui.theme.getFontFamilyForText
import org.jetbrains.compose.resources.stringResource
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.view_less
import wordanalysistool.composeapp.generated.resources.view_more

@Composable
fun SingletonRow(
    singleton: ReviewWord,
    onFlagged: () -> Unit,
    enabled: Boolean = true,
) {
    val density = LocalDensity.current
    val reference = "${singleton.ref.book.uppercase()} " +
            "${singleton.ref.chapter}:${singleton.ref.verse}"
    val style = TextStyle.Default.copy(
        lineHeight = 28.sp,
        fontSize = 16.sp
    )
    val refHorizontalPadding = 8.dp
    val refVerticalPadding = 2.dp

    val textMeasurer = rememberTextMeasurer()
    val placeholderWidth = remember(reference, refHorizontalPadding, textMeasurer, style) {
        val textWidthInPixels = textMeasurer.measure(reference, style).size.width
        val paddingInPixels = with(density) { (refHorizontalPadding * 2).toPx() }
        val totalWidthInPixels = textWidthInPixels + paddingInPixels

        with(density) {
            totalWidthInPixels.toSp()
        }
    }

    val flagged = singleton.correct == false
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.animateContentSize()
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            SelectionContainer {
                Text(
                    text = singleton.word,
                    style = LocalTextStyle.current.copy(
                        textDirection = TextDirection.ContentOrLtr,
                        fontFamily = getFontFamilyForText(singleton.word),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = if (flagged) {
                        MaterialTheme.colorScheme.error
                    } else MaterialTheme.colorScheme.onSurface
                )
            }

            FlagButton(
                enabled = enabled,
                flagged = flagged,
                onClick = onFlagged
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            val referenceTag = "reference"
            val referenceView = InlineTextContent(
                placeholder = Placeholder(
                    width = placeholderWidth,
                    height = style.lineHeight,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                )
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.background,
                            shape = MaterialTheme.shapes.small
                        )
                        .padding(
                            horizontal = refHorizontalPadding,
                            vertical = refVerticalPadding
                        )
                ) {
                    Text(
                        text = reference,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            val annotatedText = buildAnnotatedString {
                appendInlineContent(referenceTag, reference)

                val textToSearch = singleton.ref.text
                val wordToFind = singleton.word

                val regex = Regex(
                    pattern = "(?<!\\p{L})${Regex.escape(wordToFind)}(?!\\p{L})",
                    option = RegexOption.IGNORE_CASE
                )

                val match = regex.find(textToSearch)
                if (match != null) {
                    val startIndex = match.range.first
                    val endIndex = match.range.last + 1

                    append(textToSearch.substring(0, startIndex))

                    withStyle(
                        style = SpanStyle(
                            color = if (flagged) {
                                MaterialTheme.colorScheme.error
                            } else MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        append(match.value)
                    }

                    append(textToSearch.substring(endIndex))
                } else {
                    append(textToSearch)
                }
            }

            SelectionContainer(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = annotatedText,
                    style = style.copy(
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        textDirection = TextDirection.ContentOrLtr,
                        fontFamily = getFontFamilyForText(annotatedText.text)
                    ),
                    overflow = TextOverflow.Ellipsis,
                    inlineContent = mapOf("reference" to referenceView),
                    maxLines = if (isExpanded) Int.MAX_VALUE else 1
                )
            }
        }

        Row(
            modifier = Modifier.align(Alignment.End)
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = { isExpanded = !isExpanded }
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(if (isExpanded) {
                    Res.string.view_less
                } else Res.string.view_more),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = if (isExpanded) {
                    Icons.Default.KeyboardArrowUp
                } else Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(if (isExpanded) {
                    Res.string.view_less
                } else Res.string.view_more),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
