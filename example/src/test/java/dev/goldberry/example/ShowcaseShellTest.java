package dev.goldberry.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.example.ui.AppMenu;
import dev.goldberry.example.ui.Screen;
import dev.goldberry.render.window.WindowSpec;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widgets.menu.Item;
import dev.goldberry.widgets.menu.MenuBar;
import dev.goldberry.widgets.menu.Separator;

/// The window's three bands: how it opens, what its overflow menu says, and that the
/// gallery's screens are named in one place.
///
/// None of it is a thing a golden image can show: every picture in
/// [GalleryGoldenTest] is drawn at a size the test chose, so a window that opened
/// 200px wide would look exactly the same in every one of them.
class ShowcaseShellTest {

    private final Showcase showcase = new Showcase();

    // --- how the window opens ------------------------------------------------

    @Test
    @DisplayName("the showcase opens maximized, and asks for a size to restore to")
    void itOpensMaximized() {
        assertTrue(showcase.maximized(), "a gallery is a layout whose whole subject is how much fits on a screen");

        // Not incidental: `size()` is what un-maximizing puts the window back to,
        // and 1280 is the narrowest shape on which the Forms screen's three
        // columns are still three columns.
        assertEquals(1280, showcase.size().width());
        assertEquals(800, showcase.size().height());
    }

    @Test
    @DisplayName("a plain application is not maximized, which is the right default")
    void theDefaultIsNo() {
        var plain = new dev.goldberry.Application() {
            @Override
            public dev.goldberry.widget.Widget root() {
                return null;
            }
        };

        assertFalse(plain.maximized());
    }

    @Test
    @DisplayName("the spec the launcher would build carries both facts")
    void theSpecCarriesIt() {
        var spec = WindowSpec.of(showcase.title(), showcase.size()).withMaximized(showcase.maximized());

        assertTrue(spec.maximized());
        assertEquals(showcase.size(), spec.size());
    }

    // --- the gallery ---------------------------------------------------------

    /// A screen per chapter of the guide, section by section in the sidebar's
    /// order, the guide's order within a section, then the Lookalike screen
    /// last. The chapters with nothing to try are Home's, as links. Ten have a
    /// digit, which are the first ten in sidebar order; the rest are reached by
    /// the sidebar, search, and Edit ▸ Go to.
    ///
    /// Written out rather than read from [dev.goldberry.example.ui.gallery.Gallery],
    /// because what this pins is the order a reader of the guide meets: a screen
    /// moved in the list moves a digit, and that is a change worth seeing in a
    /// diff.
    @Test
    @DisplayName("a screen per chapter, in the sidebar's order; the first ten have a digit")
    void theGallery() {
        assertEquals(
                List.of(
                        "layout",
                        "scrolling",
                        "text",
                        "styling",
                        "design",
                        "buttons",
                        "choices",
                        "values",
                        "forms",
                        "panels",
                        "collections",
                        "navigation",
                        "menus",
                        "overlays",
                        "windows",
                        "charts",
                        "drawing",
                        "icons",
                        "emoji",
                        "gpu",
                        "audio",
                        "video",
                        "markdown",
                        "html",
                        "web",
                        "input",
                        "diagnostics",
                        "application",
                        "lookalike"),
                Screen.GALLERY);
        assertEquals("panels", Screen.GALLERY.get(9), "Ctrl+0 is the tenth screen");
    }

    @Test
    @DisplayName("every screen has a title, and a name that is not one is refused")
    void everyScreenIsNamed() {
        for (var name : Screen.GALLERY) {
            var title = Screen.title(name);
            assertNotNull(title);
            assertFalse(title.isBlank(), () -> name + " has a blank title");
        }

        // Refused rather than defaulted, because a defaulted title is a menu row
        // reading "collections" that nobody notices for a month.
        var thrown = assertThrows(IllegalArgumentException.class, () -> Screen.title("histograms"));
        assertTrue(thrown.getMessage().contains("histograms"), thrown.getMessage());
    }

    // --- the menu bar --------------------------------------------------------

