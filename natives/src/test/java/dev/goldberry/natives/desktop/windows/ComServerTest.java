package dev.goldberry.natives.desktop.windows;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// [ComServer]'s `IUnknown`, called through its own vtable as native code
/// calls it, on any platform.
@SuppressWarnings("restricted")
@DisplayName("a COM object written in Java")
class ComServerTest {

    private static final String SAMPLE = "{11111111-2222-3333-4444-555555555555}";
    private static final String OTHER = "{99999999-2222-3333-4444-555555555555}";

    private static final MethodHandle QUERY =
            Linker.nativeLinker().downcallHandle(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle COUNT =
            Linker.nativeLinker().downcallHandle(FunctionDescriptor.of(JAVA_INT, ADDRESS));

    /// An object with no methods of its own, answering one interface.
    private static final class Sample extends ComServer {

        private static final MemorySegment VTABLE = vtable();

        boolean released;

        Sample() {
            super(VTABLE, SAMPLE);
        }

        @Override
        protected void released() {
            released = true;
        }
    }

    private static int query(MemorySegment object, String iid, MemorySegment out) {
        try (var arena = Arena.ofConfined()) {
            return (int) Com.call(object, 0, QUERY, Com.guid(arena, iid), out);
        }
    }

    @Test
    @DisplayName("answers IUnknown and its own interface with itself, counting a reference")
    void answers() {
        var sample = new Sample();
        try (var arena = Arena.ofConfined()) {
            var out = arena.allocate(ADDRESS);

            assertEquals(ComServer.S_OK, query(sample.pointer(), ComServer.IUNKNOWN, out));
            assertEquals(sample.pointer().address(), out.get(ADDRESS, 0).address());
            assertEquals(ComServer.S_OK, query(sample.pointer(), SAMPLE, out));
            assertEquals(3, sample.references());
        } finally {
            sample.release();
            sample.release();
            sample.release();
        }
    }

    @Test
    @DisplayName("answers another interface with E_NOINTERFACE and a null pointer")
    void refuses() {
        var sample = new Sample();
        try (var arena = Arena.ofConfined()) {
            var out = arena.allocate(ADDRESS);
            out.set(ADDRESS, 0, MemorySegment.ofAddress(0x1234));

            assertEquals(ComServer.E_NOINTERFACE, query(sample.pointer(), OTHER, out));
            assertEquals(0, out.get(ADDRESS, 0).address());
            assertEquals(1, sample.references());
            assertEquals(ComServer.E_POINTER, query(sample.pointer(), SAMPLE, MemorySegment.NULL));
        } finally {
            sample.release();
        }
    }

    @Test
    @DisplayName("is gone at its last Release, and says so once")
    void lastRelease() {
        var sample = new Sample();
        var address = sample.pointer().address();

        assertEquals(2, (int) Com.call(sample.pointer(), 1, COUNT));
        assertEquals(1, (int) Com.call(sample.pointer(), Com.RELEASE, COUNT));
        assertTrue(ComServer.isLive(address));
        assertFalse(sample.released);

        assertEquals(0, (int) Com.call(sample.pointer(), Com.RELEASE, COUNT));
        assertTrue(sample.released);
        assertFalse(ComServer.isLive(address));
    }
}
