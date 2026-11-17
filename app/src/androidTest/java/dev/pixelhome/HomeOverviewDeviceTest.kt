package dev.pixelhome

import android.app.KeyguardManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.InputDevice
import android.view.MotionEvent
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Actual MainActivity layout and optional native authentication prompt; never enters credentials. */
@RunWith(AndroidJUnit4::class)
class HomeOverviewDeviceTest {
    private fun flatten(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> =
        if(root==null) emptyList() else listOf(root)+(0 until root.childCount).flatMap { flatten(root.getChild(it)) }
    @Test fun overviewControlsFitWithoutScrolling() {
        val i=InstrumentationRegistry.getInstrumentation();val context=i.targetContext
        val prefs=context.getSharedPreferences("hub_display",0)
        val args=InstrumentationRegistry.getArguments()
        val overrides=mutableMapOf("dock_suppressed" to false,"dock_dashboard" to true,"show_when_locked" to true,"dock_show_music" to true)
        if(args.containsKey("dark")) overrides["dark"]=args.getString("dark")=="true"
        val previous=overrides.keys.associateWith {if(prefs.contains(it)) prefs.getBoolean(it,false) else null}
        prefs.edit().also {edit -> overrides.forEach { (key,value) -> edit.putBoolean(key,value) }}.commit()
        DockTestRuntime.start(SystemClock.elapsedRealtime())
        try {
            ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)).use { scenario ->
                var nodes=emptyList<AccessibilityNodeInfo>()
                for(attempt in 0..60) {
                    nodes=flatten(i.uiAutomation.rootInActiveWindow)
                    if(nodes.any {it.text?.toString()=="Favorites"}) break
                    Thread.sleep(100)
                }
                assertTrue("Overview missing",nodes.any {it.text?.toString()=="Favorites"})
                assertFalse("Home overview unexpectedly scrolls",nodes.any {it.isScrollable})
                val capture=InstrumentationRegistry.getArguments().getString("capture_name","overview")!!.replace(Regex("[^a-z0-9_-]"),"")
                val bitmap=i.uiAutomation.takeScreenshot()
                File(context.getExternalFilesDir(null),"$capture.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                bitmap.recycle()
                for(description in listOf("Previous track","Play","Next track","Volume")) {
                    val node=nodes.firstOrNull {it.contentDescription?.toString()==description}
                    assertNotNull("Missing visible music control: $description",node)
                    var target=node!!
                    // Icons expose their own visual bounds; measure the containing touch target.
                    while(description!="Volume" && !target.isClickable && target.parent!=null) target=target.parent
                    val rect=Rect();target.getBoundsInScreen(rect)
                    // Material's range node describes its 44dp thumb, inside a 56dp slider container.
                    assertTrue("Clipped control: $description $rect",rect.width()>0 && rect.height() >= (if(description=="Volume") 44 else 56)*context.resources.displayMetrics.density-2)
                }
                val volume=nodes.first {it.contentDescription?.toString()=="Volume"}
                val rootBounds=Rect();i.uiAutomation.rootInActiveWindow.getBoundsInScreen(rootBounds)
                val volumeBounds=Rect();volume.getBoundsInScreen(volumeBounds)
                assertTrue("Volume extends below viewport",rootBounds.contains(volumeBounds))
                val guard=context.getSystemService(KeyguardManager::class.java)
                if(args.getString("expected_locked")=="true") assertTrue("Android must remain locked",guard.isKeyguardLocked || guard.isDeviceLocked)
                if(guard.isKeyguardLocked || guard.isDeviceLocked) {
                    assertFalse("Legacy dock toolbar still visible",nodes.any {it.text?.toString() in listOf("Dock dashboard","Unlock tablet","Dismiss dock")})
                    assertTrue("A switch was enabled on the lock screen",nodes.filter {it.isCheckable}.none {it.isEnabled})
                    assertTrue("A slider was enabled on the lock screen",nodes.filter {it.rangeInfo != null}.none {it.isEnabled})
                }
                // Additional items remain available in a separate details sheet without scrolling the overview.
                val all=nodes.firstOrNull {it.text?.toString()?.matches(Regex("All [0-9]+"))==true && it.isClickable}
                if(all!=null) {
                    all.performAction(AccessibilityNodeInfo.ACTION_CLICK);Thread.sleep(500)
                    val sheet=flatten(i.uiAutomation.rootInActiveWindow)
                    assertTrue("Additional favorites cannot be reached",sheet.any {it.text?.toString()=="All favorites"})
                    i.uiAutomation.injectInputEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK),true)
                    i.uiAutomation.injectInputEvent(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK),true)
                    Thread.sleep(400)
                }
                if(args.getString("verify_swipe")=="true") {
                    assertTrue("Swipe acceptance needs an already locked tablet",guard.isDeviceLocked)
                    val root=Rect();i.uiAutomation.rootInActiveWindow.getBoundsInScreen(root)
                    val density=context.resources.displayMetrics.density
                    val down=SystemClock.uptimeMillis()
                    val edge=args.getString("native_edge")=="true"
                    val startY=root.bottom-(if(edge) 4f else 80f*density)
                    for(step in 0..12) {
                        val event=MotionEvent.obtain(down,down+step*25L,when(step) {0->MotionEvent.ACTION_DOWN;12->MotionEvent.ACTION_UP;else->MotionEvent.ACTION_MOVE},root.exactCenterX(),startY-step*14f*density,0)
                        event.source=InputDevice.SOURCE_TOUCHSCREEN
                        i.uiAutomation.injectInputEvent(event,true);event.recycle()
                        Thread.sleep(25)
                    }
                    var nativePrompt=false
                    for(attempt in 0..40) {
                        val active=i.uiAutomation.rootInActiveWindow
                        if(active?.packageName?.toString()=="com.android.systemui") {nativePrompt=true;break}
                        Thread.sleep(100)
                    }
                    assertTrue("Bottom swipe did not hand control to Android authentication",nativePrompt)
                    assertTrue("Gesture bypassed Android authentication",guard.isDeviceLocked)
                    // Cancel the native prompt, without entering or reading any credential.
                    i.uiAutomation.injectInputEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK),true)
                    i.uiAutomation.injectInputEvent(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK),true)
                }
            }
        } finally {
            DockTestRuntime.end()
            val edit=prefs.edit()
            previous.forEach { (key,value) -> if(value==null) edit.remove(key) else edit.putBoolean(key,value) }
            edit.commit()
        }
    }
}
