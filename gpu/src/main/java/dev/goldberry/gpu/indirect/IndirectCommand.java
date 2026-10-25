package dev.goldberry.gpu.indirect;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/// One command an indirect draw or dispatch reads: a run of 32-bit words, in
/// the order the GPU reads them.
public sealed interface IndirectCommand permits DrawCommand, IndexedDrawCommand, DispatchCommand {

    /// How many bytes it takes in a buffer, which is also how far apart two of
    /// a kind are.
    int bytes();

    /// Writes its words at `target`'s position, in `target`'s byte order,
    /// and moves the position past them. The GPU reads native order.
    ///
    /// @throws java.nio.BufferOverflowException when fewer than [#bytes] remain
    void writeTo(ByteBuffer target);

    /// `commands`, one after the other in native byte order, ready to upload:
    /// the buffer's position is 0 and its limit is the end of the last.
    ///
    /// @throws IllegalArgumentException when there are none, or they are not
    ///                                  all of one kind: an indirect draw reads
    ///                                  one kind at a fixed stride
    static ByteBuffer pack(List<? extends IndirectCommand> commands) {
        if (commands.isEmpty()) {
            throw new IllegalArgumentException("no indirect commands to pack");
        }
        var kind = commands.getFirst().getClass();
        var total = 0;
        for (var command : commands) {
            if (command.getClass() != kind) {
                throw new IllegalArgumentException("a " + command.getClass().getSimpleName() + " among "
                        + kind.getSimpleName() + "s; a buffer of commands holds one kind");
            }
            total = Math.addExact(total, command.bytes());
        }
        var packed = ByteBuffer.allocate(total).order(ByteOrder.nativeOrder());
        commands.forEach(command -> command.writeTo(packed));
        return packed.flip();
    }
}
