# ADR-0521: The macOS natives jar carries the web view

- **Status:** Accepted
- **Date:** 2026-10-02
- **Relates to:** [ADR-0016](0016-verify-the-artifact-and-never-skip-the-check.md),
  [ADR-0334](0334-central-is-fed-once-per-run.md),
  [ADR-0441](0441-a-web-page-is-a-window-not-a-box.md),
  [ADR-0442](0442-a-page-is-a-child-window-where-the-window-system-allows-one.md)

## Context

Deploy Orc, an application on Goldberry, reported that `web-view` cannot open a
page on macOS: `WebViews.isAvailable()` is false, and `libgoldberry.dylib` in
`goldberry-natives-…-macos-aarch64.jar` exports no `webview_*` symbols. The report
guessed that the web view is built only where WebKit's headers are present.

That guess is wrong. The engine is a second library, `libgoldberry-webview`,
linked into nothing and opened on demand. On macOS the superbuild makes it
unconditionally, since WKWebView is a system framework and there is nothing to
probe for, and `nativeJarMacosAarch64` packages it when it is there. The macOS job
built it on every run. But the job's artifact named only `libgoldberry.dylib`, so
`publish.yml` never received the web view library, and every natives jar on
Central's snapshot repository went out without it. The newest macOS jar,
`2026.2-20261002.092855-3`, holds one dylib.

Nothing caught this, for two reasons:

- The packaging treated a missing web view as a supported build on every
  platform. That is right on Linux, where WebKitGTK's headers may be absent, and
  never right on macOS. It logged one line and carried on.
- The verify job loads only what it downloads. With no web view library beside
  `libgoldberry`, every test that needs one skipped, and no test was required to
  load it. That is exactly the green tick over an unverified artifact that
  ADR-0016 forbids for `libgoldberry`.

## Decision

**On macOS the web view library is part of the natives jar, and a Mac build
without it is refused at each of the three places it could be lost.**

- **The macOS job** checks that `libgoldberry-webview.dylib` exists, exports
  `goldberry_webview_create` and links `WebKit.framework`. It then uploads both
  dylibs in the `native-macos-aarch64` artifact.
- **The verify job** finds the dylib beside `libgoldberry`, where `WebviewLibrary`
  looks, and runs `:natives:test` with `-Dgoldberry.webview.required=true`.
  `WebviewBindingTest` loads the library, checks its ABI against `Webview.ABI`
  and binds every call, without opening a page. `WebviewRequirement` turns a
  skip into a failure when the property is set, as `NativeLibraryRequirement`
  does for `libgoldberry`.
- **The packaging** marks `macos-aarch64` with `webviewRequired: true`.
  `nativeJarMacosAarch64` fails without the dylib rather than logging. Linux and
  Windows keep the old behaviour: their library is optional and its absence is
  logged.

## Consequences

- The next macOS snapshot, and the first macOS release, carry the web view.
  `WebViews.isAvailable()` is true on a Mac with the natives jar, and
  `Capability.WEB_VIEW` is reported.
- A Mac run that loses the library is red in CI. It fails at the check after the
  build, in the verify job, or at packaging in `publish.yml`, before anything is
  uploaded.
- Linux and Windows have the same upload defect: `linux.yml` and `windows.yml`
  also name only `libgoldberry`. This record does not change them; it was asked
  for macOS. Their library stays optional, so fixing them is the upload line and
  no refusal.
- The rest of the report is still open. That is a cookie store and a navigation
  hook, so that a page can complete an SSO login for the application.
