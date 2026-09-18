package io.github.digitalsmile.goldberry.example.ui;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.css.value.CssColor;
import io.github.digitalsmile.goldberry.example.Showcase;
import io.github.digitalsmile.goldberry.image.Image;
import io.github.digitalsmile.goldberry.image.ImageFormat;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.event.TextEvent;
import io.github.digitalsmile.goldberry.offscreen.Offscreen;
import io.github.digitalsmile.goldberry.paint.CanvasStyle;
import io.github.digitalsmile.goldberry.paint.Dash;
import io.github.digitalsmile.goldberry.paint.Frame;
import io.github.digitalsmile.goldberry.paint.Gradient;
import io.github.digitalsmile.goldberry.paint.Path;
import io.github.digitalsmile.goldberry.paint.Stroke;
import io.github.digitalsmile.goldberry.qr.Level;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.render.model.PhysicalRect;
import io.github.digitalsmile.goldberry.text.Paragraph;
import io.github.digitalsmile.goldberry.text.edit.Editor;
import io.github.digitalsmile.goldberry.text.font.Font;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.core.Row;
import io.github.digitalsmile.goldberry.widgets.core.canvas.Canvas;
import io.github.digitalsmile.goldberry.widgets.core.canvas.Input;
import io.github.digitalsmile.goldberry.widgets.core.image.Fit;
import io.github.digitalsmile.goldberry.widgets.core.image.ImageSource;
import io.github.digitalsmile.goldberry.widgets.core.image.ImageView;
import io.github.digitalsmile.goldberry.widgets.core.qrcode.QrCode;
import io.github.digitalsmile.goldberry.widgets.panel.card.Card;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// The **Canvas** screen: the escape hatch, and what it is now able to do.
///
/// ## Why this screen exists
///
/// The charts are built on the *painter* — `Paints`, `Painter` and `Box.painting`
/// — and not on the `canvas` widget, so a wall of them demonstrates the drawing
/// primitive and says nothing about `canvas` itself. What `canvas` adds is the
/// thing an application reaches for when the catalogue has no widget for what it
/// wants: a surface it draws on *and* one it hears (ADR-0281).
///
/// Four cards, each pinning one claim:
///
/// 1. **The paint values are the toolkit's.** Nothing on this screen names a
///    `:natives` type, which was not true of any drawing in this repository
///    before ADR-0277 — the five widgets that drew curves all imported
///    `BlendPath`.
/// 2. **A dash is Goldberry's own arithmetic.** Blend2D stores a dash array and
///    never strokes with it, so the dashed ring here is a solid stroke of a path
///    that was cut up first (ADR-0278). The dotted rings are a **short** dash
///    rather than SVG's zero-length one, because this screen is where we found
///    out that Blend2D will not ink a zero-length sub-path.
/// 3. **Input lands where the ink is.** The pointer card has padding, and the
///    crosshair follows the pointer exactly — which it would not if it read
///    `local()` instead of `content()`.
/// 4. **An image is a value.** The image card draws one decoded PNG four ways
///    from a single static field: nothing is closed, nothing is borrowed, and
///    the same image is drawn twice in the same frame (ADR-0283). Its natural
///    size is one image pixel per *device* pixel, so it is crisp at 200% rather
///    than twice as big.
/// 5. **The format comes from the bytes.** The codecs card draws the same 96×64
///    picture decoded from PNG, QOI, WebP, GIF and JPEG, labelled with what
///    `ImageFormat` read out of each file's first twelve bytes — never its name.
///    Two of the five did not decode at all before ADR-0329, and which decoder
///    each goes to is invisible from here, which is the point.
///
/// ## The data is a constant, and the pointer starts nowhere
///
/// Like [Charts], this screen is also a golden image. Every coordinate below is
/// written down, and the two interactive cards draw **nothing extra** until a
/// pointer touches them — so the picture at rest is the same one every time,
/// on every machine.
public record CanvasScreen() implements Widget.Stateful {

    private static final String NOTE =
            "§1's `canvas` — the one widget an application writes its own drawing into. Everything"
                    + " here is `paint.Path`, `Stroke`, `Gradient` and `image.Image` — five formats"
                    + " of it: no drawing on"
                    + " this screen names a type from the native layer, and none of it could have"
                    + " been written that way before. Move the pointer over the lower two cards.";

    /// The chart palette's first three slots, so this screen sits beside the
    /// others rather than inventing colours of its own.
    private static final int INK = 0xFF88C0D0;

    private static final int ACCENT = 0xFFA3BE8C;

    private static final int WARN = 0xFFEBCB8B;

    private static final int MUTED = 0xFF4C566A;

    /// The sticky's paper — a Nord `nord13`-ish yellow, which is what a sticky is.
    private static final int PAPER = 0xFFEBCB8B;

    private static final float STICKY_WIDTH = 260;

    private static final float STICKY_HEIGHT = 150;

    private static final float STICKY_PADDING = 10;

