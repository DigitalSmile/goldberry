/// The downcall holders for Blend2D: one record per object — context, font,
/// gradient, image, path, and the runtime query — with one static final handle per
/// function.
///
/// **Not exported**, like every `…calls` package. The package-private binding
/// classes in `…natives.blend2d` are their only callers.
///
/// Marked for NullAway, so a parameter that may be null says so on its signature.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
@NullMarked
package dev.goldberry.natives.blend2d.calls;

import org.jspecify.annotations.NullMarked;
