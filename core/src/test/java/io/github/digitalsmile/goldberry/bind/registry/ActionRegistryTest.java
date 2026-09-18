package io.github.digitalsmile.goldberry.bind.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// What a `press=` or `change=` name resolves to (ADR-0051, ADR-0073).
///
/// The registry keeps two maps — plain [Runnable]s and [Consumer]s that are told
/// which option — and a name belongs to exactly one of them at a time. Most of
/// what is worth testing here is that the two maps agree about that.
class ActionRegistryTest {

    @Nested
    @DisplayName("one name, one binding")
    class OneName {

        @Test
        @DisplayName("a plain action resolves as a valued one that ignores its value")
        void plainResolvesValued() {
            var runs = new ArrayList<String>();
            var actions = ActionRegistry.strict().bind("save", () -> runs.add("plain"));

            actions.resolveValued("save").accept("ignored");

            assertEquals(List.of("plain"), runs);
        }

        @Test
        @DisplayName("binding a name that is already bound the other way is refused")
        void theTwoMapsAreOneVocabulary() {
            var valued = ActionRegistry.strict().bind("pick", (Consumer<String>) value -> {});
            assertThrows(IllegalStateException.class, () -> valued.bind("pick", () -> {}));

            var plain = ActionRegistry.strict().bind("pick", () -> {});
            assertThrows(IllegalStateException.class, () -> plain.bind("pick", (Consumer<String>) value -> {}));
        }

        @Test
        @DisplayName("a strict registry refuses an unknown name and says what it knows")
        void strictRefusesUnknown() {
            var actions = ActionRegistry.strict().bind("save", () -> {});

            var thrown = assertThrows(IllegalArgumentException.class, () -> actions.resolveValued("svae"));

            assertTrue(thrown.getMessage().contains("svae"), thrown.getMessage());
            assertTrue(thrown.getMessage().contains("save"), thrown.getMessage());
        }

        @Test
        @DisplayName("a lenient registry resolves an unknown name to nothing")
        void lenientAnswersNothing() {
            assertNull(ActionRegistry.lenient().resolveValued("svae"));
        }
    }

    /// The 2026-09-18 review's C6, from both sides.
    ///
    /// `rebind` replaces a binding of **either** kind, because `bind` refuses to
    /// let one name be both. Only the `Consumer` overload used to clear its
    /// counterpart: a `rebind(name, Runnable)` over a valued binding left the old
    /// consumer in the map `resolveValued` reads first, so the registry went on
    /// answering the handler that call had just replaced — silently, and for the
    /// rest of the process.
    @Nested
    @DisplayName("rebinding replaces whichever kind was there")
    class Rebinding {

        @Test
        @DisplayName("a plain rebind replaces a valued binding of the same name")
        void plainOverValued() {
            var runs = new ArrayList<String>();
            var actions = ActionRegistry.strict()
                    .bind("pick", (Consumer<String>) value -> runs.add("valued:" + value))
                    .rebind("pick", () -> runs.add("plain"));

            actions.resolveValued("pick").accept("dark");
            actions.resolve("pick").run();

            assertEquals(List.of("plain", "plain"), runs, "the replaced consumer is still being answered");
        }

        @Test
        @DisplayName("a valued rebind replaces a plain binding of the same name")
        void valuedOverPlain() {
            var runs = new ArrayList<String>();
            var actions = ActionRegistry.strict()
                    .bind("pick", () -> runs.add("plain"))
                    .rebind("pick", (Consumer<String>) value -> runs.add("valued:" + value));

            actions.resolveValued("pick").accept("dark");

            assertEquals(List.of("valued:dark"), runs);
        }

        @Test
        @DisplayName("after a rebind the name is bound once, not twice")
        void theNameIsBoundOnce() {
            var actions = ActionRegistry.strict()
                    .bind("pick", (Consumer<String>) value -> {})
                    .rebind("pick", () -> {});

            // `bind` refuses a name that is bound either way, so a leftover in the
            // other map shows up here as well as at resolution.
            assertThrows(IllegalStateException.class, () -> actions.bind("pick", () -> {}));
            assertEquals(1, actions.bound().size(), "one name, one entry");
        }

        @Test
        @DisplayName("rebinding a name nothing bound just binds it")
        void rebindIsAlsoBind() {
            var runs = new ArrayList<String>();

            ActionRegistry.strict()
                    .rebind("save", () -> runs.add("plain"))
                    .resolve("save")
                    .run();

            assertEquals(List.of("plain"), runs);
        }
    }
}