    @Override
    public State<?> createState() {
        return new CanvasState();
    }

    /// The one image on this screen, decoded once.
    ///
    /// A holder class rather than a field on the state, so that it is decoded the
    /// first time something paints it and not when a screen is built: the *shape*
    /// tests inflate every document without a rasterizer under them, and a decode
    /// in a constructor would need one.
    ///
    /// A missing resource is a build that did not package what its source names,
    /// so it fails here rather than drawing a placeholder — the placeholder is how
    /// a broken build reaches a release.
    private static final class Sample {

        private static final Image IMAGE = decode();

        private Sample() {}

        private static Image decode() {
            try (var bytes = CanvasScreen.class.getResourceAsStream("canvas-sample.png")) {
                if (bytes == null) {
                    throw new IllegalStateException("canvas-sample.png is not on the classpath beside CanvasScreen");
                }
                return Image.decode(bytes.readAllBytes());
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read canvas-sample.png", e);
            }
        }
    }

    /// The same 96x64 picture, written five ways — `docs/gaps.md` G35a,
    /// [ADR-0329].
    ///
    /// One entry per format [Image#decode(byte[])] reads. The point of the card
    /// they feed is that **the format comes from the bytes**: the name under each
    /// tile is [ImageFormat#of] reading the file's first twelve bytes, never its
    /// extension, and the picture above it is what the codec that name chose
    /// produced.
    ///
    /// Two of the five did not decode at all before ADR-0329, and they are
    /// answered differently: WebP is VP8 and goes to libwebp, GIF is nine pages
    /// and goes to a decoder this toolkit wrote. Neither is visible from here,
    /// which is the whole idea.
    ///
    /// A holder class for [Sample]'s reason — decoded on first paint rather than
    /// when a screen is built, so the shape tests can inflate this document with
    /// no rasterizer under them.
    private static final class Codecs {

        /// In the order they are drawn: the lossless three first, then the two
        /// that lose something, so the differences below read left to right.
        private static final List<Coded> ALL = load();

        private Codecs() {}

        private static List<Coded> load() {
            var files = List.of(
                    "canvas-sample.png",
                    "canvas-sample.qoi",
                    "canvas-sample.webp",
                    "canvas-sample.gif",
                    "canvas-sample.jpg");
            var out = new ArrayList<Coded>(files.size());
            for (var file : files) {
                var bytes = read(file);
                // The **bytes** decide, which is what this card exists to show:
                // `canvas-sample.jpg` would decode identically if it were called
                // `canvas-sample.txt`, and a PNG with a `.gif` name still decodes.
                var format = ImageFormat.of(java.nio.ByteBuffer.wrap(bytes));
                out.add(new Coded(format, bytes.length, Image.decode(bytes)));
            }
            return List.copyOf(out);
        }

        private static byte[] read(String file) {
            try (var bytes = CanvasScreen.class.getResourceAsStream(file)) {
                if (bytes == null) {
                    throw new IllegalStateException(file + " is not on the classpath beside CanvasScreen");
                }
                return bytes.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read " + file, e);
            }
        }
    }

    /// One tile of the codecs card: what the bytes turned out to be, how many of
    /// them there were, and the picture they decoded to.
    ///
    /// @param format what [ImageFormat#of] made of the first twelve bytes
    /// @param bytes  the file's size, which is the only thing that differs
    ///               visibly between the three lossless entries
    /// @param image  the decoded picture — a value, so it is a field here with
    ///               nothing to close (ADR-0283)
    private record Coded(ImageFormat format, int bytes, Image image) {}

    /// A widget tree rendered with no window, decoded back from the PNG it was
    /// encoded to.
    ///
    /// **The whole of `Offscreen` in five lines** (ADR-0284): stylesheets, a
    /// tree, a size, a picture. What comes back is an ordinary
    /// [Image][io.github.digitalsmile.goldberry.image.Image], so the canvas draws
    /// it with the same call the card above uses for a file on disk.
    ///
    /// Through `encodePng` and back on purpose. Nothing here needs the bytes —
    /// the render already produced the image — and going round them is the point:
    /// what a server would send is what this card is drawing.
    ///
    /// At **2&times;**, so the picture stays sharp wherever it is drawn: on a 1&times;
    /// display the rectangle below is a supersample of it, and on a 2&times; one it
    /// is pixel for pixel.
    private static final class Rendered {

        private static final Image IMAGE = render();

        private Rendered() {}

        private static Image render() {
            var sheets = new ArrayList<Stylesheet>(Controls.stylesheets(Theme.NORD_DARK));
            sheets.add(Stylesheet.resource(CascadeLayer.APPLICATION, Showcase.class, "showcase.css"));

            var tree = new Card(
                    List.of(
                            new Text("Rendered with no window", Attributes.NONE.classes("card-title")),
                            new Text(
                                    "A widget tree, a buffer and a PNG. No display, no SDL," + " no compositor.",
                                    Attributes.NONE.classes("caption")),
                            new Button("Begin again")),
                    Attributes.NONE.classes("wall-card"));

            return Image.decode(Offscreen.of(560, 240)
                    .scale(2f)
                    .stylesheets(sheets)
                    .render(tree)
                    .encodePng());
        }
    }

