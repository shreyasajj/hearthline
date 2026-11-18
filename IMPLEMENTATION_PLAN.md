# Hearthline roadmap

Current version: **0.7.5** (Android app, package `dev.pixelhome`; integration hearthline-ha 0.7.5). Last reviewed against the source on November 18, 2026.

This file tracks what is built, what is built but not yet proven on real hardware, and what is still to build. Home Assistant stays the source of truth, and Android authentication is never bypassed. Update this file in the same change that ships or starts a milestone.

## Shipped

| Version | What it added | Reference |
| --- | --- | --- |
| 0.3 | Native semantic answer rendering and the Home Assistant display LLM API | [DISPLAY-SETUP.md](DISPLAY-SETUP.md), [ASSISTANT-PROMPT.md](ASSISTANT-PROMPT.md), `display-contract/response.schema.json` |
| 0.4 | Journey, weather and home-status templates, alongside the generic answer layouts | [TEMPLATE-SETUP.md](TEMPLATE-SETUP.md) |
| 0.5 | Native Sendspin receiver with encrypted pairing | [SENDSPIN-SETUP.md](SENDSPIN-SETUP.md) |
| 0.6 | Ambient clock, dimming, measured-light dark theme, optional offline "wake up" phrase and local face wake | [AMBIENT-SETUP.md](AMBIENT-SETUP.md) |
| 0.7.0 | Integration-managed pairing with a scoped credential stored under Android Keystore encryption; opt-in foreground telemetry | [HEARTHLINE-SETUP.md](HEARTHLINE-SETUP.md), [TABLET-PROTOCOL.md](TABLET-PROTOCOL.md) |
| 0.7.1 | One-page Home and locked-dock overview | [STARTUP-TEST.md](STARTUP-TEST.md) |
| 0.7.2 | Full-screen locked dock with swipe-up unlock | [STARTUP-TEST.md](STARTUP-TEST.md) |
| 0.7.3 | Tablet discovery for Home Assistant: mDNS advertisement and a read-only identity responder on TCP 8787 | [README.md](README.md#tablet-discovery-for-home-assistant) |
| 0.7.4 | Push stream from HA (live state, polling fallback), telemetry sampled every second and isolated from the dashboard connection, discovery pauses once paired | [TABLET-PROTOCOL.md](TABLET-PROTOCOL.md#push-stream-ha-074--tablet-074) |
| 0.7.5 | Pairing with a one-time 8-digit code from the HA entry options; the long-lived token moves under Advanced | [TABLET-PROTOCOL.md](TABLET-PROTOCOL.md#pairing-with-a-code-ha-075--tablet-075) |

The "wake up" phrase only wakes the display. It is not a general voice pipeline.

## Built, awaiting live acceptance

The code and automated tests exist, but these have not been proven end to end. [VALIDATION.md](VALIDATION.md) ("Not executed / remaining acceptance") is the authoritative list; do not copy it here. In summary:

- [ ] Clean Home Assistant deployment plus APK pairing over Android-trusted HTTPS.
- [ ] Live configuration delivery, edited player order, account-authorized actions, app restart with Keystore restore, and revoke/unpair.
- [ ] Physical dock attach/detach and owner-authenticated unlock/relock.
- [ ] Sendspin playback and ambient sleep/wake after the 0.7.x layout changes.
- [ ] Code pairing against a live HA server served over Android-trusted HTTPS.
- [ ] Push stream against a live HA server, including behind a reverse proxy, and the 10-second unavailability after leaving the foreground.
- [ ] Discovery across VLANs (mDNS reflection, HA-to-tablet TCP 8787) and authenticated onboarding from a discovered tablet.
- [ ] TalkBack pass, including the **Unlock tablet** accessibility action.
- [ ] GrapheneOS. Only stock Android 16 on a Pixel Tablet has been observed.
- [ ] Home Assistant versions other than 2026.10.4.

## Not built yet

Listed in suggested order. Each item has a "done when" so it can be handed to a contributor or an agent as a brief.

- [ ] **General voice pipeline.** Wake word, microphone capture, transcription, speech playback, and interruption/echo handling.
  Done when: a spoken request on the tablet reaches the Home Assistant conversation agent, the answer renders through the existing display contract and is spoken back, and speaking over playback stops it. Runs only while the app is visible; no new background service.
- [ ] **Music Assistant library and queue browsing.** The Music page currently plays configured shortcuts only.
  Done when: the user can search the library, start an item on the selected player, and view and reorder the queue, with the same locked-control restrictions as the rest of the dashboard.
- [ ] **Dock audio output registration.** Display delivery does not make the dock a Music Assistant player.
  Done when: the docked tablet appears as a selectable player and audio routes to the dock speaker when attached, falling back cleanly when detached.
- [ ] **Room and camera screens.**
  Done when: a room view lists that area's entities from the HA entry's configuration, and a camera view shows a live stream that stops when the app is paused or the device is locked.
- [ ] **Calendar source.** Weather and home status are already selectable per HA entry; calendar is not.
  Done when: a calendar entity chosen in the HA entry's options appears on the overview or as a template, with credentials staying on HA.

## Voice and Music Assistant review (November 17, 2026)

**Voice today.** The only speech feature is the offline "wake up" phrase: Vosk, bundled English model, microphone open only while the ambient clock is showing, nothing recorded or sent. Answers from the HA LLM display API already carry `spoken_text`, but the tablet never speaks it, and there is no path from the tablet microphone to HA Assist.

- [ ] **Speak `spoken_text` locally** (small, independent of the pipeline below). Use Android `TextToSpeech` when an answer renders, stop it on dismiss, lock or pause, and add a Settings toggle. The display contract already advertises a `tts` capability.
- [ ] **Assist pipeline through the integration.** The scoped tablet credential cannot call HA's `assist_pipeline/run` WebSocket, which needs an account token. Add a tablet endpoint in hearthline-ha that runs the pipeline as the entry's owner (the same pattern as `command`), streaming 16 kHz PCM up and pipeline events down. On the tablet, reuse the wake-phrase recogniser to trigger capture while visible. Interruption and echo handling come after this works end to end.
- [ ] **APK size.** The debug APK is about 130 MB, mostly the Vosk model and native libraries. Consider downloading the model on first enable instead of bundling it.

**Music Assistant today.** Two separate paths:
1. *Controlling players*: HA `media_player` entities (transport, volume, configured shortcuts) through the paired channel.
2. *The tablet as a speaker*: the Sendspin receiver, encrypted pairing required.

- [ ] **Receiver stops when the screen turns off.** It follows `onStart`/`onStop`, so audio ends when the display sleeps or another app opens, unless the dock view is showing over the lock screen. This is deliberate (no background service), but it is the main limit on using the dock as a speaker. A user-enabled media foreground service while docked would fix it; it needs its own decision because it adds a persistent notification and background audio.
- [x] The receiver reported itself to Music Assistant as "Hearthline 0.6.1" regardless of version. Fixed in 0.7.4.
- [ ] Library browsing and queue (already listed above).

## Code follow-ups

From the November 17, 2026 review. Done in 0.7.4:

- [x] Telemetry failures no longer mark the dashboard offline; telemetry has its own loop and backoff (`HubViewModel.telemetryLoop`).
- [x] Installation ID is written atomically; a corrupt or empty ID is regenerated while unpaired and fails closed while paired (`installationId`).
- [x] Discovery stays on by default for new tablets and pauses once paired, since HA has already found the tablet.
- [x] Discovery starts once per resume; network callbacks that leave the bound address unchanged are ignored.
- [x] The discovery responder serves up to four clients concurrently and closes extras instead of queueing them (`TabletDiscoveryResponder`).
- [x] `usesCleartextTraffic` replaced by `res/xml/network_security_config.xml`, which documents why cleartext is still needed (manual `ws://` Sendspin) and where each client enforces HTTPS instead.
- [x] `MainActivity.kt` split from 642 to 368 lines: `DeviceTiles.kt`, `MusicScreens.kt`, `SettingsScreen.kt`.
- [x] Discovery socket and lifecycle logic moved into a JVM-testable class with tests.

Still open:

- [ ] Adopt a formatter (ktfmt or ktlint) in CI. Not done in 0.7.4 because the first run reformats every file; do it as a standalone formatting-only commit so reviews of real changes stay readable.
- [ ] The assistant display bridge still polls `display` every two seconds. It could move onto the push stream once the stream carries display commands.
- [ ] An instrumented test for the push stream on a device against a test HA instance.
