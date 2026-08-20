package io.github.digitalsmile.goldberry.natives;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/// One downcall handle per **signature**, shared by every symbol that has it.
///
/// A binding class keeps the *address* of each function it bound, and calls it
/// through the constant for its signature:
///
/// ```java
/// this.contextEnd = Downcalls.symbol(lookup, "bl_context_end");
/// ...
/// check("bl_context_end", (int) Downcalls.INT__PTR.invokeExact(contextEnd, context));
/// ```
///
/// A name is `<return>__<arguments>`, in C's terms rather than Java's, so
/// `INT__PTR_PTR_INT` is `int f(void*, void*, int)` and `INT__VOID` is
/// `int f(void)`. `BOOL` is C's `_Bool` — **one byte**, not the four an `int`
/// would take — and `PTR` is any pointer.
///
/// ## Why the handle is not kept beside its symbol
///
/// Because a `MethodHandle` that is not a **compile-time constant** is not a
/// call. It is an interpreted lambda form, and GraalVM's native image has no
/// second chance to make it one — there is no JIT behind it to notice that the
/// receiver never changes.
///
/// Measured on this machine, GraalVM CE 25.2.4, calling a trivial
/// `int f(void)` twenty million times
/// ([ADR-0161](../../../../../../book/src/adr/0161-a-downcall-handle-is-a-constant-or-it-is-not-a-call.md)):
///
/// | how the handle is held                  | JVM   | native image |
/// |-----------------------------------------|-------|--------------|
/// | bound to its address, built at run time | 10 ns | **4560 ns**  |
/// | unbound, built at run time              | 10 ns | **4500 ns**  |
/// | unbound, built at **image build time**  | 10 ns | **10 ns**    |
///
/// Both halves are needed. An [unbound][Linker#downcallHandle(FunctionDescriptor,Linker.Option...)]
/// handle takes the target address as its leading argument, so it depends on no
/// `SymbolLookup` and the class holding it can be initialised while the image is
/// being built — which is what makes it a constant the compiler can lower into a
/// direct call to the generated stub. A handle *bound* to an address cannot be:
/// the address is only known once `libgoldberry` has been `dlopen`ed, which is at
/// run time by definition.
///
/// So the image build must be told
/// `--initialize-at-build-time=io.github.digitalsmile.goldberry.natives.Downcalls`.
/// Without it every constant here is built at run time and the whole file buys
/// nothing — see the middle row, and note that nothing *fails*: the image builds,
/// runs and paints correctly at a fortieth of the speed. This module ships the
/// flag itself, in
/// `META-INF/native-image/io.github.digitalsmile/goldberry-natives/native-image.properties`,
/// so an application building an image never has to know (ADR-0160).
///
/// Nothing about this costs the JVM anything: an unbound handle is the same 10 ns
/// there, because the address arrives as a value the JIT folds just as it folded
/// the bound handle's.
///
/// ## Why one per signature rather than one per function
///
/// Because **the constant has to be read by the method that calls it.** The same
/// measurement, one level down: a constant handle passed *into* a three-line
/// helper costs 810 ns in an image against 8.9 ns when the helper names it
/// itself. The JVM inlines and folds either way; native-image does not.
///
/// The binding classes call through shape-generic helpers — Yoga's `call`,
/// `SdlVideo`'s `callBoolean`, `Blend2D`'s `invoke` — precisely so that a hundred
/// call sites share one `try`/`catch`. A handle named for one C function could
/// not be read inside a helper shared by forty of them, so per-function naming
/// would mean deleting every helper and inlining it. The signature is what those
/// helpers have in common, so the signature is what the constants are named for.
///
/// ## Adding one
///
/// **When a new signature appears, not when a new symbol does** — 134 bindings
/// share the 56 below. `DowncallsTest` checks that every name here describes the
/// descriptor beside it, so a constant whose name and layouts disagree fails the
/// build rather than the first call that reaches it.
public final class Downcalls {

    private static final Linker LINKER = Linker.nativeLinker();

