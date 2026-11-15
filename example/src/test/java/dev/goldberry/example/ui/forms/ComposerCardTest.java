package dev.goldberry.example.ui.forms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Theme;
import dev.goldberry.example.ShowcaseScene;
import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Modifiers;
import dev.goldberry.input.key.PrimaryModifier;
import dev.goldberry.input.key.Shortcut;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.text.mark.MarkKind;
import dev.goldberry.text.mark.MarkedFormat;
import dev.goldberry.widget.Element;
import dev.goldberry.widgets.form.richinput.RichInput;
import dev.goldberry.widgets.text.RichText;
import dev.goldberry.widgets.text.Run;

/// The composer card: the draft opens formatted, `Enter` sends it, the sent
/// message is a `rich-text` whose runs carry the marks' classes, and a contact
/// is mentioned with the keyboard alone.
class ComposerCardTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    @Test
    @DisplayName("the draft opens with a word in each style")
    void draft() {
        var kinds = ComposerCard.DRAFT.marks().stream().map(mark -> mark.kind()).toList();
        assertTrue(kinds.contains(MarkKind.Style.BOLD));
        assertTrue(kinds.contains(MarkKind.Style.CODE));
        assertEquals("bold", ComposerCard.DRAFT.text().substring(13, 17));
        assertEquals("code()", ComposerCard.DRAFT.text().substring(27, 33));
    }

    @Test
    @DisplayName("Enter sends the draft, which comes back as rich text with the marks as classes")
    void sends() {
        try (var scene = new ShowcaseScene();
                var session = Offscreen.of(1280, 1600)
                        .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                        .session(new ComposerCard())) {
            session.frame();
            assertTrue(session.focus(ComposerCard.FIELD));
            session.key(Key.ENTER);
            session.frame();
            var sent = find(session.byId("composer-column").orElseThrow());
            var bold = sent.runs().stream()
                    .filter(run -> run.classes().contains("bold"))
                    .map(Run::text)
                    .toList();
            assertEquals(List.of("bold"), bold);
            assertEquals(
                    Set.of("link"),
                    sent.runs().stream()
                            .filter(run -> run.text().equals("link"))
                            .findFirst()
                            .orElseThrow()
                            .classes());
        }
    }

    @Test
    @DisplayName("the keyboard alone mentions somebody: @, a letter, Down, Enter, and Enter sends")
    void mentionsWithKeys() {
        try (var scene = new ShowcaseScene();
                var session = Offscreen.of(1280, 1600)
                        .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                        .session(new ComposerCard())) {
            session.frame();
            assertTrue(session.focus(ComposerCard.FIELD));
            session.key(Shortcut.primary(Key.A).toString());
            session.key(Key.BACKSPACE);
            session.type("Ask @a");
            session.frame();
            // Ann Lee and Ana Silva start with an a; Down marks the second.
            session.key(Key.DOWN);
            session.key(Key.ENTER);
            session.type("today");
            session.key(Key.ENTER);
            session.frame();
            var sent = find(session.byId("composer-column").orElseThrow());
            var mention = sent.runs().stream()
                    .filter(run -> run.classes().contains("mention"))
                    .map(Run::text)
                    .toList();
            assertEquals(List.of("@Ana Silva"), mention);
            assertEquals(
                    "Ask @Ana Silva today", sent.runs().stream().map(Run::text).reduce("", String::concat));
        }
    }

    @Test
    @DisplayName("Escape closes the list, and Enter then sends the query as typed")
    void escapeThenSend() {
        try (var scene = new ShowcaseScene();
                var session = Offscreen.of(1280, 1600)
                        .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                        .session(new ComposerCard())) {
            session.frame();
            assertTrue(session.focus(ComposerCard.FIELD));
            session.key(Key.A, Modifiers.of(PrimaryModifier.current()));
            session.key(Key.BACKSPACE);
            session.type("@an");
            session.key(Key.ESCAPE);
            session.key(Key.ENTER);
            session.frame();
            var sent = find(session.byId("composer-column").orElseThrow());
            assertEquals("@an", sent.runs().stream().map(Run::text).reduce("", String::concat));
            assertTrue(sent.runs().stream().noneMatch(run -> run.classes().contains("mention")));
        }
    }

    @Test
    @DisplayName("a draft with no formatting is read as Markdown when it is sent")
    void markdownOnSend() {
        try (var scene = new ShowcaseScene();
                var session = Offscreen.of(1280, 1600)
                        .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                        .session(new ComposerCard())) {
            session.frame();
            assertTrue(session.focus(ComposerCard.FIELD));
            session.key(Key.A, Modifiers.of(PrimaryModifier.current()));
            session.key(Key.BACKSPACE);
            session.type("ship it **today**, `x()`");
            session.key(Key.ENTER);
            session.frame();
            var sent = find(session.byId("composer-column").orElseThrow());
            assertEquals(
                    "ship it today, x()", sent.runs().stream().map(Run::text).reduce("", String::concat));
            assertEquals(
                    List.of("today"),
                    sent.runs().stream()
                            .filter(run -> run.classes().contains("bold"))
                            .map(Run::text)
                            .toList());
            assertEquals(
                    List.of("x()"),
                    sent.runs().stream()
                            .filter(run -> run.classes().contains("code"))
                            .map(Run::text)
                            .toList());
        }
    }

    @Test
    @DisplayName("the composer reads and offers HTML on the clipboard")
    void installsHtml() {
        try (var scene = new ShowcaseScene();
                var session = Offscreen.of(1280, 1600)
                        .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                        .session(new ComposerCard())) {
            session.frame();
            // A headless session has no clipboard to paste from, so what is
            // checked here is that the format is there; reading a browser's
            // HTML is MarkedHtml's to test.
            var field = walk(session.byId("composer-column").orElseThrow())
                    .map(Element::widget)
                    .filter(RichInput.class::isInstance)
                    .map(RichInput.class::cast)
                    .findFirst()
                    .orElseThrow();
            assertEquals(
                    List.of("text/html"),
                    field.clipboardFormats().stream().map(MarkedFormat::mime).toList());
        }
    }

    private static RichText find(Element from) {
        return walk(from)
                .map(Element::widget)
                .filter(RichText.class::isInstance)
                .map(RichText.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no sent message under " + from.id()));
    }

    private static Stream<Element> walk(Element element) {
        return Stream.concat(Stream.of(element), element.children().stream().flatMap(ComposerCardTest::walk));
    }
}