    /// One counter per window command, so a test can press a row and ask which
    /// handler ran.
    ///
    /// Seven [Runnable]s of one type is exactly the shape a positional slip hides
    /// in — Help ▸ Take the tour selected a screen and Help ▸ About opened the
    /// unsaved-changes dialog until the 2026-09-18 review read the rows against
    /// their names. So the counters are named and every row is pressed.
    private record Presses(
            AtomicInteger dialogs,
            AtomicInteger huds,
            AtomicInteger toasts,
            AtomicInteger tours,
            AtomicInteger abouts,
            AtomicInteger quits,
            AtomicInteger searches) {

        Presses() {
            this(
                    new AtomicInteger(),
                    new AtomicInteger(),
                    new AtomicInteger(),
                    new AtomicInteger(),
                    new AtomicInteger(),
                    new AtomicInteger(),
                    new AtomicInteger());
        }

        AppMenu.Handlers handlers() {
            return new AppMenu.Handlers(
                    dialogs::incrementAndGet,
                    huds::incrementAndGet,
                    toasts::incrementAndGet,
                    tours::incrementAndGet,
                    abouts::incrementAndGet,
                    quits::incrementAndGet,
                    searches::incrementAndGet);
        }

        /// What every counter reads, in the order the record declares them.
        List<Integer> counts() {
            return List.of(
                    dialogs.get(), huds.get(), toasts.get(), tours.get(), abouts.get(), quits.get(), searches.get());
        }
    }

    private AppMenu menu(Presses presses) {
        return new AppMenu(showcase.actions(), presses.handlers(), null);
    }

    private static List<Item> itemsOf(MenuBar bar) {
        return bar.children().stream()
                .filter(Item.class::isInstance)
                .map(Item.class::cast)
                .toList();
    }

    /// The four menus, under one overflow heading.
    private static List<Item> menusOf(MenuBar bar) {
        return itemsOf(bar).getFirst().submenu().stream()
                .filter(Item.class::isInstance)
                .map(Item.class::cast)
                .toList();
    }

    @Test
    @DisplayName("the bar is one overflow heading holding File, Edit, View and Help")
    void theBarIsAnOverflow() {
        var bar = menu(new Presses()).bar(false);

        assertEquals(
                List.of(AppMenu.HEADING), itemsOf(bar).stream().map(Item::label).toList());
        var titles = menusOf(bar).stream().map(Item::label).toList();
        assertEquals(List.of("File", "Edit", "View", "Help"), titles);
        for (var item : menusOf(bar)) {
            assertFalse(item.submenu().isEmpty(), () -> item.label() + " is a heading with nothing under it");
        }
    }

    @Test
    @DisplayName("the window's seven commands are reachable from the bar and nowhere else")
    void theWindowCommandsAreWired() {
        var presses = new Presses();
        var bar = menu(presses).bar(false);

        // Pressed by label rather than by position, so inserting a row above one
        // of them does not silently move which command this asserts.
        press(bar, "Unsaved changes…");
        press(bar, "Frame rate");
        press(bar, "Send word");
        press(bar, "Take the tour");
        press(bar, "About Goldberry");
        press(bar, "Quit");
        press(bar, "Search…");

        assertEquals(1, presses.searches().get(), "View ▸ Search… opens no palette");
        assertEquals(1, presses.dialogs().get(), "File ▸ Unsaved changes… opens no dialog");
        assertEquals(1, presses.huds().get(), "Help ▸ Frame rate floats no HUD");
        assertEquals(1, presses.toasts().get(), "Edit ▸ Send word raises no toast");
        assertEquals(1, presses.tours().get(), "Help ▸ Take the tour starts no tour");
        assertEquals(1, presses.abouts().get(), "Help ▸ About Goldberry opens no about box");
        assertEquals(1, presses.quits().get(), "File ▸ Quit closes nothing");
    }

