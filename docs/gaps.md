# What brd needs from Goldberry

brd is an application on Goldberry, and this is the queue of things it cannot do without the toolkit
growing them. It exists because of one rule, written down as
[ADR-0013](../book/src/adr/0013-no-reimplementation-of-goldberry-capabilities.md): **brd does not reimplement what
belongs in Goldberry.** Drawing, input, text, windows and platform integration are the toolkit's;
boards, notes, CRDT sync and agents are brd's. When brd hits the line, the answer is an entry here —
not a workaround that quietly becomes a second toolkit.

Updated 2026-09-18.

**Every entry on this list is closed or answered again.** Six arrived together —
the desktop's theme, an italic face and text decorations, a picker inside a popup, a
panel that takes no keys, a caret under `text-align`, and a router talking to an
element it had let go of — and all six are closed, in ADR-0317 to ADR-0324.

G32 arrived after them and is closed in ADR-0325. It is the odd one on this list: not a
capability the toolkit lacked, but one it had and could not use, because the machine that
built its native library was missing a `-dev` package and nothing anywhere said so.

**Six more arrived on 2026-09-15 and 2026-09-16 and are closed in ADR-0326 to ADR-0332** —
a hover hook for something that is not a menu item, a read-only field that opened at the
wrong end of its value, two missing codecs and a missing drop event, a `chip` dot that
took no colour, a line-number gutter, and an editing seam an application can talk to.
Working notes for that batch are in [gaps-g33-g38.md](gaps-g33-g38.md).

**Five more arrived on 2026-09-17, and all five are answered.** G42, handing a URL to the desktop,
was already built by the time its entry was written (ADR-0346). The other four are closed in
ADR-0348 to ADR-0351: faces an application ships, a window icon, a `canvas` that asks for its next
frame, and a defect in `text-area`'s gutter strip. Working notes are in
[gaps-g39-g43.md](gaps-g39-g43.md). One answer is not the one its entry expected. G43's strip was
not mispositioned but clipped, and fixing it turned up a second defect beside it: a wrap width that
assumed the left and right padding were equal. The drift the entry reported could not be reproduced
against this checkout, and ADR-0350 says so rather than claiming it.

Two of them are worth naming because of *how* they were answered rather than that they
were. G35a is one gap and two decisions: WebP is VP8, so the toolkit links the reference
decoder, and GIF is nine pages, so the toolkit writes one. G37 could not be a column of
number widgets at all — a widget's children are described before anything is laid out, so
the numbers would have been a frame behind the text on every keystroke that changed the
line structure.

**Six more arrived on 2026-09-18, from a notes application and a chat client.** Two of them are
unlike anything on this list before: G44 and G45 are not missing features but **measurements** — a style
pass and a layout pass that grow with the size of a document rather than with the size of the change —
and what they ask for is a cost rather than an API. Working notes for this batch are in
[gaps-g44-g49.md](gaps-g44-g49.md).

