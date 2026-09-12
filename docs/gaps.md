# What brd needs from Goldberry

brd is an application on Goldberry, and this is the queue of things it cannot do without the toolkit
growing them. It exists because of one rule, written down as
[ADR-0013](../book/src/adr/0013-no-reimplementation-of-goldberry-capabilities.md): **brd does not reimplement what
belongs in Goldberry.** Drawing, input, text, windows and platform integration are the toolkit's;
boards, notes, CRDT sync and agents are brd's. When brd hits the line, the answer is an entry here —
not a workaround that quietly becomes a second toolkit.

Updated 2026-09-12.

---

## 0. Where the line is

Two facts decide what counts as a leak, and both come from Goldberry's own documentation rather than
from taste:

- **`paint.Painter` and `paint.Frame` are application API.** `Painter`'s javadoc: *"The one place an
  application is handed the toolkit's own rasterizer."* A `canvas` widget exists to hand them over. Using
  them is not a leak.
- **`natives.*` is not.** `:natives` is the FFM layer, and its types are not meant to escape it —
  Goldberry's own ADR-0174 and its module graph say so.

Measured against that, here is what brd imports from Goldberry today, and what the showcase — the
sanctioned pattern — imports:

| | brd-app | Goldberry's `:example` (Showcase) |
|---|---|---|
| `widgets.*`, `widget.*`, `css.*`, `icon.*`, `render.model.*`, `Application`/`Host`/`Goldberry` | yes | yes |
| `paint.Painter` / `paint.Frame` | yes (the canvas painter) | once (`paint.Box`, in a custom widget) |
| **`natives.blend2d.*`** | ~~yes~~ **closed and sealed** — `paint.Path`, `paint.Stroke`, `paint.Gradient` | never |
| **`natives.yoga.*`** | no | ~~yes~~ **closed and sealed** — `io.…goldberry.layout` |
| `natives.harfbuzz.*`, `natives.blend2d` fonts | no | ~~only inside `:core`~~ **closed and sealed** — `paint.GlyphPen`, `text.ShapedRun` |

**This section was wrong when it was written, and the correction matters.** It
measured the leak by what *brd* imports, and concluded there was "exactly one
leak, in one file". That was true of brd and false of the toolkit.

`natives.blend2d` was one of two families. The other is `natives.yoga`, and it
comes through **`paint.Box` and `css.ComputedStyle`** — thirteen Yoga-typed record
components each, plus `BoxPainter.Placed`, `paint.tree.ContainingBlock`,
`widget.style.Corner.insets` and `css.value.CssLength.parse`, whose *return type*
is Yoga's `StyleLength`. `Box` is what every custom widget returns from `render()`,
so the sanctioned pattern reads Yoga; `:example` imports `FlexDirection` too. That
is ~99 importing file/type pairs and roughly a thousand references across
`:widgets` and the test source sets.

The root cause was neither file. It was the module graph: `:core` declared
`requires transitive io.github.digitalsmile.goldberry.natives` and `:natives`
exported its wrapper packages unqualified, so every application that required
`:widgets` could see both families.

