package dev.pixelhome

import org.junit.Assert.*
import org.junit.Test

class DockUnlockSwipeTest {
    @Test fun bottomSwipeRequestsAuthenticationAtEitherDensity() {
        for(d in listOf(1f,2f)) assertTrue(isDockUnlockSwipe(400*d,740*d,405*d,600*d,800*d,d))
    }
    @Test fun tapsAndShortDragsDoNotRequestAuthentication() {
        assertFalse(isDockUnlockSwipe(400f,750f,400f,750f,800f,1f))
        assertFalse(isDockUnlockSwipe(400f,750f,400f,700f,800f,1f))
    }
    @Test fun gesturesInTheDashboardBodyRemainAvailableToDetails() {assertFalse(isDockUnlockSwipe(400f,500f,400f,200f,800f,1f))}
    @Test fun horizontalAndDownwardGesturesDoNotUnlock() {
        assertFalse(isDockUnlockSwipe(400f,750f,600f,650f,800f,1f))
        assertFalse(isDockUnlockSwipe(400f,720f,400f,790f,800f,1f))
    }
    @Test fun invalidCoordinatesDoNotRequestAuthentication() {
        assertFalse(isDockUnlockSwipe(400f,750f,400f,600f,800f,Float.NaN))
        assertFalse(isDockUnlockSwipe(400f,850f,400f,600f,800f,1f))
    }
}
