package dev.goldberry.natives.desktop.dragout.ole;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.util.HashMap;
import java.util.Map;

import dev.goldberry.natives.desktop.windows.Com;
import dev.goldberry.natives.desktop.windows.GlobalMemory;

/// [GlobalMemory] for tests on any platform: a "handle" is the address of a
/// block in an arena of its own, and the blocks still out are counted.
@SuppressWarnings("restricted")
final class HeapGlobalMemory implements GlobalMemory, AutoCloseable {

    private static final MethodHandle RELEASE =
            Linker.nativeLinker().downcallHandle(FunctionDescriptor.of(JAVA_INT, ADDRESS));

    private final Map<Long, Arena> blocks = new HashMap<>();
    private final Map<Long, Integer> sizes = new HashMap<>();
    int mediaReleased;

    @Override
    public MemorySegment allocate(byte[] bytes) {
        var arena = Arena.ofShared();
        var block = arena.allocate(Math.max(1, bytes.length));
        MemorySegment.copy(bytes, 0, block, JAVA_BYTE, 0, bytes.length);
        blocks.put(block.address(), arena);
        sizes.put(block.address(), bytes.length);
        return MemorySegment.ofAddress(block.address());
    }

    @Override
    public byte[] read(MemorySegment handle) {
        var size = sizes.get(handle.address());
        if (size == null) {
            throw new IllegalArgumentException("not a block of this memory: " + handle);
        }
        return handle.reinterpret(size).toArray(JAVA_BYTE);
    }

    @Override
    public void free(MemorySegment handle) {
        var arena = blocks.remove(handle.address());
        sizes.remove(handle.address());
        if (arena == null) {
            throw new IllegalArgumentException("freed twice, or never allocated: " + handle);
        }
        arena.close();
    }

    @Override
    public void releaseMedium(MemorySegment medium) {
        mediaReleased++;
        var read = OleStructs.Medium.read(medium);
        if (read.release().address() != 0) {
            var _ = (int) Com.call(read.release(), Com.RELEASE, RELEASE);
            return;
        }
        switch (read.tymed()) {
            case OleStructs.TYMED_HGLOBAL -> free(read.handle());
            case OleStructs.TYMED_ISTREAM, OleStructs.TYMED_ISTORAGE -> Com.call(read.handle(), Com.RELEASE, RELEASE);
            default -> {}
        }
    }

    /// How many blocks have not been freed.
    int outstanding() {
        return blocks.size();
    }

    @Override
    public void close() {
        blocks.values().forEach(Arena::close);
        blocks.clear();
        sizes.clear();
    }
}
