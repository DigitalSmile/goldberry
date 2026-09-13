package io.github.digitalsmile.goldberry.markdown.view;

import org.junit.jupiter.api.Assumptions;

import io.github.digitalsmile.goldberry.markdown.Markdown;

/// [MarkdownRequirement]'s twin, for the tests that live in the view's package.
///
/// A copy of four lines rather than an export: `MarkdownRequirement` is
/// package-private in the package it guards, and making it public would put a test
/// helper in the module's surface.
final class MarkdownViews {

    private MarkdownViews() {}

    /// Returns normally when md4c can parse, and aborts the test when the library is
    /// not there.
    static void requireLibrary() {
        try {
            Markdown.parse("x");
        } catch (UnsatisfiedLinkError | NoClassDefFoundError | ExceptionInInitializerError e) {
            Assumptions.abort("libgoldberry is not loadable from :html's tests, so nothing can parse: " + e);
        }
    }
}
