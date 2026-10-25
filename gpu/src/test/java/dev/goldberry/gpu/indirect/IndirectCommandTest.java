package dev.goldberry.gpu.indirect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The three indirect commands as words in a buffer: the sizes SDL reads, the
/// order of the words, native byte order, and one kind to a buffer. That SDL's
/// structs are these sizes is the layout probe's to check, in `:natives`.
@DisplayName("an indirect command")
class IndirectCommandTest {

    @Test
    @DisplayName("is as many bytes as SDL's struct, four, five or three words")
    void sizes() {
        assertEquals(16, DrawCommand.BYTES);
        assertEquals(20, IndexedDrawCommand.BYTES);
        assertEquals(12, DispatchCommand.BYTES);
        assertEquals(DrawCommand.BYTES, new DrawCommand(3, 1, 0, 0).bytes());
        assertEquals(IndexedDrawCommand.BYTES, new IndexedDrawCommand(3, 1, 0, 0, 0).bytes());
        assertEquals(DispatchCommand.BYTES, new DispatchCommand(1, 1, 1).bytes());
    }

    @Test
    @DisplayName("packs one after another in native order, words in SDL's order, and reads back as itself")
    void packsAndReadsBack() {
        var first = new DrawCommand(36, 12, 0, 0);
        var second = new DrawCommand(6, 1, 36, 0);
        var packed = IndirectCommand.pack(List.of(first, second));
        assertEquals(ByteOrder.nativeOrder(), packed.order());
        assertEquals(0, packed.position());
        assertEquals(2 * DrawCommand.BYTES, packed.limit());
        assertEquals(36, packed.getInt(0), "the vertex count is the first word");
        assertEquals(12, packed.getInt(4), "then the instances");
        assertEquals(first, DrawCommand.readFrom(packed));
        assertEquals(second, DrawCommand.readFrom(packed));

        var indexed = new IndexedDrawCommand(6, 2, 3, -4, 1);
        var bytes = IndirectCommand.pack(List.of(indexed));
        assertEquals(-4, bytes.getInt(12), "the vertex offset is signed, the fourth word");
        assertEquals(indexed, IndexedDrawCommand.readFrom(bytes));

        var dispatch = new DispatchCommand(8, 4, 1);
        assertEquals(dispatch, DispatchCommand.readFrom(IndirectCommand.pack(List.of(dispatch))));
    }

    @Test
    @DisplayName("writes at the target's position in the target's order")
    void writesWhereTold() {
        var target = ByteBuffer.allocate(4 + DispatchCommand.BYTES).order(ByteOrder.BIG_ENDIAN);
        target.position(4);
        new DispatchCommand(1, 2, 3).writeTo(target);
        assertEquals(target.capacity(), target.position());
        assertEquals(2, target.getInt(8));
        assertThrows(BufferOverflowException.class, () -> new DispatchCommand(1, 1, 1).writeTo(target));
    }

    @Test
    @DisplayName("takes zero counts, which draw nothing, and refuses negative ones")
    void counts() {
        assertEquals(0, new DrawCommand(0, 0, 0, 0).vertices(), "what a pass that culled everything writes");
        assertEquals(0, new DispatchCommand(0, 1, 1).groupsX());
        assertThrows(IllegalArgumentException.class, () -> new DrawCommand(-1, 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new IndexedDrawCommand(3, 1, -1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new DispatchCommand(1, -1, 1));
    }

    @Test
    @DisplayName("is packed one kind to a buffer, and at least one")
    void oneKind() {
        assertThrows(IllegalArgumentException.class, () -> IndirectCommand.pack(List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> IndirectCommand.pack(List.of(new DrawCommand(3, 1, 0, 0), new DispatchCommand(1, 1, 1))));
    }
}
