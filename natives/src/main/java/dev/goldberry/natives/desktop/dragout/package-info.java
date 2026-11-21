/// The platform's own drag source, for a drag that leaves the application.
///
/// SDL is a drop target on every platform and a drag source on none, so each
/// platform's source is written here, under the SDL backend and for this one
/// capability: XDND on X11 over SDL's own `Display`. A [DragSourceListener]
/// hears where the pointer is over the application's own windows and how the
/// drag ended; [DragData] answers the types and bytes a target asks for.
///
/// Not exported to applications: `:core`'s sdl3 backend is the only caller.
///
/// `@NullMarked`, which puts this package under NullAway.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
@NullMarked
package dev.goldberry.natives.desktop.dragout;

import org.jspecify.annotations.NullMarked;
