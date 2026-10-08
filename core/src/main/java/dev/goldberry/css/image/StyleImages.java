package dev.goldberry.css.image;

import java.util.concurrent.CompletableFuture;

import org.jspecify.annotations.Nullable;

import dev.goldberry.bind.Subscription;
import dev.goldberry.image.Image;
import dev.goldberry.image.ImageAddress;

/// Where the pictures a stylesheet names come from: the service that turns an
/// address into decoded pixels.
///
/// ```css
/// .panel { background-image: url("classpath:/ui/leather.png") }
/// ```
///
/// A stylesheet is resolved in this module and the toolkit's image cache lives
/// in the widget catalogue, which this module cannot name. So the cache
/// announces itself through a service: the catalogue's provider is the one an
/// application gets, and a CSS picture and an `image` showing the same file
/// share one decode. With no provider on the module path a small one in this
/// module reads the file itself.
///
/// ## Asynchronous, and drawn when it arrives
///
/// [#resolve] answers the picture when it has been decoded and null while it is
/// on its way. The first frame that asks starts the load; when it finishes,
/// [#generation()] moves on and every [#onArrival] listener runs, which is how a
/// window learns to paint again. A layer whose picture is not there yet draws
/// nothing, so a box shows its colour and its gradients first and its picture a
/// frame later. Outside a running toolkit — a test, an offscreen render — the
/// load happens in the caller and the first frame already has it.
///
/// A picture that cannot be read is said once and not asked for again.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#backgrounds-and-gradients).
public interface StyleImages {

    /// Starts loading `address`, or answers what is already loaded.
    ///
    /// The future completes on the UI thread while the toolkit is running, or is
    /// already complete. A region of a picture is cut from the whole picture's
    /// one decode.
    CompletableFuture<Image> load(ImageAddress address);

    /// The decoded picture for `url` at a display of `scale`, or null while it is
    /// loading or when it cannot be.
    ///
    /// Above 100% the `@2x` variant is asked for first, and the 1x picture is
    /// drawn when there is none.
    static @Nullable StyleImage resolve(CssImage.Url url, double scale) {
        return Registry.resolve(url, scale);
    }

    /// How many pictures have arrived since the process started: a number that
    /// moves whenever one more can be drawn.
    ///
    /// A retained render tree remembers the number it last painted at, so a box
    /// that names a picture is painted again when it changes, although the box
    /// itself has not.
    static long generation() {
        return Registry.generation();
    }

    /// Runs `listener` whenever a picture arrives, until the answer is closed.
    ///
    /// On the UI thread while the toolkit is running. What a window asks for
    /// another frame with.
    static Subscription onArrival(Runnable listener) {
        return Registry.onArrival(listener);
    }

    /// The provider in use: the first on the module path, or the one this module
    /// carries.
    static StyleImages provider() {
        return Registry.provider();
    }
}
