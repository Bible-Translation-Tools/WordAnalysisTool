package org.bibletranslationtools.wat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.bibletranslationtools.wat.data.Verse
import org.bibletranslationtools.wat.ui.dialogs.ProgressDialog
import org.koin.core.parameter.parametersOf

class AnalyzeScreen(private val verses: List<Verse>) : Screen {

    @Composable
    override fun Content() {
        val viewModel = koinScreenModel<AnalyzeViewModel> {
            parametersOf(verses)
        }
        val navigator = LocalNavigator.currentOrThrow

        val words by viewModel.singletonWords.collectAsStateWithLifecycle(emptyMap())
        var refs by remember { mutableStateOf<List<Verse>>(emptyList()) }
        var selectedWord by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            viewModel.findSingletonWords()
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Button(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                onClick = { navigator.pop() }
            ) {
                Text("Go back")
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.padding(20.dp)
            ) {
                LazyColumn(modifier = Modifier.weight(0.2f)) {
                    words.forEach { word ->
                        item {
                            Text(
                                text = word.key,
                                fontWeight = if (selectedWord == word.key)
                                    FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier
                                    .clickable {
                                        refs = word.value.refs
                                        selectedWord = word.key
                                    }
                            )
                        }
                    }
                }

                VerticalDivider()

                LazyColumn(modifier = Modifier.weight(0.8f)) {
                    selectedWord?.let {
                        item {
                            Text(
                                text = it,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().height(40.dp)
                            )
                        }
                    }
                    items(refs) { ref ->
                        val verse = "[${ref.bookSlug}] ${ref.bookName} ${ref.chapter}:${ref.number} ${ref.text}"
                        Text(text = verse)
                    }
                }
            }
        }

        viewModel.progress?.let {
            ProgressDialog(it)
        }
    }
}