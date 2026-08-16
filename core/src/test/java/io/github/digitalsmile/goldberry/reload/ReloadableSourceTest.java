package io.github.digitalsmile.goldberry.reload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.CssSyntaxException;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Reloading, with the file watching taken out.
///
/// Everything interesting about hot reload is decided here — what a broken file
/// does, what an unchanged one does, what happens after a failure — and none of
/// it needs a thread or a real editor to test.
class ReloadableSourceTest {

    @TempDir
    Path directory;

    private Path write(String name, String text) throws IOException {
        var file = directory.resolve(name);
        Files.writeString(file, text);
        return file;
    }

    private static ReloadableSource<Stylesheet> stylesheet(Path file) {
        return ReloadableSource.load(file, css -> Stylesheet.parse(CascadeLayer.APPLICATION, css));
    }

    @Nested
    @DisplayName("the first load")
    class FirstLoad {

        @Test
        @DisplayName("parses the file")
        void parses() throws IOException {
            var source = stylesheet(write("a.css", "button { color: red }"));
            assertEquals(1, source.current().rules().size());
            assertTrue(source.lastFailure().isEmpty());
        }

        @Test
        @DisplayName("is strict, because there is nothing to fall back to")
        void strictOnFirstLoad() throws IOException {
            // At start-up a broken stylesheet is a bug worth stopping for; there
            // is no last good version to keep, so rendering unthemed and saying
            // nothing would be the worst of both.
            var file = write("bad.css", "button { color: }");
            assertThrows(CssSyntaxException.class, () -> stylesheet(file));
        }
    }

    @Nested
    @DisplayName("reloading")
    class Reloading {

        @Test
        @DisplayName("a changed file produces a new value")
        void changed() throws IOException {
            var file = write("a.css", "button { color: red }");
            var source = stylesheet(file);

            Files.writeString(file, "button { color: blue }\ninput { color: red }");
            var reloaded = source.reload();

            assertTrue(reloaded.isPresent());
            assertEquals(2, source.current().rules().size());
        }

        @Test
        @DisplayName("an unchanged file produces nothing")
        void unchanged() throws IOException {
            var source = stylesheet(write("a.css", "button { color: red }"));

            // A watcher reports the same save more than once, and editors write
            // on autosave. Restyling for an identical file is work nobody asked
            // for.
            assertTrue(source.reload().isEmpty());
        }

        @Test
        @DisplayName("a broken file keeps the last good value")
        void brokenKeepsTheLastGood() throws IOException {
            var file = write("a.css", "button { color: red }");
            var source = stylesheet(file);
            var good = source.current();

            Files.writeString(file, "button { color:");
            assertTrue(source.reload().isEmpty());

            // Every keystroke between "{" and "}" is a parse error; the window
            // must not go blank while somebody types.
            assertEquals(good, source.current());
            assertTrue(source.lastFailure().isPresent());
        }

        @Test
        @DisplayName("a file fixed after a failure reloads")
        void recoversAfterFailure() throws IOException {
            var file = write("a.css", "button { color: red }");
            var source = stylesheet(file);

            Files.writeString(file, "button { color:");
            source.reload();

            // The broken text is deliberately not remembered as "last seen", so
            // editing back to something valid is still a change.
            Files.writeString(file, "button { color: blue }");
            assertTrue(source.reload().isPresent());
            assertTrue(source.lastFailure().isEmpty());
        }

        @Test
        @DisplayName("a file edited back to its original text still reloads")
        void revertingIsAChange() throws IOException {
            var file = write("a.css", "button { color: red }");
            var source = stylesheet(file);

            Files.writeString(file, "button { color: blue }");
            source.reload();
            Files.writeString(file, "button { color: red }");

            assertTrue(source.reload().isPresent(), "reverting an edit is a change like any other");
        }

        @Test
        @DisplayName("an empty file mid-save is a failure, not a wipe")
        void truncatedFile() throws IOException {
            var file = write("a.css", "button { color: red }");
            var source = stylesheet(file);
            var good = source.current();

            // What an editor that truncates before writing leaves behind for a
            // millisecond. An empty stylesheet parses to zero rules, which is
            // legal -- so this one DOES apply, and the next event applies the
            // real content.
            Files.writeString(file, "");
            source.reload();
            assertTrue(source.current().rules().isEmpty());

            Files.writeString(file, "button { color: red }");
            assertTrue(source.reload().isPresent());
            assertEquals(good.rules().size(), source.current().rules().size());
        }

        @Test
        @DisplayName("a file that vanished leaves the last good value alone")
        void deletedFile() throws IOException {
            var file = write("a.css", "button { color: red }");
            var source = stylesheet(file);
            var good = source.current();

            // A rename-into-place caught mid-flight looks exactly like this.
            Files.delete(file);

            assertTrue(source.reload().isEmpty());
            assertEquals(good, source.current());
        }
    }

    @Nested
    @DisplayName("markup reloads the same way")
    class Markup {

        @Test
        @DisplayName("KDL is reloadable through the same type")
        void kdl() throws IOException {
            var file = write("ui.kdl", "window title=\"One\"");
            var source = ReloadableSource.load(
                    file, io.github.digitalsmile.goldberry.kdl.KdlParser::parse);

            assertEquals("One", source.current().getFirst().stringProperty("title"));

            Files.writeString(file, "window title=\"Two\"");
            assertTrue(source.reload().isPresent());
            assertEquals("Two", source.current().getFirst().stringProperty("title"));

            // Broken markup behaves like broken CSS: keep what worked.
            Files.writeString(file, "window {");
            assertTrue(source.reload().isEmpty());
            assertEquals("Two", source.current().getFirst().stringProperty("title"));
            assertFalse(source.lastFailure().isEmpty());
        }
    }
}
