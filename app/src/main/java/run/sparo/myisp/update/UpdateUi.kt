package run.sparo.myisp.update

import android.content.ActivityNotFoundException
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import run.sparo.myisp.BuildConfig
import run.sparo.myisp.R
import run.sparo.myisp.ui.ago
import run.sparo.myisp.ui.components.PrimaryButton
import run.sparo.myisp.ui.theme.AppIcons
import run.sparo.myisp.ui.theme.AppTheme
import run.sparo.myisp.ui.theme.Radius
import run.sparo.myisp.ui.theme.Space
import java.util.Locale

/*
 * The updater's screens, in the app's own style:
 * the ISP's colours and logo, the ring, a sheet over Home. The logo in the
 * top bar is where an update lives - a dot while one waits, a ring while it
 * downloads - so the customer keeps using the app throughout.
 */

private fun mb(bytes: Long): String = "%.1f MB".format(Locale.UK, bytes / 1_000_000.0)

/** 0, 25, 50, 75 or 100: often enough for TalkBack, never every byte. */
private fun quartile(fraction: Float): Int = ((fraction * 4).toInt().coerceIn(0, 4)) * 25

/**
 * The provider's logo with the update on its edge: a dot when one is
 * waiting, a ring that fills while it downloads, one soft pulse when it is
 * ready. Taps open the sheet. Nothing is drawn when there is no update.
 */
@Composable
fun UpdateLogoBadge(state: UpdateState, size: Dp, onOpen: () -> Unit, logo: @Composable () -> Unit) {
    val colors = AppTheme.colors
    val ring = MaterialTheme.colorScheme.primary
    val stage = state.stage
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(stage is UpdateStage.Ready) {
        if (stage is UpdateStage.Ready) {
            pulse.animateTo(1.08f, tween(180))
            pulse.animateTo(1f, tween(260))
        }
    }
    val fraction by animateFloatAsState(
        when (stage) {
            is UpdateStage.Downloading -> stage.fraction
            is UpdateStage.Ready, is UpdateStage.Installing, is UpdateStage.NeedsPermission -> 1f
            else -> 0f
        },
        tween(400),
        label = "update-ring",
    )
    val description = when (stage) {
        is UpdateStage.Downloading -> stringResource(R.string.update_badge_downloading_a11y, quartile(stage.fraction))
        else -> stringResource(R.string.update_badge_a11y)
    }
    val modifier = if (state.pending) {
        Modifier.clip(RoundedCornerShape(13.dp)).clickable(role = Role.Button, onClickLabel = description, onClick = onOpen)
            .semantics { contentDescription = description }
    } else {
        Modifier
    }

    Box(modifier.size(size).scale(pulse.value), contentAlignment = Alignment.Center) {
        logo()
        if (state.pending) {
            // Drawn just outside the logo, so nothing beside it moves.
            Canvas(Modifier.matchParentSize()) {
                val w = 2.5.dp.toPx()
                val out = 4.dp.toPx()
                val topLeft = Offset(-out, -out)
                val arcSize = Size(this.size.width + out * 2, this.size.height + out * 2)
                if (fraction > 0f) {
                    drawRoundRect(ring.copy(alpha = 0.18f), topLeft, arcSize, androidx.compose.ui.geometry.CornerRadius(17.dp.toPx()), style = Stroke(w))
                    drawArc(ring, -90f, fraction * 360f, false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round))
                }
                if (stage is UpdateStage.Available || stage is UpdateStage.Failed) {
                    val r = 5.dp.toPx()
                    val c = Offset(this.size.width - r * 0.4f, r * 0.4f)
                    drawCircle(colors.dark.let { if (it) Color.Black else Color.White }, r + 2.dp.toPx(), c)
                    drawCircle(ring, r, c)
                }
            }
        }
    }
}

