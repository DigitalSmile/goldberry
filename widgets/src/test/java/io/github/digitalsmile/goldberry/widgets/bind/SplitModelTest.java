package io.github.digitalsmile.goldberry.widgets.bind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.bind.Models;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// That a class may keep a model's values and another class may change them.
///
/// "Values here, actions there" (ADR-0134, ADR-0136), asserted against the
/// **build's own output** — which since ADR-0155 means the *reflective* binding,
/// because `:widgets` no longer weaves its models and neither does an ordinary
/// application's jar. `SplitWeaveTest` in `:weaver` is the same shape asserted
/// against the woven form, and `RuntimeAgreesWithWovenTest` is what holds the two
/// to the same answers.
///
/// Every action here is dispatched **through the registry**, which is what a
/// document does and what the two forms share. Calling `actions.bump()` as a Java
/// method is a write like any other: woven it notifies from inside the
/// assignment, and bound at run time it waits for a sweep — so a test that called
/// it directly would be asserting which of the two it got.
@DisplayName("values in one class, actions in another")
class SplitModelTest {

    private static List<Object> watch(Object model, String path) {
        var seen = new ArrayList<>();
        Models.observable(model, path).subscribe(seen::add);
        return seen;
    }

    /// What a `button press="split.bump"` does.
    private static void press(Object actions, String name) {
        Models.actions(actions).resolve(name).run();
    }

    /// What a `slider change="split.say"` does.
    private static void send(Object actions, String name, String value) {
        Models.actions(actions).resolveValued(name).accept(value);
    }

    @Test
    @DisplayName("an action in another class notifies the model's binding")
    void crossClassWriteNotifies() {
        var values = new Split.Values();
        var actions = new Split.Actions(values);
        var seen = watch(values, "split.count");

        press(actions, "split.bump");
        press(actions, "split.bump");

        assertEquals(List.of(1, 2), seen);
    }

    @Test
    @DisplayName("and a write that changes nothing is still silent")
    void unchangedIsSilent() {
        var values = new Split.Values();
        var actions = new Split.Actions(values);
        var seen = watch(values, "split.label");

        send(actions, "split.say", "hello");
        send(actions, "split.say", "hello");

        assertEquals(List.of("hello"), seen);
    }

    @Test
    @DisplayName("two fields moved by one action are two notifications")
    void perField() {
        var values = new Split.Values();
        var actions = new Split.Actions(values);
        var counts = watch(values, "split.count");
        var labels = watch(values, "split.label");

        press(actions, "split.both");

        assertEquals(List.of(1), counts);
        assertEquals(List.of("moved"), labels);
    }

    @Test
    @DisplayName("the frame request follows the field, not the class that wrote it")
    void repaintIsPerField() {
        var values = new Split.Values();
        var actions = new Split.Actions(values);
        var frames = new int[1];
        Models.onRepaint(values, () -> frames[0]++);

        press(actions, "split.tick");
        assertEquals(0, frames[0], "split.quiet is declared repaint = false");

        press(actions, "split.bump");
        assertEquals(1, frames[0]);
    }

    @Test
    @DisplayName("the actions class publishes the names, the values class the paths")
    void registriesSplitToo() {
        var values = new Split.Values();
        var actions = new Split.Actions(values);

        assertEquals(List.of("split.count", "split.label", "split.quiet"),
                List.copyOf(Models.bindings(values).bound().keySet()));
        // A set, not a list: which order a registry's names come out in is the
        // one thing the two forms of a binding do not promise to agree on --
        // the weaver publishes in class-file order and reflection cannot see
        // that, so the runtime form sorts by member name instead of leaving it
        // to `getDeclaredMethods` (ADR-0155).
        assertEquals(java.util.Set.of("split.bump", "split.say", "split.tick", "split.both"),
                Models.actions(actions).bound().keySet());
        // And each publishes only its own half, which is the point of the two
        // markers: `@Model` for the values, `@Actions` for the methods.
        assertTrue(Models.actions(values).bound().isEmpty(),
                "a @Model with no @Action method publishes no names");
        assertTrue(Models.bindings(actions).bound().isEmpty(),
                "an @Actions class holds no values, so it publishes no paths");
    }

    @Test
    @DisplayName("a record holds the actions, because it holds no state")
    void actionsAreARecord() {
        assertTrue(Split.Actions.class.isRecord());
        assertTrue(!Split.Values.class.isRecord(),
                "values cannot be a record: a record's components are final and a"
                        + " bound field has to be assignable");
    }
}
