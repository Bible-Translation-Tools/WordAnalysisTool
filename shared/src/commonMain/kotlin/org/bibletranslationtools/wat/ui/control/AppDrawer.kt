package org.bibletranslationtools.wat.ui.control

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.burnoo.compose.remembersetting.rememberStringSettingOrNull
import kotlinx.coroutines.launch
import org.bibletranslationtools.wat.domain.Settings
import org.bibletranslationtools.wat.domain.User
import org.bibletranslationtools.wat.navigation.UrlManager
import org.bibletranslationtools.wat.ui.LoginScreen
import org.bibletranslationtools.wat.ui.SettingsScreen
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import wordanalysistool.shared.generated.resources.Res
import wordanalysistool.shared.generated.resources.admin
import wordanalysistool.shared.generated.resources.app_name
import wordanalysistool.shared.generated.resources.home
import wordanalysistool.shared.generated.resources.settings
import wordanalysistool.shared.generated.resources.sign_out

/** Opens the [AppDrawer] wrapped around the current screen. */
@Composable
fun MenuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = Icons.Default.Menu,
            contentDescription = stringResource(Res.string.settings),
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
fun rememberAppDrawerState(): DrawerState = rememberDrawerState(DrawerValue.Closed)

/**
 * The app's menu, wrapped around [content].
 *
 * A screen puts its own controls in [actions], at the top; getting around the app
 * and signing out sit at the bottom, in the same place on every screen. [onHome]
 * and [onAdmin] are left out where they make no sense — a screen does not offer a
 * way to itself.
 */
@Composable
fun AppDrawer(
    user: User,
    drawerState: DrawerState,
    onHome: (() -> Unit)? = null,
    onAdmin: (() -> Unit)? = null,
    actions: @Composable ColumnScope.() -> Unit = {},
    content: @Composable () -> Unit
) {
    val navigator = LocalNavigator.currentOrThrow
    val scope = rememberCoroutineScope()
    var accessToken by rememberStringSettingOrNull(Settings.ACCESS_TOKEN.name)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Text(
                    text = stringResource(Res.string.app_name),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.W600,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(16.dp)
                )

                HorizontalDivider()

                fun closeThen(action: () -> Unit): () -> Unit = {
                    scope.launch { drawerState.close() }
                    action()
                }

                // What this screen offers, above the way out of it.
                Column(
                    modifier = Modifier.weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    content = actions
                )

                HorizontalDivider()

                Spacer(modifier = Modifier.height(8.dp))

                onHome?.let { home ->
                    DrawerItem(
                        label = stringResource(Res.string.home),
                        onClick = closeThen(home)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = null
                        )
                    }
                }

                DrawerItem(
                    label = stringResource(Res.string.settings),
                    onClick = closeThen { navigator.push(SettingsScreen(user)) }
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null
                    )
                }

                if (user.admin) {
                    onAdmin?.let { admin ->
                        DrawerItem(
                            label = stringResource(Res.string.admin),
                            onClick = closeThen(admin)
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.admin),
                                contentDescription = null
                            )
                        }
                    }
                }

                DrawerItem(
                    label = stringResource(Res.string.sign_out, user.username),
                    onClick = closeThen {
                        accessToken = null
                        UrlManager.replaceAll(LoginScreen())
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        },
        content = content
    )
}

@Composable
private fun DrawerItem(
    label: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    NavigationDrawerItem(
        label = { Text(label) },
        icon = icon,
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp)
    )
}
