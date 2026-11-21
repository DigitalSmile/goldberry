package dev.goldberry.input.global;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Mod;
import dev.goldberry.natives.desktop.shortcuts.Hotkey;
import dev.goldberry.natives.desktop.shortcuts.HotkeyBackend;
import dev.goldberry.natives.desktop.shortcuts.HotkeyKey;
import dev.goldberry.natives.desktop.shortcuts.HotkeyListener;
import dev.goldberry.natives.desktop.shortcuts.HotkeyOutcome;

/// Global shortcuts asked for, granted or refused, and heard going down and
/// coming up, on the executor they were opened with.
@DisplayName("global shortcuts")
class GlobalShortcutsTest {

    private static final GlobalShortcut TALK = GlobalShortcut.of("talk", "Push to talk", "Ctrl+Alt+T");
    private static final GlobalShortcut MUTE = GlobalShortcut.of("mute", "Mute", "F9");

    @Nested
    @DisplayName("a shortcut")
    class Shortcuts {

        @Test
        @DisplayName("parses its trigger as an accelerator is parsed")
        void parses() {
            assertEquals(Mod.CTRL.and(Mod.ALT).and(Key.T), TALK.trigger());
        }

        @Test
        @DisplayName("needs an id")
        void needsAnId() {
            assertThrows(IllegalArgumentException.class, () -> GlobalShortcut.of(" ", "x", "F1"));
        }
    }

    @Nested
    @DisplayName("crossing to the native layer")
    class Crossing {

        @Test
        @DisplayName("every key has a hotkey key of the same name, and no hotkey key is left over")
        void keys() {
            var keys = new TreeSet<String>();
            Arrays.stream(Key.values()).filter(key -> key != Key.UNKNOWN).forEach(key -> keys.add(key.name()));
            var hotkeys = new TreeSet<String>();
            Arrays.stream(HotkeyKey.values()).forEach(key -> hotkeys.add(key.name()));

            assertEquals(keys, hotkeys);
        }

        @Test
        @DisplayName("the modifier bits are the native layer's")
        void modifiers() {
            assertAll(
                    () -> assertEquals(Hotkey.SHIFT, Mod.SHIFT.bit()),
                    () -> assertEquals(Hotkey.CTRL, Mod.CTRL.bit()),
                    () -> assertEquals(Hotkey.ALT, Mod.ALT.bit()),
                    () -> assertEquals(Hotkey.META, Mod.META.bit()));
        }

        @Test
        @DisplayName("a shortcut becomes a hotkey with its id, description, key and modifiers")
        void hotkey() {
            assertEquals(
                    new Hotkey("talk", "Push to talk", HotkeyKey.T, Hotkey.CTRL | Hotkey.ALT),
                    SystemGlobalShortcuts.hotkey(TALK));
        }

        @Test
        @DisplayName("each outcome becomes its grant, an unsupported refusal an Unsupported")
        void grants() {
            assertAll(
                    () -> assertEquals(
                            new Grant.Granted("a", "F9"),
                            SystemGlobalShortcuts.grant(new HotkeyOutcome.Bound("a", "F9"))),
                    () -> assertEquals(
                            new Grant.Refused("a", Grant.Reason.DENIED, "no"),
                            SystemGlobalShortcuts.grant(
                                    new HotkeyOutcome.Refused("a", HotkeyOutcome.Refusal.DENIED, "no"))),
                    () -> assertEquals(
                            new Grant.Refused("a", Grant.Reason.TAKEN, "t"),
                            SystemGlobalShortcuts.grant(
                                    new HotkeyOutcome.Refused("a", HotkeyOutcome.Refusal.TAKEN, "t"))),
                    () -> assertEquals(
                            new Grant.Refused("a", Grant.Reason.UNMAPPABLE, "m"),
                            SystemGlobalShortcuts.grant(
                                    new HotkeyOutcome.Refused("a", HotkeyOutcome.Refusal.UNMAPPABLE, "m"))),
                    () -> assertEquals(
                            new Grant.Unsupported("a", "u"),
                            SystemGlobalShortcuts.grant(
                                    new HotkeyOutcome.Refused("a", HotkeyOutcome.Refusal.UNSUPPORTED, "u"))));
        }
    }

    /// A backend that grants everything and is pressed by the test, on the
    /// test's thread.
    private static final class FakeBackend implements HotkeyBackend {

        final boolean mainThread;
        final List<List<Hotkey>> binds = new ArrayList<>();

        @Nullable
        HotkeyListener listener;

        boolean closed;

        FakeBackend(boolean mainThread) {
            this.mainThread = mainThread;
        }

        @Override
        public String name() {
            return "fake";
        }

        @Override
        public boolean bindsOnMainThread() {
            return mainThread;
        }

        @Override
        public CompletableFuture<List<HotkeyOutcome>> bind(List<Hotkey> hotkeys, HotkeyListener heard) {
            binds.add(hotkeys);
            listener = heard;
            return CompletableFuture.completedFuture(hotkeys.stream()
                    .<HotkeyOutcome>map(hotkey -> new HotkeyOutcome.Bound(hotkey.id(), hotkey.xdgTrigger()))
                    .toList());
        }

