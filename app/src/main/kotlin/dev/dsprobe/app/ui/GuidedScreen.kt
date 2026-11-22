package dev.dsprobe.app.ui

import android.Manifest
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.R
import dev.dsprobe.app.audio.AndroidMic
import dev.dsprobe.app.audio.AndroidTone
import dev.dsprobe.app.debug.Confidence
import dev.dsprobe.app.debug.connectionOf
import dev.dsprobe.app.debug.DebugRunner
import dev.dsprobe.app.debug.EndpointCard
import dev.dsprobe.app.debug.EndpointTour
import dev.dsprobe.app.debug.Readout
import dev.dsprobe.app.debug.ReadoutKind
import dev.dsprobe.app.debug.TryIt
import dev.dsprobe.app.debug.TryKind
import dev.dsprobe.app.debug.chevronBackShown
import dev.dsprobe.app.debug.chevronNextShown
import dev.dsprobe.app.debug.readoutKindOf
import dev.dsprobe.app.debug.RowLines
import dev.dsprobe.app.debug.Verdict
import dev.dsprobe.app.debug.guessText
import dev.dsprobe.app.debug.locationLines
import dev.dsprobe.app.debug.sheetLines
import dev.dsprobe.app.output.OutputsShown
import dev.dsprobe.app.route.PadSession
import dev.dsprobe.app.route.Reduction
import dev.dsprobe.app.route.Route
import dev.dsprobe.app.route.RouteState
import dev.dsprobe.app.route.boundary
import dev.dsprobe.app.ui.theme.NumberStyle

private const val TAG = "GuidedScreen"
private const val DRAG_PX_MIN = 12f
private const val HANDLE_WIDTH_DP = 40
private const val HANDLE_HEIGHT_DP = 4

private val MONO_SMALL = TextStyle(fontFamily = NumberStyle.fontFamily, fontSize = 12.sp)

