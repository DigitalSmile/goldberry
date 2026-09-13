package io.github.digitalsmile.goldberry.markdown;

import org.junit.jupiter.api.Assumptions;

/// What a test that parses does when `libgoldberry` is not there.
///
/// The same job `RendererRequirement` does for the rasterizer and
/// `NativeLibraryRequirement` does inside `:natives`, and it cannot reuse either:
/// one asks Blend2D and the other lives in a module's test sources. So it asks the
/// only question this module can — whether a parse works — by trying one.
///
/// A missing library is an ordinary state on a contributor's machine and skips. A
/// broken one is a real failure and propagates: `-Dgoldberry.native.required=true`
/// is not consulted here because it does not need to be, since a library that loads
/// and then parses wrongly fails the assertions rather than skipping them.
final class MarkdownRequirement {

    private MarkdownRequirement() {}

    /// Returns normally when md4c can parse, and aborts the test when the library is
    /// simply not there.
    static void enforce() {
        try {
            Markdown.parse("x");
        } catch (UnsatisfiedLinkError | NoClassDefFoundError | ExceptionInInitializerError e) {
            Assumptions.abort("libgoldberry is not loadable from :html's tests, so nothing can parse: " + e
                    + ". Run :natives:cmakeBuild, or pass -Dgoldberry.native.library=<path>"
                    + " — see html/build.gradle.");
        }
    }
}
