/// What the GPU paths draw with: the toolkit's own shaders, loaded in the
/// format the device takes, and the arithmetic that places a quad.
///
/// Not exported. The composited window (`docs/gpu-plan.md`, phase 3), GPU
/// layers (phase 4), `canvas3d` (phase 5) and video (phase 6) are its callers.
@org.jspecify.annotations.NullMarked
package dev.goldberry.gpu.render;
