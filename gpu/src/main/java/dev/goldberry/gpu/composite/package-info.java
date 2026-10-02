/// The compositor: a window claimed from the CPU path, presented through a
/// swapchain with its GPU layers on top, or read back into the frame where a
/// window cannot be claimed.
///
/// [dev.goldberry.gpu.composite.SdlCompositor] is `:core`'s
/// `render.composite.Compositor`, found by `ServiceLoader`. Not exported. The
/// shaders and the quad arithmetic this package draws with stay in
/// `…gpu.render`.
///
/// Marked for NullAway from its first commit.
///
/// Read more:
/// [What the module does to a window](https://goldberry.dev/docs/components/gpu.html#what-the-module-does-to-a-window).
@NullMarked
package dev.goldberry.gpu.composite;

import org.jspecify.annotations.NullMarked;
