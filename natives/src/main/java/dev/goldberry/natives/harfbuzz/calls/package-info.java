/// The downcall holders for HarfBuzz: its buffer, its blob, face and font, the
/// shaper with its tag lookups, and the version query — one static final handle per
/// function.
///
/// **Not exported**, like every `…calls` package. The package-private binding
/// class in `…natives.harfbuzz` is their only caller.
///
/// Marked for NullAway, so a parameter that may be null says so on its signature.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
@NullMarked
package dev.goldberry.natives.harfbuzz.calls;

import org.jspecify.annotations.NullMarked;
