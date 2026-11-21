package dev.goldberry.natives.desktop.dragout.ole;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.natives.Downcalls;
import dev.goldberry.natives.Upcalls;
import dev.goldberry.natives.desktop.windows.Com;
import dev.goldberry.natives.desktop.windows.ComServer;
import dev.goldberry.natives.desktop.windows.GlobalMemory;

/// `IDataObject` over what a drag offers: each format's bytes made when a
/// target asks for them, handed over in global memory.
///
/// It also keeps what others put on it. The shell's drag image helper stores
/// the picture and its window on the data object with `SetData`, and a
/// target's drop helper reads them back with `GetData`, so a format set here
/// is answered and enumerated like an offered one. Global memory is copied;
/// a stream or storage is kept by reference.
///
/// Called on the UI thread only, which is the apartment OLE calls it in.
public final class OleDataObject extends ComServer {

    private static final Logger LOG = Logs.of(OleDataObject.class);

    /// `IID_IDataObject`.
    public static final String IID = "{0000010E-0000-0000-C000-000000000046}";

    /// `HRESULT GetData(this, FORMATETC *, STGMEDIUM *)`, and `GetDataHere`
    /// and `GetCanonicalFormatEtc`, which have its shape.
    static final FunctionDescriptor THIS_AND_TWO =
            Upcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));

    /// `HRESULT QueryGetData(this, FORMATETC *)`, and `EnumDAdvise`.
    static final FunctionDescriptor THIS_AND_ONE = Upcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

    /// `HRESULT SetData(this, FORMATETC *, STGMEDIUM *, BOOL)`.
    static final FunctionDescriptor SET_DATA =
            Upcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT));

    /// `HRESULT EnumFormatEtc(this, DWORD, IEnumFORMATETC **)`.
    static final FunctionDescriptor ENUM_FORMATS =
            Upcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));

    /// `HRESULT DAdvise(this, FORMATETC *, DWORD, IAdviseSink *, DWORD *)`.
    static final FunctionDescriptor ADVISE =
            Upcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));

    /// `HRESULT DUnadvise(this, DWORD)`.
    static final FunctionDescriptor UNADVISE = Upcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    /// `ULONG AddRef(this)` and `ULONG Release(this)`, called on an interface
    /// someone else made.
    private static final FunctionDescriptor COUNT = Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS));

    /// `IUnknown::AddRef`'s slot.
    private static final int ADD_REF = 1;

    /// A format a drag offers in global memory.
    ///
    /// @param format the clipboard format, standard or registered
    /// @param bytes  made on request; null when there is nothing to give after all
    public record Offered(int format, Supplier<byte @Nullable []> bytes) {

        public Offered {
            Objects.requireNonNull(bytes, "bytes");
        }
    }

    /// What `SetData` left here.
    private sealed interface Stored {

        int tymed();

        /// Global memory, copied out of the caller's.
        record Bytes(byte[] bytes) implements Stored {

            @Override
            public int tymed() {
                return OleStructs.TYMED_HGLOBAL;
            }
        }

        /// A stream or storage, one reference of which is this object's.
        record Shared(int tymed, MemorySegment pointer) implements Stored {}
    }

    private final GlobalMemory memory;
    private final List<Offered> offered;
    private final Map<Integer, Stored> stored = new LinkedHashMap<>();
    private final MethodHandle count = Downcalls.link(COUNT);

    /// A data object with one reference, the caller's.
    ///
    /// @param memory  where the bytes are handed over
    /// @param offered the drag's formats, most specific first
    public OleDataObject(GlobalMemory memory, List<Offered> offered) {
        super(Table.VTABLE, IID);
        this.memory = Objects.requireNonNull(memory, "memory");
        this.offered = List.copyOf(offered);
    }

    /// The formats `SetData` has left here, in the order they came.
    public List<Integer> storedFormats() {
        return List.copyOf(stored.keySet());
    }

    @Override
    protected void released() {
        for (var kept : stored.values()) {
            if (kept instanceof Stored.Shared(var _, var pointer)) {
                var _ = (int) Com.call(pointer, Com.RELEASE, count);
            }
        }
        stored.clear();
    }

    private @Nullable Offered offered(int format) {
        for (var entry : offered) {
            if (entry.format() == format) {
                return entry;
            }
        }
        return null;
    }

    /// What `QueryGetData` and `GetData` both check first: whether `wanted`
    /// is here, and in a medium the caller takes.
    private int check(OleStructs.FormatEtc wanted) {
        if ((wanted.aspect() & OleStructs.DVASPECT_CONTENT) == 0) {
            return OleStructs.DV_E_FORMATETC;
        }
        var kept = stored.get(wanted.format());
        if (kept != null) {
            return (wanted.tymed() & kept.tymed()) != 0 ? S_OK : OleStructs.DV_E_TYMED;
        }
        if (offered(wanted.format()) == null) {
            return OleStructs.DV_E_FORMATETC;
        }
        return (wanted.tymed() & OleStructs.TYMED_HGLOBAL) != 0 ? S_OK : OleStructs.DV_E_TYMED;
    }

    private int getData(OleStructs.FormatEtc wanted, MemorySegment medium) {
        var checked = check(wanted);
        if (checked != S_OK) {
            return checked;
        }
        var kept = stored.get(wanted.format());
        switch (kept) {
            case Stored.Bytes(var bytes) -> {
                return hand(bytes, medium);
            }
            case Stored.Shared(var tymed, var pointer) -> {
                var _ = (int) Com.call(pointer, ADD_REF, count);
                OleStructs.Medium.write(medium, tymed, pointer);
                return S_OK;
            }
            case null -> {
                var entry = Objects.requireNonNull(offered(wanted.format()));
                byte[] bytes;
                try {
                    bytes = entry.bytes().get();
                } catch (RuntimeException e) {
                    LOG.warn("a drag's data for clipboard format {} failed", wanted.format(), e);
                    return E_UNEXPECTED;
                }
                return bytes == null ? OleStructs.DV_E_FORMATETC : hand(bytes, medium);
            }
        }
    }

    private int hand(byte[] bytes, MemorySegment medium) {
        var handle = memory.allocate(bytes);
        if (handle.address() == 0) {
            return E_OUTOFMEMORY;
        }
        OleStructs.Medium.write(medium, OleStructs.TYMED_HGLOBAL, handle);
        return S_OK;
    }

    private int setData(OleStructs.FormatEtc format, MemorySegment address, boolean release) {
        var medium = OleStructs.Medium.read(address);
        Stored kept;
        switch (medium.tymed()) {
            case OleStructs.TYMED_HGLOBAL -> kept = new Stored.Bytes(memory.read(medium.handle()));
            case OleStructs.TYMED_ISTREAM, OleStructs.TYMED_ISTORAGE -> {
                if (medium.handle().address() == 0) {
                    return E_POINTER;
                }
                var _ = (int) Com.call(medium.handle(), ADD_REF, count);
                kept = new Stored.Shared(medium.tymed(), medium.handle());
            }
            default -> {
                return OleStructs.DV_E_TYMED;
            }
        }
        if (release) {
            memory.releaseMedium(address);
        }
        var previous = stored.put(format.format(), kept);
        if (previous instanceof Stored.Shared(var _, var pointer)) {
            var _ = (int) Com.call(pointer, Com.RELEASE, count);
        }
        return S_OK;
    }

    private List<OleFormatEnumerator.Entry> entries() {
        var entries = new ArrayList<OleFormatEnumerator.Entry>();
        for (var entry : offered) {
            if (!stored.containsKey(entry.format())) {
                entries.add(new OleFormatEnumerator.Entry(entry.format(), OleStructs.TYMED_HGLOBAL));
            }
        }
        for (var entry : stored.entrySet()) {
            entries.add(new OleFormatEnumerator.Entry(
                    entry.getKey(), entry.getValue().tymed()));
        }
        return entries;
    }

    /// `IDataObject::GetData`.
    static int getData(MemorySegment self, MemorySegment format, MemorySegment medium) {
        try {
            var object = find(self, OleDataObject.class);
            if (object == null || format.address() == 0 || medium.address() == 0) {
                return E_POINTER;
            }
            return object.getData(OleStructs.FormatEtc.read(format), medium);
        } catch (Throwable t) {
            return E_UNEXPECTED;
        }
    }

    /// `IDataObject::GetDataHere`: into the caller's own medium, which no
    /// drag target asks for.
    static int getDataHere(MemorySegment self, MemorySegment format, MemorySegment medium) {
        return E_NOTIMPL;
    }

    /// `IDataObject::QueryGetData`.
    static int queryGetData(MemorySegment self, MemorySegment format) {
        try {
            var object = find(self, OleDataObject.class);
            if (object == null || format.address() == 0) {
                return E_POINTER;
            }
            return object.check(OleStructs.FormatEtc.read(format));
        } catch (Throwable t) {
            return E_UNEXPECTED;
        }
    }

    /// `IDataObject::GetCanonicalFormatEtc`: every format is its own
    /// canonical one, which the SDK spells as the input with no target device.
    @SuppressWarnings("restricted")
    static int canonicalFormat(MemorySegment self, MemorySegment format, MemorySegment out) {
        try {
            if (format.address() == 0 || out.address() == 0) {
                return E_POINTER;
            }
            var into = out.reinterpret(OleStructs.FORMATETC_SIZE);
            MemorySegment.copy(format.reinterpret(OleStructs.FORMATETC_SIZE), 0, into, 0, OleStructs.FORMATETC_SIZE);
            into.set(ADDRESS, 8, MemorySegment.NULL);
            return OleStructs.DATA_S_SAMEFORMATETC;
        } catch (Throwable t) {
            return E_UNEXPECTED;
        }
    }

    /// `IDataObject::SetData`.
    static int setData(MemorySegment self, MemorySegment format, MemorySegment medium, int release) {
        try {
            var object = find(self, OleDataObject.class);
            if (object == null || format.address() == 0 || medium.address() == 0) {
                return E_POINTER;
            }
            return object.setData(OleStructs.FormatEtc.read(format), medium, release != 0);
        } catch (Throwable t) {
            return E_UNEXPECTED;
        }
    }

    /// `IDataObject::EnumFormatEtc`: the formats a target can get.
    @SuppressWarnings("restricted")
    static int enumFormats(MemorySegment self, int direction, MemorySegment out) {
        try {
            var object = find(self, OleDataObject.class);
            if (object == null || out.address() == 0) {
                return E_POINTER;
            }
            var result = out.reinterpret(ADDRESS.byteSize());
            if (direction != OleStructs.DATADIR_GET) {
                result.set(ADDRESS, 0, MemorySegment.NULL);
                return E_NOTIMPL;
            }
            result.set(ADDRESS, 0, new OleFormatEnumerator(object.entries()).pointer());
            return S_OK;
        } catch (Throwable t) {
            return E_UNEXPECTED;
        }
    }

    /// `IDataObject::DAdvise`: nothing here changes, so nothing is advised.
    static int advise(
            MemorySegment self, MemorySegment format, int flags, MemorySegment sink, MemorySegment connection) {
        return OleStructs.OLE_E_ADVISENOTSUPPORTED;
    }

    /// `IDataObject::DUnadvise`.
    static int unadvise(MemorySegment self, int connection) {
        return OleStructs.OLE_E_ADVISENOTSUPPORTED;
    }

    /// `IDataObject::EnumDAdvise`.
    @SuppressWarnings("restricted")
    static int enumAdvise(MemorySegment self, MemorySegment out) {
        try {
            if (out.address() != 0) {
                out.reinterpret(ADDRESS.byteSize()).set(ADDRESS, 0, MemorySegment.NULL);
            }
            return OleStructs.OLE_E_ADVISENOTSUPPORTED;
        } catch (Throwable t) {
            return E_UNEXPECTED;
        }
    }

    /// The kind's table, made the first time a data object is.
    private static final class Table {

        static final MemorySegment VTABLE = vtable(
                stub("getData", THIS_AND_TWO),
                stub("getDataHere", THIS_AND_TWO),
                stub("queryGetData", THIS_AND_ONE),
                stub("canonicalFormat", THIS_AND_TWO),
                stub("setData", SET_DATA),
                stub("enumFormats", ENUM_FORMATS),
                stub("advise", ADVISE),
                stub("unadvise", UNADVISE),
                stub("enumAdvise", THIS_AND_ONE));

        @SuppressWarnings("restricted")
        private static MemorySegment stub(String name, FunctionDescriptor shape) {
            try {
                var target = MethodHandles.lookup().findStatic(OleDataObject.class, name, shape.toMethodType());
                return Linker.nativeLinker().upcallStub(target, shape, Arena.global());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("no IDataObject method " + name, e);
            }
        }
    }
}
