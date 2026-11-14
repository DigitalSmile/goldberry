# Answers to Tessera

Goldberry's reply to [`goldberry-gaps-2.md`](goldberry-gaps-2.md), entry by
entry. It is written while the work happens, and each entry ends with what has
landed and in which commit. Ids are Tessera's own, G-01 to G-32. Goldberry's
plan calls them T-01 to T-32 ([`goldberry-gaps-plan.md`](goldberry-gaps-plan.md)).

Checked against Goldberry master `992c8c21` on 2026-11-08. Only ray-tracing
work landed after Tessera's review against `2026.2-20261025.195555-18`, so
the "Today in Goldberry" column was still accurate on every row.
The dropped rows (G-11, G-21, G-27) and the closed ones (G-14, G-28) need
nothing.

| # | Gap | Answer | Status |
|---|-----|--------|--------|
| G-01 | Weaver not published | Weaver jar and a `dev.goldberry.weave` Gradle plugin | on master, not yet in a snapshot |
| G-02 | Rich-text input | Built in Goldberry; a design document first | planned |
| G-03 | Rich-text display with entities | Hit-testing, run clicks, selection, spoilers on `RichText` | planned |
| G-04 | Sticky section headers | Sections in `ListView` | planned |
| G-05 | Camera and microphone | `goldberry-camera` and `goldberry-mic` | planned |
| G-06 | Encoders | **Declined**: bring your own | declined, ADR-0689 |
| G-07 | Video tile from pushed frames | Works today through `PictureRenderer`; a picture source for `video-view` | answered + planned |
| G-08 | Web view JS bridge | Init script, `eval`, `postMessage` | on master, not yet in a snapshot |
| G-09 | Secure credential storage | `Keychain` over the three OS stores | planned |
| G-10 | Deep links, single instance | macOS URL delivery and `SingleInstance`; registration stays packaging | on master, not yet in a snapshot |
| G-12 | Gestures, `Path.contains`, tile cache | Pinch, `Path.contains`; the tile cache is Tessera's | on master, not yet in a snapshot |
| G-13 | `font-feature-settings` | Built | planned |
| G-15 | `backdrop-filter` | Built on CPU frames | planned |
| G-16 | Layout animation | Opt-in layout animation | planned |
| G-17 | Screen-reader bridge | Still on hold | on hold |
| G-18 | Text scaling | `Host.textScale` | on master, not yet in a snapshot |
| G-19 | Windows notifications | Click and badge | planned |
| G-20 | Drag out to the OS | All four platforms | planned |
| G-22 | SVG | `goldberry-vector` on ThorVG | planned |
| G-23 | PDF | Still parked | parked, ADR-0690 |
| G-24 | HTML mail styles | A subset of `style=` and `<style>` | planned |
| G-25 | Spellcheck | Hunspell | planned |
| G-26 | Pluralization | `dev.goldberry.i18n` with CLDR plurals | on master, not yet in a snapshot |
| G-29 | Global hotkeys | `GlobalShortcuts` on all four platforms | planned |
| G-30 | Shared GPU memory for Board tiles | Zero-copy rasters; a design document first | planned |
| G-31 | Goldberry on the web | A plan document (TeaVM or a compiler of our own) | planned |
| G-32 | Web view store, cookies, user agent | Built | planned |

## G-01. The weaver is not published

**Goldberry's fix.** `goldberry-weaver` is published to the snapshot repository,
in the BOM but not in the umbrella. Beside it comes a Gradle plugin,
`dev.goldberry.weave`, which does for an application what Goldberry's own
build does:

- it writes the `@Markup` catalog after `classes`, which a library with its
  own markup widgets needs on the JVM too;
- it weaves models on request, or always for a native image.

The book's snippet gets fixed: the weaver needs `goldberry-core` on its class
path.

**Today.** Run on the JVM with the AOT cache, as ADR 0007 does.

**Landed.** Built on Goldberry's master (not yet in a snapshot), ADR-0685. `dev.goldberry:goldberry-weaver` (pinned by the BOM) and `plugins { id 'dev.goldberry.weave' version '…' }` from Maven Central (add `mavenCentral()` to `pluginManagement.repositories`). `goldberry { nativeImage = true }` weaves the models for the image build. Both appear on the next snapshot once a snapshot is published from master.

## G-02. Rich-text input

**Built in Goldberry**, as Tessera's ADR 0020 expects. It starts with a design
document (`docs/rich-text-input.md` in Goldberry), because the model is the
whole question:

