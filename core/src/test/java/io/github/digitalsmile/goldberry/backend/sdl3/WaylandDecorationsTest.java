package io.github.digitalsmile.goldberry.backend.sdl3;

import io.github.digitalsmile.goldberry.backend.sdl3.WaylandDecorations.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Tests for [WaylandDecorations].
///
/// The behaviour worth pinning down is not "does it warn" but *when it stays
/// quiet*. This message is only useful if it is true every time it appears, so
/// the cases that must produce nothing — X11, a machine whose plugin directory
/// could not be located, a directory with a working plugin in it — carry as much
/// weight here as the one that produces the warning.
@DisplayName("WaylandDecorations")
class WaylandDecorationsTest {

    private static final String GTK = WaylandDecorations.GTK_PLUGIN;
    private static final String CAIRO = "libdecor-cairo.so";

    /// What the stock `java` launcher gives you: main on a thread it created.
    private static final Optional<Boolean> CREATED_THREAD = Optional.of(false);

    /// What an embedded `JNI_CreateJavaVM` launcher gives you.
    private static final Optional<Boolean> INITIAL_THREAD = Optional.of(true);

    /// A machine whose /proc could not answer.
    private static final Optional<Boolean> UNKNOWN_THREAD = Optional.empty();

    private static Optional<List<String>> plugins(String... names) {
        return Optional.of(List.of(names));
    }

    private static Optional<List<String>> noDirectory() {
        return Optional.empty();
    }

    @Nested
    @DisplayName("verdict")
    class VerdictOf {

        @Test
        @DisplayName("the GTK plugin alone means no decorations under the stock java launcher")
        void gtkAloneMeansUndecorated() {
            assertEquals(Verdict.UNDECORATED, WaylandDecorations.verdict("wayland", plugins(GTK), CREATED_THREAD));
        }

        @Test
        @DisplayName("the GTK plugin alone is fine on the initial thread — it is the launcher, not the JVM")
        void gtkAloneWorksOnTheInitialThread() {
            // Measured, not assumed: an embedded JNI_CreateJavaVM launcher runs
            // Java on the primordial thread, and there libdecor loads the GTK
            // plugin and draws decorations that match the desktop. Warning in
            // that configuration would be plainly wrong -- the window has a
            // titlebar the user is looking at.
            assertEquals(Verdict.DECORATED, WaylandDecorations.verdict("wayland", plugins(GTK), INITIAL_THREAD));
        }

        @Test
        @DisplayName("says nothing when it cannot tell which thread this is")
        void staysQuietWithNoThreadAnswer() {
            // Only matters when GTK is the sole candidate, because that is the
            // only case whose answer depends on the thread.
            assertEquals(Verdict.UNKNOWN, WaylandDecorations.verdict("wayland", plugins(GTK), UNKNOWN_THREAD));
        }

        @Test
        @DisplayName("an empty directory is undecorated whatever thread asks")
        void noPluginsIgnoresTheThread() {
            // Nothing to load is nothing to load; the GTK check never comes into
            // it, so an unknown thread must not soften this to UNKNOWN.
            assertAll(
                    () -> assertEquals(Verdict.UNDECORATED,
                            WaylandDecorations.verdict("wayland", plugins(), INITIAL_THREAD)),
                    () -> assertEquals(Verdict.UNDECORATED,
                            WaylandDecorations.verdict("wayland", plugins(), UNKNOWN_THREAD)));
        }

        @Test
        @DisplayName("an empty plugin directory means no decorations")
        void emptyDirectoryMeansUndecorated() {
            assertEquals(Verdict.UNDECORATED, WaylandDecorations.verdict("wayland", plugins(), CREATED_THREAD));
        }

        @Test
        @DisplayName("the Cairo plugin alongside GTK is enough: libdecor falls through to it")
        void cairoAlongsideGtkIsEnough() {
            // Verified against the real libdecor: with both present and the caller
            // off the initial thread, libdecor reports the GTK failure and then
            // decorates anyway, and its "falling back on no decorations" line is
            // not printed.
            assertEquals(Verdict.DECORATED, WaylandDecorations.verdict("wayland", plugins(GTK, CAIRO), CREATED_THREAD));
        }

