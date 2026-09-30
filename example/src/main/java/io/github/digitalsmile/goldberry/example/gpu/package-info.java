/// The GPU screen's scene: a lit cube, written as an application writes a
/// `canvas3d` renderer, and the few matrices it needs (`docs/gpu-plan.md`,
/// phase 5).
///
/// Its shaders are the showcase's own, compiled by `:gpu:compileShaders` into this
/// package's resources.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.example.gpu;

import org.jspecify.annotations.NullMarked;
