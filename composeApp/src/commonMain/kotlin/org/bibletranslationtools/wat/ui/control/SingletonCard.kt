package org.bibletranslationtools.wat.ui.control

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.bibletranslationtools.wat.data.Consensus
import org.bibletranslationtools.wat.data.SingletonWord
import org.jetbrains.compose.resources.stringResource
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.correct
import wordanalysistool.composeapp.generated.resources.incorrect
import wordanalysistool.composeapp.generated.resources.is_word_correct
import wordanalysistool.composeapp.generated.resources.is_word_name
import wordanalysistool.composeapp.generated.resources.likely_correct
import wordanalysistool.composeapp.generated.resources.likely_incorrect
import wordanalysistool.composeapp.generated.resources.names
import wordanalysistool.composeapp.generated.resources.no
import wordanalysistool.composeapp.generated.resources.review_needed
import wordanalysistool.composeapp.generated.resources.scripture_reference
import wordanalysistool.composeapp.generated.resources.yes

@Composable
fun SingletonCard(
    word: SingletonWord?,
    onAnswer: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val question = if (word?.result?.consensus == Consensus.NAME) {
        stringResource(Res.string.is_word_name)
    } else {
        stringResource(Res.string.is_word_correct)
    }
    val answerCorrect = if (word?.result?.consensus == Consensus.NAME) {
        stringResource(Res.string.yes)
    } else {
        stringResource(Res.string.correct)
    }
    val answerIncorrect = if (word?.result?.consensus == Consensus.NAME) {
        stringResource(Res.string.no)
    } else {
        stringResource(Res.string.incorrect)
    }

    val localizedConsensus = Consensus.entries.associate { it to localizeConsensus(it) }

    Column(
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .padding(20.dp)
    ) {
        word?.let {
            Column {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Circle,
                        tint = consensusBgColor(word.result?.consensus),
                        contentDescription = null,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = word.result?.consensus?.let { localizedConsensus[it] } ?: "",
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(text = question)
                SelectionContainer {
                    Text(
                        text = it.word,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = consensusFgColor(word.result?.consensus)
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(text = stringResource(Res.string.scripture_reference))
                Spacer(modifier = Modifier.height(24.dp))
                SelectionContainer {
                    val reference = StringBuilder()
                    reference.append("${it.ref.bookName} ")
                    reference.append("(${it.ref.bookSlug.uppercase()}) ")
                    reference.append("${it.ref.chapter}:${it.ref.number} ")
                    Text(
                        text = reference.toString(),
                        style = LocalTextStyle.current.copy(
                            textDirection = TextDirection.ContentOrLtr
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                SelectionContainer {
                    val annotatedText = buildAnnotatedString {
                        val text = it.ref.text
                        val index = text.indexOf(it.word)
                        append(text.substring(0, index))
                        withStyle(
                            style = SpanStyle(
                                color = consensusFgColor(word.result?.consensus)
                            )
                        ) {
                            append(it.word)
                        }
                        append(text.substring(index + it.word.length, text.length))
                    }
                    Text(
                        text = annotatedText,
                        style = LocalTextStyle.current.copy(
                            textDirection = TextDirection.ContentOrLtr
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (word.result != null && word.correct == null) {
                        Button(
                            onClick = { onAnswer(false) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(text = answerIncorrect)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(
                            onClick = { onAnswer(true) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            )
                        ) {
                            Text(text = answerCorrect)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun consensusBgColor(consensus: Consensus?): Color {
    return when (consensus) {
        Consensus.LIKELY_CORRECT -> MaterialTheme.colorScheme.tertiary
        Consensus.LIKELY_INCORRECT -> MaterialTheme.colorScheme.error
        Consensus.NAME -> MaterialTheme.colorScheme.primary
        Consensus.NEEDS_REVIEW -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.background
    }
}

@Composable
private fun consensusFgColor(consensus: Consensus?): Color {
    return when (consensus) {
        null -> MaterialTheme.colorScheme.onBackground
        else -> consensusBgColor(consensus)
    }
}

@Composable
private fun localizeConsensus(consensus: Consensus): String {
    return when (consensus) {
        Consensus.LIKELY_CORRECT -> stringResource(Res.string.likely_correct)
        Consensus.LIKELY_INCORRECT -> stringResource(Res.string.likely_incorrect)
        Consensus.NAME -> stringResource(Res.string.names)
        Consensus.NEEDS_REVIEW -> stringResource(Res.string.review_needed)
    }
}