package dev.goldberry.css.select;

import static dev.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.TestElement;

/// Two chains are equal exactly when no selector could tell them apart.
@DisplayName("a selector chain")
class SelectorChainTest {

    private static TestElement opener() {
        var button = element("button#more.primary");
        element("window").with(element("aside.sidebar").with(button));
        return button;
    }

    @Test
    @DisplayName("is empty for nothing, and the same each time it is read unchanged")
    void unchanged() {
        assertSame(SelectorChain.NONE, SelectorChain.of(null));

        var button = opener();
        var first = SelectorChain.of(button);
        assertEquals(3, first.links().size(), "the opener, the sidebar and the window");
        assertEquals(first, SelectorChain.of(button));
    }

    @Test
    @DisplayName("changes with a class anywhere on it")
    void aClass() {
        var button = opener();
        var before = SelectorChain.of(button);

        ((TestElement) button.parent()).addClass("collapsed");

        assertNotEquals(before, SelectorChain.of(button));
    }

    @Test
    @DisplayName("changes with a state anywhere on it")
    void aState() {
        var button = opener();
        var before = SelectorChain.of(button);

        button.state(Selector.PseudoClass.HOVER, true);

        assertNotEquals(before, SelectorChain.of(button));
    }

    @Test
    @DisplayName("changes with the opener's place among its siblings")
    void aPosition() {
        var button = opener();
        var before = SelectorChain.of(button);

        ((TestElement) button.parent()).with(element("button#after"));

        assertNotEquals(before, SelectorChain.of(button), ":last-child no longer holds");
    }

    @Test
    @DisplayName("tells two elements apart even when they look alike")
    void identity() {
        assertNotEquals(SelectorChain.of(opener()), SelectorChain.of(opener()));
    }

    @Test
    @DisplayName("goes on through a popup opened from a popup")
    void throughAPopup() {
        var button = opener();
        var row = element("item#rename");
        element("menu").with(row).openedFrom(button);

        assertEquals(5, SelectorChain.of(row).links().size(), "the row, its menu, then the button's three");
    }
}
