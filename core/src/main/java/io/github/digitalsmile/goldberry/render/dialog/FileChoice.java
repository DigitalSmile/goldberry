package io.github.digitalsmile.goldberry.render.dialog;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// What a file dialog answered.
///
/// Three cases and no fourth, which is what `sealed` is for: a caller that
/// switches over this without a `default` stops compiling if a fourth is ever
/// added, rather than silently treating it as one of the three.
///
/// ```java
/// switch (choice) {
///     case FileChoice.Chosen(var paths, var filter) -> open(paths.getFirst());
///     case FileChoice.Cancelled ignored             -> { }
///     case FileChoice.Failed(var message)           -> toast(message);
/// }
/// ```
///
/// **Cancelled is not a failure** and the platforms say so with the same value —
/// SDL returns an empty list for both "the user pressed Escape" and "the user
/// chose nothing" — so an export that was called off leaves no error behind to
/// show. [Failed] is for a dialog that could not be put up or broke while it was.
public sealed interface FileChoice {

    /// The user picked at least one path.
    ///
    /// @param paths  what they picked, in the platform's order; never empty
    /// @param filter which filter was selected, when the platform reports one —
    ///               several do not, and a save dialog on Linux usually does not
    record Chosen(List<Path> paths, Optional<FileFilter> filter) implements FileChoice {

        public Chosen {
            paths = List.copyOf(Objects.requireNonNull(paths, "paths"));
            Objects.requireNonNull(filter, "filter");
            if (paths.isEmpty()) {
                throw new IllegalArgumentException("a choice of nothing is a Cancelled, not a Chosen");
            }
        }

        /// The first path, which is the only one unless the request allowed many.
        public Path path() {
            return paths.getFirst();
        }
    }

    /// The user closed the dialog without picking anything.
    record Cancelled() implements FileChoice {

        /// The only one there is — nothing distinguishes two cancellations.
        ///
        /// Private, and reached through [FileChoice#cancelled()], because a
        /// constant of a subtype *on the interface* is a class-initialization
        /// deadlock waiting to happen: initializing `FileChoice` would initialize
        /// `Cancelled`, which initializes `FileChoice`.
        private static final Cancelled INSTANCE = new Cancelled();
    }

    /// The dialog could not be shown, or failed while it was up.
    ///
    /// @param message the platform's own words, for a log or a toast
    record Failed(String message) implements FileChoice {

        public Failed {
            Objects.requireNonNull(message, "message");
        }
    }

    /// The one cancellation there is.
    static Cancelled cancelled() {
        return Cancelled.INSTANCE;
    }

    /// One path, and no filter reported.
    static Chosen of(Path path) {
        return new Chosen(List.of(path), Optional.empty());
    }

    /// Several paths, and no filter reported.
    static Chosen of(List<Path> paths) {
        return new Chosen(paths, Optional.empty());
    }

    /// A failure with the platform's message.
    static Failed failed(String message) {
        return new Failed(message);
    }

    /// The paths chosen, or an empty list for a cancel or a failure — for the
    /// caller that has nothing different to do about either.
    default List<Path> paths() {
        return this instanceof Chosen chosen ? chosen.paths() : List.of();
    }
}