    static final class CanvasState extends State<CanvasScreen> {

        /// Where the pointer last was, in the pointer card's own coordinates, or
        /// null when it has never been over it.
        ///
        /// Null rather than a sentinel: "nowhere" is a real state — it is the one
        /// the golden image is taken in — and a canvas that drew a crosshair at
        /// (0, 0) until touched would be drawing a lie.
        private float[] at;

        /// The sticky's text, its caret and its undo stack.
        ///
        /// **A field on the state, and it has to be**: an editor is where the
        /// caret is, and a widget is a value that is rebuilt every frame. Built on
        /// first use rather than in the constructor, because a `Font` needs the
        /// rasterizer and the tests that only read this screen's *shape* have
        /// none (ADR-0285).
        private Font stickyFont;

        private Editor sticky;

        /// The window, for its clipboard. Null in a test that renders the screen
        /// without one, which is why every use of it is guarded.
        private Host host;

        private Editor sticky() {
            var current = sticky;
            if (current == null) {
                stickyFont = Font.bundled(BundledFont.UI, 13);
                current = new Editor(stickyFont)
                        .multiline(true)
                        .wrapWidth(STICKY_WIDTH - 2 * STICKY_PADDING)
                        .text("Type here. The caret, the selection, Ctrl+Z and the word"
                                + " jumps are the toolkit's — this card holds an Editor"
                                + " and draws what it says.");
                if (host != null) {
                    current.clipboard(host.clipboard());
                }
                sticky = current;
            }
            return current;
        }

        /// The font is this state's, so it is this state's to close. A widget
        /// cannot own something with a `close()`; a state can, and this is the
        /// hook for it.
        @Override
        protected void dispose() {
            if (stickyFont != null) {
                stickyFont.close();
                stickyFont = null;
                sticky = null;
            }
        }

        /// What the last clipboard key did, drawn on the card.
        ///
        /// **A demo whose success looks the same as its failure is a bad demo.**
        /// Ctrl+C changes nothing visible and Ctrl+V with an empty clipboard
        /// changes nothing either, so without this the card cannot be told from a
        /// broken one — which is how it was reported (ADR-0286).
        private String note = "";

        /// An image pasted onto the image card, and where it was dropped.
        ///
        /// **Under the pointer**, which is what pasting onto a board means: the
        /// picture goes where you are looking rather than replacing the card.
        /// Null at rest, so the golden is the same picture on every machine
        /// whatever happens to be on the clipboard.
        private Image pasted;

        private float pastedX;

        private float pastedY;

        /// Where the pointer last was over the image card, for a paste to land
        /// at. NaN until it has been over it — a paste before then goes to the
        /// corner rather than to a coordinate nobody chose.
        private float pointerX = Float.NaN;

        private float pointerY = Float.NaN;

        /// Whether the sticky has the keyboard, which is the only reason to draw
        /// a caret at all. Starts false, so the golden image is the same picture
        /// on every machine.
        private boolean editing;

        /// How far the plan has been dragged, and how far it has been zoomed.
        private float panX;

        private float panY;

        private float zoom = 1;

        private float dragX;

        private float dragY;

        /// The sample the image card loads through the widget rather than a painter.
        private static final ImageSource SAMPLE = ImageSource.resource(CanvasScreen.class, "canvas-sample.jpg");

        /// What the three codes on the card carry.
        ///
        /// A real link rather than a `tg://login?token=…`, because a sign-in
        /// token is a credential and a showcase screenshot is published: the
        /// gap this widget closes is Telegram's, and the demonstration is the
        /// other half of it — "open this on your phone".
        private static final String SHARE_LINK = "https://goldberry.example/open/showcase";

        private static Attributes id(String id, String... classes) {
            return new Attributes(id, Set.of(classes), id);
        }

        private static Widget caption(String text) {
            return new Text(text, Attributes.NONE.classes("caption"));
        }

        private static Widget captioned(String title, Attributes attributes, Widget... parts) {
            var children = new ArrayList<Widget>(parts.length + 1);
            children.add(new Text(title, Attributes.NONE.classes("card-title")));
            children.addAll(List.of(parts));
            return new Card(List.copyOf(children), attributes.classes("wall-card"));
        }

        // --- the three drawings ----------------------------------------------

