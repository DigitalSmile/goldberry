package dev.goldberry.css;

import static dev.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.css.cascade.StyleResolver;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.css.value.Transform;
import dev.goldberry.css.value.ViewTransition;

/// `view-transition-name` through the cascade: a box's identity for layout
/// animation, carried on its transform.
@DisplayName("view-transition-name")
class ViewTransitionNameTest {

    private static Transform transform(String css) {
        var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, css);
        var declarations = new StyleResolver(List.of(sheet)).resolve(element("tile"));
        return ComputedStyle.of(declarations, CssLength.Context.DEFAULT).transform();
    }

    @Test
    @DisplayName("names a box, at View Transitions' default timing until the renderer times it")
    void names() {
        var named = transform("tile { view-transition-name: call-7 }").viewTransition();

        assertEquals(ViewTransition.named("call-7"), named);
        assertEquals(250, named.durationMillis());
        assertEquals(ViewTransition.DEFAULT_EASING, named.easing());
    }

    @Test
    @DisplayName("keeps the case it was written in, since a custom ident is case-sensitive")
    void caseSensitive() {
        assertEquals(
                "Tile",
                transform("tile { view-transition-name: Tile }")
                        .viewTransition()
                        .name());
    }

    @Test
    @DisplayName("is none by default, and none takes it away")
    void none() {
        assertNull(transform("tile { opacity: 1 }").viewTransition());
        assertNull(transform("tile { view-transition-name: a } tile { view-transition-name: none }")
                .viewTransition());
    }

    @ParameterizedTest
    @ValueSource(strings = {"auto", "match-element", "default", "a b", "\"quoted\"", "12px"})
    @DisplayName("drops what is not a custom ident it can use, so the box is not named")
    void refused(String value) {
        assertNull(transform("tile { view-transition-name: " + value + " }").viewTransition());
    }

    @Test
    @DisplayName("sits beside a transform without being one")
    void besideATransform() {
        var both = transform("tile { transform: scale(2); view-transition-name: a }");

        assertEquals("a", both.viewTransition().name());
        assertEquals(1, both.functions().size());
        assertEquals(List.of(), transform("tile { view-transition-name: a }").functions());
    }

    @Test
    @DisplayName("is not a transform that moves anything on its own")
    void notAMatrix() {
        var named = transform("tile { view-transition-name: a }");

        assertTrue(named.isNone());
    }
}
