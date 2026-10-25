package dev.goldberry.example.model.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.bind.runtime.Models;

/// The chapters in the strip and the breadcrumbs: what each request leaves
/// behind, and that every change is a new list.
@DisplayName("the navigation slice")
class NavigationModelTest {

    private final NavigationModel model = new NavigationModel();
    private final NavigationModel.Actions actions = new NavigationModel.Actions(model);

    private List<String> tabs() {
        return Models.<List<String>>observable(model, "app.tabs").get();
    }

    private Object tab() {
        return Models.observable(model, "app.tab").get();
    }

    @Test
    @DisplayName("a new chapter is the next stage of the road, and is shown")
    void newTabIsTheNextStage() {
        actions.newTab();
        actions.newTab();

        assertEquals(List.of("Rivendell", "Moria", "Bree", "Weathertop"), tabs());
        assertEquals("Weathertop", tab());
    }

    @Test
    @DisplayName("a stage already open is walked again rather than opened twice")
    void aRepeatIsWalkedAgain() {
        actions.newTab();
        for (var i = 1; i < 10; i++) {
            actions.newTab();
        }
        // Eleven opened: the road wraps, and "Bree" is still open.
        actions.newTab();

        assertEquals("Bree again", tab());
    }

    @Test
    @DisplayName("closing the chapter shown shows its neighbour, and the last leaves nothing shown")
    void closingPicksANeighbour() {
        actions.closeTab("Rivendell");
        assertEquals(List.of("Moria"), tabs());
        assertEquals("Moria", tab());

        actions.closeTab("Moria");
        assertEquals(List.of(), tabs());
        assertNull(tab());
    }

    @Test
    @DisplayName("closing a chapter that is not open changes nothing")
    void closingNothing() {
        var before = tabs();

        actions.closeTab("Mordor");

        assertEquals(before, tabs());
        assertEquals("Rivendell", tab());
    }

    @Test
    @DisplayName("a dragged chapter lands where it was dropped, clamped to the strip")
    void moveClamps() {
        actions.newTab();
        actions.moveTab("Rivendell", 99);
        assertEquals(List.of("Moria", "Bree", "Rivendell"), tabs());

        actions.moveTab("Rivendell", -4);
        assertEquals(List.of("Rivendell", "Moria", "Bree"), tabs());
    }

    @Test
    @DisplayName("every change is a new list, because assignment is what is observed")
    void everyChangeIsANewList() {
        var before = tabs();

        actions.newTab();

        assertNotSame(before, tabs());
        assertEquals(List.of("Rivendell", "Moria"), before, "the old list was not edited in place");
    }

    @Test
    @DisplayName("going up keeps at least the root, and going deeper stops at the end of the road")
    void theTrail() {
        actions.goUp(0);
        assertEquals(List.of("Home"), model.path());

        actions.goUp(42);
        assertEquals(List.of("Home"), model.path());

        for (var i = 0; i < NavigationModel.DEEPER.size() + 3; i++) {
            actions.goDeeper();
        }
        assertEquals(NavigationModel.DEEPER.size() + 1, model.path().size());
        assertEquals(NavigationModel.DEEPER.getLast(), model.path().getLast());

        actions.goUp(2);
        assertEquals(List.of("Home", "Library"), model.path());
    }
}