- marks against a tree;
- entities as offsets (Telegram's shape) or as markup;
- mentions as atoms that IME preedit cannot split;
- undo that keeps formatting.

The widget is a new `rich-input` over `Editor`, not more arguments on
`text-area`.

**Landed.** —

## G-03. Rich-text display with entities

**Goldberry's plan.** `RichText` keeps its laid-out text, so that:

- a pointer maps to an offset and to a run;
- a `Run` gets its own press handler and hover cursor (mentions, links,
  hashtags);
- drag selection and copy work, through the same geometry `text-area` uses;
- a spoiler is a run style that covers its rectangles until revealed. The
  reveal animation is Tessera's.

**Today.** As the entry says: `MarkdownView` for selectable messages.

**Landed.** —

## G-04. Variable-height list with sticky section headers

**Goldberry's plan.** `ListView` sections: a key per row and a header widget.
One header is pinned for the first visible row's section and pushed up by the
next section's start, as in a phone's contact list. Home, End and typeahead
skip header rows.

**Today.** As the entry says: an `affix` over the list.

**Landed.** —

## G-05. Camera and microphone capture

**Goldberry's plan.** Two modules, as Goldberry's architecture always named
them:

- `goldberry-mic`: SDL3 recording devices, with a device list and hot-plug.
- `goldberry-camera`: SDL3 cameras, with frames handed out as `VideoPlanes`,
  so they go straight into G-07's video tile.

On Linux, SDL's PipeWire camera backend is turned on, so cameras behind the
portal work too. The camera permission on macOS (the `Info.plist` usage
string) stays packaging, which is Tessera's.

**Landed.** —

## G-06. Encoders and public codec access

**Declined.** `goldberry-media` stays a playback module: it decodes and does
not encode. Bring your own Opus and VP9 encoders, as Tessera's D-07 plans,
and decode through the public `Decoder` SPI, which is exported already.

**Landed.** The refusal is recorded as ADR-0689 (2026-11-09, on Goldberry's master, not yet in a snapshot).

## G-07. Video tile fed by app-pushed frames

**Works today, on the GPU.** `dev.goldberry.media.gpu.PictureRenderer` is
exported, and has been since before Tessera's snapshot. In a
`Canvas3dRenderer`:

```java
var picture = new VideoPlanes(...);   // or new VideoPicture(...) for BGRA
pictureRenderer.render(frame, picture, target);
```

It converts I420 to RGB in a shader, the same path `video-view` uses. The
rule to keep: a picture's bytes must stay valid until two more pictures have
been handed to the renderer.

**Goldberry's plan on top.** A public picture source (the latest picture plus a
"next picture due" signal), which `MediaPlayer` implements, and `video-view`
accepting any source. Then a call tile is a `video-view`, with its fit,
poster and styling.

**Landed.** —

## G-08. Web view JS bridge for Mini Apps

**Goldberry's fix.**

- `WebViewSpec.initScript(...)` runs a script before every page's own
  scripts, through `webview_init`. That is where `window.Telegram.WebApp` is
  defined.
- `WebViewController.eval(js)` runs script in the page from the app.
- `WebViewController.postMessage(json)` dispatches a message event in the
  page.

Page-to-app calls work already (`WebViewSpec.on`).

"Painted overlays cannot cover the page" is not changing: a page is a native
child window.

**Landed.** Built on Goldberry's master (not yet in a snapshot), ADR-0684. `WebPage.of(url).initScript("window.Telegram = …")` (or `WebView.initScript`), `controller.eval(js)`, `controller.postMessage(json)`. Proven on WebKitGTK: the page's first inline script sees the init script's global. Needs the natives rebuilt (web view ABI 9). macOS and Windows go through upstream `webview_init`, which those engines implement.

## G-09. Secure credential storage

**Goldberry's fix**, reversing its own earlier refusal (Goldberry ADR-0291),
because Tessera is the second application to ask. `Keychain` stores, loads and
deletes a secret by service and account:

- Linux: the Secret Service (GNOME Keyring, KWallet) over D-Bus.
- macOS: the Keychain, through Security.framework.
- Windows: the Credential Manager.

These are system libraries only, with no new native dependency. Then
`CredentialStore` gets a second implementation, and `CredentialFile` stays
the fallback where no Secret Service runs.

**Landed.** —

## G-10. Deep links and single instance

**Goldberry's fix**, reversing the same refusal:

- **macOS.** SDL delivers an opened URL as a drop with no window, and Goldberry
  used to discard it. It now arrives through `Application.onOpenUrl(URI)`.
- **Single instance.** `SingleInstance` holds a lock under the application's
  data directory. A second launch hands its arguments to the first over a
  local socket and exits. The first hears them through the same
  `onOpenUrl`, on the UI thread. This replaces Tessera's own lock file and
  socket in `client/app`.
- **Registration** stays packaging: the `.desktop` file's
  `x-scheme-handler/tessera`, `CFBundleURLTypes` in `Info.plist`, and a
  registry key written by the installer. A library cannot install itself.

**Landed.** Built on Goldberry's master (not yet in a snapshot), ADR-0683. Override `Application.singleInstance()` with an id and `Application.onOpenUrl(URI)`; a second `tessera` launch hands `tessera://…` to the running window and exits, and the macOS URL arrives the same way. `dev.goldberry.instance.SingleInstance` is public for a launcher of Tessera's own. The macOS half is unverified on a Mac.

## G-12. Board canvas: gestures, hit-testing, tile cache

**Goldberry's plan.**

- Pinch from trackpads and touchscreens, as a pointer event with a scale. SDL
  3.4 reports it on macOS, Wayland and X11. Windows has no pinch in SDL.
- `Path.contains(x, y, fillRule)`.

The tile cache stays Tessera's: what a tile is, and when it is stale, is the
Board's knowledge. G-30's zero-copy rasters are what makes its upload cheap.

**Landed.** `Path.contains(x, y)` (`nonzero`) and `Path.contains(x, y, true)`
(`evenodd`), on Goldberry's master, not yet in a snapshot, ADR-0687.
Curves are flattened to a hundredth of a unit and open sub-paths count as
closed, as a fill draws them. A box with `clip-path: path(…)` is now hit by
the pointer inside the path too, holes left out.

Pinch landed too (ADR-0692): a `PointerEvent.Kind.PINCH` where the pointer
is, with `event.scale()` (the factor since the last change) and
`event.pinchPhase()` (`BEGAN`, `CHANGED`, `ENDED`). It bubbles like a wheel,
so a Board canvas's `Input` can handle it beside `WHEEL`:

```java
case PINCH -> board.zoomBy(event.scale(), event.content().x(), event.content().y());
```

macOS, Wayland and X11 report it. On Windows a touchpad's zoom still arrives
as `Ctrl`+wheel.

## G-13. `font-feature-settings`

**Goldberry's fix.** `font-feature-settings` (`"tnum"`, `"case"`, `"calt"` and
any other tag) and `font-variant-numeric: tabular-nums` in stylesheets,
inherited as CSS says, and passed to HarfBuzz when shaping.

