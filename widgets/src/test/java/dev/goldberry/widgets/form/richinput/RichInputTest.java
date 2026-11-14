package dev.goldberry.widgets.form.richinput;

import static dev.goldberry.text.mark.MarkKind.Style.BOLD;
import static dev.goldberry.text.mark.MarkKind.Style.ITALIC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.bind.Property;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.input.key.Key;
import dev.goldberry.kdl.KdlParser;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.offscreen.Session;
import dev.goldberry.text.mark.Mark;
import dev.goldberry.text.mark.MarkedText;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.semantics.Role;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.Widgets;
import dev.goldberry.widgets.core.Column;

/// `rich-input` in a window: typing, formatting keys, undo, growing, the
/// stylesheet's looks and markup.
@DisplayName("rich-input")
class RichInputTest {

    private final List<MarkedText> changes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private Session open(RichInput input) {
        var sheets = new ArrayList<>(Controls.stylesheets(Theme.NORD_DARK));
        sheets.add(Stylesheet.parse(CascadeLayer.APPLICATION, "#column { width: 320px; padding: 10px }"));
        var session = Offscreen.of(360, 320)
                .stylesheets(sheets)
                .session(new Column(
                        List.of(input.withAttributes(Attributes.NONE.id("composer"))), Attributes.NONE.id("column")));
        session.frame();
        session.frame();
        assertTrue(session.focus("composer"), "the field takes the focus");
        session.frame();
        return session;
    }

    private RichInput composer() {
        return new RichInput(MarkedText.EMPTY, changes::add).placeholder("Message");
    }

    private static RichInputState state(Session session) {
        var found = find(session.byId("column").orElseThrow());
        if (found == null) {
            throw new AssertionError("no rich-input state under the column");
        }
        return found;
    }

    private static @Nullable RichInputState find(Element from) {
        if (from.state().orElse(null) instanceof RichInputState state) {
            return state;
        }
        for (var child : from.children()) {
            var found = find(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static float height(Session session) {
        var box = session.byId("composer").orElseThrow();
        return session.regions().stream()
                .filter(region -> region.owner() == box)
                .findFirst()
                .orElseThrow()
                .height();
    }

    @Nested
    @DisplayName("typing")
    class Typing {

        @Test
        @DisplayName("typed text is reported as a marked value")
        void typed() {
            try (var session = open(composer())) {
                session.type("Ship it");
                session.frame();
                assertEquals(MarkedText.of("Ship it"), changes.getLast());
            }
        }

        @Test
        @DisplayName("Ctrl+B over a selection makes it bold, and Ctrl+Z takes it back")
        void boldAndUndo() {
            try (var session = open(composer())) {
                session.type("Ship it");
                session.key("Ctrl+A");
                session.key("Ctrl+B");
                session.frame();
                assertEquals(List.of(new Mark(0, 7, BOLD)), changes.getLast().marks());
                session.key("Ctrl+Z");
                session.frame();
                assertTrue(changes.getLast().isPlain());
            }
        }

        @Test
        @DisplayName("Ctrl+I with nothing selected styles what is typed next")
        void italicForTyping() {
            try (var session = open(composer())) {
                session.type("say ");
                session.key("Ctrl+I");
                session.type("so");
                session.frame();
                assertEquals(List.of(new Mark(4, 6, ITALIC)), changes.getLast().marks());
            }
        }

        @Test
        @DisplayName("a mark set in the text gets a mark node the stylesheet styles")
        void markNodes() {
            try (var session = open(composer())) {
                session.type("Ship it");
                session.key("Ctrl+A");
                session.key("Ctrl+B");
                session.frame();
                var box = session.byId("composer").orElseThrow();
                assertTrue(box.children().stream()
                        .anyMatch(child ->
                                child.widget() instanceof MarkNode(var classes) && classes.equals(Set.of("bold"))));
                var editor = state(session).editor().orElseThrow();
                var plain = editor.document().paragraphOf(0);
                assertTrue(plain.widthBetween(0, 7) > 0);
            }
        }
    }

    @Nested
    @DisplayName("size")
    class Size {

        @Test
        @DisplayName("grows a line at a time, and stops at max-rows")
        void grows() {
            try (var session = open(composer().maxRows(3))) {
                var one = height(session);
                session.type("a");
                session.key(Key.ENTER);
                session.type("b");
                session.frame();
                var two = height(session);
                assertTrue(two > one, "two lines are taller than one: " + one + " → " + two);
                for (var i = 0; i < 4; i++) {
                    session.key(Key.ENTER);
                    session.type("c");
                }
                session.frame();
                var capped = height(session);
                assertEquals(one + 2 * (two - one), capped, 0.5, "three lines and no more");
            }
        }

        @Test
        @DisplayName("wraps at its width")
        void wraps() {
            try (var session = open(composer())) {
                var one = height(session);
                session.type("a sentence long enough to need a second line in a field three hundred wide");
                session.frame();
                session.frame();
                assertTrue(height(session) > one);
            }
        }
    }

    @Nested
    @DisplayName("binding and markup")
    class Binding {

        @Test
        @DisplayName("follows a bound value, and a change from the field is not loaded back")
        void follows() {
            var model = Property.<MarkedText>of(MarkedText.of("draft"));
            try (var session = open(RichInput.of(model, model::set))) {
                assertEquals("draft", state(session).value().text());
                session.type("!");
                session.frame();
                assertEquals("draft!", model.get().text());
                model.set(MarkedText.of("sent").mark(0, 4, BOLD));
                session.frame();
                assertEquals(
                        List.of(new Mark(0, 4, BOLD)), state(session).value().marks());
            }
        }

        @Test
        @DisplayName("a read-only field takes no typing and no formatting")
        void readOnly() {
            try (var session = open(new RichInput(MarkedText.of("fixed"), changes::add).readOnly(true))) {
                session.type("x");
                session.key("Ctrl+A");
                session.key("Ctrl+B");
                session.frame();
                assertTrue(changes.isEmpty());
                assertEquals(MarkedText.of("fixed"), state(session).value());
            }
        }

        @Test
        @DisplayName("is a text field to a screen reader, named by its placeholder")
        void semantics() {
            try (var session = open(composer())) {
                assertTrue(session.byRole(Role.TEXT_FIELD, "Message").isPresent());
            }
        }

        @Test
        @DisplayName("inflates from markup")
        void markup() {
            var widgets = Widgets.inflater()
                    .inflateAll(
                            KdlParser.parse("rich-input placeholder=\"Message\" rows=2 max-rows=6 read-only=#true"));
            var input = (RichInput) widgets.getFirst();
            assertEquals("Message", input.placeholder());
            assertEquals(2, input.rows());
            assertEquals(6, input.maxRows());
            assertTrue(input.readOnly());
            assertFalse(input.disabled());
        }
    }
}
