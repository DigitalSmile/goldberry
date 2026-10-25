package dev.goldberry.gpu.indirect;

import java.nio.ByteBuffer;

/// The counts of one indexed draw, as `RenderPass.drawIndexedIndirect` reads
/// them: SDL's `SDL_GPUIndexedIndirectDrawCommand`, five words.
///
/// @param indices       how many indices each instance has
/// @param instances     how many instances
/// @param firstIndex    the index the draw starts at in the bound index buffer
/// @param vertexOffset  what is added to each index before it picks a vertex;
///                      may be negative
/// @param firstInstance the instance it starts at, as [DrawCommand] says
public record IndexedDrawCommand(int indices, int instances, int firstIndex, int vertexOffset, int firstInstance)
        implements IndirectCommand {

    /// Bytes from one to the next in a buffer.
    public static final int BYTES = 5 * Integer.BYTES;

    /// Checks no count is negative.
    public IndexedDrawCommand {
        if (indices < 0 || instances < 0 || firstIndex < 0 || firstInstance < 0) {
            throw new IllegalArgumentException("an indexed draw of " + indices + " indices, " + instances
                    + " instances from " + firstIndex + ", " + firstInstance);
        }
    }

    /// The command read from `source`'s position, which moves past it.
    public static IndexedDrawCommand readFrom(ByteBuffer source) {
        return new IndexedDrawCommand(
                source.getInt(), source.getInt(), source.getInt(), source.getInt(), source.getInt());
    }

    @Override
    public int bytes() {
        return BYTES;
    }

    @Override
    public void writeTo(ByteBuffer target) {
        target.putInt(indices)
                .putInt(instances)
                .putInt(firstIndex)
                .putInt(vertexOffset)
                .putInt(firstInstance);
    }
}