    /// The test the review's B8 asked for: not "a handler ran" but "*this* handler
    /// ran and no other".
    ///
    /// Six positional [Runnable]s of one type cannot be checked any other way, and
    /// the two rows below were wrong for as long as nothing did check — Take the
    /// tour called `pickScreen("navigation")`, which is the first line of a tour
    /// and not a tour, and About Goldberry opened the `Turn back?` dialog.
    @Test
    @DisplayName("every Help row runs its own handler and nobody else's")
    void eachRowRunsItsOwnHandler() {
        assertEquals(List.of(0, 0, 0, 1, 0, 0, 0), pressedAlone("Take the tour"));
        assertEquals(List.of(0, 0, 0, 0, 1, 0, 0), pressedAlone("About Goldberry"));
        assertEquals(List.of(0, 1, 0, 0, 0, 0, 0), pressedAlone("Frame rate"));
        assertEquals(List.of(1, 0, 0, 0, 0, 0, 0), pressedAlone("Unsaved changes…"));
        assertEquals(List.of(0, 0, 1, 0, 0, 0, 0), pressedAlone("Send word"));
        assertEquals(List.of(0, 0, 0, 0, 0, 1, 0), pressedAlone("Quit"));
        assertEquals(List.of(0, 0, 0, 0, 0, 0, 1), pressedAlone("Search…"));
    }

    private List<Integer> pressedAlone(String label) {
        var presses = new Presses();
        press(menu(presses).bar(false), label);
        return presses.counts();
    }

    @Test
    @DisplayName("the frame-rate row draws a tick when the HUD is up, and none when it is not")
    void theHudRowIsCheckable() {
        var off = row(menu(new Presses()).bar(false), "Frame rate");
        var on = row(menu(new Presses()).bar(true), "Frame rate");

        assertTrue(off.isCheckable(), "a HUD is a state you are in, not a step you take");
        assertEquals(Boolean.FALSE, off.checked());
        assertEquals(Boolean.TRUE, on.checked());
    }

    @Test
    @DisplayName("Edit ▸ Go to has a row per screen, off the gallery's own list")
    void goToFollowsTheGallery() {
        var bar = menu(new Presses()).bar(false);

        var goTo = row(bar, "Go to");
        var names = goTo.submenu().stream()
                .filter(Item.class::isInstance)
                .map(Item.class::cast)
                .map(Item::label)
                .toList();

        assertEquals(
                Screen.GALLERY.stream().map(Screen::title).toList(),
                names,
                "a screen added to the gallery has to arrive here without this menu" + " being touched");
    }

    @Test
    @DisplayName("the destructive rows are last, behind a rule")
    void theDestructiveRowsAreFenced() {
        var bar = menu(new Presses()).bar(false);

        var file = menusOf(bar).getFirst().submenu();
        var quit = file.getLast();
        assertTrue(quit instanceof Item item && "Quit".equals(item.label()), "Quit is not the last row of File");

        // Somewhere above Quit and below the ordinary rows: a destructive row
        // beside an ordinary one is a row somebody presses by accident.
        var lastRule = -1;
        for (var i = 0; i < file.size(); i++) {
            if (file.get(i) instanceof Separator) {
                lastRule = i;
            }
        }
        assertTrue(lastRule >= 0 && lastRule < file.size() - 2, "nothing separates Quit from the rows above it");
    }

    @Test
    @DisplayName("View ticks the reading size in force and the rail, and greys Back when there is no way back")
    void theViewMenuFollowsTheModel() {
        var bar = menu(new Presses()).bar(false);

        assertEquals(Boolean.TRUE, row(bar, "Medium text").checked());
        assertEquals(Boolean.FALSE, row(bar, "Large text").checked());
        assertEquals(Boolean.FALSE, row(bar, "Sidebar as icons").checked());
        assertTrue(row(bar, "Back").disabled(), "a window that has just opened has nowhere to go back to");
        assertEquals("Ctrl+K", row(bar, "Search…").accelerator());
    }

    /// The one row in the whole bar that is disabled, and stays disabled: the
    /// counter has an undo and no redo, and a menu that offered one anyway would
    /// be a row that silently did nothing.
    @Test
    @DisplayName("a command the model cannot answer is disabled rather than absent")
    void oneRowIsHonestlyDisabled() {
        var bar = menu(new Presses()).bar(false);

        assertTrue(row(bar, "Press on").disabled());
        assertFalse(row(bar, "Turn back").disabled());
    }

