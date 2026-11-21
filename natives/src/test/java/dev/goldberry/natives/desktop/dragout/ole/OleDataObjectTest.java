package dev.goldberry.natives.desktop.dragout.ole;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.natives.desktop.windows.Com;
import dev.goldberry.natives.desktop.windows.ComServer;

/// [OleDataObject] and its [OleFormatEnumerator], called through their
/// vtables as OLE and a target call them, with global memory from the heap.
@SuppressWarnings("restricted")
@DisplayName("a drag's IDataObject")
class OleDataObjectTest {

    private static final int REGISTERED = 0xC123;

    private static final Linker LINKER = Linker.nativeLinker();
    private static final MethodHandle THIS_AND_TWO =
            LINKER.downcallHandle(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle THIS_AND_ONE =
            LINKER.downcallHandle(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
    private static final MethodHandle SET_DATA =
            LINKER.downcallHandle(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT));
    private static final MethodHandle ENUM =
            LINKER.downcallHandle(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
    private static final MethodHandle ADVISE =
            LINKER.downcallHandle(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));
    private static final MethodHandle NEXT =
            LINKER.downcallHandle(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));
    private static final MethodHandle THIS_AND_NUMBER =
            LINKER.downcallHandle(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
    private static final MethodHandle THIS_ONLY = LINKER.downcallHandle(FunctionDescriptor.of(JAVA_INT, ADDRESS));

    private static final int GET_DATA = 3;
    private static final int GET_DATA_HERE = 4;
    private static final int QUERY_GET_DATA = 5;
    private static final int CANONICAL = 6;
    private static final int SET = 7;
    private static final int ENUM_FORMATS = 8;
    private static final int DADVISE = 9;

    /// A stream that counts its references, for `SetData` to keep.
    private static final class FakeStream extends ComServer {

        private static final MemorySegment VTABLE = vtable();

        FakeStream() {
            super(VTABLE);
        }
    }

    private HeapGlobalMemory memory;
    private Arena arena;
    private final AtomicInteger textAsked = new AtomicInteger();

    @BeforeEach
    void open() {
        memory = new HeapGlobalMemory();
        arena = Arena.ofConfined();
    }

    @AfterEach
    void close() {
        arena.close();
        memory.close();
    }

    private OleDataObject dataObject() {
        return new OleDataObject(
                memory,
                List.of(
                        new OleDataObject.Offered(OleStructs.CF_UNICODETEXT, () -> {
                            textAsked.incrementAndGet();
                            return "hi\0".getBytes(StandardCharsets.UTF_16LE);
                        }),
                        new OleDataObject.Offered(REGISTERED, () -> new byte[] {1, 2, 3})));
    }

    private MemorySegment format(int format, int tymed) {
        var at = arena.allocate(OleStructs.FORMATETC_SIZE, 8);
        OleStructs.FormatEtc.write(at, format, tymed);
        return at;
    }

    private static int call(MemorySegment object, int slot, MethodHandle shape, Object... arguments) {
        return (int) Com.call(object, slot, shape, arguments);
    }

    private int getData(OleDataObject data, int format, int tymed, MemorySegment medium) {
        return call(data.pointer(), GET_DATA, THIS_AND_TWO, format(format, tymed), medium);
    }

    @Test
    @DisplayName("hands each offered format over in global memory, made when it is asked for")
    void getData() {
        var data = dataObject();
        try {
            assertEquals(0, textAsked.get(), "nothing is made before a target asks");
            var medium = arena.allocate(OleStructs.STGMEDIUM_SIZE, 8);

            assertEquals(ComServer.S_OK, getData(data, OleStructs.CF_UNICODETEXT, OleStructs.TYMED_HGLOBAL, medium));
            var read = OleStructs.Medium.read(medium);
            assertAll(
                    () -> assertEquals(OleStructs.TYMED_HGLOBAL, read.tymed()),
                    () -> assertEquals(0, read.release().address(), "the receiver frees the memory itself"),
                    () -> assertArrayEquals("hi\0".getBytes(StandardCharsets.UTF_16LE), memory.read(read.handle())),
                    () -> assertEquals(1, textAsked.get()));
            memory.releaseMedium(medium);

            assertEquals(
                    ComServer.S_OK,
                    getData(data, REGISTERED, OleStructs.TYMED_HGLOBAL | OleStructs.TYMED_ISTREAM, medium));
            assertArrayEquals(
                    new byte[] {1, 2, 3},
                    memory.read(OleStructs.Medium.read(medium).handle()));
            memory.releaseMedium(medium);
            assertEquals(0, memory.outstanding());
        } finally {
            data.release();
        }
    }

    @Test
    @DisplayName("refuses a format it does not have, a medium it cannot give, and another aspect")
    void refusals() {
        var data = dataObject();
        try {
            var medium = arena.allocate(OleStructs.STGMEDIUM_SIZE, 8);
            var thumbnail = format(OleStructs.CF_UNICODETEXT, OleStructs.TYMED_HGLOBAL);
            thumbnail.set(JAVA_INT, 16, 2);

            assertAll(
                    () -> assertEquals(
                            OleStructs.DV_E_FORMATETC,
                            getData(data, OleStructs.CF_HDROP, OleStructs.TYMED_HGLOBAL, medium)),
                    () -> assertEquals(
                            OleStructs.DV_E_TYMED,
                            getData(data, OleStructs.CF_UNICODETEXT, OleStructs.TYMED_ISTREAM, medium)),
                    () -> assertEquals(
                            OleStructs.DV_E_FORMATETC, call(data.pointer(), GET_DATA, THIS_AND_TWO, thumbnail, medium)),
                    () -> assertEquals(
                            ComServer.E_POINTER,
                            call(
                                    data.pointer(),
                                    GET_DATA,
                                    THIS_AND_TWO,
                                    format(OleStructs.CF_UNICODETEXT, OleStructs.TYMED_HGLOBAL),
                                    MemorySegment.NULL)),
                    () -> assertEquals(
                            ComServer.E_NOTIMPL,
                            call(
                                    data.pointer(),
                                    GET_DATA_HERE,
                                    THIS_AND_TWO,
                                    format(OleStructs.CF_UNICODETEXT, OleStructs.TYMED_HGLOBAL),
                                    medium)),
                    () -> assertEquals(0, textAsked.get(), "a refusal asks the offer nothing"),
                    () -> assertEquals(0, memory.outstanding()));
        } finally {
            data.release();
        }
    }

    @Test
    @DisplayName("answers QueryGetData as GetData would, without making anything")
    void queryGetData() {
        var data = dataObject();
        try {
            assertAll(
                    () -> assertEquals(
                            ComServer.S_OK,
                            call(
                                    data.pointer(),
                                    QUERY_GET_DATA,
                                    THIS_AND_ONE,
                                    format(OleStructs.CF_UNICODETEXT, OleStructs.TYMED_HGLOBAL))),
                    () -> assertEquals(
                            OleStructs.DV_E_TYMED,
                            call(
                                    data.pointer(),
                                    QUERY_GET_DATA,
                                    THIS_AND_ONE,
                                    format(REGISTERED, OleStructs.TYMED_ISTORAGE))),
                    () -> assertEquals(
                            OleStructs.DV_E_FORMATETC,
                            call(
                                    data.pointer(),
                                    QUERY_GET_DATA,
                                    THIS_AND_ONE,
                                    format(OleStructs.CF_DIBV5, OleStructs.TYMED_HGLOBAL))),
                    () -> assertEquals(0, textAsked.get()));
        } finally {
            data.release();
        }
    }

    @Test
    @DisplayName("answers a data supplier that fails or has nothing without crossing the failure")
    void failingSupplier() {
        var data = new OleDataObject(
                memory,
                List.of(
                        new OleDataObject.Offered(OleStructs.CF_UNICODETEXT, () -> {
                            throw new IllegalStateException("the application's own failure");
                        }),
                        new OleDataObject.Offered(REGISTERED, () -> null)));
        try {
            var medium = arena.allocate(OleStructs.STGMEDIUM_SIZE, 8);

            assertEquals(
                    ComServer.E_UNEXPECTED, getData(data, OleStructs.CF_UNICODETEXT, OleStructs.TYMED_HGLOBAL, medium));
            assertEquals(OleStructs.DV_E_FORMATETC, getData(data, REGISTERED, OleStructs.TYMED_HGLOBAL, medium));
        } finally {
            data.release();
        }
    }

    private List<Integer> enumerate(MemorySegment enumerator) {
        var formats = new ArrayList<Integer>();
        var slots = arena.allocate(OleStructs.FORMATETC_SIZE * 8, 8);
        var fetched = arena.allocate(JAVA_INT);
        var result = call(enumerator, 3, NEXT, 8, slots, fetched);
        assertEquals(ComServer.S_FALSE, result, "fewer than asked for");
        for (var index = 0; index < fetched.get(JAVA_INT, 0); index++) {
            var slot = slots.asSlice(OleStructs.FORMATETC_SIZE * index);
            assertEquals(OleStructs.DVASPECT_CONTENT, slot.get(JAVA_INT, 16));
            assertEquals(-1, slot.get(JAVA_INT, 20));
            assertEquals(0, slot.get(ADDRESS, 8).address());
            formats.add(Short.toUnsignedInt(slot.get(JAVA_SHORT, 0)));
        }
        return formats;
    }

    @Test
    @DisplayName("enumerates every format in order, and steps, resets and clones")
    void enumerates() {
        var data = dataObject();
        try {
            var out = arena.allocate(ADDRESS);
            assertEquals(ComServer.S_OK, call(data.pointer(), ENUM_FORMATS, ENUM, OleStructs.DATADIR_GET, out));
            var enumerator = out.get(ADDRESS, 0);

            assertEquals(List.of(OleStructs.CF_UNICODETEXT, REGISTERED), enumerate(enumerator));

            assertEquals(ComServer.S_OK, call(enumerator, 5, THIS_ONLY));
            assertEquals(ComServer.S_OK, call(enumerator, 4, THIS_AND_NUMBER, 1));
            var one = arena.allocate(OleStructs.FORMATETC_SIZE, 8);
            assertEquals(ComServer.S_OK, call(enumerator, 3, NEXT, 1, one, MemorySegment.NULL));
            assertEquals(REGISTERED, Short.toUnsignedInt(one.get(JAVA_SHORT, 0)));
            assertEquals(ComServer.S_FALSE, call(enumerator, 4, THIS_AND_NUMBER, 5), "past the end");

            call(enumerator, 5, THIS_ONLY);
            call(enumerator, 4, THIS_AND_NUMBER, 1);
            var cloned = arena.allocate(ADDRESS);
            assertEquals(ComServer.S_OK, call(enumerator, 6, THIS_AND_ONE, cloned));
            assertEquals(List.of(REGISTERED), enumerate(cloned.get(ADDRESS, 0)), "a clone starts where it was");

            assertEquals(0, call(cloned.get(ADDRESS, 0), Com.RELEASE, THIS_ONLY));
            assertEquals(0, call(enumerator, Com.RELEASE, THIS_ONLY));
            assertFalse(ComServer.isLive(enumerator.address()));
            assertEquals(
                    ComServer.E_NOTIMPL,
                    call(data.pointer(), ENUM_FORMATS, ENUM, 2, out),
                    "nothing is set by DATADIR_SET");
        } finally {
            data.release();
        }
    }

    @Test
    @DisplayName("keeps global memory set on it, frees what it was given, and answers and lists it")
    void setGlobal() {
        var data = dataObject();
        try {
            var medium = arena.allocate(OleStructs.STGMEDIUM_SIZE, 8);
            OleStructs.Medium.write(medium, OleStructs.TYMED_HGLOBAL, memory.allocate(new byte[] {9, 8}));

            assertEquals(
                    ComServer.S_OK,
                    call(data.pointer(), SET, SET_DATA, format(0xC200, OleStructs.TYMED_HGLOBAL), medium, 1));
            assertEquals(0, memory.outstanding(), "released, as the caller asked");
            assertEquals(List.of(0xC200), data.storedFormats());

            var out = arena.allocate(OleStructs.STGMEDIUM_SIZE, 8);
            assertEquals(ComServer.S_OK, getData(data, 0xC200, OleStructs.TYMED_HGLOBAL, out));
            assertArrayEquals(
                    new byte[] {9, 8}, memory.read(OleStructs.Medium.read(out).handle()));
            memory.releaseMedium(out);

            var enumerator = arena.allocate(ADDRESS);
            call(data.pointer(), ENUM_FORMATS, ENUM, OleStructs.DATADIR_GET, enumerator);
            assertEquals(List.of(OleStructs.CF_UNICODETEXT, REGISTERED, 0xC200), enumerate(enumerator.get(ADDRESS, 0)));
            call(enumerator.get(ADDRESS, 0), Com.RELEASE, THIS_ONLY);
        } finally {
            data.release();
        }
    }

    @Test
    @DisplayName("keeps a stream set on it by reference, and gives the reference back when it goes")
    void setStream() {
        var stream = new FakeStream();
        var data = dataObject();
        var medium = arena.allocate(OleStructs.STGMEDIUM_SIZE, 8);
        OleStructs.Medium.write(medium, OleStructs.TYMED_ISTREAM, stream.pointer());

        assertEquals(
                ComServer.S_OK,
                call(data.pointer(), SET, SET_DATA, format(0xC201, OleStructs.TYMED_ISTREAM), medium, 0));
        assertEquals(2, stream.references(), "its own reference, the caller keeping theirs");

        var out = arena.allocate(OleStructs.STGMEDIUM_SIZE, 8);
        assertEquals(ComServer.S_OK, getData(data, 0xC201, OleStructs.TYMED_ISTREAM, out));
        assertEquals(
                stream.pointer().address(), OleStructs.Medium.read(out).handle().address());
        assertEquals(3, stream.references(), "one more for the receiver");
        memory.releaseMedium(out);
        assertEquals(2, stream.references());

        data.release();
        assertEquals(1, stream.references(), "given back with the data object");
        stream.release();
    }

    @Test
    @DisplayName("answers the canonical format as the same one, and no advise sinks")
    void canonicalAndAdvise() {
        var data = dataObject();
        try {
            var in = format(OleStructs.CF_UNICODETEXT, OleStructs.TYMED_HGLOBAL);
            in.set(ADDRESS, 8, MemorySegment.ofAddress(0x40));
            var out = arena.allocate(OleStructs.FORMATETC_SIZE, 8);

            assertEquals(OleStructs.DATA_S_SAMEFORMATETC, call(data.pointer(), CANONICAL, THIS_AND_TWO, in, out));
            assertEquals(OleStructs.CF_UNICODETEXT, Short.toUnsignedInt(out.get(JAVA_SHORT, 0)));
            assertEquals(0, out.get(ADDRESS, 8).address(), "no target device");
            assertEquals(
                    OleStructs.OLE_E_ADVISENOTSUPPORTED,
                    call(data.pointer(), DADVISE, ADVISE, in, 0, MemorySegment.NULL, arena.allocate(JAVA_INT)));
        } finally {
            data.release();
        }
    }

    @Test
    @DisplayName("answers IDataObject, and is gone once every reference is given back")
    void references() {
        var data = dataObject();
        var address = data.pointer().address();
        var out = arena.allocate(ADDRESS);
        var iid = Com.guid(arena, OleDataObject.IID);

        assertEquals(ComServer.S_OK, call(data.pointer(), 0, THIS_AND_TWO, iid, out));
        assertEquals(address, out.get(ADDRESS, 0).address());
        assertEquals(1, call(data.pointer(), Com.RELEASE, THIS_ONLY));
        assertTrue(ComServer.isLive(address));
        data.release();
        assertFalse(ComServer.isLive(address));
    }
}
