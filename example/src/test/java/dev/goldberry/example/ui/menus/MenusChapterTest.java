package dev.goldberry.example.ui.menus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Theme;
import dev.goldberry.example.Showcase;
import dev.goldberry.example.ShowcaseModel;
import dev.goldberry.example.ShowcaseScene;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.offscreen.Session;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.Widget;
import dev.goldberry.widgets.panel.card.Card;
import dev.goldberry.widgets.text.Text;

/// The Menus screen: its cards, accelerators that work with the bar shut, and a
/// menu that says so when the platform has nowhere to put it.
@DisplayName("the Menus screen")
class MenusChapterTest {

    private ShowcaseScene scene;
    private Fonts fonts;

    @BeforeEach
    void open() {
        RendererRequirement.enforce();
        scene = new ShowcaseScene();
        fonts = Fonts.bundled();
    }

    @AfterEach
    void close() {
        fonts.close();
        scene.close();
    }

    private Session session(Widget root) {
        return Offscreen.of(1280, 900)
                .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                .fonts(fonts)
                .session(root);
    }

    private static Stream<Element> walk(Element element) {
        return Stream.concat(Stream.of(element), element.children().stream().flatMap(MenusChapterTest::walk));
    }

    private static String text(Session session, String id) {
        return ((Text) session.byId(id).orElseThrow().widget()).content();
    }

    @Test
    @DisplayName("has the document's cards, then its own")
    void cards() {
        try (var session = session(scene.root("menus"))) {
            var ids = walk(session.byId("screen-menus").orElseThrow())
                    .map(Element::widget)
                    .filter(widget -> widget instanceof Card card
                            && card.attributes().classes().contains("wall-card"))
                    .map(widget -> ((Card) widget).attributes().id())
                    .toList();

            assertEquals(
                    Set.of(
                            "menubar-card",
                            "menu-card",
                            "context-card",
                            "menus-row-menu",
                            "menus-accelerators",
                            "menus-macos",
                            "menus-tray"),
                    Set.copyOf(ids));
            assertEquals(7, ids.size(), "a card twice: " + ids);
            // What the window's own commands are aimed at.
            assertTrue(session.byId("menu-button").isPresent(), "app.open-menu opens its menu at #menu-button");
            assertTrue(session.byId("context-target").isPresent());
        }
    }

    @Test
    @DisplayName("an accelerator a menu bar names works with every menu shut")
    void accelerators() {
        try (var session = session(new AcceleratorsCard())) {
            assertEquals("0 leagues", text(session, "tally"));

            session.key("Ctrl+Shift+A");
            session.key("Ctrl+Shift+A");
            assertEquals("2 leagues", text(session, "tally"));

            session.key("Ctrl+Shift+X");
            assertEquals("1 league", text(session, "tally"));
        }
    }

    @Test
    @DisplayName("the tray card's buttons run the tray's own first two commands")
    void trayCommands() {
        var models = new Showcase().models();
        var model = models.stream()
                .filter(ShowcaseModel.class::isInstance)
                .map(ShowcaseModel.class::cast)
                .findFirst()
                .orElseThrow();
        var actions = models.stream()
                .filter(ShowcaseModel.Actions.class::isInstance)
                .map(ShowcaseModel.Actions.class::cast)
                .findFirst()
                .orElseThrow();
        try (var session = session(new TrayCard(model, actions))) {
            var theme = model.theme();
            var density = model.density();

            session.click("tray-light");
            session.click("tray-density");

            assertNotEquals(theme, model.theme(), "Switch the light changed nothing");
            assertNotEquals(density, model.density(), "Switch the density changed nothing");
        }
    }

    @Test
    @DisplayName("a menu with nowhere to open says so rather than doing nothing")
    void noPopupWindows() {
        try (var session = session(new RowMenuCard())) {
            session.click(RowMenuCard.BUTTON);

            assertEquals("No popup windows here, so the menu cannot open.", text(session, "row-menu-last"));
        }
    }
}
