package dev.goldberry.example.ui.collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Theme;
import dev.goldberry.example.ShowcaseScene;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.offscreen.Session;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Styled;
import dev.goldberry.widgets.panel.card.Card;
import dev.goldberry.widgets.text.Text;

/// The Collections screen: its cards, and the cards that answer a click.
@DisplayName("the Collections screen")
class CollectionsChapterTest {

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
        if (fonts != null) {
            fonts.close();
        }
        if (scene != null) {
            scene.close();
        }
    }

    private Session session(Widget root) {
        return Offscreen.of(1280, 900)
                .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                .fonts(fonts)
                .session(root);
    }

    static Stream<Element> walk(Element element) {
        return Stream.concat(Stream.of(element), element.children().stream().flatMap(CollectionsChapterTest::walk));
    }

    static String text(Session session, String id) {
        return ((Text) session.byId(id).orElseThrow().widget()).content();
    }

    @Test
    @DisplayName("has a card for the list, the table, the tree and the slot")
    void cards() {
        try (var session = session(scene.root("collections"))) {
            var ids = walk(session.byId("screen-collections").orElseThrow())
                    .map(Element::widget)
                    .filter(widget -> widget instanceof Card card
                            && card.attributes().classes().contains("wall-card"))
                    .map(widget -> ((Card) widget).attributes().id())
                    .toList();

            assertEquals(Set.of("leagues-card", "company-card", "lands-card", "collections-slot"), Set.copyOf(ids));
            assertEquals(4, ids.size(), "a card twice: " + ids);
        }
    }

    @Test
    @DisplayName("a slot draws the widget the value holds, and a new value redraws it")
    void slot() {
        try (var session = session(new SlotCard())) {
            assertEquals("Nothing selected", text(session, "slot-region"));

            var rows = walk(session.byId("slot-list").orElseThrow())
                    .filter(element -> element.widget() instanceof Styled styled
                            && styled.cssType().equals("list-row"))
                    .toList();
            session.click(rows.get(1));

            assertEquals("Samwise", text(session, "slot-name"));
            assertTrue(session.byId("slot-region").isPresent(), "the region lost the document's id");
        }
    }

    @Test
    @DisplayName("a header asks for a sort and the card does it")
    void sorting() {
        try (var session = session(new CompanyCard())) {
            var headers = walk(session.byId("company").orElseThrow())
                    .filter(element -> element.widget() instanceof Styled styled
                            && styled.cssType().equals("table-header"))
                    .toList();
            assertEquals(4, headers.size());

            session.click(headers.getFirst());

            var first = walk(session.byId("company").orElseThrow())
                    .filter(element -> element.widget() instanceof Styled styled
                            && styled.cssType().equals("table-cell"))
                    .flatMap(CollectionsChapterTest::walk)
                    .map(Element::widget)
                    .filter(Text.class::isInstance)
                    .map(widget -> ((Text) widget).content())
                    .findFirst()
                    .orElseThrow();
            assertEquals("Aragorn", first, "sorted by name, Aragorn comes first");
        }
    }
}