        /// Every kind of segment a [Path] has, and every way a [Stroke] draws one.
        ///
        /// Static, and deliberately so: this is the card that has to look the same
        /// in a golden image as it does on screen.
        private static void paintPaths(Frame frame, LogicalSize size) {
            var width = size.width();
            var height = size.height();

            // A filled shape with a gradient through it -- `Gradient.fade`, which
            // repeats the RGB at the transparent end so the ramp thins out rather
            // than going through grey (ADR-0207).
            var hill = Path.builder()
                    .moveTo(0, height)
                    .lineTo(0, height * 0.55f)
                    .cubicTo(width * 0.25f, height * 0.2f, width * 0.45f, height * 0.9f, width * 0.6f, height * 0.5f)
                    .cubicTo(width * 0.75f, height * 0.2f, width * 0.85f, height * 0.35f, width, height * 0.3f)
                    .lineTo(width, height)
                    .close()
                    .build();
            frame.fillPath(hill, Gradient.fade(0, height * 0.2f, 0, height, CssColor.fade(INK, 0.55)));

            // The same curve as a stroke, round-capped and round-joined -- the pen
            // every line chart in the catalogue draws with.
            var ridge = Path.builder()
                    .moveTo(0, height * 0.55f)
                    .cubicTo(width * 0.25f, height * 0.2f, width * 0.45f, height * 0.9f, width * 0.6f, height * 0.5f)
                    .cubicTo(width * 0.75f, height * 0.2f, width * 0.85f, height * 0.35f, width, height * 0.3f)
                    .build();
            frame.strokePath(ridge, Stroke.round(2), INK);

            // A dashed baseline. Blend2D would have drawn this solid: it keeps a
            // dash array and its stroker never reads one, so the dashes are cut
            // into the path before it is stroked (ADR-0278).
            frame.strokePath(
                    Path.line(0, height * 0.82f, width, height * 0.82f),
                    Stroke.of(1).dashed(4, 4),
                    MUTED);

            // A ring, and a dotted ring around it. The dots are a **short** dash
            // and not a zero-length one: `Dasher` emits the zero-length sub-path
            // SVG asks for, and Blend2D declines to ink it -- which this screen is
            // how we found out (ADR-0278).
            frame.strokePath(Path.circle(width * 0.16f, height * 0.28f, 14), Stroke.round(2), ACCENT);
            frame.strokePath(
                    Path.circle(width * 0.16f, height * 0.28f, 22),
                    Stroke.round(2).dash(Dash.of(0.5, 5)),
                    ACCENT);

            // A rounded rectangle and an arc, which were `RoundRect` and `Arc` and
            // are `Path` factories now.
            frame.strokePath(Path.roundRect(width - 76, 14, 60, 30, 8), Stroke.of(1.5), WARN);
            frame.strokePath(
                    Path.arc(width - 46, height * 0.55f, 18, -Math.PI / 2, Math.PI * 1.4), Stroke.round(3), WARN);
        }

        /// The same image four times: as it is, stretched, cropped and faded.
        ///
        /// Static like [#paintPaths], and for the same reason — this card is a
        /// golden image, so every number in it is written down.
        ///
        /// The four draws are the four things [Frame#drawImage] can be asked for,
        /// and they are drawn from **one** decoded image that is decoded once for
        /// the life of the application. That is the practical difference an
        /// `Image` being a value rather than a handle makes: it is a static field
        /// here, with no lifetime travelling alongside it and nothing to close
        /// (ADR-0283).
        private void paintImages(Frame frame, LogicalSize size, CanvasStyle style) {
            var image = Sample.IMAGE;
            var width = size.width();

            // 1. Natural size: one image pixel per *device* pixel, so this is 96
            // logical points wide at 100% and 48 at 200% -- and crisp on both.
            frame.drawImage(image, 8, 8);

            // 2. Into a rectangle of the card's choosing, which scales and does
            // not preserve the aspect ratio. Nothing in the toolkit guesses at
            // `contain` or `cover`: a caller that wants one is doing layout and
            // knows both sizes.
            frame.drawImage(image, 116, 8, 128, 64);

            // 3. A crop -- the source rectangle is in the image's own pixels, and
            // this one is the left disc. Drawn into a square two and a half times
            // its size, because a crop and a scale are separate decisions.
            frame.drawImage(image, PhysicalRect.of(13, 11, 26, 26), 256, 8, 64, 64, 1);
            frame.strokePath(Path.roundRect(256, 8, 64, 64, 4), Stroke.of(1), MUTED);

            // What was pasted, where it was dropped, scaled down to fit the card
            // if it is a screenshot rather than an icon. Drawn **over** the demo
            // rather than instead of it, because that is what a paste onto a board
            // is (ADR-0286).
            if (pasted != null) {
                var fit = Math.min(1, Math.min(220f / pasted.width(), 110f / pasted.height()));
                var wide = pasted.width() * fit;
                var tall = pasted.height() * fit;
                frame.drawImage(pasted, pastedX, pastedY, wide, tall);
                frame.strokePath(Path.roundRect(pastedX, pastedY, wide, tall, 3), Stroke.of(1), ACCENT);
            }

            // What the last clipboard key did. Empty until one is pressed, so the
            // golden image is still of a card nobody has touched.
            if (!note.isEmpty()) {
                // `style.font()` rather than `Font.bundled(BundledFont.UI, 12)`,
                // which is what this line used to be and what G11 was about: the
                // card's own `font-family` and `font-size` reach the drawing, so
                // a theme switch moves this text with the rest of the screen —
                // and the face is the renderer's book rather than a fresh parse
                // of the file every frame (ADR-0288).
                Paragraph.of(style.font(), note).paint(frame, 8, size.height() - 22, width - 16, ACCENT);
            }

            // 4. And faded, stretched across whatever width the card turned out to
            // have. The fade is the frame's, applied once to the blit and put back
            // afterwards -- the ring below is drawn at full strength.
            var strip = Math.max(96, width - 16);
            frame.drawImage(image, 8, 96, strip, 56, 0.35);
            frame.strokePath(
                    Path.circle(8 + strip * 0.5f, 124, 18), Stroke.round(1.5).dash(Dash.of(0.5, 6)), ACCENT);
        }

