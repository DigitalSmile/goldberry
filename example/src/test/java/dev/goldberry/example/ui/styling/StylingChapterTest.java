package dev.goldberry.example.ui.styling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Theme;
import dev.goldberry.css.parse.ParseMode;
import dev.goldberry.example.ShowcaseScene;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.offscreen.Session;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.widget.Element;
import dev.goldberry.widgets.Density;
import dev.goldberry.widgets.panel.Panel;
import dev.goldberry.widgets.panel.card.Card;
import dev.goldberry.widgets.text.Text;

/// The Styling and Design system screens, mounted the way the window mounts
/// them, and used: every card the chapters need is on screen, and the cards
/// that do something do it.
class StylingChapterTest {

    /// Tall enough that every card on either wall is drawn and can be pressed.
    private static final int WIDTH = 1280;

    private static final int HEIGHT = 7000;

    private ShowcaseScene scene;

    private Fonts fonts;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        scene = new ShowcaseScene();
        fonts = Fonts.bundled();
    }

    @AfterEach
    void tearDown() {
        if (fonts != null) {
            fonts.close();
        }
        if (scene != null) {
            scene.close();
        }
    }

    private Session open(String screen) {
        return Offscreen.of(WIDTH, HEIGHT)
                .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                .fonts(fonts)
                .session(scene.root(screen));
    }

    private static List<String> cardIds(Session session, String screen) {
        var ids = new ArrayList<String>();
        walk(session.byId("screen-" + screen).orElseThrow())
                .map(Element::widget)
                .forEach(widget -> {
                    if (widget instanceof Card card
                            && card.attributes().classes().contains(ShowcaseCard.CARD)) {
                        ids.add(card.attributes().id());
                    }
                });
        return ids;
    }

    /// The ids in a fixed order: a masonry offers its cards to whichever column
    /// is shortest, so the tree's order is the layout's and not the chapter's.
    private static List<String> sorted(List<String> ids) {
        return ids.stream().sorted().toList();
    }

    private static Stream<Element> walk(Element element) {
        return Stream.concat(Stream.of(element), element.children().stream().flatMap(StylingChapterTest::walk));
    }

    private static String text(Session session, String id) {
        if (session.byId(id).orElseThrow().widget() instanceof Text text) {
            return text.content();
        }
        throw new AssertionError(id + " is not a text");
    }

    @Test
    @DisplayName("the Styling screen has a card for every section of the chapter, none missing and none twice")
    void stylingCards() {
        try (var session = open("styling")) {
            assertEquals(
                    sorted(List.of(
                            "styling-sheets",
                            "styling-cascade",
                            "styling-selectors",
                            "styling-parts",
                            "styling-properties",
                            "styling-box",
                            "styling-text-flow",
                            "styling-card",
                            "styling-colour",
                            "styling-gradients",
                            "styling-borders",
                            "styling-transform",
                            "motion-floor-card",
                            "motion-keyframes-card",
                            "motion-entering-card",
                            "styling-media",
                            "styling-cursor",
                            "styling-custom-properties",
                            "styling-inheritance",
                            "styling-restyle",
                            "styling-themes",
                            "styling-desktop",
                            "styling-text-scale")),
                    sorted(cardIds(session, "styling")));
            assertEquals(List.of(), session.overruns(), "nothing on the wall overruns its box");
        }
    }

    @Test
    @DisplayName("the Design system screen has a card for every section of the chapter, none missing and none twice")
    void designCards() {
        try (var session = open("design")) {
            assertEquals(
                    sorted(List.of(
                            "design-principles",
                            "design-palette",
                            "design-aliases",
                            "design-spacing",
                            "type-card",
                            "design-shape",
                            "design-icons",
                            "design-motion",
                            "design-states",
                            "design-focus",
                            "design-keyboard",
                            "design-density",
                            "design-scrollbars",
                            "design-metrics",
                            "design-accessibility",
                            "design-governance")),
                    sorted(cardIds(session, "design")));
            assertEquals(List.of(), session.overruns(), "nothing on the wall overruns its box");
        }
    }

    @Test
    @DisplayName("the theme, density and scroll-bar buttons ask the application, as Ctrl+T does")
    void themeButtons() {
        try (var session = open("styling")) {
            var theme = scene.model().theme();
            session.click("theme-switch");
            assertNotEquals(theme, scene.model().theme(), "the light did not switch");

            var density = scene.model().density();
            session.click("density-switch");
            assertNotEquals(density, scene.model().density());

            var bars = scene.model().scrollbars();
            session.click("scrollbars-switch");
            assertNotEquals(bars, scene.model().scrollbars());

            session.click("restyle-theme");
            assertEquals(theme, scene.model().theme(), "the restyle card switches the same light back");
            assertTrue(text(session, "theme-now").startsWith("Nord"), text(session, "theme-now"));
        }
    }

    @Test
    @DisplayName("the desktop card asks the capabilities it was given whether it can ask")
    void desktopCardReadsTheContext() {
        var cannot = new ThemeCards.Desktop(scene.actions(), Set.of());
        try (var session = Offscreen.of(480, 400)
                .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                .fonts(fonts)
                .session(cannot)) {
            assertEquals("This build cannot ask the desktop.", text(session, "desktop-says"));
        }
        try (var session = open("styling")) {
            assertNotEquals(
                    "This build cannot ask the desktop.",
                    text(session, "desktop-says"),
                    "the window's screen pins a build that can ask");
        }
    }

    @Test
    @DisplayName("the density card switches the density, and its controls keep their own values")
    void densityCard() {
        try (var session = open("design")) {
            assertEquals(Density.REGULAR, scene.model().density());
            session.click("design-density-switch");
            assertEquals(Density.COMPACT, scene.model().density());
        }
    }

    @Test
    @DisplayName("the turned button is pressed where it is drawn, and counts")
    void turnedButtonCounts() {
        try (var session = open("styling")) {
            assertEquals("Pressed 0 times", text(session, "transform-button-count"));
            session.click("transform-button");
            assertEquals("Pressed once", text(session, "transform-button-count"));
            session.click("transform-button");
            assertEquals("Pressed 2 times", text(session, "transform-button-count"));
        }
    }

    @Test
    @DisplayName("the sheet probe drops two rules when lenient and refuses the sheet when strict")
    void sheetProbe() {
        try (var session = open("styling")) {
            assertEquals("Press Read the sheet.", firstLine(session, "probe-modes-result"));

            session.click("probe-modes-read");
            var lenient = lines(session, "probe-modes-result");
            assertEquals("Kept 1 rule and dropped 2.", lenient.getFirst(), lenient.toString());
            assertTrue(lenient.stream().anyMatch(line -> line.contains(".lane::before")), lenient.toString());
            assertTrue(lenient.stream().anyMatch(line -> line.contains(":hovered")), lenient.toString());

            session.click("probe-modes-mode");
            session.click("probe-modes-read");
            var strict = lines(session, "probe-modes-result");
            assertEquals(1, strict.size(), strict.toString());
            assertTrue(strict.getFirst().startsWith("Refused the whole sheet at line 2"), strict.toString());
        }
    }

    @Test
    @DisplayName("the properties probe names the declarations that do nothing")
    void propertiesProbe() {
        var lines = SheetProbe.read(SheetCards.UNKNOWN_PROPERTIES, ParseMode.LENIENT);
        assertEquals("Kept 1 rule.", lines.getFirst(), lines.toString());
        assertTrue(lines.stream().anyMatch(line -> line.contains("letter-spacing")), lines.toString());
        assertTrue(lines.stream().anyMatch(line -> line.contains("justify")), lines.toString());
        assertTrue(lines.stream().noneMatch(line -> line.contains("font-style")), lines.toString());
    }

    @Test
    @DisplayName("the three durations send their swatches across and back")
    void durations() {
        try (var session = open("design")) {
            assertFalse(classesOf(session, "duration-overlay").contains("across"));
            session.click("durations-go");
            assertTrue(classesOf(session, "duration-overlay").contains("across"));
            session.click("durations-go");
            assertFalse(classesOf(session, "duration-overlay").contains("across"));
        }
    }

    private static Set<String> classesOf(Session session, String id) {
        if (session.byId(id).orElseThrow().widget() instanceof Panel panel) {
            return panel.attributes().classes();
        }
        throw new AssertionError(id + " is not a panel");
    }

    private static List<String> lines(Session session, String id) {
        return session.byId(id).orElseThrow().children().stream()
                .map(Element::widget)
                .filter(Text.class::isInstance)
                .map(Text.class::cast)
                .map(Text::content)
                .toList();
    }

    private static String firstLine(Session session, String id) {
        return lines(session, id).getFirst();
    }
}
