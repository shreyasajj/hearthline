# Hearthline 0.7.5

[![CI](https://github.com/shreyasajj/hearthline/actions/workflows/ci.yml/badge.svg)](https://github.com/shreyasajj/hearthline/actions/workflows/ci.yml) [![CodeQL](https://github.com/shreyasajj/hearthline/actions/workflows/codeql.yml/badge.svg)](https://github.com/shreyasajj/hearthline/actions/workflows/codeql.yml)

Source: [shreyasajj/hearthline](https://github.com/shreyasajj/hearthline). Download the current development APK from a successful [CI run](https://github.com/shreyasajj/hearthline/actions/workflows/ci.yml). See [security maintenance](SECURITY.md) for protected merges, dependency updates, and automatic CodeQL fix PRs.

A local Android home dashboard with integration-managed Home Assistant setup, remembered tablet pairing, and opt-in foreground tablet telemetry. This tablet project lives in `pixel-home`. Home Assistant integration source is maintained separately in `pixel-table-ha` and https://github.com/shreyasajj/hearthline-ha.

The locked dock now opens directly into the full-screen Good morning overview, with no dock toolbar or unlock buttons. Swipe up from the bottom to reach Android's unlock screen. The native navigation gesture remains available, and screen-reader users have an **Unlock tablet** action. Demonstration, disconnected and assumed-dock states remain labeled in the overview.

The Home Assistant integration is named **hearthline-ha** in Devices & services; the Android app is **Hearthline**. The Home and locked dock overview now fit on one page: a clock and date, up to four favorites, a horizontal scenes row, and music controls. Larger text reduces the number of visible favorites; **All** opens the rest in a details sheet. Device cards open full controls in a sheet. The overview itself does not scroll. Landscape and portrait use the same native components, including dark mode and the existing locked-control restrictions.

The HA domain remains `pixel_home` and the Kotlin namespace remains `dev.pixelhome` to preserve the display/template APIs. No dashboard configuration sensor is required for the new pairing path. Protocols 1.0/1.1 and capability revision `pixel-home-0.4` remain unchanged.

Read [HEARTHLINE-SETUP.md](HEARTHLINE-SETUP.md) for installation, pairing, migration, permissions and first-push checks. [TABLET-PROTOCOL.md](TABLET-PROTOCOL.md) specifies authentication, schemas and lifecycle. [VALIDATION.md](VALIDATION.md) records current evidence and outstanding device acceptance. [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) is the roadmap: what has shipped, what awaits live acceptance and what is not built yet. [HA examples](https://github.com/shreyasajj/hearthline-ha/tree/feat/hearthline-ha-integration/examples) contains status and automation examples.

`install/Hearthline-preview.apk` uses the existing separate preview ID `dev.pixelhome.templates`. `install/Hearthline-debug.apk` uses `dev.pixelhome` and replaces an installation with the same package/signing key. Both are development builds, signed with the local Android debug certificate, not production release artifacts. Do not clear an existing app's data to test this fork without saving any settings you want to keep.

Build with JDK 17 and Android SDK 35:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug -PpixelHomeTestId=dev.pixelhome.templates
```

Home Assistant integration tests and automation examples are in the separate [hearthline-ha repository](https://github.com/shreyasajj/hearthline-ha). Run its tests from `../pixel-table-ha`.

Only HA 2026.10.4 has been exercised here; no wider compatibility claim is made. HA installation requires an HTTPS origin with a certificate trusted by Android. There is no insecure TLS option, provisioning QR, cloud dependency or automatic HA deployment.

Existing native templates, Sendspin, ambient settings, Home role and protected dock behavior remain in the source. Their setup references are [DISPLAY-SETUP.md](DISPLAY-SETUP.md), [TEMPLATE-SETUP.md](TEMPLATE-SETUP.md), [SENDSPIN-SETUP.md](SENDSPIN-SETUP.md) and [AMBIENT-SETUP.md](AMBIENT-SETUP.md). Their earlier evidence is archived in [VALIDATION-0.6.1.md](VALIDATION-0.6.1.md); it is not acceptance evidence for this integration milestone.

## Tablet discovery for Home Assistant

Version 0.7.3 advertises `_hearthline._tcp.local.` while the activity is visible on private Wi-Fi/Ethernet. In the Home Assistant settings card, **Allow HA to discover this tablet while visible** controls advertising and the bounded read-only identity listener on TCP 8787. The card shows the bound tablet IP and installation ID. No credential, device state, command, microphone or background service is exposed through discovery. Activity pause closes active/listening sockets; network changes restart discovery while visible.

Update the separate hearthline-ha integration to 0.7.3 or later. Use its discovered tablet tile, or manually add it and enter the tablet IP. Across VLANs allow HA-to-tablet TCP 8787; mDNS discovery also needs reflection/forwarding. HA authentication and controls still use the tablet's explicit trusted HTTPS HA origin. Discovery never authorizes pairing. See https://github.com/shreyasajj/hearthline-ha#find-a-tablet-automatically-or-by-ip.

From 0.7.4, advertising also stops once the tablet is paired, since HA has already found it. **Forget stored server locally** or **Unpair and forget server** makes it discoverable again.

## Live updates from Home Assistant (0.7.4)

With hearthline-ha 0.7.4 or later, a paired tablet keeps a read-only WebSocket open to HA while it is visible. State changes appear as they happen instead of on a two-second poll, and the Settings card shows **Connected · live updates**. With an older integration, or if the socket cannot be opened (for example a reverse proxy without WebSocket support), the app polls as before. Tablet telemetry is now sampled every second and pushed on change, separately from the dashboard connection, so a failed telemetry push no longer marks the dashboard offline. When the app leaves the foreground, HA marks the tablet's telemetry unavailable about 10 seconds later. See [TABLET-PROTOCOL.md](TABLET-PROTOCOL.md#push-stream-ha-074--tablet-074).

## Pair with a code (0.7.5)

You no longer need a long-lived access token. In HA open the tablet's hearthline-ha entry → **Configure → Pair a tablet with a code**, then type the 8-digit code (with or without the hyphen, such as `12345678` or `1234-5678`) into Hearthline's Settings with your HTTPS HA address. The code works once and expires after 5 minutes. Token pairing remains under **Advanced**. Requires hearthline-ha 0.7.5 or later. See [TABLET-PROTOCOL.md](TABLET-PROTOCOL.md#pairing-with-a-code-ha-075--tablet-075).

## Debugging mode

Settings → **Debugging mode** shows the connection banner, command messages and activity indicator. It defaults to off and is remembered on the tablet, leaving the connected dashboard more room. Demo and disconnected warnings remain visible.