        /// The same picture, decoded from five different formats.
        ///
        /// `docs/gaps.md` G35a's card, and it is deliberately boring to look at:
        /// five tiles that are meant to be **identical**, with the name the
        /// toolkit read out of each file's first twelve bytes under them.
        /// PNG, QOI and WebP are lossless and are pixel-for-pixel the same
        /// picture; GIF quantized 958 colours to 255 and JPEG is JPEG, so those
        /// two are near enough that a reader has to look, which is the honest
        /// thing for a card about codecs to show.
        ///
        /// Static like the two cards above it — this is a golden image, so every
        /// number in it is written down.
        private static void paintCodecs(Frame frame, LogicalSize size, CanvasStyle style) {
            var all = Codecs.ALL;
            var gap = 8f;
            // As wide as the card turned out to be, shared out. The aspect ratio
            // is the picture's own 96x64, so the tiles stay square-ish at any
            // column width rather than stretching.
            var tile = Math.max(40, (size.width() - 16 - gap * (all.size() - 1)) / all.size());
            var tall = tile * 64 / 96;
            var top = 26f;

            Paragraph.of(style.font(), "One picture, five codecs — and the name comes from the bytes")
                    .paint(frame, 8, 6, size.width() - 16, MUTED);

            for (var i = 0; i < all.size(); i++) {
                var coded = all.get(i);
                var x = 8 + i * (tile + gap);
                frame.drawImage(coded.image(), x, top, tile, tall);
                frame.strokePath(Path.roundRect(x, top, tile, tall, 3), Stroke.of(1), MUTED);

                // What `ImageFormat.of` made of the file, not what it was called.
                Paragraph.of(style.font(), coded.format().name()).paint(frame, x, top + tall + 6, tile, INK);
                Paragraph.of(style.font(), coded.bytes() / 1000 + "." + coded.bytes() / 100 % 10 + " kB")
                        .paint(frame, x, top + tall + 22, tile, MUTED);
            }

            // The two that are not exact, said out loud rather than left for a
            // reader to wonder about.
            Paragraph.of(style.font(), "GIF quantizes to 255 colours and JPEG is lossy; the other three are exact")
                    .paint(frame, 8, top + tall + 46, size.width() - 16, MUTED);
        }

        /// Why a paste found nothing, in the words of what is actually there.
        ///
        /// "There is no image" and "there is an image in a format this toolkit
        /// cannot read" are the same answer from `has(mime)` and completely
        /// different answers to somebody who has just pressed Ctrl+V, so the card
        /// says what the clipboard is offering (ADR-0286).
        private static String describe(io.github.digitalsmile.goldberry.render.Clipboard board) {
            var types = board.types();
            if (types.isEmpty()) {
                return board.hasText() ? "The clipboard holds text, not an image." : "The clipboard is empty.";
            }
            return "Nothing here can decode: " + String.join(", ", types);
        }

        /// The picture [Rendered] took, drawn twice.
        ///
        /// Static, like the two cards above it, and for the same reason: this is a
        /// golden image, so nothing in it may depend on when it was drawn — which
        /// the offscreen render's frozen clock is what guarantees.
        private static void paintRendered(Frame frame, LogicalSize size) {
            var image = Rendered.IMAGE;
            var width = size.width();

            // Into a rectangle rather than at natural size. The raster is twice
            // the logical box on purpose (see [Rendered]), so "natural size" would
            // put it on screen at twice the size it was composed at -- and half
            // the size again on a 2x display, which is the arithmetic
            // `drawImage(image, x, y)` is right about and this card does not want.
            var boxWidth = Math.min(280f, width - 180);
            var boxHeight = boxWidth * 240 / 560;
            frame.drawImage(image, 8, 8, boxWidth, boxHeight);

            // A piece of it, magnified: the source rectangle is in the rendered
            // image's own pixels, so this is its heading drawn 1:1 on a 1x
            // display. A crop of a render is what a thumbnail strip of a document
            // is made of.
            var right = width - 8 - 160;
            frame.drawImage(image, PhysicalRect.of(24, 16, 320, 56), right, 8, 160, 28, 1);
            frame.strokePath(Path.roundRect(right, 8, 160, 28, 3), Stroke.of(1), MUTED);

            // And the whole picture again at half the size, faded — a value is a
            // value, so the frame draws it as many times as it likes and nothing
            // is decoded, copied or closed in between.
            frame.drawImage(image, right, 48, 160, 160 * 240 / 560f, 0.45);
        }

