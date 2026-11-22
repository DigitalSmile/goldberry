package dev.goldberry.vector.view;

import java.util.Objects;

/// Where one [SvgView]'s document stands: on its way, read, or refused.
sealed interface SvgLoad {

    /// The document is on its way: the box is drawn with `svg-view.loading`.
    record Loading() implements SvgLoad {}

    /// The document, read and checked.
    ///
    /// @param document its bytes, size and drawings
    record Ready(SvgDocument document) implements SvgLoad {
        public Ready {
            Objects.requireNonNull(document, "document");
        }
    }

    /// It could not be read, or is not a document: the box is drawn with
    /// `svg-view.error`, the icon and the alt text in it.
    ///
    /// @param reason what went wrong, for the log
    record Failed(String reason) implements SvgLoad {}
}
