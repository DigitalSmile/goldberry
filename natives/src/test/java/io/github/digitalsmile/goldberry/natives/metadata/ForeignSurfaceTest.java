package io.github.digitalsmile.goldberry.natives.metadata;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.natives.CompiledClasses;

/// [ForeignSurface] is complete or it is worthless: a holder it does not
/// initialise is a `MissingForeignRegistrationError` on somebody's desktop, on
/// the first screen that reaches it.
@DisplayName("the foreign surface")
class ForeignSurfaceTest {

    @Test
    @DisplayName("lists every class in the holder packages, nested holders included")
    void listsTheHolders() {
        var names = ForeignSurface.holderClassNames();
        assertAll(
                () -> assertTrue(names.size() > 100, "found only " + names.size()),
                () -> assertTrue(names.stream().allMatch(ForeignSurface::inHolderPackage), names.toString()),
                () -> assertTrue(
                        names.contains("io.github.digitalsmile.goldberry.natives.md4c.calls.MarkdownCalls$Parse"),
                        "the Markdown parser's holder, which is the one the showcase's trace never reached"),
                () -> assertFalse(names.stream().anyMatch(name -> name.endsWith("module-info"))));
    }

    @Test
    @DisplayName("a holder package is one that ends in .calls, wherever it sits")
    void holderPackages() {
        assertAll(
                () -> assertTrue(ForeignSurface.inHolderPackage("a.b.calls.X")),
                () -> assertTrue(ForeignSurface.inHolderPackage("a.b.calls.X$Y")),
                () -> assertFalse(ForeignSurface.inHolderPackage("a.b.calls")),
                () -> assertFalse(ForeignSurface.inHolderPackage("a.b.callsx.X")),
                () -> assertFalse(ForeignSurface.inHolderPackage("X")));
    }

    @Test
    @DisplayName("knows the Markdown parser's downcall without any run having parsed Markdown")
    void downcallsIncludeTheUnreached() {
        var downcalls = ForeignSurface.downcalls();
        assertAll(
                () -> assertTrue(downcalls.size() > 50, "found only " + downcalls.size()),
                () -> assertTrue(downcalls.contains(FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT))),
                () -> assertEquals(
                        downcalls.size(),
                        new TreeSet<>(downcalls.stream().map(Object::toString).toList()).size(),
                        "no descriptor twice"));
    }

    /// Every holder's `FD_…` handle has a shape, and that shape has to be one
    /// of the descriptors reported — which is what says the registry saw the
    /// holder's initialiser rather than somebody else's.
    @Test
    @DisplayName("reports a descriptor for every holder's handle")
    void everyHandleIsCovered() throws ClassNotFoundException {
        var downcalls = ForeignSurface.downcalls();
        var uncovered = new ArrayList<String>();
        for (var name : ForeignSurface.holderClassNames()) {
            var holder = Class.forName(name, false, ForeignSurfaceTest.class.getClassLoader());
            var handle = handleOf(holder);
            if (handle == null) {
                continue;
            }
            // The address comes first on an unbound handle; the C arguments after.
            var type = handle.type().dropParameterTypes(0, 1);
            if (downcalls.stream()
                    .noneMatch(descriptor -> carriersOf(descriptor).equals(type))) {
                uncovered.add(name + " " + type);
            }
        }
        assertEquals(List.of(), uncovered);
    }

    @Test
    @DisplayName("declares the six upcall shapes, Yoga's struct return among them")
    void upcalls() {
        var upcalls = ForeignSurface.upcalls();
        assertAll(
                () -> assertEquals(6, upcalls.size(), upcalls.toString()),
                () -> assertTrue(
                        upcalls.stream()
                                .anyMatch(d -> d.returnLayout()
                                        .filter(StructLayout.class::isInstance)
                                        .isPresent()),
                        "the measure callback returns YGSize by value"),
                () -> assertTrue(upcalls.contains(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS)), "the tray's"));
    }

    /// The owners are a list, so the list is held to the tree: every class that
    /// makes a stub declares its shape, or the image meets it unregistered.
    @Test
    @DisplayName("names every class that makes an upcall stub")
    void ownersCoverTheSources() throws IOException {
        var sources = CompiledClasses.mainClassesBeside(ForeignSurfaceTest.class)
                .getParent()
                .getParent()
                .getParent()
                .getParent()
                .resolve("src/main/java");
        assertTrue(Files.isDirectory(sources), sources.toString());
        var makers = new TreeSet<String>();
        try (Stream<Path> files = Files.walk(sources)) {
            for (var file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                if (Files.readString(file).contains(".upcallStub(")) {
                    makers.add(binaryName(sources.relativize(file)));
                }
            }
        }
        var owners = new TreeSet<String>();
        for (var owner : ForeignSurface.UPCALL_OWNERS) {
            owners.add(owner.getName());
        }
        assertEquals(makers, owners);
    }

    private static String binaryName(Path relative) {
        var name = new StringBuilder();
        for (var element : relative) {
            if (!name.isEmpty()) {
                name.append('.');
            }
            name.append(element.toString());
        }
        return name.substring(0, name.length() - ".java".length());
    }

    private static MethodHandle handleOf(Class<?> holder) {
        for (var field : holder.getDeclaredFields()) {
            if (field.getName().startsWith("FD_")
                    && field.getType() == MethodHandle.class
                    && Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true);
                try {
                    return (MethodHandle) field.get(null);
                } catch (IllegalAccessException e) {
                    throw new AssertionError(holder.getName(), e);
                }
            }
        }
        return null;
    }

    private static MethodType carriersOf(FunctionDescriptor descriptor) {
        var parameters = descriptor.argumentLayouts().stream()
                .map(ForeignSurfaceTest::carrierOf)
                .toList();
        var returned =
                descriptor.returnLayout().map(ForeignSurfaceTest::carrierOf).orElse(void.class);
        return MethodType.methodType(returned, parameters);
    }

    private static Class<?> carrierOf(MemoryLayout layout) {
        return layout instanceof ValueLayout value ? value.carrier() : java.lang.foreign.MemorySegment.class;
    }
}
