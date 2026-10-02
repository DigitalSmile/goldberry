/// HarfBuzz's enumerations — text direction and memory mode. They are C constants
/// that touch no foreign memory, each checked against the compiled library.
///
/// HarfBuzz numbers some of its enums with deliberate gaps, so every value is
/// declared rather than counted from an ordinal. Exported to `:core` alone, with the
/// wrappers in `…natives.harfbuzz`.
///
/// Marked for NullAway, so a parameter that may be null says so on its signature.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
@NullMarked
package dev.goldberry.natives.harfbuzz.enums;

import org.jspecify.annotations.NullMarked;