@Composable
private fun ConfidenceChip(c: Confidence) {
    val scheme = MaterialTheme.colorScheme
    val (back, front, label) = when (c) {
        Confidence.PROVEN -> Triple(
            scheme.primaryContainer,
            scheme.onPrimaryContainer,
            R.string.tour_tag_proven,
        )

        Confidence.DOCUMENTED -> Triple(
            scheme.tertiaryContainer,
            scheme.onTertiaryContainer,
            R.string.tour_tag_documented,
        )

        Confidence.UNKNOWN -> Triple(
            scheme.errorContainer,
            scheme.onErrorContainer,
            R.string.tour_tag_unknown,
        )
    }
    Surface(color = back, contentColor = front, shape = RoundedCornerShape(8.dp)) {
        Text(
            stringResource(label),
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Title with the Details button, then the confidence chip and the FACTS rows. */
@Composable
private fun CardHead(card: EndpointCard, onDetails: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            card.title,
            Modifier.weight(1f),
            style = MaterialTheme.typography.headlineSmall,
            maxLines = TITLE_LINES_MAX,
        )
        TextButton(onDetails, Modifier.heightIn(min = TOUCH_TARGET_DP.dp)) {
            OneLine(R.string.details_open)
        }
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConfidenceChip(card.confidence)
        val facts =
            card.factsRow?.let { stringResource(R.string.tour_facts, it) }
                ?: stringResource(R.string.tour_no_facts)
        Text(
            facts,
            style = NumberStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
        )
    }
}

private fun reactionLabel(v: Verdict?): Int? = when (v) {
    Verdict.WORKS -> R.string.reaction_yes
    Verdict.DOESNT -> R.string.reaction_no
    Verdict.NOT_SURE -> R.string.reaction_not_sure
    null -> null
}

/**
 * Asked once, inline, and only after Try it: the answer goes on the tour and clears the output's
 * own pending question.
 */
@Composable
private fun Reaction(tour: EndpointTour, env: TryEnv) {
    val answer = { verdict: Verdict ->
        tour.answer(verdict, "")
        when (verdict) {
            Verdict.WORKS -> env.outputs.answerChanged(true)
            Verdict.DOESNT -> env.outputs.answerChanged(false)
            Verdict.NOT_SURE -> env.outputs.dismissAsk()
        }
    }
    Text(stringResource(R.string.reaction_question), style = MaterialTheme.typography.titleMedium)
    ReactionRow(reactionLabel(tour.verdictNow), {
        answer(Verdict.WORKS)
    }, { answer(Verdict.DOESNT) }, { answer(Verdict.NOT_SURE) })
}

@Composable
private fun ReactionOrAnswer(tour: EndpointTour, env: TryEnv) {
    if (tour.reactionPending) {
        Reaction(tour, env)
        return
    }
    tour.verdictNow?.let { v ->
        Text(
            stringResource(
                R.string.reaction_answered,
                stringResource(checkNotNull(reactionLabel(v))),
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The main area of one card: no vertical scroll, so each part has to stay inside its declared
 * rows (see mainAreaOf). Everything long is in the Details sheet.
 */
@Composable
private fun CardBody(
    tour: EndpointTour,
    card: EndpointCard,
    env: TryEnv,
    live: CardLive,
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CardHead(card, live.onDetails)
        ReadoutArea(card, live.readout, routeOf(env.state), live.onResetGyro, compact = true)
        TryPanel(card, env, tour::markTried, live.onDetails)
        ReactionOrAnswer(tour, env)
        ExpectedLine(card.id, connectionOf(env.state))
    }
}

/** What a card's main area reads from the pad each frame, and the Details button. */
private class CardLive(
    val readout: Readout?,
    val onResetGyro: () -> Unit,
    val onDetails: () -> Unit,
)

/**
 * Everything too long for the main area: the rumble panel and the full motion readout where they
 * exist, every location line, the guess, and the other connections. It scrolls, and it is a
 * layer in the layout, not a dialog, so the pad's own events keep arriving.
 */
@Composable
private fun DetailsSheet(
    card: EndpointCard,
    env: TryEnv,
    live: CardLive,
    onTried: () -> Unit,
    onClose: () -> Unit,
) {
    val route = routeOf(env.state)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    card.title,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                )
                QuietButton(onClose) { OneLine(R.string.details_close) }
            }
            if (card.tryIt.let { it is TryIt.Action && it.kind == TryKind.RUMBLE }) {
                RumbleFullControls(env, onTried)
            }
            if (readoutKindOf(card) in FULL_READOUT_KINDS) {
                ReadoutArea(card, live.readout, route, live.onResetGyro)
            }
            val places = locationLines(
                card,
                env.shown.playerLeds,
                route,
                stringResource(R.string.tour_no_android_event),
            )
            for (place in places) {
                Text(place, style = NumberStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(guessText(card, route), style = MaterialTheme.typography.bodyLarge)
            ExpectedHere(card.id, connectionOf(env.state))
        }
    }
}

private val FULL_READOUT_KINDS = setOf(ReadoutKind.MOTION, ReadoutKind.FINGER)
private const val TITLE_LINES_MAX = 2

@Composable
private fun SheetHandle(open: Boolean, onToggle: (Boolean) -> Unit) {
    val description = stringResource(R.string.tour_sheet_handle_cd)
    Column(
        Modifier.fillMaxWidth().heightIn(min = TOUCH_TARGET_DP.dp)
            .clickable { onToggle(!open) }
            .pointerInput(open) {
                detectVerticalDragGestures { _, dy ->
                    if (dy < -DRAG_PX_MIN) {
                        onToggle(true)
                    } else if (dy > DRAG_PX_MIN) {
                        onToggle(false)
                    }
                }
            }
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.width(
                HANDLE_WIDTH_DP.dp,
            ).height(
                HANDLE_HEIGHT_DP.dp,
            ).background(MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp)),
        )
        Text(
            stringResource(R.string.tour_sheet_handle),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The trace rows for this card's endpoint, newest on top, with a switch for all rows. In the
 * layout, not a dialog, so the pad's own events keep arriving.
 */
@Composable
private fun LogSheet(lines: List<RowLines>, showAll: Boolean, onShowAll: (Boolean) -> Unit) {
    Surface(
        Modifier.fillMaxWidth().height(AppLimits.SHEET_HEIGHT_DP.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.tour_sheet_all),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                )
                Switch(showAll, onShowAll)
            }
            if (lines.isEmpty()) {
                Text(
                    stringResource(R.string.tour_sheet_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(Modifier.fillMaxWidth()) {
                items(lines) { line ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(line.headline, style = NumberStyle.copy(fontWeight = FontWeight.Bold))
                        Text(
                            line.location,
                            style = MONO_SMALL,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomBar(tour: EndpointTour) {
    val isOnCard = tour.current != null
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QuietButton(tour::back, enabled = tour.index > 0) { OneLine(R.string.tour_back) }
        Text(
            stringResource(
                R.string.tour_progress_short,
                tour.number.coerceAtMost(tour.cards.size),
                tour.cards.size,
            ),
            Modifier.weight(1f),
            style = NumberStyle.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
        )
        TonalButton(tour::next, enabled = isOnCard) { OneLine(R.string.tour_next) }
    }
}

@Composable
private fun Summary(tour: EndpointTour, onShare: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.tour_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.tour_finished, tour.answers.size),
            style = MaterialTheme.typography.bodyLarge,
        )
        PrimaryButton(tour::start, Modifier.fillMaxWidth()) { OneLine(R.string.tour_restart) }
        TonalButton(onShare, Modifier.fillMaxWidth()) { OneLine(R.string.debug_share) }
    }
}

/** A mic permission launcher that records on a yes and says why on a no. */
@Composable
private fun rememberMicTap(mic: AndroidMic): () -> Unit {
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) mic.start() else mic.refuse()
        }
    return {
        if (mic.hasPermission()) mic.start() else launcher.launch(Manifest.permission.RECORD_AUDIO)
    }
}

/**
 * One page index for the pager, Next and Back: the tour's. A swipe settles on a page and tells the
 * tour; Next and Back change the tour and the pager follows. [onSwipe] fires only for a page the
 * tour did not already hold.
 */
@OptIn(ExperimentalFoundationApi::class) // Pager is experimental in this foundation (BOM 2024.06)
@Composable
private fun SyncPager(tour: EndpointTour, pager: PagerState, onSwipe: () -> Unit) {
    val target = tour.index.coerceIn(0, tour.pageCount - 1)
    LaunchedEffect(target) { if (pager.currentPage != target) pager.animateScrollToPage(target) }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page ->
            if (page != tour.index.coerceIn(0, tour.pageCount - 1)) {
                onSwipe()
                tour.goTo(page)
            }
        }
    }
}

/** A chevron at an edge: more cards that way. Fades out instead of vanishing. */
@Composable
private fun EdgeChevron(isShown: Boolean, isRight: Boolean, modifier: Modifier) {
    val alpha by animateFloatAsState(
        if (isShown) 1f else 0f,
        tween(AppLimits.CHEVRON_FADE_MS),
        label = "chevron",
    )
    if (alpha <= 0f) return
    Icon(
        if (isRight) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.AutoMirrored.Filled.KeyboardArrowLeft,
        stringResource(if (isRight) R.string.chevron_next_cd else R.string.chevron_back_cd),
        modifier.alpha(alpha).size(CHEVRON_SIZE_DP.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
}

private const val CHEVRON_SIZE_DP = 40

/** The pager's pages: the open card in full, the neighbours as a title while one is dragged in. */
@OptIn(ExperimentalFoundationApi::class) // see SyncPager
@Composable
private fun GuidedPages(
    tour: EndpointTour,
    pager: PagerState,
    env: TryEnv,
    live: CardLive,
    isLocked: Boolean,
    onShare: () -> Unit,
) {
    HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = !isLocked) { page ->
        val card = tour.cards.getOrNull(page)
        when {
            card == null -> Summary(tour, onShare)
            page == tour.index -> CardBody(tour, card, env, live)
            else -> Text(card.title, Modifier.padding(16.dp), style = MaterialTheme.typography.headlineSmall)
        }
    }
}

/**
 * The guided test on its own screen: one card per page, swiped or stepped with a fixed bottom
 * bar, the long text behind Details, and the log pulled up from a handle.
 */
@OptIn(ExperimentalFoundationApi::class) // see SyncPager
@Composable
fun GuidedScreen(
    session: PadSession,
    runner: DebugRunner,
    view: RouteView,
    pump: FeedPump,
    actions: ScreenActions,
) {
    val hub = session.debug
    val tour = hub.tour
    val context = LocalContext.current
    val mic = remember(context) { AndroidMic(context.applicationContext) }
    val tone = remember(context) { AndroidTone(context.applicationContext) }
    val micTap = rememberMicTap(mic)
    val shareTitle = stringResource(R.string.debug_share)
    var sheetOpen by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    var hasSwiped by remember { mutableStateOf(false) }
    val card = tour.current
    var detailsOpen by remember(card?.id) { mutableStateOf(false) }
    // opening the screen for the first time starts the walk; coming back keeps the place
    LaunchedEffect(tour) { if (!tour.isRunning && tour.answers.isEmpty()) tour.start() }
    DisposableEffect(mic, tone) {
        onDispose {
            mic.stop()
            tone.stop()
        }
    }
    LaunchedEffect(card?.id) {
        mic.stop()
        tone.stop()
    }
    val env = TryEnv(view.state, view.locked, view.shown, session.outputs, mic, tone, micTap)
    val connection = connectionOf(view.state)
    LaunchedEffect(card?.id, connection) { tour.noteConnection(connection) }
    val share: () -> Unit = {
        boundary("share log", {
            Log.w(TAG, it)
        }) { shareLog(context, session.exportLog(runner.results), shareTitle) }
    }
    val pager = rememberPagerState(tour.index.coerceIn(0, tour.pageCount - 1)) { tour.pageCount }
    SyncPager(tour, pager) { hasSwiped = true }
    val live = CardLive(pump.readout.value, pump::resetGyro) { detailsOpen = true }
    Column(Modifier.fillMaxSize()) {
        CloseBar(R.string.tour_title, R.string.tour_leave, actions.onUp)
        ConnectionLine(connection)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            GuidedPages(tour, pager, env, live, detailsOpen, share)
            val page = pager.currentPage
            EdgeChevron(
                chevronNextShown(page, tour.pageCount, hasSwiped),
                isRight = true,
                Modifier.align(Alignment.CenterEnd),
            )
            EdgeChevron(
                chevronBackShown(page, hasSwiped),
                isRight = false,
                Modifier.align(Alignment.CenterStart),
            )
            if (detailsOpen && card != null) {
                DetailsSheet(card, env, live, tour::markTried) { detailsOpen = false }
            }
        }
        if (card != null) {
            val rows = remember(pump.traceVersion.longValue) { hub.trace.rows }
            SheetHandle(sheetOpen) { sheetOpen = it }
            if (sheetOpen) {
                LogSheet(sheetLines(rows, tour.seenNow, card, showAll), showAll) { showAll = it }
            }
        }
        BottomBar(tour)
    }
}
