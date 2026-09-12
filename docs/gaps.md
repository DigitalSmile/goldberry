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
| **`natives.blend2d.*`** | ~~yes~~ **closed** — `paint.Path`, `paint.Stroke`, `paint.Gradient` | never |
| **`natives.yoga.*`** | no | ~~yes~~ **closed and sealed** — `io.…goldberry.layout` |
| `natives.harfbuzz.*`, `natives.blend2d` fonts | no | only inside `:core`, and [G14](#g14) is the rest |

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

Both are now closed — [G1](#g1), [G2](#g2) and [G10](#g10) for drawing,
[G13](#g13) for layout — and the layout half is **sealed**: `:natives` exports its
Yoga packages to `:core` and to nobody else, so a module that names `StyleLength`
does not compile. What is left is the text stack, which is [G14](#g14), and the
way to enumerate it is to delete one word from a module descriptor and read the
compiler's answer.

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
| [G7](#g7) | Clipboard beyond text: images and custom types | paste a screenshot, copy shapes between boards | medium |
| [G8](#g8) | `goldberry-html`: Markdown through md4c | Note preview (E1), Note → HTML in the Viewer (D1) | medium |
| [G9](#g9) | Native file dialogs | export PNG/SVG/MD (G3), import an image | medium |
| ~~[G10](#g10)~~ | ~~Gradients as a `core.paint` value~~ | **closed** — ADR-0277 | done |
| [G11](#g11) | The computed font of a box, inside a painter | canvas text that follows the theme rather than naming a font | low |
| [G12](#g12) | Deep-link URI handling and single-instance handoff | `brd://open/<token>` (plan D5) | medium |
| ~~[G13](#g13)~~ | ~~The other `natives` leak: Yoga through `paint.Box`~~ | **closed** — ADR-0279, ADR-0280 | done |
| [G14](#g14) | The last `natives` leak: **one method**, `Frame.drawGlyphs` | sealing `blend2d` | low |
| [G15](#g15) | IME preedit: the composition string, inline | typing Japanese, Chinese or Korean into a sticky | medium |

**Not gaps** — available today, and brd must use them rather than grow its own:

- **Text on a canvas.** `Font.bundled(BundledFont.UI | UI_STRONG | CODE | EMOJI, size)`,
  `Paragraph.of(font, text)`, `Paragraph.paint(frame, x, top, maxWidth, argb)`, `Font.draw(frame, …)`.
  None of it touches `natives`. brd's stickies are wordless because brd has not done this yet, not
  because it cannot.
- **Editing text on a canvas.** `text.edit.Editor` over a `canvas`: a caret, a selection, word jumps,
  `Home`/`End` on the visual line, undo that folds a typing run, and the clipboard — none of it a
  `text-input`. [G15](#g15) is the one part still missing, and it is the IME's composition string.
- **Images on a canvas.** `Image.decode(bytes | file)`, `Frame.drawImage(...)` with a crop and an alpha,
  and `Image.encodePng()` — all of it in `io.github.digitalsmile.goldberry.image`, none of it touching
  `natives`, and nothing to close. There is no `img` *widget* yet; a `canvas` is how an image is drawn
  today, which is what brd's board is anyway.
- **Widget catalogue**: menus, dialogs, toasts, tree, tabs, table, cards, masonry, scroll, text inputs,
  charts. The assistant panel, the board list and the settings dialog are compositions of these.
- **Stylesheets and theming**: `Controls.stylesheets(Theme[, Density])`, the cascade, `Icons`.
- **Markup and binding**: `Widgets.inflater(...)` over KDL, `@Bind`/`@Action` with `Models`.
- **Shortcuts, overlays, popups, tray**: `Host.shortcut(...)`, `Host.overlay(...)`, `Popup`, tray SPI.
- **Clipboard (text)**, **cursors**, **headless backend**, **start-up timeline** (`Startup`), **frame
  stats**/HUD.

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

**IME preedit is not in it** — see [G15](#g15). Committed text works and always
did, so a Latin keyboard is complete and an input method's *result* arrives; what
is missing is the inline composition a CJK user sees while choosing.

<a id="g7"></a>
### G7 — Clipboard beyond text

**Today.** `render.Clipboard` is `hasText` / `text` / `text(String)`.

**Needed for.** Pasting a screenshot onto a board is the most-used way anything gets onto one (plan B3,
C5). Copying shapes between two brd windows needs a custom type as well.

**Proposed.** `boolean hasImage()`, `Image image()`, `boolean image(Image)`, and a typed pair —
`boolean has(String mime)` / `byte[] read(String mime)` / `boolean write(String mime, byte[] bytes)`.

**The type in those signatures exists now** — `image.Image`, from [G4](#g4), which
decodes what a platform hands over and encodes what it is given. What is left here
is the platform half: SDL3 has no clipboard image API of its own, so this is
`SDL_SetClipboardData` with a callback per MIME type, and it is the whole of the
remaining work.

---

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
### G9 — Native file dialogs

**Today.** No binding. SDL3 has `SDL_ShowOpenFileDialog` / `SDL_ShowSaveFileDialog`.

**Needed for.** Export a board as PNG or SVG and a note as Markdown (plan G3); import an image; open a
local `.am` snapshot while sync is still being built.

**Proposed.** `Dialogs.openFile(...)`, `Dialogs.saveFile(...)`, `Dialogs.openFolder(...)`, async with a
callback on the UI thread, with filters and a starting directory.

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
### G11 — The computed font, inside a painter

A painter is given a `Frame` and a size. It is not given the `ComputedStyle` of the box it is painting,
so canvas text has to name a font (`BundledFont.UI`) rather than inherit the one the cascade resolved.
Fine for brd today — a board's fonts are the *document's*, not the theme's — and wrong for a chart or a
custom control. `Painter.paint(Frame, LogicalSize, ComputedStyle)`, or a `Frame.style()`.

---

<a id="g12"></a>
### G12 — Deep links and single-instance handoff

**Today.** Nothing. Plan D5 needs `brd://open/<token>` to reach a running client, which is three
platform mechanisms (a registry key, `CFBundleURLTypes`, a `.desktop` entry) plus an IPC handoff so a
second launch focuses the first.

**Why it might be Goldberry's.** It is window-and-platform integration, which is the toolkit's half of
the world, and every desktop application that ships needs it. If Goldberry would rather not own it, brd
will — but that is a decision to take deliberately rather than by default, which is what this entry is
for. The OS keychain (plan `auth/`) is the same question and probably the same answer.

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
### G14 — The last `natives` leak: one method

**Mostly closed** by [ADR-0282](../book/src/adr/0282-a-shaped-run-is-a-value-and-the-last-leak-is-one-method.md).
`GlyphRun` and `TextDirection` are `text.ShapedRun` and `text.TextDirection` now,
and HarfBuzz's packages are sealed to `:core` alongside Yoga's.

**Today.** One method remains:

```java
public void Frame.drawGlyphs(double x, double baseline, BlendFont font, BlendGlyphBuffer glyphs, int argb);
```

`Font.draw` is its only caller and nothing outside `:core` touches it. The other
two leaks were **values**, and a value can be mirrored; these are **handles**, and
the difficulty is ownership rather than transcription — rasterizing a glyph needs
a context, a font and a staged buffer, `paint` owns the first and `text.font` the
other two, and within one module Java offers nothing between package-private and
public.

**Proposed.** Move the native font into `paint`: a pen there owns the `BlendFont`
and the buffer, `text.font` becomes shaping and metrics, and `Frame.drawGlyphs`
goes package-private. It is a change to the text stack's shape — font creation is
`FontFace`'s today, with the fallback chain and the paragraph cache built on it —
so it wants its own ADR rather than being improvised.

**Closing it** seals `blend2d` and lets `:core` drop `requires transitive` for
good. Nothing brd does is blocked on it.

---

<a id="g15"></a>
### G15 — IME preedit, inline

**Today.** `SDL_EVENT_TEXT_INPUT` is bound and delivers **committed** text, which
is what `Editor.onText` takes and what an input method produces when the user
accepts a candidate. The *composition* — the underlined string being assembled,
with its own cursor — is `SDL_EVENT_TEXT_EDITING`, and it is **not bound at all**;
neither is `SDL_SetTextInputArea`, which is how the platform is told where to put
the candidate window.

So today a Japanese, Chinese or Korean user typing into a sticky sees nothing at
all until they commit, and the candidate window opens wherever the compositor
guesses.

**Needed for.** Any sticky, label or note typed in a language that composes —
which is not a minority case for a board tool.

**Proposed.** The event and the area call bound in `:natives`; a `PreeditEvent`
routed to the focused node beside `TextEvent`; a preedit string and its cursor on
`Editor`, drawn under the caret and **not** in the text, because a composition is
not an edit until it is committed; and `SDL_SetTextInputArea` fed from
`TextGeometry.caretAt`, which is already the right rectangle.

**Why it is Goldberry's.** It is a platform binding and an input route — the
toolkit's half of the world twice over. Goldberry's own M5 already lists "IME
preedit" as hardening work.

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
