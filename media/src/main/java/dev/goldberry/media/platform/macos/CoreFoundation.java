package dev.goldberry.media.platform.macos;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/// The Core Foundation the decoders need: the reference counting, and the
/// numbers, arrays and dictionaries that configure a session.
///
/// Every `create` here follows Core Foundation's Create Rule: the caller owns
/// the object and gives it to [#release].
final class CoreFoundation {

    /// `kCFNumberSInt32Type`.
    private static final long NUMBER_SINT32 = 3;

    /// `void CFRelease(CFTypeRef cf)`
    private static final MethodHandle FD_CFRelease = Framework.link(FunctionDescriptor.ofVoid(ADDRESS));

    /// `CFNumberRef CFNumberCreate(CFAllocatorRef allocator, CFNumberType theType, const void *valuePtr)`
    private static final MethodHandle FD_CFNumberCreate =
            Framework.link(FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_LONG, ADDRESS));

    /// `CFArrayRef CFArrayCreate(CFAllocatorRef allocator, const void **values, CFIndex numValues,`
    /// `const CFArrayCallBacks *callBacks)`
    private static final MethodHandle FD_CFArrayCreate =
            Framework.link(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS));

    /// `CFDictionaryRef CFDictionaryCreate(CFAllocatorRef allocator, const void **keys, const void **values,`
    /// `CFIndex numValues, const CFDictionaryKeyCallBacks *keyCallBacks,`
    /// `const CFDictionaryValueCallBacks *valueCallBacks)`
    private static final MethodHandle FD_CFDictionaryCreate =
            Framework.link(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS, ADDRESS));

    /// `Boolean CFEqual(CFTypeRef cf1, CFTypeRef cf2)`
    private static final MethodHandle FD_CFEqual = Framework.link(FunctionDescriptor.of(JAVA_BYTE, ADDRESS, ADDRESS));

    private final MemorySegment cfRelease;
    private final MemorySegment cfNumberCreate;
    private final MemorySegment cfArrayCreate;
    private final MemorySegment cfDictionaryCreate;
    private final MemorySegment cfEqual;

    /// `kCFTypeArrayCallBacks`: an array that retains its values.
    private final MemorySegment typeArrayCallBacks;
    /// `kCFTypeDictionaryKeyCallBacks` and `…ValueCallBacks`: a dictionary that
    /// retains, compares and hashes its keys and values as CF objects.
    private final MemorySegment typeDictionaryKeyCallBacks;

    private final MemorySegment typeDictionaryValueCallBacks;

    CoreFoundation(SymbolLookup lookup) {
        var f = Framework.CORE_FOUNDATION;
        this.cfRelease = f.symbol(lookup, "CFRelease");
        this.cfNumberCreate = f.symbol(lookup, "CFNumberCreate");
        this.cfArrayCreate = f.symbol(lookup, "CFArrayCreate");
        this.cfDictionaryCreate = f.symbol(lookup, "CFDictionaryCreate");
        this.cfEqual = f.symbol(lookup, "CFEqual");
        // The callback structs are the symbols themselves, passed by address.
        this.typeArrayCallBacks = f.symbol(lookup, "kCFTypeArrayCallBacks");
        this.typeDictionaryKeyCallBacks = f.symbol(lookup, "kCFTypeDictionaryKeyCallBacks");
        this.typeDictionaryValueCallBacks = f.symbol(lookup, "kCFTypeDictionaryValueCallBacks");
    }

    /// Releases `object`. NULL is passed over, where `CFRelease` would crash.
    void release(MemorySegment object) {
        if (object.equals(MemorySegment.NULL)) {
            return;
        }
        try {
            FD_CFRelease.invokeExact(cfRelease, object);
        } catch (Throwable t) {
            throw Framework.failure("CFRelease", t);
        }
    }

    /// A new `CFNumber` holding the 32-bit `value`.
    MemorySegment number(int value) {
        try (var arena = Arena.ofConfined()) {
            var result = (MemorySegment) FD_CFNumberCreate.invokeExact(
                    cfNumberCreate, MemorySegment.NULL, NUMBER_SINT32, arena.allocateFrom(JAVA_INT, value));
            return created("CFNumberCreate", result);
        } catch (Throwable t) {
            throw rethrow("CFNumberCreate", t);
        }
    }

    /// A new `CFArray` of `values`, which it retains.
    MemorySegment array(MemorySegment... values) {
        try (var arena = Arena.ofConfined()) {
            var result = (MemorySegment) FD_CFArrayCreate.invokeExact(
                    cfArrayCreate,
                    MemorySegment.NULL,
                    pointers(arena, values),
                    (long) values.length,
                    typeArrayCallBacks);
            return created("CFArrayCreate", result);
        } catch (Throwable t) {
            throw rethrow("CFArrayCreate", t);
        }
    }

    /// A new `CFDictionary` of `keys[i]` to `values[i]`, which it retains.
    MemorySegment dictionary(MemorySegment[] keys, MemorySegment[] values) {
        if (keys.length != values.length) {
            throw new IllegalArgumentException(keys.length + " keys and " + values.length + " values");
        }
        try (var arena = Arena.ofConfined()) {
            var result = (MemorySegment) FD_CFDictionaryCreate.invokeExact(
                    cfDictionaryCreate,
                    MemorySegment.NULL,
                    pointers(arena, keys),
                    pointers(arena, values),
                    (long) keys.length,
                    typeDictionaryKeyCallBacks,
                    typeDictionaryValueCallBacks);
            return created("CFDictionaryCreate", result);
        } catch (Throwable t) {
            throw rethrow("CFDictionaryCreate", t);
        }
    }

    /// Whether `a` and `b` are equal CF objects. False when either is NULL.
    boolean equal(MemorySegment a, MemorySegment b) {
        if (a.equals(MemorySegment.NULL) || b.equals(MemorySegment.NULL)) {
            return false;
        }
        try {
            return (byte) FD_CFEqual.invokeExact(cfEqual, a, b) != 0;
        } catch (Throwable t) {
            throw Framework.failure("CFEqual", t);
        }
    }

    private static MemorySegment pointers(Arena arena, MemorySegment[] values) {
        var array = arena.allocate(ADDRESS, Math.max(1, values.length));
        for (var i = 0; i < values.length; i++) {
            array.setAtIndex(ADDRESS, i, values[i]);
        }
        return array;
    }

    private static MemorySegment created(String function, MemorySegment result) {
        if (result.equals(MemorySegment.NULL)) {
            throw new IllegalStateException(function + " returned NULL");
        }
        return result;
    }

    private static RuntimeException rethrow(String function, Throwable t) {
        return t instanceof RuntimeException e ? e : Framework.failure(function, t);
    }
}
