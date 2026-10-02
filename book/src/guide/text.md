# Text, fonts and icons

<p class="gb-lede">Two embedded typefaces, an optional emoji face, 1544 stroked icons, and an image that is a value: what an application needs from the text and picture stack, one sample each.</p>

By the end of this chapter you can open a font and measure a string, ship a
face of your own, name an icon from markup, draw emoji, put a caret on a
canvas, and decode, scale, copy and encode an image.

```java
try (var fonts = Fonts.bundled()) {
    Font body = fonts.of(BundledFont.UI, 13);
    double width = body.widthOf("Goldberry");          // what layout will ask
    body.draw(frame, 16, 16 + body.ascent(), "Goldberry", 0xFFECEFF4);
}
```

The `y` of a draw is the baseline. The top of a line is
`baseline - ascent()`.

## Faces, fonts and the book

A `FontFace` is a typeface parsed once. A `Font` is one size of it. `Fonts`
is the book that caches both, keyed by family, weight and style, then by
size ([ADR-0044](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0044-one-face-many-sizes.md)).

```java
try (var face = FontFace.bundled(BundledFont.UI);
        var title = Font.on(face, 24);
        var body = Font.on(face, 16)) {
    // two sizes, one parse of Inter
}
```

A second size over a shared face costs microseconds and no memory. The face
must outlive every font over it. `Font.bundled(BundledFont.UI, 16)` parses a
face of its own, which is right for exactly one size.

Shaping runs in the face's own design units and the size is applied once, in
the font matrix, so a shaped run is correct at every size
([ADR-0034](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0034-one-size-and-the-design-unit-crossing.md)). `Font`
exists to make applying it twice unrepresentable.

A running application does not open a book of its own. The launcher opens one
and hands it to the renderer, and a widget asks `Paints.Context.font(style)`
for the font its resolved typography names. A canvas that measures its own
labels asks `host.fonts()`.

### The bundled faces

| `BundledFont` | File | Family | Weight | Style |
|---|---|---|---|---|
| `UI` | Inter variable | Inter | 400 | upright |
| `UI_STRONG` | Inter SemiBold | Inter | 600 | upright |
| `UI_ITALIC` | Inter Italic | Inter | 400 | italic |
| `UI_STRONG_ITALIC` | Inter SemiBold Italic | Inter | 600 | italic |
| `CODE` | JetBrains Mono | JetBrains Mono | 400 | upright |
| `EMOJI` | Noto Color Emoji | Noto Color Emoji | 400 | upright |

A weight is a face and so is an italic: Inter's italic is drawn, not
sheared, and `font-weight: 600; font-style: italic` resolves to a file rather
than to the nearer of three
([ADR-0066](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0066-a-weight-is-a-face-and-color-inherits.md),
[ADR-0323](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0323-an-italic-is-a-face-and-the-matrix-closes.md)). A
CSS weight no file provides resolves to the nearer one that does. A glyph
neither family has draws `.notdef`: there is no fallback cascade beyond the
emoji slot, so `font-family: Inter, sans-serif` keeps the first name only.

### Shipping a face

```java
@Override public List<FontSource> fonts() {
    return List.of(FontSource.resource(
            "Forum", BundledFont.Weight.REGULAR, BundledFont.Style.UPRIGHT,
            MyApp.class, "fonts/Forum-Regular.ttf"
    ));
}
```

`Application.fonts()` is read once, before `start`, when the window's book
opens. A face named here reaches the cascade, paragraph layout, a field's
caret and the glyph cache together, and a stylesheet names it with
`font-family: Forum`. The bundled families are searched first, so a file
called `Inter` cannot replace the face the design system was drawn against. A
face whose bytes cannot be read is logged once and drawn in the UI face
([ADR-0349](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0349-a-face-an-application-ships-is-found-after-the-bundled-ones.md)).
`FontSource.of(family, weight, style, bytes)` takes bytes already in hand.

## Paragraphs

```java
var prose = Paragraph.of(font, "Prose that has to fit somewhere.");
TextLayout lines = prose.layout(240);                 // wrapped at 240 logical px
prose.paint(frame, 16, 16, 240, 0xFFECEFF4);
```

A `Paragraph` is shaped once. Every re-wrap after that is arithmetic over the
glyphs shaping produced, which is what makes it affordable to answer a layout
pass from inside it
([ADR-0036](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0036-the-paragraph-is-shaped-once-and-wrapped-many-times.md)).
A widget never calls `Paragraph.of` itself: `Paints.Context.paragraph(style,
text)` shapes through a cache and returns the same instance each frame for
the same text, which the retained render tree relies on.

