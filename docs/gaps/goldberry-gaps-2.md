# Goldberry gaps

What Tessera needs from [Goldberry](https://github.com/DigitalSmile/goldberry)
that Goldberry does not provide, or does not provide in the release Tessera
builds against. For each gap: what Tessera does meanwhile, and what would close
it upstream. The [roadmap](roadmap.md) refers to these by ID.

Baseline: Tessera builds on **Goldberry's snapshot** (`2026.2-SNAPSHOT`,
published from `master` to Central's snapshot repository on every green push;
[ADR 0036](adr/0036-desktop-on-the-goldberry-snapshot.md)), not on a release.
So what counts is what the snapshot has: *in the snapshot* below is what the
client already builds on.

Last reviewed on **2026-11-07** against `2026.2-20261025.195555-18`, the newest
snapshot, which the client resolves, built from `master` as of 2026-10-25
(the same commit as the local `../goldberry` checkout). Published artifacts:
`common`, `natives`, `core`, `widgets`, `html`, `emoji`, `gpu`, `media`, the
BOM and the umbrella jar; not `weaver` and no testing artifact.

**Rules**

- Add a row the moment a gap is hit, even mid-task. Say which roadmap item it
  blocks and what Tessera does meanwhile.
- Workarounds live in Tessera behind an interface, in the package that owns
  the concern (e.g. `client/core/.../auth` for credential storage), so they can
  be swapped for the Goldberry version later without touching callers.
- When a gap closes (in the snapshot the client builds on, and adopted here),
  move it to *Closed* with the snapshot build. A later release changes nothing.
- A gap nothing waits for any more, because Tessera's own way is the answer
  and no roadmap item needs Goldberry's, moves to *Dropped*. IDs are never
  reused.
- Review the open rows whenever the client picks up a newer snapshot
  (`--refresh-dependencies`), and say so in the baseline.

| Mark | Meaning |
| --- | --- |
| ⛔ | missing, blocks a roadmap item on the critical path |
| ◐ | partly in the snapshot |
| ☐ | missing, not yet on the critical path |
| ☑ | closed |

## Open

### Blocking or on the critical path

