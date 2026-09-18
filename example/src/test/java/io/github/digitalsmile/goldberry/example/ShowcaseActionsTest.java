package io.github.digitalsmile.goldberry.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.ref.Reference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.bind.runtime.Models;

/// What the showcase's actions say when a document hands them something they
/// cannot take.
///
/// `md.toggle-task` is sent an ordinal by the preview (ADR-0300), and the only
/// thing a wrong one can be is a document bug — so the refusal names the action
/// and the text, which is what a reader needs to find it.
class ShowcaseActionsTest {

    private static ShowcaseModel.Actions actions() {
        return new Showcase()
                .models().stream()
                        .filter(ShowcaseModel.Actions.class::isInstance)
                        .map(ShowcaseModel.Actions.class::cast)
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("the showcase publishes its actions as a model"));
    }

    /// The two names `BindingBenchmark.showcaseModel` resolves, resolved here
    /// under `check`. The benchmark is tagged `benchmark` and runs nightly only,
    /// so when the actions moved into their own record the name it asked for
    /// stopped resolving and the lane was red for a week before anybody read it
    /// (ADR-0397). A count rather than a timing, which is what `check` may
    /// assert.
    @Test
    @DisplayName("the road's click is bound on the actions record and counts on the model")
    void theRoadsClickCounts() {
        var model = new ShowcaseModel();
        var actions = new ShowcaseModel.Actions(model);
        var click = Models.actions(actions).resolve("app.click");
        var clicks = Models.observable(model, "app.clicks");

        click.run();
        click.run();

        assertEquals(2, clicks.get(), "app.click counts on app.clicks, which is what the benchmark times");
        // A registry is a weak window onto its model; the record must outlive the clicks.
        Reference.reachabilityFence(actions);
    }

    @Test
    @DisplayName("a task ordinal that is not a number is refused by the action's name")
    void toggleTaskRefusesWords() {
        var actions = actions();
        var before = Models.observable(actions.values(), "md.source").get();

        var refused = assertThrows(
                IllegalArgumentException.class,
                () -> Models.actions(actions).resolveValued("md.toggle-task").accept("first"));

        assertTrue(refused.getMessage().contains("md.toggle-task"), refused.getMessage());
        assertTrue(refused.getMessage().contains("\"first\""), refused.getMessage());
        assertInstanceOf(NumberFormatException.class, refused.getCause());
        assertEquals(
                before, Models.observable(actions.values(), "md.source").get(), "a refused toggle rewrote nothing");
    }
}
