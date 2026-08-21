package io.github.digitalsmile.goldberry.widgets.panel.accordion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.input.Handles;
import io.github.digitalsmile.goldberry.input.PointerEvent;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widgets.Widgets;
import io.github.digitalsmile.goldberry.widgets.panel.Described;
import io.github.digitalsmile.goldberry.widgets.panel.collapse.Collapse;
import io.github.digitalsmile.goldberry.widgets.text.Text;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `column accordion=#true` — §5's "one section open at a time" ([ADR-0166]).
///
/// The claim is about **siblings**, which is why it cannot live on a `collapse`:
/// a section knows nothing about the others. So the assertions here are all of
/// the form "opening this one closed that one", and the one that matters most is
/// that a section the *application* already controls is left alone — two things
/// deciding one boolean is a bug, and the application asked first.
class AccordionTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static Accordion of(int open) {
        return new Accordion(open, null, List.of(
                new Collapse("One", new Text("body one")),
                new Collapse("Two", new Text("body two")),
                new Collapse("Three", new Text("body three"))),
                io.github.digitalsmile.goldberry.widget.Attributes.NONE);
    }

    /// The headers, in order — what a click has to be aimed at.
    private static List<Element> headers(ElementTree tree) {
        var found = new java.util.ArrayList<Element>();
        collect(tree.root(), found);
        return List.copyOf(found);
    }

    private static void collect(Element element, List<Element> into) {
        if (element.widget() instanceof io.github.digitalsmile.goldberry.widget.Styled styled
                && "collapse-header".equals(styled.cssType())) {
            into.add(element);
        }
        for (var child : element.children()) {
            collect(child, into);
        }
    }

    private static void click(ElementTree tree, int section) {
        ((Handles) headers(tree).get(section).widget()).onPointer(
                new PointerEvent(PointerEvent.Kind.CLICKED, 0, 0,
                        PointerEvent.Button.PRIMARY, 1, null));
        tree.flush();
    }

    private static long bodies(ElementTree tree) {
        return Described.counting(tree, "collapse-body");
    }

    private static boolean showing(ElementTree tree, String text) {
        return Described.in(tree).stream()
                .anyMatch(w -> w instanceof Text it && text.equals(it.content()));
    }

    /// **The claim.** Not "the second opened" — "the second opened *and the first
    /// closed*", which is the half a `collapse` cannot do for itself.
    @Test
    @DisplayName("opening a section closes the one that was open")
    void oneAtATime() {
        var tree = new ElementTree(of(Accordion.NONE));

        click(tree, 0);
        assertEquals(1, bodies(tree));
        assertTrue(showing(tree, "body one"));

        click(tree, 1);
        assertEquals(1, bodies(tree), "still exactly one, not two");
        assertTrue(showing(tree, "body two"));
        assertFalse(showing(tree, "body one"), "and the first one is gone");
    }

    /// Falls out of holding **one number** rather than a boolean per section:
    /// there is no second piece of state that could disagree with the first.
    @Test
    @DisplayName("never more than one is open, whichever order they are clicked")
    void neverTwo() {
        var tree = new ElementTree(of(Accordion.NONE));

        for (var section : List.of(2, 0, 1, 2, 1)) {
            click(tree, section);
            assertTrue(bodies(tree) <= 1, "two sections open after clicking " + section);
        }
    }

    /// Clicking the open one shuts it, leaving none — an accordion is not a
    /// radio group, and "all closed" is a legal state.
    @Test
    @DisplayName("clicking the open section closes it, leaving none open")
    void closingTheOpenOne() {
        var tree = new ElementTree(of(0));
        assertEquals(1, bodies(tree));

        click(tree, 0);

        assertEquals(0, bodies(tree));
    }

    @Test
    @DisplayName("a section can start open")
    void startsOpen() {
        var tree = new ElementTree(of(1));

        assertTrue(showing(tree, "body two"));
        assertFalse(showing(tree, "body one"));
    }

    /// Not everything in an accordion is a section.
    @Test
    @DisplayName("anything that is not a collapse passes through untouched")
    void nonSections() {
        var heading = new Text("Settings");
        var tree = new ElementTree(new Accordion(Accordion.NONE, null,
                List.of(heading, new Collapse("One", new Text("body one"))),
                io.github.digitalsmile.goldberry.widget.Attributes.NONE));

        assertTrue(showing(tree, "Settings"));
    }

    /// Two things deciding one boolean is a bug, and the application asked first.
    @Test
    @DisplayName("a section the application already controls is left alone")
    void applicationWins() {
        var asked = new java.util.concurrent.atomic.AtomicReference<Boolean>();
        var tree = new ElementTree(new Accordion(Accordion.NONE, null,
                List.of(
                        new Collapse("Controlled", false, asked::set, new Text("body")),
                        new Collapse("Ordinary", new Text("other"))),
                io.github.digitalsmile.goldberry.widget.Attributes.NONE));

        click(tree, 0);

        assertEquals(Boolean.TRUE, asked.get(), "its own handler still hears it");
        assertEquals(0, bodies(tree), "and the accordion did not open it behind the app's back");
    }

    /// An accordion *is* a column — the flag says how its children behave, not
    /// what it is — so a rule written for `column` still applies to it.
    @Test
    @DisplayName("it reports itself as a column, with an accordion class")
    void looksLikeAColumn() {
        var node = Described.first(new ElementTree(of(0)), AccordionColumn.class);

        assertEquals("column", node.cssType());
        assertTrue(node.classes().contains("accordion"));
    }

    /// §5 puts the flag on `column`, and a document must be able to write that.
    @Test
    @DisplayName("`column accordion=#true` inflates to one")
    void inflates() {
        var widget = Widgets.inflater().inflate(KdlParser.parse("""
                column accordion=#true {
                    collapse title="One" { text "body one" }
                    collapse title="Two" { text "body two" }
                }
                """).getFirst());

        assertInstanceOf(Accordion.class, widget);
    }

    /// And an ordinary column must stay an ordinary column, paying nothing.
    @Test
    @DisplayName("a plain column is still a plain column")
    void plainColumn() {
        var widget = Widgets.inflater().inflate(
                KdlParser.parse("column { text \"one\" }").getFirst());

        assertInstanceOf(io.github.digitalsmile.goldberry.widgets.core.Column.class, widget);
    }
}
