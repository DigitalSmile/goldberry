# Limitations

<p class="gb-lede">What Goldberry does not do, in one place, and why.</p>

The gaps below are the ones an application developer meets first.

## Accessibility

| Gap | Why |
|---|---|
| Screen readers. Nothing on any platform reads the UI aloud | Every widget carries a role and an accessible name in a semantics tree, and no bridge connects that tree to a platform accessibility API |

The rest of the baseline is in place: every control is reachable by keyboard,
the focus ring is drawn, contrast meets WCAG AA, hit targets are 32 pixels,
reduced motion is honoured and text scales to 150%.

## Platforms

| Gap | Why |
|---|---|
| Windows on ARM and macOS on Intel | Four native artifacts, not six. Both targets are a shrinking share of desktops |
| A web view on Wayland | A page is a child window placed over the widget's box. Wayland allows neither reparenting a foreign surface nor placing a window where a widget is, so there the widget opens nothing and says why |
| H.264, HEVC, AAC, AC-3 and E-AC-3 on Windows | They play through Windows' own decoders, which are off unless `-Dgoldberry.media.mediaFoundation=true`. VP8, VP9, AV1, Opus, Vorbis, FLAC and MP3 play everywhere through FFmpeg |

## Text

| Gap | Why |
|---|---|
| Bidirectional text | Right-to-left runs are shaped in logical order and drawn mirrored. The paragraph says so rather than refusing |
| IME preedit | Committed text from an input method arrives and a field takes it. The underlined string still being composed is not drawn |
| Rich text | A paragraph has one style. Markdown emphasis is a faux oblique, and a line of mixed faces is a row of words rather than one shaped run |
| Justification and hyphenation | Both need an HTML engine, and `html-view` is a document and not an engine |

## Widgets and content

| Gap | Why |
|---|---|
| Rows of varying height in a virtual `list` or `table` | Virtualization is index times height |
| An HTML engine | `html-view` is a parser and a fold into widgets. No scripting, no network, no navigation, no `<style>` applied |
| PDF, code, terminal, vector, camera and microphone modules | The content modules are Markdown and HTML, emoji, and media. No module exists for the others |
| Platform drag and drop | A file dropped on a window arrives with its position. Nothing can be dragged out of the application |
| Custom image cursors | The standard set is bound. `grab` and `grabbing` fall back to `move` |
| Animated WebP and GIF | A GIF decodes to its first frame. An animated WebP does not decode |

## Styling and motion

| Gap | Why |
|---|---|
| Transitions on layout properties | The whitelist is `opacity`, `background-color`, `border-color`, `color` and `transform`. Animating a width would run layout every frame. A declaration outside the list is dropped with a warning |
| `@keyframes` and an animation controller | There is no `@keyframes` and no controller. A perpetual loop is a function of the clock and needs no state |
| `:not()` and other selector functions | The router refuses to set `:hover` on a disabled widget instead, which is one choke point for every control |
| Variable font weights | A weight is a face. Inter ships 400 and 600, upright and italic, and a CSS weight resolves to the nearer face |

## Build and distribution

| Gap | Why |
|---|---|
| A Gradle plugin that picks the platform's natives jar | A BOM knows versions, not platforms, and module-metadata variants cannot match a consumer that declares no operating system. An application adds the classifier jars itself |
| A Maven plugin for the weaver | `exec-maven-plugin` runs the weaver at `process-classes`, which is the phase for it ([Ahead-of-time binding](../ahead-of-time-binding.md#maven)) |

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
