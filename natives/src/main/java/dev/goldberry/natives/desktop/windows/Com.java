package dev.goldberry.natives.desktop.windows;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.ByteOrder;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import dev.goldberry.natives.Downcalls;

/// COM, as much of it as a binding needs: initialise the thread, create an
/// object, call a method through its vtable, release it.
///
/// A COM object is a pointer to a pointer to a table of function pointers,
/// and every method takes the object itself first. So a call is: read the
/// table, read slot `index`, and call it with a downcall handle of the
/// method's shape, the object first. [#call] does that; the shape is the
/// caller's, declared as a constant through [Downcalls#describe] like every
/// other.
///
/// UNVERIFIED: written against the Windows SDK's documentation and not yet run
/// on Windows. The parts that can be checked anywhere, a GUID's bytes and a
/// vtable read, are checked by a test.
public final class Com {

    /// `IUnknown::Release`, slot 2 of every vtable.
    public static final int RELEASE = 2;

    /// `COINIT_APARTMENTTHREADED`: the UI thread is an apartment of its own.
    private static final int APARTMENT_THREADED = 0x2;

    /// `CLSCTX_INPROC_SERVER`.
    private static final int IN_PROCESS = 0x1;

    /// `RPC_E_CHANGED_MODE`: the thread is initialised already, in the other
    /// mode, which still leaves COM usable on it.
    static final int CHANGED_MODE = 0x80010106;

    private static final Pattern GUID = Pattern.compile(
            "\\{?([0-9a-fA-F]{8})-([0-9a-fA-F]{4})-([0-9a-fA-F]{4})-([0-9a-fA-F]{4})-([0-9a-fA-F]{12})}?");

    /// `HRESULT CoInitializeEx(LPVOID, DWORD)`.
    private static final FunctionDescriptor INITIALISE =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    /// `HRESULT CoCreateInstance(REFCLSID, LPUNKNOWN, DWORD, REFIID, LPVOID *)`.
    private static final FunctionDescriptor CREATE =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));

    /// `ULONG IUnknown::Release(this)`.
    private static final FunctionDescriptor RELEASE_SHAPE =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS));

    private final MethodHandle initialise;
    private final MethodHandle create;
    private final MethodHandle release = Downcalls.link(RELEASE_SHAPE);

    private Com(MethodHandle initialise, MethodHandle create) {
        this.initialise = initialise;
        this.create = create;
    }

    /// COM on this machine, or empty off Windows.
    @SuppressWarnings("restricted")
    public static Optional<Com> open() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return Optional.empty();
        }
        try {
            var ole = SymbolLookup.libraryLookup("ole32.dll", Arena.global());
            return Optional.of(new Com(
                    Downcalls.link(INITIALISE).bindTo(ole.find("CoInitializeEx").orElseThrow()),
                    Downcalls.link(CREATE).bindTo(ole.find("CoCreateInstance").orElseThrow())));
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            return Optional.empty();
        }
    }

    /// Initialises COM on the calling thread, as an apartment. True when COM
    /// can be used on it, which includes a thread SDL or anyone else
    /// initialised first, in either mode.
    public boolean initialise() {
        var result = (int) call(initialise, MemorySegment.NULL, APARTMENT_THREADED);
        return succeeded(result) || result == CHANGED_MODE;
    }

    /// Creates the in-process object `clsid` and answers its `iid`
    /// interface, or [MemorySegment#NULL] when it cannot be made. The caller
    /// [#release]s what it gets.
    public MemorySegment create(String clsid, String iid) {
        try (var arena = Arena.ofConfined()) {
            var out = arena.allocate(ADDRESS);
            var result = (int) call(create, guid(arena, clsid), MemorySegment.NULL, IN_PROCESS, guid(arena, iid), out);
            return succeeded(result) ? out.get(ADDRESS, 0) : MemorySegment.NULL;
        }
    }

    /// Calls slot `index` of `object`'s vtable through `shape`, which is the
    /// method's unbound handle with the object as its first C argument.
    public static Object call(MemorySegment object, int index, MethodHandle shape, Object... arguments) {
        var all = new Object[arguments.length + 2];
        all[0] = method(object, index);
        all[1] = object;
        System.arraycopy(arguments, 0, all, 2, arguments.length);
        return call(shape, all);
    }

    /// Releases `object`, which may be null.
    public void release(MemorySegment object) {
        if (object.address() != 0) {
            call(object, RELEASE, release);
        }
    }

    /// The function in slot `index` of `object`'s vtable.
    @SuppressWarnings("restricted")
    public static MemorySegment method(MemorySegment object, int index) {
        var table = object.reinterpret(ADDRESS.byteSize()).get(ADDRESS, 0);
        return table.reinterpret(ADDRESS.byteSize() * (index + 1L)).getAtIndex(ADDRESS, index);
    }

    /// Whether an `HRESULT` is a success: `S_OK`, `S_FALSE` and the rest of
    /// the non-negative ones.
    public static boolean succeeded(int result) {
        return result >= 0;
    }

    /// A GUID in its in-memory form, from the registry form
    /// `{56FDF344-FD6D-11D0-958A-006097C9A090}`: the first three groups little
    /// endian, the last eight bytes as written.
    public static MemorySegment guid(Arena arena, String text) {
        var matcher = GUID.matcher(text.strip());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("not a GUID: " + text);
        }
        var guid = arena.allocate(16, 4);
        var little = ByteOrder.LITTLE_ENDIAN;
        guid.set(JAVA_INT.withOrder(little), 0, (int) Long.parseLong(matcher.group(1), 16));
        guid.set(JAVA_SHORT.withOrder(little), 4, (short) Integer.parseInt(matcher.group(2), 16));
        guid.set(JAVA_SHORT.withOrder(little), 6, (short) Integer.parseInt(matcher.group(3), 16));
        var tail = HexFormat.of().parseHex(matcher.group(4) + matcher.group(5));
        MemorySegment.copy(tail, 0, guid, JAVA_BYTE, 8, tail.length);
        return guid;
    }

    private static Object call(MethodHandle handle, Object... arguments) {
        try {
            var result = handle.invokeWithArguments(arguments);
            return result == null ? Boolean.TRUE : result;
        } catch (Throwable e) {
            throw new IllegalStateException("a COM call failed", e);
        }
    }
}
