package dev.goldberry.gpu.indirect;

import java.nio.ByteBuffer;

/// The workgroups of one dispatch, as `ComputePass.dispatchIndirect` reads
/// them: SDL's `SDL_GPUIndirectDispatchCommand`, three words.
///
/// A count of 0 dispatches nothing.
///
/// @param groupsX workgroups along x
/// @param groupsY workgroups along y
/// @param groupsZ workgroups along z
public record DispatchCommand(int groupsX, int groupsY, int groupsZ) implements IndirectCommand {

    /// Bytes it takes in a buffer.
    public static final int BYTES = 3 * Integer.BYTES;

    /// Checks no count is negative.
    public DispatchCommand {
        if (groupsX < 0 || groupsY < 0 || groupsZ < 0) {
            throw new IllegalArgumentException("a dispatch of " + groupsX + "x" + groupsY + "x" + groupsZ);
        }
    }

    /// The command read from `source`'s position, which moves past it.
    public static DispatchCommand readFrom(ByteBuffer source) {
        return new DispatchCommand(source.getInt(), source.getInt(), source.getInt());
    }

    @Override
    public int bytes() {
        return BYTES;
    }

    @Override
    public void writeTo(ByteBuffer target) {
        target.putInt(groupsX).putInt(groupsY).putInt(groupsZ);
    }
}
