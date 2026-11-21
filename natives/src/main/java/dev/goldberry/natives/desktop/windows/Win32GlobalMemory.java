package dev.goldberry.natives.desktop.windows;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.Locale;
import java.util.Optional;

import dev.goldberry.natives.Downcalls;

/// [GlobalMemory] from kernel32's `GlobalAlloc` family and ole32's
/// `ReleaseStgMedium`.
///
/// UNVERIFIED: written against the Windows SDK's documentation; the Windows
/// lane's tests are the first to run it.
public final class Win32GlobalMemory implements GlobalMemory {

    /// `GHND`: movable and zeroed, the kind a data object hands over.
    private static final int MOVABLE_ZEROED = 0x42;

    /// `HGLOBAL GlobalAlloc(UINT, SIZE_T)`.
    private static final FunctionDescriptor ALLOCATE =
            Downcalls.describe(FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_LONG));

    /// `LPVOID GlobalLock(HGLOBAL)`, and `HGLOBAL GlobalFree(HGLOBAL)`.
    private static final FunctionDescriptor HANDLE_TO_ADDRESS =
            Downcalls.describe(FunctionDescriptor.of(ADDRESS, ADDRESS));

    /// `BOOL GlobalUnlock(HGLOBAL)`.
    private static final FunctionDescriptor UNLOCK = Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS));

    /// `SIZE_T GlobalSize(HGLOBAL)`.
    private static final FunctionDescriptor SIZE = Downcalls.describe(FunctionDescriptor.of(JAVA_LONG, ADDRESS));

    /// `void ReleaseStgMedium(LPSTGMEDIUM)`.
    private static final FunctionDescriptor RELEASE_MEDIUM = Downcalls.describe(FunctionDescriptor.ofVoid(ADDRESS));

    private final MethodHandle allocate;
    private final MethodHandle lock;
    private final MethodHandle unlock;
    private final MethodHandle size;
    private final MethodHandle free;
    private final MethodHandle releaseMedium;

    private Win32GlobalMemory(
            MethodHandle allocate,
            MethodHandle lock,
            MethodHandle unlock,
            MethodHandle size,
            MethodHandle free,
            MethodHandle releaseMedium) {
        this.allocate = allocate;
        this.lock = lock;
        this.unlock = unlock;
        this.size = size;
        this.free = free;
        this.releaseMedium = releaseMedium;
    }

    /// Global memory on this machine, or empty off Windows.
    @SuppressWarnings("restricted")
    public static Optional<GlobalMemory> open() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return Optional.empty();
        }
        try {
            var kernel = SymbolLookup.libraryLookup("kernel32.dll", Arena.global());
            var ole = SymbolLookup.libraryLookup("ole32.dll", Arena.global());
            return Optional.of(new Win32GlobalMemory(
                    Downcalls.link(ALLOCATE).bindTo(kernel.find("GlobalAlloc").orElseThrow()),
                    Downcalls.link(HANDLE_TO_ADDRESS)
                            .bindTo(kernel.find("GlobalLock").orElseThrow()),
                    Downcalls.link(UNLOCK).bindTo(kernel.find("GlobalUnlock").orElseThrow()),
                    Downcalls.link(SIZE).bindTo(kernel.find("GlobalSize").orElseThrow()),
                    Downcalls.link(HANDLE_TO_ADDRESS)
                            .bindTo(kernel.find("GlobalFree").orElseThrow()),
                    Downcalls.link(RELEASE_MEDIUM)
                            .bindTo(ole.find("ReleaseStgMedium").orElseThrow())));
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            return Optional.empty();
        }
    }

    @Override
    @SuppressWarnings("restricted")
    public MemorySegment allocate(byte[] bytes) {
        try {
            var handle = (MemorySegment) allocate.invokeExact(MOVABLE_ZEROED, (long) Math.max(1, bytes.length));
            if (handle.address() == 0) {
                return MemorySegment.NULL;
            }
            var block = (MemorySegment) lock.invokeExact(handle);
            if (block.address() == 0) {
                var _ = (MemorySegment) free.invokeExact(handle);
                return MemorySegment.NULL;
            }
            MemorySegment.copy(bytes, 0, block.reinterpret(bytes.length), JAVA_BYTE, 0, bytes.length);
            var _ = (int) unlock.invokeExact(handle);
            return handle;
        } catch (Throwable t) {
            throw Downcalls.failure("GlobalAlloc", t);
        }
    }

    @Override
    @SuppressWarnings("restricted")
    public byte[] read(MemorySegment handle) {
        try {
            var length = (long) size.invokeExact(handle);
            var block = (MemorySegment) lock.invokeExact(handle);
            if (block.address() == 0) {
                return new byte[0];
            }
            try {
                return block.reinterpret(length).toArray(JAVA_BYTE);
            } finally {
                var _ = (int) unlock.invokeExact(handle);
            }
        } catch (Throwable t) {
            throw Downcalls.failure("GlobalLock", t);
        }
    }

    @Override
    public void free(MemorySegment handle) {
        try {
            var _ = (MemorySegment) free.invokeExact(handle);
        } catch (Throwable t) {
            throw Downcalls.failure("GlobalFree", t);
        }
    }

    @Override
    public void releaseMedium(MemorySegment medium) {
        try {
            releaseMedium.invokeExact(medium);
        } catch (Throwable t) {
            throw Downcalls.failure("ReleaseStgMedium", t);
        }
    }
}
