package io.github.digitalsmile.goldberry.offscreen;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.frame.FrameSequence;
import io.github.digitalsmile.goldberry.image.Image;
import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.motion.Clock;
import io.github.digitalsmile.goldberry.paint.Frame;
import io.github.digitalsmile.goldberry.paint.Painter;
import io.github.digitalsmile.goldberry.paint.tree.RenderTree;
import io.github.digitalsmile.goldberry.render.PixelBuffer;
import io.github.digitalsmile.goldberry.render.model.DisplayScale;
import io.github.digitalsmile.goldberry.render.model.PhysicalSize;
import io.github.digitalsmile.goldberry.render.model.PixelFormat;
import io.github.digitalsmile.goldberry.text.font.Font;
import io.github.digitalsmile.goldberry.text.font.FontSource;
import io.github.digitalsmile.goldberry.text.font.Fonts;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;

/// A picture of a scene, with no window under it.
///
/// ```java
/// var png = Offscreen.of(1200, 900)
///         .stylesheets(Controls.stylesheets(Theme.NORD_DARK))
///         .render(new BoardPreview(document))
///         .encodePng();
/// ```
///
/// Two things can be rendered, and they are the two halves of the toolkit:
/// [#paint(Painter)] runs a painter over the whole buffer, and [#render(Widget)]
/// builds, styles, lays out and paints a **real widget tree** — the same
/// sequence a window runs, in the same order, with no display, no SDL and no
/// compositor anywhere near it (ADR-0284). Since ADR-0423 that is not a
/// resemblance: both run their steps through the same
/// [io.github.digitalsmile.goldberry.frame.FrameSequence].
///
/// A third terminal, [#strip(Widget)], mounts the tree and keeps it, so a caller
/// can drive the clock and take a picture repeatedly (ADR-0424).
///
/// What it is for: a server-rendered preview, an OpenGraph card, an export, a
/// thumbnail. `docs/gaps.md` G5 asked for it because the pieces all existed and
/// none of them was an entry point — `PixelBuffer.allocate`, `Frame.over` and the
/// headless backend are public, and stitching them into the sequence a window
/// runs was left to whoever needed it. This is that sequence, shipped.
///
/// ## Three passes, and a clock that does not tick
///
/// [#render(Widget)] lays the tree out **twice** before it paints it once, and
/// the reason is not performance:
///
/// - A newly mounted element starts no transition, and a clock-driven arrival has
///   no beginning until something reads the clock. A tree painted once shows every
///   arriving widget at the *start* of its entrance — for a `message` that is four
///   banners at zero opacity, holding their space and drawing nothing.
/// - A self-measuring widget — `text-area`, `masonry` — learns its own region from
///   the rectangles a laid-out frame produced, delivered by the router. A pass
///   that never fed them back would give every one of them a first-frame answer
///   for ever: a `text-area` wrapped as though it were narrow, a `masonry` caught
///   mid-settle.
///
/// So the first pass mounts and measures, the clock advances past the transition
/// duration, the second pass measures again — and only then is the tree rendered
/// and painted, because a widget told its size may rearrange itself in response
/// and the picture has to be of what it rearranged into. The two measuring passes
/// draw nothing at all; one rasterization happens, at the end. The clock is
/// **virtual**: a
/// preview that depended on when it was taken would be a preview that differs
/// between two requests for the same document, and a `spinner` is enough to make
/// that happen. [#settle(int)] is how far it moves.
///
/// ## What it costs, and what a server should keep
///
/// A buffer of `width * height * 4` bytes, a full build and cascade of the tree,
/// a font book opened and closed around the call, and a cascade index and shaping
/// cache built cold and thrown away. **A server rendering many previews should
/// hold a [Studio]**, which keeps all four and hands out renders wired to them
/// (ADR-0425).
///
/// What is never kept is the tree: each render mounts a fresh one and unmounts it,
/// so two renders cannot share state through one and a `State`'s `dispose` runs.
/// That is deliberate and it is not a cache — a preview is a picture of program
/// state, and the only honest cache key for one is the caller's.
///
/// ## Threads
///
/// **A render may run on any thread, and several may run at once.** Nothing on the
/// path touches a window, a backend, SDL or the `GoldberryRuntime`, and every
/// object it builds — the element tree, the cascade, the render tree and its Yoga
/// nodes, the shaping cache, the frame and its Blend2D context — is created and
/// dropped inside the call, on the calling thread. `OffscreenThreadTest` renders
/// the same tree on eight threads at once and compares every pixel against a
/// render on one.
///
/// The limit is *sharing*, not the thread: [Fonts], a [Font] and a [Studio] are
/// each confined to the thread that opened them, because the faces underneath them
/// belong to HarfBuzz and Blend2D and those are confined too. So [#fonts(Fonts)],
/// [#font(Font)] and [Studio] are per-thread objects, and a pool of four workers
/// wants four of them. All three now refuse a foreign thread rather than corrupt
/// themselves quietly, which is the other half of ADR-0425.
///
/// Blend2D's rasterization workers are a process-wide pool shared by every live
/// context, so concurrent renders contend for it and a render that cannot get
/// workers paints synchronously instead (ADR-0042). That is slower and it is not
/// wrong: the pixels are the same, which is what the concurrency test asserts.
public final class Offscreen {

