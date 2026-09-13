package io.github.digitalsmile.goldberry.markdown.model;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/// `![alt](src "title")`.
///
/// A leaf whose alt text is a `String`, and that is a decision rather than an
/// oversight: Markdown lets the alt text be marked up, and what an alt text *is* —
/// the words a reader hears when the picture is not there — has no marks in it. The
/// model keeps the flattened text and drops the emphasis nobody can hear.
///
/// **Nothing here fetches anything.** A `src` is a string; whether it is on disk, in
/// a jar or behind an `HttpClient` under the application's policy is the
/// application's business, which is the rule every content module shares
/// (ADR-0190).
///
/// @param src where the image is
/// @param title the tooltip, or null
/// @param alt the alt text, marks dropped
public record Image(String src, @Nullable String title, String alt) implements Inline {

    public Image {
        Objects.requireNonNull(src, "src");
        Objects.requireNonNull(alt, "alt");
    }

    @Override
    public List<Inline> children() {
        return List.of();
    }
}
