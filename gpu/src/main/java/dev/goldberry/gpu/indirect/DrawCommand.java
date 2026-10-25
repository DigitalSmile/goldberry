package dev.goldberry.gpu.indirect;

import java.nio.ByteBuffer;

/// The counts of one draw, as `RenderPass.drawIndirect` reads them: SDL's
/// `SDL_GPUIndirectDrawCommand`, four words.
///
/// A count of 0 is a draw of nothing, which is what a compute pass that culled
/// everything writes.
///
/// @param vertices      how many vertices each instance has
/// @param instances     how many instances
/// @param firstVertex   the vertex the draw starts at
/// @param firstInstance the instance it starts at. Other than 0 needs
///                      `drawIndirectFirstInstance` on Vulkan, which not every
///                      driver has
public record DrawCommand(int vertices, int instances, int firstVertex, int firstInstance) implements IndirectCommand {

    /// Bytes from one to the next in a buffer.
    public static final int BYTES = 4 * Integer.BYTES;

    /// Checks no count is negative.
    public DrawCommand {
        if (vertices < 0 || instances < 0 || firstVertex < 0 || firstInstance < 0) {
            throw new IllegalArgumentException("a draw of " + vertices + " vertices, " + instances + " instances from "
                    + firstVertex + ", " + firstInstance);
        }
    }

    /// The command read from `source`'s position, which moves past it.
    public static DrawCommand readFrom(ByteBuffer source) {
        return new DrawCommand(source.getInt(), source.getInt(), source.getInt(), source.getInt());
    }

    @Override
    public int bytes() {
        return BYTES;
    }

    @Override
    public void writeTo(ByteBuffer target) {
        target.putInt(vertices).putInt(instances).putInt(firstVertex).putInt(firstInstance);
    }
}
