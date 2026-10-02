package dev.goldberry.natives.desktop.macos;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import dev.goldberry.natives.Downcalls;

/// The Objective-C runtime, through `libobjc` and FFM: classes, selectors,
/// `objc_msgSend` in the handful of shapes AppKit's menus and the
/// notification center need, and a class of this process's own whose methods
/// are upcall stubs.
///
/// **Not a bridge.** `objc_msgSend` has one address and a different C
/// signature for every message, so each shape is a downcall handle of its own,
/// made from the Java types of the arguments: a [MemorySegment] is an `id`, a
/// `SEL` or a pointer, a [Long] is an `NSInteger`/`NSUInteger`, a [Boolean] is
/// a `BOOL`, a [Double] a `CGFloat`. Struct arguments and returns are not
/// supported, and nothing that needs one is called through here.
///
/// The shapes this module sends are also declared as constants, so a native
/// image is told them wherever it is built — see `Downcalls.describe`.
///
/// Main-thread confined, as AppKit is. Work that makes autoreleased objects is
/// wrapped in [#autoreleased], because a call from Java is outside any pool
/// SDL's pump keeps.
public final class ObjC {

    // The shapes AppKit's menus and the notification center are sent in.
    static {
        for (var shape : new FunctionDescriptor[] {
            FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS),
            FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS),
            FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS),
            FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS),
            FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_LONG),
            FunctionDescriptor.of(JAVA_LONG, ADDRESS, ADDRESS),
            FunctionDescriptor.of(JAVA_BYTE, ADDRESS, ADDRESS),
            FunctionDescriptor.of(JAVA_BYTE, ADDRESS, ADDRESS, ADDRESS),
            FunctionDescriptor.ofVoid(ADDRESS, ADDRESS),
            FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS),
            FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS, ADDRESS),
            FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_LONG),
            FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_BYTE),
            FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_LONG, ADDRESS),
            // A completion handler, called with the block and nothing else, and
            // with the block and options.
            FunctionDescriptor.ofVoid(ADDRESS),
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG),
        }) {
            Downcalls.describe(shape);
        }
    }

    private static final FunctionDescriptor P_P = Downcalls.describe(FunctionDescriptor.of(ADDRESS, ADDRESS));
    private static final FunctionDescriptor P_PPL =
            Downcalls.describe(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_LONG));
    private static final FunctionDescriptor B_PPPP =
            Downcalls.describe(FunctionDescriptor.of(JAVA_BYTE, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
    private static final FunctionDescriptor B_PP =
            Downcalls.describe(FunctionDescriptor.of(JAVA_BYTE, ADDRESS, ADDRESS));
    private static final FunctionDescriptor V_P = Downcalls.describe(FunctionDescriptor.ofVoid(ADDRESS));
    private static final FunctionDescriptor P_V = Downcalls.describe(FunctionDescriptor.of(ADDRESS));

    private static final class Holder {
        private static final @Nullable ObjC INSTANCE = load();
    }

    private final MemorySegment msgSend;
    private final MethodHandle getClass;
    private final MethodHandle registerName;
    private final MethodHandle allocateClassPair;
    private final MethodHandle registerClassPair;
    private final MethodHandle addMethod;
    private final MethodHandle getProtocol;
    private final MethodHandle addProtocol;
    private final MethodHandle poolPush;
    private final MethodHandle poolPop;
    private final Map<String, MemorySegment> selectors = new ConcurrentHashMap<>();
    private final Map<FunctionDescriptor, MethodHandle> shapes = new ConcurrentHashMap<>();

    private ObjC(SymbolLookup objc) {
        msgSend = find(objc, "objc_msgSend");
        getClass = bind(objc, "objc_getClass", P_P);
        registerName = bind(objc, "sel_registerName", P_P);
        allocateClassPair = bind(objc, "objc_allocateClassPair", P_PPL);
        registerClassPair = bind(objc, "objc_registerClassPair", V_P);
        addMethod = bind(objc, "class_addMethod", B_PPPP);
        getProtocol = bind(objc, "objc_getProtocol", P_P);
        addProtocol = bind(objc, "class_addProtocol", B_PP);
        poolPush = bind(objc, "objc_autoreleasePoolPush", P_V);
        poolPop = bind(objc, "objc_autoreleasePoolPop", V_P);
    }

    /// The runtime, or empty off macOS and wherever `libobjc` will not bind.
    public static Optional<ObjC> get() {
        return Optional.ofNullable(Holder.INSTANCE);
    }

    @SuppressWarnings("restricted")
    private static @Nullable ObjC load() {
        var os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("mac") && !os.contains("darwin")) {
            return null;
        }
        try {
            return new ObjC(SymbolLookup.libraryLookup("libobjc.A.dylib", Arena.global()));
        } catch (IllegalArgumentException | UnsatisfiedLinkError | IllegalStateException e) {
            return null;
        }
    }

    /// Loads a system framework by path, so that its classes are registered
    /// with the runtime. False when it is not on this machine.
    @SuppressWarnings("restricted")
    public static boolean loadFramework(String name) {
        try {
            SymbolLookup.libraryLookup("/System/Library/Frameworks/" + name + ".framework/" + name, Arena.global());
            return true;
        } catch (IllegalArgumentException | UnsatisfiedLinkError e) {
            return false;
        }
    }

    private static MemorySegment find(SymbolLookup lookup, String name) {
        return lookup.find(name).orElseThrow(() -> new UnsatisfiedLinkError("libobjc does not export " + name));
    }

    private static MethodHandle bind(SymbolLookup lookup, String name, FunctionDescriptor shape) {
        return Downcalls.link(shape).bindTo(find(lookup, name));
    }

    /// The class called `name`, or [MemorySegment#NULL] where none is
    /// registered.
    public MemorySegment cls(String name) {
        try (var arena = Arena.ofConfined()) {
            return (MemorySegment) invoke(getClass, arena.allocateFrom(name));
        }
    }

    /// The selector called `name`.
    public MemorySegment sel(String name) {
        return selectors.computeIfAbsent(name, key -> {
            try (var arena = Arena.ofConfined()) {
                return (MemorySegment) invoke(registerName, arena.allocateFrom(key));
            }
        });
    }

    /// Sends a message that answers an object or a pointer.
    public MemorySegment send(MemorySegment receiver, String selector, Object... arguments) {
        return (MemorySegment) message(ADDRESS, receiver, selector, arguments);
    }

    /// Sends a message that answers an `NSInteger` or `NSUInteger`.
    public long sendLong(MemorySegment receiver, String selector, Object... arguments) {
        return (long) message(JAVA_LONG, receiver, selector, arguments);
    }

    /// Sends a message that answers a `BOOL`.
    public boolean sendBoolean(MemorySegment receiver, String selector, Object... arguments) {
        return (byte) message(JAVA_BYTE, receiver, selector, arguments) != 0;
    }

    /// Sends a message that answers nothing.
    public void sendVoid(MemorySegment receiver, String selector, Object... arguments) {
        message(null, receiver, selector, arguments);
    }

    /// An `NSString` holding `text`, autoreleased: call inside [#autoreleased].
    public MemorySegment string(String text) {
        try (var arena = Arena.ofConfined()) {
            return send(cls("NSString"), "stringWithUTF8String:", arena.allocateFrom(text));
        }
    }

    /// The text of an `NSString`, or null for nil.
    @SuppressWarnings("restricted")
    public @Nullable String text(MemorySegment string) {
        if (string.address() == 0) {
            return null;
        }
        var bytes = send(string, "UTF8String");
        return bytes.address() == 0
                ? null
                : bytes.reinterpret(Integer.MAX_VALUE).getString(0);
    }

    /// Runs `work` inside an autorelease pool and answers what it answered.
    public <T> T autoreleased(Supplier<T> work) {
        var pool = (MemorySegment) invoke(poolPush);
        try {
            return work.get();
        } finally {
            invoke(poolPop, pool);
        }
    }

    /// Registers a class of this process's own, `name`, under `superclass`,
    /// adopting `protocol` if it is not null, with `methods` as its instance
    /// methods — each an upcall stub with the Objective-C type encoding beside
    /// it. Answers the class already registered under `name` if there is one.
    public MemorySegment defineClass(
            String name, String superclass, @Nullable String protocol, Map<String, Method> methods) {
        var existing = cls(name);
        if (existing.address() != 0) {
            return existing;
        }
        try (var arena = Arena.ofConfined()) {
            var made = (MemorySegment) invoke(allocateClassPair, cls(superclass), arena.allocateFrom(name), 0L);
            if (made.address() == 0) {
                throw new IllegalStateException("the runtime would not make a class called " + name);
            }
            if (protocol != null) {
                var adopted = (MemorySegment) invoke(getProtocol, arena.allocateFrom(protocol));
                if (adopted.address() != 0) {
                    invoke(addProtocol, made, adopted);
                }
            }
            for (var method : methods.entrySet()) {
                invoke(
                        addMethod,
                        made,
                        sel(method.getKey()),
                        method.getValue().implementation(),
                        arena.allocateFrom(method.getValue().types()));
            }
            invoke(registerClassPair, made);
            return made;
        }
    }

    /// One method of a class made by [#defineClass]: its implementation, an
    /// upcall stub that takes `self` and `_cmd` first, and its type encoding.
    public record Method(MemorySegment implementation, String types) {}

    /// Calls a block an Objective-C API handed over — a completion handler —
    /// with `arguments` after the block itself.
    ///
    /// A block is a struct whose fourth field is the function to call, with the
    /// block as its first argument: Clang's block ABI, which is what the
    /// runtime itself calls through.
    @SuppressWarnings("restricted")
    public void callBlock(MemorySegment block, Object... arguments) {
        if (block.address() == 0) {
            return;
        }
        var invoke = block.reinterpret(BLOCK.byteSize()).get(ADDRESS, INVOKE_OFFSET);
        var layouts = new MemoryLayout[arguments.length + 1];
        var values = new Object[arguments.length + 2];
        layouts[0] = ADDRESS;
        values[0] = invoke;
        values[1] = block;
        for (var index = 0; index < arguments.length; index++) {
            layouts[index + 1] = layout(arguments[index]);
            values[index + 2] = arguments[index] instanceof Boolean flag ? (byte) (flag ? 1 : 0) : arguments[index];
        }
        invoke(handle(FunctionDescriptor.ofVoid(layouts)), values);
    }

    /// A block of this process's own that calls `invoke` — a stub taking the
    /// block first — for an API that wants a completion handler. Global, like
    /// a block literal that captures nothing, so the runtime neither copies nor
    /// frees it.
    @SuppressWarnings("restricted")
    public MemorySegment globalBlock(MemorySegment invoke) {
        var isa = Linker.nativeLinker()
                .defaultLookup()
                .find("_NSConcreteGlobalBlock")
                .orElseThrow(() -> new UnsatisfiedLinkError("no _NSConcreteGlobalBlock in this process"));
        var descriptor = Arena.global().allocate(16);
        descriptor.set(JAVA_LONG, 0, 0);
        descriptor.set(JAVA_LONG, 8, BLOCK.byteSize());
        var block = Arena.global().allocate(BLOCK);
        block.set(ADDRESS, 0, isa);
        block.set(JAVA_INT, 8, BLOCK_IS_GLOBAL);
        block.set(JAVA_INT, 12, 0);
        block.set(ADDRESS, INVOKE_OFFSET, invoke);
        block.set(ADDRESS, 24, descriptor);
        return block;
    }

    /// `isa`, `flags`, `reserved`, `invoke`, `descriptor`.
    private static final MemoryLayout BLOCK = MemoryLayout.structLayout(ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS);

    private static final long INVOKE_OFFSET = 16;

    /// `BLOCK_IS_GLOBAL`.
    private static final int BLOCK_IS_GLOBAL = 1 << 28;

    private Object message(
            @Nullable MemoryLayout returns, MemorySegment receiver, String selector, Object... arguments) {
        var layouts = new MemoryLayout[arguments.length + 2];
        layouts[0] = ADDRESS;
        layouts[1] = ADDRESS;
        var values = new Object[arguments.length + 3];
        values[0] = msgSend;
        values[1] = receiver;
        values[2] = sel(selector);
        for (var index = 0; index < arguments.length; index++) {
            layouts[index + 2] = layout(arguments[index]);
            values[index + 3] = arguments[index] instanceof Boolean flag ? (byte) (flag ? 1 : 0) : arguments[index];
        }
        var shape = returns == null ? FunctionDescriptor.ofVoid(layouts) : FunctionDescriptor.of(returns, layouts);
        return invoke(handle(shape), values);
    }

    private MethodHandle handle(FunctionDescriptor shape) {
        return shapes.computeIfAbsent(shape, Downcalls::link);
    }

    private static MemoryLayout layout(Object argument) {
        return switch (argument) {
            case MemorySegment _ -> ADDRESS;
            case Long _ -> JAVA_LONG;
            case Integer _ -> JAVA_INT;
            case Boolean _ -> JAVA_BYTE;
            case Double _ -> JAVA_DOUBLE;
            default -> throw new IllegalArgumentException("no Objective-C type for " + argument.getClass());
        };
    }

    private static Object invoke(MethodHandle handle, Object... arguments) {
        try {
            var result = handle.invokeWithArguments(arguments);
            return result == null ? Boolean.TRUE : result;
        } catch (Throwable e) {
            throw new IllegalStateException("an Objective-C call failed", e);
        }
    }
}