    /// The one format the whole toolkit blits.
    private static final PixelFormat FORMAT = PixelFormat.BGRA32_PREMULTIPLIED;

    /// How far the clock moves between the two passes, in milliseconds.
    ///
    /// Past `Phase.DURATION_MILLIS`, so everything that arrives has arrived. Still
    /// a frozen clock and still deterministic — a `spinner` is at whatever it is
    /// at 200 ms, on every machine and in every run.
    private static final int DEFAULT_SETTLE_MILLIS = 200;

    private final PhysicalSize size;

    private DisplayScale scale = DisplayScale.ONE;

    private List<Stylesheet> stylesheets = List.of();

    /// The book, or the one font, or neither — at most one of these is set, and
    /// the pair is a sealed choice written as two fields because an application
    /// picks one by calling one method.
    private @Nullable Fonts fonts;

    private @Nullable Font font;

    /// The faces the book this call opens should know beyond the bundled ones —
    /// ignored when the caller hands over a book or a font of their own.
    private List<FontSource> shippedFonts = List.of();

    private int settleMillis = DEFAULT_SETTLE_MILLIS;

    private int background;

    /// A renderer a [Studio] is keeping across renders, or null for the usual case
    /// of one built for this call and thrown away (ADR-0425).
    private @Nullable WidgetRenderer kept;

    private Offscreen(PhysicalSize size) {
        this.size = size;
    }

    /// Wires this render to a studio's kept renderer, book and stylesheets.
    ///
    /// Package-private, and the three go in together on purpose: a renderer is
    /// built over a cascade and a book, so a caller who could attach one without
    /// the other two could attach a renderer that resolves different styles than
    /// the stylesheets this builder claims.
    Offscreen keeping(WidgetRenderer renderer, Fonts book, List<Stylesheet> sheets) {
        this.kept = renderer;
        this.fonts = book;
        this.font = null;
        this.shippedFonts = List.of();
        this.stylesheets = sheets;
        return this;
    }

    /// A render of `size` **physical** pixels.
    ///
    /// Physical, because this is a raster: the file that comes out is this many
    /// pixels wide whatever scale it was drawn at. What [#scale(float)] changes is
    /// how much *content* fits in them.
    ///
    /// @throws IllegalArgumentException if the size has no pixels in it
    public static Offscreen of(PhysicalSize size) {
        Objects.requireNonNull(size, "size");
        if (size.isEmpty()) {
            throw new IllegalArgumentException("there is nothing to render into: " + size);
        }
        return new Offscreen(size);
    }

    /// [#of(PhysicalSize)] in pixels.
    public static Offscreen of(int width, int height) {
        return of(new PhysicalSize(width, height));
    }

    /// The display scale to draw at. One by default.
    ///
    /// A 1200&times;900 render at 2&times; holds 600&times;450 **logical** pixels
    /// of interface drawn at twice the detail — which is what a retina preview is,
    /// and is not the same picture enlarged.
    public Offscreen scale(DisplayScale value) {
        this.scale = Objects.requireNonNull(value, "scale");
        return this;
    }

    /// [#scale(DisplayScale)] from a factor.
    public Offscreen scale(float factor) {
        return scale(new DisplayScale(factor));
    }

