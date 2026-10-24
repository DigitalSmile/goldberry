package dev.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.StyleElement;
import dev.goldberry.css.TestElement;

/// [OpenedFrom]'s scope on its own: who is the opener while a popup opens, with
/// no window and no popup.
@DisplayName("the opener a popup is opened from")
class OpenedFromTest {

    private final TestElement field = TestElement.element("field");
    private final TestElement toolbar = TestElement.element("toolbar");

    @Test
    @DisplayName("is nobody outside an opening")
    void nobodyOutside() {
        assertEquals(Optional.empty(), OpenedFrom.current());
    }

    @Test
    @DisplayName("is the element named, for the opening and not after it")
    void namedForTheOpening() {
        var seen = OpenedFrom.unlessNamed(field, OpenedFrom::current);

        assertEquals(Optional.of(field), seen);
        assertEquals(Optional.empty(), OpenedFrom.current());
    }

    @Test
    @DisplayName("is nobody when the toolkit names no element")
    void noElement() {
        var seen = new AtomicReference<Optional<StyleElement>>();

        OpenedFrom.runUnlessNamed(null, () -> seen.set(OpenedFrom.current()));

        assertEquals(Optional.empty(), seen.get());
    }

    @Test
    @DisplayName("is the outer caller's choice over the toolkit's own, with or without an answer")
    void callerWins() {
        var answered = new AtomicReference<Optional<StyleElement>>();
        var ran = new AtomicReference<Optional<StyleElement>>();

        OpenedFrom.unlessNamed(toolbar, () -> {
            answered.set(OpenedFrom.unlessNamed(field, OpenedFrom::current));
            OpenedFrom.runUnlessNamed(field, () -> ran.set(OpenedFrom.current()));
            return true;
        });

        assertSame(toolbar, answered.get().orElseThrow());
        assertSame(toolbar, ran.get().orElseThrow());
    }

    @Test
    @DisplayName("is the element named for an opening that answers nothing")
    void runNamesTheElement() {
        var ran = new boolean[1];
        var seen = new AtomicReference<Optional<StyleElement>>();

        OpenedFrom.runUnlessNamed(field, () -> {
            ran[0] = true;
            seen.set(OpenedFrom.current());
        });

        assertTrue(ran[0]);
        assertEquals(Optional.of(field), seen.get());
        assertEquals(Optional.empty(), OpenedFrom.current());
    }
}
