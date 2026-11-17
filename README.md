# Hearthline 0.7.2

[![CI](https://github.com/shreyasajj/hearthline/actions/workflows/ci.yml/badge.svg)](https://github.com/shreyasajj/hearthline/actions/workflows/ci.yml) [![CodeQL](https://github.com/shreyasajj/hearthline/actions/workflows/codeql.yml/badge.svg)](https://github.com/shreyasajj/hearthline/actions/workflows/codeql.yml)

Source: [shreyasajj/hearthline](https://github.com/shreyasajj/hearthline). Download the current development APK from a successful [CI run](https://github.com/shreyasajj/hearthline/actions/workflows/ci.yml). See [security maintenance](SECURITY.md) for protected merges, dependency updates, and automatic CodeQL fix PRs.

A local Android home dashboard with integration-managed Home Assistant setup, remembered tablet pairing, and opt-in foreground tablet telemetry. This tablet project lives in `pixel-home`. Home Assistant integration source is maintained separately in `pixel-table-ha` and https://github.com/shreyasajj/hearthline-ha.

The locked dock now opens directly into the full-screen Good morning overview, with no dock toolbar or unlock buttons. Swipe up from the bottom to reach Android's unlock screen. The native navigation gesture remains available, and screen-reader users have an **Unlock tablet** action. Demonstration, disconnected and assumed-dock states remain labeled in the overview.

The Home Assistant integration is named **hearthline-ha** in Devices & services; the Android app is **Hearthline**. The Home and locked dock overview now fit on one page: a clock and date, up to four favorites, a horizontal scenes row, and music controls. Larger text reduces the number of visible favorites; **All** opens the rest in a details sheet. Device cards open full controls in a sheet. The overview itself does not scroll. Landscape and portrait use the same native components, including dark mode and the existing locked-control restrictions.

The HA domain remains `pixel_home` and the Kotlin namespace remains `dev.pixelhome` to preserve the display/template APIs. No dashboard configuration sensor is required for the new pairing path. Protocols 1.0/1.1 and capability revision `pixel-home-0.4` remain unchanged.

Read [HEARTHLINE-SETUP.md](HEARTHLINE-SETUP.md) for installation, pairing, migration, permissions and first-push checks. [TABLET-PROTOCOL.md](TABLET-PROTOCOL.md) specifies authentication, schemas and lifecycle. [VALIDATION.md](VALIDATION.md) records current evidence and outstanding device acceptance. [HA examples](https://github.com/shreyasajj/hearthline-ha/tree/feat/hearthline-ha-integration/examples) contains status and automation examples.

`install/Hearthline-preview.apk` uses the existing separate preview ID `dev.pixelhome.templates`. `install/Hearthline-debug.apk` uses `dev.pixelhome` and replaces an installation with the same package/signing key. Both are development builds, signed with the local Android debug certificate, not production release artifacts. Do not clear an existing app's data to test this fork without saving any settings you want to keep.

Build with JDK 17 and Android SDK 35:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug -PpixelHomeTestId=dev.pixelhome.templates
```

Home Assistant integration tests and automation examples are in the separate [hearthline-ha repository](https://github.com/shreyasajj/hearthline-ha). Run its tests from `../pixel-table-ha`.

Only HA 2026.10.4 has been exercised here; no wider compatibility claim is made. HA installation requires an HTTPS origin with a certificate trusted by Android. There is no insecure TLS option, provisioning QR, cloud dependency or automatic HA deployment.

Existing native templates, Sendspin, ambient settings, Home role and protected dock behavior remain in the source. Their setup references are [DISPLAY-SETUP.md](DISPLAY-SETUP.md), [TEMPLATE-SETUP.md](TEMPLATE-SETUP.md), [SENDSPIN-SETUP.md](SENDSPIN-SETUP.md) and [AMBIENT-SETUP.md](AMBIENT-SETUP.md). Their earlier evidence is archived in [VALIDATION-0.6.1.md](VALIDATION-0.6.1.md); it is not acceptance evidence for this integration milestone.
