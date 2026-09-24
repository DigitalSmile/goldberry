/// The seam between the sdl3 backend and `:gpu`'s compositor
/// (`docs/gpu-plan.md`, phase 3, and ADR-0479).
///
/// `:core` cannot depend on `:gpu`, which depends on it, and a composited
/// window needs both: the window, its frame loop and its pacing are `:core`'s,
/// and the device, the shaders and the draws are `:gpu`'s. So `:core` declares
/// what it needs here and finds an implementation with `ServiceLoader`. With no
/// `:gpu` on the module path there is none, and every window presents on the
/// CPU exactly as before.
///
/// **Exported to `:gpu` alone.** The signatures name `:natives`' window handle,
/// which an application has no business holding here, and nothing in this
/// package is for applications: the public GPU API is `:gpu`'s.
@org.jspecify.annotations.NullMarked
package io.github.digitalsmile.goldberry.render.composite;