**Landed.** —

## G-15. Backdrop blur

**Goldberry's plan.** `backdrop-filter` blurs what is already painted under the
box. Over a GPU layer (a `canvas3d`, video on the GPU) the box draws its
background instead, which is the opaque fallback Tessera uses today, so frost
over a call's video tiles stays opaque.

**Landed.** —

## G-16. Layout (FLIP) animation

**Goldberry's plan.** An opt-in on keyed widgets: when layout moves or resizes
one, it animates from its old box to its new one. A tile that moves to
another parent in the call grid keeps its identity through its key.

**Landed.** —

## G-17. Screen-reader bridge

**Still on hold** (Goldberry ADR-0440). The semantics tree stays, so naming
every control and testing the tree, as Tessera does, is the right
preparation.

## G-18. Runtime text scaling

**Goldberry's fix.** `host.textScale(double)` changes the text scale of every
window at run time (0.9 to 1.5, as the renderer clamps). Its starting value
comes from `Application.textScale()`. A Settings row can call it directly.

**Landed.** Built on Goldberry's master (not yet in a snapshot), ADR-0678. `Application.textScale()` for the starting value (clamped, and an unreadable value is 1), `host.textScale(1.25)` from the Settings row, and `Offscreen.of(w, h).textScale(1.25)` or a session's `host().textScale(…)` in tests. Only text scales: boxes given a fixed size do not grow.

## G-19. Notifications on Windows

**Goldberry's plan.** A click on a Windows notification runs its action, as on
Linux and macOS. The badge becomes an overlay icon on the taskbar button.
Both are written against Windows' documentation, and will be marked
unverified until Goldberry's Windows lane drives them.

**Landed.** —

## G-20. Drag out to the OS

**Goldberry's plan.** An in-app drag that leaves the window becomes an OS drag
carrying files or an image, on Windows, macOS, X11 and Wayland. SDL has no
drag source, so this is platform code of Goldberry's own.