Right-to-left text is shaped in logical order and therefore drawn mirrored.
`isBidiApproximate()` says when that happened. It is an approximation that
says so rather than a refusal
([ADR-0218](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0218-a-paragraph-approximates-bidi-rather-than-refusing-it.md)).
Style runs within one paragraph are not built.

## Text scale

Text scales from 90% to 150% without the boxes around it moving, which is
the condition every control is built to survive
([ADR-0267](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0267-a-text-scale-scales-the-text-and-not-the-layout.md)).

```java
renderer.textScale(1.5);     // clamped to WidgetRenderer.MINIMUM_TEXT_SCALE..MAXIMUM_TEXT_SCALE
```

The factor is applied where a resolved style becomes a `Font`, nowhere in
the cascade. It is a switch on `WidgetRenderer`, which the low-level path
builds itself; the launcher does not expose it yet.

## Icons

```kdl
row {
  button icon="plus" press="app.create" "New"
  button icon="palette" press="app.toggle-theme" "Switch"
}
```

```java
@Override public void start(Host host) {
    plus = Icon.bundled("plus", Icons.SLOT);              // 16, a button's lead slot
    icons = Icons.strict().bind("plus", plus).bind("palette", Icon.bundled("palette", Icons.SLOT));
}
```

Lucide's 1544 icons ship in `goldberry-core` as path data on a 24 by 24 grid.
They are strokes, not fills: 2 px round-capped outlines, so an `Icon` carries
a stroke width as well as a shape, and it is built pre-scaled to one size so
nothing is transformed at draw time
([ADR-0043](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0043-icons-are-stroked-paths.md)).
`BundledAssets.iconNames()` lists the set, and `Icon.of(name, pathData, size)`
builds one from your own SVG path data on the same grid. `Icon.bundled` throws
for a name the set does not have. `Icon.find(name, size)` returns an
`Optional<Icon>` instead, for a name that comes from outside the program.

Markup names an icon and never builds one. `icon="plus"` resolves against
the `Icons` registry the application owns, because a document reloaded on
every keystroke would otherwise rebuild one per reload. An `Icon` is an
immutable value whose `close()` does nothing since
[ADR-0277](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0277-a-path-is-a-value-and-the-rasterizers-is-package-private.md),
and the showcase still opens its icons in `start` and closes them in `stop`.