        /// A sticky with a caret in it.
        ///
        /// Everything here is the editor's: the selection rectangles, the shaped
        /// text and the caret all come out of one shaping, which is what keeps a
        /// click landing where the glyph was drawn (ADR-0285). What the card owns
        /// is the paper it is written on.
        private void paintSticky(Frame frame, LogicalSize size) {
            var left = 8f;
            var top = 8f;

            frame.fillPath(Path.roundRect(left, top, STICKY_WIDTH, STICKY_HEIGHT, 6), PAPER);
            frame.strokePath(
                    Path.roundRect(left, top, STICKY_WIDTH, STICKY_HEIGHT, 6), Stroke.of(1), editing ? ACCENT : MUTED);

            frame.save();
            try {
                // Clipped to the paper, so a sticky that has been typed past the
                // bottom of does not write on the card.
                frame.clipTo(left, top, STICKY_WIDTH, STICKY_HEIGHT);
                sticky().paint(
                                frame,
                                left + STICKY_PADDING,
                                top + STICKY_PADDING,
                                new Editor.Ink(0xFF2E3440, CssColor.fade(INK, 0.45), 0xFF2E3440),
                                editing);
            } finally {
                frame.restore();
            }
        }

        /// A grid, and a crosshair wherever the pointer is.
        ///
        /// The card has padding, so this is the drawing that would be visibly
        /// wrong if input were reported from the border box instead of the
        /// content box — the crosshair would trail the pointer by the padding
        /// (ADR-0281).
        private void paintPointer(Frame frame, LogicalSize size) {
            var width = size.width();
            var height = size.height();

            var grid = Path.builder();
            for (var x = 0f; x <= width; x += 24) {
                grid.moveTo(x, 0).lineTo(x, height);
            }
            for (var y = 0f; y <= height; y += 24) {
                grid.moveTo(0, y).lineTo(width, y);
            }
            frame.strokePath(grid.build(), Stroke.of(1), CssColor.fade(MUTED, 0.5));

            if (at == null) {
                return;
            }
            frame.strokePath(
                    Path.builder()
                            .moveTo(0, at[1])
                            .lineTo(width, at[1])
                            .moveTo(at[0], 0)
                            .lineTo(at[0], height)
                            .build(),
                    Stroke.of(1).dashed(3, 3),
                    ACCENT);
            frame.fillPath(Path.circle(at[0], at[1], 4), ACCENT);
        }

        /// A plan the wheel zooms and a drag moves.
        ///
        /// The transform is the application's arithmetic, not the frame's: what
        /// the toolkit supplies is the wheel's detents and a drag that keeps
        /// reporting after it leaves the box.
        private void paintPlan(Frame frame, LogicalSize size) {
            var width = size.width();
            var height = size.height();
            var cx = width / 2 + panX;
            var cy = height / 2 + panY;

            frame.strokePath(
                    Path.roundRect(cx - 54 * zoom, cy - 30 * zoom, 108 * zoom, 60 * zoom, 6 * zoom),
                    Stroke.of(1.5),
                    INK);
            frame.strokePath(
                    Path.line(cx - 54 * zoom, cy, cx + 54 * zoom, cy),
                    Stroke.of(1).dashed(5, 4),
                    MUTED);
            frame.fillPath(Path.circle(cx, cy, 5 * zoom), ACCENT);
            frame.strokePath(Path.circle(cx, cy, 26 * zoom), Stroke.round(1.5).dash(Dash.of(0.5, 6)), WARN);
        }

