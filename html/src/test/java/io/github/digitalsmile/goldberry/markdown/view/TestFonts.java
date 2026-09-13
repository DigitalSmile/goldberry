package io.github.digitalsmile.goldberry.markdown.view;

import org.junit.jupiter.api.Assumptions;

import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.text.font.Fonts;

/// The bundled faces, for the tests that draw.
///
/// `:widgets` has `TestFont` for this job and it cannot be reused — it lives in that
/// module's test sources — so this asks the only question this module needs answered:
/// can a face be opened. Held for the run rather than built per test, because a
/// `FontFace` owns native memory from two libraries and parsing one costs about 700
/// microseconds (ADR-0044).
final class TestFonts {

    private static Fonts fonts;

    private TestFonts() {}

    static synchronized Fonts get() {
        if (fonts == null) {
            try {
                fonts = Fonts.bundled();
                // The first parse here, so a machine with no native library skips
                // rather than failing inside a paint pass.
                fonts.of(BundledFont.UI, 13);
            } catch (UnsatisfiedLinkError | NoClassDefFoundError | ExceptionInInitializerError e) {
                Assumptions.abort("libgoldberry is not loadable from :html's tests, so nothing can shape text: " + e);
            }
        }
        return fonts;
    }
}