/**
 * The What's new sheet over Home. [onClose] after "Not now", a swipe, or
 * by itself a moment after a download starts - the ring on the logo then
 * carries the progress.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheet(manager: UpdateManager, state: UpdateState, providerName: String, logo: @Composable () -> Unit, onClose: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    fun close() {
        // Closing an offer counts as "Not now"; closing mid-download does not.
        (state.stage as? UpdateStage.Available)?.let { manager.dismiss(it.update) }
        scope.launch { sheet.hide() }.invokeOnCompletion { onClose() }
    }

    // A moment after the download starts, the sheet steps aside.
    LaunchedEffect(state.stage is UpdateStage.Downloading) {
        if (state.stage is UpdateStage.Downloading) {
            delay(1600)
            scope.launch { sheet.hide() }.invokeOnCompletion { onClose() }
        }
    }

    ModalBottomSheet(
        onDismissRequest = ::close,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = Space.l),
        ) {
            UpdatePanel(manager, state, providerName, logo, forced = false, onNotNow = ::close)
        }
    }
}

/**
 * Everything an update goes through, as one panel: the offer, the download,
 * the one-time permission, verified-and-ready, and failures. The sheet shows
 * it over Home; the too-old screen shows it on its own with [forced] set,
 * so there is no "Not now".
 */
@Composable
fun UpdatePanel(
    manager: UpdateManager,
    state: UpdateState,
    providerName: String,
    logo: @Composable () -> Unit,
    forced: Boolean,
    onNotNow: () -> Unit,
) {
    val appName = stringResource(R.string.app_name)
    val update = when (val s = state.stage) {
        is UpdateStage.Available -> s.update
        is UpdateStage.Downloading -> s.update
        is UpdateStage.NeedsPermission -> s.update
        is UpdateStage.Ready -> s.update
        is UpdateStage.Installing -> s.update
        is UpdateStage.Failed -> s.update
        else -> state.latest
    }

    Header(appName, providerName, update, logo)

    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(Space.l)) {
        when (val stage = state.stage) {
            UpdateStage.Checking -> Busy(stringResource(R.string.update_checking))
            is UpdateStage.Available -> {
                Notes(stage.update)
                PrimaryButton(stringResource(R.string.update_action), { manager.download(stage.update, appName) })
                if (!forced) NotNow(onNotNow)
            }
            is UpdateStage.Downloading -> {
                Notes(stage.update)
                ProgressBar(stage)
                Text(stringResource(R.string.update_leave_hint), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.muted)
                TextButton(onClick = manager::cancelDownload, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.update_cancel))
                }
            }
            is UpdateStage.NeedsPermission -> AllowInstalls(manager, appName)
            is UpdateStage.Ready -> Ready(manager, appName, busy = false)
            is UpdateStage.Installing -> Ready(manager, appName, busy = true)
            is UpdateStage.Failed -> Failure(manager, stage, appName, forced, onNotNow)
            UpdateStage.None -> Busy(stringResource(R.string.update_up_to_date))
        }
    }
}

