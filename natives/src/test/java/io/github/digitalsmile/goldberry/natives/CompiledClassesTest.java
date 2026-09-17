package io.github.digitalsmile.goldberry.natives;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// [CompiledClasses] is what two structural tests stand on, so it is checked on
/// its own: a helper that resolved the wrong directory would make both of them
/// pass over an empty tree.
@DisplayName("the compiled classes of this module")
class CompiledClassesTest {

    @Test
    @DisplayName("are found through a class file that is really there")
    void testClassesHoldThisTest() {
        var tests = CompiledClasses.testClassesOf(CompiledClassesTest.class);
        var thisClass = tests.resolve(CompiledClassesTest.class.getName().replace('.', '/') + ".class");
        assertAll(
                () -> assertTrue(Files.isDirectory(tests), tests.toString()),
                () -> assertTrue(Files.isRegularFile(thisClass), thisClass.toString()));
    }

    @Test
    @DisplayName("have the main classes, with the module descriptor, beside the test classes")
    void mainClassesCarryTheDescriptor() {
        var main = CompiledClasses.mainClassesBeside(CompiledClassesTest.class);
        assertAll(
                () -> assertEquals("main", main.getFileName().toString()),
                () -> assertTrue(Files.isRegularFile(main.resolve("module-info.class")), main.toString()));
    }

    @Test
    @DisplayName("come from a URI, which is the form that survives a drive letter")
    void resolvedThroughTheUri() throws Exception {
        // The path a `getPath()` conversion would have refused on Windows is a
        // legal one everywhere else, so what can be pinned here is that the
        // helper and `Path.of(URI)` agree -- the platform does the rest.
        var location =
                CompiledClassesTest.class.getProtectionDomain().getCodeSource().getLocation();
        assertEquals(Path.of(location.toURI()), CompiledClasses.testClassesOf(CompiledClassesTest.class));
    }
}