        @Test
        @DisplayName("any non-GTK plugin counts, including one that does not exist yet")
        void anyOtherPluginCounts() {
            // Not an allow-list of known plugin names. A distribution shipping its
            // own plugin should not trip a warning about a missing one, and this
            // code cannot be updated on their release schedule.
            assertEquals(Verdict.DECORATED,
                    WaylandDecorations.verdict("wayland", plugins(GTK, "libdecor-something-new.so"), CREATED_THREAD));
        }

        @ParameterizedTest(name = "the {0} driver is not libdecor''s business")
        @ValueSource(strings = {"x11", "cocoa", "windows", "offscreen", "dummy"})
        @DisplayName("says nothing about a driver that does not use libdecor")
        void staysQuietOffWayland(String driver) {
            // X11 windows are decorated by the window manager, and never reach
            // libdecor -- which is exactly why forcing x11 is the workaround the
            // message suggests.
            assertEquals(Verdict.UNKNOWN, WaylandDecorations.verdict(driver, plugins(GTK), CREATED_THREAD));
        }

        @Test
        @DisplayName("says nothing when the plugin directory could not be located")
        void staysQuietWithNoDirectory() {
            // The difference between "there are no plugins" and "I do not know
            // where the plugins live" is the whole reason the verdict is not a
            // boolean.
            assertEquals(Verdict.UNKNOWN, WaylandDecorations.verdict("wayland", noDirectory(), CREATED_THREAD));
        }

        @Test
        @DisplayName("says nothing when the driver is unknown")
        void staysQuietWithNoDriver() {
            assertEquals(Verdict.UNKNOWN, WaylandDecorations.verdict(null, plugins(GTK), CREATED_THREAD));
        }

        @Test
        @DisplayName("ignores files in the directory that are not plugins")
        void ignoresNonPlugins() {
            assertEquals(Verdict.UNDECORATED,
                    WaylandDecorations.verdict("wayland", plugins(GTK, "README", "libdecor-cairo.so.disabled"), CREATED_THREAD));
        }
    }

    @Nested
    @DisplayName("the message")
    class Message {

        @Test
        @DisplayName("names the symptom, the cause, and the package that fixes it")
        void namesSymptomCauseAndFix() {
            var message = WaylandDecorations.diagnose("wayland", plugins(GTK), CREATED_THREAD).orElseThrow();

            assertAll(
                    () -> assertTrue(message.contains("no titlebar"), message),
                    () -> assertTrue(message.contains("no way to resize"), message),
                    () -> assertTrue(message.contains("initial thread"), message),
                    () -> assertTrue(message.contains("gettid() != getpid()"), message),
                    () -> assertTrue(message.contains("sudo apt install libdecor-0-plugin-1-cairo"), message),
                    () -> assertTrue(message.contains("nothing needs rebuilding"), message));
        }

        @Test
        @DisplayName("heads off the downgrade, which is the obvious wrong idea")
        void headsOffTheDowngrade() {
            // libdecor <= 0.2.2 has no such check and decorates a JVM window
            // perfectly, so anyone who saw it work will reach for an older
            // libdecor first. That reintroduces the GTK state corruption the
            // check was added for (libdecor issue #72).
            var message = WaylandDecorations.diagnose("wayland", plugins(GTK), CREATED_THREAD).orElseThrow();

            assertAll(
                    () -> assertTrue(message.contains("0.2.3"), message),
                    () -> assertTrue(message.contains("Downgrading"), message),
                    () -> assertTrue(message.contains("crash"), message));
        }

        @Test
        @DisplayName("offers the X11 escape hatch under the property that actually works")
        void offersTheX11Escape() {
            var message = WaylandDecorations.diagnose("wayland", plugins(GTK), CREATED_THREAD).orElseThrow();
            // Spelled from the constant, so renaming the property cannot leave the
            // message advising a flag that no longer exists.
            assertTrue(message.contains("-D" + Sdl3Backend.VIDEO_DRIVER_PROPERTY + "=x11"), message);
        }

