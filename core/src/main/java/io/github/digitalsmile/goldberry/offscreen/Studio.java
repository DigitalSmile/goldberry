package io.github.digitalsmile.goldberry.offscreen;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.render.model.PhysicalSize;
import io.github.digitalsmile.goldberry.text.font.FontSource;
import io.github.digitalsmile.goldberry.text.font.Fonts;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;

/// What a render may keep between renders: one book, one cascade, one shaping
/// cache, one thread.
///
/// ```java
/// try (var studio = Studio.of(Controls.stylesheets(Theme.NORD_DARK))) {
///     for (var document : documents) {
///         var png = studio.picture(1200, 630)
///                 .render(new Card(document))
///                 .encodePng();
///         write(png);
///     }
/// }
/// ```
///
/// [Offscreen] on its own builds everything it needs and throws all of it away:
/// a font book opened and closed around the call unless one was handed in, a
/// [WidgetRenderer] and therefore a fresh [io.github.digitalsmile.goldberry.css.cascade.StyleResolver]
/// over stylesheets that have not changed, and a cold shaping cache. Rendering the
/// same document twice therefore does all of that work twice, which ADR-0284
/// recorded as a consequence and ADR-0425 is the decision about.
///
/// A studio is that work, done once and held. It is not a *result* cache: two
/// renders of the same document still build, style, lay out and rasterize the tree
/// from scratch, because what a preview is a picture of is program state and the
/// only honest cache key for it is the caller's. What is kept is the machinery —
/// the parsed and indexed cascade, the memory-mapped faces, and the shaped
/// paragraphs, which is where the repeated cost actually was.
///
/// ## One per thread, and that is the same decision
///
/// Everything a studio holds is confined to the thread that opened it, and this is
/// not a caution — [Fonts] and
/// [io.github.digitalsmile.goldberry.text.ParagraphCache]
/// both refuse a foreign thread outright, and the `Font` a book vends is held by
/// HarfBuzz and Blend2D objects that refuse one too. So the object that makes reuse
/// possible is exactly the object that must not be shared, and the rule is the
/// short one: **a studio per thread, reused as much as you like within it.**
///
/// That is not a restriction on rendering off the UI thread, which is fully
/// supported and tested — see [Offscreen]. It is a restriction on rendering with
/// *one studio* from several threads, and a pool of four workers wants four
/// studios, which costs four font books and buys four independent renders.
///
/// ## What a studio does not serve
///
/// [Offscreen#strip(io.github.digitalsmile.goldberry.widget.Widget)]. A
/// [Filmstrip] owns a clock for its whole lifetime and a renderer holds exactly
/// one, so a strip taken from a studio would move the clock under every still
/// picture taken beside it. A strip opened from [#picture] therefore shares the
/// book — the expensive part — and builds its own renderer, which costs one
/// cascade index and buys a strip that cannot interfere with anything.
public final class Studio implements AutoCloseable {

    private final List<Stylesheet> stylesheets;

    /// The book this studio opened, or null when the caller's own was handed over.
    private final @Nullable Fonts ownFonts;

    private final Fonts fonts;

    /// The kept renderer, and the whole of what this class is for.
    ///
    /// One per studio rather than one per render: it owns the cascade index and the
    /// shaping cache, and both are keyed on things a studio holds fixed — the
    /// stylesheets and the book. Its per-frame state (the frame's time, whether
    /// anything is animating, which element is being styled) is reset by every
    /// `render`, so sequential renders through one renderer cannot see each
    /// other's.
    private final WidgetRenderer renderer;

    private final Thread owner = Thread.currentThread();

    private boolean closed;

    private Studio(List<Stylesheet> stylesheets, @Nullable Fonts ownFonts, Fonts fonts) {
        this.stylesheets = stylesheets;
        this.ownFonts = ownFonts;
        this.fonts = fonts;
        this.renderer = new WidgetRenderer(stylesheets, fonts);
    }

    /// A studio over `sheets`, with a bundled book of its own.
    public static Studio of(List<Stylesheet> sheets) {
        return of(sheets, List.of());
    }

    /// A studio over `sheets`, with a bundled book of its own plus the faces the
    /// application ships — [io.github.digitalsmile.goldberry.Application#fonts()]'s
    /// list, so a preview draws the families the window draws (ADR-0349).
    public static Studio of(List<Stylesheet> sheets, List<FontSource> shipped) {
        Objects.requireNonNull(sheets, "sheets");
        Objects.requireNonNull(shipped, "shipped");
        var book = Fonts.bundled(List.copyOf(shipped));
        try {
            return new Studio(List.copyOf(sheets), book, book);
        } catch (RuntimeException e) {
            book.close();
            throw e;
        }
    }

    /// A studio over `sheets` and a book the caller already has.
    ///
    /// The book is **not** closed by [#close()], and it must have been opened on
    /// this thread — a studio is confined to its own thread and so is everything it
    /// holds, so a book from elsewhere would throw on the first paragraph rather
    /// than here.
    public static Studio over(List<Stylesheet> sheets, Fonts fonts) {
        Objects.requireNonNull(sheets, "sheets");
        Objects.requireNonNull(fonts, "fonts");
        return new Studio(List.copyOf(sheets), null, fonts);
    }

    /// A render of `size` physical pixels, wired to this studio.
    ///
    /// Everything else on the returned builder is still the caller's to set. Two of
    /// them give the studio's renderer back up, and say so where they are declared:
    /// [Offscreen#stylesheets(List)] and the three font methods describe a renderer
    /// this studio is not holding.
    ///
    /// @throws IllegalStateException if this studio is closed, or if the caller is
    ///         not the thread that opened it
    public Offscreen picture(PhysicalSize size) {
        requireUsable();
        return Offscreen.of(size).keeping(renderer, fonts, stylesheets);
    }

    /// [#picture(PhysicalSize)] in pixels.
    public Offscreen picture(int width, int height) {
        return picture(new PhysicalSize(width, height));
    }

    /// The renderer this studio keeps, for a test or a diagnostic.
    ///
    /// Exists for one assertion, and it is the one that says this class works:
    /// `renderer().paragraphs().misses()` does not move when a document is rendered
    /// a second time. A count is the right evidence for a cache — a stopwatch would
    /// be a flaky test about the same claim (ADR-0299).
    public WidgetRenderer renderer() {
        return renderer;
    }

    /// Closes the book this studio opened, if it opened one. Idempotent.
    ///
    /// Nothing else needs closing: a renderer holds no native handle of its own, and
    /// every tree a studio's renders built was unmounted before its render returned.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        requireOwner();
        closed = true;
        if (ownFonts != null) {
            ownFonts.close();
        }
    }

    private void requireUsable() {
        if (closed) {
            throw new IllegalStateException("this Studio has been closed");
        }
        requireOwner();
    }

    private void requireOwner() {
        var current = Thread.currentThread();
        if (current != owner) {
            throw new IllegalStateException("a Studio belongs to the thread that opened it (" + owner.getName()
                    + "), and this is " + current.getName()
                    + "; open one studio per thread — rendering off the UI thread is supported,"
                    + " sharing one studio between threads is not");
        }
    }

    @Override
    public String toString() {
        return "Studio[" + stylesheets.size() + " stylesheet(s), " + fonts + (closed ? ", closed" : "") + "]";
    }
}
