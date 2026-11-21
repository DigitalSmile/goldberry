/// The backend's half of dragging out of the application: the request a
/// window is asked to start, the session it reports to, and how the platform
/// said the drag ended.
///
/// A backend that cannot start a platform drag refuses
/// (`BackendWindow.startDrag` answers false by default), and the drag stays an
/// in-app drag, exactly as it was before a drag could leave. One that can
/// starts the platform's drag and reports to the [DragSession] on the UI
/// thread, through to one [DragEnd].
///
/// [OfferedData] turns an offer into MIME types and bytes, the names X11,
/// Wayland and macOS ask in. Windows names its formats differently and reads
/// the same offer through it as well.
///
/// `@NullMarked`, which puts this package under NullAway.
///
/// Read more: [Input and focus](https://goldberry.dev/docs/guide/input.html#dropped-files-and-text).
@NullMarked
package dev.goldberry.render.dragout;

import org.jspecify.annotations.NullMarked;