        @Test
        @DisplayName("does not blame the GTK plugin when no plugin is installed at all")
        void doesNotBlameGtkWhenItIsAbsent() {
            var message = WaylandDecorations.diagnose("wayland", plugins(), CREATED_THREAD).orElseThrow();

            assertAll(
                    () -> assertTrue(message.contains("No libdecor plugin is installed"), message),
                    // The GTK-specific accusation, not the words "initial thread"
                    // -- those now appear in the third remedy, which is offered
                    // whatever the cause was.
                    () -> assertFalse(message.contains("refuses to start"), message),
                    () -> assertFalse(message.contains("Downgrading"), message),
                    () -> assertTrue(message.contains("libdecor-0-plugin-1-cairo"), message));
        }

        @Test
        @DisplayName("is absent whenever the verdict is not UNDECORATED")
        void isAbsentOtherwise() {
            assertAll(
                    () -> assertTrue(WaylandDecorations.diagnose("wayland", plugins(GTK, CAIRO), CREATED_THREAD).isEmpty()),
                    () -> assertTrue(WaylandDecorations.diagnose("x11", plugins(GTK), CREATED_THREAD).isEmpty()),
                    () -> assertTrue(WaylandDecorations.diagnose("wayland", noDirectory(), CREATED_THREAD).isEmpty()));
        }
    }

    @Nested
    @DisplayName("verdictForWayland — the driver-independent core")
    class DriverIndependent {

        @Test
        @DisplayName("gives the same answer as verdict, without needing a driver name")
        void agreesWithTheDriverForm() {
            // These two must not drift: `verdict` is the whole public answer and
            // this is its core with the driver test peeled off.
            for (var files : List.of(plugins(GTK), plugins(), plugins(GTK, CAIRO))) {
                for (var thread : List.of(CREATED_THREAD, INITIAL_THREAD, UNKNOWN_THREAD)) {
                    assertEquals(WaylandDecorations.verdict("wayland", files, thread),
                            WaylandDecorations.verdictForWayland(files, thread),
                            "disagreed for " + files + " / " + thread);
                }
            }
        }

        @Test
        @DisplayName("reports UNDECORATED only when it is certain")
        void reportsUndecoratedOnlyWhenCertain() {
            // ADR-0086 removed the caller that acted on this before SDL_Init, but
            // the distinction still governs whether the warning is emitted, and it
            // is the shape any future conditional fallback would depend on.
            assertAll(
                    () -> assertEquals(Verdict.UNDECORATED,
                            WaylandDecorations.verdictForWayland(plugins(GTK), CREATED_THREAD)),
                    () -> assertEquals(Verdict.UNKNOWN,
                            WaylandDecorations.verdictForWayland(noDirectory(), CREATED_THREAD)),
                    () -> assertEquals(Verdict.UNKNOWN,
                            WaylandDecorations.verdictForWayland(plugins(GTK), UNKNOWN_THREAD)),
                    () -> assertEquals(Verdict.DECORATED,
                            WaylandDecorations.verdictForWayland(plugins(GTK, CAIRO), CREATED_THREAD)),
                    () -> assertEquals(Verdict.DECORATED,
                            WaylandDecorations.verdictForWayland(plugins(GTK), INITIAL_THREAD)));
        }

    }

    @Nested
    @DisplayName("reading the thread from /proc")
    class ReadingTheThread {

        @Test
        @DisplayName("this test runs on a created thread, and says so")
        void thisJvmIsNotOnTheInitialThread() {
            // JUnit runs tests on a thread the framework made, and even a plain
            // `java` main is not the primordial thread. Either way this must be
            // false here -- which is also a live check that the /proc parsing
            // works on the machine running the build.
            assertEquals(Optional.of(false), WaylandDecorations.onInitialThread());
        }

