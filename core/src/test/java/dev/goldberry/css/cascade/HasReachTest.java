package dev.goldberry.css.cascade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.media.MediaContext;
import dev.goldberry.render.desktop.SystemTheme;

/// What the resolver tells the element tree about sheets that read siblings
/// and descendants.
class HasReachTest {

    private static StyleResolver resolver(String css) {
        return new StyleResolver(List.of(Stylesheet.parse(CascadeLayer.APPLICATION, css)));
    }

    @Test
    @DisplayName("sheets without :has() or a sibling combinator ask the tree for nothing")
    void nothing() {
        var resolver = resolver("card text { color: red } list > item:hover { color: blue }");
        assertFalse(resolver.hasMatters());
        assertFalse(resolver.hasLooksAtSiblings());
        assertFalse(resolver.siblingsMatter());
        assertEquals(HasReach.NONE, resolver.hasReach("card"));
    }

    @Test
    @DisplayName("a :has() reaches the elements of the type it sits on, and only those")
    void typed() {
        var resolver = resolver("card:has(:checked) { color: red }");
        assertTrue(resolver.hasMatters());
        assertEquals(HasReach.OWN, resolver.hasReach("card"));
        assertEquals(HasReach.NONE, resolver.hasReach("list"));
        assertEquals(HasReach.NONE, resolver.hasReach(null));
        assertFalse(resolver.siblingsMatter(), "a :has() alone reads no sibling");
    }

    @Test
    @DisplayName("left of a combinator, it reaches the subtree")
    void subtree() {
        var resolver = resolver("card:has(:checked) text { color: red }");
        assertEquals(HasReach.SUBTREE, resolver.hasReach("card"));
        assertEquals(HasReach.NONE, resolver.hasReach("text"));
    }

    @Test
    @DisplayName("on a compound that names no type, it reaches every element")
    void untyped() {
        var resolver = resolver(".open:has(item) { color: red }");
        assertEquals(HasReach.OWN, resolver.hasReach("anything"));
        assertEquals(HasReach.OWN, resolver.hasReach(null));
    }

    @Test
    @DisplayName("inside :is() and :not(), as on the compound itself")
    void nested() {
        var resolver = resolver(":not(card:has(item)) { color: red }");
        assertEquals(HasReach.OWN, resolver.hasReach("card"));
    }

    @Test
    @DisplayName("an argument that starts at a sibling makes earlier siblings readers")
    void siblings() {
        assertTrue(resolver("card:has(+ note) { color: red }").hasLooksAtSiblings());
        assertTrue(resolver("card:has(~ note x) { color: red }").hasLooksAtSiblings());
        assertFalse(resolver("card:has(> note + x) { color: red }").hasLooksAtSiblings());
    }

    @Test
    @DisplayName("a sibling combinator makes siblings matter, and a reader in an ancestor position reaches below")
    void siblingCombinators() {
        var plain = resolver("a + b { color: red }");
        assertTrue(plain.siblingsMatter());
        assertFalse(plain.positionReachesDescendants("b"));

        var deep = resolver("a ~ b text { color: red }");
        assertTrue(deep.siblingsMatter());
        assertTrue(deep.positionReachesDescendants("b"));
        assertFalse(deep.positionReachesDescendants("a"));
    }

    @Test
    @DisplayName("a resolver under another media context keeps the index")
    void underKeepsIt() {
        var resolver = resolver("@media (min-width: 600px) { card:has(x) { color: red } }");
        var wide = resolver.under(new MediaContext(800, 600, SystemTheme.LIGHT, false));
        assertEquals(HasReach.OWN, wide.hasReach("card"));
    }
}
