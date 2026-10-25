package dev.goldberry.example.model.controls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.bind.runtime.Models;
import dev.goldberry.widgets.controls.checkbox.Checkbox;

/// The road, the choices and the chips: the arithmetic the application does,
/// which is very little.
@DisplayName("the controls slice")
class ControlsModelTest {

    private final ControlsModel model = new ControlsModel();
    private final ControlsModel.Actions actions = new ControlsModel.Actions(model);

    @Test
    @DisplayName("the road counts up, never turns back past Bag End, and begins again")
    void theRoad() {
        assertFalse(model.hasClicks());

        actions.click();
        actions.click();
        actions.undo();
        assertEquals(1, model.clicks());

        actions.undo();
        actions.undo();
        assertEquals(0, model.clicks(), "turning back stops at Bag End");

        actions.click();
        actions.reset();
        assertEquals(0, model.clicks());
    }

    @Test
    @DisplayName("the verse's box toggles and its switch sets")
    void theVerse() {
        assertTrue(model.isProseShown());

        actions.toggleProse();
        assertFalse(model.isProseShown());

        actions.setProse(true);
        actions.setProse(true);
        assertTrue(model.isProseShown(), "a switch asks for a state, not for the other one");
    }

    @Test
    @DisplayName("a mixed box goes to checked, and nothing a user does brings mixed back")
    void thePartlyTickedBox() {
        var partly = Models.observable(model, "app.partly");
        assertEquals(Checkbox.Value.MIXED, partly.get());

        actions.togglePartly();
        assertEquals(Checkbox.Value.CHECKED, partly.get());

        actions.togglePartly();
        assertEquals(Checkbox.Value.UNCHECKED, partly.get());
    }

    @Test
    @DisplayName("the gain starts whole and takes the fraction a slider reports")
    void theGain() {
        var gain = Models.observable(model, "app.gain");
        assertEquals(40, gain.get(), "a whole 40, which a text prints as 40 and not 40.0");

        actions.setGain(62.5);
        assertEquals(62.5, gain.get());
    }

    @Test
    @DisplayName("each chip toggles only itself")
    void theChips() {
        actions.toggleStarred();
        actions.toggleUnread();

        assertEquals(false, Models.observable(model, "app.chip-unread").get());
        assertEquals(true, Models.observable(model, "app.chip-starred").get());
        assertEquals(false, Models.observable(model, "app.chip-archived").get());
    }

    @Test
    @DisplayName("a dismissed tag leaves the row, and the five come back")
    void theTags() {
        var five = model.tags();

        actions.dropTag("kdl");
        actions.dropTag("not a tag");
        assertEquals(List.of("java", "blend2d", "harfbuzz", "yoga"), model.tags());

        actions.resetTags();
        assertEquals(five, model.tags());
    }
}
