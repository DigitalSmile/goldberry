package io.github.digitalsmile.goldberry.natives;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.MemorySegment;
import java.lang.module.ModuleDescriptor;
import java.lang.reflect.Executable;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The rule this module exists to enforce, checked rather than asserted in prose.
///
/// `docs/ARCHITECTURE.md` §3.1 says a raw `MemorySegment` never leaves this
/// module, and the module descriptor is what enforces it: the binding classes
/// live in packages that are not exported, and the wrapper packages that *are*
/// exported traffic in Java types. Until
/// [ADR-0172](../../../../../../book/src/adr/0172-a-package-is-a-role-and-the-module-is-the-fence.md)
/// each library was one package, so the rule was mostly kept by a binding class
/// being package-private. Splitting each library into the wrappers that hold a
/// handle and the enums that hold nothing moved several types across an export
/// boundary, and "several types" is exactly the size of mistake a compiler does
/// not catch — `Blend2D` stayed package-private, but nothing would have
/// complained if a wrapper carrying a `MemorySegment` had been carried out with
/// the enums.
///
/// So the boundary is a test now. It reads this module's own descriptor and its
/// own class files: no list of package names is written here, which means a
/// package added tomorrow is checked tomorrow.
@DisplayName("the exported native surface")
class ExportedSurfaceTest {

    /// Where this module's compiled classes are, found through a class rather
    /// than through a path, so it works from Gradle and from an IDE alike.
    private static Path classesRoot() {
        var source = ExportedSurfaceTest.class.getProtectionDomain().getCodeSource();
        if (source == null) {
            fail("no code source for the test classes; cannot locate the module's own classes");
        }
        // `.../build/classes/java/test` -> `.../build/classes/java/main`
        var tests = Path.of(source.getLocation().getPath());
        var main = tests.resolveSibling("main");
        if (!Files.isDirectory(main)) {
            fail("expected the main classes beside the test classes, at " + main);
        }
        return main;
    }

    private static ModuleDescriptor descriptor(Path root) {
        var moduleInfo = root.resolve("module-info.class");
        assertTrue(Files.isRegularFile(moduleInfo),
                "this module ships a descriptor; nothing below means anything without it");
        try (var in = Files.newInputStream(moduleInfo)) {
            return ModuleDescriptor.read(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /// Every class file under `root`, as a binary name.
    private static List<String> classNames(Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".class"))
                    .map(root::relativize)
                    .map(Path::toString)
                    .map(name -> name.substring(0, name.length() - ".class".length()))
                    .map(name -> name.replace(java.io.File.separatorChar, '.'))
                    .filter(name -> !name.equals("module-info"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String packageOf(String binaryName) {
        var dot = binaryName.lastIndexOf('.');
        return dot < 0 ? "" : binaryName.substring(0, dot);
    }

    private static Set<String> exportedPackages() {
        var root = classesRoot();
        return descriptor(root).exports().stream()
                .filter(e -> !e.isQualified())
                .map(ModuleDescriptor.Exports::source)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Test
    @DisplayName("carries no MemorySegment in any signature an application can reach")
    void noForeignMemoryInTheExportedSurface() throws ClassNotFoundException {
        var root = classesRoot();
        var exported = exportedPackages();
        var leaks = new ArrayList<String>();

        for (var name : classNames(root)) {
            if (!exported.contains(packageOf(name))) {
                continue;
            }
            var type = Class.forName(name, false, ExportedSurfaceTest.class.getClassLoader());
            if (!isReachable(type)) {
                continue;
            }
            for (var field : type.getDeclaredFields()) {
                if (isReachable(field.getModifiers()) && field.getType() == MemorySegment.class) {
                    leaks.add(name + "." + field.getName());
                }
            }
            Stream.<Executable[]>of(type.getDeclaredMethods(), type.getDeclaredConstructors())
                    .flatMap(Stream::of)
                    .filter(m -> isReachable(m.getModifiers()))
                    .forEach(m -> {
                        if (mentionsSegment(m)) {
                            leaks.add(name + "." + m.getName() + "(...)");
                        }
                    });
        }

        assertTrue(leaks.isEmpty(),
                "these are reachable from outside :natives and traffic in raw foreign memory, "
                        + "which is the one thing §3.1 says cannot happen: " + leaks);
    }

    /// A member an application could actually call: `public` or `protected`, on a
    /// type that is itself public. Package-private members of a public class in an
    /// exported package are invisible outside their package, so they are not part
    /// of the surface.
    private static boolean isReachable(int modifiers) {
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
    }

    private static boolean isReachable(Class<?> type) {
        for (var t = type; t != null; t = t.getEnclosingClass()) {
            if (!isReachable(t.getModifiers())) {
                return false;
            }
        }
        return true;
    }

    private static boolean mentionsSegment(Executable member) {
        if (member instanceof java.lang.reflect.Method method
                && method.getReturnType() == MemorySegment.class) {
            return true;
        }
        for (var parameter : member.getParameterTypes()) {
            if (parameter == MemorySegment.class) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("keeps each library's binding class out of reach")
    void bindingClassesAreNotPublic() throws ClassNotFoundException {
        var bindings = List.of(
                "io.github.digitalsmile.goldberry.natives.blend2d.Blend2D",
                "io.github.digitalsmile.goldberry.natives.yoga.Yoga",
                "io.github.digitalsmile.goldberry.natives.harfbuzz.HarfBuzz");
        for (var name : bindings) {
            var type = Class.forName(name, false, ExportedSurfaceTest.class.getClassLoader());
            assertFalse(Modifier.isPublic(type.getModifiers()),
                    name + " is the raw downcall layer. It is package-private so that only the "
                            + "wrappers beside it can call it; making it public to reach it from "
                            + "another package would be the split going one class too far.");
        }
    }
}
