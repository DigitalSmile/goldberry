package io.github.digitalsmile.goldberry.paint;

import java.util.Objects;

import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.text.font.Font;

/// What the cascade resolved for the box a painter is drawing inside —
/// `docs/gaps.md` G11.
///
/// A painter is handed a [Frame] and a size, and until this existed that was
/// all: canvas text had to **name** a font (`Font.bundled(BundledFont.UI, 14)`)
/// rather than inherit the one `canvas { font-family: … }` resolved, and an
/// animated canvas had no clock to be a function of. Both are facts the renderer
/// already had and was not passing on.
///
/// ## A snapshot, not a view
///
/// Every value here is read **when the box is built** and carried into the paint
/// pass, rather than being a live question asked during it. That is not a
/// performance choice: [io.github.digitalsmile.goldberry.widget.style.Paints.Context]
/// answers per node by knowing which node is currently rendering, so a context
/// held onto and asked later would answer for whichever node rendered last. A
/// record cannot do that.
///
/// It is also why the theme-token accessors are **not** here.
/// `Paints.Context.color("--gb-chart-1", …)` resolves against the element being
/// rendered, and a canvas painter cannot name the tokens it will want in advance.
/// A widget that needs them is a [io.github.digitalsmile.goldberry.widget.style.Paints]
/// and reads them in `render`, which is what every chart in the catalog does
/// (ADR-0195).
///
/// @param font          the node's own resolved font — `font-family`, `font-size`
///                      and `font-weight` as the cascade settled them, from the
///                      same book every label is drawn with
/// @param ink           the node's resolved `color`, as `0xAARRGGBB`
/// @param nowMillis     this frame's time on the renderer's clock, read once per
///                      frame and shared, so two canvases animate on one tick
/// @param reducedMotion whether the user asked for less movement (§1.7)
public record CanvasStyle(Font font, int ink, double nowMillis, boolean reducedMotion) {

    /// The size a font is when nothing resolved one — the toolkit's own body
    /// size, and the number `Typography`'s default carries.
    private static final double DEFAULT_SIZE = 14;

    public CanvasStyle {
        Objects.requireNonNull(font, "font");
    }

    /// One instance, made on first use.
    ///
    /// Not a `static final` on the record itself: [Font#bundled] parses the face
    /// out of the jar, which is work no class should do while it is being
    /// initialized, and a painter that never goes unbound should never pay for
    /// it. Holding it afterwards is what keeps the second unbound paint from
    /// parsing the file again.
    private static final class Fallback {
        private static final CanvasStyle INSTANCE =
                new CanvasStyle(Font.bundled(BundledFont.UI, DEFAULT_SIZE), 0xFF000000, 0, false);
    }

    /// A style for a painter that is not on a canvas.
    ///
    /// [io.github.digitalsmile.goldberry.offscreen.Offscreen#paint] draws a bare
    /// painter with no widget tree over it, so there is no node whose style could
    /// be resolved, and a test that calls a painter directly is in the same
    /// position. These are **defaults, not answers**: the bundled UI face at the
    /// body size, opaque black, time zero. A painter that cares about any of them
    /// belongs on a `canvas`, where the cascade has something to say.
    public static CanvasStyle none() {
        return Fallback.INSTANCE;
    }

    /// This style with a different font — what a painter drawing a heading inside
    /// its canvas builds, rather than opening a second book.
    public CanvasStyle font(Font value) {
        return new CanvasStyle(value, ink, nowMillis, reducedMotion);
    }

    /// This style with a different ink.
    public CanvasStyle ink(int argb) {
        return new CanvasStyle(font, argb, nowMillis, reducedMotion);
    }
}
