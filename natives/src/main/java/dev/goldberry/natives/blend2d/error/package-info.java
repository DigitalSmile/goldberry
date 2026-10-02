/// How a Blend2D failure reaches Java: a `BLResult` turned into an exception at the
/// boundary, so a failure cannot be dropped by forgetting to check a return value.
///
/// Blend2D exports no result-to-string function, so the enum of named result codes
/// is what gives a failure a name; an unnamed code still reports its hex value.
/// Exported to `:core` alone, with the wrappers.
///
/// Marked for NullAway, so a parameter that may be null says so on its signature.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
@NullMarked
package dev.goldberry.natives.blend2d.error;

import org.jspecify.annotations.NullMarked;
