/// The ground every binding in this module stands on: finding and loading
/// `libgoldberry`, the one linker all downcalls share, and the upcall signatures a
/// native image has to be told about before a stub is made.
///
/// **Not exported.** `NativeLibrary` hands out a `SymbolLookup`, and a foreign type
/// in this module's public surface would be the boundary leaking by another name.
/// What a holder is, and why the holders live in `…calls` packages of their own, is
/// told once on `Downcalls`.
///
/// Marked for NullAway, so a parameter that may be null says so on its signature.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
@NullMarked
package dev.goldberry.natives;

import org.jspecify.annotations.NullMarked;
