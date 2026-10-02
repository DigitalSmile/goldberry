/// The event loop and the events a backend delivers to it.
///
/// `EventLoop` drives a backend on the one UI thread, `UiExecutor` is the way
/// onto that thread from anywhere else, and `BackendEvent` is what a backend
/// hands an `EventSink` once it has translated the platform's own event.
/// Exported to every module, because an application's background work comes
/// back through here.
///
/// Null-marked: a parameter or return is non-null unless it says `@Nullable`.
///
/// Read more: [Windows, popups and the host](https://goldberry.dev/docs/guide/windows.html#threads).
@NullMarked
package dev.goldberry.render.event;

import org.jspecify.annotations.NullMarked;
