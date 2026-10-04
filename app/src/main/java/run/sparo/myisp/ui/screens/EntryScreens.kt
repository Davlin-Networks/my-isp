package run.sparo.myisp.ui.screens

import android.content.Intent
import android.net.Uri
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
    val scope = rememberCoroutineScope()
    var username by rememberSaveable { mutableStateOf(app.store.lastUsername.orEmpty()) }
    var password by rememberSaveable { mutableStateOf("") }
    var forgotNote by remember { mutableStateOf<String?>(null) }

    EntryColumn {
        RemoteLogo(provider.branding.logoUrl, app, 72.dp)
        Text(provider.branding.portalTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Muted("Sign in with your username and portal password - the same as your provider's web portal.", Modifier.fillMaxWidth())
        state.notice?.let { Banner(it, "warning") }
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.signIn(username, password) }),
            modifier = Modifier.fillMaxWidth(),
        )
        state.signInError?.let { Banner(it, "error") }
        PrimaryButton("Sign in", { vm.signIn(username, password) }, busy = state.signInBusy)
        TextButton(onClick = {
            if (username.isBlank()) {
                forgotNote = "Type your username first, then tap Forgot password."
            } else {
                scope.launch { forgotNote = vm.forgotPassword(username) }
            }
        }) { Text("Forgot password?") }
        forgotNote?.let { Banner(it) }
        if (AppConfig.fixedSlug == null) {
            TextButton(onClick = vm::chooseAnotherProvider) { Text("Not ${provider.name}? Choose your provider") }
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
