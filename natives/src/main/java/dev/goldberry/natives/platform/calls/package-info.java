/// The downcall holder for `goldberry_platform_capabilities`, the one function that
/// reports what this build of the platform layer can do.
///
/// **Not exported**, like every `…calls` package. The capability query in
/// `…natives.platform` is the one caller.
///
/// Marked for NullAway, so a parameter that may be null says so on its signature.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
@NullMarked
package dev.goldberry.natives.platform.calls;

import org.jspecify.annotations.NullMarked;
