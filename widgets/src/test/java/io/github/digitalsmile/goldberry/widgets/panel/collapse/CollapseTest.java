package io.github.digitalsmile.goldberry.widgets.panel.collapse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.input.Handles;
import io.github.digitalsmile.goldberry.input.Key;
import io.github.digitalsmile.goldberry.input.KeyEvent;
import io.github.digitalsmile.goldberry.input.Modifiers;
import io.github.digitalsmile.goldberry.input.PointerEvent;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widgets.Widgets;
import io.github.digitalsmile.goldberry.widgets.panel.Described;
import io.github.digitalsmile.goldberry.widgets.text.Text;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `collapse` — §5's disclosure ([ADR-0164]).
///
/// **The claim is an absence.** §5 asks for a body that is *unmounted* while
/// closed rather than hidden, so the assertion that matters is not "the body has
/// zero height" but "there is no body, and the author's own widgets were never
/// built either" — which is what keeps a shut section's subscriptions, images and
/// scroll positions from staying alive behind a header nobody has opened
/// (ADR-0004).
class CollapseTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static void click(ElementTree tree) {
        ((Handles) Described.elementOf(tree, CollapseHeader.class).widget())
                .onPointer(new PointerEvent(PointerEvent.Kind.CLICKED, 0, 0,
                        PointerEvent.Button.PRIMARY, 1, null));
        tree.flush();
    }

    private static void key(ElementTree tree, Key key) {
        ((Handles) Described.elementOf(tree, CollapseHeader.class).widget())
                .onKey(new KeyEvent(KeyEvent.Kind.PRESSED, key, Modifiers.NONE, false, null));
        tree.flush();
    }

    private static long bodies(ElementTree tree) {
        return Described.counting(tree, "collapse-body");
    }

    private static boolean built(ElementTree tree, String text) {
        return Described.in(tree).stream()
                .anyMatch(w -> w instanceof Text it && text.equals(it.content()));
    }

    /// **The claim.** Not a hidden body, not one of zero height: no body.
    @Test
    @DisplayName("a closed section has no body, rather than a hidden one")
    void closedHasNoBody() {
        var tree = new ElementTree(new Collapse("Advanced", new Text("Secret")));

        assertEquals(0, bodies(tree));
        assertTrue(!built(tree, "Secret"),
                "the author's own widgets must not be built either");
    }

    @Test
    @DisplayName("an open section has one")
    void openHasABody() {
        var tree = new ElementTree(new Collapse("Advanced", true, null, new Text("Shown")));

        assertEquals(1, bodies(tree));
        assertTrue(built(tree, "Shown"));
    }

    /// §5's "`open` is retained state": the section remembers, and the
    /// application never hears about it.
    @Test
    @DisplayName("an uncontrolled section opens itself and stays open")
    void uncontrolled() {
        var tree = new ElementTree(new Collapse("Advanced", new Text("Body")));
        assertEquals(0, bodies(tree));

        click(tree);
        assertEquals(1, bodies(tree));

        click(tree);
        assertEquals(0, bodies(tree));
    }

    /// The other arrangement, and the one every value in this catalog has: a
    /// section whose `onToggle` does nothing stays shut, which is the behaviour
    /// and not a bug.
    @Test
    @DisplayName("a controlled section asks and does not decide")
    void controlled() {
        var asked = new AtomicReference<Boolean>();
        var tree = new ElementTree(
                new Collapse("Advanced", false, asked::set, new Text("Body")));

        click(tree);

        assertEquals(Boolean.TRUE, asked.get(), "it asked to be opened");
        assertEquals(0, bodies(tree), "and stayed shut, because nobody answered");
    }

    /// §5: "`Left`/`Right` close and open." Absolute rather than a toggle, so
    /// holding `Right` down a list of sections opens all of them instead of
    /// flapping the one under the cursor.
    @Test
    @DisplayName("Right opens and does not close; Left closes and does not open")
    void arrows() {
        var tree = new ElementTree(new Collapse("Advanced", new Text("Body")));

        key(tree, Key.RIGHT);
        assertEquals(1, bodies(tree));

        key(tree, Key.RIGHT);
        assertEquals(1, bodies(tree), "Right on an open section leaves it open");

        key(tree, Key.LEFT);
        assertEquals(0, bodies(tree));

        key(tree, Key.LEFT);
        assertEquals(0, bodies(tree), "and Left on a closed one leaves it closed");
    }

    @Test
    @DisplayName("Enter and Space toggle")
    void enterAndSpace() {
        var tree = new ElementTree(new Collapse("Advanced", new Text("Body")));

        key(tree, Key.ENTER);
        assertEquals(1, bodies(tree));

        key(tree, Key.SPACE);
        assertEquals(0, bodies(tree));
    }

    /// One tab stop — §5's "the header is one Tab stop". A body full of controls
    /// has its own, but nothing in the section's chrome does.
    @Test
    @DisplayName("the header is the only focusable part of the chrome")
    void oneTabStop() {
        var tree = new ElementTree(new Collapse("Advanced", true, null, new Text("Body")));

        var focusable = Described.in(tree).stream()
                .filter(w -> w instanceof Handles h && h.isFocusable())
                .toList();
        assertEquals(1, focusable.size(), "expected only the header, got " + focusable);
    }

    /// The chevron has to be a node the cascade reaches, or the rotation §5 asks
    /// for could never be written: a transform is resolved for an element, and a
    /// mark drawn inline by the header would have none.
    @Test
    @DisplayName("the chevron is a node, and says whether the section is open")
    void chevronIsANode() {
        var shut = new ElementTree(new Collapse("Advanced", new Text("Body")));
        var open = new ElementTree(new Collapse("Advanced", true, null, new Text("Body")));

        assertTrue(!Described.first(shut, CollapseHeader.CollapseChevron.class)
                .classes().contains("open"));
        assertTrue(Described.first(open, CollapseHeader.CollapseChevron.class)
                .classes().contains("open"));
    }

    @Test
    @DisplayName("a collapse inflates from markup")
    void inflates() {
        var widget = Widgets.inflater().inflate(KdlParser.parse(
                "collapse title=\"Advanced\" open=#true { text \"Body\" }").getFirst());
        var it = assertInstanceOf(Collapse.class, widget);

        assertEquals("Advanced", it.title());
        assertTrue(it.open());
        assertEquals(1, it.children().size());
    }
}