    // The carriers, under the names the constants below are spelled with. C's
    // words and not Java's: a binding is written against a C prototype, and
    // `BOOL` is one byte there whatever `JAVA_BOOLEAN` suggests.
    private static final MemoryLayout BOOL = ValueLayout.JAVA_BOOLEAN;
    private static final MemoryLayout SHORT = ValueLayout.JAVA_SHORT;
    private static final MemoryLayout INT = ValueLayout.JAVA_INT;
    private static final MemoryLayout LONG = ValueLayout.JAVA_LONG;
    private static final MemoryLayout FLOAT = ValueLayout.JAVA_FLOAT;
    private static final MemoryLayout DOUBLE = ValueLayout.JAVA_DOUBLE;
    private static final MemoryLayout PTR = ValueLayout.ADDRESS;

    private Downcalls() {
    }

    /// The address of `symbol`, for a binding class to keep and pass as the
    /// leading argument of the constant for its signature.
    ///
    /// The failure is the one every binding used to raise for itself, in the
    /// same words: a symbol missing from `libgoldberry` is an export list that is
    /// wrong, and the message names the file to fix.
    ///
    /// @throws UnsatisfiedLinkError if the library does not export it
    public static MemorySegment symbol(SymbolLookup lookup, String symbol) {
        return lookup.find(symbol).orElseThrow(() -> new UnsatisfiedLinkError(
                "libgoldberry does not export " + symbol
                        + " — is it listed in natives/src/main/cmake/exports/goldberry.symbols?"));
    }

    /// The address of `symbol`, or null when this library does not export it.
    ///
    /// For the handful of calls the toolkit can do without — see
    /// [io.github.digitalsmile.goldberry.natives.sdl.SdlVideo]'s display-mode
    /// pair, which feed the frame pacer and have a defined answer for "the
    /// platform will not say".
    public static MemorySegment optionalSymbol(SymbolLookup lookup, String symbol) {
        return lookup.find(symbol).orElse(null);
    }

    // Restricted: linking a foreign signature is this class's entire purpose. No
    // address is named here and none is checked -- the obligation that the
    // signature matches the C prototype sits at the call site, where the
    // argument types are, and ADR-0010 accepted it there.
    @SuppressWarnings("restricted")
    private static MethodHandle of(MemoryLayout returns, MemoryLayout... arguments) {
        return LINKER.downcallHandle(FunctionDescriptor.of(returns, arguments));
    }

    @SuppressWarnings("restricted")
    private static MethodHandle ofVoid(MemoryLayout... arguments) {
        return LINKER.downcallHandle(FunctionDescriptor.ofVoid(arguments));
    }

    // --- signatures ---------------------------------------------------------

    public static final MethodHandle VOID__INT = ofVoid(INT);
    public static final MethodHandle VOID__PTR = ofVoid(PTR);
    public static final MethodHandle VOID__VOID = ofVoid();
    public static final MethodHandle VOID__PTR_INT = ofVoid(PTR, INT);
    public static final MethodHandle VOID__PTR_PTR = ofVoid(PTR, PTR);
    public static final MethodHandle VOID__PTR_BOOL = ofVoid(PTR, BOOL);
    public static final MethodHandle VOID__PTR_FLOAT = ofVoid(PTR, FLOAT);
    public static final MethodHandle VOID__PTR_INT_INT = ofVoid(PTR, INT, INT);
    public static final MethodHandle VOID__PTR_PTR_PTR = ofVoid(PTR, PTR, PTR);
    public static final MethodHandle VOID__PTR_PTR_LONG = ofVoid(PTR, PTR, LONG);
    public static final MethodHandle VOID__PTR_INT_FLOAT = ofVoid(PTR, INT, FLOAT);
    public static final MethodHandle VOID__PTR_PTR_PTR_INT = ofVoid(PTR, PTR, PTR, INT);
    public static final MethodHandle VOID__PTR_FLOAT_FLOAT_INT = ofVoid(PTR, FLOAT, FLOAT, INT);
    public static final MethodHandle VOID__PTR_PTR_INT_INT_INT = ofVoid(PTR, PTR, INT, INT, INT);
    public static final MethodHandle VOID__PTR_FLOAT_INT_FLOAT_INT_PTR_PTR =
            ofVoid(PTR, FLOAT, INT, FLOAT, INT, PTR, PTR);

