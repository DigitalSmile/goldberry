# 507. A page is taken down on WebKit's thread, and its context outlives `exit()`

Date: 2026-10-01

## Status

Accepted. Changes how `libgoldberry-webview` closes a page on Linux
([ADR-0441](0441-a-web-page-is-a-window-not-a-box.md),
[ADR-0442](0442-a-page-is-a-child-window-where-the-window-system-allows-one.md)).
The macOS and Windows paths are unchanged.

## Context

The showcase, run with `gradle run` on the GPU, printed its last summary line —
every window closed, `SDL_Quit` called — and then died with exit code 134,
SIGABRT, and no JVM crash log. Not every run: an idle showcase, and fourteen runs
through the heavy screens, exited 0. Separately, Ubuntu's crash dialog reported
`WebKitWebProcess` — WebKit's out-of-process renderer — dead of SIGSEGV in
NVIDIA's EGL driver.

Two things were wrong with how a page was closed on Linux:

- `goldberry_webview_destroy` called `webview_destroy` and returned. The message
  that tells the renderer its page has gone is sent from GLib's main context, and
  nothing iterated it again: the next thing the backend does is destroy the SDL
  window the page was reparented into, and the X server takes the page's own
  window with it. A renderer still drawing then draws into surfaces that are
  gone, and NVIDIA's EGL answers with a segfault rather than an error. Nothing
  noticed: there was no handler for WebKit's `web-process-terminated`.
- WebKit's exit-time teardown runs on the wrong thread. Stopping the renderer
  first made the abort happen on **every** run, which made it debuggable: under
  gdb, with Ubuntu's debug symbols, `exit()` on the launcher's first thread runs
  the C++ static that holds WebKit's default `WebKitWebContext`; finalising it
  releases the website data manager, whose `~WebsiteDataStore` reaches
  `allDataStores()` — main-thread-only state — and WebKit crashes on purpose,
  `WTFCrashWithInfo` at `WebsiteDataStore.cpp:124`. WebKit's main thread is the
  one that started GTK, Goldberry's UI thread; the stock `java` launcher runs
  `main` on a thread of its own and calls `exit()` from its first. Whether the
  abort happened depended on whether anything still held the context at exit,
  which is why it came and went. A native image runs `main` on the first thread
  and never met it.

## Decision

**On Linux, a page is closed in three steps, on the UI thread, before its window
can go:** the renderer is stopped (`webkit_web_view_terminate_web_process`,
WebKitGTK 2.34 and later), the page is destroyed, and GLib's main context is
drained — bounded, never blocking, for `goldberry_webview_pump`'s reason.
Termination rather than a polite close, because a close is a message the
renderer handles when it gets to it, and the window goes in the next call. An
embedded page's unload handlers do not run; closing a window under a browser tab
costs the same.

**WebKit's default context is pinned.** The first page takes one reference to
`webkit_web_context_get_default()` on the UI thread and never gives it back, so
the static's release at `exit()` only counts down and nothing is finalised off
WebKit's main thread. The context lives until the process ends, which is the
moment it was being destroyed in.

**A renderer that dies is said out loud.** `web-process-terminated` with
`WEBKIT_WEB_PROCESS_CRASHED` or `EXCEEDED_MEMORY_LIMIT` is a GLib warning in the
`goldberry-webview` domain, which ADR-0443 routes to the logger:
`WARN goldberry-webview - the page's web process crashed; the page is blank until
it is navigated again`. Termination by the API — the first step above — is
silent.

## Consequences

- The showcase on the web screen exits 0, and the three "WebKit encountered an
  internal error … `internallyFailedLoadTimerFired`" lines it used to print
  after its last summary are gone: those were the renderer's loads failing as
  the process left under it.
- `WebviewExitTest` runs `WebviewExitProbe` in a child JVM — open a page, let it
  load, close it, return from `main` — and asserts exit code 0. With the pin
  removed it fails with 134; it skips without a display or a library.
- Killing the renderer from outside while a page is open now logs the warning
  above, and the process still exits 0.
- The renderer's SIGSEGV in NVIDIA's EGL is not reproduced on demand. The
  teardown above removes the path by which a closing page could leave it drawing
  into a destroyed surface; a crash during ordinary rendering would be WebKit's
  and NVIDIA's, and would now at least be logged. WebKit's own
  `WEBKIT_DISABLE_DMABUF_RENDERER=1` is the known workaround for that driver, and
  is the user's to set, not the toolkit's.

## Alternatives considered

- **Exiting the JVM from the UI thread.** `System.exit` there would run the exit
  handlers on WebKit's main thread. It would also make `Goldberry.launch` end
  the process for every application, including those that do work after it
  returns — a contract change to dodge one library's static.
- **Releasing the context ourselves before exit.** The static still holds its
  reference and drops it at `exit()`; there is no WebKit API to clear it, and
  unreferencing an object the toolkit does not own is a double free waiting for
  a WebKit release that changes the count.
- **Leaving the renderer running and only draining.** The drain lets WebKit send
  its close, but the renderer handles it when it gets to it, and the X window is
  destroyed microseconds later.
