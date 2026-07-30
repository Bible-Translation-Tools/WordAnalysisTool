package org.bibletranslationtools.wat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlin.math.min
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.burnoo.compose.remembersetting.rememberStringSettingOrNull
import kotlinx.coroutines.launch
import org.bibletranslationtools.wat.domain.Settings
import org.bibletranslationtools.wat.domain.User
import org.bibletranslationtools.wat.navigation.UrlManager
import org.bibletranslationtools.wat.ui.control.CardFooter
import org.bibletranslationtools.wat.ui.control.MessageToast
import org.bibletranslationtools.wat.ui.control.NextCardNavigation
import org.bibletranslationtools.wat.ui.control.PrevCardNavigation
import org.bibletranslationtools.wat.ui.control.ReviewSlider
import org.bibletranslationtools.wat.ui.control.WordCard
import org.bibletranslationtools.wat.ui.dialogs.ProgressDialog
import org.bibletranslationtools.wat.ui.theme.getFontFamilyForText
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import wordanalysistool.shared.generated.resources.Res
import wordanalysistool.shared.generated.resources.admin
import wordanalysistool.shared.generated.resources.app_name
import wordanalysistool.shared.generated.resources.complete_success
import wordanalysistool.shared.generated.resources.complete_success_description
import wordanalysistool.shared.generated.resources.home
import wordanalysistool.shared.generated.resources.loading
import wordanalysistool.shared.generated.resources.return_home
import wordanalysistool.shared.generated.resources.review_instructions
import wordanalysistool.shared.generated.resources.settings
import wordanalysistool.shared.generated.resources.sign_out
import wordanalysistool.shared.generated.resources.word_of_total
import wordanalysistool.shared.generated.resources.words_checked

class ReviewScreen(
    val ietfCode: String,
    val resourceType: String,
    private val user: User,
    private val batchId: String? = null
) : Screen {

    @Composable
    override fun Content() {
        val viewModel = koinScreenModel<ReviewViewModel> {
            parametersOf(ietfCode, resourceType, user, batchId)
        }

        val navigator = LocalNavigator.currentOrThrow

        val state by viewModel.state.collectAsStateWithLifecycle()
        val event by viewModel.event.collectAsStateWithLifecycle(AdminEvent.Idle)

        var accessToken by rememberStringSettingOrNull(Settings.ACCESS_TOKEN.name)

        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        LaunchedEffect(event) {
            when (event) {
                is ReviewEvent.Logout -> {
                    accessToken = null
                    navigator.popUntilRoot()
                }
                else -> Unit
            }
        }

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

                    Spacer(modifier = Modifier.height(8.dp))

                    NavigationDrawerItem(
                        label = { Text(stringResource(Res.string.home)) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = null
                            )
                        },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            UrlManager.pop()
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(Res.string.settings)) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null
                            )
                        },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navigator.push(SettingsScreen(user))
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    if (user.admin) {
                        NavigationDrawerItem(
                            label = { Text(stringResource(Res.string.admin)) },
                            icon = {
                                Icon(
                                    painter = painterResource(Res.drawable.admin),
                                    contentDescription = null
                                )
                            },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navigator.push(
                                    AdminScreen(
                                        ietfCode = ietfCode,
                                        resourceType = resourceType,
                                        user = user
                                    )
                                )
                            },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                    NavigationDrawerItem(
                        label = {
                            Text(stringResource(Res.string.sign_out, user.username))
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null
                            )
                        },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            accessToken = null
                            UrlManager.replaceAll(LoginScreen())
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surface
            ) { paddingValues ->
                BoxWithConstraints(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                        .padding(paddingValues)
                ) {
                    // Narrow windows give the content almost the whole width.
                    val wide = maxWidth >= WIDE_WINDOW_WIDTH
                    val contentWidth = if (wide) 0.55f else 0.94f

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxSize()
                            .padding(vertical = if (wide) 24.dp else 12.dp)
                    ) {
                        ReviewHeader(
                            language = state.language?.name ?: "",
                            currentIndex = state.currentIndex,
                            total = state.total,
                            reviewed = state.reviewedCount,
                            reachableIndex = state.frontierIndex,
                            canSeek = state.savingWord == null && !state.isLoading,
                            onSeek = viewModel::goTo,
                            onMenuClicked = { scope.launch { drawerState.open() } },
                            modifier = Modifier.fillMaxWidth(contentWidth)
                        )

                        if (state.words.isEmpty()) return@Column

                        if (state.isComplete) {
                            ReviewComplete()
                        } else {
                            Spacer(modifier = Modifier.height(if (wide) 32.dp else 16.dp))

                            Instructions(modifier = Modifier.fillMaxWidth(contentWidth))

                            Spacer(modifier = Modifier.height(16.dp))

                            WordCarousel(
                                state = state,
                                cardWidthFraction = if (wide) 0.44f else 0.7f,
                                onVote = viewModel::onVote,
                                onNext = viewModel::goNext,
                                onPrev = viewModel::goPrev,
                                onFirst = viewModel::goFirst,
                                onLast = viewModel::goLast,
                                modifier = Modifier.fillMaxWidth().weight(1f)
                            )
                        }
                    }

                    if (state.isLoading) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(
                                12.dp,
                                Alignment.CenterVertically
                            ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .size(width = 300.dp, height = 100.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = MaterialTheme.shapes.medium
                                )
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline,
                                    shape = MaterialTheme.shapes.medium
                                )
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = stringResource(Res.string.loading),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = state.toast != null,
                        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 64.dp)
                    ) {
                        state.toast?.let { data ->
                            MessageToast(
                                type = data.type,
                                message = data.message,
                                onDismiss = data.onClose
                            )
                        }
                    }
                }

                state.progress?.let {
                    ProgressDialog(it)
                }
            }
        }
    }
}

