package io.github.digitalsmile.goldberry.natives;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// [CompiledClasses] is what four structural tests stand on, and all four sweep a
/// directory and assert that nothing in it is wrong — so a helper that resolved
/// the wrong directory would make every one of them pass over an empty tree.
///
/// One assertion is enough for that, and this is it: the directory it answers
/// with is really the main output and really has this module's descriptor in it.
/// What the helper does internally — resolving through the code source's URI
/// rather than its path, because `getPath()` refused a drive letter on Windows —
/// is not restated here; that is the implementation, and the platform is what
/// decides whether it was right.
@DisplayName("the compiled classes of this module")
class CompiledClassesTest {

    @Test
    @DisplayName("have the main classes, with the module descriptor, beside the test classes")
    void mainClassesCarryTheDescriptor() {
        var main = CompiledClasses.mainClassesBeside(CompiledClassesTest.class);
        assertAll(
                () -> assertEquals("main", main.getFileName().toString()),
                () -> assertTrue(Files.isRegularFile(main.resolve("module-info.class")), main.toString()));
    }
}