        @Test
        @DisplayName("reads pid and tid out of the one symlink")
        void readsBothNumbersFromOneLink(@TempDir Path temp) throws IOException {
            var initial = temp.resolve("initial");
            var created = temp.resolve("created");
            Files.createSymbolicLink(initial, Path.of("55887/task/55887"));
            Files.createSymbolicLink(created, Path.of("55889/task/55890"));

            assertAll(
                    () -> assertEquals(Optional.of(true), WaylandDecorations.onInitialThread(initial)),
                    () -> assertEquals(Optional.of(false), WaylandDecorations.onInitialThread(created)));
        }

        @Test
        @DisplayName("gives no answer where /proc is not there to give one")
        void noAnswerWithoutProc(@TempDir Path temp) throws IOException {
            var missing = temp.resolve("nothing-here");
            var notALink = Files.writeString(temp.resolve("regular"), "");
            var wrongShape = temp.resolve("odd");
            Files.createSymbolicLink(wrongShape, Path.of("55887/threads/55887"));

            assertAll(
                    () -> assertTrue(WaylandDecorations.onInitialThread(missing).isEmpty()),
                    () -> assertTrue(WaylandDecorations.onInitialThread(notALink).isEmpty()),
                    () -> assertTrue(WaylandDecorations.onInitialThread(wrongShape).isEmpty()));
        }
    }

    @Nested
    @DisplayName("finding the plugin directory")
    class FindingTheDirectory {

        @Test
        @DisplayName("takes LIBDECOR_PLUGIN_DIR at its word, with no fallback")
        void honoursTheEnvironmentOverride() {
            var candidates = WaylandDecorations.candidateDirectories("/opt/plugins", "amd64");
            // Exactly one: libdecor does not fall back either, so a warning derived
            // from a conventional directory it will never read would be fiction.
            assertEquals(List.of(Path.of("/opt/plugins")), candidates);
        }

        @Test
        @DisplayName("ignores a blank override")
        void ignoresABlankOverride() {
            assertTrue(WaylandDecorations.candidateDirectories("   ", "amd64").size() > 1);
        }

        @Test
        @DisplayName("puts the multiarch directory first on Debian architectures")
        void multiarchComesFirst() {
            assertEquals(Path.of("/usr/lib/x86_64-linux-gnu/libdecor/plugins-1"),
                    WaylandDecorations.candidateDirectories(null, "amd64").getFirst());
            assertEquals(Path.of("/usr/lib/aarch64-linux-gnu/libdecor/plugins-1"),
                    WaylandDecorations.candidateDirectories(null, "aarch64").getFirst());
        }

        @Test
        @DisplayName("still offers lib64 and lib for an architecture it has no triplet for")
        void unknownArchitectureStillHasCandidates() {
            var candidates = WaylandDecorations.candidateDirectories(null, "riscv64");
            assertAll(
                    () -> assertTrue(WaylandDecorations.multiarchTriplet("riscv64").isEmpty()),
                    () -> assertTrue(candidates.contains(Path.of("/usr/lib64/libdecor/plugins-1"))),
                    () -> assertTrue(candidates.contains(Path.of("/usr/lib/libdecor/plugins-1"))));
        }

        @Test
        @DisplayName("takes the first directory that exists, and skips the ones that do not")
        void takesTheFirstThatExists(@TempDir Path temp) throws IOException {
            var real = Files.createDirectories(temp.resolve("plugins-1"));
            Files.writeString(real.resolve(CAIRO), "");

            var asked = new java.util.ArrayList<Path>();
            var listing = WaylandDecorations.pluginFiles(null, "amd64", directory -> {
                asked.add(directory);
                return directory.equals(Path.of("/usr/lib/libdecor/plugins-1"))
                        ? Optional.of(List.of(CAIRO))
                        : Optional.empty();
            });

            assertAll(
                    () -> assertEquals(Optional.of(List.of(CAIRO)), listing),
                    // Stopped at the one that answered rather than listing them all.
                    () -> assertEquals(Path.of("/usr/lib/libdecor/plugins-1"), asked.getLast()));
        }

        @Test
        @DisplayName("reports no directory when none of the candidates exist")
        void reportsNoDirectoryWhenNoneExist() {
            assertTrue(WaylandDecorations.pluginFiles(null, "amd64", directory -> Optional.empty()).isEmpty());
        }
    }
}