        @Override
        public Widget build(BuildContext context) {
            // Kept rather than used: the editor is built on first use, so the
            // clipboard is handed over there. A `State` is the only thing that is
            // handed a `Host`, and without one the sticky's Ctrl+C would be a key
            // that did nothing rather than a key nobody took (ADR-0285).
            host = context.host().orElse(null);
            return new Wall(
                    "canvas",
                    "Canvas",
                    NOTE,
                    2,
                    List.of(
                            captioned(
                                    "Paths, strokes and a ramp",
                                    id("paths-card"),
                                    new Canvas(CanvasState::paintPaths, id("paths")),
                                    caption("Lines, cubics, arcs, a rounded rectangle, a dashed baseline and a"
                                            + " dotted ring — and a gradient that thins out rather than going"
                                            + " through grey. Not one of them names a native type.")),
                            captioned(
                                    "An image, and a piece of one",
                                    id("images-card"),
                                    new Canvas(
                                            this::paintImages,
                                            new Input() {

                                                @Override
                                                public void onPointer(PointerEvent event) {
                                                    // Where a paste will land.
                                                    // `content()` and not
                                                    // `local()`, so it is the
                                                    // rectangle the painter draws
                                                    // in (ADR-0281).
                                                    var at = event.content();
                                                    setState(() -> {
                                                        pointerX = at.x();
                                                        pointerY = at.y();
                                                    });
                                                }

                                                @Override
                                                public void onKey(KeyEvent event) {
                                                    if (event.kind() != KeyEvent.Kind.PRESSED
                                                            || !event.modifiers()
                                                                    .control()) {
                                                        return;
                                                    }
                                                    var board = host == null ? null : host.clipboard();
                                                    if (board == null) {
                                                        return;
                                                    }
                                                    switch (event.key()) {
                                                        case C -> {
                                                            // Out of the application
                                                            // and into anything: a
                                                            // PNG is what every
                                                            // desktop pastes.
                                                            var copied = (pasted == null ? Sample.IMAGE : pasted)
                                                                    .toClipboard(board);
                                                            setState(() -> note = copied
                                                                    ? "Copied as image/png."
                                                                    : "The platform declined it.");
                                                            if (copied) {
                                                                event.consume();
                                                            }
                                                        }
                                                        case V -> {
                                                            var found = Image.fromClipboard(board);
                                                            setState(() -> {
                                                                found.ifPresent(image -> {
                                                                    pasted = image;
                                                                    pastedX = Float.isNaN(pointerX) ? 8 : pointerX;
                                                                    pastedY = Float.isNaN(pointerY) ? 8 : pointerY;
                                                                });
                                                                note = found.isPresent()
                                                                        ? "Pasted "
                                                                                + found.get()
                                                                                        .width()
                                                                                + "x"
                                                                                + found.get()
                                                                                        .height()
                                                                                + " under the pointer."
                                                                        : describe(board);
                                                            });
                                                            if (found.isPresent()) {
                                                                event.consume();
                                                            }
                                                        }
                                                        default -> {}
                                                    }
                                                }

                                                @Override
                                                public String accessibleName() {
                                                    return "A picture, and a piece of it";
                                                }
                                            },
                                            id("images")),
                                    caption("One decoded PNG drawn four ways: at natural size, stretched into a"
                                            + " rectangle, cropped to a source region, and faded. Click it,"
                                            + " then Ctrl+V drops a screenshot under the pointer and Ctrl+C"
                                            + " copies this picture out.")),
                            captioned(
                                    "Five formats, one picture",
                                    id("codecs-card"),
                                    new Canvas(CanvasState::paintCodecs, id("codecs")),
                                    caption("PNG, QOI, WebP, GIF and JPEG — the same 96×64 picture, decoded"
                                            + " by `Image.decode` and drawn side by side. Nothing here names"
                                            + " a format: the label under each tile is what `ImageFormat`"
                                            + " read out of the file's first twelve bytes, so a `.png` full"
                                            + " of JPEG would still decode and still say JPEG.")),
                            captioned(
                                    "The image widget",
                                    id("image-widget-card"),
                                    new Row(
                                            List.of(
                                                    new ImageView(SAMPLE, "The sample, whole")
                                                            .withAttributes(id("image-contain", "framed")),
                                                    new ImageView(SAMPLE, "The sample, cropped to a square")
                                                            .fit(Fit.COVER)
                                                            .withAttributes(id("image-cover", "framed")),
                                                    new ImageView(SAMPLE, "The sample, stretched")
                                                            .fit(Fit.FILL)
                                                            .withAttributes(id("image-fill", "framed")),
                                                    new ImageView(SAMPLE, "The sample, at its own size")
                                                            .withAttributes(id("image-natural"))),
                                            id("image-row")),
                                    caption("No painter: `image` loads the same JPEG off the classpath on a"
                                            + " virtual thread and draws it contained, covering, filling"
                                            + " and at its natural size. The first three boxes are square,"
                                            + " so each fit is visible by what it leaves out.")),
                            captioned(
                                    "A code for a phone",
                                    id("qr-card"),
                                    new Row(
                                            List.of(
                                                    new QrCode(SHARE_LINK, Level.L, 4, id("qr-low")),
                                                    new QrCode(SHARE_LINK, Level.M, 4, id("qr-medium")),
                                                    new QrCode(SHARE_LINK, Level.H, 4, id("qr-high"))),
                                            id("qr-row")),
                                    caption("The same link at error correction L, M and H. The third is a"
                                            + " denser code for the same forty characters, because a"
                                            + " quarter of it is parity — and all three are drawn with"
                                            + " whole device pixels a module at every display scale, which"
                                            + " is what a phone camera needs. Point one at it.")),
                            captioned(
                                    "Rendered with no window",
                                    id("rendered-card"),
                                    new Canvas(CanvasState::paintRendered, id("rendered")),
                                    caption("The picture above is a widget tree — a card, two lines and a"
                                            + " button — rendered offscreen, encoded as a PNG and decoded"
                                            + " back. A server can take the same picture of a document.")),
                            captioned(
                                    "A caret on a canvas",
                                    id("sticky-card"),
                                    new Canvas(
                                            this::paintSticky,
                                            new Input() {

                                                @Override
                                                public void onPointer(PointerEvent event) {
                                                    var at = event.content();
                                                    var x = at.x() - 8 - STICKY_PADDING;
                                                    var y = at.y() - 8 - STICKY_PADDING;
                                                    switch (event.kind()) {
                                                        case PRESSED ->
                                                            setState(() -> sticky().pointerAt(
                                                                            x,
                                                                            y,
                                                                            event.modifiers()
                                                                                    .shift(),
                                                                            event.clickCount()));
                                                        // A drag extends the selection, which
                                                        // needs no capture of its own: the
                                                        // router captures on press (ADR-0281).
                                                        case MOVED -> {
                                                            if (!Float.isNaN(event.pressX())) {
                                                                setState(() -> sticky().pointerAt(x, y, true, 1));
                                                            }
                                                        }
                                                        default -> {}
                                                    }
                                                    event.consume();
                                                }

                                                @Override
                                                public void onKey(KeyEvent event) {
                                                    // `setState` around it and `consume` after
                                                    // it: the editor answers whether the key
                                                    // did anything, and an unhandled one has to
                                                    // stay unhandled or Tab would stop moving
                                                    // focus.
                                                    if (sticky().onKey(event)) {
                                                        setState(() -> {});
                                                        event.consume();
                                                    }
                                                }

                                                @Override
                                                public void onText(TextEvent event) {
                                                    if (sticky().onText(event.text())) {
                                                        setState(() -> {});
                                                        event.consume();
                                                    }
                                                }

                                                @Override
                                                public void onFocusChanged(boolean focused, boolean fromKeyboard) {
                                                    setState(() -> editing = focused);
                                                }

                                                // The switch that makes typing
                                                // work: a focused canvas is
                                                // offered committed text, and the
                                                // platform produces none until
                                                // something says it is typed into
                                                // -- so without this the sticky
                                                // takes every arrow key and never
                                                // a character (ADR-0285).
                                                @Override
                                                public boolean wantsText() {
                                                    return true;
                                                }

                                                @Override
                                                public String accessibleName() {
                                                    return "A sticky note";
                                                }
                                            },
                                            id("sticky")),
                                    caption("Click into it and type. Arrows, Ctrl+arrows, Home, End, Shift to"
                                            + " select, Ctrl+Z and the clipboard all work — and none of it is"
                                            + " a text-input: it is `text.edit.Editor` over a canvas.")),
                            captioned(
                                    "Where the pointer is",
                                    id("pointer-card"),
                                    new Canvas(
                                            this::paintPointer,
                                            new Input() {
                                                @Override
                                                public void onPointer(PointerEvent event) {
                                                    var local = event.content();
                                                    setState(() -> at = event.kind() == PointerEvent.Kind.EXITED
                                                            ? null
                                                            : new float[] {local.x(), local.y()});
                                                }

                                                @Override
                                                public String accessibleName() {
                                                    return "Pointer position";
                                                }
                                            },
                                            id("pointer")),
                                    caption("This card has padding, and the crosshair still lands under the"
                                            + " pointer: a canvas reads content() — the rectangle the painter"
                                            + " was given — and not the box it sits in.")),
                            captioned(
                                    "A wheel and a drag",
                                    id("plan-card"),
                                    new Canvas(
                                            this::paintPlan,
                                            new Input() {
                                                @Override
                                                public void onPointer(PointerEvent event) {
                                                    switch (event.kind()) {
                                                        case PRESSED ->
                                                            setState(() -> {
                                                                dragX = event.x() - panX;
                                                                dragY = event.y() - panY;
                                                            });
                                                        case MOVED -> {
                                                            if (!Float.isNaN(event.pressX())) {
                                                                setState(() -> {
                                                                    panX = event.x() - dragX;
                                                                    panY = event.y() - dragY;
                                                                });
                                                            }
                                                        }
                                                        case WHEEL ->
                                                            setState(() -> zoom = (float) Math.clamp(
                                                                    zoom * Math.pow(1.1, -event.ticksY()), 0.4, 3.0));
                                                        default -> {}
                                                    }
                                                    event.consume();
                                                }

                                                @Override
                                                public String accessibleName() {
                                                    return "Plan, pan and zoom";
                                                }
                                            },
                                            id("plan")),
                                    caption("Drag to move it and roll the wheel to scale it. The drag keeps"
                                            + " reporting after it leaves the card — the router captures the"
                                            + " pointer on press, so nothing here asked for it."))));
        }
    }
}
