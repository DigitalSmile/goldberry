/// The commands an indirect draw or dispatch reads from a buffer, laid out as
/// the GPU reads them.
///
/// An indirect draw takes its counts from a [dev.goldberry.gpu.GpuBuffer] made
/// with [dev.goldberry.gpu.BufferUsage#INDIRECT] rather than from its
/// arguments, so a compute pass can decide what is drawn: culling instances
/// against the view, or emitting as many particles as are alive, with nothing
/// read back to the CPU. The records here are the three commands, for filling
/// such a buffer from Java or for reading one back in a test; a shader writing
/// them writes the same words in the same order.
///
/// ```java
/// var commands = IndirectCommand.pack(List.of(
///         new DrawCommand(36, visibleCubes, 0, 0),
///         new DrawCommand(6, 1, 36, 0)));
/// frame.copyPass(copy -> copy.upload(indirect, 0, commands));
/// frame.renderPass(target, Load.clear(0, 0, 0, 1), pass -> {
///     pass.bindPipeline(pipeline);
///     pass.bindVertexBuffer(0, vertices);
///     pass.drawIndirect(indirect, 0, 2);
/// });
/// ```
///
/// Exported.
///
/// Read more: [The GPU canvas](https://goldberry.dev/docs/components/gpu.html).
@org.jspecify.annotations.NullMarked
package dev.goldberry.gpu.indirect;
