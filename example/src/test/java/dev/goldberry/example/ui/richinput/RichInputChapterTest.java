package dev.goldberry.example.ui.richinput;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Theme;
import dev.goldberry.example.ShowcaseScene;
import dev.goldberry.example.model.forms.FormsModel;
import dev.goldberry.example.ui.gallery.Gallery;
import dev.goldberry.example.ui.gallery.Section;
import dev.goldberry.example.ui.richinput.composer.ComposerCard;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.offscreen.Session;
import dev.goldberry.text.mark.MarkedText;
import dev.goldberry.widgets.form.richinput.RichInput;
import dev.goldberry.widgets.panel.card.Card;

/// The Rich input screen: in the sidebar after Forms, every card on it, and
/// the markup card's fields following the model.
class RichInputChapterTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    @Test
    @DisplayName("is listed under Controls, after Forms")
    void listed() {
        var names = Gallery.names();
        assertEquals(names.indexOf("forms") + 1, names.indexOf(RichInputChapter.NAME));
        assertEquals(Section.CONTROLS, Gallery.tab(RichInputChapter.NAME).section());
        assertEquals("Rich input", Gallery.tab(RichInputChapter.NAME).title());
    }

    @Test
    @DisplayName("shows the markup card and every Java card, once each")
    void everyCard() {
        try (var scene = new ShowcaseScene();
                var session = session(scene)) {
            var ids = CardSession.walk(session.byId("root").orElseThrow())
                    .map(element -> element.widget())
                    .filter(Card.class::isInstance)
                    .map(card -> ((Card) card).attributes().id())
                    .filter(id -> id != null && (id.startsWith("rich-") || id.equals(ComposerCard.ID)))
                    .toList();
            // A masonry places each card under its shortest column, so the
            // order on screen is the wall's to choose.
            assertEquals(11, ids.size(), "each card once: " + ids);
            assertEquals(
                    Set.of(
                            "rich-markup-card",
                            KindsCard.ID,
                            RowsCard.ID,
                            StatesCard.ID,
                            BarCard.ID,
                            TriggersCard.ID,
                            StylesCard.ID,
                            ClipboardCard.ID,
                            EditorCard.ID,
                            ComposerCard.ID,
                            ConvertCard.ID),
                    Set.copyOf(ids));
        }
    }

    @Test
    @DisplayName("the markup card's fields take their attributes, and the bound ones follow the model")
    void markup() {
        try (var scene = new ShowcaseScene();
                var session = session(scene)) {
            var bound = field(session, "rich-markup-bound");
            assertEquals(2, bound.rows());
            assertEquals(4, bound.maxRows());
            assertEquals("Bound to app.rich-note", bound.placeholder());
            assertEquals(
                    FormsModel.RICH_NOTE,
                    Objects.requireNonNull(bound.binding()).get());
            assertTrue(field(session, "rich-markup-read-only").readOnly());
            assertEquals(List.of(), field(session, "rich-markup-barless").formatting());
            assertTrue(field(session, "rich-markup-disabled").disabled());
            session.click("rich-markup-clear");
            session.frame();
            assertEquals(MarkedText.EMPTY, bound(session, "rich-markup-bound"));
            assertEquals(MarkedText.EMPTY, bound(session, "rich-markup-read-only"));
            session.click("rich-markup-sample");
            session.frame();
            assertEquals(FormsModel.RICH_NOTE, bound(session, "rich-markup-bound"));
        }
    }

    private static Session session(ShowcaseScene scene) {
        var session = Offscreen.of(1280, 2400)
                .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                .session(scene.root(RichInputChapter.NAME));
        session.frame();
        return session;
    }

    private static Object bound(Session session, String id) {
        return Objects.requireNonNull(field(session, id).binding(), id).get();
    }

    private static RichInput field(Session session, String id) {
        return CardSession.widget(session, id, RichInput.class);
    }
}
