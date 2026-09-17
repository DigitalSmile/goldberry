package io.github.digitalsmile.goldberry.widgets.core.image;

import java.util.Objects;

import io.github.digitalsmile.goldberry.image.Image;

/// Where one [ImageView]'s load stands: waiting, drawn, or refused.
sealed interface ImageLoad {

    /// The pixels are on their way. The box is drawn with `image.loading`, which
    /// is §1's "placeholder fill until loaded".
    record Loading() implements ImageLoad {}

    /// The pixels, and the scale of the variant they came from.
    ///
    /// @param image the decoded raster
    /// @param scale the display scale it was drawn for, so its natural size is
    ///              its pixels over this
    record Ready(Image image, double scale) implements ImageLoad {

        public Ready {
            Objects.requireNonNull(image, "image");
        }

        double naturalWidth() {
            return image.width() / scale;
        }

        double naturalHeight() {
            return image.height() / scale;
        }
    }

    /// The load failed: a missing file, bytes that are not an image, a supplier
    /// that threw. The box is drawn with `image.error` and the alt text in it.
    ///
    /// @param reason what went wrong, for the log
    record Failed(String reason) implements ImageLoad {}
}
