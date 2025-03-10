package org.bibletranslationtools.wat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import org.bibletranslationtools.wat.ui.control.TopNavigationBar
import org.jetbrains.compose.resources.stringResource
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.settings

class SettingsScreen : Screen {

    @Composable
    override fun Content() {
        Scaffold(
            topBar = {
                TopNavigationBar(stringResource(Res.string.settings), isHome = false)
            }
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                Text("This is Settings", modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}