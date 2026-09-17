package io.github.digitalsmile.goldberry.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.example.ui.AppMenu;
import io.github.digitalsmile.goldberry.example.ui.Screen;
import io.github.digitalsmile.goldberry.render.window.WindowSpec;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widgets.menu.Item;
import io.github.digitalsmile.goldberry.widgets.menu.MenuBar;
import io.github.digitalsmile.goldberry.widgets.menu.Separator;

/// The window's three bands: how it opens, what its menu bar says, and that the
/// gallery's nine screens are named in one place
/// (ADR-0221,
/// ADR-0222).
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
        var plain = new io.github.digitalsmile.goldberry.Application() {
            @Override
            public io.github.digitalsmile.goldberry.widget.Widget root() {
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

    /// Thirteen screens, ten of which have a digit — and **which** ten is the
    /// decision this asserts ([ADR-0307]).
    ///
    /// This test used to say `GALLERY.size() <= 10` and call the eleventh screen
    /// "a decision about which one loses its key". The decision is that **none of
    /// them does**: `Ctrl+1`…`Ctrl+0` keep meaning exactly what they have always
    /// meant, and `icons`, `emoji` and `motion` are reached by the strip, by the
    /// arrows inside it, and by Edit ▸ Go to.
    ///
    /// The reason is what the two kinds of screen are for. The first ten are
    /// galleries a reader moves *between* — the digit is worth having because the
    /// comparison is the point. The two sheets are references opened once and
    /// searched, and re-pointing a shortcut somebody already knows in order to
    /// give one a key would cost more than it bought ([ADR-0386] added the second
    /// of them and moved no digit, which is the property being kept).
    ///
    /// What is still load-bearing is that the digits and the strip agree about
    /// the first ten, which is [GalleryOrderTest]'s.
    @Test
    @DisplayName("thirteen screens; the first ten have a digit and the rest have the strip")
    void theGallery() {
        assertEquals(
                List.of(
                        "basic",
                        "panels",
                        "overlays",
                        "forms",
                        "navigation",
                        "collections",
                        "charts",
                        "markdown",
                        "html",
                        "canvas",
                        "icons",
                        "emoji",
                        "motion"),
                Screen.GALLERY);

        // A keyboard has ten digits and `Screen.GALLERY` may be longer. What must
        // not drift is the *prefix*: the screens that have keys are the first ten
        // in strip order, so no digit ever points at a different screen than it
        // did yesterday.
        assertEquals(
                "canvas",
                Screen.GALLERY.get(9),
                "Ctrl+0 is the tenth screen, and inserting one above it would move every digit");
        assertTrue(
                Screen.GALLERY.size() > 10,
                "if the gallery is back to ten, this test and ADR-0307 are describing a window"
                        + " that no longer exists");
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

    private AppMenu menu(AtomicInteger dialogs, AtomicInteger huds, AtomicInteger toasts, AtomicInteger quits) {
        var model = showcase.models().stream()
                .filter(ShowcaseModel.Actions.class::isInstance)
                .map(ShowcaseModel.Actions.class::cast)
                .findFirst()
                .orElseThrow();
        return new AppMenu(
                model,
                new AppMenu.Handlers(
                        dialogs::incrementAndGet, huds::incrementAndGet,
                        toasts::incrementAndGet, quits::incrementAndGet),
                null);
    }

    private static List<Item> itemsOf(MenuBar bar) {
        return bar.children().stream()
                .filter(Item.class::isInstance)
                .map(Item.class::cast)
                .toList();
    }

    @Test
    @DisplayName("the bar is File, Edit and Help, and every one of them has a submenu")
    void theBarIsThree() {
        var bar = menu(new AtomicInteger(), new AtomicInteger(), new AtomicInteger(), new AtomicInteger())
                .bar(false);

        var titles = itemsOf(bar).stream().map(Item::label).toList();
        assertEquals(List.of("File", "Edit", "Help"), titles);
        for (var item : itemsOf(bar)) {
            assertFalse(item.submenu().isEmpty(), () -> item.label() + " is a heading with nothing under it");
        }
    }

    @Test
    @DisplayName("the window's four commands are reachable from the bar and nowhere else")
    void theWindowCommandsAreWired() {
        var dialogs = new AtomicInteger();
        var huds = new AtomicInteger();
        var toasts = new AtomicInteger();
        var quits = new AtomicInteger();
        var bar = menu(dialogs, huds, toasts, quits).bar(false);

        // Pressed by label rather than by position, so inserting a row above one
        // of them does not silently move which command this asserts.
        press(bar, "Unsaved changes…");
        press(bar, "Frame rate");
        press(bar, "Send word");
        press(bar, "Quit");

        assertEquals(1, dialogs.get(), "File ▸ Unsaved changes… opens no dialog");
        assertEquals(1, huds.get(), "Help ▸ Frame rate floats no HUD");
        assertEquals(1, toasts.get(), "Edit ▸ Send word raises no toast");
        assertEquals(1, quits.get(), "File ▸ Quit closes nothing");
    }

    @Test
    @DisplayName("the frame-rate row draws a tick when the HUD is up, and none when it is not")
    void theHudRowIsCheckable() {
        var off = row(
                menu(new AtomicInteger(), new AtomicInteger(), new AtomicInteger(), new AtomicInteger())
                        .bar(false),
                "Frame rate");
        var on = row(
                menu(new AtomicInteger(), new AtomicInteger(), new AtomicInteger(), new AtomicInteger())
                        .bar(true),
                "Frame rate");

        assertTrue(off.isCheckable(), "a HUD is a state you are in, not a step you take");
        assertEquals(Boolean.FALSE, off.checked());
        assertEquals(Boolean.TRUE, on.checked());
    }

    @Test
    @DisplayName("Edit ▸ Go to has a row per screen, off the gallery's own list")
    void goToFollowsTheGallery() {
        var bar = menu(new AtomicInteger(), new AtomicInteger(), new AtomicInteger(), new AtomicInteger())
                .bar(false);

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
        var bar = menu(new AtomicInteger(), new AtomicInteger(), new AtomicInteger(), new AtomicInteger())
                .bar(false);

        var file = itemsOf(bar).getFirst().submenu();
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

    /// The one row in the whole bar that is disabled, and stays disabled: the
    /// counter has an undo and no redo, and a menu that offered one anyway would
    /// be a row that silently did nothing.
    @Test
    @DisplayName("a command the model cannot answer is disabled rather than absent")
    void oneRowIsHonestlyDisabled() {
        var bar = menu(new AtomicInteger(), new AtomicInteger(), new AtomicInteger(), new AtomicInteger())
                .bar(false);

        assertTrue(row(bar, "Press on").disabled());
        assertFalse(row(bar, "Turn back").disabled());
    }

    // --- the root ------------------------------------------------------------

    /// Built the way [GalleryGoldenTest] builds it rather than through
    /// [Showcase#start], which wants a real [io.github.digitalsmile.goldberry.Host]
    /// to hang eleven accelerators off.
    @Test
    @DisplayName("the window is a menu bar, a bar and a gallery, in that order")
    void theWindowIsThreeBands() {
        io.github.digitalsmile.goldberry.RendererRequirement.enforce();
        var model = showcase.models().stream()
                .filter(ShowcaseModel.class::isInstance)
                .map(ShowcaseModel.class::cast)
                .findFirst()
                .orElseThrow();
        var actions = showcase.models().stream()
                .filter(ShowcaseModel.Actions.class::isInstance)
                .map(ShowcaseModel.Actions.class::cast)
                .findFirst()
                .orElseThrow();

        try (var plus = io.github.digitalsmile.goldberry.icon.Icon.bundled("plus", 16)) {
            var inflater = io.github.digitalsmile.goldberry.widgets.Widgets.inflater(
                    model.named(),
                    io.github.digitalsmile.goldberry.widgets.Icons.strict()
                            .bind("palette", plus)
                            .bind("plus", plus),
                    showcase.models().toArray());
            var screen = new Screen(
                    model,
                    actions,
                    inflater,
                    plus,
                    () -> {},
                    menu(new AtomicInteger(), new AtomicInteger(), new AtomicInteger(), new AtomicInteger()));

            // `#root` and not the tree's root: `Screen` is stateful, so the
            // element at the top is the widget and the column it builds is under
            // it.
            var root = byId(new ElementTree(screen).root(), "root");
            assertNotNull(root, "the window has no #root column");
            // By widget class rather than by id: `MenuBar` and `Tabs` are
            // stateful, so the element at this level is the widget itself and the
            // `#app-menu` / `#gallery` ids land on the box its state builds one
            // level further down.
            var bands = new ArrayList<String>();
            root.children().forEach(child -> bands.add(child.widget().getClass().getSimpleName()));

            assertEquals(
                    List.of("MenuBar", "Row", "Tabs"),
                    bands,
                    "the menu is above the bar and the bar above the gallery");
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

        var registry = io.github.digitalsmile.goldberry.bind.runtime.Models.actions(window);
        for (var action : List.of("app.open-menu", "app.toggle-hud", "app.open-dialog", "app.raise-toast")) {
            assertNotNull(registry.resolve(action), () -> action + " is not bound");
        }
    }

    @Test
    @DisplayName("the model and its actions are the same pair the documents see")
    void theModelsAreOneObject() {
        var model = showcase.models().stream()
                .filter(ShowcaseModel.class::isInstance)
                .map(ShowcaseModel.class::cast)
                .findFirst()
                .orElseThrow();
        var actions = showcase.models().stream()
                .filter(ShowcaseModel.Actions.class::isInstance)
                .map(ShowcaseModel.Actions.class::cast)
                .findFirst()
                .orElseThrow();

        assertSame(model, actions.values(), "the actions write to a different model from the one the documents read");
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
