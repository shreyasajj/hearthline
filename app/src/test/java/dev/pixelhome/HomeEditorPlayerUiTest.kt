package dev.pixelhome

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w1280dp-h800dp-land")
class HomeEditorPlayerUiTest {
    @get:Rule val compose=createEmptyComposeRule()
    private var controller: ActivityController<SendspinPairingTestActivity>?=null
    private val vm=HubViewModel().also { it.attachAccessCheck { true } }
    private fun show(readOnly: Boolean=false) {
        val receiver=SendspinReceiver(ApplicationProvider.getApplicationContext<Application>())
        SendspinPairingTestActivity.screen={
            CompositionLocalProvider(LocalSendspinReceiver provides receiver,LocalDockReadOnly provides readOnly) {
                HomeOverview(vm,LocalDateTime.of(2026,11,18,12,0))
            }
        }
        controller=Robolectric.buildActivity(SendspinPairingTestActivity::class.java).setup()
    }
    @After fun close() { controller?.pause()?.stop()?.destroy(); SendspinPairingTestActivity.screen={} }
    @Test fun editorOpensWithAnActionableMessageBeforePairingAndCanBeCancelled() {
        show()
        compose.onNodeWithText("Edit Home").performClick()
        compose.onNodeWithText("Pair this tablet with hearthline-ha to edit Home. Legacy connections can be configured in Home Assistant.").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        assertFalse(vm.editorOpen)
    }
    @Test fun sendspinOnlyPickerRemovesTheHaFallbackAndShowsReceiverStatus() {
        show()
        compose.onNodeWithText("Fallback: ${vm.player!!.name}").performClick()
        compose.onNodeWithText("Sendspin only").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        assertEquals("",vm.selectedPlayer)
        compose.onNodeWithText("Sendspin receiver").assertIsDisplayed()
        compose.onNodeWithText("Fallback: Sendspin only").assertIsDisplayed()
    }
    @Test fun lockedDockDoesNotOfferTheEditor() {
        show(readOnly=true)
        compose.onNodeWithText("Edit Home").assertDoesNotExist()
    }
}
