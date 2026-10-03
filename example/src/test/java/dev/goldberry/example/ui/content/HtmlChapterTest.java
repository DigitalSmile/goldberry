package dev.goldberry.example.ui.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.bind.runtime.Models;
import dev.goldberry.html.model.Element;
import dev.goldberry.html.view.HtmlView;
import dev.goldberry.input.event.TextEvent;
import dev.goldberry.input.handler.Handles;
import dev.goldberry.widgets.controls.button.Button;

/// That the HTML screen is **live**, and that its links reach the application.
///
/// [MarkdownChapterTest]'s twin. The second half is what this screen has and the
/// other does not: an anchor a reader can press, whose `href` arrives at a valued
/// action the document named and no Java in this application wired.
class HtmlChapterTest {

    private ContentFixture screen;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        screen = new ContentFixture(HtmlChapter::new);
    }

    @AfterEach
    void tearDown() {
        if (screen != null) {
            screen.close();
        }
    }

    /// The editor's box, which is the node that hears the keyboard.
    private Handles editor() {
        return screen.all(Handles.class).stream()
                .filter(handles -> handles.getClass().getSimpleName().equals("TextAreaBox"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the HTML screen has no editor"));
    }

    /// Every link in the rendered page: a `button.link`, which is what an anchor
    /// becomes.
    private List<Button> links() {
        return screen.all(Button.class).stream()
                .filter(button -> button.attributes().classes().contains("html-a"))
                .toList();
    }

    @Test
    @DisplayName("types on the left and the page on the right is the next frame")
    void typingChangesThePreview() {
        var before = screen.first(HtmlView.class).resolved();

        editor().onText(new TextEvent("\n<h2>Typed just now</h2>\n", null));
        screen.frame();

        var after = screen.first(HtmlView.class).resolved();
        assertNotEquals(before, after, "the preview should not be showing the page it was showing");
        var source =
                String.valueOf(Models.observable(screen.model, "html.source").get());
        assertTrue(source.endsWith("<h2>Typed just now</h2>\n"), "the model holds what was typed, at the caret");
        assertEquals(
                List.of("Typed just now"),
                after.find("h2").stream()
                        .map(Element::text)
                        .filter(text -> text.equals("Typed just now"))
                        .toList(),
                "the preview parses what was typed");
    }

    @Test
    @DisplayName("reads and writes one property, which is what makes it live")
    void oneProperty() {
        var preview = screen.first(HtmlView.class);
        assertNotNull(preview.binding(), "a preview that follows nothing is a screenshot");
        assertSame(Models.observable(screen.model, "html.source"), preview.binding());
    }

    @Test
    @DisplayName("an anchor in the page is a button a reader can press")
    void anchorsAreButtons() {
        var links = links();
        assertFalse(links.isEmpty(), "the sample page has anchors, and each should be a button.link");
        assertTrue(links.getFirst().attributes().classes().contains("link"), "an anchor reads as a link");
    }

    @Test
    @DisplayName("and pressing it hands the href to the action the document named")
    void pressingALinkReachesTheModel() {
        var link = links().getFirst();
        assertNotNull(link.onPress(), "link= in html.kdl is what wires this, and nothing in Java does");

        link.onPress().run();
        screen.frame();

        var followed =
                String.valueOf(Models.observable(screen.model, "html.followed").get());
        assertTrue(
                followed.startsWith("Followed: http"),
                () -> "the application should have been handed the href, and was handed: " + followed);
    }
}
