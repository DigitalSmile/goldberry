# Limitations

<p class="gb-lede">What Goldberry does not do today, in one place. Each line says whether the gap is deliberate, deferred or in progress, and links the record that explains it.</p>

Goldberry is pre-release. No release tag has been cut, snapshots are published on
every push, and the first version is the `2026.1` line. The gaps below are the
ones an application developer meets first. [TODO](../TODO.md) is the complete
list, entry by entry, and [Status](../status.md) is what is built.

<span class="gb-pill absent">not scheduled</span> is a decision.
<span class="gb-pill partial">deferred</span> has a seam waiting for it.
<span class="gb-pill built">in progress</span> has code and an open end.

## Accessibility

| Gap | State | Why |
|---|---|---|
| Screen readers. Nothing on any platform reads the UI aloud | <span class="gb-pill absent">not scheduled</span> | The AccessKit bridge is on hold. The semantics tree stays: every widget carries a role and an accessible name, and a sweep enforces it ([ADR-0440](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0440-the-accessibility-bridge-is-on-hold-and-the-semantics-tree-stays.md)) |

The rest of the baseline is built and checked: every control is reachable by
keyboard, the focus ring is real, contrast is authored to WCAG AA and tested,
hit targets are 32 pixels, reduced motion is honoured and text scales to 150%.

## Platforms

| Gap | State | Why |
|---|---|---|
| Windows on ARM and macOS on Intel | <span class="gb-pill absent">not scheduled</span> | Four native artifacts, not six. Neither target has been built and both are a shrinking share ([ADR-0041](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0041-three-platforms-four-artifacts-two-backends.md)) |
| A web view on Wayland | <span class="gb-pill absent">not scheduled</span> | A page is a child window placed over the widget's box. Wayland allows neither reparenting a foreign surface nor placing a window where a widget is, so there the widget opens nothing and says why ([ADR-0442](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0442-a-page-is-a-child-window-where-the-window-system-allows-one.md)) |
| A web view on Windows | <span class="gb-pill partial">deferred</span> | Written against WebView2 and not yet verified on a Windows desktop |
| Media on Windows and on `linux-aarch64` | <span class="gb-pill partial">deferred</span> | Written and untested. FFmpeg is built for `linux-x64` and `macos-aarch64` today; a release refuses to ship without all four ([ADR-0495](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0495-media-is-published-and-snapshots-publish-again.md)) |
| Frame evidence on three platforms | <span class="gb-pill built">in progress</span> | The budget is measured on one Linux machine. CI paints 300 frames while resizing on each runner and asserts no budget, because a GPU-less runner has no display somebody chose ([ADR-0342](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0342-a-window-is-resized-from-outside-and-the-run-says-what-it-cost.md), [ADR-0452](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0452-a-refresh-budget-needs-a-display-somebody-chose.md)) |
| The GPU lane in CI | <span class="gb-pill built">in progress</span> | Composition, `canvas3d` and GPU video are measured on Metal and have run on Linux under X11. The lavapipe lane on CI has run and not yet reached a test ([ADR-0503](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0503-the-gpu-lane-finds-lavapipe-a-device-goes-before-sdl-and-a-gpu-golden-has-its-own-tolerance.md)) |
| A native image on macOS and Windows | <span class="gb-pill built">in progress</span> | Built on every platform on a release tag. The macOS and Windows traces are not reviewed ([ADR-0337](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0337-the-native-showcase-is-built-on-every-platform.md)) |

## Text

| Gap | State | Why |
|---|---|---|
| Bidirectional text | <span class="gb-pill partial">deferred</span> | Right-to-left runs are shaped in logical order and drawn mirrored. The paragraph says so rather than refusing ([ADR-0218](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0218-a-paragraph-approximates-bidi-rather-than-refusing-it.md)) |
| IME preedit | <span class="gb-pill partial">deferred</span> | Committed text from an input method arrives and a field takes it. The underlined in-progress string is not drawn |
| Rich text | <span class="gb-pill partial">deferred</span> | A paragraph has one style. Markdown emphasis is a faux oblique, and a line of mixed faces is a row of words rather than one shaped run ([ADR-0295](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0295-a-document-is-a-value-and-a-paragraph-is-a-row-of-words.md)) |
| Justification and hyphenation | <span class="gb-pill absent">not scheduled</span> | What an HTML engine would buy, and the reason there is none ([ADR-0298](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0298-html-is-a-document-and-not-an-engine.md)) |

