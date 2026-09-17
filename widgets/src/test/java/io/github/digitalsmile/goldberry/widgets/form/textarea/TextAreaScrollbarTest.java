package io.github.digitalsmile.goldberry.widgets.form.textarea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.hit.Extent;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.input.key.Mod;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollBar;

/// §4's "scrollbar beyond" the maximum rows ([ADR-0362]).
class TextAreaScrollbarTest {

    private final TestHost host = new TestHost();

    private static String lines(int count) {
        var text = new StringBuilder();
        for (var line = 1; line <= count; line++) {
            text.append("line ").append(line);
            if (line < count) {
                text.append('\n');
            }
        }
        return text.toString();
    }

    private ElementTree mounted(TextArea area) {
        var tree = new ElementTree(area, host);
        render(tree);
        box(tree).measured(new Extent(240, 400), new Extent(240, 400));
        render(tree);
        render(tree);
        return tree;
    }

    private void render(ElementTree tree) {
        tree.flush();
        new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get()).render(tree);
    }

    private static TextAreaBox box(ElementTree tree) {
        return (TextAreaBox) tree.root().children().getFirst().widget();
    }

    private static TextAreaState state(ElementTree tree) {
        return (TextAreaState) tree.root().state().orElseThrow();
    }

    private static ScrollBar bar(ElementTree tree) {
        for (var child : tree.root().children().getFirst().children()) {
            if (child.widget() instanceof ScrollBar found) {
                return found;
            }
        }
        return null;
    }

    private static Element barElement(ElementTree tree) {
        return tree.root().children().getFirst().children().getLast();
    }

    @Test
    @DisplayName("a text that fits has no scrollbar")
    void fits() {
        var tree = mounted(new TextArea(lines(2), null).rows(3, 5));

        assertNull(bar(tree));
    }

    @Test
    @DisplayName("past the maximum rows there is one, sized to the proportion on screen")
    void overflows() {
        var tree = mounted(new TextArea(lines(40), null).rows(3, 4));
        var bar = bar(tree);

        assertNotNull(bar);
        assertTrue(bar.vertical());
        assertEquals(10.0, bar.content() / bar.viewport(), 0.001, "forty lines, four on screen");
        assertEquals(0, bar.offset(), 0.001);
        assertEquals("scrollbar", barElement(tree).type());
    }

    @Test
    @DisplayName("the wheel moves the thumb with the text")
    void wheelMovesTheThumb() {
        var tree = mounted(new TextArea(lines(40), null).rows(3, 4));

        var event = PointerEvent.wheel(10, 10, 0, 1, null);
        event.localTo(new PointerEvent.Local(10, 10, 240, 100));
        box(tree).onPointer(event);
        render(tree);

        assertTrue(state(tree).scrolledBy() > 0);
        assertEquals(state(tree).scrolledBy(), bar(tree).offset(), 0.001);
    }

    @Test
    @DisplayName("dragging the thumb scrolls the text, clamped to what there is")
    void dragScrollsTheText() {
        var tree = mounted(new TextArea(lines(40), null).rows(3, 4));
        var bar = bar(tree);

        bar.onScroll().accept(bar.content() / 2);
        render(tree);
        assertEquals(bar.content() / 2, state(tree).scrolledBy(), 0.001);

        bar(tree).onScroll().accept(1e9);
        render(tree);
        assertEquals(bar.content() - bar.viewport(), state(tree).scrolledBy(), 0.001);
        assertEquals(state(tree).scrolledBy(), bar(tree).offset(), 0.001);
    }

    @Test
    @DisplayName("a caret that scrolls the text moves the thumb too, a rebuild later")
    void caretChaseMovesTheThumb() {
        var tree = mounted(new TextArea(lines(40), null).rows(3, 4));
        box(tree).onFocusChanged(true, true);
        render(tree);

        box(tree).onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.END, Modifiers.of(Mod.CTRL), false, null));
        render(tree);
        render(tree);

        assertTrue(state(tree).scrolledBy() > 0, "the caret at the end scrolled the text");
        assertEquals(state(tree).scrolledBy(), bar(tree).offset(), 0.001, "and the thumb followed it");
    }
}
