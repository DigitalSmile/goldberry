package dev.goldberry.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.example.ui.gallery.Gallery;
import dev.goldberry.example.ui.home.HomeScreen;
import dev.goldberry.example.ui.shell.ReadingSize;

/// The model's half of getting around: which screen is showing, Back and
/// Forward, the next screen down the sidebar, the sidebar's rail and the
/// reading size.
@DisplayName("getting around the showcase")
class ShowcaseNavigationTest {

    private final ShowcaseModel model = new ShowcaseModel();
    private final ShowcaseModel.Actions actions = new ShowcaseModel.Actions(model);

    @Test
    @DisplayName("opens on Home, with nowhere to go back to")
    void opensHome() {
        assertEquals(HomeScreen.NAME, model.screen());
        assertFalse(model.canGoBack());
        assertEquals(List.of(), model.recentScreens());
    }

    @Test
    @DisplayName("Back and Forward retrace the screens picked")
    void history() {
        actions.pickScreen("buttons");
        actions.pickScreen("forms");

        actions.back();
        assertEquals("buttons", model.screen());
        assertTrue(model.canGoForward());
        actions.back();
        assertEquals(HomeScreen.NAME, model.screen());
        actions.forward();
        actions.forward();
        assertEquals("forms", model.screen());
        assertFalse(model.canGoForward());
    }

    @Test
    @DisplayName("picking the screen that is showing is not a step in the history")
    void samePick() {
        actions.pickScreen("buttons");
        actions.pickScreen("buttons");
        actions.back();

        assertEquals(HomeScreen.NAME, model.screen());
        assertFalse(model.canGoBack());
    }

    @Test
    @DisplayName("Next and Previous walk the sidebar's order")
    void nextAndPrevious() {
        actions.nextScreen();
        assertEquals(Gallery.TABS.getFirst().name(), model.screen());
        actions.nextScreen();
        assertEquals(Gallery.TABS.get(1).name(), model.screen());
        actions.previousScreen();
        actions.previousScreen();
        assertEquals(Gallery.TABS.getLast().name(), model.screen());
    }

    @Test
    @DisplayName("Home offers the gallery screens visited lately, newest first, each once")
    void recent() {
        actions.pickScreen("buttons");
        actions.pickScreen("forms");
        actions.pickScreen("buttons");
        actions.pickScreen(HomeScreen.NAME);

        assertEquals(List.of("buttons", "forms"), model.recentScreens());
    }

    @Test
    @DisplayName("the sidebar folds to a rail and back")
    void sidebar() {
        assertTrue(model.isSidebarWide());
        actions.toggleSidebar();
        assertFalse(model.isSidebarWide());
    }

    @Test
    @DisplayName("the text grows and shrinks a step at a time, and a size can be picked by name")
    void reading() {
        assertEquals(ReadingSize.MEDIUM, model.readingSize());
        actions.largerText();
        assertEquals(ReadingSize.LARGE, model.readingSize());
        actions.smallerText();
        actions.smallerText();
        assertEquals(ReadingSize.SMALL, model.readingSize());
        actions.pickReading("large");
        assertEquals(ReadingSize.LARGE, model.readingSize());
    }
}
