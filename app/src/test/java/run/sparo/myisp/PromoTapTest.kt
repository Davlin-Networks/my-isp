package run.sparo.myisp

import org.junit.Assert.assertEquals
import org.junit.Test
import run.sparo.myisp.data.Promo
import run.sparo.myisp.ui.screens.PromoTap

/** What tapping one of the ISP's promotions does. */
class PromoTapTest {
    private fun promo(action: String, link: String? = null, plan: Long? = null) =
        Promo(1, "https://x.test/a.webp", "Upgrade", action, link, plan)

    @Test
    fun aPlanOpensTheRenewSheetOnIt() {
        assertEquals(PromoTap.Plan(7), PromoTap.of(promo("plan", plan = 7)))
    }

    @Test
    fun aLinkOpensOnlyWhenItIsAWebLink() {
        assertEquals(PromoTap.Link("https://juja.example/offer"), PromoTap.of(promo("link", link = "https://juja.example/offer")))
        assertEquals(PromoTap.None, PromoTap.of(promo("link", link = "intent://evil#Intent;end")))
        assertEquals(PromoTap.None, PromoTap.of(promo("link")))
    }

    @Test
    fun anythingUnknownIsJustAPicture() {
        assertEquals(PromoTap.None, PromoTap.of(promo("none")))
        assertEquals(PromoTap.None, PromoTap.of(promo("video", link = "https://x.test")))
        assertEquals(PromoTap.None, PromoTap.of(promo("plan")))
    }
}
