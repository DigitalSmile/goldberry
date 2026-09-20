package io.github.digitalsmile.goldberry.natives.metadata;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
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
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.natives.CompiledClasses;
import io.github.digitalsmile.goldberry.natives.Downcalls;

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
    @DisplayName("declares the ten upcall shapes, Yoga's struct return among them")
    void upcalls() {
        var upcalls = ForeignSurface.upcalls();
        assertAll(
                () -> assertEquals(10, upcalls.size(), upcalls.toString()),
                () -> assertTrue(
                        upcalls.stream()
                                .anyMatch(d -> d.returnLayout()
                                        .filter(StructLayout.class::isInstance)
                                        .isPresent()),
                        "the measure callback returns YGSize by value"),
                () -> assertTrue(upcalls.contains(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS)), "the tray's"),
                // The three ADR-0443 added. Named rather than counted, because a
                // count that moved is the least informative failure there is:
                // these are the shapes GLib's two log hooks and SDL's one have,
                // and a native image that has not been told about them meets an
                // unregistered stub the first time a tray is created.
                () -> assertTrue(
                        upcalls.contains(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, ADDRESS, ADDRESS)),
                        "GLib's GLogFunc"),
                () -> assertTrue(
                        upcalls.contains(FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS)),
                        "GLib's GLogWriterFunc"),
                () -> assertTrue(
                        upcalls.contains(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, JAVA_INT, ADDRESS)),
                        "SDL's SDL_LogOutputFunction"),
                // A page calling back into the application (ADR-0448): the id,
                // the arguments as JSON, and the number the binding was made
                // with.
                () -> assertTrue(
                        upcalls.contains(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS)),
                        "a web page's binding callback"));
    }

    /// The holders that bind a **system** library, which is the case
    /// [#everyHandleIsCovered] cannot see: they keep no `FD_…` handle, because
    /// the library may be absent and the handle is linked only where it is not.
    ///
    /// This is the shape that took the showcase's Linux native image down
    /// (ADR-0451). `PortalSettings` binds `dbus_bus_get` and built its
    /// descriptor inside the constructor, so nothing recorded it on a build
    /// machine without libdbus — and the image met it unregistered the first
    /// time a window asked the desktop about reduced motion.
    ///
    /// **Independent of what this machine has installed**, which is the whole
    /// point and is what makes the test worth having: it passes on a machine
    /// with no libdbus, no libobjc and no user32, and it failed before the fix
    /// on every one of them.
    @Test
    @DisplayName("declares the system libraries' shapes, present or not")
    void systemLibraryShapesAreDeclared() {
        var downcalls = ForeignSurface.downcalls();
        assertAll(
                () -> assertTrue(
                        downcalls.contains(FunctionDescriptor.of(ADDRESS, JAVA_INT, ADDRESS)),
                        "libdbus' dbus_bus_get — the one that crashed"),
                () -> assertTrue(
                        downcalls.contains(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS)),
                        "libdbus' dbus_message_iter_init_append"),
                () -> assertTrue(
                        downcalls.contains(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS)),
                        "libdbus' dbus_message_new_method_call"),
                () -> assertTrue(
                        downcalls.contains(FunctionDescriptor.of(JAVA_BYTE, ADDRESS, ADDRESS)),
                        "libobjc's objc_msgSend returning a BOOL — macOS' reduce-motion answer"),
                () -> assertTrue(
                        downcalls.contains(FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT)),
                        "user32's SystemParametersInfoW — Windows' reduce-motion answer"));
    }

    /// The rule behind the test above, held to the source so that the next
    /// holder cannot reintroduce the defect.
    ///
    /// A descriptor written inline at a `Bindings.link(…)` call site is one
    /// built when the binding runs, and a binding against a system library runs
    /// only where that library is. Written as a constant it is built when the
    /// class is initialised, which [ForeignSurface] does for every class in a
    /// `…calls` package on any machine at all.
    @Test
    @DisplayName("no system-library descriptor is built at its link site")
    void systemLibraryShapesAreConstants() throws IOException {
        var sources = holderSources();
        var inline = new TreeSet<String>();
        try (Stream<Path> files = Files.walk(sources)) {
            for (var file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                var text = Files.readString(file);
                // `Bindings.link(` followed by anything but a bare identifier
                // and its closing bracket. Whitespace and newlines between the
                // two are spotless' business and not this test's.
                var matcher = LINK_ARGUMENT.matcher(text);
                while (matcher.find()) {
                    if (!matcher.group(1).strip().matches("\\w+")) {
                        inline.add(file.getFileName() + ": " + matcher.group(1).strip());
                    }
                }
            }
        }
        assertEquals(
                new TreeSet<String>(),
                inline,
                "these descriptors are built where they are linked, so a machine without the library"
                        + " never records them — declare each as a constant through Bindings.describe."
                        + " See ADR-0451.");
    }

    /// `Bindings.link(` and everything up to its matching close, which for these
    /// three files is always the whole argument: a descriptor, or the name of
    /// one.
    private static final Pattern LINK_ARGUMENT =
            Pattern.compile("Bindings\\.link\\(((?:[^()]|\\([^()]*\\))*)\\)", Pattern.DOTALL);

    /// `natives/src/main/java/.../desktop/calls`, the one package that binds
    /// libraries other than `libgoldberry`.
    private static Path holderSources() {
        var sources = mainSources().resolve("io/github/digitalsmile/goldberry/natives/desktop/calls");
        assertTrue(Files.isDirectory(sources), sources.toString());
        return sources;
    }

    /// The downcall twin of [#ownersCoverTheSources()], and the reason both
    /// exist: a shape reaches the metadata only if it passes a choke point, so
    /// what has to be guarded is that there are no other doors.
    ///
    /// `Linker.downcallHandle` is called in exactly two places — [Downcalls],
    /// which records, and `Bindings`, whose callers declare their shapes as
    /// constants and are held to it by [#systemLibraryShapesAreConstants()]. A
    /// third would be a binding nothing records, which is ADR-0451 again.
    @Test
    @DisplayName("links a downcall in two places, and both of them record")
    void downcallsHaveTwoChokePoints() throws IOException {
        var sources = mainSources();
        var callers = new TreeSet<String>();
        try (Stream<Path> files = Files.walk(sources)) {
            for (var file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                if (Files.readString(file).contains(".downcallHandle(")) {
                    callers.add(file.getFileName().toString());
                }
            }
        }
        assertEquals(
                new TreeSet<>(List.of("Bindings.java", "Downcalls.java")),
                callers,
                "a downcall linked anywhere else is a shape no metadata knows about — see ADR-0451");
    }

    private static Path mainSources() {
        var sources = CompiledClasses.mainClassesBeside(ForeignSurfaceTest.class)
                .getParent()
                .getParent()
                .getParent()
                .getParent()
                .resolve("src/main/java");
        assertTrue(Files.isDirectory(sources), sources.toString());
        return sources;
    }

    /// The owners are a list, so the list is held to the tree: every class that
    /// makes a stub declares its shape, or the image meets it unregistered.
    @Test
    @DisplayName("names every class that makes an upcall stub")
    void ownersCoverTheSources() throws IOException {
        var sources = mainSources();
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
