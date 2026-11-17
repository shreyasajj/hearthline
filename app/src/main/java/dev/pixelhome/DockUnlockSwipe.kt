package dev.pixelhome

import kotlin.math.abs

/** Coordinates are window pixels. Navigation-bar gestures remain owned by Android. */
internal fun isDockUnlockSwipe(startX: Float,startY: Float,x: Float,y: Float,height: Float,density: Float): Boolean {
    if(!listOf(startX,startY,x,y,height,density).all {it.isFinite()} || density<=0 || height<=0) return false
    val upward=startY-y
    return startY in (height-96*density)..height && upward>=72*density && upward>abs(x-startX)*1.5f
}
