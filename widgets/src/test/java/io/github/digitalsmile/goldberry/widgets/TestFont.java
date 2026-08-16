package io.github.digitalsmile.goldberry.widgets;

import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.text.Font;
import org.junit.jupiter.api.Assumptions;

/// A real font for the tests that render, and a skip for the machines with no
/// libgoldberry to shape with.
///
/// `:core` has [io.github.digitalsmile.goldberry.RendererRequirement] for the
/// same job and it cannot be reused: it lives in that module's test sources.
/// Rather than depend on those, this asks the only question `:widgets` actually
/// needs answered — can a font be loaded — by trying.
final class TestFont {

    private static Font font;

    private TestFont() {
    }

    /// The bundled UI face at body size, or a skipped test.
    ///
    /// Held for the run rather than built per test: a `Font` owns native memory
    /// from two libraries and building one costs about 700 microseconds
    /// (ADR-0044). It is never closed, which is right for a value that lives as
    /// long as the JVM.
    static synchronized Font get() {
        if (font == null) {
            try {
                font = Font.bundled(BundledFont.UI, 14);
            } catch (UnsatisfiedLinkError | NoClassDefFoundError | ExceptionInInitializerError e) {
                Assumptions.abort(
                        "libgoldberry is not loadable from :widgets' tests, so nothing can shape"
                                + " text: " + e);
            }
        }
        return font;
    }
}
