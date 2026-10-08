package dev.goldberry.widgets.core.image;

import java.util.concurrent.CompletableFuture;

import dev.goldberry.Goldberry;
import dev.goldberry.image.Image;

/// Turns an [ImageSource] into pixels, and says when.
///
/// **The future completes on the UI thread**, or is already complete. That is
/// the whole contract an [ImageView] relies on: it reads the result where it can
/// set its state, with no hand-off of its own; there is one UI thread, and the
/// decoding runs on a virtual thread behind it.
///
/// [#shared()] is what a view uses unless told otherwise, and an application
/// replaces it for one reason — to put its own policy in front, an HTTP cache or
/// a thumbnail service — by handing a view its own loader.
///
/// Read more: [Canvas, images and QR codes](https://goldberry.dev/docs/components/drawing.html#image).
@FunctionalInterface
public interface ImageLoader {

    /// Starts loading `source`, or answers what is already loaded.
    CompletableFuture<Image> load(ImageSource source);

    /// The toolkit's loader: one bounded cache for the process, decodes on a
    /// virtual thread while the toolkit is running, and in the caller when it is
    /// not — a test, or an offscreen render with no event loop, where there is no
    /// UI thread to come back to and a picture that arrived "later" would never be
    /// photographed.
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
    /// to, the caller otherwise. A [ImageSource.Decoded] source never leaves, and
    /// neither does a region of one: cutting pixels already in hand is a copy, not
    /// a decode.
    static CompletableFuture<Image> run(ImageSource source) {
        switch (source) {
            case ImageSource.Decoded(var image) -> {
                return CompletableFuture.completedFuture(image);
            }
            case ImageSource.Region(ImageSource.Decoded(var image), var rect) -> {
                try {
                    return CompletableFuture.completedFuture(image.cropped(rect));
                } catch (IllegalArgumentException e) {
                    return CompletableFuture.failedFuture(e);
                }
            }
            default -> {
                // Read and decoded below.
            }
        }
        if (Goldberry.isUiThread()) {
            return Goldberry.async(source::load);
        }
        return immediate().load(source);
    }
}
