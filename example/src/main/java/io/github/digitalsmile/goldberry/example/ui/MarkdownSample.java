package io.github.digitalsmile.goldberry.example.ui;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/// The document the Markdown screen opens with.
///
/// A **resource** rather than a text block, and the reason is what is in it: the
/// sample covers every construct the parser reports, which means it contains
/// backslash escapes, backticks, `\n` sequences that are supposed to be two
/// characters, and two trailing spaces on a line that mean a hard break. A Java
/// string literal eats the first three and every formatter in the world eats the
/// fourth.
///
/// Beside the screens' KDL documents, because it is the same kind of thing: content
/// the application ships, which somebody edits without recompiling to see it.
public final class MarkdownSample {

    private static final String RESOURCE = "markdown-sample.md";

    private MarkdownSample() {}

    /// The sample, as it ships.
    ///
    /// Read on each call, which is once: the model reads it when it is constructed.
    /// No `opens` is needed for it — this class and the file are in the same module,
    /// and JPMS encapsulates a resource from *other* modules (ADR-0093).
    public static String text() {
        try (var in = MarkdownSample.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("the Markdown sample is missing from the jar: " + RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + RESOURCE, e);
        }
    }
}
