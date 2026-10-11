package dev.goldberry.widgets.form.textinput;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.css.cascade.StyleResolver;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.input.hit.Extent;
import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Mod;
import dev.goldberry.input.key.Modifiers;
import dev.goldberry.layout.Length;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.WidgetRenderer;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.TestHost;
import dev.goldberry.widgets.controls.TestFont;

/// A `text-input` holding right-to-left text: the field's own paragraph takes
/// the base direction its style gives it, and the caret, the click and the
/// arrow keys follow.
class TextInputBidiTest {

    /// Hebrew, "shalom".
    private static final String SHALOM = "שלום";

    private final TestHost host = new TestHost();

    /// Mounts `input` under `css`, renders it, and gives it a size.
    private ElementTree mounted(TextInput input, @Nullable String css) {
        var tree = new ElementTree(input, host);
        render(tree, css);
        field(tree).measured(new Extent(200, 32), new Extent(200, 32));
        render(tree, css);
        return tree;
    }

    private void render(ElementTree tree, @Nullable String css) {
        tree.flush();
        var sheets = css == null
                ? List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load())
                : List.of(
                        Controls.baseStylesheet(),
                        Theme.NORD_DARK.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, css));
        new WidgetRenderer(sheets, TestFont.get()).render(tree);
    }

    private TextField field(ElementTree tree) {
        return (TextField) tree.root().children().getFirst().widget();
    }

    /// The boxes the field describes, rendered by hand with the base sheets,
    /// as `TextInputTest` renders them.
    private List<Box> parts(ElementTree tree) {
        var element = tree.root().children().getFirst();
        var style = ComputedStyle.of(
                new StyleResolver(Controls.stylesheets(Theme.NORD_DARK)).resolve(element), CssLength.Context.DEFAULT);
        var context = TestFont.context();
        var field = field(tree);
        var children = field.children().stream()
                .map(child -> ((Paints) child).render(style, List.of(), context))
                .toList();
        return field.render(style, children, context).children();
    }

    private static float points(Length length) {
        return length instanceof Length.Points p ? p.value() : Float.NaN;
    }

    private TextInputState state(ElementTree tree) {
        return (TextInputState) tree.root().state().orElseThrow();
    }

    private void key(ElementTree tree, Key key, @Nullable String css) {
        field(tree).onKey(new KeyEvent(KeyEvent.Kind.PRESSED, key, Modifiers.NONE, false, null));
        render(tree, css);
    }

    private void press(ElementTree tree, float x, @Nullable String css) {
        var event = new PointerEvent(
                PointerEvent.Kind.PRESSED,
                x,
                0,
                PointerEvent.Button.PRIMARY,
                1,
                Float.NaN,
                Float.NaN,
                Modifiers.NONE,
                null);
        event.localTo(new PointerEvent.Local(x, 0, 200, 32));
        field(tree).onPointer(event);
        render(tree, css);
    }

    @Test
    @DisplayName("under direction: rtl the field's text reads from the right, and Left reads on")
    void rightToLeftField() {
        var css = "text-input { direction: rtl }";
        var tree = mounted(new TextInput(SHALOM, null), css);
        field(tree).onFocusChanged(true, false);
        render(tree, css);

        key(tree, Key.HOME, css);
        assertEquals(0, field(tree).edit().caret());
        var atStart = state(tree).caretOffset();

        key(tree, Key.LEFT, css);
        assertEquals(1, field(tree).edit().caret(), "Left moves on through right-to-left text");
        assertTrue(state(tree).caretOffset() < atStart, "and the caret moves left on screen");

        key(tree, Key.RIGHT, css);
        assertEquals(0, field(tree).edit().caret(), "Right moves back");
    }

    @Test
    @DisplayName("a click at the right of a right-to-left field is the start of its text, at the left its end")
    void clicks() {
        var css = "text-input { direction: rtl }";
        var tree = mounted(new TextInput(SHALOM, null), css);
        field(tree).onFocusChanged(true, false);
        render(tree, css);

        press(tree, 199, css);
        assertEquals(0, field(tree).edit().caret());
        press(tree, 1, css);
        assertEquals(SHALOM.length(), field(tree).edit().caret());
    }

    @Test
    @DisplayName("Hebrew in a left-to-right field is a right-to-left run, and the keys stay logical")
    void hebrewInALeftToRightField() {
        var tree = mounted(new TextInput("ab " + SHALOM, null), null);
        field(tree).onFocusChanged(true, false);
        render(tree, null);

        key(tree, Key.END, null);
        var end = field(tree).edit().caret();
        key(tree, Key.LEFT, null);
        assertEquals(end - 1, field(tree).edit().caret(), "the base is left to right, so Left goes back");
        // The caret after the last Hebrew letter is at the run's left edge,
        // right after the space, and moves right as it goes back.
        var afterLast = state(tree).caretOffset();
        key(tree, Key.LEFT, null);
        assertTrue(state(tree).caretOffset() > afterLast, "going back through the run moves right");
    }

    @Test
    @DisplayName("a selection across a change of direction is one highlight holding a piece on each side")
    void selectionAcrossDirections() {
        var tree = mounted(new TextInput("ab " + SHALOM, null), null);
        field(tree).onFocusChanged(true, false);
        render(tree, null);
        key(tree, Key.HOME, null);
        key(tree, Key.RIGHT, null);
        for (var i = 0; i < 4; i++) {
            field(tree).onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.RIGHT, Modifiers.of(Mod.SHIFT), false, null));
            render(tree, null);
        }
        assertEquals(1, field(tree).edit().start());
        assertEquals(5, field(tree).edit().end());

        var highlight = parts(tree).getFirst();
        assertEquals(2, highlight.children().size(), "`b ` at the left, the first two letters at the right");
        var whole = points(highlight.width());
        var left = highlight.children().getFirst();
        var right = highlight.children().getLast();
        assertEquals(0, points(left.inset().left()), 0.01);
        assertEquals(whole, points(right.inset().left()) + points(right.width()), 0.01);
        assertTrue(
                points(right.inset().left()) > points(left.width()), "the letters between the two pieces are not lit");
    }

    @Test
    @DisplayName("an untouched right-to-left field shows the right end of a value too long for it")
    void showsItsHead() {
        var css = "text-input { direction: rtl }";
        var tree = mounted(new TextInput((SHALOM + " ").repeat(20), null), css);

        assertTrue(state(tree).scrolledBy() > 0, "scrolled to the end that holds the start of the text");
    }

    @Test
    @DisplayName("plaintext gives the field the direction of its first strong character")
    void plaintext() {
        var css = "text-input { unicode-bidi: plaintext }";
        var tree = mounted(new TextInput(SHALOM, null), css);
        field(tree).onFocusChanged(true, false);
        render(tree, css);

        key(tree, Key.HOME, css);
        key(tree, Key.LEFT, css);
        assertEquals(1, field(tree).edit().caret(), "read from the right, so Left reads on");
    }
}
