/// Yoga, the flexbox layout engine: owning wrappers over its nodes and configs, the
/// values a layout pass reads back, and the measure callback through which Yoga asks
/// a leaf for its size.
///
/// Exported to `:core` alone. A node's `YGNodeRef` never leaves this package, and
/// the binding class behind the wrappers is package-private. The measure callback is
/// a struct-by-value upcall, the hardest crossing in the module, and the measure
/// probe is how it is proven correct from C.
///
/// Marked for NullAway, so a parameter that may be null says so on its signature.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
@NullMarked
package dev.goldberry.natives.yoga;

import org.jspecify.annotations.NullMarked;
