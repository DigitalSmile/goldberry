/// The GPU screen's ray-traced room: a scene of a few solids on a checkered
/// floor, drawn by a `canvas3d` renderer and lit by the hardware ray tracer
/// when it is asked to, with the settings a game's options screen changes.
///
/// Its shaders are the showcase's own, compiled by `:gpu:compileShaders` beside
/// the cube's.
///
/// Marked for NullAway, as every package in the repository is.
///
/// Read more: [Ray tracing](https://goldberry.dev/docs/components/gpu.html#the-renderer).
@NullMarked
package dev.goldberry.example.gpu.scene;

import org.jspecify.annotations.NullMarked;
