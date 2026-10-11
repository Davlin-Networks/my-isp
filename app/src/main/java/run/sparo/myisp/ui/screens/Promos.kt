package run.sparo.myisp.ui.screens

import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import run.sparo.myisp.AppContainer
import run.sparo.myisp.R
import run.sparo.myisp.data.ApiException
import run.sparo.myisp.data.Promo
import run.sparo.myisp.ui.components.RemoteImage
import run.sparo.myisp.ui.theme.AppIcons
import run.sparo.myisp.ui.theme.AppTheme
import run.sparo.myisp.ui.theme.Radius

/*
 * The ISP's promotions on Home (docs/home-ads-plan.md): one card below the
 * wallet, never in the Renew sheet or above the plan. Its own promotions,
 * labelled as such - no ad network, no tracking.
 */

/** "Not now" hides the card this long; a new promotion does not bring it back sooner. */
const val PROMOS_HIDE_MS = 7L * 24 * 60 * 60 * 1000

/** What a tap does. Anything the app does not know is a picture. */
sealed interface PromoTap {
    data class Plan(val packageId: Long) : PromoTap
    data class Link(val url: String) : PromoTap
    data object None : PromoTap

    companion object {
        fun of(promo: Promo): PromoTap = when {
            promo.action == "plan" && promo.packageId != null -> Plan(promo.packageId)
            // The server allows only http(s) links; anything else is not opened.
            promo.action == "link" && promo.linkUrl != null &&
                (promo.linkUrl.startsWith("https://") || promo.linkUrl.startsWith("http://")) -> Link(promo.linkUrl)
            else -> None
        }
    }
}

/** This sign-in's promotions. Refreshed with the account, not on a timer of its own. */
class PromoVm(private val app: AppContainer) : ViewModel() {
    private val _promos = MutableStateFlow<List<Promo>>(emptyList())
    val promos: StateFlow<List<Promo>> = _promos

    private val _hidden = MutableStateFlow(app.store.promosHiddenUntil > System.currentTimeMillis())
    val hidden: StateFlow<Boolean> = _hidden

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            try {
                _promos.value = app.api.promos()
            } catch (e: ApiException) {
                // Offline or refused: keep what was shown. A card is never worth an error.
            }
        }
    }

    fun hideForAWeek() {
        app.store.promosHiddenUntil = System.currentTimeMillis() + PROMOS_HIDE_MS
        _hidden.value = true
    }
}

/**
 * The carousel: cards shaped like the ISP's images, a "From {ISP}" chip, the title on a soft scrim,
 * page dots. Advances every six seconds - not while touched, not while the
 * app is in the background, not with TalkBack on - and swipes by hand.
 */
@Composable
fun PromoCarousel(promos: List<Promo>, providerName: String, app: AppContainer, onTap: (PromoTap) -> Unit, onHide: () -> Unit) {
    if (promos.isEmpty()) return
    val pager = rememberPagerState { promos.size }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val colors = AppTheme.colors
    // The card takes the shape of the first image, between 3:2 and 2.7:1:
    // a photo keeps its shape, a 3:1 portal strip loses only its outer edges.
    var aspect by remember { mutableStateOf(2.7f) }
    val talkBack = remember {
        (context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager)?.isTouchExplorationEnabled == true
    }

    if (promos.size > 1 && !talkBack) {
        LaunchedEffect(promos.size) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(6000)
                    if (!pager.isScrollInProgress) pager.animateScrollToPage((pager.currentPage + 1) % promos.size)
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box {
            HorizontalPager(
                state = pager,
                pageSpacing = 12.dp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m)),
            ) { page ->
                val promo = promos[page]
                val tap = PromoTap.of(promo)
                val label = stringResource(R.string.promo_a11y, providerName, promo.title ?: "")
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspect)
                        .clip(RoundedCornerShape(Radius.m))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .then(if (tap != PromoTap.None) Modifier.clickable(role = Role.Button) { onTap(tap) } else Modifier)
                        .semantics(mergeDescendants = true) { contentDescription = label },
                ) {
                    RemoteImage(promo.imageUrl, app, Modifier.fillMaxSize(), onSize = { w, h ->
                        if (page == 0 && w > 0 && h > 0) aspect = (w.toFloat() / h).coerceIn(1.5f, 2.7f)
                    })
                    if (!promo.title.isNullOrBlank()) {
                        Box(
                            Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f))))
                                .padding(start = 14.dp, end = 14.dp, top = 24.dp, bottom = 12.dp),
                        ) {
                            Text(promo.title, color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2)
                        }
                    }
                }
            }
            // Like an ad network's choices badge: tiny, faint, in the corner.
            PromoMenu(Modifier.align(Alignment.TopStart), onHide)
        }
    }
}

@Composable
private fun PromoMenu(modifier: Modifier, onHide: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        // The tap area stays 40dp; only what is drawn is small and faint.
        Box(
            Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = stringResource(R.string.promo_more)) { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(18.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Info, contentDescription = stringResource(R.string.promo_more), tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.promo_hide)) }, onClick = {
                open = false
                onHide()
            })
        }
    }
}
