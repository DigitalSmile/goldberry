package io.github.digitalsmile.goldberry.natives.yoga;

import io.github.digitalsmile.goldberry.natives.layout.Layouts;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;

/// A [MeasureFunction] as a native function pointer Yoga can call.
///
/// This is the upcall ADR-0017 is about, and the hardest thing Goldberry asks of
/// FFM: `YGMeasureFunc` returns `YGSize` **by value**, in registers that differ
/// per target. Everything below exists to make that crossing correct and cheap,
/// because it happens once per measured node per layout pass.
///
/// Two decisions are load-bearing:
///
/// **The returned segment is reused.** Allocating a two-float segment per call
/// would put an allocation on the layout hot path to hold a value the linker
/// copies out immediately. One segment per callback is enough: the callback is
/// synchronous, so the linker has copied the result before Yoga can call again.
///
/// **Exceptions are caught, not propagated.** An exception escaping an upcall
/// into native code terminates the JVM — there is no C++ frame that can unwind
/// it. So a failing measure function reports zero to Yoga, and its exception is
/// held and rethrown by [#throwIfFailed()] once control is back in Java. Yoga
/// lays out one node wrongly; the alternative is losing the process.
///
/// Each instance owns a confined [Arena], so it is bound to the thread that
/// created it and must be [#close()]d. Closing invalidates the stub: a Yoga node
/// still holding it will call freed memory.
public final class MeasureCallback implements AutoCloseable {

    private static final Linker LINKER = Linker.nativeLinker();

    /// ```c
    /// typedef YGSize (*YGMeasureFunc)(
    ///     YGNodeConstRef node,
    ///     float width, YGMeasureMode widthMode,
    ///     float height, YGMeasureMode heightMode);
    /// ```
    ///
    /// The `YGMeasureMode` arguments are JAVA_INT: Yoga declares them as C enums,
    /// which every toolchain we target passes as `int`. [MeasureMode#of(int)]
    /// rejects anything outside the three defined values, so if that assumption
    /// ever fails on a new target it surfaces as an exception naming the value
    /// rather than as a plausible-looking layout.
    private static final FunctionDescriptor DESCRIPTOR = FunctionDescriptor.of(
            Layouts.YG_SIZE.layout(),
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_FLOAT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_FLOAT,
            ValueLayout.JAVA_INT);

    private static final MethodHandle INVOKE = invokeHandle();

    private static final long WIDTH_OFFSET =
            Layouts.YG_SIZE.offsetOf("width");
    private static final long HEIGHT_OFFSET =
            Layouts.YG_SIZE.offsetOf("height");

    private final MeasureFunction function;
    private final Arena arena;
    private final MemorySegment result;
    private final MemorySegment stub;

    private Throwable failure;

    private MeasureCallback(MeasureFunction function) {
        this.function = function;
        this.arena = Arena.ofConfined();
        try {
            this.result = arena.allocate(Layouts.YG_SIZE.layout());
            this.stub = upcallStub(INVOKE.bindTo(this), arena);
        } catch (RuntimeException | Error e) {
            arena.close();
            throw e;
        }
    }

    /// Wraps a measure function as a native function pointer.
    ///
    /// The result is confined to the calling thread and must be closed.
    public static MeasureCallback of(MeasureFunction function) {
        return new MeasureCallback(Objects.requireNonNull(function, "function"));
    }

    /// Rethrows whatever the measure function threw, if anything, and clears it.
    ///
    /// Call this after every native call that could have invoked the callback.
    /// Until it is called the failure is invisible: Yoga was told zero and
    /// carried on.
    public void throwIfFailed() {
        var pending = takeFailure();
        if (pending != null) {
            throw asUnchecked(pending);
        }
    }

    /// Hands back the pending failure and clears it, without throwing.
    ///
    /// [YogaNode] uses this rather than [#throwIfFailed()] because a layout pass
    /// can fail in several callbacks at once: throwing at the first one found
    /// would leave the others pending, and the next pass would then fail with an
    /// exception from the one before it. The tree collects them all and reports
    /// them together.
    Throwable takeFailure() {
        var pending = failure;
        failure = null;
        return pending;
    }

    /// Rethrows what a measure function threw, wrapping only what has to be
    /// wrapped so that a caller can still catch its own exception type.
    static RuntimeException asUnchecked(Throwable failure) {
        return switch (failure) {
            case RuntimeException e -> e;
            case Error e -> throw e;
            default -> new IllegalStateException("the measure function failed", failure);
        };
    }

    /// Releases the stub and its arena.
    ///
    /// Any Yoga node still holding this pointer is left with a dangling one, so
    /// close it only once nothing native refers to it.
    @Override
    public void close() {
        arena.close();
    }

    /// The `YGMeasureFunc` pointer. Package-private: a native address is exactly
    /// what `docs/ARCHITECTURE.md` §3.1 keeps inside this module.
    MemorySegment pointer() {
        return stub;
    }

    /// The upcall target. Called by native code, so it must not throw.
    ///
    /// The `node` pointer is ignored — a callback belongs to one node already,
    /// and passing the address on would leak a raw segment out of the module.
    private MemorySegment invoke(
            MemorySegment node, float width, int widthMode, float height, int heightMode) {
        try {
            var measured = function.measure(
                    width, MeasureMode.of(widthMode), height, MeasureMode.of(heightMode));
            result.set(ValueLayout.JAVA_FLOAT, WIDTH_OFFSET, measured.width());
            result.set(ValueLayout.JAVA_FLOAT, HEIGHT_OFFSET, measured.height());
        } catch (Throwable t) {
            // First failure wins: a later one is likely a consequence of this.
            if (failure == null) {
                failure = t;
            }
            result.set(ValueLayout.JAVA_FLOAT, WIDTH_OFFSET, 0f);
            result.set(ValueLayout.JAVA_FLOAT, HEIGHT_OFFSET, 0f);
        }
        return result;
    }

    // Restricted: building an upcall stub is what this class is for. The
    // descriptor must match YGMeasureFunc exactly -- the obligation ADR-0010
    // accepts, and the one ADR-0017's round-trip test discharges.
    @SuppressWarnings("restricted")
    private static MemorySegment upcallStub(MethodHandle target, Arena arena) {
        return LINKER.upcallStub(target, DESCRIPTOR, arena);
    }

    private static MethodHandle invokeHandle() {
        try {
            return MethodHandles.lookup().findVirtual(
                    MeasureCallback.class,
                    "invoke",
                    MethodType.methodType(
                            MemorySegment.class,
                            MemorySegment.class,
                            float.class,
                            int.class,
                            float.class,
                            int.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

}