    /// The stylesheets the tree is cascaded against — `Controls.stylesheets(theme)`
    /// and whatever the application adds.
    ///
    /// Empty by default, which renders an unstyled tree rather than refusing: a
    /// `Box`-level scene is a legitimate thing to photograph.
    /// Naming sheets gives up a [Studio]'s kept renderer, if this render came from
    /// one: a renderer *is* its cascade, and one built over other sheets would
    /// resolve other styles.
    public Offscreen stylesheets(List<Stylesheet> sheets) {
        this.stylesheets = List.copyOf(Objects.requireNonNull(sheets, "sheets"));
        this.kept = null;
        return this;
    }

    /// The font book text is shaped against. **Owned by the caller** and not
    /// closed here.
    ///
    /// Without one, a bundled book is opened for the call and closed after it —
    /// correct, and wasteful if there is a second call coming.
    public Offscreen fonts(Fonts value) {
        this.fonts = Objects.requireNonNull(value, "fonts");
        this.font = null;
        // A kept renderer shapes against the book it was built over, so naming
        // another one gives it up -- see [Studio#picture].
        this.kept = null;
        return this;
    }

    /// The faces an application ships, for the book this call opens —
    /// [io.github.digitalsmile.goldberry.Application#fonts()]'s list, so a render
    /// test paints the families the window paints (ADR-0349).
    ///
    /// Clears a book or a font given earlier: a caller naming faces is asking for
    /// the book to be opened here, with them in it.
    public Offscreen fonts(List<FontSource> shipped) {
        this.shippedFonts = List.copyOf(Objects.requireNonNull(shipped, "shipped"));
        this.fonts = null;
        this.font = null;
        this.kept = null;
        return this;
    }

    /// Draw every node with one font, whatever the cascade said.
    ///
    /// The narrow form `WidgetRenderer` offers for a test or a benchmark: it
    /// ignores `font-family`, `font-size` and `font-weight` entirely, so a
    /// button's label is drawn at the same weight as the prose beside it. An
    /// application wants [#fonts(Fonts)].
    public Offscreen font(Font value) {
        this.font = Objects.requireNonNull(value, "font");
        this.fonts = null;
        this.kept = null;
        return this;
    }

    /// How far the virtual clock moves between the two passes. 200 ms by default.
    ///
    /// Zero renders the tree as it is the instant it mounts — every arriving
    /// widget at the start of its entrance — which is occasionally what a caller
    /// wants and is never what a preview wants.
    ///
    /// @throws IllegalArgumentException if negative: a clock that runs backwards
    ///         is a different feature
    public Offscreen settle(int millis) {
        if (millis < 0) {
            throw new IllegalArgumentException("a settle time is not negative, and " + millis + " is");
        }
        this.settleMillis = millis;
        return this;
    }

    /// A colour to fill the buffer with before anything is drawn. Transparent by
    /// default.
    ///
    /// A rendered tree paints whatever its root's `background` resolves to, which
    /// is usually enough. This is for the case a PNG is going somewhere that
    /// cannot show transparency — an OpenGraph card on a white page — where the
    /// alternative is a checkerboard nobody intended.
    public Offscreen background(int argb) {
        this.background = argb;
        return this;
    }

    /// Runs `painter` over the whole buffer and returns what it drew.
    ///
    /// The painter is handed the frame with the origin at the top-left and the
    /// logical size of the buffer, exactly as a `canvas` would — and is bracketed
    /// in a `save`/`restore` pair for the same reason `canvas` does it: whatever
    /// it leaves set is not this method's to clean up.
    public Image paint(Painter painter) {
        Objects.requireNonNull(painter, "painter");
        var buffer = PixelBuffer.allocate(size, FORMAT);
        var frame = Frame.over(buffer, scale);
        try {
            fillBackground(frame);
            frame.save();
            try {
                painter.paint(frame, frame.size());
            } finally {
                frame.restore();
            }
        } finally {
            // Before a pixel is read: Blend2D may still have work queued, and a
            // buffer read from a context that has not ended is half-drawn
            // (ADR-0042).
            frame.end();
        }
        return Image.of(buffer);
    }