@Composable
private fun ReviewHeader(
    language: String,
    currentIndex: Int,
    total: Int,
    reviewed: Int,
    reachableIndex: Int,
    canSeek: Boolean,
    onSeek: (Int) -> Unit,
    onMenuClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val wordOfTotalParts = stringResource(
        Res.string.word_of_total,
        PLACEHOLDER, PLACEHOLDER
    ).split(PLACEHOLDER)
    val checkedParts = stringResource(
        Res.string.words_checked,
        PLACEHOLDER
    ).split(PLACEHOLDER)

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onMenuClicked) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(26.dp)
                    )
                    Text(
                        text = stringResource(Res.string.app_name),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = language,
                    style = LocalTextStyle.current.copy(
                        textDirection = TextDirection.ContentOrLtr,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.W500,
                        fontFamily = getFontFamilyForText(language),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = buildAnnotatedString {
                        append(wordOfTotalParts.getOrElse(0) { "" })
                        withStyle(BoldSpan) { append((currentIndex + 1).toString()) }
                        append(wordOfTotalParts.getOrElse(1) { " " })
                        withStyle(BoldSpan) { append(total.toString()) }
                        append(wordOfTotalParts.getOrElse(2) { "" })
                    },
                    fontSize = 16.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = buildAnnotatedString {
                        append(checkedParts.getOrElse(0) { "" })
                        withStyle(BoldSpan) { append(reviewed.toString()) }
                        append(checkedParts.getOrElse(1) { "" })
                    },
                    fontSize = 15.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        ReviewSlider(
            position = currentIndex,
            reachable = reachableIndex,
            total = total,
            onSeek = onSeek,
            enabled = canSeek,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Marks where the header counters put their numbers, so they can be bolded. */
private const val PLACEHOLDER = "\u0000"

private val BoldSpan = SpanStyle(fontWeight = FontWeight.Bold)

/** Side cards keep a fixed height; the center card is always taller than them. */
/** Below this the review screen switches to its narrow layout. */
private val WIDE_WINDOW_WIDTH = 900.dp
private val PEEK_CARD_HEIGHT = 435.dp
private val CENTER_CARD_HEIGHT = 500.dp

@Composable
private fun Instructions(modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(
            width = 2.dp,
            color = MaterialTheme.colorScheme.secondary
        ),
        modifier = modifier
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = stringResource(Res.string.review_instructions),
                fontSize = 18.sp,
                fontWeight = FontWeight.W400,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun WordCarousel(
    state: ReviewState,
    cardWidthFraction: Float,
    onVote: (Boolean) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onFirst: () -> Unit,
    onLast: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.words.isEmpty()) return

    // Cards are laid out on a track and the track slides to the current one.
    val trackPosition by animateFloatAsState(
        targetValue = state.currentIndex.toFloat(),
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
    )

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier.clipToBounds()
    ) {
        val cardWidth = maxWidth * cardWidthFraction
        val step = cardWidth + maxWidth * 0.05f
        val arrowOffset = cardWidth / 2 + 40.dp

        // A card never outgrows the carousel, or its lower half would be cut off.
        val centerHeight = minOf(CENTER_CARD_HEIGHT, maxHeight)
        val peekHeight = centerHeight * (PEEK_CARD_HEIGHT / CENTER_CARD_HEIGHT)

        // Nothing may move while the current review is in flight.
        val unlocked = state.savingWord == null && !state.isLoading

        // Only cards near the track position are on screen; the current one goes
        // last so it paints on top of its neighbors.
        val visible = (state.currentIndex - 2..state.currentIndex + 2)
            .filter { it in state.words.indices }
            .filter { abs(it - trackPosition) <= 1.6f }
            .sortedByDescending { abs(it - trackPosition) }

        visible.forEach { index ->
            val word = state.words[index]
            val distance = index - trackPosition
            val isCurrent = index == state.currentIndex

            WordCard(
                word = word,
                footer = when {
                    !isCurrent -> {
                        if (word.correct != null) CardFooter.SAVED else CardFooter.NONE
                    }
                    state.savingWord == word.word -> CardFooter.SAVING
                    word.correct == null -> CardFooter.NONE
                    state.canGoNext -> CardFooter.NEXT
                    else -> CardFooter.SAVED
                },
                enabled = isCurrent && unlocked,
                onVote = onVote,
                onNext = onNext,
                modifier = Modifier.align(Alignment.Center)
                    .width(cardWidth)
                    .height(if (isCurrent) centerHeight else peekHeight)
                    .offset(x = step * distance)
                    .alpha(lerp(1f, 0.4f, min(1f, abs(distance))))
                    .then(
                        if (isCurrent) {
                            Modifier
                        } else Modifier.clickable(
                            enabled = unlocked && (index < state.currentIndex ||
                                    state.canGoNext),
                            onClick = if (index < state.currentIndex) onPrev else onNext
                        )
                    )
            )
        }

        if (state.canGoPrev) {
            PrevCardNavigation(
                enabled = unlocked,
                onFirst = onFirst,
                onPrev = onPrev,
                modifier = Modifier.align(Alignment.Center).offset(x = -arrowOffset)
            )
        }

        if (state.currentIndex < state.total - 1) {
            NextCardNavigation(
                enabled = unlocked && state.canGoNext,
                onNext = onNext,
                onLast = onLast,
                modifier = Modifier.align(Alignment.Center).offset(x = arrowOffset)
            )
        }
    }
}

@Composable
private fun ReviewComplete() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        modifier = Modifier.fillMaxWidth(0.55f).fillMaxSize()
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(60.dp)
        )
        Text(
            text = stringResource(Res.string.complete_success),
            fontSize = 36.sp,
            fontWeight = FontWeight.W500,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(Res.string.complete_success_description),
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = UrlManager::pop,
            shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            Text(text = stringResource(Res.string.return_home))
        }
    }
}
