package io.github.digitalsmile.goldberry.widgets;

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
final class TestFont {

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
    static synchronized Fonts get() {
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
    static Font one() {
        return get().of(BundledFont.UI, 13);
    }
}
