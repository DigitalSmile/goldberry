/// The GPU screen's scene: a lit cube, written as an application writes a
/// `canvas3d` renderer, and the few matrices it needs.
///
/// Its shaders are the showcase's own, compiled by `:gpu:compileShaders` into this
/// package's resources.
///
/// Marked for NullAway, as every package in the repository is.
///
/// Read more: [The renderer](https://goldberry.dev/docs/components/gpu.html#the-renderer).
@NullMarked
package dev.goldberry.example.gpu;

import org.jspecify.annotations.NullMarked;
