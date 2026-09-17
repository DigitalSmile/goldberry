package io.github.digitalsmile.goldberry.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
