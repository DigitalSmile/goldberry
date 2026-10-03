package dev.goldberry.build.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.goldberry.build.repository.Repository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Every member the build reaches only by weaving or reflection says so to Qodana:
 * a catalogue {@code inflate}, and every {@code @Bind} and {@code @Action} member,
 * carries {@code @SuppressWarnings("unused")}.
 *
 * <p>The woven {@code GoldberryCatalog} calls each {@code inflate} from bytecode
 * that has no source, and {@code RuntimeBinding} finds {@code @Bind} fields and
 * {@code @Action} methods by reflection. IntelliJ's {@code unused} inspection reads
 * source, so to it they are dead. The entry points declared in
 * {@code .idea/misc.xml} were meant to answer that, and Qodana in CI stopped
 * honouring them when the namespace moved to {@code dev.goldberry}: 123 of these
 * members were reported. A suppression on the member is read by every IntelliJ,
 * in CI and in the IDE alike.
 *
 * <p>Read as text over every top-level module's {@code src/main/java}, the only
 * sources Qodana inspects.
 */
@DisplayName("reflective entry points")
class ReflectiveEntryPointsTest {

    /** A widget's catalogue factory: the shape {@code CatalogWeaver} requires. */
    private static final Pattern INFLATE = Pattern.compile("^\\s*public static Widget inflate\\(KdlNode\\b");

    /** A binding annotation on a line of its own, which is how every model writes it. */
    private static final Pattern BINDING = Pattern.compile("^\\s*@(?:Bind|Action)\\(");

    private static final Pattern ANNOTATION = Pattern.compile("^\\s*@\\w");

    private static final Pattern SUPPRESSES_UNUSED = Pattern.compile("@SuppressWarnings\\(\\{?[^)]*\"unused\"");

    /** One member the build reaches without a source caller. */
    private record EntryPoint(int line, boolean suppressed) {}

    @Test
    @DisplayName("every catalogue inflate and every @Bind and @Action member suppresses unused")
    void everyOneIsSuppressed() {
        var missing = new TreeSet<String>();
        var found = 0;
        for (var file : mainSources()) {
            for (var entry : entryPoints(Repository.read(relative(file)))) {
                found++;
                if (!entry.suppressed()) {
                    missing.add(relative(file) + ":" + entry.line());
                }
            }
        }
        // Not vacuous: the widgets alone have more than seventy.
        assertTrue(found > 100, "found only " + found + " entry points; is the pattern still right?");
        assertEquals(
                Set.of(),
                missing,
                "reached by weaving or reflection, so Qodana's `unused` reports them;"
                        + " add @SuppressWarnings(\"unused\") beside the annotation");
    }

    @Nested
    @DisplayName("the detector")
    class Detector {

        @Test
        @DisplayName("finds an inflate with no suppression, and accepts one with it, alone or in a list")
        void inflate() {
            var source = """
                    @Markup("a")
                    record A() {
                        public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) { }
                    }
                    @Markup("b")
                    record B() {
                        @SuppressWarnings("unused")
                        public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) { }
                    }
                    @Markup("c")
                    record C() {
                        @SuppressWarnings({"unchecked", "unused"})
                        public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) { }
                    }
                    """;

            assertEquals(
                    List.of(new EntryPoint(3, false), new EntryPoint(8, true), new EntryPoint(13, true)),
                    entryPoints(source));
        }

        @Test
        @DisplayName("reads the annotations on either side of @Bind and @Action, and ignores doc comments")
        void bindings() {
            var source = """
                    /// @Bind("in.a.comment") is not a binding
                    @Bind("app.a")
                    private int a;
                    @SuppressWarnings("unused")
                    @Bind("app.b")
                    private int b;
                    @Action("app.c")
                    @SuppressWarnings("unused")
                    void c() { }
                    @SuppressWarnings("unchecked")
                    @Action("app.d")
                    void d() { }
                    """;

            assertEquals(
                    List.of(
                            new EntryPoint(2, false),
                            new EntryPoint(5, true),
                            new EntryPoint(7, true),
                            new EntryPoint(11, false)),
                    entryPoints(source));
        }
    }

    /** The entry points in one source, each with whether its annotations suppress {@code unused}. */
    private static List<EntryPoint> entryPoints(String source) {
        var lines = source.split("\n", -1);
        var found = new ArrayList<EntryPoint>();
        for (var i = 0; i < lines.length; i++) {
            if (INFLATE.matcher(lines[i]).find() || BINDING.matcher(lines[i]).find()) {
                found.add(new EntryPoint(i + 1, suppressed(lines, i)));
            }
        }
        return found;
    }

    /** Whether the annotation run that line {@code at} sits in or under suppresses {@code unused}. */
    private static boolean suppressed(String[] lines, int at) {
        var first = at;
        while (first > 0 && ANNOTATION.matcher(lines[first - 1]).find()) {
            first--;
        }
        var last = at;
        while (last + 1 < lines.length && ANNOTATION.matcher(lines[last + 1]).find()) {
            last++;
        }
        for (var i = first; i <= last; i++) {
            if (SUPPRESSES_UNUSED.matcher(lines[i]).find()) {
                return true;
            }
        }
        return false;
    }

    private static List<Path> mainSources() {
        try (Stream<Path> modules = Files.list(Repository.root())) {
            return modules.filter(module -> !module.getFileName().toString().startsWith("."))
                    .map(module -> module.resolve("src/main/java"))
                    .filter(Files::isDirectory)
                    .sorted()
                    .flatMap(ReflectiveEntryPointsTest::javaFiles)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list " + Repository.root(), e);
        }
    }

    private static Stream<Path> javaFiles(Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(file -> file.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList()
                    .stream();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot walk " + root, e);
        }
    }

    private static String relative(Path file) {
        return Repository.root().relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/");
    }
}
