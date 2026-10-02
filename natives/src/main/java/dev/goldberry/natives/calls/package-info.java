/// The downcall holders for `libgoldberry`'s own exports — the shim's functions,
/// which belong to none of the wrapped libraries. One record, one static final
/// handle per function.
///
/// **Not exported**, like every `…calls` package in this module: a `call` takes and
/// returns raw addresses, and raw foreign memory never leaves the module. This is
/// the smallest example of the shape every holder has.
///
/// Marked for NullAway, so a parameter that may be null says so on its signature.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
@NullMarked
package dev.goldberry.natives.calls;

import org.jspecify.annotations.NullMarked;
