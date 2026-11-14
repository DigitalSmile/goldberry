package dev.goldberry.widgets.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Mod;
import dev.goldberry.input.key.Modifiers;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.offscreen.Session;
import dev.goldberry.render.Cursor;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.text.Paragraph;
import dev.goldberry.text.edit.TextGeometry;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.core.Column;

/// A `rich-text` that does something: runs that are pressed, a spoiler that is
/// revealed, and words that are selected.
///
/// Driven through a session, so the pointer is routed as a window routes it,
/// and aimed through the paragraph the state laid out: a point is where a
/// character was drawn.
@DisplayName("rich-text input")
class RichTextInputTest {

    private static final String HELLO = "Hello ";
    private static final String MENTION = "@frodo";
    private static final String AND = " and ";
    private static final String SECRET = "secret";
    private static final String END = ".";

    private final AtomicInteger pressed = new AtomicInteger();

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private RichText message(boolean selectable) {
        return new RichText(
                        List.of(
                                new Run(HELLO),
                                new Run(MENTION).onPress(pressed::incrementAndGet),
                                new Run(AND),
                                new Run(SECRET).spoiler(true),
                                new Run(END)),
                        Attributes.NONE.id("message"))
                .selectable(selectable);
    }

    private Session open(boolean selectable) {
        var sheets = new ArrayList<>(Controls.stylesheets(Theme.NORD_DARK));
        sheets.add(Stylesheet.parse(CascadeLayer.APPLICATION, "#column { width: 380px; padding: 10px }"));
        var session = Offscreen.of(400, 120)
                .stylesheets(sheets)
                .session(new Column(List.of(message(selectable)), Attributes.NONE.id("column")));
        session.frame();
        return session;
    }

    /// The `rich-text` node.
    private static Element box(Session session) {
        return session.byId("message").orElseThrow();
    }

    private static RichTextState state(Session session) {
        var column = session.byId("column").orElseThrow();
        return find(column);
    }

    private static RichTextState find(Element from) {
        if (from.state().orElse(null) instanceof RichTextState state) {
            return state;
        }
        for (var child : from.children()) {
            var found = findOrNull(child);
            if (found != null) {
                return found;
            }
        }
        throw new AssertionError("no rich-text under " + from);
    }

    private static @Nullable RichTextState findOrNull(Element from) {
        if (from.state().orElse(null) instanceof RichTextState state) {
            return state;
        }
        for (var child : from.children()) {
            var found = findOrNull(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /// Where the box was painted.
    private static LogicalRect painted(Session session) {
        var element = box(session);
        return session.regions().stream()
                .filter(region -> region.owner() == element)
                .findFirst()
                .orElseThrow()
                .painted();
    }

    /// A point in the middle of the character at `offset`, in the window.
    private static float[] at(Session session, int offset) {
        var paragraph = paragraphOf(session);
        var layout = paragraph.layout(380);
        var left = TextGeometry.caretAt(paragraph, layout, offset);
        var right = TextGeometry.caretAt(paragraph, layout, offset + 1);
        var rect = painted(session);
        return new float[] {
            (float) (rect.left() + (left.x() + right.x()) / 2), (float) (rect.top() + left.top() + left.height() / 2)
        };
    }

    private static Paragraph paragraphOf(Session session) {
        var paragraph = state(session).paragraph();
        assertTrue(paragraph != null, "a frame has laid the paragraph out");
        return paragraph;
    }

    private static int mention() {
        return HELLO.length() + 2;
    }

    private static int secret() {
        return HELLO.length() + MENTION.length() + AND.length() + 2;
    }

    private static Element run(Session session, int index) {
        return box(session).children().get(index);
    }

    @Nested
    @DisplayName("a pressable run")
    class Pressing {

        @Test
        @DisplayName("is pressed where its words are, and nowhere else")
        void pressed() {
            try (var session = open(false)) {
                var on = at(session, mention());
                session.click(on[0], on[1]);
                assertEquals(1, RichTextInputTest.this.pressed.get(), "a press on the mention");

                var off = at(session, 1);
                session.click(off[0], off[1]);
                assertEquals(1, RichTextInputTest.this.pressed.get(), "and none on the words before it");
            }
        }

        @Test
        @DisplayName("carries pressable, and the pointer over it is the run's cursor")
        void cursor() {
            try (var session = open(false)) {
                assertTrue(run(session, 1).classes().contains(Run.PRESSABLE));
                var on = at(session, mention());
                session.hover(on[0], on[1]);
                session.frame();
                var widget = (RichTextBox) box(session).widget();
                assertEquals(1, widget.hovered(), "the run under the pointer");
                assertEquals(Cursor.POINTER, cursorOf(session));

                var off = at(session, 1);
                session.hover(off[0], off[1]);
                session.frame();
                assertEquals(0, ((RichTextBox) box(session).widget()).hovered());
            }
        }

        private static Cursor cursorOf(Session session) {
            var element = box(session);
            var found = new ArrayList<Cursor>();
            session.regions().stream()
                    .filter(region -> region.owner() == element)
                    .forEach(region -> found.add(region.cursor()));
            return found.getFirst();
        }
    }

    @Nested
    @DisplayName("a spoiler")
    class Spoiler {

        @Test
        @DisplayName("is covered until pressed, and the press only reveals it")
        void reveals() {
            try (var session = open(false)) {
                assertTrue(run(session, 3).classes().contains(Run.SPOILER));
                assertFalse(run(session, 3).classes().contains(Run.REVEALED));

                // The top row of the line box, which no glyph reaches: the cover
                // fills it, and once revealed the column's background shows.
                var on = at(session, secret());
                var top = painted(session).top() + 1;
                var covered = session.frame().argb((int) on[0], (int) top);
                session.click(on[0], on[1]);
                var shown = session.frame().argb((int) on[0], (int) top);
                assertTrue(
                        covered != shown,
                        () -> "covered " + Integer.toHexString(covered) + ", shown " + Integer.toHexString(shown));

                assertEquals(Set.of(Run.SPOILER, Run.REVEALED), run(session, 3).classes());
                assertEquals(0, RichTextInputTest.this.pressed.get());
            }
        }
    }

    @Nested
    @DisplayName("selection")
    class Selection {

        @Test
        @DisplayName("is dragged across, and Ctrl+A takes it all")
        void drags() {
            try (var session = open(true)) {
                var from = at(session, 0);
                var to = at(session, HELLO.length() + 1);
                session.drag(from[0] - 2, from[1], to[0], to[1]);
                session.frame();

                var selection = state(session).selection();
                assertEquals(0, selection.get(0));
                assertTrue(selection.get(1) >= HELLO.length(), () -> "through the first word: " + selection);
                assertEquals(0, RichTextInputTest.this.pressed.get(), "a drag is not a press");

                session.key(Key.A, Modifiers.of(Mod.CTRL));
                session.frame();
                var whole = HELLO + MENTION + AND + SECRET + END;
                assertEquals(whole, state(session).selectedText());
            }
        }

        @Test
        @DisplayName("is not made on a paragraph that is not selectable")
        void notSelectable() {
            try (var session = open(false)) {
                var from = at(session, 0);
                var to = at(session, HELLO.length() + 1);
                session.drag(from[0], from[1], to[0], to[1]);
                assertEquals(List.of(-1, -1), state(session).selection());
                assertEquals("", state(session).selectedText());
            }
        }
    }
}
