/// `canvas3d`: a box the GPU draws into, with an application's renderer.
///
/// - [Canvas3d] is the widget, `canvas3d` in markup: a leaf sized by its
///   stylesheet, drawn on every frame or on demand, with a depth target if it
///   asks for one. It is a GPU layer: opaque, in paint order, and
///   composited or read back as its window presents.
/// - [Canvas3dRenderer] is what the application writes: `init` on the window's
///   device, `resize`, `render` into a [Canvas3dTarget], and `dispose`, all on
///   the UI thread, with the GPU API of `dev.goldberry.gpu`.
///
/// Where there is no GPU the canvas fills its box with
/// `--gb-canvas3d-unavailable` and, in a running window, says why.
///
/// Read more: [`canvas3d`](https://goldberry.dev/docs/components/gpu.html#canvas3d).
@org.jspecify.annotations.NullMarked
package dev.goldberry.gpu.view;
