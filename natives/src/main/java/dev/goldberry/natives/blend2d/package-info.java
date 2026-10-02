/// Blend2D, the rasterizer: owning wrappers over its images, rendering contexts,
/// paths, gradients and fonts, and the package-private binding classes behind them.
///
/// Exported to `:core` alone. An application draws through `:core`'s paint types and
/// never names one of these. Each wrapper owns its handle, is confined to the thread
/// that created it and must be closed; the binding class beside it is the only way
/// to its calls. Blend2D draws into pixels somebody else owns, except in a decode
/// and a resample, where only Blend2D can allocate.
///
/// Marked for NullAway, so a parameter that may be null says so on its signature.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
@NullMarked
package dev.goldberry.natives.blend2d;

import org.jspecify.annotations.NullMarked;