    /// Builds, styles, lays out and paints `root`, and returns the picture.
    ///
    /// The same sequence `Launcher` runs for a window — prepare, flush, render,
    /// update, capture the regions — twice with the clock advanced in between,
    /// and then once more to paint. See the note on this class for why three.
    ///
    /// Literally the same, since ADR-0423: the steps and their order are
    /// [io.github.digitalsmile.goldberry.frame.FrameSequence]'s,
    /// which a window runs its frames through too. What is this method's own is
    /// how many passes there are and what happens to the clock between them.
    ///
    /// The tree is mounted and unmounted inside the call, so a `State`'s
    /// `dispose` runs and nothing survives the picture.
    public Image render(Widget root) {
        Objects.requireNonNull(root, "root");
        // Opened here and closed in the `finally`, and only when the caller named
        // no fonts of their own: a book is memory-mapped faces and is the one
        // thing in a render worth keeping between calls.
        var ownFonts = opensItsOwnBook() ? Fonts.bundled(shippedFonts) : null;
        try {
            var clock = Clock.virtual();
            var renderer = renderer(ownFonts, clock);

            var buffer = PixelBuffer.allocate(size, FORMAT);
            var frame = Frame.over(buffer, scale);
            var tree = new ElementTree(root);
            try {
                var render = RenderTree.create();
                try {
                    // The router is here for one reason and it is not input: it is
                    // what delivers `Measured` to a widget that sizes itself from
                    // the region it was laid out into. A render that skipped it
                    // would photograph every `text-area` and `masonry` on its
                    // first guess.
                    var router = new PointerRouter();
                    // The steps, and their order, are `FrameSequence`'s -- the
                    // same object a window runs its frames through (ADR-0423).
                    // What is left here is the *number* of passes and the clock
                    // between them, which is the part that is this class's own.
                    var sequence = FrameSequence.over(tree, render, router);
                    fillBackground(frame);
                    // Two measuring passes and then the picture, and the third one
                    // is not a rounding of the second. A region fed back is
                    // delivered to a widget that may `setState` on it -- `masonry`
                    // reflows its columns when it learns how wide they came out --
                    // so the tree that is painted has to be rendered *after* the
                    // last feedback, not during it. Painting the second pass
                    // instead photographs every self-arranging widget one move
                    // from settled, which is what the gallery's Basic screen
                    // showed the first time this was written that way.
                    measure(frame, renderer, sequence);
                    clock.advance(settleMillis);
                    measure(frame, renderer, sequence);
                    draw(frame, renderer, sequence, render);
                } finally {
                    // **The frame first, and this order is not tidiness.** A
                    // frame's Blend2D context is asynchronous: painting queues
                    // commands that still point at the fonts, the paths and the
                    // layer rasters the tree lent them, and `end()` is what joins
                    // the workers. Releasing any of that before the join is a
                    // read of freed memory inside Blend2D's command processor --
                    // which is a SIGSEGV in a worker thread rather than an
                    // exception, and is how this was found: the showcase's
                    // sticky is the first widget whose `State` owns a `Font` and
                    // closes it in `dispose`, and unmounting before the join
                    // crashed the JVM (ADR-0284).
                    frame.end();
                    render.close();
                }
            } finally {
                // Last: a mounted tree holds state objects with `dispose` hooks,
                // and a render that leaked them would leak one per preview.
                tree.unmount();
            }
            return Image.of(buffer);
        } finally {
            if (ownFonts != null) {
                ownFonts.close();
            }
        }
    }

    /// The renderer for this call, over whichever of the three font sources is in
    /// force.
    ///
    /// `WidgetRenderer` takes a book or a single font and there is no third form,
    /// so this is where the choice is made once rather than at each use.
    private WidgetRenderer renderer(@Nullable Fonts ownFonts, Clock clock) {
        // A studio's, when this render came from one: the cascade index and the
        // shaping cache behind it are what a studio exists to keep (ADR-0425).
        // Its clock is re-pointed at this render's, which is safe because a studio
        // is confined to one thread and its renders are therefore sequential.
        var studios = kept;
        if (studios != null) {
            return studios.clock(clock);
        }
        if (ownFonts != null) {
            return new WidgetRenderer(stylesheets, ownFonts).clock(clock);
        }
        var book = fonts;
        if (book != null) {
            return new WidgetRenderer(stylesheets, book).clock(clock);
        }
        return new WidgetRenderer(stylesheets, Objects.requireNonNull(font, "font")).clock(clock);
    }

