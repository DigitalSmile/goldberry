package dev.goldberry.natives.desktop.dragout.ole;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Objects;

import dev.goldberry.natives.Upcalls;
import dev.goldberry.natives.desktop.windows.ComServer;

/// `IEnumFORMATETC` over a fixed list: what a data object answers
/// `EnumFormatEtc` with.
///
/// Written here rather than taken from shell32's `SHCreateStdEnumFmtEtc` so
/// that the enumeration is called through its vtable by the tests on every
/// platform, as the rest of the data object is.
public final class OleFormatEnumerator extends ComServer {

    /// `IID_IEnumFORMATETC`.
    public static final String IID = "{00000103-0000-0000-C000-000000000046}";

    /// `HRESULT Next(this, ULONG, FORMATETC *, ULONG *)`.
    static final FunctionDescriptor NEXT =
            Upcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));

    /// `HRESULT Skip(this, ULONG)`, and every other method that takes one
    /// 32-bit number.
    static final FunctionDescriptor THIS_AND_NUMBER =
            Upcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    /// `HRESULT Clone(this, IEnumFORMATETC **)`.
    static final FunctionDescriptor THIS_AND_OUT = Upcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

    /// One format the enumerator hands out, for content, in `tymed`.
    ///
    /// @param format the clipboard format
    /// @param tymed  the medium it comes in
    public record Entry(int format, int tymed) {}

    private final List<Entry> entries;
    private int next;

    /// A new enumerator at the start of `entries`, with one reference, the
    /// caller's.
    public OleFormatEnumerator(List<Entry> entries) {
        this(entries, 0);
    }

    private OleFormatEnumerator(List<Entry> entries, int next) {
        super(Table.VTABLE, IID);
        this.entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        this.next = next;
    }

    /// `IEnumFORMATETC::Next`.
    @SuppressWarnings("restricted")
    static int next(MemorySegment self, int count, MemorySegment into, MemorySegment fetched) {
        try {
            var enumerator = find(self, OleFormatEnumerator.class);
            if (enumerator == null || into.address() == 0 || count < 0) {
                return E_POINTER;
            }
            if (fetched.address() == 0 && count != 1) {
                return E_POINTER;
            }
            var given = 0;
            var slots = into.reinterpret(OleStructs.FORMATETC_SIZE * count);
            while (given < count && enumerator.next < enumerator.entries.size()) {
                var entry = enumerator.entries.get(enumerator.next++);
                OleStructs.FormatEtc.write(
                        slots.asSlice(OleStructs.FORMATETC_SIZE * given), entry.format(), entry.tymed());
                given++;
            }
            if (fetched.address() != 0) {
                fetched.reinterpret(JAVA_INT.byteSize()).set(JAVA_INT, 0, given);
            }
            return given == count ? S_OK : S_FALSE;
        } catch (Throwable t) {
            return E_UNEXPECTED;
        }
    }

    /// `IEnumFORMATETC::Skip`.
    static int skip(MemorySegment self, int count) {
        try {
            var enumerator = find(self, OleFormatEnumerator.class);
            if (enumerator == null) {
                return E_POINTER;
            }
            var size = enumerator.entries.size();
            var wanted = (long) enumerator.next + Integer.toUnsignedLong(count);
            enumerator.next = (int) Math.min(wanted, size);
            return wanted <= size ? S_OK : S_FALSE;
        } catch (Throwable t) {
            return E_UNEXPECTED;
        }
    }

    /// `IEnumFORMATETC::Reset`.
    static int reset(MemorySegment self) {
        try {
            var enumerator = find(self, OleFormatEnumerator.class);
            if (enumerator == null) {
                return E_POINTER;
            }
            enumerator.next = 0;
            return S_OK;
        } catch (Throwable t) {
            return E_UNEXPECTED;
        }
    }

    /// `IEnumFORMATETC::Clone`: a second enumerator at the same place.
    @SuppressWarnings("restricted")
    static int copy(MemorySegment self, MemorySegment out) {
        try {
            var enumerator = find(self, OleFormatEnumerator.class);
            if (enumerator == null || out.address() == 0) {
                return E_POINTER;
            }
            var clone = new OleFormatEnumerator(enumerator.entries, enumerator.next);
            out.reinterpret(ADDRESS.byteSize()).set(ADDRESS, 0, clone.pointer());
            return S_OK;
        } catch (Throwable t) {
            return E_UNEXPECTED;
        }
    }

    /// The kind's table, made the first time an enumerator is.
    private static final class Table {

        static final MemorySegment VTABLE = vtable(
                stub("next", NEXT),
                stub("skip", THIS_AND_NUMBER),
                stub("reset", THIS_ONLY),
                stub("copy", THIS_AND_OUT));

        @SuppressWarnings("restricted")
        private static MemorySegment stub(String name, FunctionDescriptor shape) {
            try {
                var target = MethodHandles.lookup().findStatic(OleFormatEnumerator.class, name, shape.toMethodType());
                return Linker.nativeLinker().upcallStub(target, shape, Arena.global());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("no IEnumFORMATETC method " + name, e);
            }
        }
    }
}
