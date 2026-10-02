/// The Java half of Yoga's measure function: the constraint a leaf is measured
/// under, the size it answers with, and the interface a measurer implements.
///
/// A measured size is validated on construction, because it is one of the few values
/// native code will not check. Exported to `:core` alone; the native function
/// pointer made from a measurer lives in `…natives.yoga`.
///
/// Marked for NullAway, so a parameter that may be null says so on its signature.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
@NullMarked
package dev.goldberry.natives.yoga.measure;

import org.jspecify.annotations.NullMarked;
