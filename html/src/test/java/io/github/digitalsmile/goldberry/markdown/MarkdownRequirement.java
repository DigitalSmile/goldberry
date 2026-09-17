package io.github.digitalsmile.goldberry.markdown;

import java.util.Optional;

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
public final class MarkdownRequirement {

    private MarkdownRequirement() {}

    /// Returns normally when md4c can parse, and aborts the test when the library is
    /// simply not there.
    public static void enforce() {
        missing().ifPresent(Assumptions::abort);
    }

    /// Why nothing can parse, or empty when md4c is there.
    ///
    /// The question on its own, for a runner that skips by returning a reason rather
    /// than by catching an abort -- jqwik, whose properties report an abort as an
    /// error ([MarkdownAvailable]).
    public static Optional<String> missing() {
        try {
            Markdown.parse("x");
            return Optional.empty();
        } catch (UnsatisfiedLinkError | NoClassDefFoundError | ExceptionInInitializerError e) {
            return Optional.of("libgoldberry is not loadable from :html's tests, so nothing can parse: " + e
                    + ". Run :natives:cmakeBuild, or pass -Dgoldberry.native.library=<path>"
                    + " — see html/build.gradle.");
        }
    }
}
