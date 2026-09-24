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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
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
import org.bibletranslationtools.wat.ui.control.AppDrawer
import org.bibletranslationtools.wat.ui.control.CardFooter
import org.bibletranslationtools.wat.ui.control.MenuButton
import org.bibletranslationtools.wat.ui.control.MessageToast
import org.bibletranslationtools.wat.ui.control.NAV_STEP_OFFSET
import org.bibletranslationtools.wat.ui.control.NextCardNavigation
import org.bibletranslationtools.wat.ui.control.PrevCardNavigation
import org.bibletranslationtools.wat.ui.control.ReviewSlider
import org.bibletranslationtools.wat.ui.control.WordCard
import org.bibletranslationtools.wat.ui.control.rememberAppDrawerState
import org.bibletranslationtools.wat.ui.dialogs.ProgressDialog
import org.bibletranslationtools.wat.ui.theme.getFontFamilyForText
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import wordanalysistool.shared.generated.resources.Res
import wordanalysistool.shared.generated.resources.app_name
import wordanalysistool.shared.generated.resources.complete_success
import wordanalysistool.shared.generated.resources.complete_success_description
import wordanalysistool.shared.generated.resources.instructions
import wordanalysistool.shared.generated.resources.loading
import wordanalysistool.shared.generated.resources.return_home
import wordanalysistool.shared.generated.resources.review_instructions
import wordanalysistool.shared.generated.resources.word_of_total
import wordanalysistool.shared.generated.resources.words_checked
import kotlin.math.abs
import kotlin.math.min

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

        val drawerState = rememberAppDrawerState()
        // A review that has not been started opens on the instructions card.
        var atInstructions by remember(state.total) {
            mutableStateOf(state.total > 0 && state.frontierIndex == 0)
        }
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

        AppDrawer(
            user = user,
            drawerState = drawerState,
            onHome = UrlManager::pop,
            onAdmin = {
                navigator.push(
                    AdminScreen(
                        ietfCode = ietfCode,
                        resourceType = resourceType,
                        user = user
                    )
                )
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
                            atInstructions = atInstructions,
                            canSeek = state.savingWord == null && !state.isLoading,
                            onSeek = { card ->
                                atInstructions = card == 0
                                if (card > 0) viewModel.goTo(card - 1)
                            },
                            onMenuClicked = { scope.launch { drawerState.open() } },
                            modifier = Modifier.fillMaxWidth(contentWidth)
                        )

                        if (state.words.isEmpty()) return@Column

                        if (state.isComplete) {
                            ReviewComplete()
                        } else {
                            Spacer(modifier = Modifier.height(if (wide) 32.dp else 16.dp))

                            WordCarousel(
                                state = state,
                                atInstructions = atInstructions,
                                maxCardWidthFraction = if (wide) 0.44f else 0.7f,
                                onVote = viewModel::onVote,
                                onNext = {
                                    if (atInstructions) {
                                        atInstructions = false
                                        viewModel.goTo(0)
                                    } else viewModel.goNext()
                                },
                                onPrev = {
                                    if (state.currentIndex == 0) {
                                        atInstructions = true
                                    } else viewModel.goPrev()
                                },
                                onFirst = {
                                    atInstructions = true
                                    viewModel.goFirst()
                                },
                                onLast = {
                                    atInstructions = false
                                    viewModel.goLast()
                                },
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
    atInstructions: Boolean,
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
            MenuButton(onClick = onMenuClicked)

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
                        // No word is under review on the instructions card.
                        withStyle(BoldSpan) {
                            append(if (atInstructions) "0" else "${currentIndex + 1}")
                        }
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

        // The slider runs over the carousel's cards, the instructions included:
        // card 0 is the instructions, word i is card i + 1.
        ReviewSlider(
            position = if (atInstructions) 0 else currentIndex + 1,
            reachable = reachableIndex + 1,
            total = total + 1,
            onSeek = onSeek,
            enabled = canSeek,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Marks where the header counters put their numbers, so they can be bolded. */
private const val PLACEHOLDER = "\u0000"

private val BoldSpan = SpanStyle(fontWeight = FontWeight.Bold)

/** Space between the cards, as a share of the carousel's width. */
private const val CARD_GAP_FRACTION = 0.03f

/** Below this the review screen switches to its narrow layout. */
private val WIDE_WINDOW_WIDTH = 900.dp

/** The centre card's size, kept unless the window is too small to hold it. */
private val CENTER_CARD_WIDTH = 800.dp
private val CENTER_CARD_HEIGHT = 580.dp

/** Side cards are the same shape as the centre one, a little smaller. */
private const val PEEK_CARD_SCALE = 0.87f

@Composable
private fun WordCarousel(
    state: ReviewState,
    atInstructions: Boolean,
    maxCardWidthFraction: Float,
    onVote: (Boolean) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onFirst: () -> Unit,
    onLast: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.words.isEmpty()) return

    // The instructions are the first card, so a word sits one slot further on.
    val currentSlot = if (atInstructions) 0 else state.currentIndex + 1
    val lastSlot = state.total

    // Cards are laid out on a track and the track slides to the current one.
    val trackPosition by animateFloatAsState(
        targetValue = currentSlot.toFloat(),
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
    )

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier.clipToBounds()
    ) {
        // The card keeps its size until the carousel cannot hold it, so that the
        // window can be resized without the cards being redrawn at a new size.
        val cardWidth = minOf(CENTER_CARD_WIDTH, maxWidth * maxCardWidthFraction)
        val cardGap = maxWidth * CARD_GAP_FRACTION
        val step = cardWidth + cardGap

        // The step buttons sit on the edges of the neighboring cards. Both are
        // measured from the card geometry, so they hold at any window size.
        val neighbourEdge = cardWidth / 2 + cardGap
        val arrowOffset = neighbourEdge + NAV_STEP_OFFSET

        // A card never outgrows the carousel, or its lower half would be cut off.
        val centerHeight = minOf(CENTER_CARD_HEIGHT, maxHeight)
        val peekHeight = centerHeight * PEEK_CARD_SCALE

        // Nothing may move while the current review is in flight.
        val unlocked = state.savingWord == null && !state.isLoading

        // Stepping forward off the instructions is always allowed; past a word,
        // only once it has been reviewed.
        val canGoForward = atInstructions || state.canGoNext

        // Only cards near the track position are on screen; the current one goes
        // last so it paints on top of its neighbors.
        val visible = (currentSlot - 2..currentSlot + 2)
            .filter { it in 0..lastSlot }
            .filter { abs(it - trackPosition) <= 1.6f }
            .sortedByDescending { abs(it - trackPosition) }

        visible.forEach { slot ->
            val isCurrent = slot == currentSlot
            val distance = slot - trackPosition
            val placement = Modifier.align(Alignment.Center)
                .width(cardWidth)
                .height(if (isCurrent) centerHeight else peekHeight)
                .offset(x = step * distance)
                .alpha(lerp(1f, 0.4f, min(1f, abs(distance))))
                .then(
                    if (isCurrent) {
                        Modifier
                    } else Modifier.clickable(
                        enabled = unlocked && (slot < currentSlot || canGoForward),
                        onClick = if (slot < currentSlot) onPrev else onNext
                    )
                )

            if (slot == 0) {
                InstructionsCard(modifier = placement)
                return@forEach
            }

            val word = state.words[slot - 1]

            WordCard(
                word = word,
                footer = when {
                    state.savingWord == word.word -> CardFooter.SAVING
                    word.correct != null -> CardFooter.SAVED
                    else -> CardFooter.NONE
                },
                enabled = isCurrent && unlocked,
                expandable = isCurrent,
                onVote = onVote,
                modifier = placement
            )
        }

        // Jumping is only offered when it would go further than a single step.
        val reachableSlot = state.frontierIndex + 1

        if (currentSlot > 0) {
            PrevCardNavigation(
                enabled = unlocked,
                showFirst = unlocked && currentSlot > 1,
                onFirst = onFirst,
                onPrev = onPrev,
                modifier = Modifier.align(Alignment.Center).offset(x = -arrowOffset)
            )
        }

        if (currentSlot < lastSlot) {
            NextCardNavigation(
                enabled = unlocked && canGoForward,
                showLast = unlocked && reachableSlot > currentSlot + 1,
                onNext = onNext,
                onLast = onLast,
                modifier = Modifier.align(Alignment.Center).offset(x = arrowOffset)
            )
        }
    }
}

/** How to review, as the card that comes before the first word. */
@Composable
private fun InstructionsCard(modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.secondary),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            modifier = Modifier.fillMaxSize().padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = stringResource(Res.string.instructions),
                fontSize = 32.sp,
                fontWeight = FontWeight.W600,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(Res.string.review_instructions),
                fontSize = 20.sp,
                lineHeight = 34.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
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
