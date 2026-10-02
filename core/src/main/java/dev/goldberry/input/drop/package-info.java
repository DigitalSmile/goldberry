/// Things dropped on a window — files or text — each delivered as one assembled
/// value with the window-relative point it landed on.
///
/// A desktop reports a drop as a run of events: a beginning, a moving position,
/// one event per file or line, and an end. The toolkit reassembles that run once,
/// here, rather than leave each application to get the bookkeeping slightly
/// wrong, and it keeps the position because a drop means "put this *here*". Its
/// own package rather than types in `input.event`, because a drop is a gesture
/// assembled from many platform events. Exported to applications.
///
/// `@NullMarked`, which puts this package under NullAway.
///
/// Read more: [Input and focus](https://goldberry.dev/docs/guide/input.html#dropped-files-and-text).
@NullMarked
package dev.goldberry.input.drop;

import org.jspecify.annotations.NullMarked;
