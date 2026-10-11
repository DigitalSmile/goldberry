/// The few Xlib calls SDL leaves no other way to make.
///
/// Like `glib`, this binds a **system** library found by `dlopen` on a soname,
/// not a symbol on `exports/goldberry.symbols`. It is reached only after SDL has
/// reported an X11 display, so the library is always already in the process.
///
/// Not exported: the SDL wrappers in this module are its only callers, and what
/// it takes and returns are raw `Display*` pointers and visual ids.
///
/// Marked for NullAway, so a parameter that may be null says so on its signature.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
@NullMarked
package dev.goldberry.natives.x11;

import org.jspecify.annotations.NullMarked;
