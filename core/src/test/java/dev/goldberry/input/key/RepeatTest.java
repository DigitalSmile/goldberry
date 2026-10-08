package dev.goldberry.input.key;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.input.PointerRouter;

/// What an accelerator does while its key is held down, decided per binding.
class RepeatTest {

    @Nested
    @DisplayName("the policy")
    class Policy {

        @Test
        @DisplayName("FIRE runs on the first press and on every repeat")
        void fire() {
            assertTrue(Repeat.FIRE.runs(false));
            assertTrue(Repeat.FIRE.runs(true));
        }

        @Test
        @DisplayName("IGNORE runs on the first press only")
        void ignore() {
            assertTrue(Repeat.IGNORE.runs(false));
            assertFalse(Repeat.IGNORE.runs(true));
        }
    }

    @Nested
    @DisplayName("a binding on the router")
    class Router {

        private final List<String> log = new ArrayList<>();
        private final PointerRouter router = new PointerRouter();

        @Test
        @DisplayName("bound with no policy, fires on repeats, as every binding always has")
        void defaultFires() {
            router.shortcut(Shortcut.of(Key.F5), () -> log.add("refresh"));

            router.keyPressed(Key.F5, Modifiers.NONE, false);
            router.keyPressed(Key.F5, Modifiers.NONE, true);

            assertEquals(List.of("refresh", "refresh"), log);
        }

        @Test
        @DisplayName("bound to ignore repeats, runs once however long the key is held")
        void ignoredRepeats() {
            router.shortcut(Shortcut.of(Key.ESCAPE), () -> log.add("menu"), null, Repeat.IGNORE);

            router.keyPressed(Key.ESCAPE, Modifiers.NONE, false);
            router.keyPressed(Key.ESCAPE, Modifiers.NONE, true);
            router.keyPressed(Key.ESCAPE, Modifiers.NONE, true);
            router.keyPressed(Key.ESCAPE, Modifiers.NONE, false);

            assertEquals(List.of("menu", "menu"), log, "the second press is a new press and runs again");
        }

        /// The declined repeat is still the accelerator's key, so a held Tab
        /// bound as a toggle does not start walking the focus half way through.
        @Test
        @DisplayName("a declined repeat is consumed, not passed on")
        void declinedRepeatIsConsumed() {
            router.shortcut(Shortcut.of(Key.TAB), () -> log.add("toggle"), null, Repeat.IGNORE);

            assertTrue(router.keyPressed(Key.TAB, Modifiers.NONE, true));
            assertEquals(List.of(), log);
        }

        @Test
        @DisplayName("an owner's binding keeps its policy and its owner")
        void ownerAndPolicy() {
            var owner = new Object();
            router.shortcut(Shortcut.of(Key.G), () -> log.add("graveyard"), owner, Repeat.IGNORE);

            router.keyPressed(Key.G, Modifiers.NONE, false);
            router.keyPressed(Key.G, Modifiers.NONE, true);
            router.removeShortcut(Shortcut.of(Key.G), owner);
            router.keyPressed(Key.G, Modifiers.NONE, false);

            assertEquals(List.of("graveyard"), log);
        }
    }
}
