package io.github.digitalsmile.goldberry.weaver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.bind.Models;
import io.github.digitalsmile.goldberry.bind.Observable;
import io.github.digitalsmile.goldberry.weaver.models.Split;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The cross-class rewrite: a `putfield` in one class file, on a `@Bind` field
/// declared in another.
///
/// The capability [ADR-0134] added, and the one that makes "values here, actions
/// there" possible at all — before it a write was only rewritten inside the class
/// that declared the field, so an actions class assigning to a model's field
/// compiled, ran, and notified nobody.
///
/// It lived in `:widgets` while every module wove its models. Since
/// [ADR-0155](../../../../book/src/adr/0155-a-jar-binds-at-run-time-an-image-is-woven.md)
/// they do not, so the assertion moved to where the woven bytes are actually
/// produced: [Woven#group] does in memory what `WeaverMain` does to a directory,
/// which is the two-pass collection this rule needs. `SplitModelTest` in
/// `:widgets` is the same shape asserted against the reflective binding.
@DisplayName("a write from the class beside the model")
class SplitWeaveTest {

    /// The woven pair, and a `values` for the `actions` to hold.
    private record Pair(Object values, Object actions) {

        static Pair of() {
            var group = Woven.group(Split.class, Split.Values.class, Split.Actions.class);
            try {
                var values = group.get(Split.Values.class.getName())
                        .getDeclaredConstructor().newInstance();
                var actions = group.get(Split.Actions.class.getName())
                        .getDeclaredConstructors()[0].newInstance(values);
                return new Pair(values, actions);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("could not build the woven pair", e);
            }
        }

        /// Called as a **Java method**, which is the whole subject: the write is
        /// an ordinary assignment in `Actions`, and what is asserted is that the
        /// weaver rewrote it anyway.
        void call(String method, Object... arguments) {
            try {
                var types = new Class<?>[arguments.length];
                for (var i = 0; i < arguments.length; i++) {
                    types[i] = arguments[i].getClass();
                }
                actions.getClass().getMethod(method, types).invoke(actions, arguments);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("could not call " + method, e);
            }
        }

        List<Object> watch(String path) {
            var seen = new ArrayList<>();
            Models.<Object>observable(values, path).subscribe(seen::add);
            return seen;
        }

        Observable<?> path(String name) {
            return Models.bindings(values).resolve(name);
        }
    }

    @Test
    @DisplayName("is rewritten, so it notifies the model it wrote to")
    void crossClassWriteNotifies() {
        var pair = Pair.of();
        var seen = pair.watch("split.count");

        pair.call("bump");
        pair.call("bump");

        assertEquals(List.of(1, 2), seen);
        assertEquals(2, pair.path("split.count").get());
    }

    @Test
    @DisplayName("and a write that changes nothing is still silent")
    void unchangedIsSilent() {
        var pair = Pair.of();
        var seen = pair.watch("split.label");

        pair.call("say", "hello");
        pair.call("say", "hello");

        assertEquals(List.of("hello"), seen);
    }

    @Test
    @DisplayName("two writes in one method are two notifications, so the rewrite is per instruction")
    void perInstruction() {
        var pair = Pair.of();
        var counts = pair.watch("split.count");
        var labels = pair.watch("split.label");

        pair.call("both");

        assertEquals(List.of(1), counts);
        assertEquals(List.of("moved"), labels);
    }

    @Test
    @DisplayName("the frame request follows the field, not the class that wrote it")
    void repaintIsPerField() {
        var pair = Pair.of();
        var frames = new int[1];
        Models.onRepaint(pair.values(), () -> frames[0]++);

        pair.call("tick");
        assertEquals(0, frames[0], "split.quiet is declared repaint = false");

        pair.call("bump");
        assertEquals(1, frames[0]);
    }

    @Test
    @DisplayName("the setters stay private, because the two classes are nestmates")
    void nestmatesKeepTheirFields() {
        // ADR-0137: a model whose actions sit beside it in the same nest needs no
        // package-private setter, because a nestmate may call a private one. The
        // synthesised members are what the weaver adds, and every one of them is
        // still private here.
        var woven = Woven.group(Split.class, Split.Values.class, Split.Actions.class)
                .get(Split.Values.class.getName());

        for (var method : woven.getDeclaredMethods()) {
            if (method.getName().startsWith("goldberry$set$")) {
                assertTrue(java.lang.reflect.Modifier.isPrivate(method.getModifiers()),
                        method.getName() + " should still be private");
            }
        }
    }

    @Test
    @DisplayName("each class publishes its own half and nothing of the other's")
    void registriesSplitToo() {
        var pair = Pair.of();

        assertEquals(List.of("split.count", "split.label", "split.quiet"),
                List.copyOf(Models.bindings(pair.values()).bound().keySet()));
        assertEquals(java.util.Set.of("split.bump", "split.say", "split.tick", "split.both"),
                Models.actions(pair.actions()).bound().keySet());
        assertTrue(Models.actions(pair.values()).bound().isEmpty(),
                "a @Model with no @Action method publishes no names");
        assertTrue(Models.bindings(pair.actions()).bound().isEmpty(),
                "an @Actions class holds no values, so it publishes no paths");
    }
}
