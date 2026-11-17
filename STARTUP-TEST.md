# Dock, Home and lock-screen setup — Hearthline v0.7.2

Use `install/Hearthline-preview.apk` for a separate installation that preserves the existing Pixel Home app. Signing/update limits are recorded in VALIDATION.md. The app never reads your PIN or removes Android authentication.

## One-page overview

Home and the main locked dock dashboard share a fixed overview. Landscape places favorites and scenes beside music; portrait puts music below them. Up to four favorites are visible at normal tablet text size. At larger text sizes, **All** opens the remaining favorites without squeezing touch targets. Tap a card for full device details. Extra scenes are available through the count button. Only these explicit detail sheets scroll.

The locked overview starts with Good morning and fills the screen without a dock toolbar, unlock/dismiss buttons or status bar. Android's bottom navigation remains visible. Swipe up from the bottom edge to reach Android authentication; screen-reader users can use the dashboard's **Unlock tablet** custom action. Controls remain read-only until Android authenticates. Android Back dismisses the dock to the normal lock screen; the existing rearm/dock eligibility policies still apply. End an assumed-dock test from Settings after unlocking. The music-visibility setting still controls the locked overview.

## Dock detection and protected view

In Settings → Startup & lock screen:

1. Enable **Show over lock screen**.
2. In normal mode, a confirmed physical dock connection is mandatory. The old dock-only opt-out is ignored and migrated to on.
3. Select **Automatic dock signals**. Android dock events, dock power or an audio device explicitly classified as a dock count as evidence. Ordinary AC/USB/wireless charging does not. The observed tablet exposed a digital dock audio device while battery data only reported AC.
4. If your OS cannot confirm docking, the protected view stays hidden. A manual undocked setting can suppress it; the temporary local test below is the only explicit simulation. Old forced-docked settings migrate back to Automatic.
5. Configure **Show dock view again on next lock**, **Keep dock view awake while locked**, and **Show music information while locked** to your preference. Defaults are rearm on, locked keep-awake off, music information on. All preferences persist.

While unlocked, normal controls and the Android tablet remain usable. On lock, the main dock dashboard appears immediately with home controls disabled. Show main dashboard while locked can be turned off for the private clock/music view. Undocking or losing confirmation hides the protected view. A bottom-edge swipe invokes Android authentication. Android Back returns to the normal keyguard and suppresses the current lock cycle. With rearm enabled, unlocking/locking again or a new screen-off cycle re-arms the dock view; with it disabled, use **Resume dismissed dock view** in Settings.

The normal foreground/Home activity path requires no overlay permission. It only appears where Android considers Pixel Home the relevant activity.

## Test without a dock

While unlocked, tap **Assume docked for testing** in Settings → Startup & lock screen. It now opens the normal Home interface immediately, with a small **Assumed dock · test** badge. It no longer replaces the interface with a test-status page.

The lease lasts ten minutes, survives activity recreation, and ends with End dock test in Settings or process restart. It is shared with the explicitly enabled lock watcher, so return from another app can be tested without real docking after Android overlay/notification permissions are allowed. Physical observations and HA data remain unchanged. Normal off-dock return is denied once the lease ends.

**Preview locked dock dashboard** shows the same main layout with read-only controls while you are still unlocked, for visual checks. Android Back exits preview; a screen-reader Exit preview action is also available. This preview does not pretend that Android is locked or send any device commands.

## Immediate lock presentation

**Show main dashboard while locked** defaults on for the requested dock experience. It exposes configured device/music information above keyguard, with switches, sliders, scene buttons, transport and speaker selection disabled. Settings and assistant answers stay concealed. Turn it off to retain the private clock/music presentation. Swipe up from the bottom to invoke Android authentication on the main overview; the private clock retains its unlock button.

**Wake dock display immediately on lock** defaults on. Eligible lock entry resets the ambient idle timer and requests Android's turn-screen-on behavior; the dim black clock comes later, after inactivity. Android/OEM background launch policy can still limit a return request. Foreground locked rendering does not require overlay permission. Returning from another app requires the watcher setup below.

## Return automatically after locking from another app

This additional behavior requires explicit setup. Assume docked also starts this setup for the requested lock-return test, using Android’s notification prompt when needed.

1. Open the updated build while unlocked.
2. Tap **Grant automatic-return permission**, then grant Android's **Display over other apps** permission and return to Pixel Home.
3. Enable **Automatically return on lock from other apps**. Allow notifications when Android asks. This also enables the protected lock-screen view.
4. A foreground notification indicates that the local dock/lock watcher is running. Its **Pause auto-return** action stops monitoring. Disabling Show over lock screen also disables automatic return.

The monitor checks lock changes promptly (public broadcasts with short settling checks and a 250 ms live watcher) and rechecks real keyguard state immediately before requesting an activity return. It never opens Pixel Home during an unlocked session. It only makes one attempt per eligible lock cycle, respects dismissal, and retains Android authentication. It does not capture audio, contact Google services, or push telemetry to HA. Hearthline telemetry is separately opt-in and foreground-only; see HEARTHLINE-SETUP.md.

Android/OEM policy may still block a background activity request; the notification's explicit Open action is the fallback. Force-stop/process death ends monitoring; reopen the app to resume the configured watcher. This build does not add boot auto-start, request accessibility/device-admin access, or install itself as Home. Screen wake is controlled by the setting above. The updated permission/service path passed an actual locked-return and dismissal check on the Pixel Tablet; owner unlock/relock and physical dock attach/detach remain separate checks.

Primary Android references: [activity launch restrictions](https://developer.android.com/guide/components/activities/secure-bal), [foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use), [dock audio device API](https://developer.android.com/reference/android/media/AudioDeviceInfo#TYPE_DOCK).

## Default Home

For dock-only use with normal tablet navigation, keep your usual Android launcher as the default Home app. Selecting Pixel Home as Home makes every Home gesture return to Pixel Home even while unlocked or undocked; it is independent of dock presentation.

Settings → Make Pixel Home my Home app opens Android's normal role chooser. Home selection is per user/profile and persists through restart. Complete Android's first unlock normally. Use Change default Home app to restore your usual launcher. No boot receiver bypasses the first unlock.

Managed Hearthline pairing remembers a scoped tablet credential using Android Keystore; legacy account tokens remain session-only. See HEARTHLINE-SETUP.md. Demo fixtures are labeled.

## Manual device acceptance

- Confirm actual dock attachment/removal signals on the installed OS; do not treat a USB charger as a dock.
- Undock while locked: Android keyguard must cover the app.
- Dismiss the dock view, authenticate using Android, and use another app. No automatic return while unlocked.
- Lock again while docked: when the optional permission/service is enabled and the OS permits, the protected clock returns. Device controls must remain disabled; settings/assistant answers must stay concealed. Dashboard information may be visible according to the locked-layout preference.
- Cancel authentication: remain locked. Complete authentication: normal app/tablet use resumes.
- Disable rearm: dismissal persists until Resume dismissed dock view. Disable automatic return or pause the notification: no background return attempts.
- Test notification/overlay permission revocation, force-stop and process restart; do not claim continuous monitoring when the process is absent.
- Regress Home selection, music → idle → Home navigation, sensitive action confirmation, large fonts, rotation, and foreground-only assistant delivery.

The actual return from the normal launcher, locked dashboard controls, dismissal and another screen-off/on during the same locked cycle passed on the tablet. Owner-authenticated unlock/relock and physical attach/detach remain manual acceptance checks.
