package dev.goldberry.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.example.model.ShowcaseActions;
import dev.goldberry.example.model.ShowcaseModel;
import dev.goldberry.example.ui.gallery.Gallery;
import dev.goldberry.example.ui.home.HomeScreen;
import dev.goldberry.example.ui.shell.ReadingSize;

/// The model's half of getting around: which screen is showing, Back and
/// Forward, the next screen down the sidebar, the sidebar's rail and the
/// reading size.
@DisplayName("getting around the showcase")
class ShowcaseNavigationTest {

    private final ShowcaseModel model = new ShowcaseModel();
    private final ShowcaseActions actions = new ShowcaseActions(model);

    @Test
    @DisplayName("opens on Home, with nowhere to go back to")
    void opensHome() {
        assertEquals(HomeScreen.NAME, model.shell().screen());
        assertFalse(model.shell().canGoBack());
        assertEquals(List.of(), model.shell().recentScreens());
    }

    @Test
    @DisplayName("Back and Forward retrace the screens picked")
    void history() {
        actions.shell().pickScreen("buttons");
        actions.shell().pickScreen("forms");

        actions.shell().back();
        assertEquals("buttons", model.shell().screen());
        assertTrue(model.shell().canGoForward());
        actions.shell().back();
        assertEquals(HomeScreen.NAME, model.shell().screen());
        actions.shell().forward();
        actions.shell().forward();
        assertEquals("forms", model.shell().screen());
        assertFalse(model.shell().canGoForward());
    }

    @Test
    @DisplayName("picking the screen that is showing is not a step in the history")
    void samePick() {
        actions.shell().pickScreen("buttons");
        actions.shell().pickScreen("buttons");
        actions.shell().back();

        assertEquals(HomeScreen.NAME, model.shell().screen());
        assertFalse(model.shell().canGoBack());
    }

    @Test
    @DisplayName("Next and Previous walk the sidebar's order")
    void nextAndPrevious() {
        actions.shell().nextScreen();
        assertEquals(Gallery.TABS.getFirst().name(), model.shell().screen());
        actions.shell().nextScreen();
        assertEquals(Gallery.TABS.get(1).name(), model.shell().screen());
        actions.shell().previousScreen();
        actions.shell().previousScreen();
        assertEquals(Gallery.TABS.getLast().name(), model.shell().screen());
    }

    @Test
    @DisplayName("Home offers the gallery screens visited lately, newest first, each once")
    void recent() {
        actions.shell().pickScreen("buttons");
        actions.shell().pickScreen("forms");
        actions.shell().pickScreen("buttons");
        actions.shell().pickScreen(HomeScreen.NAME);

        assertEquals(List.of("buttons", "forms"), model.shell().recentScreens());
    }

    @Test
    @DisplayName("the sidebar folds to a rail and back")
    void sidebar() {
        assertTrue(model.shell().isSidebarWide());
        actions.shell().toggleSidebar();
        assertFalse(model.shell().isSidebarWide());
    }

    @Test
    @DisplayName("the text grows and shrinks a step at a time, and a size can be picked by name")
    void reading() {
        assertEquals(ReadingSize.MEDIUM, model.shell().readingSize());
        actions.shell().largerText();
        assertEquals(ReadingSize.LARGE, model.shell().readingSize());
        actions.shell().smallerText();
        actions.shell().smallerText();
        assertEquals(ReadingSize.SMALL, model.shell().readingSize());
        actions.shell().pickReading("large");
        assertEquals(ReadingSize.LARGE, model.shell().readingSize());
    }
}
