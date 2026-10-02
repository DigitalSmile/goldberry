/// libwebp: decoding a WebP still or animation into pixels Java owns, and encoding
/// one.
///
/// Every call copies into Java arrays and frees libwebp's buffer before it returns,
/// so nothing here has a lifetime to hand over. Exported to `:core` alone: an
/// application calls `Image.decode`, which names no type of this module.
///
/// Marked for NullAway, so a parameter that may be null says so on its signature.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
@NullMarked
package dev.goldberry.natives.webp;

import org.jspecify.annotations.NullMarked;
