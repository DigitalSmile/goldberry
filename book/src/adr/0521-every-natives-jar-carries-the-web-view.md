# ADR-0521: Every natives jar carries the web view

- **Status:** Accepted
- **Date:** 2026-10-02
- **Relates to:** [ADR-0016](0016-verify-the-artifact-and-never-skip-the-check.md),
  [ADR-0334](0334-central-is-fed-once-per-run.md),
  [ADR-0441](0441-a-web-page-is-a-window-not-a-box.md),
  [ADR-0442](0442-a-page-is-a-child-window-where-the-window-system-allows-one.md),
  [ADR-0450](0450-the-webview2-runtime-ships-with-windows-its-headers-do-not.md)

## Context

Deploy Orc, an application on Goldberry, reported that `web-view` cannot open a
page on macOS: `WebViews.isAvailable()` is false, and `libgoldberry.dylib` in
`goldberry-natives-…-macos-aarch64.jar` exports no `webview_*` symbols. The report
guessed that the web view is built only where WebKit's headers are present.

The engine is a second library, `libgoldberry-webview`, linked into nothing and
opened on demand. `nativeJar*` packages it beside `libgoldberry` when it is
there. Checking each platform's last snapshot run found three different states:

- **macOS** built it on every run, since WKWebView is a system framework and
  there is nothing to probe for. The job's artifact named only
  `libgoldberry.dylib`, so `publish.yml` never received it. The newest macOS
  snapshot jar, `2026.2-20261002.092855-3`, holds one dylib.
- **Windows** built it on every run too ("Goldberry web view: ON (WebView2 SDK
  1.0.1150.38)"), from the headers the superbuild fetches (ADR-0450). The
  artifact named only `goldberry.dll`.
- **Linux** never built it. The manylinux_2_28 image is AlmaLinux 8, and
  `dnf install webkit2gtk4.1-devel` answered "No match" in an optional step
  that was allowed to fail. The configure printed "Goldberry web view: OFF".

So no published natives jar, on any platform, has ever had a web view. Nothing
caught it, for two reasons:

- The packaging treated a missing web view as a supported build everywhere. It
  logged one line and carried on.
- The verify jobs load only what they download. With no web view library beside
  `libgoldberry`, every test that needs one skipped, and none was required to
  load it. That is the green tick over an unverified artifact that ADR-0016
  forbids for `libgoldberry`.

## Decision

**Every published natives jar carries the web view, and CI refuses one without
it at each place it could be lost.**

- **macOS and Windows** check the library after the build (macOS also checks it
  exports `goldberry_webview_create` and links `WebKit.framework`) and upload it
  beside `libgoldberry` in `native-<target>`.
- **Linux** builds it in a job of its own, `webview`, in an `ubuntu:22.04`
  container on the same x64 and arm64 runners. 22.04 is the oldest mainstream
  release with WebKitGTK 4.1's headers. The whole superbuild configures, since
  the shim's CMake and its webview pin live there, and only `goldberry-webview`
  is built. The job fails unless the configure says "web view: ON", the library
  exports `goldberry_webview_create` and it links `libwebkit2gtk-4.1.so.0`. It
  uploads `webview-<target>`, which the verify job and `publish.yml` put beside
  `libgoldberry.so`. `libgoldberry` itself stays on manylinux_2_28.
- **Every verify job** runs `:natives:test` with
  `-Dgoldberry.webview.required=true` (Linux installs `libwebkit2gtk-4.1-0`
  first). `WebviewBindingTest` loads the library, checks its ABI against
  `Webview.ABI` and binds every call, without opening a page.
  `WebviewRequirement` turns a skip into a failure when the property is set, as
  `NativeLibraryRequirement` does for `libgoldberry`.
- **The packaging** marks all four targets `webviewRequired: true`, so
  `nativeJar*` fails without the library rather than logging. A local build
  without WebKitGTK's headers is still a supported build; it just cannot make a
  publishable jar.

## Consequences

- The next snapshot, and the first release, carry the web view on all four
  targets. `WebViews.isAvailable()` is true wherever the platform's engine is
  installed, and `Capability.WEB_VIEW` is reported.
- The Linux web view library has a higher glibc floor than `libgoldberry`. Built
  on 22.04 it needs GLIBC 2.34 and GLIBCXX 3.4.21, against 2.28 for the main
  library. That costs nothing real: it links WebKitGTK 4.1, and every system that
  has `libwebkit2gtk-4.1.so.0` is newer than that. A system without WebKitGTK 4.1
  loads `libgoldberry` as before and reports no web view.
- The Linux library links the GTK 3 pairing, as the superbuild always preferred,
  so it shares the `libgtk-3` a tray icon already maps. A desktop with only
  WebKitGTK 6.0 gets no web view from the published jar.
- A run that loses the library on any platform is red in CI: at the check after
  the build, in the verify job, or at packaging in `publish.yml`, before
  anything is uploaded.
- The rest of the report is still open: a cookie store and a navigation hook, so
  that a page can complete an SSO login for the application.
