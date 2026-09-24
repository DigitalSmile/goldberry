/// The GPU API: a small, safe layer over SDL's GPU API for `canvas3d` renderers
/// and GPU layers (`docs/gpu-plan.md`, phase 2). No SDL struct, handle or
/// `MemorySegment` appears in it.
///
/// ## The pieces
///
/// - [GpuDevice]: the one GPU of the process, handed out by the backend. It
///   makes everything else.
/// - Resources, each a [GpuResource] closed when done with: [GpuTexture],
///   [GpuBuffer], [GpuSampler], [Shader], [GraphicsPipeline]. Each is made from
///   a record saying what it is -- [TextureSpec], [SamplerSpec], [ShaderCode],
///   [PipelineSpec] -- and the enums those name.
/// - [GpuFrame]: one frame's command buffer. Its [CopyPass]es upload and its
///   [RenderPass]es draw, each open only while the lambda given for it runs;
///   [GpuFrame#readback] brings pixels back as a [Readback].
/// - [GpuLayer]: pixels the GPU draws into a window, placed by a painter in
///   paint order among what the CPU paints, and rendered with the window's
///   device into a texture the toolkit composites or reads back (ADR-0481).
///
/// ## A frame
///
/// ```java
/// var vertex = device.createShader(ShaderCode.load(
///         ShaderStage.VERTEX, "/shaders/cube.vert", 0, 1, MyRenderer.class::getResourceAsStream));
/// var fragment = device.createShader(ShaderCode.load(
///         ShaderStage.FRAGMENT, "/shaders/cube.frag", 0, 0, MyRenderer.class::getResourceAsStream));
/// var pipeline = device.createPipeline(PipelineSpec.builder(vertex, fragment, TextureFormat.B8G8R8A8_UNORM)
///         .vertexBuffer(VertexBufferLayout.perVertex(0, 28,
///                 VertexAttribute.of(0, VertexFormat.FLOAT3, 0),
///                 VertexAttribute.of(1, VertexFormat.FLOAT4, 12)))
///         .cull(CullMode.BACK, FrontFace.COUNTER_CLOCKWISE)
///         .depthTest(DepthTest.less(TextureFormat.D16_UNORM))
///         .build());
///
/// try (var frame = device.beginFrame()) {
///     frame.copyPass(copy -> copy.upload(vertices, 0, mesh));
///     frame.renderPass(colour, Load.clear(0, 0, 0, 1), DepthTarget.clear(depth), pass -> {
///         pass.bindPipeline(pipeline);
///         pass.bindVertexBuffer(0, vertices);
///         pass.pushVertexUniforms(0, modelViewProjection);
///         pass.draw(36);
///     });
///     frame.submit();
/// }
/// ```
///
/// ## Rules
///
/// - **One thread.** Everything belongs to the device's thread, the UI thread;
///   any other gets [WrongThreadException].
/// - **Checked in Java.** A closed resource, another device's, a region outside
///   its texture, a draw with nothing bound: `IllegalArgumentException` or
///   `IllegalStateException`, before the driver sees it. What only the driver
///   can refuse is a [GpuException].
/// - **Nothing waits but [Readback#await].** Uploads are staged and cycled, and
///   submitting does not wait for the GPU.
/// - **Premultiplied colour.** What the toolkit composites is premultiplied, so
///   [BlendMode#PREMULTIPLIED_OVER] and [Readback#awaitPixels] assume it.
/// - **Coordinates** are pixels from a target's top left; clip space is SDL's,
///   y up, as [RenderPass] notes.
///
/// ## Shaders
///
/// A device takes one family of bytecode: SPIR-V on Vulkan, MSL on Metal, DXIL
/// on Direct3D 12. [ShaderCode] carries as many as were compiled, and
/// [GpuDevice#createShader] picks the one the device takes. The toolkit writes
/// its own in HLSL and compiles them with DXC and SPIRV-Cross
/// (`docs/gpu-plan.md`, D7); `gpu/build.gradle`'s `compileShaders` is the recipe.
@org.jspecify.annotations.NullMarked
package io.github.digitalsmile.goldberry.gpu;
