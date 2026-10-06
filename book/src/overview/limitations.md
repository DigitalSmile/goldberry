# Limitations

<p class="gb-lede">What Goldberry does not do, in one place. Each line says whether the gap is deliberate, deferred or in progress, and why.</p>

The gaps below are the ones an application developer meets first.
[TODO](../TODO.md) is the complete list, entry by entry, and
[Status](../status.md) is what is built.

<span class="gb-pill absent">not scheduled</span> is a decision.
<span class="gb-pill partial">deferred</span> has a seam in the design for it.
<span class="gb-pill built">in progress</span> has code and an open end.

## Accessibility

| Gap | State | Why |
|---|---|---|
| Screen readers. Nothing on any platform reads the UI aloud | <span class="gb-pill absent">not scheduled</span> | Every widget carries a role and an accessible name in a semantics tree, and no bridge connects that tree to a platform accessibility API |

The rest of the baseline is in place: every control is reachable by keyboard,
the focus ring is drawn, contrast meets WCAG AA, hit targets are 32 pixels,
reduced motion is honoured and text scales to 150%.

## Platforms

| Gap | State | Why |
|---|---|---|
| Windows on ARM and macOS on Intel | <span class="gb-pill absent">not scheduled</span> | Four native artifacts, not six. Both targets are a shrinking share of desktops |
| A web view on Wayland | <span class="gb-pill absent">not scheduled</span> | A page is a child window placed over the widget's box. Wayland allows neither reparenting a foreign surface nor placing a window where a widget is, so there the widget opens nothing and says why |
| A web view on Windows | <span class="gb-pill partial">deferred</span> | Written against WebView2. It is not verified on a Windows desktop |
| H.264, HEVC, AAC, AC-3 and E-AC-3 on Windows | <span class="gb-pill partial">deferred</span> | They play through Windows' own decoders, which are off unless `-Dgoldberry.media.mediaFoundation=true`. FFmpeg is built for all four targets, so VP8, VP9, AV1, Opus, Vorbis, FLAC and MP3 play everywhere |
| Frame evidence on three platforms | <span class="gb-pill built">in progress</span> | The frame budget is measured on Linux. On Windows and macOS frames are painted and timed while the window is resized, and no budget is held, because a budget needs a display somebody chose |
| The GPU lane in CI | <span class="gb-pill built">in progress</span> | Composition, `canvas3d` and GPU video are measured on Metal and run on Linux under X11. They are not verified on a software Vulkan device |
| A native image on macOS and Windows | <span class="gb-pill built">in progress</span> | The showcase's native image is built on every platform. The macOS and Windows images are not verified |

## Text

| Gap | State | Why |
|---|---|---|
| Bidirectional text | <span class="gb-pill partial">deferred</span> | Right-to-left runs are shaped in logical order and drawn mirrored. The paragraph says so rather than refusing |
| IME preedit | <span class="gb-pill partial">deferred</span> | Committed text from an input method arrives and a field takes it. The underlined in-progress string is not drawn |
| Rich text | <span class="gb-pill partial">deferred</span> | A paragraph has one style. Markdown emphasis is a faux oblique, and a line of mixed faces is a row of words rather than one shaped run |
| Justification and hyphenation | <span class="gb-pill absent">not scheduled</span> | Both need an HTML engine, and `html-view` is a document and not an engine |

## Widgets and content

| Gap | State | Why |
|---|---|---|
| Rows of varying height in a virtual `list` or `table` | <span class="gb-pill partial">deferred</span> | Virtualization is index times height |
| An HTML engine | <span class="gb-pill absent">not scheduled</span> | `html-view` is a parser and a fold into widgets. No scripting, no network, no navigation, no `<style>` applied |
| PDF, code, terminal, vector, camera and microphone modules | <span class="gb-pill partial">deferred</span> | The content modules are Markdown and HTML, emoji, and media. No module exists for the others |
| Platform drag and drop | <span class="gb-pill partial">deferred</span> | A file dropped on a window arrives with its position. Dragging out of the application is not built |
| Custom image cursors | <span class="gb-pill partial">deferred</span> | The standard set is bound. `grab` and `grabbing` fall back to `move` |
| Animated WebP and GIF | <span class="gb-pill partial">deferred</span> | A GIF decodes to its first frame. An animated WebP does not decode |

## Styling and motion

| Gap | State | Why |
|---|---|---|
| Transitions on layout properties | <span class="gb-pill absent">not scheduled</span> | The whitelist is `opacity`, `background-color`, `border-color`, `color` and `transform`. Animating a width would run layout every frame. A declaration outside the list is dropped with a warning |
| `@keyframes` and an animation controller | <span class="gb-pill partial">deferred</span> | There is no `@keyframes` and no controller. A perpetual loop is a function of the clock and needs no state |
| `:not()` and other selector functions | <span class="gb-pill absent">not scheduled</span> | The router refuses to set `:hover` on a disabled widget instead, which is one choke point for every control |
| Variable font weights | <span class="gb-pill absent">not scheduled</span> | A weight is a face. Inter ships 400 and 600, upright and italic, and a CSS weight resolves to the nearer face |

## Build and distribution

| Gap | State | Why |
|---|---|---|
| A Gradle plugin that picks the platform's natives jar | <span class="gb-pill partial">deferred</span> | A BOM knows versions, not platforms, and module-metadata variants cannot match a consumer that declares no operating system. An application adds the classifier jars itself |
| A Maven plugin for the weaver | <span class="gb-pill partial">deferred</span> | `exec-maven-plugin` runs the weaver at `process-classes`, which is the phase for it ([Model weaving](../weaving.md#maven)) |

## By design

Some absences are the design rather than a gap:

- **No hand-written platform backends and no AWT bridge.** SDL3 is the
  desktop layer.
- **No expressions in markup.** A `bind=` is a dotted path. Negation, formatting
  and conditionals stay in Java, where they are testable.
- **No `repaint()` in an application.** A value that changed asks for its own
  frame.
- **No global font or icon cache.** A `Fonts` book and an `Icons` registry are
  opened by the application and closed by it, because they hold native memory.
- **No mobile or touch profile.** Desktop only.