**Today.** "Save as…" and the clipboard, as the entry says.

**Landed.** —

## G-22. SVG rendering

**Goldberry's plan.** `goldberry-vector`, a module on ThorVG (MIT): an
`svg-view`, and SVG files accepted wherever an image is, once the module is
on the path. Then the 40 library shapes can be SVG files rather than paths
built from a JSON spec.

**Landed.** —

## G-23. PDF page rendering

**Still parked**, on the question of shipping a prebuilt PDFium. Thumbnails
from the Media service, as the entry plans, remain the answer.

**Landed.** ADR-0690 records it as parked, not declined (2026-11-09, on Goldberry's master): a record of PDFium's provenance comes first.

## G-24. HTML for mail bodies

**Goldberry's plan.** `html-view` applies a subset of inline `style=` and
`<style>`, scoped to the view, so one mail's rules cannot reach the shell.
How author colours meet the theme is still being designed.

**Today.** As the entry says: a server-side subset.

**Landed.** —

## G-25. Spellcheck

**Goldberry's plan.** Hunspell, built from source into its own native library,
checks text inputs off the UI thread. Misspellings get a wavy underline, and
suggestions appear in the text menu. Dictionaries come from the application:
their licences differ per language, so Goldberry ships none.

**Landed.** —

## G-26. Pluralization and locale message formatting

**Goldberry's plan.** `dev.goldberry.i18n`:

- CLDR plural rules for every locale, generated from CLDR's data.
- A formatter for ICU's `{count, plural, one {# message} few {# messages} …}`
  and `select`.
- Goldberry's own labels ("Copy", "Paste") made translatable.

Tessera's `client/core/.../i18n` can then delegate to it.

**Landed.** `dev.goldberry.i18n`, on Goldberry's master, not yet in a snapshot, ADR-0686:

```java
var files = IcuMessage.parse(
        "{count, plural, one {# файл} few {# файла} many {# файлов} other {# файла}}");
files.format(Locale.forLanguageTag("ru"), Map.of("count", 21));   // "21 файл"
PluralRules.of(Locale.forLanguageTag("ru"), PluralType.CARDINAL).select(5);   // MANY
```

- `PluralRules`: CLDR 49's cardinal and ordinal rules for every locale, with
  UTS #35 operands, so `1` and `1.0` differ where a language says so. Every
  sample number CLDR lists is checked against them in Goldberry's tests.
- `IcuMessage`: `{x}`, `{x, number}`, `plural`/`selectordinal` with `=n`,
  `offset:` and `#`, `select`, and ICU's apostrophe quoting. A plural without
  `other` is refused when parsed. Dates and `choice` are not parsed.
- `Labels.use(bundle)`: the toolkit's own words (the text menu, close and
  dismiss buttons, the tour's Back/Next/Done, the media player's names) read
  from a `ResourceBundle` under `goldberry.*` keys, English where a key is
  missing.

## G-29. OS-wide global hotkeys

**Goldberry's plan.** `GlobalShortcuts` delivers press and release (what
push-to-talk needs) while the app is unfocused:

- Wayland: the GlobalShortcuts portal. The user approves each shortcut.
- X11: a key grab.
- Windows: a hotkey plus a keyboard hook for the release.
- macOS: Carbon hotkeys.

**Landed.** —

## G-30. Shared memory between `Canvas3d` layers and Board tiles

**Goldberry's plan.** Tessera's answer was real zero-copy: a raster that
Blend2D paints into and the GPU samples with no staging copy, where the
device allows it, and staging elsewhere. It starts with a design document,
because it differs per driver.

**Today.** Tiles as textures in a `Canvas3dRenderer`, uploaded by damaged
region with `CopyPass.upload(texture, buffer, regions)`.

**Landed.** —

## G-31. Goldberry on the web

**Goldberry's plan.** A plan document first, weighing TeaVM (Java to
JavaScript/Wasm) against a GWT-like compiler of Goldberry's own. Either way,
the native drawing and text layers need a browser replacement. Nothing is
built until that plan has been read.

**Landed.** —

## G-32. Web view: private store, cookies, user agent

**Goldberry's plan.**

- `WebViewController.deleteCookies(...)` and `clearData()`.
- A user agent on `WebViewSpec`.
- An ephemeral (private) store on `WebViewSpec`, on all three engines, so a
  sign-out leaves no IdP session behind.

**Landed.** —
