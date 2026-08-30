package io.github.digitalsmile.goldberry.natives;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Every holder's `call` is the function its `FD_…` descriptor describes.
///
/// This is the check the old `DowncallsTest` did in the only form it could. When
/// a handle was a shared constant named `INT__PTR_INT`, the only thing checkable
/// was that the *name* matched the layouts, because nothing tied either to a call
/// site — `invokeExact` checks the handle against the caller, at run time, on
/// whichever platform got there first.
///
/// A holder ties them together: the descriptor and the `call` method are two
/// statements of one signature, one in layouts and one in Java types, sitting in
/// the same class. So the check is now the real one — that they agree — and it
/// covers the case the old test could not, a descriptor whose *carrier* is right
/// and whose Java type is not.
///
/// It needs no `libgoldberry`: an unbound handle is linked from a descriptor and
/// names no address, which is the whole point
/// (ADR-0173).
@DisplayName("a bound function's holder")
class HolderShapeTest {

    /// The holder classes, found rather than listed: every nested class of a
    /// `…Calls` type that declares an `FD_…` handle.
    private static List<Class<?>> holders() {
        var root = classesRoot();
        var found = new ArrayList<Class<?>>();
        try (Stream<Path> files = Files.walk(root)) {
            for (var file :
                    files.filter(f -> f.toString().endsWith("Calls.class")).toList()) {
                var binary = root.relativize(file).toString().replace(java.io.File.separatorChar, '.');
                var enclosing = Class.forName(
                        binary.substring(0, binary.length() - ".class".length()),
                        false,
                        HolderShapeTest.class.getClassLoader());
                for (var nested : enclosing.getDeclaredClasses()) {
                    if (handleOf(nested) != null) {
                        found.add(nested);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
        return found;
    }

    private static Path classesRoot() {
        var source = HolderShapeTest.class.getProtectionDomain().getCodeSource();
        if (source == null) {
            fail("no code source for the test classes");
        }
        var main = Path.of(source.getLocation().getPath()).resolveSibling("main");
        if (!Files.isDirectory(main)) {
            fail("expected the main classes beside the test classes, at " + main);
        }
        return main;
    }

    private static java.lang.reflect.Field handleField(Class<?> holder) {
        for (var field : holder.getDeclaredFields()) {
            if (field.getType() == MethodHandle.class && field.getName().startsWith("FD_")) {
                return field;
            }
        }
        return null;
    }

    private static MethodHandle handleOf(Class<?> holder) {
        var field = handleField(holder);
        if (field == null) {
            return null;
        }
        field.setAccessible(true);
        try {
            return (MethodHandle) field.get(null);
        } catch (IllegalAccessException e) {
            throw new AssertionError(holder.getName() + "." + field.getName(), e);
        }
    }

    private static java.lang.reflect.Method callOf(Class<?> holder) {
        for (var method : holder.getDeclaredMethods()) {
            if (method.getName().equals("call") && Modifier.isPublic(method.getModifiers())) {
                return method;
            }
        }
        return null;
    }

    @Test
    @DisplayName("declares a call whose Java types are the handle's, with the address dropped")
    void callMatchesItsHandle() {
        var holders = holders();
        assertTrue(
                holders.size() > 100,
                "found only " + holders.size() + " holders; this test discovers them by walking "
                        + "the compiled classes, and finding almost none means it checks nothing");

        var wrong = new ArrayList<String>();
        for (var holder : holders) {
            var handle = handleOf(holder);
            var call = callOf(holder);
            if (call == null) {
                wrong.add(holder.getName() + " declares a handle and no call");
                continue;
            }
            // The handle is unbound, so it takes the target address first and the
            // C arguments after. `call` supplies the address from the instance,
            // so its type is the handle's with that leading parameter dropped.
            var expected = handle.type().dropParameterTypes(0, 1);
            var actual = MethodType.methodType(call.getReturnType(), call.getParameterTypes());
            if (!expected.equals(actual)) {
                wrong.add(holder.getName() + ": FD_ says " + expected + ", call says " + actual);
            }
        }
        assertTrue(wrong.isEmpty(), String.join("\n", wrong));
    }

    @Test
    @DisplayName("takes the address of the symbol it is named for, and only that")
    void everyHolderKeepsExactlyOneAddress() {
        var wrong = new ArrayList<String>();
        for (var holder : holders()) {
            var addresses = Stream.of(holder.getDeclaredFields())
                    .filter(f -> !Modifier.isStatic(f.getModifiers()))
                    .filter(f -> f.getType() == MemorySegment.class)
                    .toList();
            if (addresses.size() != 1) {
                wrong.add(holder.getName() + " keeps " + addresses.size() + " addresses");
            }
            var handles = Stream.of(holder.getDeclaredFields())
                    .filter(f -> f.getType() == MethodHandle.class)
                    .toList();
            for (var field : handles) {
                if (!Modifier.isStatic(field.getModifiers()) || !Modifier.isFinal(field.getModifiers())) {
                    // This is the whole performance argument: a handle that is not
                    // a static final constant is an interpreted lambda form in a
                    // native image, and 450x slower (ADR-0161, ADR-0173).
                    wrong.add(holder.getName() + "." + field.getName()
                            + " is not static final, so an image cannot fold it");
                }
            }
        }
        assertTrue(wrong.isEmpty(), String.join("\n", wrong));
    }

    @Test
    @DisplayName("links from a descriptor and needs no library to do it")
    void descriptorsLinkWithoutTheLibrary() {
        // Reaching the holders at all forces their initialisers, which is what
        // links every handle. If any descriptor were malformed this would have
        // thrown before the first assertion in this class ran.
        assertEquals(0, holders().stream().filter(h -> handleOf(h) == null).count());
    }
}
