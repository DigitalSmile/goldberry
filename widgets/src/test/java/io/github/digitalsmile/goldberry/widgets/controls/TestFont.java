package io.github.digitalsmile.goldberry.widgets.controls;

import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.text.Font;
import io.github.digitalsmile.goldberry.text.Fonts;
import org.junit.jupiter.api.Assumptions;

/// A real font book for the tests that render, and a skip for the machines with
/// no libgoldberry to shape with.
///
/// `:core` has [io.github.digitalsmile.goldberry.RendererRequirement] for the
/// same job and it cannot be reused: it lives in that module's test sources.
/// Rather than depend on those, this asks the only question `:widgets` actually
/// needs answered — can a font be loaded — by trying.
public final class TestFont {

    private static Fonts fonts;

    private TestFont() {
    }

    /// The bundled faces, opened lazily and kept for the run.
    ///
    /// Held rather than built per test: a `FontFace` owns native memory from two
    /// libraries and parsing one costs about 700 microseconds (ADR-0044). Never
    /// closed, which is right for a value that lives as long as the JVM.
    ///
    /// A book rather than one `Font`, because the cascade now resolves
    /// `font-family`, `font-size` and `font-weight` per node — a golden image
    /// drawn through a single font would not show that a button's label is
    /// SemiBold and the prose beside it is not.
    public static synchronized Fonts get() {
        if (fonts == null) {
            try {
                fonts = Fonts.bundled();
                // Force the first parse here, so a machine with no native
                // library skips rather than failing inside a paint pass.
                fonts.of(BundledFont.UI, 13);
            } catch (UnsatisfiedLinkError | NoClassDefFoundError | ExceptionInInitializerError e) {
                Assumptions.abort(
                        "libgoldberry is not loadable from :widgets' tests, so nothing can shape"
                                + " text: " + e);
            }
        }
        return fonts;
    }

    /// One font, for the tests that render a widget directly rather than through
    /// a cascade and therefore have no style to resolve one from.
    public static Font one() {
        return get().of(BundledFont.UI, 13);
    }

    /// A paint context over [#one()], for a test that calls `render` by hand.
    ///
    /// It carries a real [io.github.digitalsmile.goldberry.text.ParagraphCache]
    /// rather than shaping directly, because the cache is what makes a paragraph
    /// the *same instance* across calls — and a test that skipped it would not
    /// exercise the identity the retained render tree reads to keep a measure
    /// callback (ADR-0069).
    public static io.github.digitalsmile.goldberry.widget.Paints.Context context() {
        var cache = io.github.digitalsmile.goldberry.text.ParagraphCache.create();
        return new io.github.digitalsmile.goldberry.widget.Paints.Context() {

            @Override
            public Font font(io.github.digitalsmile.goldberry.css.ComputedStyle style) {
                return one();
            }

            @Override
            public io.github.digitalsmile.goldberry.text.Paragraph paragraph(
                    io.github.digitalsmile.goldberry.css.ComputedStyle style, String text) {
                return cache.paragraph(one(), text);
            }

            /// A stopped clock, which is what a test calling `render` by hand
            /// wants: the frame it gets is the frame at zero, every time. A test
            /// that needs a moving one drives a [WidgetRenderer] with
            /// [io.github.digitalsmile.goldberry.motion.Clock#virtual()] instead
            /// — which is also the only way to see a widget's own loop, since
            /// the renderer is what reads the clock once per frame (ADR-0081).
            @Override
            public double nowMillis() {
                return 0;
            }

            @Override
            public boolean reducedMotion() {
                return false;
            }
        };
    }
}
