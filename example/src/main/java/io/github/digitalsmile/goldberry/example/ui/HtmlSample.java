package io.github.digitalsmile.goldberry.example.ui;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/// The page the HTML screen opens with.
///
/// A **resource** rather than a text block, for [MarkdownSample]'s reason and one
/// more of its own: the sample is markup, so it is full of quotation marks, and a
/// Java string literal full of `\"` is a page nobody can read in the editor it is
/// about to be shown in.
///
/// Beside the screens' KDL documents, because it is the same kind of thing: content
/// the application ships, which somebody edits without recompiling to see it.
public final class HtmlSample {

    private static final String RESOURCE = "html-sample.html";

    private HtmlSample() {}

    /// The sample, as it ships.
    ///
    /// Read on each call, which is once: the model reads it when it is constructed.
    /// No `opens` is needed for it — this class and the file are in the same module,
    /// and JPMS encapsulates a resource from *other* modules (ADR-0093).
    public static String text() {
        try (var in = HtmlSample.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("the HTML sample is missing from the jar: " + RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + RESOURCE, e);
        }
    }
}
