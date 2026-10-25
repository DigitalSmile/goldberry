package dev.goldberry.gpu;

import java.util.Objects;

import dev.goldberry.natives.sdl.gpu.SdlGpuPipelineDescription;

/// A pipeline's stencil test, the same for front and back faces: the pass's
/// [stencil reference][RenderPass#setStencilReference] compared with a
/// sample's stencil, and what the sample's stencil becomes.
///
/// It needs a [DepthTest] of a format with a stencil
/// ([TextureFormat#D24_UNORM_S8_UINT] or [TextureFormat#D32_FLOAT_S8_UINT]),
/// and draws in a pass whose [DepthTarget] is of that format.
/// [DepthTest#off] is the one to pair with a stencil test that leaves depth
/// alone.
///
/// ```java
/// // Mark the portal's pixels with 1, then draw the far room only there.
/// var mark = PipelineSpec.builder(vertex, flat, TextureFormat.B8G8R8A8_UNORM)
///         .depthTest(DepthTest.off(TextureFormat.D32_FLOAT_S8_UINT))
///         .stencilTest(StencilTest.replace())
///         .build();
/// var inside = PipelineSpec.builder(vertex, lit, TextureFormat.B8G8R8A8_UNORM)
///         .depthTest(DepthTest.less(TextureFormat.D32_FLOAT_S8_UINT))
///         .stencilTest(StencilTest.equal())
///         .build();
/// ```
///
/// @param compare   how the reference is compared with the sample's stencil,
///                  both masked by `readMask`: `LESS` passes where the
///                  reference is less than the stencil
/// @param fail      what a sample that fails the stencil test does to it
/// @param pass      what a sample that passes both tests does to it
/// @param depthFail what a sample that passes the stencil test and fails the
///                  depth test does to it
/// @param readMask  the bits compared, 0 to 255
/// @param writeMask the bits written, 0 to 255
public record StencilTest(
        CompareOp compare, StencilOp fail, StencilOp pass, StencilOp depthFail, int readMask, int writeMask) {

    /// Every bit of the stencil.
    public static final int ALL_BITS = 0xFF;

    /// Checks nothing is null and the masks are bytes.
    public StencilTest {
        Objects.requireNonNull(compare, "compare");
        Objects.requireNonNull(fail, "fail");
        Objects.requireNonNull(pass, "pass");
        Objects.requireNonNull(depthFail, "depthFail");
        if (readMask < 0 || readMask > ALL_BITS || writeMask < 0 || writeMask > ALL_BITS) {
            throw new IllegalArgumentException("stencil masks " + readMask + " and " + writeMask + ", not 0 to 255");
        }
    }

    /// Every sample passes, and one that also passes depth writes the
    /// reference: marking where something is drawn.
    public static StencilTest replace() {
        return new StencilTest(CompareOp.ALWAYS, StencilOp.KEEP, StencilOp.REPLACE, StencilOp.KEEP, ALL_BITS, ALL_BITS);
    }

    /// Samples whose stencil is the reference pass, and nothing is written:
    /// drawing only where an earlier pipeline marked.
    public static StencilTest equal() {
        return new StencilTest(CompareOp.EQUAL, StencilOp.KEEP, StencilOp.KEEP, StencilOp.KEEP, ALL_BITS, 0);
    }

    /// Samples whose stencil is not the reference pass, and nothing is
    /// written: an outline drawn around what was marked.
    public static StencilTest notEqual() {
        return new StencilTest(CompareOp.NOT_EQUAL, StencilOp.KEEP, StencilOp.KEEP, StencilOp.KEEP, ALL_BITS, 0);
    }

    SdlGpuPipelineDescription.StencilTest sdl() {
        return new SdlGpuPipelineDescription.StencilTest(
                compare.sdl(), fail.sdl(), pass.sdl(), depthFail.sdl(), readMask, writeMask);
    }
}
