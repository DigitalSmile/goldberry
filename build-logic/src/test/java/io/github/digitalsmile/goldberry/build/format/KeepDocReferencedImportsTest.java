package io.github.digitalsmile.goldberry.build.format;

import java.io.File;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.diffplug.spotless.FormatterStep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("a step that keeps imports /// comments use")
class KeepDocReferencedImportsTest {

    private static final File FILE = new File("A.java");

    /**
     * Stands in for palantir's step at its blindest: the class below uses none of
     * its imports in code, so every one of them goes -- including the one a
     * {@code ///} comment links to, which is the loss being repaired.
     */
    record DroppingStep() implements FormatterStep {

        @Override
        public String getName() {
            return "dropping";
        }

        @Override
        public String format(String rawUnix, File file) {
            return rawUnix.replaceAll("(?m)^import [\\w.]+;\\n", "");
        }

        @Override
        public void close() {
        }
    }

    /** A delegate that reports "no change", as Spotless steps may. */
    record UnchangedStep() implements FormatterStep {

        @Override
        public String getName() {
            return "unchanged";
        }

        @Override
        public String format(String rawUnix, File file) {
            return null;
        }

        @Override
        public void close() {
        }
    }

    @Test
    @DisplayName("repairs its delegate's output")
    void repairs() throws Exception {
        var source = """
                package p;

                import a.Registry;
                import a.Unused;

                /// See [Registry].
                final class A {}
                """;

        var out = new KeepDocReferencedImports(new DroppingStep()).format(source, FILE);

        assertTrue(out.contains("import a.Registry;"), out);
        assertFalse(out.contains("import a.Unused;"), out);
    }

    @Test
    @DisplayName("passes on a delegate's \"no change\"")
    void passesNull() throws Exception {
        assertNull(new KeepDocReferencedImports(new UnchangedStep()).format("class A {}\n", FILE));
    }

    @Test
    @DisplayName("is named and compared by its delegate")
    void identity() {
        var step = new KeepDocReferencedImports(new DroppingStep());
        assertEquals("dropping", step.getName());
        assertEquals(new KeepDocReferencedImports(new DroppingStep()), step);
    }

    @Test
    @DisplayName("refuses a null delegate")
    void nullDelegate() {
        assertThrows(NullPointerException.class, () -> new KeepDocReferencedImports(null));
        assertThrows(NullPointerException.class, () -> KeepDocReferencedImports.palantir(null));
    }
}
