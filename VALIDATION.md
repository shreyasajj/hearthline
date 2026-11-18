# Hearthline 0.7.4 validation — November 17, 2026

The newest evidence is in [0.7.4](#074--push-stream-telemetry-isolation-discovery-fixes) at the end, after [Discovery 0.7.3](#discovery-073). Earlier sections are retained from 0.7.2 and before.

This includes actual one-page layout checks on the connected tablet, using clearly labeled demonstration data. It is **not completed live Home Assistant tablet onboarding acceptance** and has not been deployed to the user's HA instance. The current tablet source and its uncommitted layout changes have been consolidated into `pixel-home`; the HA integration now lives separately in `pixel-table-ha`. Earlier upstream validation is archived separately in VALIDATION-0.6.1.md.

## Full-screen dock and swipe unlock — 0.7.2

- The locked overview renders directly from the shared Home component, without the dock toolbar or unlock/dismiss buttons. Status bars hide while the full overview is active; Android navigation remains available. Demo/disconnected/test labels sit below the date. Screen-reader unlock is available as a custom accessibility action; preview uses Android Back to exit.
- **92 Android unit tests passed**, including five edge-swipe cases: density changes, taps/short drags, gestures outside the bottom edge, horizontal/downward motion and invalid coordinates. Preview assembly, instrumentation assembly and lint passed (**0 errors, 11 existing warnings**). Default-package assembly and signature checks passed. Evidence is under `validation/fullscreen-dock/`.
- **Six actual locked Pixel Tablet runs passed**: landscape, 200% text landscape, portrait, dark landscape, a swipe starting just above navigation, and a swipe starting at the native bottom navigation edge. Both swipe runs reached Android System UI while `KeyguardManager.isDeviceLocked` remained true; the test canceled without entering or collecting credentials. The layout checks also verify the toolbar is absent, switches/sliders disabled, transport visible and volume inside the viewport, with no overview scroll container. Screenshots show the actual app with labeled demo fixtures.
- The harness restored original font/rotation settings and temporary display preferences, ended its assumed-dock lease, and removed the test APK. The default launcher and Android authentication were preserved. A screen-reader action was implemented but was not manually tested using TalkBack.
- Owner-authenticated completion, real hardware dock attach/detach, live Home Assistant pairing, actual Sendspin playback and ambient sleep/wake were not retested. The automated swipe checks establish handoff to Android's locked UI, not successful owner authentication. Previous milestone evidence below is retained and is not a new run.

## One-page overview — 0.7.1

- Android app unit tests: **87 passed**, including six capacity cases for landscape, portrait, large text, hidden music, empty/odd favorites and bounded capacity. Preview/main assembly and lint passed; build evidence is in `validation/one-page/`.
- Actual Pixel Tablet `MainActivity` instrumentation: **seven layout runs passed**. Unlocked Home: landscape, landscape at font scale 2.0, portrait. Locked dock: landscape, landscape at font scale 2.0, portrait, and dark landscape. Each run verifies no overview scroll container, visible music transport and volume, and volume within the viewport. Locked runs assert Android remains locked and switches/sliders remain disabled. The large-text locked run also opens **All favorites** and verifies the detail sheet is reachable.
- Actual screenshots and individual test output: `validation/one-page/overview-*.png`, `dock-*.png` and corresponding `.txt`. Fixtures explicitly say **Design demo · sample devices · no audio playback**; these are not real home or playback observations. Normal locked landscape shows four favorites plus scenes/music on one page. Locked 200% text shows two favorites plus **All 4**; full controls remain available through details after authentication.
- Tests use a temporary local assumed-dock lease. The harness restores original Android font/rotation settings and temporary app display preferences, ends its lease, and removes the test APK. It does not authenticate Android, change the default launcher, invoke home service actions, or grant permissions.
- Physical dock attach/detach, owner-authenticated unlock/relock, live HA pairing/configuration delivery, Sendspin audio and ambient wake were **not retested** for this layout change. Their code paths were preserved; no new live acceptance is claimed.

The integration and regression results below are retained from the 0.7.0 milestone; the HA suite was not rerun for this UI-only change.

## Executed successfully

- Home Assistant **2026.10.4**, Python **3.14.10**, `pytest-homeassistant-custom-component==0.13.375`: **28 tests passed**. Tests use the actual HA config-entry/flow/options/entity/device/HTTP fixtures, not substitute dependency-free mocks. They cover invalid/missing entities, empty selections, preserved unavailable state, pairing/authentication, duplicate pairing, cross-tablet isolation, closed control dispatch with account context, revocation, disabled owner, options order/revision delivery, entry reload, restarted telemetry unavailability, malformed/oversized telemetry, schema/enums/nulls, timestamp bounds, sequence/session fencing, manual overrides, diagnostic/provider-secret redaction and unchanged-state heartbeat coalescing. All three example automation schemas were validated with HA's actual automation configuration validator. Notification actions are placeholders, not live notifications.
- Android JDK **17**, Android SDK compile/target **35**: `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:lintDebug` passed with preview ID `dev.pixelhome.templates`. **87 app unit tests**, including the new managed-dashboard validation/origin/fixture tests and existing navigation/access/dock/ambient/token/template/display regressions. Lint: **0 errors, 11 warnings** (retained camera/platform/icon/layout warnings; see report). Default-package debug assembly also passed.
- Sendspin regression tasks `:sendspin-player:testDebugUnitTest` and `:sendspin-shared:jvmTest`: **56 + 210 = 266 tests passed**.
- Existing dependency-free Python generic/native display/provider regression suite: **45 tests passed**.
- Python modules compile successfully. APK signatures and SHA-256 hashes verified; package validation checks ZIP CRC, allowlisted contents and embedded APK hashes. Build/pytest evidence and a signature report are included in `validation/`.

## Connected-device observations only

Read-only ADB observations on the connected Pixel Tablet: Android **16**, API **36**, build **CP1A.260505.005**, security patch **2026-05-05**. Battery service reported AC=true, USB=false, wireless=false, dock-powered=false, level=100, battery status=5 (full). This is not charging=true and AC is not physical dock confirmation. Historical audio configuration includes digital-dock routes; an active named dock route and physical attach/detach were not established in this run. GrapheneOS was not tested or inferred from these observations. No PIN or hardware identity was collected into the delivery artifacts.

## Not executed / remaining acceptance

- No clean standalone HA deployment plus APK pairing against Android-trusted HTTPS; no production HA installation or account authorization was supplied or performed.
- No live tablet demonstration of configuration delivery, edited player order, account-authorized home actions, app restart/Keystore restore, revoke/unpair, or receiver/music artwork against an actual HA player. The corresponding server paths and dashboard validation have automated coverage; compilation is not live evidence.
- No physical dock attach/detach, authenticated unlock/relock acceptance, or instrumentation exercise of Keystore backup/key-loss behavior. The layout checks above use assumed docking and do not establish those outcomes. Existing pure policy/access regressions pass; managed-pairing lifecycle/backoff/jitter still need live acceptance.
- No provisioning QR or multicast discovery is implemented. Explicit manual HTTPS setup is the supported path. The official HA raw-token and Sendspin flows remain distinct. Malicious/expired/replayed provisioning-QR acceptance is inapplicable to this build; a future QR feature would need separate challenge/expiry/replay tests.
- HA process/storage restart across a separate running server was not tested; config-entry reload tests verify retained entry credentials and reset telemetry. Persisted Android reconnect still requires live device validation.
- Compatibility is pinned to tested HA **2026.10.4** only. No broader version/GrapheneOS claim is made.

A live acceptance session needs a disposable HA server with an Android-trusted HTTPS origin, actual test entities and explicit authorization, plus a user-unlocked tablet and physical dock interaction. Follow HEARTHLINE-SETUP.md; do not install a certificate, clear existing app data, change the Home role or bypass Android authentication to satisfy a test.

## Implementation boundaries

Telemetry is opt-in and activity-resumed only, coalesced to changed snapshots and 30-second heartbeats; an in-flight request can finish after pause within 15 seconds. No new background sender/service, wake, Android permission change, remote dock-test activation or remote configuration entity was added. Unchanged sensor states do not add sequence/timestamp attributes; last-seen/app-version diagnostics start disabled. A 120-second default receipt timeout (60–900 configurable, 15-second evaluation) marks data unavailable.

New remembered authorization is a separate scoped credential. Legacy pasted/scanned HA account tokens stay process-only. Journey credentials/options stay on HA. No private signing keys, HA credentials, receiver secrets or installed-app lists are packaged.

## Primary API references checked

- https://developers.home-assistant.io/docs/creating_integration_manifest/
- https://developers.home-assistant.io/docs/config_entries_config_flow_handler/
- https://developers.home-assistant.io/docs/core/entity/
- https://developers.home-assistant.io/docs/device_registry_index/
- https://developer.android.com/reference/android/media/AudioDeviceInfo
- https://developer.android.com/reference/android/content/Intent#ACTION_DOCK_EVENT

The BatteryManager web reference timed out; Android SDK/source and the existing implementation supplied the battery constants used here.

## Project separation verified

All tablet source (including uncommitted one-page layout edits) was consolidated into `pixel-home` and byte-compared against the pre-split source snapshot. The HA integration now lives separately under `../pixel-table-ha/custom_components/pixel_home`. The pre-split source trees and Git bundle are backed up outside both projects.

After moving, app unit tests (87 tests), preview assembly and lint passed from `pixel-home`; default-package assembly and APK signature verification also passed. The current tablet package excludes Home Assistant integration Python source, which has its own HA-only package in the sibling repository. Historical combined archives are preserved in the split backup.

## Discovery 0.7.3

Current Android unit suite: 95 tests, zero failures/errors. Unit tests exercise the read-only HTTP protocol, header/body bounds and metadata exclusions. Android preview assembly and lint passed. The connected unlocked tablet was updated with the preview APK without clearing data. A LAN browser observed its mDNS service, and the HA production identification validator accepted its real HTTP response using a standalone real-aiohttp transport harness. Returning the existing production app to the foreground closed the preview listener. No Home-role change, PIN, home command or production HA deployment was involved.

The private-IP responder and same-LAN advertisement are live-device verified; routing/mDNS reflection across the user's VLANs and authenticated HA onboarding still require acceptance. The updated APK is mandatory; old APKs cannot advertise this service merely from an HA integration update. Targeted pre-change source files are backed up outside both projects.

## 0.7.4 — push stream, telemetry isolation, discovery fixes

Build and unit tests (JDK 17, SDK 35, preview ID `dev.pixelhome.templates`): `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:lintDebug` pass. **107 unit tests, 0 failures.** New: push-stream message parsing and validation (`StreamEventTest`), installation-ID creation, corruption and fail-closed-while-paired cases (`InstallationIdTest`), and the discovery responder over real loopback sockets: identity and 400 responses, a slow client not delaying a second request, connections beyond the limit closed rather than queued, close dropping active clients, and private-address preference (`TabletDiscoveryResponderTest`). Lint: **0 errors, 7 warnings**; `InsecureBaseConfiguration` is the documented cleartext allowance in `res/xml/network_security_config.xml`.

On the connected Pixel Tablet (Android 16), the preview APK was updated in place with `adb install -r`; app data and the installation ID were kept. Observed:

- The discovery endpoint returned `app_version` 0.7.4 with the existing installation ID; another path returned 400.
- With one client holding a half-sent request open, a second request was answered in 26 ms.
- Sending the app Home closed the listener (connection refused); bringing it back reopened it, and a macOS `dns-sd` browser saw `Hearthline-9a4c3672`.
- One pause/resume cycle produced exactly one `_hearthline._tcp` registration in the system mDNS log (previously two).
- No crash in logcat across the session.

Not exercised on the device: the push stream itself, telemetry isolation and discovery pausing after pairing. They need the tablet paired to an HA server it trusts over HTTPS, which was not available in this session. The HA side of the stream is covered by eight tests against HA's real HTTP/WebSocket stack in hearthline-ha 0.7.4.

## 0.7.5 — pairing with a code

Settings now leads with **Pairing code** and **Pair with code**; token pairing moved under **Advanced**. `PairingCodeTest` covers code entry (digits only, 8 maximum, cleared with the token), validation of the pairing response when HA chooses the entry, rejection of malformed responses, and the HTTPS-only origin and 8-digit check before any request. **111 unit tests, 0 failures**; assemble and lint pass (0 errors). The HA side is covered by six tests in hearthline-ha 0.7.5. Pairing against the user's HA is blocked until HA is served over HTTPS that Android trusts; it currently answers only on plain HTTP.


## Combined dependency updates — November 18, 2026

Validated AGP 9.5.1 with its built-in Kotlin support, the Android KMP library plugin, compile API 37, Compose BOM 2026.09.00, OkHttp 5.5.1, Ktor 3.6.1, coroutines 1.11.0 and JNA 5.19.1. Minimum API 31 and target API 35 are unchanged. GitHub Actions use the pinned updates from the actions Dependabot PR and install the matching SDK/build tools.

Local checks: debug APK assembly and lint passed; 114 application, 56 player, 210 shared JVM and 672 shared Android host tests passed, with no skipped tests. The Android host suite is explicitly enabled, has its MockK dependency, and runs in CI. The dependency inventory audited 430 resolved Maven components with no known OSV advisories after applying minimum patched versions to isolated test/build-tool configurations. Security automation and dependency-audit Python unit tests also passed.

The newer Compose lint rules exposed two non-observable locale reads in the journey/weather UI; these now read the observable Android configuration. Physical tablet, dock audio and live Home Assistant/Music Assistant acceptance were not rerun for these dependency updates.
