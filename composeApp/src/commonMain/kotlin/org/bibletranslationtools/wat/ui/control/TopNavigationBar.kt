package org.bibletranslationtools.wat.ui.control

import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.bibletranslationtools.wat.ui.SettingsScreen
import org.jetbrains.compose.resources.stringResource
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopNavigationBar(
    title: String,
    isHome: Boolean = true
) {
    val navigator = LocalNavigator.currentOrThrow
    var showDropDownMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            SingleLineText(title)
        },
        navigationIcon = {
            if (!isHome) {
                IconButton(onClick = { navigator.pop() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = { showDropDownMenu = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = null
                )
            }
            DropdownMenu(
                expanded = showDropDownMenu,
                onDismissRequest = { showDropDownMenu = false },
                modifier = Modifier.width(200.dp)
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.settings)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        showDropDownMenu = false
                        if (navigator.lastItem !is SettingsScreen) {
                            navigator.push(SettingsScreen())
                        }
                    }
                )
            }
        }
    )
}