That six could arrive at once is the list's job working rather than failing: §3 says
a new brd need gets an entry here *before* any code is written in brd, and every one
of these was written down, with its stopgap named, before it was built.

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
| ~~[G8](#g8)~~ | ~~`goldberry-html`: Markdown through md4c~~ | **closed** — ADR-0294, ADR-0295, ADR-0296; `html-view` is [G17](#g17) | done |
| ~~[G9](#g9)~~ | ~~Native file dialogs~~ | **closed** — ADR-0287 | done |
| ~~[G10](#g10)~~ | ~~Gradients as a `core.paint` value~~ | **closed** — ADR-0277 | done |
| ~~[G11](#g11)~~ | ~~The computed font of a box, inside a painter~~ | **closed** — ADR-0288 | done |
| ~~[G12](#g12)~~ | ~~Deep-link URI handling and single-instance handoff~~ | **not Goldberry's** — ADR-0291 | answered |
| ~~[G13](#g13)~~ | ~~The other `natives` leak: Yoga through `paint.Box`~~ | **closed** — ADR-0279, ADR-0280 | done |
| ~~[G14](#g14)~~ | ~~The last `natives` leak: **one method**, `Frame.drawGlyphs`~~ | **closed** — ADR-0290 | done |
| ~~[G15](#g15)~~ | ~~IME preedit: the composition string, inline~~ | **closed** — ADR-0289; the fields are [G16](#g16) | done |
| ~~[G16](#g16)~~ | ~~IME preedit in `text-input`~~ | **closed** — ADR-0292; `text-area` too | done |
| ~~[G17](#g17)~~ | ~~`html-view`: HTML through litehtml~~ | **closed** — ADR-0298; litehtml is **answered**, not built | done |
| ~~[G26](#g26)~~ | ~~The desktop's light-or-dark setting~~ | **closed** — ADR-0322 | done |
| ~~[G27](#g27)~~ | ~~An italic face, and text decorations~~ | **closed** — ADR-0321 for the decorations, ADR-0323 for the faces | done |
| ~~[G28](#g28)~~ | ~~A picker whose popover opens inside a popup~~ | **closed** — ADR-0320 | done |
| ~~[G29](#g29)~~ | ~~A panel that never claims the keyboard~~ | **closed** — ADR-0319 | done |
| ~~[G30](#g30)~~ | ~~A caret that knows about `text-align`~~ | **closed** — ADR-0318, and ADR-0324 for the toolkit's own fields | done |
| ~~[G31](#g31)~~ | ~~A router that does not talk to elements it has let go of~~ | **closed** — ADR-0317 | done |
| ~~[G32](#g32)~~ | ~~The SDL the toolkit ships cannot ask the desktop on Linux~~ | **closed** — ADR-0325 | done |
| ~~[G33](#g33)~~ | ~~Hovering, for something that is not a menu item~~ | **closed** — ADR-0327 | done |
| ~~[G34](#g34)~~ | ~~A long value in a read-only field opens scrolled to its end~~ | **closed** — ADR-0326 | done |
| ~~[G35a](#g35)~~ | ~~No codec for GIF or WebP~~ | **closed** — ADR-0329 | done |
| ~~[G35b](#g35)~~ | ~~A file dropped on a window raises nothing~~ | **closed** — ADR-0330 | done |
| ~~[G36](#g36)~~ | ~~A `chip`'s dot takes no colour~~ | **closed** — ADR-0328 | done |
| ~~[G37](#g37)~~ | ~~A line-number gutter on `text-area`~~ | **closed** — ADR-0331 | done |
| ~~[G38](#g38)~~ | ~~An editing seam an application can talk to~~ | **closed** — ADR-0332 | done |
| ~~[G39](#g39)~~ | ~~Faces an application ships, reachable from `font-family`~~ | **closed** — ADR-0349 | done |
| ~~[G40](#g40)~~ | ~~A window icon~~ | **closed** — ADR-0351 | done |
| ~~[G41](#g41)~~ | ~~A `canvas` that can ask for its next frame~~ | **closed** — ADR-0348 | done |
| ~~[G42](#g42)~~ | ~~Hand a URL to the desktop~~ | **closed** — ADR-0346 | done |
| ~~[G43](#g43)~~ | ~~`text-area`'s gutter strip does not reach its padding~~ | **closed** — ADR-0350 | done |
| [G44](#g44) | A `text-area` style pass that does not grow with its text | a preview budget on a long note | high |
| [G45](#g45) | A `markdown-view` that restyles only the block that changed | the same budget, worse | high |
| ~~[G46](#g46)~~ | ~~A canvas transform that composes with the one it is painted under~~ | **closed** — ADR-0390 | done |
| [G47](#g47) | A `qr-code` widget | sign-in by QR, share links, device invites | high |
| ~~[G48](#g48)~~ | ~~A viewport that opens at its end, and stays put when rows are added above~~ | **closed** — ADR-0392 | done |
| ~~[G49](#g49)~~ | ~~Emoji render as boxes~~ | **closed** — ADR-0393 | done |

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
- **Markdown**: `Markdown.parse(source)` to a tree of records, `MarkdownHtml.of(document)` to serve it
  and `markdown-view` to show it — **live**, through `bind=`, so an editor and its preview are one
  property read twice. The optional `:html` module, which an application adds to its own dependencies
  and its own stylesheet list ([G8](#g8)).
- **HTML**: `Html.parse(source)` to a tree of records, `html-view` to show it, `document.find("a")` and
  `document.text()` to walk it — the same module, the same `bind=`, the same stylesheet pattern, and an
  anchor that is a `button.link` handing its `href` to the application ([G17](#g17)). No engine, no
  network, no scripting; what an engine would still buy is real inline layout and text selection.
- **A document a reader can use**, in both views: links that are pressed, images that are drawn from an
  `ImageSource` the application supplies, and — in Markdown — task boxes that report their ordinal, which
  `Markdown.toggleTask` turns into a one-character edit of the source (ADR-0300). Every one of them is
  the toolkit reporting and the application deciding; none of them opens a browser or a file.
- **Text a reader can select and copy**, in both views: drag, double-click for a word, triple-click for a
  block, `Ctrl+A`, `Ctrl+C` — with a space between words and a newline between blocks, because the fold
  is what knows where those went (ADR-0301). A drag costs a repaint rather than a rebuild, which is why
  it is usable on a long note.
- **A document that draws at speed.** A page is one `text` widget per word, and the paragraph cache sizes
  itself to the frame rather than to a constant — 287 re-shapes per settled frame became **zero**, and
  the style pass went from 9.5 ms to 0.8 (ADR-0299). Worth knowing when deciding how long a document
  brd's preview may be.
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
### G8 — `goldberry-html`: Markdown through md4c — **closed**

Landed as [ADR-0294](../book/src/adr/0294-a-parser-crosses-the-boundary-once.md)
and [ADR-0295](../book/src/adr/0295-a-document-is-a-value-and-a-paragraph-is-a-row-of-words.md).

```java
var document = Markdown.parse(note.source());                // GitHub's dialect
var plain    = Markdown.parse(note.source(), MarkdownSyntax.commonMark());

var html    = MarkdownHtml.of(document);                     // GET /docs/{id}/body.html
var preview = MarkdownView.of(document);                     // the editor's other half
```

```kdl
scroll {
    markdown-view id="preview" "# Hello\n\nSome *words*."
}
```

`:html` is the first of `content-widgets.md`'s optional modules, and an
application opts into it: nothing in `:core` or `:widgets` knows Markdown exists,
and `MarkdownStyles.stylesheet()` goes beside `Controls.stylesheets(theme)`.

**A document is a value, and that is the departure worth knowing about.** This
entry asked for a widget and a way to serve HTML; what landed is a **model** — a
sealed tree of records in `…markdown.model` — with the widget and the HTML writer
as two folds over it. A preview is not the only thing brd does with a note: an
outline, a word count, a table of contents and the first paragraph as a summary
are all walks of the same tree, and a widget that hid the parse would make each of
them a second parse. It also makes the two halves agree by construction, which two
renderers — one in C, one in Java — would not.

**md4c is inside `libgoldberry` rather than in a native of its own**, which
departs from ADR-0190's quarantine rule and says so: that rule is for heavy or
encumbered natives, and md4c is one MIT C file whose object code is tens of
kilobytes. A second superbuild, four classifier jars and four CI legs would cost
more than the thing they isolate. litehtml, when it comes, still gets its own
library.

**A parse is one crossing.** md4c is a SAX parser, and binding it the obvious way
would mean five upcall stubs, seven detail-struct layouts and several thousand
crossings per note. The events are encoded into one buffer in C and read once —
which is ADR-0190's own "the hot path never crosses FFM", applied to a parser — so
nothing in `:html` owns native memory and a `Document` is an ordinary value.

Four things the widget renderer does not do, each because there is no engine under
it, and each written down rather than faked:

- **Nothing is clickable.** A word does not hear a pointer, so a link is the
  accent colour and not a destination.
- **An image is its alt text.** There is no `img` widget, and fetching anything is
  the application's.
- **Emphasis is a faux oblique** — `skewX(-10deg)` — which was written because the
  system shipped two upright faces. It ships four since [G27](#g27) (ADR-0323), so
  the skew is now a *picture* decision rather than a constraint: one declaration —
  `font-style: italic` — replaces it, and it moves every Markdown and HTML golden,
  which is why it is a change to take on purpose rather than a consequence of the
  faces existing. The same is true of the muted strikethrough, which
  `text-decoration: line-through` now expresses honestly.
- **A hard break inside a paragraph does nothing**, because a wrapping row has no
  widget that means "start a new line here". It does the right thing in the HTML.

**A preview follows a property.** `markdown-view` takes §9's `bind=`, so an
editor and its preview are two nodes over one value and nothing in between
(ADR-0296):

```kdl
split-pane {
    text-area class="mono" bind="note.source" change="note.set-source" fill=#true
    scroll { markdown-view bind="note.source" }
}
```

The editor writes through an action, the preview's element is subscribed to the
property, and the frame after a keystroke is the parsed document. The showcase's
**Markdown** screen is exactly that, and building it found two defects in
`:widgets` that had nothing to do with Markdown — a `split-pane` that never re-read
its own measured width, and a `text-area` that opened on the *end* of a long value
and could not be sized by its container (ADR-0297).

The dialect is a value — `MarkdownSyntax.gitHub()`, `.commonMark()`,
`.with(MarkdownExtension.WIKI_LINKS)` — because a dialect belongs to a corpus
rather than to a call, and the preview and the served HTML must not drift apart.
`[[wiki links]]` are a node of their own rather than a `Link` with an odd href:
a target names something in brd's own collection, which is the reason the
extension exists at all.

`html-view` **is** built now, and not the way this entry or that one assumed:
[G17](#g17) landed as a parser in Java over a model of records, folded by the
same code, with litehtml answered rather than embedded (ADR-0298).

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
| G17 | ADR-0298 | nothing brd had, and the entry it is: `html-view` renders a page today, and the *engine* half is what was answered — litehtml stays on `book/src/TODO.md` for the inline layout it alone buys |
| G26–G31 | ADR-0317 to ADR-0324 | the six that arrived together: the theme note's hedge, the italic stopgap, the picker-in-a-popup workaround, the panel's key-swallowing, the caret's own alignment arithmetic, and the router guard |
| G32 | ADR-0325 | nothing to delete: both the Settings note and the start-up log are things a client should say regardless, and `DesktopTheme` reads the real answer the moment there is one |
| G33 | ADR-0327 | `HoverRegion` — the `Widget.Leaf` a client owned for an input concern, which is exactly what §5.3 says to delete when the upstream change lands |
| G34 | ADR-0326 | nothing; there was no stopgap on purpose, and the field opens at the head of its value now |
| G35a | ADR-0329 | the narrowed intake set: a client's decodable formats become `{PNG, JPEG, GIF, WEBP}`, and `ImageCacheTest.decodableFormats` is the assertion that changes |
| G35b | ADR-0330 | nothing; the gesture was simply absent, and wiring `AssetService.fromFile` to `window.onFileDrop` is one call |
| G36 | ADR-0328 | nothing; the pill and the rows were labelled by name, which was complete rather than degraded — `withDot(project.color())` is the line that was waiting |
| G37 | ADR-0331 | nothing; the pane shipped without line numbers, visibly absent rather than faked |
| G38 | ADR-0332 | nothing; there was no stopgap on purpose — a caret inferred from a diff is right for typing and wrong for the three cases a shortcut fires on |

---

<a id="g17"></a>
### G17 — `html-view`: HTML on screen — **closed**, and litehtml **answered**

Landed as [ADR-0298](../book/src/adr/0298-html-is-a-document-and-not-an-engine.md).

```java
var document = Html.parse(help.body());          // io.…goldberry.html

var view  = HtmlView.of(document).onLink(app::navigate);
var links = document.find("a");                  // a link check, same parse
var words = document.text();                     // a summary, same parse
```

```kdl
scroll { html-view bind="doc.source" link="doc.open" }
```

**The entry asked for an engine and got a document, and that is the decision.**
Everything this entry listed as "what it would add over G8" is here except one
item, and that one is the item litehtml alone can buy:

| | | |
|---|---|---|
| Followable links | **built** | an anchor is a `button.link` (ADR-0293) handing its `href` to the application — a Tab stop, `:hover`, `Space` and `Enter`. `markdown-view` does it too now (ADR-0300) |
| Tags, tables, definition lists, `pre` | **built** | every element contributes `html-<tag>`, so `html.css` reads like a browser's default sheet and an unknown tag is already styleable |
| Images | **built** (ADR-0300), and never the engine's | an `ImageSource` is the application's answer about where a `src` is; a `src` nothing answers for is still its alt text |
| Real inline layout | **not built**, and **is** the engine's | a line of mixed faces is a row of `text` widgets, so no justification and no hyphenation |
| Text selection | **built** (ADR-0301) | drag, double-click a word, triple-click a block, `Ctrl+A`, `Ctrl+C` — in both views, with the newlines the document implies |

**What it waits on has changed, because `html-view` is no longer waiting.** The
paint-surface widening this entry named is still real and still shared with
`goldberry-vector` and `goldberry-terminal`; what it no longer blocks is HTML on
screen. `book/src/TODO.md` keeps it, for the inline layout above.

Costs, since none of this was free: no new native symbol and no second native
artifact — the one thing that crosses is md4c's entity table, which was already
there. The `:html` module now has two widget trees, which turned out to break the
**weaver**: it wrote the generated catalog into the longest package prefix its
widgets share, which for two trees is the package `:core` owns, and two named
modules holding one package is a `LayerInstantiationException` on the module path
that no class-path test can see. Fixed in `CatalogWeaver`, with the module's own
packages as the bound.

The showcase has an **HTML** screen beside the Markdown one: the same split pane,
the same `bind=`, one node name different — and a line under the preview that
fills in with whatever link was last pressed, which is the whole of "following a
link is the application's".

<a id="g26"></a>

### G26 — the desktop's light-or-dark setting — **closed**

**What brd needs.** To start in the theme the user's desktop is set to, and to follow it when it
changes — which on every platform with a sunset schedule is once a day, while the application is
running.

**Why it is not brd's.** Every way of asking is a platform call: `SDL_GetSystemTheme` and
`SDL_EVENT_SYSTEM_THEME_CHANGED` cover all three platforms in one, and the alternatives are
`AppleInterfaceStyle`, the `AppsUseLightTheme` registry value and the XDG settings portal. Goldberry
already owns the window, the event loop and the SDL layer; brd calling SDL is precisely what
[ADR-0015](adr/0015-no-reimplementation-of-goldberry.md) exists to prevent, and
[ADR-0004](adr/0004-ffm-lives-in-one-module.md) says which module may hold a `MemorySegment` — neither
of them is brd's client.

**Proposed API.**

```java
/// What the desktop is set to, or empty where the platform does not say.
Optional<SystemTheme> Host.systemTheme();          // LIGHT | DARK

/// Told when it changes, on the UI thread, for as long as the window is open.
void Host.onSystemThemeChanged(Consumer<SystemTheme> listener);
```

`Optional`, because SDL answers `SDL_SYSTEM_THEME_UNKNOWN` on a desktop that has no such setting, and
an application needs to tell "the desktop says light" from "the desktop does not say" — the first is a
theme and the second is a default.

**What brd does meanwhile.** `ThemeChoice.SYSTEM` is the default and resolves to light, `--theme=` and
the `brd.theme` property pin it, and the light switch works as it always did. `SystemThemeTest` asserts
that `Host` has **no** `systemTheme` method, so the day this lands the test fails and the stopgap is
deleted in the commit that takes the change — the arrangement `LucideRepairs` used.

**What landed** (ADR-0322). The two methods the entry asked for, with the signatures it asked for:

```java
Optional<SystemTheme> Host.systemTheme();                        // LIGHT | DARK, or empty
void Host.onSystemThemeChanged(Consumer<SystemTheme> listener);
```

`SystemTheme` is `render.desktop`'s, so no SDL type crosses. The path under it: `SDL_GetSystemTheme` on
the export list bound as an **optional** symbol, `SDL_EVENT_SYSTEM_THEME_CHANGED` translated into one
event per open window — the way `QUIT` already becomes one `CloseRequested` per window, because a `Host`
is per window — and both numbers in the layout probe's registry, so the C preprocessor checks them.
`Backend.systemTheme()` is a `default` returning empty, so every test double stayed correct; the
headless backend overrides it with a setter that also posts the change, which is what `SystemThemeTest`
drives.

`Window.systemTheme()` and `Window.onSystemThemeChanged(…)` exist too, for an application that holds a
window and no `Host`. **The toolkit still chooses nothing**: which theme to use, whether to follow the
desktop, and what "the desktop does not say" should mean are all the application's.

brd's stopgap goes: `ThemeChoice.SYSTEM` can resolve for real, and `SystemThemeTest`'s assertion that
`Host` has no `systemTheme` method now fails — which is the arrangement working as designed.

<a id="g27"></a>

### G27 — an italic face, and text decorations — **closed**

**What brd needs.** To set a shape's words in italic, and to underline or strike through them — the
three controls every board and document tool puts beside **bold**, and the three
[ADR-0024](adr/0024-objects-carry-a-text-style.md) left out of the board's text options bar.

**Why it is not brd's.**

*Italic is a face.* `BundledFont` ships four: Inter Regular, Inter SemiBold, JetBrains Mono and
OpenMoji. Its own note explains why a weight is a face here rather than a variable axis — instancing
`wght` at runtime needs symbols bound in both HarfBuzz and Blend2D, and `docs/design-system.md` §1.4
ships two weights, so the second is a second file (ADR-0066). Italic is the same decision one step
further on, and it is the toolkit's to make: brd cannot add a face to a bundle it does not own, and the
alternative available to an application — shearing the glyphs in the painter — is a *synthetic oblique*,
which is a decision about type design rather than a workaround.

*Underline and strikethrough are a text-layer feature.* `TextFlow` carries wrapping, ellipsis and
alignment; a decoration needs the line's extents and the font's `underlinePosition` and
`underlineThickness`, which are in the face and not reachable from `Paragraph`. Drawing a rectangle
under the text in `TextPainter` would get the thickness wrong at every size and the position wrong at
every family.

**What it would look like.** Either would be enough on its own:

- `BundledFont.UI_ITALIC` (and a `Style` beside `Weight`, since `of(family, weight)` cannot express it),
  or the variable-axis answer ADR-0066 deferred.
- `TextFlow.decoration(Decoration.UNDERLINE | Decoration.LINE_THROUGH)`, drawn by `Paragraph.paint`
  from the face's own metrics.

**What brd does meanwhile.** Nothing, on purpose. `TextStyle` has no `italic` field and the options bar
has no button, because a control that changes the document and nothing on screen is worse than one that
is not there. There is no stopgap to delete when this lands — only a field, a codec line and a button to
add.

**What landed: the decorations** (ADR-0321). The entry said either half would be enough on its own, and
this is the second one:

```java
paragraph.paint(frame, x, top, maxWidth, argb, flow.decorations(TextDecoration.UNDERLINE));
```

`text-decoration` and `text-decoration-line` resolve in the cascade — `none | underline | line-through`,
either or both — and inherit, which is how CSS's *propagation* to in-flow descendants reads here for
`text-align`'s reason: a control's text is usually an anonymous child box. A declaration that names a
colour or a style (`wavy`, `dotted`) is dropped whole rather than half-applied.

The position and thickness are the **face's own**, which is the whole reason it could not be brd's:
`bl_font_get_metrics` was already filling all sixteen `BLFontMetrics` floats and this side was reading
six of them, so four more crossed with **no new native symbol and no relink**. A face that carries none
gets conventional substitutes rather than nothing. The rule is as long as the line, indented with it
under `text-align`, absent from a blank line, and drawn across an ellipsis because the mark is part of
the line.

**And the italic faces landed too** (ADR-0323), which is the half this entry asked for first. Two files
rather than one — `Inter-Italic` and `Inter-SemiBoldItalic`, out of the release the manifest already pins
— so the matrix closes and `font-weight: 600; font-style: italic` is a **face** rather than the nearest
of three. `BundledFont.Style` is the third thing that names a file beside the family and the weight,
`font-style: normal | italic` resolves in the cascade, and `oblique` is dropped with a warning: it asks
for a *slant*, and this toolkit ships a drawn italic rather than a shear — which is the distinction this
entry made when it said a synthetic oblique is a decision about type design.

brd's "nothing, on purpose" can go for all three controls: `TextStyle` gains a field, the codec a line
and the options bar three buttons.

<a id="g28"></a>

### G28 — a picker that can open its popover from inside a popup — **closed**

**What brd needs.** To put a `color-picker` in the board's selection options bar, which is a `Popup`,
and have its plane open **beside the swatch** rather than in the corner of the window
([ADR-0025](adr/0025-a-text-shape-grows-and-a-line-stays-visible.md)).

**What happens today.** The picker opens its board with
`Host.attachedPopup(content, bounds, Placement.BELOW, …)`, where `bounds` is the rectangle
`PickerField` was handed by `Located.located(self, clip)`. Those two are in **different coordinate
spaces** whenever the control is inside a popup:

- `Located` is notified by the `PointerRouter` that painted the node, and a `Popup` has **its own**
  router — so a swatch 8 points from the bar's left edge is told it is at x=8.
- `Host.attachedPopup` places in the **owner window's** logical coordinates.

So the plane opens at (8, 30) of the main window. With the bar floating over the middle of a board, the
picker's plane appears in the top-left corner, which is what was reported.

**Why it is not brd's.** The application cannot translate the rectangle, because it never sees it: it
passes between two pieces of toolkit inside a widget it does not own. Goldberry already knows this class
of problem exists and has solved it once — `Popup.anchor(String)` says so in as many words, *"`Host.anchor`
answers from the main window's geometry and knows nothing about what is in a popup … the answer is
translated by this popup's own offset"* — but that route is for a menu opening a submenu, and a picker
has no way to reach it.

`date-picker`, `time-picker` and `select` are the same shape and presumably the same: anything with a
popover is unusable inside a popup.

**What it would look like.** The translation belongs where the mismatch is, not at every call site:

- `Located` could report in the owner window's coordinates, by having a popup's router offset what it
  notifies — which is the same correction `Popup.anchor` already applies, moved one layer down and
  applied to everyone.
- Or `BuildContext` could carry the popup a build is happening inside, so `PickerField` asks *it* for
  the anchor rather than the `Host`.

The first is the one brd would bet on: it makes every `Located` widget correct inside a popup at once,
rather than fixing the four that happen to have popovers today.

**What brd does meanwhile.** `FillOptions` offers the thirteen `Palette` presets as glyph swatches and a
`text-input` for a hex — two controls that need no popover at all. **There is no stopgap picker**: no
plane, no ramp, nothing that would have to be deleted, because a saturation/value plane written in brd
is exactly what [ADR-0015](adr/0015-no-reimplementation-of-goldberry.md) exists to prevent. When this
lands the class becomes a `ColorPicker` again and the preset row becomes its `presets(…)` argument,
which is the shape `FillOptions.PRESETS` is already in.

**What landed** (ADR-0320), and it is the answer the entry said it would bet on: **`Located` now reports
in the owner window's coordinates.** A popup sets its router's `locationOrigin` from its own offset every
frame, and both rectangles a `Located` widget is handed — the painted one and the clip — are translated
by it. So the rectangle a control hands to `Host.attachedPopup` is already in the space that call places
in, and the four controls with popovers became correct inside a popup without being touched:
`color-picker`, `date-picker`, `time-picker` and `select`. So does the fifth, whatever it is.

`Popup.move` now asks for a repaint as well, because a popover that follows a scrolling anchor moves
without anything else changing, and a frame is what re-reports where its widgets are. Nothing else is
translated: hit testing, hovering, capture and the cursor are answered against the window the pointer is
actually in, and `Popup.anchor` reads the captured regions directly, so it does not double-count.

<a id="g29"></a>

### G29 — a panel that never claims the keyboard — **closed**

**What brd needs.** To float a bar of buttons over a board — positioned by number, because it points at
a selection — **without it taking a single key from the canvas underneath**.

**What happens today.** `Window.handleKeyPressed` consults an input watcher before its own router, and
`Launcher` installs one whose rule is explicit: *"while a menu is open the keyboard belongs to it,
whether or not the platform moved focus there — otherwise an arrow would move the selection in the
window underneath the menu (ADR-0104)"*. Every key goes to the topmost open popup first, and only what
that popup's router **declines** reaches the window.

That rule is right for a menu. The board's selection bar is not a menu: it is a panel that is open the
whole time something is selected, over a canvas somebody is typing into. `Popup.takesFocus` defaults to
true, so it focused its own first control on opening, and a focused `Button` consumes `Enter` — so
`Enter` pressed a swatch instead of breaking a line in the sticky underneath
([ADR-0025](adr/0025-a-text-shape-grows-and-a-line-stays-visible.md)).

**What brd does meanwhile.** `takesFocus(false)` and `attachedPopup`, which between them leave nothing
focused inside the bar, so its router dispatches nothing and declines every key. That works and it is
not quite enough: **a press inside the popup still focuses what it landed on**, through the popup's own
`focusFromPress`, so clicking a swatch and then pressing `Enter` presses that swatch again.

**What it would look like.** A popup that can be told it is a *panel* rather than a menu, and is then
skipped by the forwarding rule and by `focusFromPress` alike — one flag rather than two, since "does
this thing want keys at all" is one question:

```java
host.attachedPopup(content, anchor, placement, minimumWidth, fit).keyboard(false)
```

`takesFocus(false)` is close and settles only the opening: it stops `focusFirst`, and nothing else.

**Why it is not brd's.** The forwarding happens in `Launcher`'s watcher, above anything an application
can install — `Window.inputWatcher` is package-private, and there is one per window. An application
cannot decline a key on a popup's behalf because it never sees the key.

**What landed** (ADR-0319), with the signature the entry wrote:

```java
host.attachedPopup(content, anchor, placement, minimumWidth, fit).keyboard(false)
```

One flag, three effects, because "does this thing want keys at all" is one question: nothing is focused
when it opens, **a press inside it focuses nothing** (`PointerRouter.pressFocuses(false)` — the half
`takesFocus` could not reach, since the press arrives at the popup's own window), and the owner does not
forward keys to it. The launcher now looks for the topmost popup that *wants* the keyboard rather than
the topmost popup, so a panel floating over a menu leaves the menu operable by arrows.

`Escape` is deliberately not this flag's business: a panel that may be dismissed by input still is,
which is `lightDismiss`, and one that must survive a keystroke says so there — the key then reaches the
window. brd's `takesFocus(false)` stopgap becomes `keyboard(false)` and the "press then Enter presses it
again" behaviour goes with it.

<a id="g30"></a>

### G30 — a caret that knows about text alignment — **closed**

**What brd needs.** To place a caret, hit-test a click and move by a visual line in text that is
**centred or right-aligned** — which is most of a board, because a sticky is centred by default.

**What happens today.** `text.edit.TextGeometry` is the toolkit's answer to the three questions an
editor asks, and it is very nearly what brd needs: `caretAt`, `offsetAt` and `moveLine`, measured through
the same `Paragraph` the glyphs were painted from, which is the half that has to know about ligatures and
grapheme clusters. What it does **not** take is the alignment. Every measurement is from the paragraph's
origin:

```java
var x = paragraph.widthBetween(line.start(), Math.max(line.start(), offset));
```

`Paragraph.paint` does take one — `TextFlow.textAlign` — and indents each line by its own share of the
slack. So the painter and the caret use two different origins the moment the text is not left-aligned,
and the caret drifts away from the glyphs by half the line's slack, growing as the line shortens.

This is not only brd's problem: a Goldberry `text-area` with `text-align: center` would drift the same
way. brd hit it first because a board's default shape is a centred one.

**What brd does meanwhile.** `TextPlacement` restates `Paragraph.paint`'s indent rule — clamp the slack
at zero, then nothing, half or all of it — and adds it to every x it computes and subtracts it from every
x it is given. It works, and a round-trip test (draw the caret at an offset, press exactly there, get the
offset back, on a wrapped centred paragraph) is what proves it.

It is still a **second copy of a rule that belongs upstream**, which is the thing this file exists to
stop. If `Paragraph.paint`'s indent ever changes — a justified alignment, an RTL line — brd's copy is
silently wrong and only that round-trip test says so.

**What it would look like.** The alignment and the width the text was drawn in, passed with the layout —
they are already what `paint` was given:

```java
TextGeometry.caretAt(paragraph, layout, offset, wrapWidth, TextAlign.CENTER)
TextGeometry.offsetAt(paragraph, layout, x, y, wrapWidth, TextAlign.CENTER)
TextGeometry.moveLine(paragraph, layout, offset, lines, desiredX, wrapWidth, TextAlign.CENTER)
```

The existing three-argument forms stay and mean `START`, which is what every caller of them assumes
today.

**Why it is not brd's.** The rule being restated is `Paragraph.paint`'s own, and brd cannot see it — it
is inside the method that draws. Two implementations of "where does this line start" is exactly the shape
of bug [io.github.digitalsmile.brd.core.scene.ConnectorPath] exists to prevent for connectors, and the
answer there was the same one: put it in the place that already knows.

**What brd keeps either way.** The *vertical* alignment and the shape's own inset — a sticky's twelve
units, a label's eight — stay brd's. Those are the board's idea of a shape, not the toolkit's idea of a
paragraph. `TextPlacement` does not go away when this lands; it gets shorter by one rule and stops
being able to drift.

**What landed** (ADR-0318). The indent rule moved to the property that owns it —
`TextAlign.indentOf(lineWidth, available)`, which `Paragraph.paint` now calls too, so there is exactly
one implementation — and all four questions gained a form that takes the width the text was drawn in and
its alignment:

```java
TextGeometry.caretAt(paragraph, layout, offset, wrapWidth, TextAlign.CENTER)
TextGeometry.offsetAt(paragraph, layout, x, y, wrapWidth, TextAlign.CENTER)
TextGeometry.moveLine(paragraph, layout, offset, lines, desiredX, wrapWidth, TextAlign.CENTER)
TextGeometry.selectionRects(paragraph, layout, start, end, wrapWidth, TextAlign.CENTER)
```

`selectionRects` is in the list although the entry did not ask for it: a highlight drifts exactly as a
caret does. The three-argument forms stay and mean `START`. `Editor` carries a `textAlign` of its own and
hands it to all four, so the canvas editor's paint, caret, hit test, `Up`/`Down` and selection move
together; setting it invalidates no layout, because alignment decides where a line starts and not where
it breaks.

`TextPlacement` gets shorter by one rule and stops being able to drift, which is what the entry asked
for.

**And the entry's aside landed as well** (ADR-0324). It said *"a Goldberry `text-area` with
`text-align: center` would drift the same way"*, and the reason it did not was that both fields ignored
the property outright. They do not now: `Value` passes `style.textFlow()`, so the glyphs are aligned and
decorated, and each control places its caret, its highlight, its composition rule and its hit test from
the same indent — by the **box** in `text-input`, whose value hugs its text, and **per line** in
`text-area`, whose value box has a definite width and whose lines therefore start in different places.
The round trip is the test in both.

**And the showcase demonstrates it**, on the Forms screen: one `text-area` with
every text property over it — the alignment and the type rank as segmented bars, the
weight, the style, the two rules and the family as checkboxes, and the declarations
printed under the field. Change anything and click into the middle of a word: the
caret is where the glyphs are, which is the half of this that no picture shows.

<a id="g31"></a>

### G31 — a router that does not talk to elements it has let go of — **closed**

**What brd needs.** To close a surface that has the keyboard — a Palette, a Sheet, a panel with a field
in it — without the window falling over on the next frame.

**What happens today.** It falls over:

```
java.lang.IllegalStateException: setState() on a state that is not mounted.
  at ...widget.State.setState(State.java:77)
  at ...form.textinput.TextInputState.focusChanged(TextInputState.java:504)
  at ...form.textinput.TextField.onFocusChanged(TextField.java:151)
  at ...input.PointerRouter.notifyFocus(PointerRouter.java:953)
  at ...input.PointerRouter.focus(PointerRouter.java:932)
  at ...input.PointerRouter.refocus(PointerRouter.java:299)
  at ...input.PointerRouter.updateRegions(PointerRouter.java:151)
  at ...Launcher.paint(Launcher.java:502)
```

`PointerRouter.refocus` is doing exactly what its own javadoc promises — *"the router never holds an
element that is not in the tree"* — and the check it makes is the right one:

```java
if (focused == null || focused.isMounted()) {
    return;
}
...
focus(null, false);            // or focus(restoreTo, …)
```

Two lines later it hands that same element, which it has just established is **not mounted**, to
`focus(...)`, which tells it so:

```java
if (lost != null && lost != focused) {
    notifyFocus(lost, false, fromKeyboard);
}
```

`focus` is right to notify `lost` — that is its contract for every ordinary focus change. What it cannot
know is that this particular caller is reporting a *death* rather than a move. And `State.setState`
is right to throw: its javadoc says an unmounted `setState` means *"a callback outlived the widget that
registered it, which is a leak worth hearing about"*. Every party is behaving correctly and the window
still dies.

It is not brd-specific and not overlay-specific. Any tree where a focused control disappears reaches it:
a tab that switched, a list that shortened, a `dialog` with a field in it that closes on its own button —
`refocus`'s own javadoc names all three.

**What brd does meanwhile.** Never lets the router be holding a node that is about to vanish: before an
overlay closes, the client moves the keyboard back to the content
([io.github.digitalsmile.brd.app.shell.ShellModel#onReleaseFocus]), so `refocus` finds a mounted element
and returns at the first check. That is also the behaviour brd wants — an overlay that closes hands the
keyboard back to what it was covering — so the stopgap is one that would be written anyway.

It also stopped rebuilding the whole window when an overlay opens, which was making the same fault far
easier to hit: the root's child changed type between `Row` and `Stack`, so *everything* under it was
unmounted and rebuilt, keyboard included ([ADR-0027](adr/0027-the-shell-grows-a-sheet-and-a-palette.md)).

**What it would look like.** One clause, in the place that already knows:

```java
// PointerRouter.focus(Element, boolean)
if (lost != null && lost != focused && lost.isMounted()) {
    notifyFocus(lost, false, fromKeyboard);
}
```

`notifyFocusWithin` walks the same two chains and wants the same guard. An unmounted element has already
been disposed; there is nobody left to tell.

**What landed** (ADR-0317): that clause, and the same guard in `notifyFocusWithin`, which walks the same
two chains. It is **per element** rather than per notification — an ancestor that survived its child
really has lost `:focus-within` and is still told, and only the elements that went away are skipped.
`mark`, the pseudo-class half of the same method, has had `element.isMounted()` in it from the
beginning; the notification half now agrees with it.

brd's stopgap is one it wanted anyway — an overlay that closes hands the keyboard back to what it was
covering — so nothing has to be deleted, and `refocus` no longer depends on it.

**Why it is not brd's.** `PointerRouter` is `:core` and is installed by `Launcher`; an application never
sees the focus change, cannot intercept it, and has no way to make a `State` tolerate being told
something after it is gone. The only lever outside is "do not be in that position", which is what the
stopgap is — and it is a lever every application would have to pull, separately, for ever.

### G32 — the SDL the toolkit ships cannot ask the desktop on Linux — **closed**

**What brd needs.** For [G26](#g26)'s answer to be a real one on Linux. `Host.systemTheme()` is the
right API and brd is using it as designed; on this desktop it answers empty, so `ThemeChoice.SYSTEM`
falls back to light on a machine that is set to dark.

**What happens today**, measured rather than guessed, on Ubuntu 26.04 / GNOME / Wayland:

```
$ gsettings get org.gnome.desktop.interface color-scheme
'prefer-dark'

$ gdbus call --session --dest org.freedesktop.portal.Desktop \
      --object-path /org/freedesktop/portal/desktop \
      --method org.freedesktop.portal.Settings.Read org.freedesktop.appearance color-scheme
(<<uint32 1>>,)                       # 1 = prefer dark

# and, calling the toolkit's own library directly:
before SDL_Init: SDL_GetSystemTheme() = 0
SDL_Init(VIDEO) = true
video driver = wayland
after  SDL_Init: SDL_GetSystemTheme() = 0        # 0 = UNKNOWN
```

The desktop answers. The portal answers. SDL does not.

**Why.** On Linux, SDL's theme detection is **entirely** the D-Bus portal —
`src/core/linux/SDL_system_theme.c` reads `org.freedesktop.appearance color-scheme` from
`org.freedesktop.portal.Settings` and subscribes to its `SettingChanged` signal, and there is no second
path. That whole file is behind `SDL_USE_LIBDBUS`, which is `#define`d only when `HAVE_DBUS_DBUS_H` is,
which SDL's CMake sets from a **build-time** probe:

```cmake
dep_option(SDL_DBUS "Enable D-Bus support" ON "${UNIX_SYS}" OFF)
...
if(SDL_DBUS)
  pkg_search_module(DBUS dbus-1 dbus)          # headers, not the library
```

SDL loads `libdbus-1.so` at *run* time by `dlopen`, but only if the *headers* were present when it was
compiled. On the machine that built Goldberry's vendored SDL they were not, so:

```
$ grep DBUS natives/.deps/linux-x64/sdl3-build/include-config-release/build_config/SDL_build_config.h
/* #undef HAVE_DBUS_DBUS_H */
```

and `SDL_GetSystemTheme()` is a compile-time constant `UNKNOWN` in every copy of `libgoldberry.so` built
that way — on every Linux desktop, however it is set.

**It is not only the theme.** The same probe gates more of the same file set, and all of it is off in
this build:

| `#undef` | what it turns off |
|---|---|
| `HAVE_DBUS_DBUS_H` | the system theme **and** its change signal, screensaver inhibit, the portal file dialog |
| `HAVE_IBUS_IBUS_H`, `HAVE_FCITX` | the **input method on X11** — [G15](#closed) and [G16](#closed), both recorded as closed. Not on Wayland: SDL drives `zwp_text_input_v3` from the compositor there and needs neither, which is why brd's IME work looked fine |
| `HAVE_LIBUDEV_H` | input-device hotplug |

That is the part worth the entry. A missing `-dev` package silently downgrades three shipped
capabilities — one of them completely, one on a display server this machine does not happen to be
using — and nothing in the build or at run time says so. The theme is simply the one that got noticed,
because it is visible the moment the window opens.

**Why it is not brd's.** Three reasons, and any one is enough. The superbuild is Goldberry's
([ADR-0003](adr/0003-one-native-library-one-superbuild.md) is brd's record of *its* superbuild; the
toolkit's is its own). Reading the portal from brd would be brd talking D-Bus to the desktop, which is
platform integration and exactly what [ADR-0015](adr/0015-no-reimplementation-of-goldberry.md) exists to
prevent — and it would be a second answer to a question `Host.systemTheme()` already answers, which is
worse than none. And brd cannot fix the shipped binary for anybody else: the client is distributed with
`libgoldberry.so` inside it.

**What it would look like.** Two parts, and the second matters more than the first:

1. **Ask for D-Bus on purpose.** `set(SDL_DBUS ON CACHE BOOL "" FORCE)` is already the default; what is
   missing is that the headers are a declared build dependency of the superbuild, named in the toolkit's
   build documentation and installed in CI (`libdbus-1-dev` / `dbus-devel`) — as `libibus-1.0-dev` and
   `libudev-dev` are for the two rows below it.
2. **Fail, or say so, rather than degrading in silence.** A configure that finds no `dbus-1` should
   either stop, or print one line that survives into the build log and a capability an application can
   read back:

   ```java
   /// What this build of the platform layer can actually do, whatever its API says.
   Set<Capability> Goldberry.capabilities();   // SYSTEM_THEME, INPUT_METHOD, DEVICE_HOTPLUG, …
   ```

   An application that could ask would have caught this at start-up instead of shipping a theme control
   that silently means "light" on one of the three platforms. That second half is the general fix: it is
   [G23](#closed)'s shape — the toolkit knowing something the application cannot see and being asked for
   it — rather than a one-off.

**What landed** (ADR-0325). Both halves the entry asked for, and a third it did not.

*The headers are a declared dependency.* `dbus-1` is `NEEDED` in the toolkit's `LinuxDependencies`
table rather than `OPTIONAL` — the necessity that already means "SDL drops this silently, so fail here
because SDL will not" — and `ibus-1.0` is a row that did not exist. `linux.yml`, which builds the
**published** artifact, installs `dbus-devel`, `systemd-devel` and `ibus-devel`; the two Ubuntu
workflows gain `libibus-1.0-dev`. The CI drift guard used to check that workflow for hard stops only,
which is why it never noticed; it now checks the package behind every capability.

*The build stops rather than degrading.* The superbuild probes for the same pkg-config modules SDL
probes for, names the package on both package managers when one is missing, and then cross-checks its
own prediction against the `SDL_build_config.h` SDL generated — headers present and `HAVE_DBUS_DBUS_H`
still `#undef` is now a build failure rather than a library that would claim a capability it does not
have. `-Pgoldberry.allowDegradedPlatform=true` builds one on purpose, for a machine that cannot install
the package.

*And the library says what it is.*

```java
Set<Capability> Goldberry.capabilities();   // SYSTEM_THEME, INPUT_METHOD, DEVICE_HOTPLUG,
                                            // FILE_DIALOG, SCREENSAVER_INHIBIT
```

The bits are compiled into `libgoldberry` by the superbuild, read back through one downcall, and
checked against C by the same layout probe every other constant goes through. They describe the
**library**, not the session: a build that can ask reports `SYSTEM_THEME` even on a desktop that has no
such setting, because *"could not ask"* and *"asked and was told nothing"* are different facts and only
the first is fixable — the second is what an empty `Host.systemTheme()` still means. The `sdl3` backend
also warns once at start-up when a capability it ships an API for is absent, naming the package.

Two things the entry expected are deliberately not true. `INPUT_METHOD` is about **X11** on Linux and
says so in its javadoc: a Wayland session needs neither IBus nor Fcitx, so a build-time bit cannot mean
more than "whether an X11 session would have one". And `FILE_DIALOG` reports the **portal**: SDL also
shells out to `zenity`, so a library without the bit may still open a dialog where that binary happens
to exist.

**What brd does meanwhile.** Nothing to the platform, and two things to itself. The Settings note under
the theme control says *"Nothing has told brd what this desktop is set to"* and names the way out,
rather than the sentence it had before — *"this desktop has no light-or-dark setting"* — which was a
client confidently blaming the user's machine for its own toolkit's build. And `BrdApp` logs one line at
start-up when `SYSTEM` is the choice and the answer is empty, because a client that follows the desktop
and cannot see it is indistinguishable from a client that ignores it. There is **no stopgap to delete**
when this lands: both are things brd should say regardless, and `DesktopTheme` already reads the real
answer the moment there is one.

### G33 — hovering, for something that is not a menu item — **closed**
<a id="g33"></a>

**Raised 2026-09-15**, by building the Palette's hover-hold Peek
([ADR-0033](adr/0033-a-label-on-the-line-a-grip-that-turns-and-four-surfaces-the-shell-owed.md)),
which is §3.4's *"Peek on hover-hold, push on Enter"*.

**What is missing.** There is no way to be told that the pointer has entered or left an arbitrary
subtree. The router derives `PointerEvent.Kind.ENTERED` and `EXITED` already — they are documented as
synthetic, computed from pointer flow — and two widgets consume them: `menu.MenuTitle` and `menu.Item`
each take an `onHovered`, because a menu bar opens on hover. Nothing else can ask.

**Why that is not enough here.** A Palette row is not a menu item. It is a `row` holding a button, a
stretch and a label, because §11 wants the *name* to be what a screen reader is told is pressable rather
than the whole strip. Making it a `menu.Item` to get the hover would be choosing a widget for its event
hook, which is the tail wagging the dog — and `Item` brings a tick column, an accelerator and a chevron
that a search result has no use for.

**Proposed.**

```java
// io.github.digitalsmile.goldberry.widget.attr.Attributes
public Attributes onPointerEnter(Runnable action);
public Attributes onPointerExit(Runnable action);
```

On `Attributes` rather than as a widget, because that is where every other cross-cutting node property
already lives (`tooltip`, `name`, `contextMenu`) and because it then composes with any widget rather than
wrapping one. It must **not** consume: a press that lands inside still belongs to whatever is inside, and
a hover hook that swallowed events would be a row you cannot click.

If that is the wrong shape, the alternative that would also answer is a `HoverRegion` in
`widgets.core` — a container that reports and consumes nothing.

**What Tessera does meanwhile.** `io.github.digitalsmile.tessera.widgets.hover.HoverRegion`: a `Widget.Leaf`
implementing `Handles`, whose entire body is a three-arm switch over `event.kind()`. It reimplements
nothing — the enter and exit events are the toolkit's own — but it is a *widget Tessera owns for an input
concern*, which is the kind of thing §5.3 says to delete when the upstream change lands. **It is a
stopgap and is marked as one.**

**Not a defect.** Nothing is broken; the hook has simply only ever been needed by menus, which is why it
is only on menus.

**Closed — [ADR-0327](../book/src/adr/0327-a-hover-is-a-node-property-not-a-menus.md).** The
`Attributes` shape, as proposed: `onPointerEnter(Runnable)` and `onPointerExit(Runnable)`, with the
chainable pair on `Attributed` so they compose with any widget. The router runs them from the same
walk that moves `:hover`, so a hook is about the **subtree** — one arrival when the pointer enters
anywhere inside, one departure when it leaves altogether, and nothing in between. They consume
nothing and cannot: the event is synthetic and is delivered to one element rather than down a chain.
There is deliberately no markup form; a `Runnable` is not a KDL value and `Attributes.of(KdlNode)`
has no `Wiring`. **The `HoverRegion` stopgap can go.**

One thing to know: a node unmounted **under** the pointer does still hear its exit — the router lets
go of a dead element and re-hit-tests against the frame just painted (ADR-0303), which is the same
walk these are raised from. What is not covered is a teardown with no frame after it, so a caller
holding a hover-hold timer cancels it on dispose as well.

---

### G34 — a long value in a read-only field opens scrolled to its end — **closed**
<a id="g34"></a>

**Raised 2026-09-15**, by putting the peer runtime's invite on the Settings page
([ADR-0037](adr/0037-the-board-in-the-window-is-the-board-on-the-wire.md)). The invite is ~120
characters of z-base-32 that exists to be selected and copied onto another machine.

**What works.** `TextInput.readOnly(true)` is exactly right and was the fix: it takes focus, selects
with the pointer and with `Ctrl+A`, copies with `Ctrl+C` through the host's own clipboard, and refuses
every edit. That is the whole of what a value somebody has to take off the screen needs, and it was
already there.

**What is missing.** The field opens **scrolled to the end**. `TextEdit.of(text)` puts the caret at
`text.length()` and the viewport follows the caret, so a value wider than the box shows its tail: a
person looking at the invite sees `…fiahiyvvqd` rather than `endpointabrq…`. For an *editable* field
that is right — you type at the end of what is there. For a read-only one it is backwards, because
nobody is going to type and everybody is going to read from the left.

**Proposed.**

```java
// io.github.digitalsmile.goldberry.widgets.form.textinput.TextInput
public TextInput caret(int offset);   // where the caret starts; the view follows it
```

Or, without new API and probably better: **a read-only field starts its caret at 0**. It cannot be
typed into, so there is no "where I left off" to preserve, and the first thing a reader wants is the
beginning of the value. Both would answer this; the second needs no call site to remember anything.

**What Tessera does meanwhile.** Nothing. There is no stopgap, on purpose: the field is *usable* --
`Ctrl+A`, `Ctrl+C`, and a drag-select all work, and a selection that runs past the edge still copies
whole -- so what is left is cosmetic, and a Tessera-side hack that re-implemented a caret to fix a
scroll offset would be exactly what [ADR-0015](adr/0015-no-reimplementation-of-goldberry.md) forbids.
The entry records it instead.

**Not a defect.** The behaviour is right for the case the widget was built for; read-only is the case
that had not been looked at.

**Closed — [ADR-0326](../book/src/adr/0326-a-value-you-cannot-type-into-opens-at-its-beginning.md).**
The second option, which the entry itself called the better one: a read-only field starts its caret
at 0, on mount and on a value the application sets later. No new widget API and nothing for a call
site to remember. `TextEdit.atStart(String)` joins `TextEdit.of(String)` as its mirror. An editable
field still opens at the end, because that is where you type.

### G35 — no codec for GIF or WebP, and no file-drop event — **closed**
<a id="g35"></a>

**Raised 2026-09-16**, by building the asset pipeline and the image tool
([ADR-0042](adr/0042-a-picture-has-an-address.md)). Two things, in one entry, because they are the two
halves of "put a picture on a board" that Tessera cannot do for itself.

#### 35a — GIF and WebP do not decode — **closed**

**What works.** `Image.decode` handles PNG, JPEG and QOI, and handles them well: format from the
bytes rather than from a name, premultiplied BGRA out, and the decoder's handle destroyed before it
returns so an `Image` is a value that can go in a cache. All of that is exactly what the board needed.

**What is missing.** Blend2D is built with those three codecs and no others
([Blend2dImage](../../goldberry/natives/src/main/java/io/github/digitalsmile/goldberry/natives/blend2d/Blend2dImage.java)
says so in as many words), so a WebP screenshot — which is what most screenshot tools now write — and
a GIF both throw `ImageDecodeException`.

**Why it matters more than it looks.** The *node* accepts all four, because a browser decodes all
four, so this is the one place the desktop client and the org node have to disagree about what a valid
asset is. Tessera handles that honestly — the client narrows its intake set and refuses a WebP with a
sentence naming the format — but the correct outcome is that it does not have to.

**Proposed.** No new Java API at all; this is a build flag and two dependencies.

```text
# natives/, alongside the PNG/JPEG/QOI codecs already compiled in
libwebp   for image/webp  (BSD, ~300 KB, the reference decoder)
+ a GIF decoder, or LodePNG-style single-file if a dependency is unwelcome
```

If the size is the objection, **WebP alone would close the case that actually occurs**: GIF on a
whiteboard is rare and WebP is what a 2026 screenshot tool produces.

**What Tessera does meanwhile.** `ImageCache.DECODABLE` is `{PNG, JPEG}`, the client's `AssetLimits`
are narrowed to it, and intake refuses anything else with `FORMAT_NOT_ACCEPTED` and a message that
names the format. **This is not a stopgap to delete** — an accepted-format set is a legitimate
configuration axis and the node uses the wide one — but the client's set becomes `{PNG, JPEG, GIF,
WEBP}` the day this lands, and `ImageCacheTest.decodableFormats` is the assertion that will need
changing.

**Closed — [ADR-0329](../book/src/adr/0329-two-more-codecs-one-fetched-and-one-written.md), and it is
one gap with two answers.** `Image.decode` now sniffs the bytes and routes: PNG, JPEG and QOI to the
rasterizer as before, WebP to **libwebp** — fetched by the superbuild, decoder target only, three
symbols bound with no C glue — and GIF to a decoder this toolkit **wrote**, in `:core` beside the PNG
encoder. The split is the point: VP8 is a video codec and there is no Java answer worth writing; GIF
is a palette, a few block headers and LZW, and taking a second native dependency for that costs more
than owning it.

An animated GIF decodes to its **first frame**, composited onto the logical screen the file declares.
An animated WebP does not decode at all — that needs `webpdemux`, which is not built.

`Image.CLIPBOARD_MIMES` grew by `image/webp` and `image/gif` the same day, because that set is a
statement about what can be decoded. **The client's set becomes `{PNG, JPEG, GIF, WEBP}` now**, and
`ImageCacheTest.decodableFormats` is the assertion to change.

#### 35b — a file dropped on a window raises nothing — **closed**

**What is missing.** There is no drop event anywhere in the toolkit. SDL3 has
`SDL_EVENT_DROP_FILE`, `SDL_EVENT_DROP_TEXT`, `SDL_EVENT_DROP_BEGIN`, `SDL_EVENT_DROP_POSITION` and
`SDL_EVENT_DROP_COMPLETE`; none is surfaced, so dragging a PNG from a file manager onto a board does
nothing at all.

**Proposed.**

```java
// io.github.digitalsmile.goldberry.Window
public Subscription onFileDrop(Consumer<FileDrop> listener);

/// Paths dropped on this window, and where.
public record FileDrop(List<Path> paths, LogicalPoint at) {}
```

The position is the part worth insisting on: a board needs to know **where** something was dropped,
not merely that it was. `SDL_EVENT_DROP_POSITION` already carries it. A `Subscription` rather than a
setter, for `onResize`'s reason.

`onTextDrop` would be the same shape and is not asked for here: nothing in Tessera wants it yet.

**What Tessera does meanwhile.** Nothing, and the gesture is simply absent. The two routes that do
exist — `I` or the tool button for a file dialog, and `Ctrl+V` for a clipboard picture — cover the
common cases, and
[AssetService.fromFile](../tessera-app/src/main/java/io/github/digitalsmile/tessera/app/asset/AssetService.java)
is already the shape a drop needs: it takes a `Path` and is public for exactly that reason. When this
lands, wiring it is one call.

**Closed — [ADR-0330](../book/src/adr/0330-a-dropped-file-arrives-somewhere.md).** As proposed,
including the position:

```java
window.onFileDrop(drop -> board.place(drop.first(), drop.at()));
```

`Subscription` rather than a setter, and for a reason of its own rather than `onResize`'s: a drop is
aimed at whatever is under the pointer, so a second listener must not replace the first. One event
per **gesture** — the platform's beginning, moving position, one event per file and end are
reassembled in `Window`, where it is testable without a desktop. `onTextDrop` is still not built;
nothing has asked for it.

**Not a defect either.** Neither half is broken behaviour; both are surface that has not been built,
and both were found by needing them rather than by reading the API.

---

### G36 — a `chip`'s dot takes no colour — **closed**
<a id="g36"></a>

**Raised 2026-09-16**, by giving the Header's Project pill something real to show
([ADR-0044](adr/0044-a-project-is-a-row-and-a-menu-that-measures-the-pill.md)).

**What works.** `chip` draws the pill [ux-design.md](ux-design.md) §10 described before there was one:
a rounded label with a pressed state and, with `withDot(true)`, a leading dot. That is the control the
Project filter has used since the shell was built, and the shape is right.

**What is missing.** The dot's colour. It is the toolkit's -- one colour for every chip -- and a
Project's colour is the Project's: [plan §0](plan.md) gives a Project *"a goal, colour, due date,
members and status"*, and the colour exists because six labels in a menu are told apart by hue long
before they are read.

So Tessera now stores a colour per Project and cannot draw it. The pill says *which* Project by name,
the menu's rows are distinguished by name, and the hue is a column with no pixel.

**Proposed.** One optional colour beside the flag that is already there.

```java
// io.github.digitalsmile.goldberry.widgets.controls.chip.Chip
public Chip withDot(boolean value);              // as today
public Chip withDot(int argb);                   // a dot in this colour, and shown
```

A colour rather than a class name, because the value is *data*: a Project's hue is a row in a database,
not a variant somebody wrote a rule for, and a stylesheet cannot have a class per Project. `int argb`
is the toolkit's own colour currency at the paint boundary (`Box.background`, `frame.fillRect`), which
keeps this from needing `Rgba` in the widget API.

The same argument will apply to a `chip` used for a Ticket's status dot (plan K2's live chips), which
is the second caller and is why this is worth a parameter rather than a Tessera widget.

**What Tessera does meanwhile.** Nothing, and there is **no stopgap**: the pill and the rows are
labelled by name, which is complete rather than degraded --
[ProjectColors](../tessera-app/src/main/java/io/github/digitalsmile/tessera/app/projects/ProjectColors.java)
hands out a hue per Project so the data is right when the control can take it, and the two lines that
draw it are `withDot(project.color())` in
[ProjectPill](../tessera-app/src/main/java/io/github/digitalsmile/tessera/app/shell/header/ProjectPill.java)
and the menu's rows.

**Not a defect.** The control does what it says; it says one colour.

**Closed — [ADR-0328](../book/src/adr/0328-a-dots-colour-is-data.md).** `withDot(int argb)` beside
`withDot(boolean)`, taking `0xAARRGGBB` — the toolkit's own currency at the paint boundary, so no
colour type enters the widget API — with `0` meaning "the stylesheet decides". A colour turns the dot
**on** as well as colouring it, and a chip carrying a colour with no dot is refused at construction,
which is the same refusal the dot-and-icon pair already gets. Markup says it too:
`chip dot-colour="#bf616a"`, or `dot-color=` for whoever spells it that way. So
`withDot(project.color())` is the line, as written.

---

### G37 — a line-number gutter on `text-area` — **closed**
<a id="g37"></a>

**Raised 2026-09-16**, by building the note editor
([docs/notes.md](notes.md) N3, [ADR-0047](adr/0047-a-note-is-a-module-and-a-splice-counts-the-way-the-document-does.md)).

**What works.** `text-area` is the editor pane, and almost all of it: soft wrap at the control's width,
`fill(true)` so it takes the height a content band gives it rather than growing to fit, the bundled
JetBrains Mono behind `class="mono"`, and an editing model — selection, clipboard, undo, word
operations, `Up`/`Down` by visual line — that is `text-input`'s and needed no second copy.

**What is missing.** The line numbers [plan §10 E1](plan.md) asks for.

They cannot be composed from outside the widget, and that is the whole entry: the numbers have to line
up with **hard** lines drawn at **soft**-wrapped positions, and only the thing that laid the text out
knows where those fell. A `Column` of numbers beside the pane is correct until the first line that
wraps, and then every number below it is wrong — which is worse than having none, because it looks
like it works.

**Proposed.** One flag, on the widget that has the layout.

```java
// io.github.digitalsmile.goldberry.widgets.form.textarea.TextArea
public TextArea gutter(boolean on);          // numbers hard lines, at the y each was laid out at
```

```kdl
text-area class="mono" gutter=#true fill=#true bind="note.body" change="note.type"
```

A boolean and not a renderer: what a line number looks like is the stylesheet's (`text-area > .gutter`),
and an application that wanted to draw something else in that column would be asking for a different
widget rather than a parameter.

**What Tessera does meanwhile.** Nothing, and there is **no stopgap**. The pane ships without line
numbers — one of E1's five clauses, visibly absent rather than faked. Everything else about the editor
works, which is why this is a gap and not a blocker.

**Not a defect.** The control does what it says; it says nothing about lines.

**Closed — [ADR-0331](../book/src/adr/0331-a-gutter-numbers-hard-lines-at-soft-positions.md).** One
flag, as proposed: `TextArea.gutter(boolean)` and `text-area gutter=#true`. What is worth knowing is
*how*, because the obvious design does not work. The numbers are **not** a column of nodes: a
widget's children are described before anything is laid out, so they would have been a frame behind
the text on every keystroke that changed the line structure — and nothing would have asked for the
correcting frame. They are one text box the control draws itself, with a number per hard line and an
empty line per wrap, at the control's own line height and the control's own scroll offset, so they
cannot drift from the text by construction.

The stylesheet owns the strip (`text-area-gutter`), the ink (`--gb-gutter-color`) and the room
(`--gb-gutter-gap`). There is no `text-area-line-number` node, and both classes say why.

---

### G38 — an editing seam an application can talk to — **closed**
<a id="g38"></a>

**Raised 2026-09-16**, from the same build, and it is the other half of E1's line.

**What works.** `text-area` reports **the new whole value** through `change=`, which is exactly right
for a form field and is what every other control in the toolkit does.

**What is missing.** The caret and the selection. `Ctrl+B` around a selection, `-` + `Enter`
continuing a list, `Tab` indenting one — every Markdown shortcut in E1 needs to know *where the caret
is* and *what is selected*, and an application can see neither.

The value that holds exactly that already exists and is already what the widget uses internally:
`io.github.digitalsmile.goldberry.text.edit.TextEdit`, a record of `(text, anchor, caret)` with every
motion and every deletion as a pure function. This is a request to let it out, not to invent it.

**Proposed.** The change event, in the richer currency, and a way to apply one back.

```java
// io.github.digitalsmile.goldberry.widgets.form.textarea.TextArea
public TextArea onEdit(Consumer<TextEdit> listener);   // text, anchor and caret, after every change
public TextArea edit(TextEdit next);                   // an edit the application computed, caret and all
```

`change=` stays as it is — a form does not want a caret — and this is beside it, for the callers that
are editors rather than fields. The second method is half the request rather than a convenience:
wrapping a selection in `**` is an edit *and* a caret move, and an application that could compute one
but only push back a `String` would leave the caret wherever the widget decided.

**Why the caret cannot simply be inferred.** It can, for typing, and that is the trap. Tessera already
computes where a change happened — `TextSplice.between(before, after)`
([ADR-0047](adr/0047-a-note-is-a-module-and-a-splice-counts-the-way-the-document-does.md)) — so after a
keystroke the caret is at the splice's end. That inference is correct for typing and wrong for a
selection, a click, and every caret move that changes no text at all: three of the four things a
shortcut needs to know about. Writing it would be a stopgap that is right often enough to be trusted
and wrong exactly when a shortcut fires.

**What Tessera does meanwhile.** Nothing, and there is **no stopgap** — see the paragraph above for
why that is a decision rather than laziness. The editor ships with no `Ctrl+B`, no list continuation
and no `Tab` indent; typing, selecting, the clipboard, undo and the live preview all work.

**Not a defect.** The event reports what it promises to report.

**Closed — [ADR-0332](../book/src/adr/0332-an-editor-is-handed-the-caret.md).** Both halves, as
proposed: `onEdit(Consumer<TextEdit>)` reports text, anchor and caret after **every** change — a
caret move and a selection included, which is the three-of-four the entry is about — and
`edit(TextEdit)` pushes one back. `change=` is untouched and still fires only when the text differs.

Two rules to build against. A pushed edit is **offered, not imposed**: the control adopts it when it
*changes* and ignores it on every rebuild in between, exactly as `value=` works, so a constant edit
does not fight the keyboard. And it is applied **after** `bind=` in the same build, so a shortcut that
changes the model *and* moves the caret in one action ends at the caret it asked for. It goes into
the undo history — `Ctrl+Z` undoes a `**` the way it undoes a keystroke — and is **not** echoed back
through `onEdit`.

<a id="g39"></a>

### G39 — faces an application ships, reachable from `font-family` — **closed**

**What Tessera needs.** The landing page sets display text in **Forum** and running text in **Golos
Text**, both SIL OFL. The client should use the same two. Today a stylesheet can name only the faces
`BundledFont` enumerates, so `font-family: Forum` resolves to nothing and the hero title is Inter at 32px
standing in.

**Why it is not Tessera's.** A face has to reach the cascade, `Paragraph` layout, the text-input caret and
the rasterizer's glyph cache together, and all four are Goldberry's. A canvas *could* load the bytes with
`Font.of(byte[], size)` and draw a title itself, but a title that does not select, wrap or follow
`font-size` like every other label is a second text stack.Cfif 

**Proposed API.**

```java
/// Faces the application ships, added to the families `font-family` resolves -- after the bundled ones,
/// so an application cannot shadow Inter by accident. Read once at start-up.
default List<FontSource> Application.fonts() { return List.of(); }

/// One face: a family name as CSS writes it, which weight and style it is, and where its bytes are.
record FontSource(String family, BundledFont.Weight weight, BundledFont.Style style, Supplier<byte[]> bytes) {
    static FontSource resource(String family, Weight weight, Style style, Class<?> anchor, String name);
}
```

and the same list on `Offscreen.fonts(...)`, so a render test paints what the window paints. The
`Weight`/`Style` closed matrix is kept as it is: a face that fills a corner it does not have falls back the
way `BundledFont.of` already does.

**What Tessera does meanwhile.** Nothing to the text. `.home-title` is size and regular weight, and
`docs/brand/README.md` says which face it is standing in for.

**Closed — [ADR-0349](../book/src/adr/0349-a-face-an-application-ships-is-found-after-the-bundled-ones.md).**
As proposed, with the record in `text.font`: `Application.fonts()` returns `FontSource`s, each a
family, a `Weight`, a `Style` and a `Supplier<byte[]>`, with `FontSource.resource(…)` and `FontSource.of(…)`
as factories. The same list goes to `Offscreen.fonts(List<FontSource>)`. The window's book searches the
bundled faces first, so a file called `Inter` is never drawn, and then the shipped ones, by the **same**
matching rule (`assets.Face.match`) that `BundledFont.of` now delegates to. A shipped face is read the
first time something is drawn in it. One that cannot be read or parsed is logged once and drawn in
Inter, not thrown out of a paint pass. Tessera's `TesseraApp.fonts()` is the change, and the
`.home-title` stand-in note can go.

<a id="g40"></a>

### G40 — a window icon — **closed**

**What Tessera needs.** The taskbar, dock and window switcher to show the mark
(`docs/brand/png/tessera-icon-dark-*.png`) rather than the platform's generic application icon. This is
separate from installer packaging (plan D6), which covers the icon in the launcher. A window run from a
jar or `gradlew run` has only what the toolkit sets.

**Why it is not Tessera's.** It is `SDL_SetWindowIcon` on a surface, and on Wayland the `xdg-toplevel-icon`
protocol. Both are platform calls that sit behind `Host` ([ADR-0004](adr/0004-ffm-lives-in-one-module.md)).

**Proposed API.**

```java
/// The window's icon, largest first; the platform picks the size it wants. Empty is the platform default.
default List<Image> Application.icon() { return List.of(); }
```

`Image` rather than a path, because `Image.decode` already exists and the application can
decode from its own resources. A list because Windows wants 16 and 32, macOS wants 1024, and scaling
one down is the blur the PNG set exists to avoid.

**What Tessera does meanwhile.** Ships the PNGs, and `AppIcon` paints them, so the day this lands the
change is one method on `TesseraApp`.

**Closed — [ADR-0351](../book/src/adr/0351-a-window-icon-is-several-sizes-and-the-backend-picks-the-base.md).**
`Application.icon()` returns `List<Image>`, and `Window.icon(List<Image>)` is there for a change at
runtime. SDL's model decides one thing the proposal left open: the surface passed to
`SDL_SetWindowIcon` is the **100% size**, the others hang off it as alternates, and X11 reads only
that base. So the backend picks it, as the smallest size at least 48 wide, and the order of the list
does not matter. Pixels leave in straight alpha. Two symbols are bound as optional, so a stale
`libgoldberry` shows the generic icon. macOS and a Wayland compositor without `xdg-toplevel-icon` answer
false, which is logged at debug. The change in Tessera is one method on `TesseraApp`.

<a id="g41"></a>

### G41 — a `canvas` that can ask for its next frame — **closed**

**What Tessera needs.** The site's tiles **settle**: each drops in from 20px above, turned a few degrees,
staggered by its distance from the focus. After that, one tile every 1.3 seconds takes the glaze of a
neighbouring band. `Mosaic.Tile.delayMillis` already carries the stagger, and a `StyledPainter` is already
handed `nowMillis` and `reducedMotion`. What is missing is a way to ask for a *next* frame:
`Paints.isAnimating()` is how a `Spinner` keeps the idle loop turning, and `Canvas` does not override it,
so a canvas painted from the clock is painted once.

**Why it is not Tessera's.** Tessera cannot implement `Paints` on a leaf of its own without writing a
second `Canvas`, and a timer that calls `host.repaint()` repaints the whole window on a clock the frame
pacer cannot see (ADR-0271's lateness measurement would count every one of them as late).

**Proposed API.**

```java
/// This canvas, asking for another frame while `animating` says so -- read once per frame, after paint,
/// with the same frame time the painter was given. `reducedMotion` is the painter's to honour.
public Canvas Canvas.animating(Predicate<CanvasStyle> animating);
```

A predicate rather than a boolean, so the settle can stop by itself when the last tile has landed
(`now - mountedAt > maxDelay + 850`) instead of the application rebuilding the widget to turn it off.

**What Tessera does meanwhile.** The floor is still, and `reducedMotion` users get what they would have got
anyway.

**Closed — [ADR-0348](../book/src/adr/0348-a-canvas-asks-for-its-next-frame-with-what-it-was-painted-with.md).**
As proposed: `Canvas.animating(Predicate<CanvasStyle>)`, asked once per frame straight after the
painter is bound, with the same `CanvasStyle`, so the frame that lands the last tile is the frame that
answers false. `Paints` gained `isAnimating(ComputedStyle, Context)` for it, and the no-argument
form every other widget uses is untouched. The showcase's Motion screen is the settle this entry
describes, with the part it implies spelled out
([ADR-0354](../book/src/adr/0354-a-choreography-is-a-function-of-time-and-a-timer-wakes-it.md)): the
predicate asks for frames while something moves, and a `host.after(1.3s)` timer starts each glaze
swap, so the loop idles between them.

<a id="g42"></a>

### G42 — hand a URL to the desktop — **closed**

**What Tessera needs.** A `[link](https://…)` pressed in a note's preview to open in the browser, and a
`mailto:` in the mail client ([ADR-0052](adr/0052-the-note-scope.md)). `markdown-view.onLink` already
hands over the `href` and says, correctly, that following it is the application's -- and the application
has nothing to follow it with.

**Why it is not Tessera's.** It is `SDL_OpenURL`, or `xdg-open` / `ShellExecute` / `NSWorkspace` behind
it: a platform call, and the toolkit owns the platform layer ([ADR-0004](adr/0004-ffm-lives-in-one-module.md)).
`java.awt.Desktop` would work on some desktops and pull AWT into a client that has none.

**Proposed API.**

```java
/// Hands `url` to the desktop. A request, not a result: false when the platform would not take it --
/// headless, a scheme nothing handles -- which the caller reports rather than retries.
default boolean Host.openExternal(String url) { return false; }
```

A local `../goldberry` checkout already has exactly this, uncommitted (it names ADR-0346), which is how
the signature above was checked against the toolkit's style -- and why nothing in Tessera calls it yet:
the published snapshot does not have it ([ADR-0050](adr/0050-goldberry-is-a-published-snapshot.md)).

**What Tessera does meanwhile.** `NoteLinkActions` is built with no opener, so a web link says *"Opening
web links from a note waits on the toolkit (G42)"* and the URL, rather than doing nothing. The day this
lands it is `new NoteLinkActions(model, host::openExternal)` and the test for the refusal is deleted.
Schemes other than `http`, `https` and `mailto` stay refused either way: a `file:` link in a shared note is
not something a click should follow.

**Answered — [ADR-0346](../book/src/adr/0346-a-link-is-a-word-and-the-desktop-opens-the-rest.md).**
The uncommitted change this entry saw is committed: `Host.openExternal(String)` with the signature
above, `SDL_OpenURL` behind it, and §2's `link` built on it. Tessera's refusal test goes once it takes
a snapshot that has it.

<a id="g43"></a>

### G43 — `text-area`'s gutter strip does not reach its padding — **closed**

**A defect, not a missing feature**, in the published `2026.1-SNAPSHOT`.

**What happens.** `TextAreaBox` places `text-area-gutter` absolutely with insets of `-padding.top`,
`-padding.bottom` and `-padding.left`, and a width of `gutterWidth + padding.left` -- the comment says
*"pulled out to the border on the left so the control's own padding is inside it"*. What paints is a strip
the size of the **content** box: with `padding: 12px 16px`, the filled column starts 12 px down and 16 px
in, and a band of `text-area`'s own background shows above it and to its left. With the left padding
removed the left band goes and the top one stays -- which is how it was found, from a person saying the
padding was "still weird" after exactly that change.

Measured in Tessera's note editor (`build/reports/notes/editor-edit.png`): at `padding: 12px 16px 12px 0`
the column's fill begins at y = 12, and the pixels above it are `--gb-surface-sunken`.

**And a second symptom, worse than the first.** With `--gb-gutter-gap` set to anything but its 8 px
default **and** a non-zero left padding, the numbers are wrapped against a different width than the text:
after the first soft-wrapped line every number sits one visual line too high. Isolated by rendering one
change at a time -- gap 14 with left padding 4 misaligns; gap 8 with padding 4, and gap 14 with padding 0,
are both right. Presumably one of the two widths reads the token and the other the constant, or one
subtracts the padding and the other does not.

**Why it is not Tessera's.** The strip's box and the numbers' wrap are the widget's; there is no
declaration that moves either, and a Tessera-drawn column beside the pane is the thing G37 closed.

**What would fix it.** Whatever makes the negative insets land -- most likely the insets being taken in
border-box coordinates by a layout that places absolute children in the padding box, the same class of
mismatch ADR-0272 fixed for the caret and the selection. A local `../goldberry` checkout has uncommitted
changes in this file; if one of them is this, publishing it closes the entry.

**What Tessera does meanwhile.** `.note-source text-area-gutter { background: transparent }`: no fill, so
no band, and the muted numbers mark the column on their own. And the left padding is **0**, with the room
coming from a 14 px gap, which is the one combination that keeps the numbers on their lines. `MarkdownEditorRenderTest.theGutterHasNoBand`
asserts the padding and the column are one colour. When this lands the rule is deleted and the column
gets its fill back.

**Closed — [ADR-0350](../book/src/adr/0350-a-gutter-strip-is-outside-the-clip-its-numbers-are-inside.md).**
The first symptom was the **clip**, not the insets. `overflow: hidden` clips a box's children to its
content box, and the strip is a child, so the strip was placed at the border and cut back by exactly the
padding. `text-area` now draws two layers: a content layer pinned to the content box that clips
everything that scrolls, and the strip beside it, from the border's inner edge, with its leading corners
fitted to the field's radius. The second symptom **could not be reproduced** against this checkout. It
was rendered with every padding and gap combination named above, and the numbers stayed on their lines.
What the reproduction found instead is a real width error next to it: the wrap width was
`width - 2 × left padding`, so `padding: 12px 16px 12px 4px` wrapped the text 12px wider than its room
and ran it under the right padding. It subtracts each edge once now. `TextAreaGutterStripTest` checks
both on pixels. If Tessera's drift survives a snapshot with this in it, it is a new entry.


<a id="g44"></a>

### G44 — `text-area`'s style pass grows with its text

**Measured, not guessed.** In [docs/notes.md](notes.md) N5's benchmark, a window holds one note editor in
**edit** mode: one `text-area`, gutter on, `class="mono"`, and nothing else. One character is typed per
frame, and the window's own `FrameStats` report each stage's mean / worst, in ms:

| note | style | build | layout | raster | frame |
|---|---|---|---|---|---|
| 2 kB | 2.87 / 4.69 | 0.57 | 0.82 | 0.78 | 7.54 |
| 50 kB | **12 / 19** | 2.40 | 0.86 | 1.72 | 23 |
| 500 kB | **186 / 325** | 35 | 15 | 57 | 298 |

The tree is the same size in every row. Only the string in the `text-area` changes, and the style
span grows with it about linearly. Layout, which you might expect to track the text, stays flat up to
50 kB. So the cascade, or something it triggers per line or per run (shaping? the gutter's numbers?), is
doing work in proportion to the text rather than to the one node that changed.

**What it blocks.** Plan E1's *preview < 100 ms* on large notes: a 500 kB note is three times over the
budget with **no preview on screen at all**, and nothing Tessera does before the frame can change that.

**Why it is not Tessera's.** The pane is the toolkit's `text-area` with the text handed back, which is its
documented controlled use. There is no declaration that makes styling it cheaper.

**What would fix it.** Whatever makes restyling a `text-area` whose text changed cost the same as
restyling one whose text did not. No API change is proposed: this is a cost, not a missing feature. The
check is `./gradlew :tessera-notes:benchmark`, and the edit-mode rows should go flat.

**What Tessera does meanwhile.** Nothing. The numbers are in
[docs/status](status/phase-e-notes.md#n5--the-preview-budget--2026-09-17).

<a id="g45"></a>

### G45 — `markdown-view` restyles and lays out every block when one changes

**Measured.** The same benchmark in **preview** mode: a `markdown-view` over `Markdown.parse(text)`,
inside a `scroll`, rebuilt with a new `Document` on every keystroke, as its documentation says to. Each
cell is mean / worst, in ms:

| note | build | style | layout | raster | frame |
|---|---|---|---|---|---|
| 2 kB | 0.92 | 0.96 | 1.73 / 9.78 | 0.88 | 6.67 / 18 |
| 50 kB | 8.35 | 8.76 | **16 / 73** | 9.29 | 47 / 124 |
| 500 kB | 137 | 163 | **213 / 1099** | 142 | 755 / 2015 |

md4c accounts for 4 ms of the 50 kB build and 8 ms of the 500 kB one (timed separately), so almost all of
each row is the view. One keystroke changes one paragraph, yet every stage grows with the whole note.
That is consistent with every block being a new widget each frame, with nothing to match it to the
block it replaced. In split mode the two widgets add up: 74 / 151 ms at 50 kB and 1859 / 4897 ms at
500 kB.

**What it blocks.** The same gate, worse. At 50 kB the **mean** frame is inside 100 ms and the worst frame
is not, and the worst is layout.

**Why it is not Tessera's.** The document model and the view are both the toolkit's, and matching two
consecutive `Document`s block by block is a reconciliation question the element tree answers.
Tessera diffing documents and handing over a patched tree would be a second view.

**Proposed API.** Possibly none: the view could key each block's widget by its position and content hash
internally, so an unchanged block keeps its element and its layout. If it cannot, then something like:

```java
/// The same view over `next`, reusing every block whose source range and text are unchanged from the
/// document this view was last built with.
MarkdownView MarkdownView.of(Document next, Document previous);
```

and `Block.sourceRange()` on the model, which md4c's offsets already have. Lazily building off-screen
blocks inside a `scroll` would take care of 500 kB on its own, but it is a larger change and a separate
entry if it is wanted.

**What Tessera does meanwhile.** Nothing. [ADR-0053](adr/0053-the-preview-budget-is-measured-in-a-window.md)
§5 explains why there is no debounce and no cache.

<a id="g46"></a>

### G46 — a canvas transform that composes with the one the canvas is painted under — **closed**

**What Tessera needs.** The landing page's tiles **turn** as they settle: each starts rotated by up to ±8°
and lands flat. The floor ([ADR-0057](adr/0057-faces-an-icon-and-a-floor-that-settles.md)) drops, fades and
scales its tiles, and leaves the turn out.

**Why it is not Tessera's.** `Frame.transform(a, b, c, d, e, f)` *replaces* the matrix. Inside a canvas the
matrix already holds the translation that puts the canvas on screen, and the painter cannot read it back.
So a painter that wants a rotated rounded rectangle has two options: set a matrix and draw at the window's
corner, or rewrite the path's coordinates itself. Goldberry's own showcase does the second
(ADR-0354's `example.motion.Rotated`, forty lines over every `Path.Segment` kind). That is path geometry,
and a second copy of it in every application that animates a canvas is the kind of second toolkit
[ADR-0015](adr/0015-no-reimplementation-of-goldberry.md) is about.

**Proposed API.** Either of these closes it:

```java
/// Multiplies the current transform by `[a b c d e f]` -- composing with whatever the tree set, which a
/// canvas painter cannot know. Paired with `save()` / `restore()`, which already exist for the clip.
public void Frame.concat(double a, double b, double c, double d, double e, double f);
```

or, keeping `Frame` free of a matrix stack as ADR-0068 wants:

```java
/// This path turned `radians` clockwise about (`cx`, `cy`), then moved by (`dx`, `dy`).
public Path Path.transformed(double radians, double cx, double cy, double dx, double dy);
```

The second is `Rotated` promoted from the showcase, which is why it may be the cheaper answer.

**What Tessera does meanwhile.** Nothing. The tiles drop and fade without turning, and `Settle`'s
documentation says why.

**Closed — [ADR-0390](../book/src/adr/0390-a-turned-shape-is-a-path-and-the-frame-can-compose.md).**
**Both**, because they answer different halves. `Path.transformed(Affine)` — with `rotated`,
`translated` and `scaled` over it, and `paint.geom.Transformer` under it — turns the *shape*, which is
what a rotated tile is. `Frame.concat` multiplies the frame's matrix by the caller's instead of replacing
it, which is what a rotated *image* or a rotated run of text needs, and neither of those is a path.

The matrix type is the cascade's own `css.value.Affine`, not a new one: a second matrix would have to
agree with the one hit testing already inverts. `Frame` keeps ADR-0068's bargain — the stack it composes
against is Java-side, mirrored in a field pushed and popped with `save()`/`restore()`, and assigned
through the `ASSIGN` op already bound. Blend2D's compose op is exported but its *enumerator* is not, and
adding one means editing the shim and rebuilding the native library for something arithmetic already
does.

`example.motion.Rotated` is deleted, and deleting it found a defect: it added the angle to an arc's
`rotation` and left the radii and the `sweep` flag alone, so it was correct for a pure rotation and
plausibly wrong for anything else. The replacement decomposes the transformed ellipse through a
closed-form 2×2 SVD and flips `sweep` under a mirroring matrix. No golden image moved.

<a id="g47"></a>

### G47 — a `qr-code` widget

**What Tessera needs.** Telegram's sign-in by QR code ([docs/chat.md](chat.md) §7.6). TDLib hands the client a
`tg://login?token=…` link and renews it about every thirty seconds until a phone scans one. The connect
dialog has to **draw that link as a QR code** and redraw it when the link changes. The same need is coming
from two other directions: a share link or a device invite handed to a phone (plan L), and any "open this on
your phone" in a Tessera surface.

**Why it is not Tessera's.** A QR code is ISO/IEC 18004: mode selection, Reed–Solomon error correction, eight
mask patterns scored by a penalty rule, format and version bits, and a quiet zone. Then it is a grid of
square modules painted crisply at whatever scale the window is at. The encoder is a specification with one
right answer, like the image codecs the toolkit already owns ([G35a](#closed)), and the grid is painting.
Neither has anything to do with chat. An application that writes its own is the second toolkit
[ADR-0015](adr/0015-no-reimplementation-of-goldberry.md) is about, and every other Goldberry application that
needs one would write it again.

**Proposed API.**

```java
/// A QR code for `payload`, as UTF-8 bytes, at the smallest version that fits it at `level`.
///
/// Sized by the stylesheet like an `image`. Modules are whole device pixels at every scale, so a
/// scanner never sees a blurred edge, and the quiet zone is part of the widget's box.
public record QrCode(String payload, Level level, int quietZone, Attributes attributes) implements Widget.Leaf {
    public enum Level { L, M, Q, H }
    public QrCode(String payload) { this(payload, Level.M, 4, Attributes.NONE); }
    public QrCode level(Level value);
    public QrCode quietZone(int modules);
}
```

```kdl
qr-code value="tg://login?token=…" level="M" quiet-zone=4
```

- **Colours** from two tokens, `--gb-qr-ink` and `--gb-qr-paper`, which default to near-black on white **in
  both themes**. Many phone scanners do not read an inverted code, so a dark theme must not flip it.
- **Semantics**: an image whose accessible name the application sets ("QR code to sign in to Telegram").
  The payload is not exposed, because while it is valid it is a credential.
- **A payload that cannot fit** (more than version 40 holds at the chosen level) is a build-time
  `IllegalArgumentException`, not an empty square.
- **Rebuilding with a new payload** re-encodes. Rebuilding with the same payload does not, which matters
  here because the dialog rebuilds on every keystroke in any field.

**What Tessera does meanwhile.** Draws a dashed square labelled *QR code* with a line naming this entry, in
`ConnectAccountDialog.qrCode` and nowhere else. The token is not printed. The stopgap is deleted in the commit
that takes the widget. Nothing is lost while it stands, because the check that would produce the payload
needs TDLib (CH7), which is not built either.

<a id="g48"></a>

### G48 — a viewport that opens at its end, and stays put when rows are added above — **closed**

**What Tessera needs.** A chat timeline ([docs/chat.md](chat.md) §7.2). Three things, and a `scroll` does
none of them:

1. **Open at the end.** A conversation opens on its newest message. Today a `scroll` opens at offset zero and
   the newest message is off the bottom of a long conversation.
2. **Stay at the end while it is there.** A message arriving while you are at the bottom should keep you at
   the bottom; one arriving while you are reading history should not move you at all.
3. **Keep the reader's line when rows are added *above*.** Paging older messages in puts content above the
   viewport, and everything the reader is looking at jumps down by exactly that much.

`ScrollController.scrollBy` is not an answer to any of the three: the content's height is not known when the
build runs, "am I at the end" is a question about layout, and a scroll issued after the fact is a visible
jump rather than a viewport that never moved.

**Why it is not Tessera's.** All three are layout facts -- where the content ends, how tall what was inserted
is -- and only the engine has them at the moment they are needed. Every application with a log, a console or a
chat wants exactly this; `docs/core-widgets.md` §1 already gives `scroll` "scroll position is retained state
surviving rebuilds", which is the same class of promise one step short of what a timeline needs.

**Proposed API.**

```java
/// Where this viewport sits when it is first laid out, and where it stays as content changes.
///
/// `START` is today's behaviour. `END` opens at the end and **sticks** there while the viewport is already
/// at the end -- a message arriving while the reader is at the bottom scrolls; one arriving while they are
/// reading history does not move them.
public Scroll anchor(ScrollAnchor value);   // enum ScrollAnchor { START, END }

/// Keeps what is on screen still when content is inserted **above** the viewport: the offset moves by the
/// height that was added, so the reader's line does not jump. On by default for `END`.
public Scroll preserveOnPrepend(boolean value);
```

```kdl
scroll anchor="end" preserve-on-prepend=#true { … }
```

**What Tessera does meanwhile.** `RoomView` draws the **last 30 messages** with a *Show earlier* button above
them, so the newest are on screen because they are the only ones built. That is one constant and one button
in one file, and it goes when this lands
([ADR-0059](adr/0059-the-chat-surfaces-are-built-on-a-hub.md)).

**Closed — [ADR-0392](../book/src/adr/0392-a-timeline-opens-at-its-end-and-keeps-the-readers-line.md).**
All three, as `anchor="end"` and `preserve-on-prepend`, and the third one needed something that did not
exist.

"Content arrived **above**" is not a fact any single frame holds: twelve lines added at the top and
twelve added at the bottom are the same number of pixels, and `Extent`, `Measured` and `Located` all
report a property of one frame. So the router gained a fourth geometry facility, `Anchored`: it picks the
first whole row at the viewport's leading edge — the reader's own line — and remembers where that node
sits **inside the content box**, in layout coordinates. The scroll is a transform on that box, so the
number cancels: it moves when something inside changed and not when the viewport did, and growth at the
bottom reports exactly zero rather than something small.

The consequence is stated rather than hidden: it needs **keyed** rows. Children matched by position are
not the same node after a prepend — element 0 merely describes a different message — so nothing is
recognised and nothing moves. Recognising a line a frame later is what a key is for.

"At the end" is half a logical pixel, the same figure `ScrollController.Position` has used since it was
written, and it is a **flag** written when the offset moves on purpose rather than a comparison
recomputed after the content has already changed. Opening at the end is not a separate case: the flag is
on before the first layout. The correction bypasses the glide, because a 240 ms slide per logged line is
not what a console wants. `preserve-on-prepend` is three states and not two, so `.anchor(END)` followed
by `.preserveOnPrepend(false)` and the reverse order mean the same thing.

<a id="g49"></a>

### G49 — emoji render as boxes — **closed**

**What Tessera needs.** A message's reactions and the emoji people type in them
([docs/chat.md](chat.md) §7.2). `👀`, `🎉`, `❤️` in a message body, and a reaction chip that is an emoji and a
count.

**What happens.** They draw as `.notdef` boxes. The toolkit **bundles** the face --
`BundledFont.EMOJI`, OpenMoji, and `docs/design-system.md` §2 calls it "the routed emoji slot" -- and
`docs/ARCHITECTURE.md` §5 describes the routing as *"emoji sequences (ZWJ, VS-16, modifiers) detected during
itemization and routed to the emoji slot"*, planned for M2. So this entry is not asking for a font or a
decision; it is reporting that the routing is not there yet, with the first application that shows it.

**Why it is not Tessera's.** Itemization is inside the shaper. There is nothing an application can do but pick
the face for a whole run, which is wrong for `Rolling to eu-2 🎉 at 14:00` and impossible for a reaction chip
that is an emoji and a number.

**What Tessera does meanwhile.** Two rules, in `MessageRows` and nowhere else: an emoji **Tessera** would have
chosen is a word instead (`Image`, `File`), and an emoji a **service** sent is drawn as sent, box and all,
because the alternative is deciding not to show somebody's reaction. The pictures in
`build/reports/shell/shell-chat-*.png` are the evidence.

**Closed — [ADR-0393](../book/src/adr/0393-an-emoji-is-routed-by-the-text-and-drawn-in-layers.md).**
Two things were missing and either alone would have left boxes on screen.

**Nothing split the text**, so `text.itemize` is new: `Itemizer.runs(text)` returns consecutive runs
labelled `TEXT` or `EMOJI`, by UTS #51's rules read out of `java.lang.Character`. What the sequence rules
add over the per-character properties is where a naive split goes wrong, and each is a test — U+FE0F and
U+FE0E decide presentation, U+200D holds a family together so the face can ligate it, skin tones and tag
sequences and keycaps belong to the emoji they follow, and the `#` of `🎉#ship` is a hashtag rather than a
keycap. `Paragraph` shapes each run in its own face and concatenates one measurement out of the two, with
the emoji face's advances **rescaled** into the base font's design units — Inter is 2048 to the em and
OpenMoji is 1024, and a prefix sum needs one unit. A paragraph with no emoji in it takes the path it took
before, allocating nothing.

**Nothing could have drawn it in colour anyway**, because what shipped was OpenMoji's monochrome build —
and `Asset.OPENMOJI` said why: the colour build was "not bundled until something can draw layered
outlines". It can now. `text.font.sfnt.ColorLayers` reads `COLR` version 0 and `CPAL` in Java, `GlyphFace`
reads them once per typeface rather than per size, and `GlyphPen` draws each layer glyph with its palette
colour. A COLRv0 layer is an ordinary glyph in the same face, so nothing new rasterizes anything; the
artifact carries 2.5 MB instead of 1.4, paid only by an application that adds it on purpose.

The routing is joined up in `Fonts`, the per-window book, so it arrives without an application asking:
every font it hands out gets the emoji face at the same size, opened lazily and closed with the book. A
build with no `goldberry-emoji` on its path gets exactly what it got before, silently, and
`BundledAssets.hasEmojiFont()` is still how one that cares asks.

The showcase's emoji sheet is in colour, and it gained a line of ordinary prose with emoji in it that
**names no font at all** — which is the thing an application actually writes. `MessageRows`' two rules go.
---

**Nothing else, and nothing open.** All five of the latest were raised by building something rather
than by reading the API surface, as all forty-three have been, and all five are answered, in ADR-0346 and
ADR-0348 to ADR-0351. The next entry goes below this line, before any code is written for it (§3).
