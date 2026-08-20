package io.github.digitalsmile.goldberry.natives;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/// What one foreign call costs, held both ways.
///
/// The measurement behind
/// [ADR-0161](../../../../../../book/src/adr/0161-a-downcall-handle-is-a-constant-or-it-is-not-a-call.md).
/// `goldberry_abi_version` is the cheapest function `libgoldberry` exports — it
/// returns a constant — so what this times is the crossing and nothing else.
///
/// **On the JVM it will report two numbers that are the same**, which is the
/// point: the change ADR-0161 made costs the JVM nothing. The difference it was
/// made for only appears in a native image, where the bound handle cannot be a
/// compile-time constant and the unbound one can. There is no GraalVM in this
/// repository's toolchain, so that half is run by hand and written down in the
/// ADR rather than measured here.
///
/// **Tagged `benchmark`, so `check` never runs it.** Run with
/// `./gradlew :natives:benchmark`.
@Tag("benchmark")
class DowncallBenchmark {

    private static final long WARMUP = 2_000_000L;
    private static final long RUNS = 20_000_000L;

    private static final FunctionDescriptor ABI_VERSION =
            FunctionDescriptor.of(ValueLayout.JAVA_INT);

    private static MemorySegment address;
    private static MethodHandle bound;

    @BeforeAll
    @SuppressWarnings("restricted")
    static void bind() {
        NativeLibraryRequirement.enforce();
        address = Downcalls.symbol(NativeLibrary.get().lookup(), "goldberry_abi_version");
        bound = Linker.nativeLinker().downcallHandle(address, ABI_VERSION);
    }

    @Test
    @DisplayName("a bound handle and an unbound one, per call")
    void perCall() throws Throwable {
        boundLoop(WARMUP);
        unboundLoop(WARMUP);

        var boundNanos = time(this::boundLoop);
        var unboundNanos = time(this::unboundLoop);

        System.out.printf(Locale.ROOT,
                "downcall  bound %.2f ns/call   unbound %.2f ns/call%n", boundNanos, unboundNanos);
    }

    /// Whether the constant has to be read by the method that calls it.
    ///
    /// It does, in an image: **8.9 ns when the helper names the constant itself,
    /// 810 ns when the same constant is passed in as a parameter.** That is the
    /// measurement behind [Downcalls] holding one handle per *signature* rather
    /// than one per function — the binding classes call through shape-generic
    /// helpers, and a helper shared by forty symbols cannot name one of them.
    ///
    /// On the JVM the two are equal, because the JIT inlines the helper and
    /// folds the argument. Nothing here reproduces the gap; only an image does.
    @Test
    @DisplayName("a constant read inside the helper, and the same one passed in")
    void throughAHelper() throws Throwable {
        insideLoop(WARMUP);
        passedLoop(WARMUP);

        var insideNanos = time(this::insideLoop);
        var passedNanos = time(this::passedLoop);

        System.out.printf(Locale.ROOT,
                "helper    constant inside %.2f ns/call   passed in %.2f ns/call%n",
                insideNanos, passedNanos);
    }

    private interface Loop {
        int run(long iterations) throws Throwable;
    }

    private static double time(Loop loop) throws Throwable {
        var started = System.nanoTime();
        var sink = loop.run(RUNS);
        var elapsed = System.nanoTime() - started;
        if (sink != GoldberryShim.SUPPORTED_ABI_VERSION * (int) RUNS) {
            // Not an assertion about speed -- an assertion that the loop ran and
            // the calls returned what the library says, so an optimiser that
            // deleted the whole thing cannot be reported as infinite throughput.
            throw new AssertionError("the benchmark loop did not call the library");
        }
        return (double) elapsed / RUNS;
    }

    private int boundLoop(long iterations) throws Throwable {
        var sink = 0;
        for (var i = 0L; i < iterations; i++) {
            sink += (int) bound.invokeExact();
        }
        return sink;
    }

    private int unboundLoop(long iterations) throws Throwable {
        var sink = 0;
        var target = address;
        for (var i = 0L; i < iterations; i++) {
            sink += (int) Downcalls.INT__VOID.invokeExact(target);
        }
        return sink;
    }

    private int insideLoop(long iterations) {
        var sink = 0;
        var target = address;
        for (var i = 0L; i < iterations; i++) {
            sink += callInside(target);
        }
        return sink;
    }

    private int passedLoop(long iterations) {
        var sink = 0;
        var target = address;
        for (var i = 0L; i < iterations; i++) {
            sink += callPassed(Downcalls.INT__VOID, target);
        }
        return sink;
    }

    /// What every invocation helper in the binding classes looks like.
    private static int callInside(MemorySegment function) {
        try {
            return (int) Downcalls.INT__VOID.invokeExact(function);
        } catch (Throwable t) {
            throw new IllegalStateException("goldberry_abi_version() failed", t);
        }
    }

    /// What one would look like if the handle were named for the function
    /// rather than for its signature: the constant arrives as an argument, and
    /// an image cannot fold it.
    private static int callPassed(MethodHandle signature, MemorySegment function) {
        try {
            return (int) signature.invokeExact(function);
        } catch (Throwable t) {
            throw new IllegalStateException("goldberry_abi_version() failed", t);
        }
    }
}