## Widgets and content

| Gap | State | Why |
|---|---|---|
| Rows of varying height in a virtual `list` or `table` | <span class="gb-pill partial">deferred</span> | Virtualization is index times height ([ADR-0213](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0213-a-virtual-list-is-two-spacers-and-a-window.md)) |
| An HTML engine | <span class="gb-pill absent">not scheduled</span> | `html-view` is a parser and a fold into widgets. No scripting, no network, no navigation, no `<style>` applied ([ADR-0298](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0298-html-is-a-document-and-not-an-engine.md)) |
| PDF, code, terminal, vector, camera and microphone modules | <span class="gb-pill partial">deferred</span> | Specified in `docs/content-widgets.md`, and each waits on a named seam. Three of the eleven content modules are built |
| Platform drag and drop | <span class="gb-pill partial">deferred</span> | A file dropped on a window arrives with its position ([ADR-0330](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0330-a-dropped-file-arrives-somewhere.md)). Dragging out of the application is not built |
| Custom image cursors | <span class="gb-pill partial">deferred</span> | The standard set is bound. `grab` and `grabbing` fall back to `move` ([ADR-0057](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0057-the-cursor-rides-on-the-painted-box.md)) |
| Animated WebP and GIF | <span class="gb-pill partial">deferred</span> | A GIF decodes to its first frame. An animated WebP does not decode ([ADR-0329](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0329-two-more-codecs-one-fetched-and-one-written.md)) |

## Styling and motion

| Gap | State | Why |
|---|---|---|
| Transitions on layout properties | <span class="gb-pill absent">not scheduled</span> | The whitelist is `opacity`, `background-color`, `border-color`, `color` and `transform`. Animating a width would run layout every frame. A declaration outside the list is dropped with a warning ([ADR-0067](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0067-motion-is-an-overlay-on-a-frame-clock.md)) |
| `@keyframes` and an animation controller | <span class="gb-pill partial">deferred</span> | A perpetual loop is a function of the clock and needs no state. The controller's subjects have not appeared ([ADR-0081](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0081-a-perpetual-loop-has-no-state.md)) |
| `:not()` and other selector functions | <span class="gb-pill absent">not scheduled</span> | The router refuses to set `:hover` on a disabled widget instead, which is one choke point for every control |
| Variable font weights | <span class="gb-pill absent">not scheduled</span> | A weight is a face. Inter ships 400 and 600, upright and italic, and a CSS weight resolves to the nearer face ([ADR-0066](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0066-a-weight-is-a-face-and-color-inherits.md), [ADR-0323](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0323-an-italic-is-a-face-and-the-matrix-closes.md)) |

## Build and distribution

| Gap | State | Why |
|---|---|---|
| A Gradle plugin that picks the platform's natives jar | <span class="gb-pill partial">deferred</span> | A BOM knows versions, not platforms, and module-metadata variants cannot match a consumer that declares no operating system. An application adds the classifier jars itself ([ADR-0438](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0438-a-jvm-consumer-carries-no-platform-so-a-variant-has-nothing-to-match.md)) |
| A Maven plugin for the weaver | <span class="gb-pill partial">deferred</span> | `exec-maven-plugin` runs the weaver at `process-classes`, which is the phase for it ([Model weaving](../weaving.md#maven)) |
| A first release | <span class="gb-pill built">in progress</span> | The publishing chain reaches Central on every push. What a release waits on is a tag and FFmpeg for two targets |

## By design

Some absences are the design rather than a gap, and will not change:

- **No hand-written platform backends and no AWT bridge.** SDL3 is the
  permanent desktop layer ([ADR-0003](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0003-sdl3-as-the-only-desktop-backend.md)).
- **No expressions in markup.** A `bind=` is a dotted path. Negation, formatting
  and conditionals stay in Java, where they are testable ([ADR-0062](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0062-bind-is-a-path-and-nothing-else.md)).
- **No `repaint()` in an application.** A value that changed asks for its own
  frame ([ADR-0128](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0128-a-change-is-its-own-frame-request.md)).
- **No global font or icon cache.** A `Fonts` book and an `Icons` registry are
  opened by the application and closed by it, because they hold native memory
  ([ADR-0044](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0044-one-face-many-sizes.md)).
- **No mobile or touch profile.** Desktop only, in this version.