    public static final MethodHandle BOOL__INT = of(BOOL, INT);
    public static final MethodHandle BOOL__PTR = of(BOOL, PTR);
    public static final MethodHandle BOOL__VOID = of(BOOL);
    public static final MethodHandle BOOL__INT_PTR = of(BOOL, INT, PTR);
    public static final MethodHandle BOOL__PTR_INT = of(BOOL, PTR, INT);
    public static final MethodHandle BOOL__PTR_PTR = of(BOOL, PTR, PTR);
    public static final MethodHandle BOOL__PTR_INT_INT = of(BOOL, PTR, INT, INT);
    public static final MethodHandle BOOL__PTR_PTR_INT = of(BOOL, PTR, PTR, INT);
    public static final MethodHandle BOOL__PTR_PTR_PTR = of(BOOL, PTR, PTR, PTR);

    public static final MethodHandle SHORT__VOID = of(SHORT);

    public static final MethodHandle INT__INT = of(INT, INT);
    public static final MethodHandle INT__PTR = of(INT, PTR);
    public static final MethodHandle INT__VOID = of(INT);
    public static final MethodHandle INT__INT_PTR = of(INT, INT, PTR);
    public static final MethodHandle INT__PTR_INT = of(INT, PTR, INT);
    public static final MethodHandle INT__PTR_PTR = of(INT, PTR, PTR);
    public static final MethodHandle INT__PTR_DOUBLE = of(INT, PTR, DOUBLE);
    public static final MethodHandle INT__PTR_INT_PTR = of(INT, PTR, INT, PTR);
    public static final MethodHandle INT__PTR_PTR_INT = of(INT, PTR, PTR, INT);
    public static final MethodHandle INT__PTR_PTR_PTR = of(INT, PTR, PTR, PTR);
    public static final MethodHandle INT__PTR_PTR_FLOAT = of(INT, PTR, PTR, FLOAT);
    public static final MethodHandle INT__PTR_PTR_PTR_INT = of(INT, PTR, PTR, PTR, INT);
    public static final MethodHandle INT__PTR_PTR_PTR_PTR = of(INT, PTR, PTR, PTR, PTR);
    public static final MethodHandle INT__PTR_DOUBLE_DOUBLE = of(INT, PTR, DOUBLE, DOUBLE);
    public static final MethodHandle INT__PTR_PTR_PTR_PTR_INT = of(INT, PTR, PTR, PTR, PTR, INT);
    public static final MethodHandle INT__PTR_PTR_LONG_PTR_PTR = of(INT, PTR, PTR, LONG, PTR, PTR);
    public static final MethodHandle INT__PTR_DOUBLE_DOUBLE_DOUBLE_DOUBLE =
            of(INT, PTR, DOUBLE, DOUBLE, DOUBLE, DOUBLE);
    public static final MethodHandle INT__PTR_INT_INT_INT_PTR_LONG_INT_PTR_PTR =
            of(INT, PTR, INT, INT, INT, PTR, LONG, INT, PTR, PTR);
    public static final MethodHandle INT__PTR_DOUBLE_DOUBLE_DOUBLE_DOUBLE_DOUBLE_DOUBLE =
            of(INT, PTR, DOUBLE, DOUBLE, DOUBLE, DOUBLE, DOUBLE, DOUBLE);
    public static final MethodHandle INT__PTR_DOUBLE_DOUBLE_DOUBLE_BOOL_BOOL_DOUBLE_DOUBLE =
            of(INT, PTR, DOUBLE, DOUBLE, DOUBLE, BOOL, BOOL, DOUBLE, DOUBLE);

    public static final MethodHandle LONG__PTR = of(LONG, PTR);

    public static final MethodHandle FLOAT__PTR = of(FLOAT, PTR);
    public static final MethodHandle FLOAT__PTR_INT = of(FLOAT, PTR, INT);

    public static final MethodHandle PTR__INT = of(PTR, INT);
    public static final MethodHandle PTR__PTR = of(PTR, PTR);
    public static final MethodHandle PTR__VOID = of(PTR);
    public static final MethodHandle PTR__PTR_INT = of(PTR, PTR, INT);
    public static final MethodHandle PTR__PTR_PTR = of(PTR, PTR, PTR);
    public static final MethodHandle PTR__PTR_INT_INT_LONG = of(PTR, PTR, INT, INT, LONG);
    public static final MethodHandle PTR__PTR_INT_INT_PTR_PTR = of(PTR, PTR, INT, INT, PTR, PTR);
    public static final MethodHandle PTR__PTR_INT_INT_INT_INT_LONG =
            of(PTR, PTR, INT, INT, INT, INT, LONG);
}
