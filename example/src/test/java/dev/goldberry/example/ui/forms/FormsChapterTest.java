package dev.goldberry.example.ui.forms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.bind.runtime.Models;
import dev.goldberry.css.Theme;
import dev.goldberry.css.select.Selector.PseudoClass;
import dev.goldberry.example.ShowcaseScene;
import dev.goldberry.example.docs.CardShape;
import dev.goldberry.input.key.Key;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.offscreen.Session;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.widget.Element;
import dev.goldberry.widgets.form.textinput.TextInput;
import dev.goldberry.widgets.panel.card.Card;
import dev.goldberry.widgets.text.Text;

/// The Forms screen, mounted the way the window mounts it and then typed into.
@DisplayName("the Forms screen")
class FormsChapterTest {

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

    /// Tall enough that every card is drawn, so each one can be pressed.
    private void withScreen(Consumer<Session> test) {
        try (var session = Offscreen.of(1280, 2400)
                .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                .fonts(fonts)
                .session(scene.root("forms"))) {
            test.accept(session);
        }
    }

    @Test
    @DisplayName("has a card for every section of the chapter")
    void hasEveryCard() {
        withScreen(session -> {
            var screen = session.byId("screen-forms").orElseThrow();
            var ids = walk(screen)
                    .filter(element -> element.widget() instanceof Card card && CardShape.isGalleryCard(card))
                    .map(Element::id)
                    .sorted()
                    .toList();
            assertEquals(
                    Stream.of(
                                    "forms-model",
                                    "named-card",
                                    "limits-card",
                                    "overflow-card",
                                    "forms-text-area",
                                    "gutter-card",
                                    "label-card",
                                    "signup-card",
                                    "stacked-card",
                                    "code-card",
                                    "date-card",
                                    "forms-time",
                                    "paint-card")
                            .sorted()
                            .toList(),
                    ids);
            walk(screen)
                    .filter(element -> element.widget() instanceof Card card && CardShape.isGalleryCard(card))
                    .forEach(element -> assertEquals(List.of(), CardShape.problems((Card) element.widget())));
        });
    }

    @Test
    @DisplayName("a field tells the model, and the other field and the echo follow")
    void aFieldTellsTheModel() {
        withScreen(session -> {
            assertTrue(session.focus("model-first"));
            session.type("Samwise");

            assertEquals("Samwise", Models.observable(scene.model(), "app.name").get());
            assertEquals("Samwise", text(session, "name-echo"));
            assertEquals("Samwise", input(session, "model-second").resolved());
        });
    }

    @Test
    @DisplayName("a required field speaks once it is left empty")
    void aRequiredFieldSpeaksWhenLeft() {
        withScreen(session -> {
            assertFalse(bearerField(session).hasState(PseudoClass.INVALID), "silent before it is visited");

            assertTrue(session.focus("bearer"));
            assertFalse(bearerField(session).hasState(PseudoClass.INVALID), "silent while it is being typed in");
            session.key(Key.TAB);

            assertTrue(bearerField(session).hasState(PseudoClass.INVALID), "speaks once it is left empty");

            assertTrue(session.focus("bearer"));
            session.type("Frodo");
            assertFalse(bearerField(session).hasState(PseudoClass.INVALID), "quiet again once it is filled");
        });
    }

    @Test
    @DisplayName("a click on a label focuses its field")
    void aLabelFocusesItsField() {
        withScreen(session -> {
            var labels = walk(session.byId("labelled").orElseThrow())
                    .filter(element -> "field-label".equals(element.type()))
                    .toList();
            assertEquals(2, labels.size());

            session.click(labels.getLast());

            assertEquals("label-target", session.focused().map(Element::id).orElse(null));
        });
    }

    @Test
    @DisplayName("a form refuses an empty submission and says how much is wrong")
    void enlistRefusesAnEmptyForm() {
        withScreen(session -> {
            session.click("enlist");

            assertEquals("2 things to put right", text(session, "signup-status"));
        });
    }

    private static String text(Session session, String id) {
        return ((Text) session.byId(id).orElseThrow().widget()).resolved();
    }

    /// The text input with this id: the control, not the field it draws.
    private static TextInput input(Session session, String id) {
        return session.byId(id).orElseThrow().findAncestor(TextInput.class).orElseThrow();
    }

    /// The `field` around the required input on the field card.
    private static Element bearerField(Session session) {
        return walk(session.byId("labelled").orElseThrow())
                .filter(element -> "field".equals(element.type()))
                .filter(field -> walk(field).anyMatch(element -> "bearer".equals(element.id())))
                .findFirst()
                .orElseThrow();
    }

    private static Stream<Element> walk(Element element) {
        return Stream.concat(Stream.of(element), element.children().stream().flatMap(FormsChapterTest::walk));
    }
}
