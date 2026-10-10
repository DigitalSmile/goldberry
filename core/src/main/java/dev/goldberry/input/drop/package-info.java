/// Things dropped: files or text dropped on a window from outside it, each
/// delivered as one assembled value with the window-relative point it landed
/// on, and payloads dragged from one widget of the application onto another.
///
/// A desktop reports a drop as a run of events: a beginning, a moving position,
/// one event per file or line, and an end. The toolkit reassembles that run once,
/// here, rather than leave each application to get the bookkeeping slightly
/// wrong, and it keeps the position because a drop means "put this *here*". Its
/// own package rather than types in `input.event`, because a drop is a gesture
/// assembled from many platform events. Exported to applications.
///
/// A drag between widgets is the pointer router's: a widget carrying
/// `Attributes.draggable` is picked up, a widget carrying a [DropTarget] takes
/// it, and the [Drop] it is handed holds the payload and the point in its own
/// content box. It stays inside the process.
///
/// `@NullMarked`, which puts this package under NullAway.
///
/// Read more: [Input and focus](https://goldberry.dev/docs/guide/input.html#dropped-files-and-text).
@NullMarked
package dev.goldberry.input.drop;

import org.jspecify.annotations.NullMarked;
