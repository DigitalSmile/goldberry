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
size.

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
the font matrix, so a shaped run is correct at every size. `Font` exists to
make applying it twice unrepresentable.

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
than to the nearer of three.
A weight is a CSS number, though, and a stylesheet may write any of them: the
face is chosen by CSS's own nearest-weight rule over the faces the family has,
so over Inter's two `font-weight: 500` is the 400 and `bold` (700) is the 600,
as a browser draws them.
There is no variable `wght` axis: Inter's 500 is not instanced from its
variable file, and an application that wants a 500 ships one. A glyph
neither family has draws `.notdef`: there is no fallback cascade beyond the
emoji slot, so `font-family: Inter, sans-serif` keeps the first name only.

### Shipping a face

```java
@Override public List<FontSource> fonts() {
    return List.of(
            forum(400, "fonts/Forum-Regular.ttf"),
            forum(700, "fonts/Forum-Bold.ttf")
    );
}

private static FontSource forum(int weight, String file) {
    return FontSource.stream("Forum", weight, BundledFont.Style.UPRIGHT, () -> MyApp.class.getResourceAsStream(file));
}
```

`Application.fonts()` is read once, before `start`, when the window's book
opens. A face named here reaches the cascade, paragraph layout, a field's
caret and the glyph cache together, and a stylesheet names it with
`font-family: Forum`. The bundled families are searched first, so a file
called `Inter` cannot replace the face the design system was drawn against.

**The weight is any CSS number**, 1 to 1000. A family shipped at 500, 600, 700
and 800 gives each of them to the stylesheet that asks, and a weight it does
not have is the nearest one it does, by the rule Inter answers by. The two
named weights, `Weight.REGULAR` and `Weight.SEMI_BOLD`, are 400 and 600.

**Read the file with your own code.** The supplier in `FontSource.stream` is
the application's lambda, so it reads a resource with the application's own
access, and a modular application opens nothing for it.
`FontSource.resource(family, weight, style, MyApp.class, name)` reads it with
the toolkit's module instead, and on the module path that needs the package
holding the file opened to `dev.goldberry.core` (`opens com.example.app.fonts
to dev.goldberry.core;` for `fonts/` beside `com.example.app.MyApp`). A
resource directory is a package of its own, so it is that package, not the
class's, that has to be opened.

**A missing file is said at start.** The book looks for every file as it
opens, before the window shows: a resource or a stream is opened and closed
unread. One that is not there is a warning naming the face and why, a
package nobody opened told apart from a file that is missing, and text in it
is drawn in the UI face. Nothing is parsed until something is drawn in the
face. `fonts.unreadable()` lists them, for an application that would rather
refuse to start.
`FontSource.of(family, weight, style, bytes)` takes bytes already in hand.

## Paragraphs

```java
var prose = Paragraph.of(font, "Prose that has to fit somewhere.");
TextLayout lines = prose.layout(240);                 // wrapped at 240 logical px
prose.paint(frame, 16, 16, 240, 0xFFECEFF4);
```

A `Paragraph` is shaped once. Every re-wrap after that is arithmetic over the
glyphs shaping produced, which is what makes it affordable to answer a layout
pass from inside it.
A widget never calls `Paragraph.of` itself: `Paints.Context.paragraph(style,
text)` shapes through a cache and returns the same instance each frame for
the same text, which the retained render tree relies on.

Right-to-left text is shaped in logical order and therefore drawn mirrored.
`isBidiApproximate()` says when that happened. It is an approximation that
says so rather than a refusal.
Style runs within one paragraph are not built.

## Text scale

Text scales from 90% to 150% without the boxes around it moving, which is
the condition every control is built to survive.

```java
renderer.textScale(1.5);     // clamped to WidgetRenderer.MINIMUM_TEXT_SCALE..MAXIMUM_TEXT_SCALE
```

The factor is applied where a resolved style becomes a `Font`, nowhere in
the cascade. It is a switch on `WidgetRenderer`, which the low-level path
builds itself. The launcher does not expose it.

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
nothing is transformed at draw time.
`BundledAssets.iconNames()` lists the set, and `Icon.of(name, pathData, size)`
builds one from your own SVG path data on the same grid. `Icon.bundled` throws
for a name the set does not have. `Icon.find(name, size)` returns an
`Optional<Icon>` instead, for a name that comes from outside the program.

Markup names an icon and never builds one. `icon="plus"` resolves against
the `Icons` registry the application owns, because a document reloaded on
every keystroke would otherwise rebuild one per reload. An `Icon` is an
immutable value, and its `close()` does nothing.

An icon is tinted by `color`, like text. Inside a widget that takes `icon=` it is
drawn at the size it was built at. The standalone [`icon`](../components/drawing.md#icon)
node is the other way round: the stylesheet sizes its box and the outline is
scaled to fit, so `icon "plus"` at 11 px and at 24 px is one line of CSS each.
It looks the name up in the registry first and then in the bundled set, so it
needs no registration. An icon-only button has an empty label and needs
`name=` for its accessible name.

## Emoji

```groovy
implementation 'dev.goldberry:goldberry-emoji:<version>'
```

Noto Color Emoji ships as its own artifact, because five megabytes of
paint graphs should not be inherited by an application that never draws one.
With the module on the path, nothing else changes: the itemizer splits an
emoji sequence out of a line and shapes it in the emoji face, and the rest of
the line stays in the family the cascade chose.
It is the COLRv1 build, drawn from its paint graphs, so an emoji is as sharp
at 400% as at 100%.
A stylesheet reaches the face by name as `font-family: "Noto Color Emoji"`.

Without the module, `Font.bundled(BundledFont.EMOJI, size)` throws a
`MissingEmojiFontException` whose message names the artifact, and
`BundledAssets.hasEmojiFont()` answers false.

## Selection and editing

`text-input` and `text-area` are controls. Underneath them is an `Editor`
that is none of those things, and it is reachable on its own: a caret in a
sticky on a board, a label on a shape, anything drawn on a `canvas`.

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
the positions the wrap put them, and `.onEdit(...)` and `.edit(...)` hand a
shortcut the caret as well as the text.

## Images

```java
Image logo = Image.decode(Files.readAllBytes(file));    // or Image.decode(file)

frame.drawImage(logo, 16, 16);                          // natural size
frame.drawImage(logo, 16, 96, 240, 120, 0.4);           // scaled, and faded
byte[] png = logo.scaled(64, 64).encodePng();
```

Encoded bytes become an `Image`: PNG, JPEG and QOI through the rasterizer's
codecs, WebP through libwebp, and GIF through a decoder written in Java. The
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
pastes.

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
Two renders of one document are the same bytes. It is what
[Testing an application](testing.md) builds on.

## Read more

- [Text and links](../components/text.md): the `text` and `link` widgets
