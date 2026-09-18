package io.github.digitalsmile.goldberry.widget.attr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.kdl.KdlParser;

/// `name=`, the attribute §3 asks for and §13 needs — on [Attributes], where
/// every widget gets it rather than each remembering its own.
///
/// The case it exists for is the one nothing else can reach: an **icon-only**
/// control's label is the empty string by construction, because the icon is the
/// whole of what is on screen, so a name derived from what is showing is a name
/// that is not there ([ADR-0260]).
class AccessibleNameTest {

    private static KdlNodeLike parse(String markup) {
        return new KdlNodeLike(markup);
    }

    /// Inflating one node, which is all these need — `Attributes.of` takes a
    /// `KdlNode` and the parser is the only thing that makes one.
    private record KdlNodeLike(String markup) {

        Attributes attributes() {
            return Attributes.of(KdlParser.parse(markup).getFirst());
        }
    }

    @Test
    @DisplayName("a widget that says nothing has no name, which is an answer")
    void noneHasNoName() {
        assertNull(Attributes.NONE.name());
    }

    @Test
    @DisplayName("`name=` is read off a markup node")
    void markupCarriesIt() {
        assertEquals(
                "Delete",
                parse("button icon=\"trash\" name=\"Delete\"").attributes().name());
    }

    @Test
    @DisplayName("and a node without one still parses to no name rather than an empty one")
    void markupWithoutOne() {
        assertNull(parse("button \"Save\"").attributes().name());
    }

    @Test
    @DisplayName("blank is the same as absent, because a name of spaces is not a name")
    void blankIsAbsent() {
        // The rule `tooltip` and `context-menu` already keep. A reader announcing
        // three spaces is a reader announcing nothing, at more length.
        assertNull(Attributes.NONE.name("   ").name());
        assertNull(Attributes.NONE.name("").name());
        assertNull(Attributes.NONE.name(null).name());
    }

    @Test
    @DisplayName("setting one leaves everything else alone")
    void witherIsIndependent() {
        var before = Attributes.NONE.id("save").classes("primary").tooltip("Save the file");
        var after = before.name("Save the document");

        assertEquals("Save the document", after.name());
        assertEquals(before.id(), after.id());
        assertEquals(before.classes(), after.classes());
        assertEquals(before.tooltip(), after.tooltip());
        assertEquals(before.key(), after.key());
    }

    @Test
    @DisplayName("and every other wither carries it forward")
    void otherWithersKeepIt() {
        // The failure this catches is the one a sixth record component invites:
        // a wither written before the field existed drops it, and nothing
        // complains because `null` is a legal name.
        var named = Attributes.NONE.name("Delete");

        assertEquals("Delete", named.id("x").name());
        assertEquals("Delete", named.classes("a").name());
        assertEquals("Delete", named.key(new Object()).name());
        assertEquals("Delete", named.tooltip("t").name());
        assertEquals("Delete", named.contextMenu("m").name());
    }

    @Test
    @DisplayName("the five-argument form still means what it meant")
    void theOlderFormIsUnchanged() {
        // Kept so that the four hundred call sites that predate this say `null`
        // by omission rather than by edit.
        var five = new Attributes("id", Set.of(), "id", "tip", "menu");

        assertNull(five.name());
        assertEquals("tip", five.tooltip());
    }

    @Test
    @DisplayName("an unnamed attributes value is the shared NONE rather than a fresh one")
    void noneIsShared() {
        // `assertSame(NONE, NONE)` stood here, which compares a constant with
        // itself (the 2026-09-18 review, §6). What is worth pinning is that a node
        // carrying no attributes parses to the same *value* as the constant a
        // widget defaults to — so the two ways of saying "nothing" agree, and a
        // parser that started defaulting something would be caught here rather
        // than in whichever widget noticed first.
        assertEquals(Attributes.NONE, parse("button \"Go\"").attributes());
        assertNull(Attributes.NONE.name());
        assertNull(Attributes.NONE.id());
        assertEquals(Set.of(), Attributes.NONE.classes());
    }
}
