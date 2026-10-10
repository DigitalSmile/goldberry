package dev.goldberry.assets;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

import dev.goldberry.text.font.FallbackSource;

/// The service through which an artifact brings fallback faces: faces searched
/// for the characters the faces a stylesheet names do not have.
///
/// ```java
/// // in the artifact's module-info
/// provides dev.goldberry.assets.FallbackFont with com.example.fonts.NotoFallbacks;
///
/// public final class NotoFallbacks implements FallbackFont {
///     @Override public List<FallbackSource> faces() {
///         return List.of(FallbackSource.of(FontSource.stream("Noto Sans Arabic", 400, Style.UPRIGHT,
///                 () -> NotoFallbacks.class.getResourceAsStream("NotoSansArabic-Regular.ttf")),
///                 UnicodeScript.ARABIC));
///     }
/// }
/// ```
///
/// The emoji face's shape, for the emoji face's reason: a face of several
/// megabytes is something an application adds on purpose, and with the
/// artifact on the module path nothing else changes. Every [dev.goldberry.text.font.Fonts]
/// book searches the faces it brings after the application's own
/// `Application.fallbacks()`.
///
/// A provider answers [FallbackSource]s rather than bytes, because a source
/// reads its file lazily, names its weight and style, and carries a hint of the
/// scripts it is worth opening for; and a list, because one family is several
/// files.
///
/// Read more:
/// [Faces, fonts and the book](https://goldberry.dev/docs/guide/text.html#faces-fonts-and-the-book).
public interface FallbackFont {

    /// The faces, in the order they are searched.
    List<FallbackSource> faces();

    /// Every provider's faces, providers ordered by class name so the order does
    /// not depend on the module path, each provider's in its own order.
    ///
    /// Looked up on each call, for [EmojiFont#provider()]'s reason: the book
    /// that opens faces asks once and keeps the answer.
    static List<FallbackSource> provided() {
        var providers = ServiceLoader.load(FallbackFont.class).stream()
                .sorted(Comparator.comparing(provider -> provider.type().getName()))
                .toList();
        var faces = new ArrayList<FallbackSource>();
        for (var provider : providers) {
            faces.addAll(provider.get().faces());
        }
        return List.copyOf(faces);
    }
}
