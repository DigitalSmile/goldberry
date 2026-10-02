/// The downcall holders for libwebp's own functions, bound directly with no C glue:
/// the decoder, the encoder and the animation reader.
///
/// **Not exported**, like every `…calls` package. The decoder in `…natives.webp` is
/// the one caller.
///
/// Marked for NullAway, so a parameter that may be null says so on its signature.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
@NullMarked
package dev.goldberry.natives.webp.calls;

import org.jspecify.annotations.NullMarked;
