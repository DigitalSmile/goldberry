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
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.text.mark.MarkKind;
import dev.goldberry.widget.Element;
import dev.goldberry.widgets.text.RichText;
import dev.goldberry.widgets.text.Run;

/// The composer card: the draft opens formatted, `Enter` sends it, and the sent
/// message is a `rich-text` whose runs carry the marks' classes.
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
