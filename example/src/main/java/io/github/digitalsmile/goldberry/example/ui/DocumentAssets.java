package io.github.digitalsmile.goldberry.example.ui;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.content.ImageSource;
import io.github.digitalsmile.goldberry.image.Image;

/// Where the two document screens' pictures come from — the **application's**
/// answer, which is the whole point of [ImageSource] (ADR-0300).
///
/// The showcase's answer is the smallest honest one: it ships one picture, beside
/// the documents that name it, and it hands that over for the `src` those documents
/// write. Anything else gets **null**, which is how both samples can also show what a
/// source a reader cannot see looks like — the alt text, exactly as before.
///
/// What a real application would put here is the interesting part, and none of it is
/// the toolkit's: a path resolved against the note's own folder, a key in a
/// content-addressed store, an `HttpClient` under its own policy. Nothing in `:html`
/// opens a file or a socket (ADR-0190).
///
/// ## Decoded once, held for ever
///
/// An [Image] is a **value** (ADR-0283) — it owns no native handle and needs no
/// closing — so an application may cache one in a map and hand it to a widget that is
/// rebuilt every frame. That is what makes this class four lines rather than a
/// lifetime to explain, and it is why the view takes a source rather than an image:
/// the cache is the application's.
public final class DocumentAssets implements ImageSource {

    /// The one picture this application has, under the name both samples write.
    private static final String SHIPPED = "sea.png";

    private final Map<String, Image> decoded = new ConcurrentHashMap<>();

    @Override
    public @Nullable Image image(String src) {
        if (!SHIPPED.equals(src)) {
            // Not a failure: a document may name anything, and a reader sees the alt
            // text the author wrote for exactly this case.
            return null;
        }
        return decoded.computeIfAbsent(src, name -> decode());
    }

    /// The shipped picture, which is the Canvas screen's sample read a second way.
    ///
    /// The same file rather than a second asset, because the point is the plumbing
    /// and not the photograph — and a showcase that shipped two pictures to make one
    /// point would be one file heavier for nothing.
    private static Image decode() {
        try (var bytes = DocumentAssets.class.getResourceAsStream("canvas-sample.png")) {
            if (bytes == null) {
                throw new IllegalStateException("canvas-sample.png is not on the classpath beside DocumentAssets");
            }
            return Image.decode(bytes.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read canvas-sample.png", e);
        }
    }
}
