package io.github.digitalsmile.goldberry.render.backend.sdl3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.render.dialog.FileChoice;

/// What a pump does with the answers a file dialog parked for it.
///
/// The dialog itself cannot be opened here — it is the platform's window, and the
/// `dummy` video driver every headless test runs under has none — so the queue is
/// filled directly, which is the same path the SDL callback takes once it has
/// translated its arguments. What is under test is [Sdl3FileDialogs#deliverPending],
/// which is the toolkit's own code either way.
class Sdl3FileDialogsTest {

    @BeforeAll
    static void requireLibrary() {
        RendererRequirement.enforce();
    }

    private final List<String> woken = new ArrayList<>();

    private Sdl3FileDialogs dialogs() {
        return new Sdl3FileDialogs(() -> woken.add("wakeup"));
    }

    @Test
    @DisplayName("every parked answer reaches its consumer, and the count says how many")
    void deliversTheBatch() {
        var dialogs = dialogs();
        var seen = new ArrayList<FileChoice>();

        dialogs.queue(seen::add, FileChoice.cancelled());
        dialogs.queue(seen::add, FileChoice.failed("no portal"));

        assertEquals(2, dialogs.deliverPending());
        assertEquals(2, seen.size());
        assertEquals(2, woken.size(), "each answer wakes the loop it was parked for");
        assertEquals(0, dialogs.deliverPending(), "and the queue is empty afterwards");
    }

    @Nested
    @DisplayName("when a consumer throws")
    class Broken {

        /// One broken handler must not lose a second dialog's result: the batch is
        /// run to its end and the failure is raised after it.
        @Test
        @DisplayName("the answers after it are still delivered")
        void therestStillRun() {
            var dialogs = dialogs();
            var seen = new ArrayList<FileChoice>();

            dialogs.queue(
                    _ -> {
                        throw new IllegalStateException("the application's own bug");
                    },
                    FileChoice.cancelled());
            dialogs.queue(seen::add, FileChoice.failed("no portal"));

            assertThrows(IllegalStateException.class, dialogs::deliverPending);
            assertEquals(1, seen.size(), "the second answer was dropped with the first handler");
        }

        /// This kept the first exception and dropped the rest on the floor, so two
        /// broken handlers in one pump reported as one and the second bug stayed
        /// invisible until the first was fixed. `UiExecutor.drain` had already been
        /// told: the later ones are suppressed causes of the first.
        @Test
        @DisplayName("a second failure is carried by the first rather than lost")
        void everyFailureIsReported() {
            var dialogs = dialogs();
            var first = new IllegalStateException("the first handler");
            var second = new IllegalArgumentException("the second handler");

            dialogs.queue(
                    _ -> {
                        throw first;
                    },
                    FileChoice.cancelled());
            dialogs.queue(
                    _ -> {
                        throw second;
                    },
                    FileChoice.cancelled());

            var thrown = assertThrows(RuntimeException.class, dialogs::deliverPending);

            assertSame(first, thrown);
            assertEquals(
                    List.of(second), Arrays.asList(thrown.getSuppressed()), "the second handler's failure was dropped");
        }

        /// A handler that rethrows one cached exception object twice would
        /// otherwise make it its own suppressed cause, which `addSuppressed`
        /// refuses with an `IllegalArgumentException` of its own — turning a
        /// reporting nicety into a crash in the pump.
        @Test
        @DisplayName("the same exception object twice is not suppressed into itself")
        void oneObjectTwice() {
            var dialogs = dialogs();
            var shared = new IllegalStateException("cached and rethrown");

            for (var i = 0; i < 2; i++) {
                dialogs.queue(
                        _ -> {
                            throw shared;
                        },
                        FileChoice.cancelled());
            }

            var thrown = assertThrows(RuntimeException.class, dialogs::deliverPending);

            assertSame(shared, thrown);
            assertEquals(0, thrown.getSuppressed().length);
        }
    }
}
