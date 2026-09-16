package io.github.digitalsmile.goldberry.input.drop;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import io.github.digitalsmile.goldberry.render.model.LogicalPoint;

/// Files dropped on a window, and where — `docs/gaps.md` G35b, [ADR-0330].
///
/// ## One event per gesture, and the position is half of it
///
/// A desktop reports a drop as a run of events: a beginning, a position that
/// updates while the pointer moves over the window, one event per file, and an
/// end. Every application that wants to do something with the drop has to
/// reassemble that into "these files, there" — so the toolkit does it once,
/// here, rather than exporting the run and letting each application get the
/// bookkeeping slightly different.
///
/// **Where** is the part worth insisting on. A board needs to know where
/// something was dropped and not merely that it was: the whole gesture is
/// "put this picture *here*". The platform carries the position already; a drop
/// event without it would make the toolkit the reason an application cannot use
/// it.
///
/// ## The coordinates
///
/// [LogicalPoint], window-relative, in the same space every pointer event and
/// every layout is in — so it can be hit-tested against the last painted frame
/// exactly as a click is.
///
/// ## The paths
///
/// Always at least one, in the order the platform reported them. They are
/// [Path]s and nothing has been read: a drop is a *name*, and whether the file
/// exists, can be opened, or is what it claims to be are all questions for
/// whoever decided to accept it.
///
/// @param paths the files, in the order they arrived; never empty
/// @param at    where in the window the drop landed, in logical pixels
public record FileDrop(List<Path> paths, LogicalPoint at) {

    public FileDrop {
        Objects.requireNonNull(paths, "paths");
        Objects.requireNonNull(at, "at");
        paths = List.copyOf(paths);
        if (paths.isEmpty()) {
            throw new IllegalArgumentException(
                    "a file drop with no files in it is not a drop; the toolkit raises one only when"
                            + " at least one path arrived");
        }
    }

    /// The first path — what an application that takes one file at a time wants,
    /// and the common case by a long way.
    public Path first() {
        return paths.getFirst();
    }

    /// How many files were dropped.
    public int count() {
        return paths.size();
    }
}
