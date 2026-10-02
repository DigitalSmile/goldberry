/// md4c, the Markdown parser, as one call that hands back a list of events.
///
/// The events are encoded natively into one buffer and read once, so nothing
/// crosses the FFM boundary per block and md4c's detail structs are never modelled
/// in Java; what leaves is records that carry no foreign memory.
///
/// Exported to `:html` and to nobody else — `:core` cannot see it.
///
/// Marked for NullAway, so a parameter that may be null says so on its signature.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
@NullMarked
package dev.goldberry.natives.md4c;

import org.jspecify.annotations.NullMarked;