    // --- the root ------------------------------------------------------------

    /// Built the way [GalleryGoldenTest] builds it rather than through
    /// [Showcase#start], which wants a real [dev.goldberry.Host]
    /// to hang eleven accelerators off.
    @Test
    @DisplayName("the window is a top bar, a body of sidebar and content, and a footer")
    void theWindowIsThreeBands() {
        dev.goldberry.RendererRequirement.enforce();
        var model = showcase.model();
        var actions = showcase.actions();

        try (var plus = dev.goldberry.icon.Icon.bundled("plus", 16)) {
            var inflater = dev.goldberry.widgets.Widgets.inflater(
                    model.named(),
                    dev.goldberry.widgets.Icons.strict().bind("palette", plus).bind("plus", plus),
                    showcase.models().toArray());
            var screen = new Screen(model, actions, inflater, plus, () -> {}, () -> {}, menu(new Presses()));

            // `#root` and not the tree's root: `Screen` is stateful, so the
            // element at the top is the widget and the column it builds is under
            // it.
            var root = byId(new ElementTree(screen).root(), "root");
            assertNotNull(root, "the window has no #root column");
            var bands = new ArrayList<String>();
            root.children().forEach(child -> bands.add(child.widget().getClass().getSimpleName()));

            assertEquals(
                    List.of("TopBar", "Row", "Row"),
                    bands,
                    "the top bar is above the body and the body above the footer");
            var body = root.children().get(1);
            assertEquals("body", body.id());
            assertEquals(
                    List.of("Sidebar", "Column"),
                    body.children().stream()
                            .map(child -> child.widget().getClass().getSimpleName())
                            .toList(),
                    "the sidebar is beside the content");
        }
    }

    private static Element byId(Element from, String id) {
        if (id.equals(from.id())) {
            return from;
        }
        for (var child : from.children()) {
            var found = byId(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Test
    @DisplayName("the window's own commands are named for the documents that press them")
    void theDocumentsCanReachTheWindow() {
        var window = showcase.models().stream()
                .filter(WindowActions.class::isInstance)
                .map(WindowActions.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("overlays.kdl presses app.open-menu and nothing binds it"));

        var registry = dev.goldberry.bind.runtime.Models.actions(window);
        for (var action : List.of("app.open-menu", "app.toggle-hud", "app.open-dialog", "app.raise-toast")) {
            assertNotNull(registry.resolve(action), () -> action + " is not bound");
        }
    }

    @Test
    @DisplayName("the model and its actions are the same pair the documents see")
    void theModelsAreOneObject() {
        var model = showcase.model();
        var actions = showcase.actions();

        assertTrue(actions.write(model), "the actions write to a different model from the one the documents read");
        // And the documents see exactly those slices: every one of them, in
        // order, and then the window's own commands.
        var published = showcase.models();
        var slices = new ArrayList<Object>(model.slices());
        slices.addAll(actions.slices());
        assertEquals(slices.size() + 1, published.size(), "the window publishes something besides its slices");
        for (var i = 0; i < slices.size(); i++) {
            assertSame(slices.get(i), published.get(i), "the documents read a different slice from the one Java reads");
        }
        assertInstanceOf(WindowActions.class, published.getLast());
    }

    // --- finding a row -------------------------------------------------------

    private static Item row(MenuBar bar, String label) {
        for (var top : itemsOf(bar)) {
            var found = find(top, label);
            if (found != null) {
                return found;
            }
        }
        throw new AssertionError("no row called \"" + label + "\" on the bar");
    }

    private static Item find(Item item, String label) {
        if (label.equals(item.label())) {
            return item;
        }
        for (var child : item.submenu()) {
            if (child instanceof Item nested) {
                var found = find(nested, label);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void press(MenuBar bar, String label) {
        var item = row(bar, label);
        assertNotNull(item.onPress(), () -> label + " does nothing");
        item.onPress().run();
    }
}