        @Override
        public void close() {
            closed = true;
        }

        HotkeyListener heard() {
            return Objects.requireNonNull(listener, "nothing was bound");
        }
    }

    /// An executor that runs nothing until the test says, so a test can see
    /// what was handed to it.
    private static final class Queued implements Executor {

        final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }

        void runAll() {
            Runnable task;
            while ((task = tasks.poll()) != null) {
                task.run();
            }
        }
    }

    @Nested
    @DisplayName("the desktop's")
    class Desktop {

        @Test
        @DisplayName("hands its answer and every event to the executor")
        void movesToTheExecutor() {
            var backend = new FakeBackend(false);
            var executor = new Queued();
            var shortcuts = new SystemGlobalShortcuts(backend, executor);
            var heard = new ArrayList<GlobalShortcutEvent>();

            var answer = shortcuts.bind(List.of(TALK, MUTE), heard::add);
            backend.heard().pressed("talk");
            backend.heard().released("talk");

            assertFalse(answer.isDone());
            assertEquals(List.of(), heard);
            executor.runAll();
            assertEquals(
                    List.of(new Grant.Granted("talk", "CTRL+ALT+t"), new Grant.Granted("mute", "F9")), answer.join());
            assertEquals(
                    List.of(
                            new GlobalShortcutEvent("talk", GlobalShortcutEvent.Kind.PRESSED),
                            new GlobalShortcutEvent("talk", GlobalShortcutEvent.Kind.RELEASED)),
                    heard);
        }

        @Test
        @DisplayName("binds on the executor where the backend binds on the main thread")
        void bindsOnTheMainThread() {
            var backend = new FakeBackend(true);
            var executor = new Queued();
            var shortcuts = new SystemGlobalShortcuts(backend, executor);

            var answer = shortcuts.bind(List.of(TALK), _ -> {});

            assertEquals(List.of(), backend.binds);
            executor.runAll();
            assertEquals(1, backend.binds.size());
            assertTrue(answer.join().getFirst().isGranted());
        }

        @Test
        @DisplayName("where there is no backend, every shortcut is unsupported")
        void noBackend() {
            var shortcuts = new SystemGlobalShortcuts(null, Runnable::run);

            var grants = shortcuts.bind(List.of(TALK), _ -> {}).join();

            assertAll(
                    () -> assertFalse(shortcuts.isSupported()),
                    () -> assertEquals("none", shortcuts.name()),
                    () -> assertTrue(grants.getFirst() instanceof Grant.Unsupported));
        }

        @Test
        @DisplayName("refuses two shortcuts with one id, and a bind after closing")
        void refuses() {
            var backend = new FakeBackend(false);
            var shortcuts = new SystemGlobalShortcuts(backend, Runnable::run);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> shortcuts.bind(List.of(TALK, GlobalShortcut.of("talk", "again", "F2")), _ -> {}));
            shortcuts.close();
            shortcuts.close();
            assertTrue(backend.closed);
            assertThrows(IllegalStateException.class, () -> shortcuts.bind(List.of(TALK), _ -> {}));
        }
    }

    @Nested
    @DisplayName("simulated")
    class Simulated {

        @Test
        @DisplayName("grants what it is asked, except what the test refuses")
        void grants() {
            var shortcuts = GlobalShortcuts.simulated().refuse("mute", Grant.Reason.TAKEN);

            var grants = shortcuts.bind(List.of(TALK, MUTE), _ -> {}).join();

            assertEquals(
                    List.of(
                            new Grant.Granted("talk", "Ctrl+Alt+T"),
                            new Grant.Refused("mute", Grant.Reason.TAKEN, "simulated")),
                    grants);
        }

        @Test
        @DisplayName("presses once, releases once, and never a refused shortcut")
        void presses() {
            var shortcuts = GlobalShortcuts.simulated().refuse("mute", Grant.Reason.DENIED);
            var heard = new ArrayList<String>();
            shortcuts.bind(List.of(TALK, MUTE), event -> heard.add(event.kind() + " " + event.id()));

            shortcuts.release("talk");
            shortcuts.press("talk");
            shortcuts.press("talk");
            shortcuts.press("mute");
            shortcuts.release("talk");

            assertEquals(List.of("PRESSED talk", "RELEASED talk"), heard);
        }

        @Test
        @DisplayName("releases a held shortcut when bound again or closed")
        void releasesOnRebindAndClose() {
            var shortcuts = GlobalShortcuts.simulated();
            var heard = new ArrayList<String>();
            shortcuts.bind(List.of(TALK), event -> heard.add(event.kind() + " " + event.id()));
            shortcuts.press("talk");

            shortcuts.bind(List.of(TALK), event -> heard.add("again " + event.kind()));
            shortcuts.press("talk");
            shortcuts.close();

            assertEquals(List.of("PRESSED talk", "RELEASED talk", "again PRESSED", "again RELEASED"), heard);
            assertTrue(shortcuts.isClosed());
            assertThrows(IllegalStateException.class, () -> shortcuts.bind(List.of(), _ -> {}));
        }
    }
}
