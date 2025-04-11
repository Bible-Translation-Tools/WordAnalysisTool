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
import wordanalysistool.composeapp.generated.resources.scripture_reference

@Composable
fun SingletonCard(
    word: SingletonWord?,
    onAnswer: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
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
                        text = word.result?.consensus?.value ?: "",
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(text = stringResource(Res.string.is_word_correct))
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
                    Text(text = reference.toString())
                }
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
                        )
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { onAnswer(false) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(text = stringResource(Res.string.incorrect))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = { onAnswer(true) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Text(text = stringResource(Res.string.correct))
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
        Consensus.NAME -> MaterialTheme.colorScheme.secondary
        Consensus.NEEDS_REVIEW -> MaterialTheme.colorScheme.primary
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