package dev.goldberry.widgets.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.offscreen.Session;
import dev.goldberry.render.Cursor;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.core.Column;

/// `user-select` on the words of a page: a `text` is selected and copied under
/// `text` or `all` and not otherwise, and a `rich-text` takes the property as
/// its own `selectable` flag.
@DisplayName("user-select")
class UserSelectTest {

    private static final String WORDS = "Deploy the orc board";

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static Session open(String css, Widget... children) {
        var sheets = new ArrayList<>(Controls.stylesheets(Theme.NORD_DARK));
        sheets.add(Stylesheet.parse(CascadeLayer.APPLICATION, "#column { width: 380px; padding: 10px } " + css));
        var session = Offscreen.of(400, 160)
                .stylesheets(sheets)
                .session(new Column(List.of(children), Attributes.NONE.id("column")));
        session.frame();
        return session;
    }

    private static LogicalRect painted(Session session, String id) {
        var element = session.byId(id).orElseThrow();
        return session.regions().stream()
                .filter(region -> region.owner() == element)
                .findFirst()
                .orElseThrow()
                .painted();
    }

    /// Dragged from just inside the left edge of `id` to past its right.
    private static void dragAcross(Session session, String id) {
        var rect = painted(session, id);
        var y = (rect.top() + rect.height() / 2);
        session.drag(rect.left() + 1, y, (rect.left() + rect.width() + 40), y);
    }

    private static String selected(Session session) {
        return session.router().textSelection().map(s -> s.selectedText()).orElse("");
    }

    @Nested
    @DisplayName("on a text")
    class OnText {

        @Test
        @DisplayName("user-select: text makes a drag select the words")
        void textSelects() {
            try (var session = open("#words { user-select: text }", new Text(WORDS, Attributes.NONE.id("words")))) {
                dragAcross(session, "words");
                assertEquals(WORDS, selected(session));
            }
        }

        @Test
        @DisplayName("inherited from a container, as CSS says")
        void inherits() {
            try (var session = open("#column { user-select: text }", new Text(WORDS, Attributes.NONE.id("words")))) {
                dragAcross(session, "words");
                assertEquals(WORDS, selected(session));
            }
        }

        @Test
        @DisplayName("a plain text is not selected: auto is a label, which is pressed")
        void autoDoesNot() {
            try (var session = open("", new Text(WORDS, Attributes.NONE.id("words")))) {
                dragAcross(session, "words");
                assertEquals("", selected(session));
            }
        }

        @Test
        @DisplayName("user-select: all takes the whole text at a click")
        void all() {
            try (var session = open("#words { user-select: all }", new Text(WORDS, Attributes.NONE.id("words")))) {
                var rect = painted(session, "words");
                session.click(rect.left() + 3, (rect.top() + rect.height() / 2));
                assertEquals(WORDS, selected(session));
            }
        }

        @Test
        @DisplayName("the pointer over a selectable text is an I-beam")
        void cursor() {
            try (var session = open("#words { user-select: text }", new Text(WORDS, Attributes.NONE.id("words")))) {
                var rect = painted(session, "words");
                session.hover(rect.left() + 3, (rect.top() + rect.height() / 2));
                assertEquals(Cursor.TEXT, session.router().cursor());
            }
        }

        @Test
        @DisplayName("a button's label is the button's, even under an inherited user-select: text")
        void buttonLabel() {
            var copy = new Button("Copy").withAttributes(Attributes.NONE.id("copy"));
            try (var session =
                    open("#column { user-select: text }", new Text(WORDS, Attributes.NONE.id("words")), copy)) {
                dragAcross(session, "words");
                assertEquals(WORDS, selected(session));

                session.click("copy");
                assertEquals(WORDS, selected(session), "the press on the label did not start a selection of its own");
                var rect = painted(session, "copy");
                session.hover(rect.left() + rect.width() / 2, rect.top() + rect.height() / 2);
                assertEquals(Cursor.POINTER, session.router().cursor(), "and the pointer over it is the button's");
            }
        }

        @Test
        @DisplayName("a press elsewhere clears it, and a press a button takes does not")
        void clearing() {
            var copy = new Button("Copy").withAttributes(Attributes.NONE.id("copy"));
            try (var session =
                    open("#words { user-select: text }", new Text(WORDS, Attributes.NONE.id("words")), copy)) {
                dragAcross(session, "words");
                assertEquals(WORDS, selected(session));

                session.click("copy");
                assertEquals(WORDS, selected(session), "a toolbar's Copy acts on what is selected");

                var column = painted(session, "column");
                session.click(column.left() + 2, (column.top() + column.height() - 2));
                assertEquals("", selected(session));
            }
        }
    }

    @Nested
    @DisplayName("on a rich-text")
    class OnRichText {

        private static RichText message(boolean selectable) {
            return new RichText(List.of(new Run("Deploy "), new Run("the orc board")), Attributes.NONE.id("message"))
                    .selectable(selectable);
        }

        @Test
        @DisplayName("user-select: text makes one that was not made selectable selectable")
        void textTurnsItOn() {
            try (var session = open("#message { user-select: text }", message(false))) {
                dragAcross(session, "message");
                var state = state(session);
                assertEquals(List.of(0, WORDS.length()), state.selection());
                assertTrue(
                        session.byId("message").orElseThrow().widget() instanceof RichTextBox box && box.isFocusable());
            }
        }

        @Test
        @DisplayName("user-select: none stops one that was")
        void noneTurnsItOff() {
            try (var session = open("#message { user-select: none }", message(true))) {
                dragAcross(session, "message");
                assertEquals(List.of(-1, -1), state(session).selection());
            }
        }

        @Test
        @DisplayName("user-select: all selects the whole paragraph at a click")
        void all() {
            try (var session = open("#message { user-select: all }", message(false))) {
                var rect = painted(session, "message");
                session.click(rect.left() + 3, (rect.top() + rect.height() / 2));
                assertEquals(List.of(0, WORDS.length()), state(session).selection());
            }
        }

        @Test
        @DisplayName("it is not also selected by the window's selection")
        void notTwice() {
            try (var session = open("#message { user-select: text }", message(false))) {
                dragAcross(session, "message");
                assertFalse(session.router().textSelection().isPresent()
                        && !session.router().textSelection().get().isEmpty());
            }
        }

        private static RichTextState state(Session session) {
            var box = session.byId("message").orElseThrow();
            for (var current = box;
                    current != null;
                    current = current.parent() instanceof Element parent ? parent : null) {
                if (current.state().orElse(null) instanceof RichTextState state) {
                    return state;
                }
            }
            throw new AssertionError("no rich-text state above the box");
        }
    }
}