Both are closed — [G1](#g1), [G2](#g2) and [G10](#g10) for drawing,
[G13](#g13) for layout, [G14](#g14) for the last method of the text stack — and
**all three wrapped libraries are sealed**: `:natives` exports Blend2D, Yoga and
HarfBuzz to `:core` and to nobody else, and `:core` no longer requires `:natives`
`transitive`ly. A module that names a `BlendPath` or a `StyleLength` does not
compile. The rule at the top of this section is a compiler error now rather than
a rule.

---

## 1. The list

| # | Gap | Blocks | Priority |
|---|---|---|---|
| ~~[G1](#g1)~~ | ~~`Path` and `Stroke` value types in `core.paint`~~ | **closed** — ADR-0277 | done |
| ~~[G2](#g2)~~ | ~~Dashed strokes~~ | **closed** — ADR-0278 | done |
| ~~[G3](#g3)~~ | ~~Canvas input: pointer, wheel, keys, capture, cursor~~ | **closed** — ADR-0281 | done |
| ~~[G4](#g4)~~ | ~~An image primitive: decode bytes, draw into a frame~~ | **closed** — ADR-0283 | done |
| ~~[G5](#g5)~~ | ~~Shipped offscreen render + PNG encode~~ | **closed** — ADR-0283, ADR-0284 | done |
| ~~[G6](#g6)~~ | ~~In-canvas text editing: caret, selection, undo~~ | **closed** — ADR-0285; IME preedit is [G15](#g15) | done |
| ~~[G7](#g7)~~ | ~~Clipboard beyond text: images and custom types~~ | **closed** — ADR-0286 | done |
| [G8](#g8) | `goldberry-html`: Markdown through md4c | Note preview (E1), Note → HTML in the Viewer (D1) | medium |
| ~~[G9](#g9)~~ | ~~Native file dialogs~~ | **closed** — ADR-0287 | done |
| ~~[G10](#g10)~~ | ~~Gradients as a `core.paint` value~~ | **closed** — ADR-0277 | done |
| ~~[G11](#g11)~~ | ~~The computed font of a box, inside a painter~~ | **closed** — ADR-0288 | done |
| ~~[G12](#g12)~~ | ~~Deep-link URI handling and single-instance handoff~~ | **not Goldberry's** — ADR-0291 | answered |
| ~~[G13](#g13)~~ | ~~The other `natives` leak: Yoga through `paint.Box`~~ | **closed** — ADR-0279, ADR-0280 | done |
| ~~[G14](#g14)~~ | ~~The last `natives` leak: **one method**, `Frame.drawGlyphs`~~ | **closed** — ADR-0290 | done |
| ~~[G15](#g15)~~ | ~~IME preedit: the composition string, inline~~ | **closed** — ADR-0289; the fields are [G16](#g16) | done |
| ~~[G16](#g16)~~ | ~~IME preedit in `text-input`~~ | **closed** — ADR-0292; `text-area` too | done |

**Not gaps** — available today, and brd must use them rather than grow its own:

- **Text on a canvas.** `Font.bundled(BundledFont.UI | UI_STRONG | CODE | EMOJI, size)`,
  `Paragraph.of(font, text)`, `Paragraph.paint(frame, x, top, maxWidth, argb)`, `Font.draw(frame, …)`.
  None of it touches `natives`. brd's stickies are wordless because brd has not done this yet, not
  because it cannot.
- **Editing text on a canvas.** `text.edit.Editor` over a `canvas`: a caret, a selection, word jumps,
  `Home`/`End` on the visual line, undo that folds a typing run, the clipboard — and, since
  [G15](#g15), the IME's composition string with its converting clause and a candidate window that
  lands under the caret. None of it a `text-input`, and nothing left out.
- **Images on a canvas.** `Image.decode(bytes | file)`, `Frame.drawImage(...)` with a crop and an alpha,
  and `Image.encodePng()` — all of it in `io.github.digitalsmile.goldberry.image`, none of it touching
  `natives`, and nothing to close. There is no `img` *widget* yet; a `canvas` is how an image is drawn
  today, which is what brd's board is anyway.
- **Widget catalogue**: menus, dialogs, toasts, tree, tabs, table, cards, masonry, scroll, text inputs,
  charts. The assistant panel, the board list and the settings dialog are compositions of these.
  `text-input` and `text-area` take an input method inline ([G16](#g16)); a `password` deliberately
  does not, because a candidate window is an unmasked window.
- **Stylesheets and theming**: `Controls.stylesheets(Theme[, Density])`, the cascade, `Icons`.
- **Markup and binding**: `Widgets.inflater(...)` over KDL, `@Bind`/`@Action` with `Models`.
- **Shortcuts, overlays, popups, tray**: `Host.shortcut(...)`, `Host.overlay(...)`, `Popup`, tray SPI.
- **Native file dialogs**: `Host.fileDialog(FileDialogSpec.saveFile()..., choice -> …)` — open, save and
  folder, asynchronous, with filters and a starting directory ([G9](#g9)).
- **A painter that follows the theme**: a three-parameter `Canvas` painter is handed the node's own
  resolved font and colour, this frame's time and `reducedMotion` ([G11](#g11)).
- **The clipboard, both halves**: text, and bytes under any MIME type, with `Image.fromClipboard` /
  `toClipboard` for the common one.
- **Cursors**, **headless backend**, **start-up timeline** (`Startup`), **frame stats**/HUD.

---

## 2. The gaps, in detail

<a id="g1"></a>
### G1 — `Path` and `Stroke` in `core.paint` — **closed**

Landed as [ADR-0277](../book/src/adr/0277-a-path-is-a-value-and-the-rasterizers-is-package-private.md).

```java
public void fillPath(Path path, int argb);
public void fillPath(double x, double y, Path path, int argb);
public void fillPath(Path path, Gradient gradient);
public void strokePath(Path path, Stroke stroke, int argb);
public void strokePath(double x, double y, Path path, Stroke stroke, int argb);
```

`Path` is immutable, built from `Path.builder()` or the factories (`line`,
`polyline`, `rect`, `roundRect`, `ellipse`, `circle`, `arc`), and readable through
a sealed `Path.Segment` of six records — which is what an SVG export needs.
`Stroke` carries width, `Cap`, `Join`, miter limit and `Dash`.

The origin overloads are an addition to what was proposed here, and they matter:
one path drawn at several places without being rebuilt is how `Icon.draw` and a
chart's readout work.

**The `BlendPath` overloads are gone from the public surface** — package-private
inside `paint`, where `:core`'s own painters still use them against a path the
frame pools. `Icon.path()` is now `Icon.outline()` returning a `Path`, and
`SvgPath.appendTo` takes a `Path.Builder`. `BoxPainter.paintOne` lost its
`BlendPath` parameter.

No golden image moved, in `:core`, `:widgets` or `:example`.

---

<a id="g2"></a>
### G2 — Dashed strokes — **closed**

Landed as [ADR-0278](../book/src/adr/0278-a-dash-is-goldberrys-arithmetic-and-not-the-rasterizers.md),
and not the way this entry assumed.

**Blend2D does not implement dashing.** It has the API and the state — a dash
array in `BLStrokeOptions`, validated, retained, copied, saved across
`save`/`restore` — and its stroker never reads it. `core/pathstroke.cpp` is 988
lines and the word "dash" does not appear in it. The calls return `BL_SUCCESS` and
the line comes out solid.

So dashing is Goldberry's, over `paint.Path`:

```java
public static Path Dasher.dash(Path path, Dash dash);     // paint.geom
public static Path Flattener.flatten(Path path);
```

A dashed stroke is a solid stroke of a different path, and `Frame.strokePath` does
it for you — a solid `Stroke` pays nothing, because the dasher hands back the very
path it was given. `Dash` carries the pattern and an offset, so brd's marching-ants
ghost layer is one number changing per frame.

The native surface gained exactly one symbol —
`bl_context_set_stroke_miter_limit`, which *is* implemented — rather than the six
this entry's plan assumed.

---

<a id="g3"></a>
### G3 — Input on a canvas — **closed**

Landed as [ADR-0281](../book/src/adr/0281-a-canvas-hears-what-it-draws-on.md).

```java
new Canvas(painter, event -> {
    var at = event.content();            // canvas coordinates, like the painter's
    switch (event.kind()) {
        case PRESSED -> board.beginDrag(at.x(), at.y());
        case MOVED   -> board.dragTo(at.x(), at.y());
        case WHEEL   -> board.zoom(event.deltaY(), at.x(), at.y());
        default      -> { }
    }
});
```

**This entry was wrong about the toolkit.** It said "there is no wheel, no drag,
no key, no pointer capture, no per-widget cursor". All five existed and were
tested; what did not exist is that `Canvas` implemented `Handles`, so a painter
was handed a frame and no events. The gap was one widget's declaration.

So most of what the sketch asked for needs nothing:

- **`capturePointer()` is the default.** The router captures on press for every
  widget and releases on the matching release, so a marquee dragged off the edge
  keeps reporting.
- **The wheel is a `PointerEvent` of kind `WHEEL`**, with `deltaY()` for a
  touchpad's fraction and `ticksY()` for a mouse's detents.
- **The cursor is the stylesheet's** — `canvas { cursor: crosshair }`.
- **`event.consume()`** is how a zoomable board keeps the wheel from scrolling the
  pane it sits in.

**Read `event.content()`, not `local()`.** That is the one thing that had to be
built. A canvas painter draws inside the padding and is clipped there (ADR-0193),
so `local()` — measured from the border box — would have put every event a
padding's distance from its own ink. `content()` is measured from the rectangle
the painter was actually given, and there is one implementation of the arithmetic
(`Length.resolve`) so that paint and input cannot drift.

Keys need focus, and a canvas is focusable exactly when it has an `Input`: a
chart that took a Tab stop and did nothing would be a keyboard trap. A canvas is
a `Role.FIGURE` and its name comes from `Input.accessibleName()`.

---

<a id="g4"></a>
### G4 — An image primitive — **closed**

Landed as [ADR-0283](../book/src/adr/0283-an-image-is-a-value-and-the-decoder-is-the-one-thing-blend2d-allocates.md).

```java
package io.github.digitalsmile.goldberry.image;

public final class Image {                              // not AutoCloseable
    public static Image decode(byte[] bytes);           // PNG, JPEG, QOI
    public static Image decode(ByteBuffer bytes);
    public static Image decode(Path file);
    public static Image ofArgb(int width, int height, int[] argb);

    public PhysicalSize size();
    public PhysicalRect bounds();
    public int argb(int x, int y);
    public PixelBuffer pixels();                        // read-only
    public byte[] encodePng();
}
```

```java
frame.drawImage(image, x, y);                                 // natural size
frame.drawImage(image, x, y, width, height);
frame.drawImage(image, x, y, width, height, alpha);
frame.drawImage(image, source, x, y, width, height, alpha);   // the crop
```

**Two departures from what this entry proposed, and both matter to brd.**

**It is not `AutoCloseable`.** The decoder allocates, its pixels are copied into a
buffer Java owns, and the handle is destroyed before `decode` returns. An image
goes into a shape, a record or a cache with no lifetime beside it — which is what
a board full of them needs, and what a handle would have made brd's problem.

**It is `image.Image`, not `paint.Image`.** Three things want the value and only
one draws: a frame draws one, an offscreen render produces one ([G5](#g5)), a
clipboard carries one ([G7](#g7)). `paint` already depends on `render`, so
`paint.Image` would have pointed that dependency both ways.

`encodePng()` ships with it, so [G5](#g5) needs only the offscreen half now. The
source rectangle is a `render.model.PhysicalRect` in the image's own pixels and is
**checked** rather than clamped: a crop that runs off the edge is reported, not
quietly drawn smaller, because a crop in a document was written somewhere else.

Decoding is Blend2D's built-in codecs — three new symbols. Encoding is
`java.base`'s `Deflater` in `image.png.PngEncoder`, which is why it is three and
not ten.

---

<a id="g5"></a>
### G5 — Offscreen render and PNG encode — **closed**

Landed as [ADR-0284](../book/src/adr/0284-a-picture-with-no-window-under-it.md),
with the encode half in [ADR-0283](../book/src/adr/0283-an-image-is-a-value-and-the-decoder-is-the-one-thing-blend2d-allocates.md).

```java
var png = Offscreen.of(1200, 900)                       // physical pixels
        .scale(2f)                                      // optional; 1 by default
        .stylesheets(Controls.stylesheets(Theme.NORD_DARK))
        .fonts(fonts)                                   // optional; a book is opened if not
        .settle(200)                                    // optional; a virtual clock
        .background(0xFF2E3440)                         // optional; transparent by default
        .render(widget)                                 // or .paint(painter)
        .encodePng();
```

`io.github.digitalsmile.goldberry.offscreen`, not `render` — the backend SPI is
underneath the toolkit and this composes what is above it.

**What this entry was really asking for is the sequence, not the buffer.**
Painting a painter into a buffer was always four lines. Rendering a widget tree
is a three-pass dance — mount, measure, feed the regions back, advance a frozen
clock, measure again, and only then build-lay-out-and-paint — and every step of it
exists because leaving it out produces a picture that is quietly wrong: a banner
at zero opacity, a `text-area` wrapped as though it were narrow, a `masonry`
photographed mid-settle. That dance is the API now.

**It is deterministic by construction.** The clock is virtual and cannot be made
otherwise, so two requests for the same document are the same bytes — which is
what `GET /docs/{id}/preview.png` needs before it can cache anything.

Goldberry's own golden-image harness renders through it, so a hundred images at
three scales are a test of the entry point an application would use. Wiring that
up found a bug in the harness: it had never applied the rebuilds a frame
triggers, so nine gallery goldens had been photographing a `masonry` arrangement
the running application does not draw.

<a id="g6"></a>
### G6 — In-canvas text editing — **closed**, apart from IME

Landed as [ADR-0285](../book/src/adr/0285-a-caret-is-the-text-stacks-and-not-a-controls.md).

```java
var editor = new Editor(font)                   // io.github.digitalsmile.goldberry.text.edit
        .multiline(true)
        .wrapWidth(240)
        .clipboard(window.clipboard())
        .text(sticky.text());

new Canvas((frame, size) -> editor.paint(frame, 8, 8, ink, focused), new Input() {
    public void onPointer(PointerEvent e) { editor.pointerAt(x, y, e.modifiers().shift(), e.clickCount()); }
    public void onKey(KeyEvent e)         { if (editor.onKey(e)) e.consume(); }
    public void onText(TextEvent e)       { if (editor.onText(e.text())) e.consume(); }
    public void onFocusChanged(boolean focused, boolean fromKeyboard) { … }
});
```

**Most of it was built and in the wrong module.** `TextEdit` — the string, the
caret, the anchor and every movement as a pure function — and `EditHistory` —
undo, with a typing run folded into one step — were inside
`widgets.form.textinput`, which put the rules of text editing in the module that
draws text *fields*. They are `text.edit`'s now, beside the shaping they are
arithmetic over, and `text-input` reads them from there like anyone else.

What was genuinely missing is the second dimension, and it is `TextGeometry`:
where a caret is on a **wrapped** paragraph, what `Up` means when lines are not
the same length (a column is an *x*, not a character count), and what shape a
selection is when it spans a break. Plus a key map anything but a widget could
reach, which is `Editor` — `text-input`'s map, key for key.

For a sticky this is the whole of it: click, drag, double-click a word,
triple-click the lot, arrows and `Ctrl+`arrows, `Home`/`End` on the **visual**
line, `Shift` to extend, `Ctrl+Z`/`Ctrl+Shift+Z`, cut, copy and paste. The caret
is drawn only when the canvas has the focus, which `Input.onFocusChanged` is new
for — and `Input.wantsText()` is the switch that turns the **platform's** text
input on, without which a canvas receives arrow keys and never a character. That
call used to be `text-input`'s own; it is the router's now, beside the cursor.

**IME preedit was not in it** — it is [G15](#g15), and that is closed now too.
Committed text always worked, so a Latin keyboard was complete and an input
method's *result* arrived; what was missing is the inline composition a CJK user
sees while choosing, and `Editor.onPreedit` is it.

<a id="g7"></a>
### G7 — Clipboard beyond text — **closed**

Landed as [ADR-0286](../book/src/adr/0286-a-clipboard-write-is-an-offer.md).

```java
boolean has(String mime);                       // cheap: what has been advertised
byte[]  read(String mime);                      // a round trip to the owner
boolean write(String mime, byte[] bytes);
boolean write(Map<String, byte[]> byMime);      // one copy, several types, in order
boolean clear();
```

and the image half, which is **not** on the clipboard:

```java
Image.onClipboard(clipboard);                   // boolean
Image.fromClipboard(clipboard);                 // Optional<Image>
image.toClipboard(clipboard);                   // as image/png
```

**A departure from what this entry proposed, and the reason matters to brd.**
`hasImage()`/`image()`/`image(Image)` would have put the decoder inside the
backend SPI — every backend implementing a clipboard would have to know what a
PNG is. A clipboard is bytes and a MIME type; what makes those an image lives
beside the decoder. Your paste is `Image.fromClipboard(window.clipboard())`.

**A write is an offer, not a copy.** `SDL_SetClipboardData` advertises types and
asks for the bytes when somebody pastes, so the bytes stay in Goldberry's arena
until the offer is replaced. That matters to brd in one visible way: copying a
shape should hand over **both** the document's own format and a PNG, in that
order, so that pasting back into a board keeps the shape and pasting into a chat
window gets a picture. `write(Map)` in a `LinkedHashMap` is how that is said.

Reading an image tries `image/png`, `image/jpeg` and `image/qoi` — the formats the
decoder has. A clipboard offering only WebP finds nothing rather than throwing;
bytes advertised as a PNG that are not one raise `ImageDecodeException`, because
the clipboard lied and a paste command should say so.

<a id="g8"></a>
### G8 — `goldberry-html`: Markdown through md4c

**Today.** It does not exist. Plan §1 already names it as the path: *"Markdown: md4c via goldberry-html —
planned module."*

**Needed for.** The Note editor's live preview (E1) and `GET /docs/{id}/body.html` for the Note Viewer
(D1). brd serves raw Markdown today and says so.

**Why it is Goldberry's.** It is a native dependency plus a renderer into the toolkit's own text and box
model — the same shape as every other thing in `:natives`. A Markdown parser vendored into brd would be
a second text stack.

---

<a id="g9"></a>
### G9 — Native file dialogs — **closed**

Landed as [ADR-0287](../book/src/adr/0287-a-file-dialog-is-the-desktops-and-the-answer-comes-back-later.md).

```java
host.fileDialog(
        FileDialogSpec.saveFile().filters(FileFilter.of("PNG image", "png")).startingAt(lastExport),
        choice -> switch (choice) {
            case FileChoice.Chosen(var paths, var filter) -> Files.write(paths.getFirst(), board.encodePng());
            case FileChoice.Cancelled ignored             -> { }
            case FileChoice.Failed(var message)           -> toast(message);
        });
```

`FileDialogSpec.openFile()`, `saveFile()` and `openFolder()` are the three this
entry asked for, as a value rather than three static calls — `.filters(...)`,
`.startingAt(...)`, `.allowMany(...)` — and `Host.fileDialogs()` is the
process-global facility underneath, for the menu that wants `supported()` before
it offers an "Export…" item.

**There is no blocking overload and there will not be one.** The UI thread is the
only thread allowed to touch windows, and on Linux blocking it also stops the
pump the XDG portal needs in order to answer — a deadlock rather than a stutter.
The callback is on the UI thread, exactly once, and `Host.fileDialog` asks for
the frame behind it, which a dialog's answer otherwise arrives without.

**Filters are extensions, not patterns.** `FileFilter.of("Images", "png", ".jpg")`
— the dot is optional, the case does not matter, and `*.png` or `image/png` is
**refused**, because each platform's own dialect is a different one and a wrong
pattern is a dialog that lists nothing.

Two departures worth knowing about:

- **Cancelling is not failing.** `FileChoice` is sealed over `Chosen`,
  `Cancelled` and `Failed`; an export the user changed their mind about leaves no
  error to show.
- **No title, accept label or cancel label.** SDL exposes them only through
  `SDL_ShowFileDialogWithProperties`, and macOS has no dialog title at all. The
  record has room for them the day something needs them.

The native surface gained three symbols. The headless backend has **real**
dialogs a test scripts — `answerWith(...)` says what the user did, `shown()` says
what was asked for — so an export button is testable with no desktop under it.

The showcase's **Basic** screen has a card for it: three buttons and a line that
fills in when the dialog closes, which is the half of this that is Goldberry's.
The dialog itself is the desktop's window and cannot be photographed.

---

<a id="g10"></a>
### G10 — Gradients as a value — **closed**

Landed with [G1](#g1) in [ADR-0277](../book/src/adr/0277-a-path-is-a-value-and-the-rasterizers-is-package-private.md),
which is what this entry asked for: closing G1 alone would have left `BlendGradient`
in `Frame`'s signature and the leak open for everyone else.

`Gradient` is a sealed interface with `Gradient.Linear` and a `List<Gradient.Stop>`,
and `Gradient.fade(x1, y1, x2, y2, argb)` repeats the RGB at the transparent end —
the thing that is wrong when a fade is written by hand, because `0x00000000` is
transparent *black*. Radial is deliberately absent rather than guessed at: sealing
means adding it is a new record and an exhaustive `switch` that stops compiling,
not a default branch that draws something else.

---

<a id="g11"></a>
### G11 — The computed font, inside a painter — **closed**

Landed as [ADR-0288](../book/src/adr/0288-a-painter-is-told-what-the-cascade-resolved.md).

```java
new Canvas((frame, size, style) -> {                    // one parameter more
    Paragraph.of(style.font(), title).paint(frame, 0, 0, size.width(), style.ink());
});
```

A three-parameter painter is a `StyledPainter` and is handed a `CanvasStyle`:
the node's **own** resolved font — the opened face at the size the cascade
settled, out of the renderer's book — its resolved `color`, this frame's time on
the renderer's clock, and whether the user asked for less movement.

**The two-parameter form is unchanged and is not second class.** `StyledPainter`
is a *subtype* of `Painter`, so the compiler picks between them by arity with no
cast, `new Canvas(null)` still resolves, and nothing in `paint` — `Box`,
`BoxPainter`, `Offscreen` — changed at all.

Two departures from what this entry proposed:

- **It is a `CanvasStyle`, not a `ComputedStyle`.** What a painter needs is an
  opened `Font`, and only the renderer can make one; a `ComputedStyle` hands over
  the *description* and leaves the caller to parse the file — which is the line
  the showcase had actually written, once per frame.
- **It is a snapshot taken when the box is built.** `Paints.Context` answers for
  the node currently rendering, so a context read during the paint pass answers
  for whichever node rendered last. That is also why the theme-token accessors
  are not on it: a painter cannot name in advance the tokens it will want, and a
  widget that needs them is a `Paints` and reads them in `render`.

The frame clock came with it, which this entry did not ask for and an animated
canvas had no other way to reach: `Paints.Context.nowMillis()` is read once per
frame and shared, so two canvases animate on one tick and a test's virtual clock
drives both.

---

<a id="g12"></a>
### G12 — Deep links and single-instance handoff — **answered: not Goldberry's**

Decided in [ADR-0291](../book/src/adr/0291-a-url-scheme-is-packaging-and-packaging-is-the-applications.md).

This entry asked a question rather than proposing an API, and the answer is
**no** — for all three of its parts, and for the OS keychain it predicted would
be the same question.

`brd://open/<token>` is three problems wearing one name:

| | What it is | Whose |
|---|---|---|
| **Registration** | a registry key, `CFBundleURLTypes`, a `.desktop` with `x-scheme-handler/brd` | the **installer's**. Goldberry ships none, and on any packaged install the scheme belongs to the package and is removed with it. |
| **Delivery** | the URL reaching a process — an `SDL_EVENT_DROP_FILE` on macOS, `argv[1]` everywhere else | the platform's, and already there |
| **Handoff** | a second launch finding the first, handing over, and exiting | **brd's**, because it is a *process model*: one window or two, and what a URL may do to a window with unsaved work |

The middle option — the toolkit owning delivery and handoff but not registration
— is the one the record spent longest on and rejected: a `Host.onOpen(URI)` that
is inert on two platforms out of three until the application does the packaging
anyway is a feature that looks supported and is not, which is worse than one
honestly absent.

**What brd writes, and what it already has.** Roughly eighty lines and one line
of packaging:

```java
// second launch: take the lock, or hand over and exit
var lock = FileChannel.open(runtimeDir.resolve("brd.lock"), CREATE, WRITE).tryLock();
if (lock == null) {
    SocketChannel.open(UnixDomainSocketAddress.of(runtimeDir.resolve("brd.sock")))
            .write(UTF_8.encode(args[0]));
    return;
}
// first launch: listen, and post what arrives onto the UI thread
host.loop().ui().execute(() -> open(uri));      // UiExecutor is the one legal way in (ADR-0019)
```

No native call, no SDL, nothing the toolkit has to lend. `Application` is handed
the process's `args`; `EventLoop.ui()` is how a listener thread reaches the UI
thread.

**The keychain is the same answer** — DPAPI, the macOS Keychain and libsecret are
three platform APIs with no SDL coverage and a failure mode that leaks a token.
It is not a gap and will not become one.

**One piece is genuinely the toolkit's and is simply not needed yet**:
drag-and-drop. `SDL_EVENT_DROP_FILE` is unbound because nothing in the catalog
drops a file; when something does, that binding lands for its own reason and
macOS's deep-link path falls out of it.

If a *second* application needs the same eighty lines, that is the moment to
reopen this — two consumers is evidence and one is a guess, which is the rule
that kept the popup, the clipboard and the tray waiting. Reopening means editing
ADR-0291, not quietly adding a method.

---

<a id="g13"></a>
### G13 — The other `natives` leak: Yoga through `Box` and `ComputedStyle` — **closed**

Landed as [ADR-0279](../book/src/adr/0279-flexbox-is-the-toolkits-vocabulary-not-yogas.md) and
[ADR-0280](../book/src/adr/0280-natives-exports-to-core-and-to-nobody-else.md).

`io.github.digitalsmile.goldberry.layout` holds the vocabulary now — `Length`,
`Insets`, `Limits`, `FlexDirection`, `Justify`, `Align`, `Wrap`, `Position`,
`Overflow`, `Measure`, `MeasureMode`, `MeasuredSize` — and `paint.Box`,
`css.ComputedStyle` and `css.value.CssLength.parse` are written in it.
`ComputedLayout` was **deleted** rather than mirrored: `render.model.LogicalRect`
was already the toolkit's rectangle.

The translation is one package-private file, `paint/tree/Yoga.java`, beside the
only class that ever touches a `YogaNode`.

**And it is enforced.** `:natives` exports its three Yoga packages `to
io.github.digitalsmile.goldberry.core` and to nobody else; a module outside
`:core` that imports `StyleLength` is refused by javac. That was checked by
compiling one.

---

<a id="g14"></a>
### G14 — The last `natives` leak: one method — **closed**

Landed as [ADR-0290](../book/src/adr/0290-the-pen-belongs-to-the-rasterizer.md).

```java
package io.github.digitalsmile.goldberry.paint;

public final class GlyphFace implements AutoCloseable {   // a typeface, to the rasterizer
    public static GlyphFace of(String name, byte[] data);
}

public final class GlyphPen implements AutoCloseable {    // that face at one size
    public static GlyphPen on(GlyphFace face, double size);
    public void draw(Frame frame, double x, double baseline, ShapedRun run, int from, int to, int argb);
    public double ascent(); public double descent(); public double lineHeight();
}
```

The entry's own instruction — delete one word from a module descriptor and read
the compiler's answer — turned out to name **two sites, both on one line**.
`Font.shape` and `Paragraph.measureFunction` had already been fixed by ADR-0282
and ADR-0279, and the descriptor's claim of eleven was stale.

So the fix is the one this entry proposed: the native font and the staged buffer
moved into `paint`, where the context they need already lived, and
`Frame.drawGlyphs` is package-private with `GlyphPen` as its only caller.
`text.font` kept what it is actually about — shaping, metrics, the fallback
chain, the paragraph cache — and `Font.draw` is three lines of delegation.

**`:core` no longer requires `:natives` `transitive`ly, and Blend2D is sealed to
`:core` beside Yoga and HarfBuzz.** ADR-0280 said it was doing this and could only
do two thirds; it is true now, and the compiler is what says so: an application
module naming a `BlendPath` does not compile, `-Xlint:exports` under `-Werror`
fails the build the moment a `:natives` type reappears in an exported signature,
and a test asserts that each of the eight wrapped packages is exported to `:core`
and to nobody else.

**`GlyphFace` and `GlyphPen` are public**, which is more surface than the leak
they replaced and is deliberate: a custom widget that wants to draw a shaped run
— a terminal, a music stave, a diff view with its own layout — has a supported
way to now, and it is the way the toolkit's own text stack draws.

Nothing brd was blocked on, and no golden image moved.

---

<a id="g15"></a>
### G15 — IME preedit, inline — **closed**

Landed as [ADR-0289](../book/src/adr/0289-a-composition-is-not-an-edit.md).

```java
new Canvas(painter, new Input() {
    public boolean wantsText()                { return true; }
    public void onText(TextEvent e)           { if (editor.onText(e.text())) e.consume(); }
    public void onPreedit(PreeditEvent e)     { if (editor.onPreedit(e)) e.consume(); }
    public Optional<LogicalRect> caretArea()  { return Optional.of(editor.caretLine().offsetBy(8, 8)); }
    public double caretOffsetIn(LogicalRect a) { return editor.caret().x(); }
});
```

`PreeditEvent` is a **third** keyboard event beside `KeyEvent` and `TextEvent`,
routed to the focused node the same way, and `Editor.onPreedit` takes it whole.
Typing Japanese into a sticky now shows the underlined composition, the clause
the input method is converting, and a caret inside it — and the candidate window
opens under the caret rather than wherever the compositor guessed.

**A composition is not an edit**, and that is the whole design. The string is held
beside the document, never in it: `Editor.text()` does not move, the undo history
does not grow, and nothing bound to the value fires until the user accepts a
candidate and it arrives as committed text. What changes is `displayText()` — the
document with the composition spliced in at the caret — so the words after it move
along as they do in a native field, and a click during a composition maps back out
to the document.

**The caret's rectangle is the toolkit's job, not the application's.** The router
asks the focused widget `caretArea()` after every event that could have moved a
caret and hands the answer to the window, which is `SDL_SetTextInputArea`. Only
the widget knows where its own caret is; nobody has to remember to publish it.

Two notes for brd:

- `Handles.onPreedit` is a `default` that does nothing, so a canvas that ignores
  it is exactly as correct as before — it still receives every committed
  character. What it loses is the underline a CJK user types against.
- A **CSS-transformed** ancestor is not compensated: the caret is reported where
  it was laid out, so a candidate window under a transformed canvas is in the
  wrong place. Nothing else is wrong, and a board that scales its own contents
  inside an untransformed canvas is unaffected.

The native surface gained one symbol, one struct and one event constant. The
headless backend can be a Japanese user — `composeText`, `inputText`,
`endComposing` — so this is testable with no input method installed.

`text-input` and `text-area` were left out of this one and are [G16](#g16),
which is closed too.

---

<a id="g16"></a>
### G16 — IME preedit in `text-input` — **closed**

Landed as [ADR-0292](../book/src/adr/0292-a-field-composes-and-a-password-does-not.md),
and in `text-area` as well — `controls.css` says the two controls "must not look
like they were designed by different people", and shipping this in one of them
would have been exactly that.

Nothing to call: a `text-input` and a `text-area` compose because they are the
toolkit's own widgets. Type Japanese into one and the composition appears
underlined at the caret, with the clause the input method is converting
highlighted, and the candidate window opens beside the line rather than over it.

**A composition is still not an edit.** It is held beside the value and spliced
only into what is drawn, so `onChange` fires once — for the accepted candidate —
rather than once per keystroke of a string that is about to be replaced. The undo
history does not grow, and a `bind=` property does not see the intermediate text.

**A `password` refuses to compose**, which is the one decision this entry said was
not mechanical. The reason is not the bullets: a candidate window is a **second,
unmasked window** showing what is being typed, drawn by the input method beside
the field, so a masked field that composed would put the password on screen in a
window the application does not own. Windows disables the IME for a secure edit
control and macOS's `NSSecureTextField` refuses marked text; this does the same.
Committed text still arrives, so the field still takes every character.

Two smaller notes:

- **The highlight draws the clause**, because a composition replaces the selection
  when it commits and every input method collapses it — so the part is free. The
  new part is `text-composition`, the rule under the whole composition, drawn in
  front of the glyphs rather than behind them.
- **The caret's rectangle differs between the two controls**: a `text-input`
  reports its whole content box, because a single-line field *is* the line; a
  `text-area` reports the caret's line, because a candidate window kept clear of
  ten lines would be a long way from the text.

No golden image moved, and nothing about `Mask` changed — a composition being a
second instance of "what is drawn is not what is held" is why this was 250 lines
rather than a rewrite.

---

## 3. How this list is used

1. A new brd need that is about drawing, input, text, windows or platform integration gets an entry
   here **before** any code is written in brd.
2. Each entry names the proposed API, because "Goldberry should support X" is not actionable and a
   signature is.
3. When an entry lands upstream, brd deletes its stopgap in the same commit that bumps the Goldberry
   checkout, and the entry moves to a "Closed" list with the commit that closed it.
4. [docs/STATUS.md](STATUS.md) points at the entries that block a plan step, so "why is the client not
   editable yet" has one answer in one place.

## 4. Closed

An entry leaves the list one of two ways: it lands upstream, or it is **answered**
— decided, deliberately, not to be Goldberry's. Both are here, because "we looked
at this and said no" is a result and an entry that quietly disappeared is not.

| # | Closed by | What brd deletes |
|---|---|---|
| G1 | ADR-0277 | `BoardRenderer`'s `natives.blend2d` imports — `BlendPath`, `BlendStrokeCap`, `BlendStrokeJoin` |
| G2 | ADR-0278 | the solid-stroke stopgap for the ghost layer, the marquee and the missing-asset box |
| G10 | ADR-0277 | nothing; it was closed so the leak was not left open for the next application |
| G13 | ADR-0279, ADR-0280 | nothing brd had; it is the leak the *showcase* had, and `Box` is what every custom widget returns |
| G3 | ADR-0281 | the viewer-only client: pan, zoom, marquee, select and every other plan B3 tool can be written now |
| G4 | ADR-0283 | the grey box with the dashed border: an `image` shape draws its own pixels now, and a paste can decode what it was given |
| G5 | ADR-0284 | the `render/` service's "headless Goldberry" TODO: `preview.png` is `Offscreen.of(...).render(...).encodePng()` |
| G6 | ADR-0285 | the wordless sticky: a caret, a selection and an undo stack over a canvas, with no `text-input` in sight |
| G7 | ADR-0286 | the paste path: a screenshot off the clipboard is an `Image`, and a copied shape can offer its own format beside a PNG |
| G9 | ADR-0287 | the export path: "save as" and "import an image" are the desktop's own dialog, and a `.am` snapshot can be opened from disk |
| G11 | ADR-0288 | nothing brd had; a board's fonts are the document's — it is the *chart* and the custom control that could not follow a theme |
| G15 | ADR-0289 | the wordless-in-Japanese sticky: a composition, its clause and its caret are drawn, and the candidate window lands under them |
| G16 | ADR-0292 | nothing brd had; it is the toolkit's own fields — the board title, the assistant prompt and the search bar take Japanese now, and a password deliberately does not |
| G14 | ADR-0290 | nothing brd had; it is the toolkit's own boundary, and closing it is what makes "`natives.*` is not application API" a compiler error rather than a rule |
| G12 | ADR-0291 | nothing — it is the entry that was **answered** rather than built: brd writes its own lock, socket and packaging line, and the toolkit stops carrying the question |
