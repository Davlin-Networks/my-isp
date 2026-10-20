package run.sparo.myisp.ui.screens

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import run.sparo.myisp.AppContainer
import run.sparo.myisp.R
import run.sparo.myisp.ui.AppState
import run.sparo.myisp.ui.components.Banner
import run.sparo.myisp.ui.components.Muted
import run.sparo.myisp.ui.components.PrimaryButton
import run.sparo.myisp.ui.theme.AppIcons
import run.sparo.myisp.ui.theme.AppTheme
import run.sparo.myisp.ui.theme.Space

/** The fewest words the server takes; below it Send stays off and says why. */
private const val MIN_LENGTH = 5

/** What each of the server's categories looks like here. An unknown one still works, plainly. */
private data class Topic(
    val icon: () -> ImageVector,
    @StringRes val hint: Int,
    @StringRes val example: Int,
    @ArrayRes val phrases: Int,
)

private val TOPICS = mapOf(
    "no_internet" to Topic({ AppIcons.Wifi }, R.string.ask_hint_no_internet, R.string.ask_example_no_internet, R.array.ask_phrases_no_internet),
    "slow" to Topic({ AppIcons.Usage }, R.string.ask_hint_slow, R.string.ask_example_slow, R.array.ask_phrases_slow),
    "billing" to Topic({ AppIcons.Wallet }, R.string.ask_hint_billing, R.string.ask_example_billing, R.array.ask_phrases_billing),
    "other" to Topic({ AppIcons.Help }, R.string.ask_hint_other, R.string.ask_example_other, R.array.ask_phrases_other),
)

/** A request's topic as an icon, wherever requests are listed. */
internal fun topicIcon(key: String?): ImageVector = TOPICS[key]?.icon?.invoke() ?: AppIcons.Help

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewTicketScreen(vm: SupportVm, state: AppState, app: AppContainer, onOpened: (Long) -> Unit, onClose: () -> Unit) {
    val support by vm.state.collectAsStateWithLifecycle()
    val categories = support.list?.categories.orEmpty()
    var category by rememberSaveable { mutableStateOf(categories.keys.firstOrNull().orEmpty()) }
    var body by rememberSaveable { mutableStateOf("") }
    // The ISP by name, as its notifications are titled - not the portal's title.
    val isp = state.provider?.name.orEmpty()
    val topic = TOPICS[category]
    val ready = category.isNotEmpty() && body.trim().length >= MIN_LENGTH

    PageScaffold(stringResource(R.string.help_new_request), onClose) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                // The keyboard, less the navigation bar the page already keeps clear of.
                .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars)),
        ) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.l, vertical = Space.s),
                verticalArrangement = Arrangement.spacedBy(Space.l),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Text(stringResource(R.string.ask_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Muted(stringResource(R.string.ask_subtitle, isp))
                }

                // The topic: four cards, two by two.
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Text(stringResource(R.string.ask_topic), style = MaterialTheme.typography.titleSmall)
                    categories.entries.chunked(2).forEach { pair ->
                        // Both cards of a row as tall as the taller one.
                        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                            pair.forEach { (key, label) ->
                                TopicCard(
                                    label = label,
                                    topic = TOPICS[key],
                                    selected = category == key,
                                    onClick = { category = key },
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }

                // The message, with phrases a tap adds to it.
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Text(stringResource(R.string.ask_details), style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it.take(2000) },
                        placeholder = { topic?.let { Text(stringResource(it.example)) } },
                        minLines = 5,
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        supportingText = {
                            Text(
                                stringResource(R.string.ask_count, body.length),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.End,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    topic?.let { t ->
                        // Each phrase once: one already in the message leaves the row.
                        val phrases = stringArrayResource(t.phrases).filterNot { body.contains(it, ignoreCase = true) }
                        if (phrases.isNotEmpty()) {
                            Text(stringResource(R.string.ask_add), style = MaterialTheme.typography.labelMedium, color = AppTheme.colors.muted)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                                phrases.forEach { phrase ->
                                    SuggestionChip(
                                        onClick = { body = withPhrase(body, phrase).take(2000) },
                                        label = { Text(phrase) },
                                        icon = { Icon(AppIcons.Plus, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        shape = RoundedCornerShape(50),
                                        colors = SuggestionChipDefaults.suggestionChipColors(iconContentColor = MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.heightIn(min = 40.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                support.actionError?.let { Banner(it, "error") }
            }

            // Send, always in reach: pinned above the keyboard, not at the end of the scroll.
            HorizontalDivider(color = AppTheme.colors.line)
            Column(
                Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.m),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                PrimaryButton(
                    stringResource(R.string.ask_send, isp),
                    { vm.create(category, body.trim(), onOpened) },
                    enabled = ready,
                    busy = support.busy,
                )
                val note = when {
                    body.isNotBlank() && !ready -> stringResource(R.string.ask_too_short)
                    app.push.enabled -> stringResource(R.string.ask_notify)
                    else -> null
                }
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.muted, textAlign = TextAlign.Center) }
            }
        }
    }
}

/** One topic: an icon, its name and a line about it. The chosen one wears the provider's colour. */
@Composable
private fun TopicCard(label: String, topic: Topic?, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .heightIn(min = 112.dp)
            .clip(shape)
            .background(if (selected) primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainer)
            .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) primary else AppTheme.colors.line), shape)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(Space.m),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(if (selected) primary else primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    topic?.icon?.invoke() ?: AppIcons.Help,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.onPrimary else primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                topic?.let { Text(stringResource(it.hint), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.muted) }
            }
        }
        if (selected) {
            Box(
                Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape).background(primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** A tapped phrase, added as its own sentence. */
private fun withPhrase(body: String, phrase: String): String {
    val text = body.trimEnd()
    return when {
        text.isEmpty() -> phrase
        text.endsWith('.') || text.endsWith('!') || text.endsWith('?') -> "$text $phrase"
        else -> "$text. $phrase"
    }
}
