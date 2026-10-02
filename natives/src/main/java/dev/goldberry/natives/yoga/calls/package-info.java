/// The downcall holders for Yoga: its config, its nodes, their style setters and
/// layout results, and the measure probe — one static final handle per function.
///
/// **Not exported**, like every `…calls` package. The package-private binding
/// class in `…natives.yoga` is their only caller.
///
/// Marked for NullAway, so a parameter that may be null says so on its signature.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
@NullMarked
package dev.goldberry.natives.yoga.calls;

import org.jspecify.annotations.NullMarked;
