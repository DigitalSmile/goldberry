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
/// The capability ADR-0134 added, and the one that makes "values here, actions
/// there" possible at all: before it, a write to a `@Bind` field was only
/// rewritten inside the class that declared it, so an actions class assigning to
/// a model's field compiled, ran, and notified nobody.
///
/// These live in `:widgets` rather than `:weaver` because they need the **build's**
/// output: `:weaver` deliberately does not weave itself — its fixtures have to
/// stay raw so its own tests can weave them in memory — while `:widgets` applies
/// `goldberry.weave` to both source sets. So `Split.Values` and `Split.Actions`
/// here are the real thing, and the cross-class rewrite had to survive the
/// two-pass collection to work at all.
@DisplayName("values in one class, actions in another")
class SplitModelTest {

    private static List<Object> watch(Object model, String path) {
        var seen = new ArrayList<>();
        Models.observable(model, path).subscribe(seen::add);
        return seen;
    }

    @Test
    @DisplayName("an action in another class notifies the model's binding")
    void crossClassWriteNotifies() {
        var values = new Split.Values();
        var actions = new Split.Actions(values);
        var seen = watch(values, "split.count");

        actions.bump();
        actions.bump();

        assertEquals(List.of(1, 2), seen);
    }

    @Test
    @DisplayName("and a write that changes nothing is still silent")
    void unchangedIsSilent() {
        var values = new Split.Values();
        var actions = new Split.Actions(values);
        var seen = watch(values, "split.label");

        actions.say("hello");
        actions.say("hello");

        assertEquals(List.of("hello"), seen);
    }

    @Test
    @DisplayName("two writes in one method are two notifications")
    void perInstruction() {
        var values = new Split.Values();
        var actions = new Split.Actions(values);
        var counts = watch(values, "split.count");
        var labels = watch(values, "split.label");

        actions.both();

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

        actions.tick();
        assertEquals(0, frames[0], "split.quiet is declared repaint = false");

        actions.bump();
        assertEquals(1, frames[0]);
    }

    @Test
    @DisplayName("the actions class publishes the names, the values class the paths")
    void registriesSplitToo() {
        var values = new Split.Values();
        var actions = new Split.Actions(values);

        assertEquals(List.of("split.count", "split.label", "split.quiet"),
                List.copyOf(Models.bindings(values).bound().keySet()));
        // A set, not a list: the weaver reads methods in class-file order, and
        // for a record javac's order is its own business. `ModelWeaverTest`
        // pins declaration order for an ordinary class, which is where the
        // guarantee is worth having.
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
