package run.sparo.myisp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import android.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import run.sparo.myisp.ui.theme.ThemeMode
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import run.sparo.myisp.ui.AppViewModel
import run.sparo.myisp.ui.Phase
import run.sparo.myisp.ui.screens.ChooseProviderScreen
import run.sparo.myisp.ui.screens.ConfirmProviderScreen
import run.sparo.myisp.ui.screens.SignInScreen
import run.sparo.myisp.ui.screens.SignedInScreen
import run.sparo.myisp.ui.screens.UnavailableScreen
import run.sparo.myisp.ui.theme.MyIspTheme

class MainActivity : ComponentActivity() {
    private val app get() = (application as MyIspApp).container

    private val vm: AppViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(app) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // The link that opened the app, if any - only on a fresh start, not
        // when the activity is recreated with the same intent (rotation).
        vm.start(if (savedInstanceState == null) intent?.data else null)

        setContent {
            val state by vm.state.collectAsStateWithLifecycle()

            // While confirming a new ISP, show it in its own colours.
            val shown = (state.phase as? Phase.ConfirmProvider)?.provider ?: state.provider

            // The status and navigation bar icons follow the app's theme, not
            // only the phone's: dark icons on the light theme, light on dark.
            val dark = when (state.themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            MyIspTheme(shown?.branding?.primaryColor, state.themeMode) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when (val phase = state.phase) {
                        Phase.Starting -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        is Phase.ChooseProvider -> ChooseProviderScreen(phase, hasProvider = state.provider != null, vm = vm)
                        is Phase.ConfirmProvider -> ConfirmProviderScreen(phase, app, vm)
                        Phase.SignIn -> SignInScreen(state, app, vm)
                        Phase.Ready -> {
                            // The signed-in screens' ViewModels live in this sign-in's own store.
                            val owner = remember(state.session) {
                                object : ViewModelStoreOwner {
                                    override val viewModelStore = vm.sessionStore
                                }
                            }
                            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                                SignedInScreen(state, app, vm)
                            }
                        }
                        is Phase.Unavailable -> UnavailableScreen(phase)
                    }
                }
            }
        }
    }

    /** A QR code scanned while the app is already open. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let(vm::onLink)
    }
}