An icon is tinted by `color`, like text. Inside a widget that takes `icon=` it is
drawn at the size it was built at. The standalone [`icon`](../components/drawing.md#icon)
node is the other way round: the stylesheet sizes its box and the outline is
scaled to fit, so `icon "plus"` at 11 px and at 24 px is one line of CSS each
([ADR-0532](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0532-an-icon-is-the-size-of-its-box.md)).
It looks the name up in the registry first and then in the bundled set, so it
needs no registration. An icon-only button has an empty label and needs
`name=` for its accessible name.

## Emoji

```groovy
implementation 'dev.goldberry:goldberry-emoji:<version>'
```

Noto Color Emoji ships as its own artifact, because five megabytes of
paint graphs should not be inherited by an application that never draws one
([ADR-0384](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0384-the-emoji-face-is-an-artifact-an-application-opts-into.md)).
With the module on the path, nothing else changes: the itemizer splits an
emoji sequence out of a line and shapes it in the emoji face, and the rest of
the line stays in the family the cascade chose
([ADR-0393](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0393-an-emoji-is-routed-by-the-text-and-drawn-in-layers.md)).
It is the COLRv1 build, drawn from its paint graphs, so an emoji is as sharp
at 400% as at 100%
([ADR-0456](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0456-the-emoji-face-is-noto-drawn-from-its-paint-graphs.md)).
A stylesheet reaches the face by name as `font-family: "Noto Color Emoji"`.

Without the module, `Font.bundled(BundledFont.EMOJI, size)` throws a
`MissingEmojiFontException` whose message names the artifact, and
`BundledAssets.hasEmojiFont()` answers false.

## Selection and editing

`text-input` and `text-area` are controls. Underneath them is an `Editor`
that is none of those things, and it is reachable on its own: a caret in a
sticky on a board, a label on a shape, anything drawn on a `canvas`
([ADR-0285](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0285-a-caret-is-the-text-stacks-and-not-a-controls.md)).

```java
var editor = new Editor(font).multiline(true).wrapWidth(240).clipboard(host.clipboard());

new Canvas((frame, size) -> editor.paint(frame, 8, 8, Editor.Ink.of(ink, selection), focused),
        new Input() {
            @Override public boolean wantsText()        { return true; }   // turns the keyboard on
            @Override public void onKey(KeyEvent e)     { if (editor.onKey(e)) e.consume(); }
            @Override public void onText(TextEvent e)   { if (editor.onText(e.text())) e.consume(); }
            @Override public void onPointer(PointerEvent e) {
                if (e.kind() == PointerEvent.Kind.PRESSED) {
                    editor.pointerAt(e.content().x() - 8, e.content().y() - 8, false, e.clickCount());
                }
            }
        });
```

`wantsText()` is not optional. The platform produces no characters until
something says it is being typed into, so an editor that has not said so gets
arrow keys and nothing else.

The editor is `text-input`'s key map, key for key: arrows and `Ctrl`+arrows,
`Home` and `End` on the visual line, `Shift` to extend, undo and redo with a
typing run folded into one step, and cut, copy and paste. A key it does not
handle is not consumed, so `Tab` still moves focus. `TextGeometry` beside it
answers where a caret is on a wrapped paragraph, what a click landed on, what
`Up` means when lines differ in length, and what shape a selection is across
a break. An input method's composition arrives through `onPreedit` and is
drawn beside the document, not in it.

A `text-area` is also an editor. `gutter=#true` numbers the hard lines at
the positions the wrap put them
([ADR-0331](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0331-a-gutter-numbers-hard-lines-at-soft-positions.md)),
and `.onEdit(...)` and `.edit(...)` hand a shortcut the caret as well as the
text ([ADR-0332](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0332-an-editor-is-handed-the-caret.md)).

## Images

```java
Image logo = Image.decode(Files.readAllBytes(file));    // or Image.decode(file)

frame.drawImage(logo, 16, 16);                          // natural size
frame.drawImage(logo, 16, 96, 240, 120, 0.4);           // scaled, and faded
byte[] png = logo.scaled(64, 64).encodePng();
```

Encoded bytes become an `Image`: PNG, JPEG and QOI through the rasterizer's
codecs, WebP through libwebp, and GIF through a decoder written in Java
([ADR-0283](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0283-an-image-is-a-value-and-the-decoder-is-the-one-thing-blend2d-allocates.md),
[ADR-0329](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0329-two-more-codecs-one-fetched-and-one-written.md)). The
format comes from the bytes, never from a file name: `ImageFormat.of(bytes)`
says which. `Image.decodeAnimation(bytes)` returns every frame of an animated
GIF or WebP with its delay; `decode` returns the first.

An image has nothing to close. The decoder copies its pixels into a
Java-owned buffer before `decode` returns, so an image is a value to keep in
a field, a record or a cache. Natural size is one image pixel per device
pixel, so a 96 by 64 image is 96 logical points wide at 100% and 48 at 200%.
`encodePng()` writes one back out in `java.base`, and `encodeWebp(quality)`
through libwebp.

In markup an `image` node takes `src=`, `srcset=`, `alt=`, `decorative=` and
`fit=`, and is in [Canvas, images and QR codes](../components/drawing.md).

### The clipboard

```java
Image.fromClipboard(host.clipboard()).ifPresent(board::add);    // Ctrl+V
picture.toClipboard(host.clipboard());                           // Ctrl+C
```

Underneath is `Clipboard`'s other half, `has`, `read` and `write` over a
MIME type. One write offers several types in order, so pasting into your
own application keeps a document's format and pasting into a chat gets a
picture. A write is an offer: the platform asks for the bytes when somebody
pastes ([ADR-0286](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0286-a-clipboard-write-is-an-offer.md)).

### A picture with no window

```java
byte[] png = Offscreen.of(1200, 900)                     // physical pixels
        .scale(1.5f)
        .stylesheets(Controls.stylesheets(Theme.NORD_DARK))
        .render(new DocumentPreview(document))           // or .paint(painter)
        .encodePng();
```

`Offscreen` runs the window's own sequence with no display, no SDL and no
compositor: mount, lay out, tell each widget what size it came out as,
advance a virtual clock past the arrival transitions, and paint what settled.
Two renders of one document are the same bytes
([ADR-0284](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0284-a-picture-with-no-window-under-it.md)). It is what
[Testing an application](testing.md) builds on.

## Read more

- [ADR-0034](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0034-one-size-and-the-design-unit-crossing.md): one size, applied once
- [ADR-0036](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0036-the-paragraph-is-shaped-once-and-wrapped-many-times.md): shaped once, wrapped many times
- [ADR-0043](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0043-icons-are-stroked-paths.md): icons are strokes
- [ADR-0044](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0044-one-face-many-sizes.md): one face, many sizes
- [ADR-0285](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0285-a-caret-is-the-text-stacks-and-not-a-controls.md): the editor
- [ADR-0349](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0349-a-face-an-application-ships-is-found-after-the-bundled-ones.md): shipping a face
- [Text and links](../components/text.md): the `text` and `link` widgets
