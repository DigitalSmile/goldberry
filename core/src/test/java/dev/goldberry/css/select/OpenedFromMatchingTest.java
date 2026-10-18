package dev.goldberry.css.select;

import static dev.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.StyleElement;
import dev.goldberry.css.TestElement;
import dev.goldberry.css.parse.CssParser;

/// A popup's root steps into the element that opened it for the two combinators
/// that walk up, and for nothing else.
@DisplayName("selectors reach into a popup from its opener")
class OpenedFromMatchingTest {

    private static boolean matches(String css, StyleElement element) {
        var selector =
                CssParser.parse(css + " { color: red }").getFirst().selectors().getFirst();
        return SelectorMatcher.matches(selector, element);
    }

    /// `window > aside.sidebar > button#more`, and a popover opened from the
    /// button holding a column of one item.
    private record Scene(TestElement sidebar, TestElement button, TestElement popover, TestElement item) {

        static Scene build() {
            var button = element("button#more");
            var sidebar = element("aside.sidebar").with(button);
            element("window").with(sidebar);
            var item = element("item");
            var popover = element("popover").with(element("column").with(item)).openedFrom(button);
            return new Scene(sidebar, button, popover, item);
        }
    }

    @Test
    @DisplayName("a descendant combinator walks out of the popup and up the window")
    void descendant() {
        var scene = Scene.build();

        assertTrue(matches(".sidebar item", scene.item()));
        assertTrue(matches("window popover", scene.popover()));
        assertTrue(matches(".sidebar button item", scene.item()));
        assertFalse(matches(".toolbar item", scene.item()), "and only up the chain it was opened from");
    }

    @Test
    @DisplayName("a child combinator treats the popup's root as the opener's child")
    void child() {
        var scene = Scene.build();

        assertTrue(matches("#more > popover", scene.popover()));
        assertTrue(matches(".sidebar > button > popover column > item", scene.item()));
        assertFalse(matches(".sidebar > popover", scene.popover()), "the opener is the parent, not its parent");
    }

    @Test
    @DisplayName("the popup's root is still :root, so it keeps its own theme")
    void stillRoot() {
        var scene = Scene.build();

        assertTrue(matches(":root", scene.popover()));
        assertFalse(matches(":root", scene.item()));
    }

    @Test
    @DisplayName("a :has() on the window does not see into the popup")
    void hasStaysInItsTree() {
        var scene = Scene.build();

        assertFalse(matches("aside:has(item)", scene.sidebar()));
        assertFalse(matches("button:has(> popover)", scene.button()));
    }

    @Test
    @DisplayName("siblings are the popup's own: the opener's are not its root's")
    void siblingsStayInTheirTree() {
        var scene = Scene.build();
        var before = element("button#before");
        scene.sidebar().with(before);

        assertFalse(matches("#before ~ popover", scene.popover()));
    }

    @Test
    @DisplayName("matched within its own tree, as the toolkit's base sheet is, nothing reaches in")
    void ownTree() {
        var scene = Scene.build();
        var selector = CssParser.parse(".sidebar item { color: red }")
                .getFirst()
                .selectors()
                .getFirst();

        assertFalse(SelectorMatcher.matches(selector, scene.item(), SelectorMatcher.Reach.OWN_TREE));
        assertTrue(SelectorMatcher.matches(selector, scene.item(), SelectorMatcher.Reach.THROUGH_OPENER));
        var inside = CssParser.parse("popover item { color: red }")
                .getFirst()
                .selectors()
                .getFirst();
        assertTrue(
                SelectorMatcher.matches(inside, scene.item(), SelectorMatcher.Reach.OWN_TREE),
                "and within the popup a descendant combinator works as it always did");
    }

    @Test
    @DisplayName("a state on an ancestor of the opener is a state a selector can ask about")
    void ancestorStates() {
        var scene = Scene.build();

        assertFalse(matches(".sidebar:hover item", scene.item()));
        scene.sidebar().state(Selector.PseudoClass.HOVER, true);
        assertTrue(matches(".sidebar:hover item", scene.item()));
    }
}
