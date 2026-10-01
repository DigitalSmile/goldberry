/// The compositor: a window claimed from the CPU path, presented through a
/// swapchain with its GPU layers on top, or read back into the frame where a
/// window cannot be claimed (`docs/gpu-plan.md`, phases 3 and 4).
///
/// [dev.goldberry.gpu.composite.SdlCompositor] is `:core`'s
/// `render.composite.Compositor`, found by `ServiceLoader`. Not exported. It was
/// part of `…gpu.render` until ADR-0496, which keeps the shaders and the quad
/// arithmetic that this package draws with.
///
/// Marked for NullAway from its first commit (`docs/testing.md` §2).
@NullMarked
package dev.goldberry.gpu.composite;

import org.jspecify.annotations.NullMarked;
