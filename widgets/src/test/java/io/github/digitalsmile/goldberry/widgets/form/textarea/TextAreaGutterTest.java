package io.github.digitalsmile.goldberry.widgets.form.textarea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.hit.Extent;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.text.flow.TextAlign;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.Widgets;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;

/// The line-number gutter — `docs/gaps.md` G37, [ADR-0331].
///
/// The rule the whole entry is about is the last test here: the numbers count
/// **hard** lines and sit at **soft** positions, so a line that wraps takes one
/// number and more than one line's height. A column of numbers built beside the
/// control is right until the first wrap and wrong for everything below it.
class TextAreaGutterTest {

    private final TestHost host = new TestHost();

    /// The box is narrow on purpose: 140 points is wide enough for a few words
    /// and narrow enough that a sentence wraps, which is the case under test.
    private ElementTree mounted(TextArea area) {
        return mounted(area, 140);
    }

    private ElementTree mounted(TextArea area, double width) {
        var tree = new ElementTree(area, host);
        render(tree);
        box(tree).measured(new Extent((float) width, 400), new Extent((float) width, 400));
        // Twice: the first frame has no measurement to wrap against, which is the
        // bargain every measured control in this catalog makes.
        render(tree);
        render(tree);
        return tree;
    }

    private Box render(ElementTree tree) {
        tree.flush();
        return new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get())
                .render(tree);
    }

    private TextAreaBox box(ElementTree tree) {
        return (TextAreaBox) tree.root().children().getFirst().widget();
    }

    private TextAreaState state(ElementTree tree) {
        return (TextAreaState) tree.root().state().orElseThrow();
    }

    /// The box holding the gutter's numbers, or null when there is none.
    ///
    /// The **last** child of the control, because the numbers are drawn by
    /// `TextAreaBox` itself after every part and the strip: an area with no gutter
    /// ends in an underline, which carries no text.
    private Box numbers(ElementTree tree) {
        var last = render(tree).children().getLast();
        return last.text() == null ? null : last;
    }

    private static String textOf(Box box) {
        return box.text().paragraph().text();
    }

    @Test
    @DisplayName("no gutter unless it is asked for")
    void offByDefault() {
        var tree = mounted(new TextArea("one\ntwo\nthree", null));

        assertNull(numbers(tree), "nothing draws a number column");
        assertEquals(0, state(tree).gutter(), "and nothing takes width from the text");
    }

    @Test
    @DisplayName("one number per hard line")
    void oneNumberPerLine() {
        var tree = mounted(new TextArea("one\ntwo\nthree", null).gutter(true));

        assertEquals("1\n2\n3", textOf(numbers(tree)));
    }

    @Test
    @DisplayName("an empty area still has a line one")
    void anEmptyAreaIsOneLine() {
        var tree = mounted(new TextArea("", null).gutter(true));

        assertEquals("1", textOf(numbers(tree)));
    }

    @Test
    @DisplayName("an empty line in the middle keeps its number")
    void blankLinesAreNumbered() {
        var tree = mounted(new TextArea("one\n\nthree", null).gutter(true));

        assertEquals("1\n2\n3", textOf(numbers(tree)));
    }

    @Test
    @DisplayName("the numbers are right-aligned and muted rather than the text's own ink")
    void theNumbersAreStyled() {
        var tree = mounted(new TextArea("one\ntwo", null).gutter(true));
        var box = numbers(tree);

        assertEquals(TextAlign.END, box.text().flow().textAlign());
        var value = render(tree).children().stream()
                .filter(child -> child.text() != null)
                .findFirst()
                .orElseThrow();
        assertFalse(box.text().argb() == value.text().argb(), "a line number is a landmark, not text");
    }

    /// **The rule this entry exists for.** The second line is long enough to wrap
    /// into two, so the third hard line's number has to sit on the *fourth*
    /// visual line. A column of numbers built outside the control would put it on
    /// the third and every number below it would be wrong.
    @Test
    @DisplayName("a wrapped line takes one number and more than one line's height")
    void aWrappedLineIsStillOneNumber() {
        var wrapping = "aa\n" + "wrap ".repeat(12) + "\nzz";
        var tree = mounted(new TextArea(wrapping, null).gutter(true));

        var drawn = textOf(numbers(tree));
        var lines = drawn.split("\n", -1);

        assertEquals("1", lines[0]);
        assertEquals("2", lines[1]);
        assertTrue(lines.length > 3, "the long line wrapped, so there are more visual lines than numbers: " + drawn);
        // Everything between line two's number and line three's is blank — those
        // are the wraps, and a wrap has no number of its own.
        for (var i = 2; i < lines.length - 1; i++) {
            assertEquals("", lines[i], "line " + i + " of " + drawn);
        }
        assertEquals("3", lines[lines.length - 1]);
    }

    @Test
    @DisplayName("the gutter takes its width off the text, and the wrap follows")
    void theTextWrapsInWhatIsLeft() {
        var text = "one\ntwo\nthree";
        var plain = mounted(new TextArea(text, null));
        var numbered = mounted(new TextArea(text, null).gutter(true));

        assertEquals(
                0, plain.root().state().map(s -> ((TextAreaState) s).gutter()).orElseThrow());
        assertTrue(state(numbered).gutter() > 0, "the column has a width");
        assertEquals(
                state(plain).contentWidth() - state(numbered).gutter(),
                state(numbered).contentWidth(),
                0.01,
                "and the text wraps in exactly what is left");
    }

    @Test
    @DisplayName("a click lands past the gutter, not through it")
    void theHitTestAccountsForTheGutter() {
        var tree = mounted(new TextArea("abcdefgh", null).gutter(true));
        var gutter = state(tree).gutter();

        // A press at the very left of the *text* is the start of the value. Without
        // the gutter in the arithmetic this would land several characters in.
        var event = new PointerEvent(PointerEvent.Kind.PRESSED, 0, 0, PointerEvent.Button.PRIMARY, 1, null);
        event.localTo(new PointerEvent.Local((float) (gutter + 8.5), 8.5f, 140, 400));
        box(tree).onPointer(event);
        render(tree);

        assertEquals(0, box(tree).edit().caret());
    }

    @Test
    @DisplayName("markup says it, and the two forms build the same value")
    void kdlAndJavaAgree() {
        var fromKdl = Widgets.inflater().inflateAll(KdlParser.parse("""
                        text-area gutter=#true "notes"
                        """)).getFirst();

        assertEquals(new TextArea().gutter(true), ((TextArea) fromKdl).placeholder(""));
        assertTrue(((TextArea) fromKdl).gutter());
    }

    @Test
    @DisplayName("the numbers scroll with the text rather than beside it")
    void theNumbersScrollWithTheText() {
        var document = new StringBuilder();
        for (var line = 1; line <= 40; line++) {
            document.append("line ").append(line).append('\n');
        }
        var tree = mounted(new TextArea(document.toString(), null).gutter(true).rows(3, 3));

        var before = numbers(tree);
        assertNotNull(before);
        var top = before.inset().top();

        box(tree).onPointer(wheel(tree));
        render(tree);

        assertTrue(state(tree).scrolledBy() > 0, "something scrolled");
        assertEquals(
                -state(tree).scrolledBy(),
                pointsOf(numbers(tree).inset().top()),
                0.01,
                "and the numbers moved by exactly what the text did");
        assertFalse(top.equals(numbers(tree).inset().top()), "which is not where they started");
    }

    private PointerEvent wheel(ElementTree tree) {
        var event = PointerEvent.wheel(10, 10, 0, 3, null);
        event.localTo(new PointerEvent.Local(10, 10, 140, 400));
        return event;
    }

    private static double pointsOf(io.github.digitalsmile.goldberry.layout.Length length) {
        return length instanceof io.github.digitalsmile.goldberry.layout.Length.Points p ? p.value() : Double.NaN;
    }
}
