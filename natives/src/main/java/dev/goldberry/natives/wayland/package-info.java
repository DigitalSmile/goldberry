/// A minimal binding of libwayland-client, for speaking on SDL's own Wayland
/// connection where SDL does not.
///
/// [WaylandClient] opens the library by its soname, so it is the instance SDL
/// loaded. A [WaylandQueue] is an event queue of the toolkit's own on SDL's
/// `wl_display`, with its own registry, so that what is bound through it is
/// dispatched when the toolkit asks and never inside SDL's pump. Requests are
/// marshalled from a [WaylandArguments] array, and every event of every
/// interface arrives through one upcall, [WaylandDispatcher], as a
/// [WaylandEvent]. [Descriptors] writes the files a client hands the
/// compositor.
///
/// Not exported: the drag source in `natives.desktop.dragout` is the one user.
///
/// `@NullMarked`, which puts this package under NullAway.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
@NullMarked
package dev.goldberry.natives.wayland;

import org.jspecify.annotations.NullMarked;
