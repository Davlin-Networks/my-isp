package run.sparo.myisp.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.sp
import run.sparo.myisp.R
import run.sparo.myisp.ui.theme.AppIcons
import run.sparo.myisp.ui.theme.AppTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import run.sparo.myisp.AppConfig
import run.sparo.myisp.AppContainer
import run.sparo.myisp.ui.AppState
import run.sparo.myisp.ui.AppViewModel
import run.sparo.myisp.ui.Phase
import run.sparo.myisp.ui.components.Banner
import run.sparo.myisp.ui.components.Gap
import run.sparo.myisp.ui.components.Muted
import run.sparo.myisp.ui.components.PrimaryButton
import run.sparo.myisp.ui.components.RemoteLogo

@Composable
private fun EntryColumn(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) { content() }
}

/** Generic build, no ISP yet. The QR code is scanned with the phone's own camera. */
@Composable
fun ChooseProviderScreen(phase: Phase.ChooseProvider, hasProvider: Boolean, vm: AppViewModel) {
    var code by rememberSaveable { mutableStateOf("") }

    EntryColumn {
        Text("Your internet provider", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Muted(
            "Scan your provider's QR code with your phone camera - it opens this app on their account. " +
                "Or type the code they gave you.",
            Modifier.fillMaxWidth(),
        )
        Gap(4.dp)
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("Provider code") },
            placeholder = { Text("e.g. kilimani-fibre") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { vm.lookUpTyped(code) }),
            modifier = Modifier.fillMaxWidth(),
        )
        phase.error?.let { Banner(it, "error") }
        PrimaryButton("Continue", { vm.lookUpTyped(code) }, enabled = code.isNotBlank(), busy = phase.busy)
        if (hasProvider) {
            TextButton(onClick = vm::cancelConfirm) { Text("Back") }
        }
    }
}

/** Before any password: is this really your provider? */
@Composable
fun ConfirmProviderScreen(phase: Phase.ConfirmProvider, app: AppContainer, vm: AppViewModel) {
    val p = phase.provider
    EntryColumn {
        RemoteLogo(p.branding.logoUrl, app, 88.dp)
        Text(p.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        p.branding.tagline?.takeIf { it.isNotBlank() }?.let { Muted(it) }
        Gap(8.dp)
        Text("Is this your internet provider?", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        phase.switchingFrom?.let {
            Banner("You're signed in to $it. Continuing signs you out there first.", "warning")
        }
        PrimaryButton("Yes, continue", vm::confirmProvider)
        OutlinedButton(onClick = vm::cancelConfirm, modifier = Modifier.fillMaxWidth()) { Text("No, go back") }
    }
}

@Composable
fun SignInScreen(state: AppState, app: AppContainer, vm: AppViewModel) {
    val provider = state.provider ?: return
    val colors = AppTheme.colors
    val scope = rememberCoroutineScope()
    var username by rememberSaveable { mutableStateOf(app.store.lastUsername.orEmpty()) }
    var password by rememberSaveable { mutableStateOf("") }
    var show by rememberSaveable { mutableStateOf(false) }
    var forgotNote by remember { mutableStateOf<String?>(null) }
    val needUsername = stringResource(R.string.forgot_need_username)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding().verticalScroll(rememberScrollState())) {
        // The ISP's own header: its gradient, its logo, its name.
        Column(
            Modifier.fillMaxWidth().background(colors.hero).statusBarsPadding().padding(start = 32.dp, end = 32.dp, top = 48.dp, bottom = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (provider.branding.logoUrl != null) {
                RemoteLogo(provider.branding.logoUrl, app, 76.dp)
            } else {
                Box(
                    Modifier.size(76.dp).clip(RoundedCornerShape(22.dp)).background(colors.onHero.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        provider.name.split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() },
                        color = colors.onHero, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
            Text(provider.branding.portalTitle, color = colors.onHero, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(stringResource(R.string.signin_tagline), color = colors.onHero.copy(alpha = 0.88f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }

        Column(
            Modifier
                .fillMaxWidth()
                .offset(y = (-32).dp)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.signin_title), style = MaterialTheme.typography.headlineSmall)
                Muted(stringResource(R.string.signin_body, provider.name))
            }
            state.notice?.let { Banner(it, "warning") }
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(R.string.username)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Username },
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.password)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.signIn(username, password) }),
                trailingIcon = {
                    IconButton(onClick = { show = !show }) {
                        Icon(
                            if (show) AppIcons.EyeOff else AppIcons.Eye,
                            contentDescription = stringResource(if (show) R.string.hide_password else R.string.show_password),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
            )
            state.signInError?.let { Banner(it, "error") }
            PrimaryButton(stringResource(R.string.signin_button), { vm.signIn(username, password) }, busy = state.signInBusy)
            TextButton(
                onClick = {
                    if (username.isBlank()) forgotNote = needUsername
                    else scope.launch { forgotNote = vm.forgotPassword(username) }
                },
                modifier = Modifier.align(Alignment.CenterHorizontally).height(48.dp),
            ) { Text(stringResource(R.string.forgot_password), fontWeight = FontWeight.Bold) }
            forgotNote?.let { Banner(it) }
            if (AppConfig.fixedSlug == null) {
                HorizontalDivider(color = colors.line)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Muted(stringResource(R.string.not_with, provider.name))
                    TextButton(onClick = vm::chooseAnotherProvider, modifier = Modifier.height(48.dp)) {
                        Text(stringResource(R.string.choose_provider), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** The ISP turned the app off, or this build is too old. The web portal always works. */
@Composable
fun UnavailableScreen(phase: Phase.Unavailable) {
    val context = LocalContext.current
    EntryColumn {
        Text(if (phase.outdated) "Update the app" else "Not available in the app", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(phase.message, textAlign = TextAlign.Center)
        phase.portalUrl?.let { url ->
            PrimaryButton("Open the web portal", { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) })
        }
    }
}
