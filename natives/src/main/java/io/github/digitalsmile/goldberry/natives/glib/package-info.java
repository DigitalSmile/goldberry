/// GLib's logging hooks, and nothing else of GLib.
///
/// Two functions out of a library with thousands, bound for one purpose: so that
/// a message a desktop library raises about itself reaches the application's log
/// rather than its stderr (ADR-0443).
///
/// This package is unlike every other binding package in the module in three
/// ways, and all three follow from GLib being the **system's** library rather
/// than one the superbuild produced:
///
/// 1. It is found by `dlopen` on a soname, not by a symbol on
///    `exports/goldberry.symbols` — see [GlibLibrary].
/// 2. Its absence is ordinary. Linux without a desktop, macOS and Windows have
///    no GLib, and nothing here is reached on those platforms.
/// 3. Nothing in it is called by the toolkit's own code paths. The only two
///    callers are the SDL backend, immediately before it does the two things
///    that load GLib: creating a tray, and opening a page.
///
/// Nothing here allocates, owns or frees foreign memory. What crosses is a
/// pointer to a string GLib owns for the length of one call, read into a Java
/// `String` and handed on.
package io.github.digitalsmile.goldberry.natives.glib;
