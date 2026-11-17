# Security maintenance

The public Android repository is https://github.com/shreyasajj/hearthline. Home Assistant integration code lives separately in https://github.com/shreyasajj/hearthline-ha.

## Protected master

Changes require a pull request, an up-to-date branch, resolved conversations, and successful Android build/tests/lint, dependency review, and CodeQL checks. Administrators are included. Force pushes and deletion are disabled. CodeQL security findings block merges through the repository's code-scanning rule. These repository settings are managed on GitHub in addition to the workflows checked into this repository.

CI audits all resolved runtime, test, and build-plugin Maven dependencies against OSV and fails on known advisories or an incomplete audit. Patched build-tool transitive dependencies use minimum version constraints in `build.gradle.kts`. The Kotlin build plugin is updated to close its build-cache deserialization advisory. These tool libraries are separate from the APK runtime.

CI builds the debug APK and runs application tests, Sendspin player tests, shared JVM tests, and Android lint. The Actions run provides the APK and test/lint reports as artifacts. These APKs use a development debug certificate, not a production release key. CI does not run camera, microphone, physical dock, or Home Assistant device acceptance tests.

## Automatic dependency updates

Dependabot checks Gradle dependencies and GitHub Actions weekly. GitHub Dependabot security updates are enabled separately so available security patches can open PRs without waiting for the weekly version-update schedule. Resolved Gradle dependencies are submitted to the dependency graph after master changes.

Dependabot PRs, including major version updates, queue for squash auto-merge. Master protection still requires every check to pass. The privileged auto-merge workflow only calls the GitHub API; it does not check out or run PR code. Failed updates remain unmerged. A trusted workflow reconciles master CI, CodeQL, and dependency submission after successful checks, with an hourly fallback: GitHub suppresses normal push events when a merge uses `GITHUB_TOKEN`. Missing master runs are explicitly dispatched without storing a personal token. All workflow actions are pinned to commit hashes, which Dependabot can update.

## CodeQL patches

CodeQL scans Kotlin/Java, Python, and GitHub Actions on PRs, master changes, and weekly. Kotlin/Java uses a manual Android build under CodeQL tracing, with caches disabled for compilation.

After a successful master scan, and hourly, the remediation workflow requests GitHub Autofix patches for open CodeQL alerts. When a patch is available it creates one security PR per alert and explicitly dispatches CI and CodeQL checks for its branch. Generated changes are never executed by the privileged remediation workflow. Security PRs are left for manual review and normal protected-branch checks; they do not auto-merge.

GitHub Autofix is not available for every query, language, or repository. Pending patches are collected on a later run. Unsupported alerts create a tracking issue for a manual patch. Alerts are never automatically dismissed, and this automation cannot guarantee that every vulnerability has a generated fix. API or workflow errors remain visible in Actions.

Report sensitive vulnerabilities privately through the repository's Security tab rather than posting credentials or exploit details in a public issue.