| ID | Gap | Today in Goldberry | Blocks | Tessera meanwhile | Upstream ask | Status |
| --- | --- | --- | --- | --- | --- | --- |
| G-01 | **The weaver is not published** (ahead-of-time binding, needed for a native image) | `weaver/` exists but is missing from `PublishedModules`, and has no publish plugin. `book/src/ahead-of-time-binding.md` still tells users to depend on `dev.goldberry:goldberry-weaver` | SP-03, P-09 (launch < 500 ms, RSS < 300 MB) | run on the JVM with an AOT cache (ADR 0007); optionally build the weaver from source in a spike | publish `goldberry-weaver`; fix the book until then | ⛔ |
| G-02 | **Rich-text input**: inline spans (bold, italic, code, mention, link) in an editable, growing field, plus a formatting popover on selection | `TextArea` is plain text (grows, undo, clipboard, IME preedit). `core/text/edit/Editor.java` is widgetless editing. `TextMenu` only does cut, copy and paste | R1.5-04 composer | decided by [ADR 0020](adr/0020-composer-on-goldberry-rich-text.md): built in Goldberry, not worked around in Tessera (roadmap R0.8-10) | a span-aware editable text widget over `Editor` | ⛔ |
| G-03 | **Rich-text display with entities**: clickable runs, spoilers, mentions, and select/copy in a read-only message | `RichText`/`Run` is in the snapshot: styled runs wrapping as one paragraph, with no click, hit-testing, selection or spoiler. `LinkText` and `MarkdownView`/`HtmlView` (with selection and copy) are there too | R1.5-03 bubbles | render entities through `MarkdownView` or a custom Canvas run layout; spoilers as an overlay | selection, hit-testing and click on `RichText` runs; spoiler style | ◐ in the snapshot |
| G-04 | **Variable-height virtualized list that keeps its place when history is added at the top**, with sticky section headers | In the snapshot: `ListView` over `RowHeights`/`MeasuredWindow` measures each row, keeps the reader's row by identity when rows are prepended, and moves the enclosing `scroll` by the change above it. `Affix` gives sticky headers, but not inside `ListView` | R1.5-02 message list (200-row window, bidirectional, sticky dates) | the snapshot's measured rows; sticky dates with an `Affix` over the list, or as rows, until `ListView` has them | sticky section headers built into `ListView` | ◐ in the snapshot |
| G-05 | **Camera and microphone capture** (the architecture's "goldberry-camera" and "goldberry-mic") | missing: SDL binds only the default *playback* device. PCM output exists (`SdlAudioSink`) | R3.1-05, voice messages in R1.5-05 | none yet; voice messages move behind R3 if this stays open. Fallback: capture in `tessera-capture` (PipeWire, CoreAudio/AVFoundation, WASAPI/MF) | SDL3 camera and recording bindings with device lists and hot-plug | ⛔ (R3) |
| G-06 | **Encoders and public codec access**: Opus and VP9 (k-SVC) *encode*; decoders callable from app code | `media.codec` is exported in the snapshot: the `Decoder`/`DecoderProvider` SPI and the frame, packet and track types. The bundled decoders (dav1d, libvpx, Opus, libyuv) sit behind it. There is no encoder | R3.1-04 | build libvpx/Opus encoders into Tessera's natives pipeline (D-07); decode through the public SPI | add libvpx and Opus encoders, or document that apps bring their own | ⛔ (R3) |
| G-07 | **Video tile fed by app-pushed frames** (I420/BGRA from the jitter buffer), sampled per vsync | In the snapshot `VideoView`/`VideoSurface` draws a `MediaPlayer`'s `shownPicture()` only, on the GPU through a `VideoPresenter` (planes converted by a shader) where it can. `VideoPicture` and `VideoPlanes` can be constructed, but nothing but a player feeds the surface | R3.3-01 call grid | a `Canvas` with `Image.ofArgb` (CPU) until a frame source exists | a `FrameSource`-driven video widget (latest frame per vsync, YUV on the GPU through the same presenter) | ◐ in the snapshot |
| G-08 | **Web view JS bridge for Mini Apps**: an init script injected before page scripts, and `eval` / app→page messages on the embedded widget | the `WebView` widget docks a child window with page→app calls (`WebViewSpec.on`), `onNavigate` and cookies. `eval` only on `BackendWebView`, not on the embedded `WebViewController`; `webview_init` not bound; painted overlays cannot cover the page | R1.7-01 Mini App host (`window.Telegram.WebApp`) | inject the API with a bootstrap page served by the client; fall back to the system browser (architecture.md, risks) | bind `webview_init`; expose `eval`/`postMessage` on `WebViewController` | ⛔ (R1) |
| G-09 | **Secure credential storage** (macOS Keychain, libsecret, Windows Credential Manager) for the device-bound refresh token | missing | R0.8-04 | `CredentialFile`: an owner-only file, AES-256-GCM under a key from a random key file beside it, behind `CredentialStore` in `client/core/.../auth/store` ([ADR 0030](adr/0030-desktop-credentials-in-an-owner-only-encrypted-file.md)); anyone who can read the user's files can take the session | a small `Keychain` API over the three OS stores | ☐ |
| G-10 | **Deep links and single instance**: `tessera://` scheme registration, routing the callback to the running instance | missing. `Host.openExternal(url)` exists | Invite links that open the desktop app (R0.2-06) | sign-in (R0.2-03) needs none: it uses the embedded `WebView`'s `onNavigate` with a private-use redirect that never reaches the OS, and a loopback redirect where the system browser is needed ([ADR 0029](adr/0029-desktop-sign-in-in-an-embedded-web-view.md)); single instance through a lock file + local socket in `client/app` | URL-scheme registration and a single-instance API | ☐ |

### Needed later or partial

| ID | Gap | Today in Goldberry | Needed by | Tessera meanwhile | Status |
| --- | --- | --- | --- | --- | --- |
| G-12 | Board canvas: pinch and trackpad gestures; path hit-testing (`Path.contains`); a tile-cache helper | `Canvas` + `Frame` (Blend2D), pointer capture, wheel with Ctrl-zoom. Offscreen `PixelBuffer` rendering works on worker threads; `Layer` is UI-thread only | R5.4 | own R-tree hit tests on Item bounds and a tile cache in `client/board/.../render`; wheel-only zoom until gestures land | ☐ |
| G-13 | `font-feature-settings` (`tnum`, `case`, `calt`) in stylesheets | not applied: the snapshot's parser sets it aside even inside `@font-face`. `font-variation-settings` (opsz) is there | R1 (timestamps, counters, timers per design-system.md) | none; numbers in proportional figures until fixed | ☐ |
| G-15 | Backdrop blur (`backdrop-filter`): the frost material for the call control bar and the Board context toolbar | missing; the snapshot's `filter` works on the element itself, and `backdrop-filter` is ignored | R3.3, R5.4 | opaque surface-raised material | ☐ |
| G-16 | Layout (FLIP) animation for the call grid and Board zoom-to-fit | missing | R3.3-01 | animate tile transforms manually from measured bounds | ☐ |
| G-17 | Screen-reader bridge (AT-SPI, UIA, NSAccessibility) | the semantics tree exists; the bridge is on hold (Goldberry ADR-0440) | P-07 accessibility | name every control and test the semantics tree; no live screen-reader support yet | ☐ |
| G-18 | Runtime text scaling (90–150 %) | `WidgetRenderer.textScale(double)` exists in the snapshot, but neither `Application` nor `Host` exposes it | P-07 accessibility (design-system.md: text scaling 90–150 %), a text-size row in Settings | no text-size setting until it is exposed | ☐ |
| G-19 | Notifications on Windows: click callback and badge | `PlatformNotifier` reports clicks on Linux and macOS, and the badge works on Linux (Unity) and macOS | R1.3-02 | on Windows a click focuses the window only; the unread count goes in the tray tooltip | ☐ |
| G-20 | Drag out to the OS (Board frames as images, files from Chat) | OS → app drop works (`FileDrop`, `TextDrop`); in-app drag (`Dragging`, `DropTarget`) is in the snapshot; dragging out is missing | R1.5, R5 | "Save as…" and copy to clipboard instead | ☐ |
| G-22 | SVG rendering (40 library shapes, custom SVG) | no vector image decoder; only `SvgPath` for icons | R5.4 | shapes as Blend2D paths from the shared JSON shape spec; custom SVG limited to path data | ☐ |
| G-23 | PDF page rendering ("goldberry-pdf" in the architecture) | missing | R5.5 (PDF on Boards), R6 (attachments) | thumbnails from the Media service (first-page preview) | ☐ |
| G-24 | HTML for mail bodies: inline `style=` and `<style>` | `HtmlView` is a Java parser with themed rendering (not litehtml), with `onLink` and controllable image loading. `<style>` and `style=` are parsed into the model and applied by nothing | R6-02 | sanitize to a supported subset server-side; plain-text fallback | ☐ |
| G-25 | Spellcheck in text inputs | missing | R1.5-04 | none | ☐ |
| G-26 | Pluralization and locale message formatting | the date pickers are locale-aware and bidi is in the snapshot; pluralization is missing | P-06 (ru/en) | an i18n package in `client/core/.../i18n` with CLDR plural rules for ru/en | ☐ |
| G-31 | **Goldberry on the web**: render the same widget tree in a browser, so Members can use Tessera without installing ([ADR 0016](adr/0016-members-desktop-only-in-v1.md)) | missing; the desktop backends only | after v1 (Members are desktop-only in v1) | none; Members install the desktop client, Guests use the TypeScript pages | ☐ |
| G-29 | OS-wide global hotkeys (push-to-talk while unfocused) | per-window shortcuts only | R3.7-01 | push-to-talk only while focused | ☐ |
| G-30 | Shared memory between `Canvas3d` GPU layers and Board tiles ("Board tiles and video frames share the GPU canvas path") | `Canvas3d` exists; `gpu.video` is exported only to `media` | P-09, R5.4 | CPU Blend2D path for Board tiles | ☐ |
| G-32 | Web view for sign-in: a private (ephemeral) data store, clearing a page's cookies, and setting the user agent | `WebViewController.cookies` reads cookies; there is no way to choose the data store, delete cookies or set the user agent | R0.2-03 sign-out, shared machines | the embedded sign-in page uses the engine's default store, so the IdP's session may outlive a Tessera sign-out ([sign-in.md](sign-in.md)) | ☐ |

### Confirmed out of Goldberry's scope (Tessera builds these)

- Screen capture (PipeWire, ScreenCaptureKit, Windows.Graphics.Capture): goes in
  `tessera-capture`, per architecture.md.
- SQLite binding: Tessera's own over FFM (D-06).
- Automerge, libdatachannel, audio processing: Tessera's natives pipeline (D-07).

## Useful things Goldberry already has

Use these before building your own:

- `ImageCache`: a byte-bounded LRU that decodes on virtual threads (the 256 MB
  image budget).
- `Clipboard`: text, MIME bytes, images and URI lists.
- `Host.openWindow` (call pop-out), `Host.openExternal` (system-browser sign-in, admin links).
- `WebView` with `onNavigate` (embedded sign-in: catches the OAuth redirect before it loads) and `WebViews.isAvailable()`.
- The tray, `Window.requestAttention`, toasts, dialogs, popovers, tooltips and
  context menus; `skeleton` (the 300 ms rule), `presence` enter and exit
  animations, `split`, `timeline`, panel `calendar`, `masonry`.
- `MarkdownView` (md4c), `qrcode` (device sign-in), charts and sparklines (the
  Usage page in the client).
- `MediaPlayer` with HTTP streaming, for voice and video message playback.
- `ListView` keyboard navigation and typeahead, `FocusScope`, `Host.focus(id)`,
  `Host.shortcut`.
- `Clock.virtual()`, `Offscreen`, `Session` for deterministic UI tests.

## Closed

| ID | Gap | Closed in Goldberry | Adopted in |
| --- | --- | --- | --- |
| G-14 | `cubic-bezier()` easing in CSS | in the snapshot (Goldberry ADR-0600: an application's sheet may write CSS's curves); reviewed in `2026.2-20261025.195555-18` | 2026-11-07: the shell writes `var(--ts-ease-enter)`, and `ThemeEasingTest` checks all three `--ts-ease-*` curves through `var()` (T-09). The overshoot curve waits for reactions (R1.5-03) |
| G-28 | Emoji set: design-system.md named OpenMoji, Goldberry bundles Noto Color Emoji | not a Goldberry change: Tessera adopted Noto ([ADR 0019](adr/0019-noto-color-emoji.md)) | 2026-11-04 |

## Dropped

Gaps Tessera stopped waiting for: its own way is the answer, and no roadmap
item needs Goldberry's. A Goldberry version is welcome, not awaited.

| ID | Gap | Why dropped | Tessera's own |
| --- | --- | --- | --- |
| G-11 | Golden-image test helpers are not published (`GoldenImage`, `HeadlessRuntime`, `TestClock` in `core/src/testFixtures`) | R0.8-08 is built on `Offscreen`, `Session` and `Clock.virtual()`, which are public; still no testing artifact in the snapshot | `client/shell/src/test/.../testing/Goldens`: write under `-Ptessera.goldens.update`, else compare at 2/256 per channel on at most 2 % of pixels |
| G-21 | Window-state persistence helper (position, size, maximized, which windows are open) | R0.8-07 is done with what `Window` and `Application` already offer; the snapshot still has no save/restore helper, and Tessera has one window | `shell.settings.WindowLayouts` + `PreferencesFile`, restored through `Application.size/position/display/maximized`. A second window (call pop-out, R3) restores the same way |
| G-27 | Command palette widget | R0.8-07 built one from the snapshot's parts; nothing else needs a toolkit palette | `shell.palette`: a `host.fill` overlay over `PaletteScrim`, a `text-input`, a panel that takes Up, Down, Enter and Escape in `onKeyCapture`; matching in `CommandMatcher` |
