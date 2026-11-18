package dev.pixelhome

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.sendspindroid.sendspin.pairing.PairingCodeFormat
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1280dp-h800dp-land")
class SendspinPairingPromptTest {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val screens = SendspinPairingScreensRule()

    @Test fun codePreservesLeadingZerosAndDisappearsWhenAttemptEnds() {
        val code = mutableStateOf<String?>("001234")
        launchPairingScreen { SendspinPairingPrompt(code.value, PairingCodeFormat.DIGITS, false, {}, { code.value = null }) }
        compose.onNodeWithText("001234").assertIsDisplayed()
        compose.onNodeWithText("Allow pairing").assertDoesNotExist()
        compose.onNodeWithText("Cancel pairing").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.onNodeWithText("001234").assertDoesNotExist()
        compose.onNodeWithText("Sendspin pairing").assertDoesNotExist()
    }

    @Test fun heldAttemptRequiresExplicitAllowAction() {
        var allowed = false
        launchPairingScreen { SendspinPairingPrompt(null, PairingCodeFormat.DIGITS, true, { allowed = true }, {}) }
        assertFalse(allowed)
        compose.onNodeWithText("Allow pairing").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        assertTrue(allowed)
    }

    @Test fun qrSessionIsRenderedAsAnImage() {
        launchPairingScreen { SendspinPairingPrompt("SP:1test", PairingCodeFormat.QR_CODE, false, {}, {}) }
        compose.onNodeWithContentDescription("Sendspin session pairing QR").assertIsDisplayed()
        compose.onNodeWithText("SP:1test").assertDoesNotExist()
    }
}

class SendspinPairingTestActivity : androidx.activity.ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { androidx.compose.material3.MaterialTheme { screen() } }
    }
    companion object { var screen: @androidx.compose.runtime.Composable () -> Unit = {} }
}

private val pairingScreens = mutableListOf<org.robolectric.android.controller.ActivityController<SendspinPairingTestActivity>>()
private fun launchPairingScreen(content: @androidx.compose.runtime.Composable () -> Unit) {
    SendspinPairingTestActivity.screen = content
    pairingScreens += org.robolectric.Robolectric.buildActivity(SendspinPairingTestActivity::class.java).setup()
}
class SendspinPairingScreensRule : org.junit.rules.ExternalResource() {
    override fun after() {
        pairingScreens.forEach { it.pause().stop().destroy() }
        pairingScreens.clear()
        SendspinPairingTestActivity.screen = {}
    }
}