@Composable
private fun Header(appName: String, providerName: String, update: AppUpdate?, logo: @Composable () -> Unit) {
    val colors = AppTheme.colors
    Column(
        Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.l))
            .background(colors.hero)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            logo()
            Text(providerName, color = colors.onHero.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
        Text(
            stringResource(R.string.update_title, appName),
            color = colors.onHero,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.semantics { heading() },
        )
        if (update != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                Box(
                    Modifier.clip(CircleShape).background(colors.onHero.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        stringResource(R.string.update_versions, BuildConfig.VERSION_NAME, update.versionName),
                        color = colors.onHero,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (update.apkSize > 0) {
                    Text(stringResource(R.string.update_size, mb(update.apkSize)), color = colors.onHero.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
    Spacer(Modifier.height(Space.l))
}

/** The notes as rows with an icon each - New, Fixed, Faster - collapsed when long. */
@Composable
fun Notes(update: AppUpdate) {
    val sections = remember(update.notes) { ReleaseNotes.parse(update.notes) }
    var expanded by rememberSaveable(update.versionCode) { mutableStateOf(false) }
    val colors = AppTheme.colors

    if (sections.isEmpty()) {
        Text(stringResource(R.string.update_notes_empty), style = MaterialTheme.typography.bodyMedium, color = colors.muted)
        return
    }
    val lines = sections.sumOf { it.bullets.size + it.paragraphs.size + 1 }
    val shown = if (expanded || lines <= 7) sections else sections.take(2)

    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        shown.forEach { section ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                Box(
                    Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(colors.brandSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(iconFor(section.kind), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    section.heading?.let {
                        Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                    }
                    section.paragraphs.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.muted) }
                    val bullets = if (expanded || lines <= 7) section.bullets else section.bullets.take(3)
                    bullets.forEach { line ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                            Text("•", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                            Text(line, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        if (!expanded && lines > 7) {
            TextButton(onClick = { expanded = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.update_show_all), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun iconFor(kind: NotesKind): ImageVector = when (kind) {
    NotesKind.New -> AppIcons.Sparkles
    NotesKind.Fixed -> AppIcons.Check
    NotesKind.Faster -> AppIcons.Zap
    NotesKind.Other -> AppIcons.Info
}

/** The Update button, become its own progress bar. */
@Composable
private fun ProgressBar(stage: UpdateStage.Downloading) {
    val primary = MaterialTheme.colorScheme.primary
    val fraction by animateFloatAsState(stage.fraction, tween(350), label = "download")
    val percent = (stage.fraction * 100).toInt()
    val announce = stringResource(R.string.update_progress_a11y, quartile(stage.fraction))
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AppTheme.colors.brandSoft)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = announce
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(primary))
        Text(
            stringResource(R.string.update_progress, mb(stage.bytes), mb(stage.total), percent),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
            color = if (fraction > 0.5f) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
    Text(stringResource(R.string.update_downloading), style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun Ready(manager: UpdateManager, appName: String, busy: Boolean) {
    val view = LocalView.current
    LaunchedEffect(Unit) { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    val good = AppTheme.colors.good
    Text(stringResource(R.string.update_ready), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AppTheme.colors.soft(good)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Icon(AppIcons.ShieldCheck, contentDescription = null, tint = good, modifier = Modifier.size(22.dp))
        Text(stringResource(R.string.update_verified, appName), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
    PrimaryButton(stringResource(R.string.update_install), manager::install, busy = busy)
    Text(stringResource(R.string.update_will_close, appName), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.muted)
    if (busy) Text(stringResource(R.string.update_installing), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.muted)
}

/** Android asks once; show what they will see, then take them there. */
@Composable
private fun AllowInstalls(manager: UpdateManager, appName: String) {
    val context = LocalContext.current
    val colors = AppTheme.colors
    // Coming back from the system page carries on by itself.
    LifecycleResumeEffect(Unit) {
        manager.continueAfterPermission()
        onPauseOrDispose { }
    }
    Text(stringResource(R.string.update_allow_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(stringResource(R.string.update_allow_body, appName), style = MaterialTheme.typography.bodyMedium, color = colors.muted)
    // A small drawing of the system switch.
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.update_allow_switch), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = true, onCheckedChange = null)
    }
    PrimaryButton(stringResource(R.string.update_continue), {
        try {
            context.startActivity(manager.installer.permissionIntent())
        } catch (e: ActivityNotFoundException) {
            manager.install()
        }
    })
}

@Composable
private fun Failure(manager: UpdateManager, stage: UpdateStage.Failed, appName: String, forced: Boolean, onNotNow: () -> Unit) {
    val critical = AppTheme.colors.critical
    val message = when (stage.reason) {
        UpdateStage.Reason.Offline -> stringResource(R.string.update_failed_offline)
        UpdateStage.Reason.Download -> stringResource(R.string.update_failed_download)
        UpdateStage.Reason.Damaged -> stringResource(R.string.update_failed_damaged)
        UpdateStage.Reason.NotOurs -> stringResource(R.string.update_failed_not_ours, appName)
        UpdateStage.Reason.Install -> stringResource(R.string.update_failed_install)
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AppTheme.colors.soft(critical)).padding(14.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Icon(AppIcons.Alert, contentDescription = null, tint = critical, modifier = Modifier.size(20.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
    PrimaryButton(stringResource(R.string.update_try_again), manager::retry)
    if (!forced) NotNow(onNotNow)
}

@Composable
private fun NotNow(onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.update_not_now), color = AppTheme.colors.muted, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Busy(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.muted)
    }
}

/**
 * Once, on the first start of a new version: "You're on 1.2.0 · What's
 * new". Fades after a few seconds; tapping it opens the notes.
 */
@Composable
fun JustUpdatedChip(update: AppUpdate, onOpen: () -> Unit, onDone: () -> Unit) {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(8000)
        visible = false
        delay(400)
        onDone()
    }
    val colors = AppTheme.colors
    AnimatedVisibility(visible, enter = fadeIn(tween(400)), exit = fadeOut(tween(400))) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.m))
                .background(colors.brandSoft)
                .clickable(role = Role.Button, onClick = onOpen)
                .heightIn(min = 48.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            Icon(AppIcons.Sparkles, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(
                stringResource(R.string.update_just_updated, update.versionName),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Icon(AppIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        }
    }
}

/** The notes of the version now running, after "What's new". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewSheet(update: AppUpdate, onClose: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = Space.l),
            verticalArrangement = Arrangement.spacedBy(Space.l),
        ) {
            Text(
                stringResource(R.string.update_whats_new, update.versionName),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.semantics { heading() },
            )
            Notes(update)
        }
    }
}

/**
 * The Account page's About row: the version, and whether it is the newest.
 * Tapping checks now; with an update waiting, it opens the sheet instead.
 */
@Composable
fun AboutRow(manager: UpdateManager, state: UpdateState, onOpen: () -> Unit) {
    val colors = AppTheme.colors
    val stage = state.stage
    val (line, tone) = when {
        !manager.enabled -> null to colors.muted
        stage is UpdateStage.Checking -> stringResource(R.string.update_checking) to colors.muted
        state.pending && state.latest != null -> stringResource(R.string.update_available_row, state.latest.versionName) to MaterialTheme.colorScheme.primary
        stage is UpdateStage.Failed && stage.update == null -> stringResource(R.string.update_failed_offline) to colors.critical
        state.checkedAt > 0 -> "${stringResource(R.string.update_up_to_date)} · ${stringResource(R.string.update_checked, ago(state.checkedAt))}" to colors.good
        else -> stringResource(R.string.update_never_checked) to colors.muted
    }
    val onSurface = MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.m))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(
                if (manager.enabled) {
                    Modifier.clickable(role = Role.Button, onClickLabel = stringResource(R.string.update_check_now)) {
                        if (state.pending) onOpen() else manager.check()
                    }
                } else {
                    Modifier
                },
            )
            .heightIn(min = 60.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(if (state.pending) colors.brandSoft else onSurface.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (state.pending) AppIcons.Download else AppIcons.Info,
                contentDescription = null,
                tint = if (state.pending) MaterialTheme.colorScheme.primary else onSurface,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (line != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (tone == colors.good) Icon(AppIcons.Check, contentDescription = null, tint = tone, modifier = Modifier.size(13.dp))
                    Text(line, style = MaterialTheme.typography.bodySmall, color = tone)
                }
            }
        }
        if (manager.enabled) {
            if (stage is UpdateStage.Checking) {
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(if (state.pending) AppIcons.ChevronRight else AppIcons.Refresh, contentDescription = null, tint = colors.muted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** The full-screen form, for a build the server has refused as too old. */
@Composable
fun ForcedUpdate(manager: UpdateManager, state: UpdateState, providerName: String, logo: @Composable () -> Unit) {
    LaunchedEffect(Unit) {
        if (state.stage is UpdateStage.None || (state.stage is UpdateStage.Failed && state.stage.update == null)) manager.check()
    }
    Column(Modifier.fillMaxWidth().padding(vertical = Space.l)) {
        UpdatePanel(manager, state, providerName, logo, forced = true, onNotNow = {})
    }
}
