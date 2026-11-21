package dev.goldberry.natives.desktop.windows;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.jspecify.annotations.Nullable;

import dev.goldberry.natives.Upcalls;

/// A COM object written in Java: a block of native memory whose first word
/// points at a vtable of upcall stubs, found again by its address when native
/// code calls through it.
///
/// The stubs are shared by every object of a kind, made once and kept for the
/// life of the process, because a stub cannot be freed while native code may
/// still be inside it, and the last `Release` is such a call. What an object
/// owns is its block, which goes when its count reaches zero.
///
/// `IUnknown`'s three methods are this class's: [#vtable] puts them in the
/// first three slots of every table. A subclass makes stubs for the rest of
/// its interface and finds itself with [#find].
///
/// An upcall must never throw, so every method a stub is made over catches
/// everything and answers an `HRESULT`.
///
/// UNVERIFIED on Windows: the objects are called through their vtables by the
/// tests on every platform, which is the same call COM makes, and by OLE itself
/// only on the Windows lane.
public abstract class ComServer {

    /// `S_OK`.
    public static final int S_OK = 0;

    /// `S_FALSE`.
    public static final int S_FALSE = 1;

    /// `E_NOTIMPL`.
    public static final int E_NOTIMPL = 0x80004001;

    /// `E_NOINTERFACE`.
    public static final int E_NOINTERFACE = 0x80004002;

    /// `E_POINTER`.
    public static final int E_POINTER = 0x80004003;

    /// `E_UNEXPECTED`: what a method answers when the Java behind it threw.
    public static final int E_UNEXPECTED = 0x8000FFFF;

    /// `E_OUTOFMEMORY`.
    public static final int E_OUTOFMEMORY = 0x8007000E;

    /// `IID_IUnknown`, which every object answers.
    public static final String IUNKNOWN = "{00000000-0000-0000-C000-000000000046}";

    /// `HRESULT QueryInterface(this, REFIID, void **)`.
    static final FunctionDescriptor QUERY_INTERFACE =
            Upcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));

    /// `ULONG AddRef(this)` and `ULONG Release(this)`, and every other method
    /// that takes only the object.
    public static final FunctionDescriptor THIS_ONLY = Upcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS));

    private static final Map<Long, ComServer> LIVE = new ConcurrentHashMap<>();

    private final Arena arena = Arena.ofShared();
    private final MemorySegment self;
    private final MemorySegment pointer;
    private final List<byte[]> interfaces;
    private final AtomicInteger references = new AtomicInteger(1);

    /// A new object with one reference, the caller's.
    ///
    /// @param vtable     the kind's table, from [#vtable]
    /// @param interfaces the IIDs it answers besides `IUnknown`, in registry form
    protected ComServer(MemorySegment vtable, String... interfaces) {
        self = arena.allocate(ADDRESS);
        self.set(ADDRESS, 0, vtable);
        // Unscoped, so that a downcall handed it does not hold the arena open:
        // the last Release closes it, and may be that very call.
        pointer = MemorySegment.ofAddress(self.address());
        var iids = new ArrayList<byte[]>();
        try (var scratch = Arena.ofConfined()) {
            iids.add(Com.guid(scratch, IUNKNOWN).toArray(JAVA_BYTE));
            for (var iid : interfaces) {
                iids.add(Com.guid(scratch, iid).toArray(JAVA_BYTE));
            }
        }
        this.interfaces = List.copyOf(iids);
        LIVE.put(self.address(), this);
    }

    /// The interface pointer native code holds.
    public final MemorySegment pointer() {
        return pointer;
    }

    /// How many references are out.
    public final int references() {
        return references.get();
    }

    /// Takes a reference, answering the new count.
    public final int addRef() {
        return references.incrementAndGet();
    }

    /// Gives a reference back, answering the new count. At zero the object is
    /// gone: [#released] runs and its block is freed.
    public final int release() {
        var left = references.decrementAndGet();
        if (left == 0) {
            LIVE.remove(self.address());
            try {
                released();
            } finally {
                arena.close();
            }
        }
        return Math.max(left, 0);
    }

    /// Whether this object answers `iid`, given as its sixteen bytes.
    final boolean answers(byte[] iid) {
        for (var known : interfaces) {
            if (Arrays.equals(known, iid)) {
                return true;
            }
        }
        return false;
    }

    /// What an object gives back when its last reference goes. Nothing by
    /// default.
    protected void released() {}

    /// Whether an object made here is still alive at `address`, for tests that
    /// count references to the end.
    public static boolean isLive(long address) {
        return LIVE.containsKey(address);
    }

    /// The live object at `self`, if it is one of `type`.
    protected static <T extends ComServer> @Nullable T find(MemorySegment self, Class<T> type) {
        var found = LIVE.get(self.address());
        return type.isInstance(found) ? type.cast(found) : null;
    }

    /// A vtable for the life of the process: `IUnknown`'s three slots, then
    /// `methods` in order.
    protected static MemorySegment vtable(MemorySegment... methods) {
        var table = Arena.global().allocate(ADDRESS, 3L + methods.length);
        table.setAtIndex(ADDRESS, 0, Unknown.QUERY_INTERFACE_STUB);
        table.setAtIndex(ADDRESS, 1, Unknown.ADD_REF_STUB);
        table.setAtIndex(ADDRESS, 2, Unknown.RELEASE_STUB);
        for (var index = 0; index < methods.length; index++) {
            table.setAtIndex(ADDRESS, 3L + index, methods[index]);
        }
        return table;
    }

    /// `IUnknown::QueryInterface`.
    @SuppressWarnings("restricted")
    static int queryInterface(MemorySegment self, MemorySegment iid, MemorySegment out) {
        try {
            if (out.address() == 0) {
                return E_POINTER;
            }
            var result = out.reinterpret(ADDRESS.byteSize());
            var object = LIVE.get(self.address());
            if (object == null || iid.address() == 0) {
                result.set(ADDRESS, 0, MemorySegment.NULL);
                return E_NOINTERFACE;
            }
            if (!object.answers(iid.reinterpret(16).toArray(JAVA_BYTE))) {
                result.set(ADDRESS, 0, MemorySegment.NULL);
                return E_NOINTERFACE;
            }
            object.addRef();
            result.set(ADDRESS, 0, object.pointer);
            return S_OK;
        } catch (Throwable t) {
            return E_UNEXPECTED;
        }
    }

    /// `IUnknown::AddRef`.
    static int addRef(MemorySegment self) {
        try {
            var object = LIVE.get(self.address());
            return object == null ? 0 : object.addRef();
        } catch (Throwable t) {
            return 0;
        }
    }

    /// `IUnknown::Release`.
    static int release(MemorySegment self) {
        try {
            var object = LIVE.get(self.address());
            return object == null ? 0 : object.release();
        } catch (Throwable t) {
            return 0;
        }
    }

    /// The three stubs every table starts with, made the first time a table is.
    private static final class Unknown {

        static final MemorySegment QUERY_INTERFACE_STUB = stub("queryInterface", QUERY_INTERFACE);
        static final MemorySegment ADD_REF_STUB = stub("addRef", THIS_ONLY);
        static final MemorySegment RELEASE_STUB = stub("release", THIS_ONLY);

        @SuppressWarnings("restricted")
        private static MemorySegment stub(String name, FunctionDescriptor shape) {
            try {
                var target = MethodHandles.lookup().findStatic(ComServer.class, name, shape.toMethodType());
                return Linker.nativeLinker().upcallStub(target, shape, Arena.global());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("no IUnknown method " + name, e);
            }
        }
    }
}
