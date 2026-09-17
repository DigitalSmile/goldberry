package io.github.digitalsmile.goldberry.widgets.core.image;

import java.util.concurrent.CompletableFuture;

import io.github.digitalsmile.goldberry.Goldberry;
import io.github.digitalsmile.goldberry.image.Image;

/// Turns an [ImageSource] into pixels, and says when.
///
/// **The future completes on the UI thread**, or is already complete. That is
/// the whole contract an [ImageView] relies on: it reads the result where it can
/// set its state, with no hand-off of its own (ADR-0020).
///
/// [#shared()] is what a view uses unless told otherwise, and an application
/// replaces it for one reason — to put its own policy in front, an HTTP cache or
/// a thumbnail service — by handing a view its own loader.
@FunctionalInterface
public interface ImageLoader {

    /// Starts loading `source`, or answers what is already loaded.
    CompletableFuture<Image> load(ImageSource source);

    /// The toolkit's loader: one bounded cache for the process, decodes on a
    /// virtual thread while the toolkit is running, and in the caller when it is
    /// not — a test, or an offscreen render with no event loop, where there is no
    /// UI thread to come back to and a picture that arrived "later" would never be
    /// photographed (ADR-0358).
    static ImageLoader shared() {
        return ImageCache.SHARED;
    }

    /// A loader with no cache that decodes in the caller. For tests, and for a
    /// view whose pixels change under the same key.
    static ImageLoader immediate() {
        return source -> {
            try {
                return CompletableFuture.completedFuture(source.load());
            } catch (RuntimeException e) {
                return CompletableFuture.failedFuture(e);
            }
        };
    }

    /// Where a load runs: a virtual thread when there is a UI thread to come back
    /// to, the caller otherwise. A [ImageSource.Decoded] source never leaves.
    static CompletableFuture<Image> run(ImageSource source) {
        if (source instanceof ImageSource.Decoded(var image)) {
            return CompletableFuture.completedFuture(image);
        }
        if (Goldberry.isUiThread()) {
            return Goldberry.async(source::load);
        }
        return immediate().load(source);
    }
}