    /// One pass that lays the tree out and tells it what it came out as, without
    /// drawing anything.
    ///
    /// **No paint at all**, which is the other half of why the passes are cheap:
    /// a measuring pass exists to produce rectangles, the rectangles come from
    /// `update`, and rasterizing them twice over would be two full frames thrown
    /// away.
    ///
    /// The regions are captured and dropped on the floor — the return value is a
    /// window's, for anchoring a menu under where a button was drawn. What this
    /// pass is after is the *side effect*: a `Measured` widget being told what it
    /// came out as.
    private static void measure(Frame frame, WidgetRenderer renderer, FrameSequence sequence) {
        sequence.layOut(frame, renderer);
        sequence.captureRegions(frame);
    }

    /// The pass that is kept: build whatever the last feedback dirtied, lay it out
    /// once more, and paint it.
    ///
    /// The full paint rather than a damaged one. A window paints only what changed
    /// because the backend promises last frame's pixels are still there
    /// (ADR-0072); this buffer has no last frame, so the question does not arise.
    /// That is the one step [FrameSequence] deliberately does not own, and this
    /// line is the whole of this side of it.
    private static void draw(Frame frame, WidgetRenderer renderer, FrameSequence sequence, RenderTree render) {
        sequence.layOut(frame, renderer);
        render.paint(frame);
    }

    /// Whether [#render] has to open a book for itself.
    ///
    /// Not when a studio is keeping one, not when the caller handed one over, and
    /// not when a single font was named — the three ways a book arrives from
    /// outside this call.
    private boolean opensItsOwnBook() {
        return kept == null && fonts == null && font == null;
    }

    /// Mounts `root` and hands back a strip to photograph it with.
    ///
    /// The third terminal, beside [#paint(Painter)] and [#render(Widget)], and the
    /// one with a lifetime: a [Filmstrip] keeps the mounted tree so a caller can
    /// drive the clock between pictures and get frame 3 of a transition rather than
    /// three first frames (ADR-0424).
    ///
    /// **[#settle(int)] does not apply**, and setting it is ignored here. A settle
    /// time is how far a still picture jumps to get past the entrance animations,
    /// and a strip is a request to photograph those animations: its clock starts at
    /// zero and moves only when [Filmstrip#advance(int)] says so.
    ///
    /// The strip must be closed. It holds a mounted tree, a native render tree and —
    /// unless a book or a font was named — a font book opened for it.
    ///
    /// @throws NullPointerException if `root` is null
    public Filmstrip strip(Widget root) {
        Objects.requireNonNull(root, "root");
        var ownFonts = opensItsOwnBook() ? Fonts.bundled(shippedFonts) : null;
        try {
            var clock = Clock.virtual();
            // **Never the studio's renderer**, even when there is one: a renderer
            // holds one clock, and a strip drives its own for its whole lifetime.
            // A strip sharing a studio's renderer would move the clock under every
            // still picture taken from that studio beside it. The book is shared --
            // that is the expensive part -- and the cascade index is rebuilt, which
            // is what a strip costs a studio ([Studio]).
            var renderer = new WidgetRenderer(stylesheets, bookFor(ownFonts)).clock(clock);
            return new Filmstrip(size, scale, FORMAT, background, ownFonts, renderer, clock, root);
        } catch (RuntimeException e) {
            if (ownFonts != null) {
                ownFonts.close();
            }
            throw e;
        }
    }

    /// The book a strip shapes against: the one opened for it, the caller's, or a
    /// studio's.
    ///
    /// A strip cannot take the [#font(Font)] form — a single font ignores the
    /// cascade entirely, and a transition whose `font-size` moves is one of the
    /// things a strip exists to photograph.
    ///
    /// @throws IllegalStateException if only a single font was named
    private Fonts bookFor(@Nullable Fonts ownFonts) {
        if (ownFonts != null) {
            return ownFonts;
        }
        var book = fonts;
        if (book != null) {
            return book;
        }
        throw new IllegalStateException("a strip shapes against a font book, and this render was given one font;"
                + " font(Font) ignores the cascade, which is most of what a strip photographs");
    }

    private void fillBackground(Frame frame) {
        if (background != 0) {
            frame.fill(background);
        }
    }
}
