package io.github.digitalsmile.goldberry.html;

import org.junit.jupiter.api.Assumptions;

/// What a test that resolves an **entity** does when `libgoldberry` is not there.
///
/// [io.github.digitalsmile.goldberry.markdown.MarkdownRequirement]'s twin, and a
/// narrower job: parsing HTML is pure Java, so almost every test in this package runs
/// on a machine with no native library at all. The one thing that crosses is the
/// named-entity table, which is md4c's — 2125 names through one exported symbol
/// rather than a copy that drifts (ADR-0010, ADR-0294) — so a test that writes
/// `&amp;` asks for the library and the rest do not.
final class HtmlRequirement {

    private HtmlRequirement() {}

    /// Returns normally when an entity resolves, and aborts the test when the library
    /// is simply not there.
    static void enforce() {
        try {
            Html.parse("&amp;");
        } catch (UnsatisfiedLinkError | NoClassDefFoundError | ExceptionInInitializerError e) {
            Assumptions.abort("libgoldberry is not loadable from :html's tests, so no entity resolves: " + e
                    + ". Run :natives:cmakeBuild, or pass -Dgoldberry.native.library=<path>"
                    + " — see html/build.gradle.");
        }
    }
}
