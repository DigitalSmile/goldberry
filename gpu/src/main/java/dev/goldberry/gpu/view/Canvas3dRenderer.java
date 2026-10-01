package dev.goldberry.gpu.view;

import dev.goldberry.gpu.GpuDevice;
import dev.goldberry.gpu.GpuFrame;
import dev.goldberry.render.model.PhysicalSize;

/// What draws a `canvas3d`: an application's own renderer, on the GPU
/// (`docs/gpu-plan.md`, phase 5; ADR-0482).
///
/// ```java
/// final class Cube implements Canvas3dRenderer {
///     private GraphicsPipeline pipeline;
///     private GpuBuffer vertices;
///
///     public void init(GpuDevice device) {
///         pipeline = device.createPipeline(...);
///         vertices = device.createBuffer(BufferUsage.VERTEX, ...);
///     }
///
///     public void render(GpuFrame frame, Canvas3dTarget target) {
///         frame.renderPass(target.colour(), Load.clear(0, 0, 0, 1), target.clearDepth(), pass -> { ... });
///     }
///
///     public void dispose() {
///         pipeline.close();
///         vertices.close();
///     }
/// }
/// ```
///
/// **The lifecycle.** [#init] once, before the first render; [#resize] before
/// the first render and whenever the canvas's size in physical pixels changes;
/// [#render] for each frame the canvas is drawn; [#dispose] when it leaves the
/// tree. A renderer is [#dispose]d and [#init]ed again if the device it was
/// made on is replaced. Every call is on the UI thread, which is the device's.
///
/// **What it is drawn into** is the toolkit's: a colour texture at the canvas's
/// size, and a depth texture beside it when the canvas asks for one. The
/// renderer covers every pixel of the colour texture -- a render pass that
/// clears is the usual way -- and its picture is opaque, as every GPU layer is
/// (ADR-0481).
///
/// **When it is drawn** is the canvas's choice: on every frame while it is
/// `continuous`, and otherwise when its [Canvas3d#revision()] changes and when its
/// size changes. Between those the last picture is shown again.
public interface Canvas3dRenderer {

    /// Makes what the renderer draws with, on `device`: pipelines, buffers,
    /// textures. Called once before the first [#render].
    ///
    /// @param device the window's device, which everything is made on
    void init(GpuDevice device);

    /// The canvas is `size` physical pixels now: for a projection's aspect, or
    /// for textures of the renderer's own at the canvas's size. Called after
    /// [#init] and before the first [#render], and again whenever the size
    /// changes. Nothing by default.
    default void resize(PhysicalSize size) {}

    /// Draws the canvas's picture into `target`, recording into `frame`. The
    /// frame is submitted by the toolkit; the renderer does not wait for the
    /// GPU.
    void render(GpuFrame frame, Canvas3dTarget target);

    /// Releases what [#init] made. Called once the canvas has left the tree, or
    /// before [#init] on a new device; nothing is rendered afterwards unless
    /// [#init] is called again.
    void dispose();
}
