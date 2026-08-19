package io.github.digitalsmile.goldberry.widgets.core;

import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;

import io.github.digitalsmile.goldberry.widgets.core.Primitives;
import io.github.digitalsmile.goldberry.widgets.core.Row;
import io.github.digitalsmile.goldberry.widgets.text.Text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.CssLength;
import io.github.digitalsmile.goldberry.css.StyleResolver;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import io.github.digitalsmile.goldberry.widgets.Widgets;

/// The parity invariant, enforced.
///
/// §11: "Every widget: Java record + KDL node + CSS-styleable, per the parity
/// invariant. […] A widget that can't is a build failure."
///
/// This is that build failure. Adding a primitive without registering it for KDL,
/// or giving it a CSS type that nothing can select, fails here rather than being
/// discovered by whoever tries to style it.
class WidgetParityTest {

    static List<String> builtIns() {
        return Primitives.builtInTypes();
    }

    @ParameterizedTest
    @MethodSource("builtIns")
    @DisplayName("every built-in is constructible from KDL")
    void constructibleFromKdl(String type) {
        // `text` needs its content argument; the rest are bare nodes.
        var markup = type.equals("text") ? "text \"hi\"" : type;
        var widget = Widgets.inflater().inflate(KdlParser.parse(markup).getFirst());

        assertNotNull(widget);
        var styled = styledNode(widget);
        assertNotNull(styled, type + " describes nothing a stylesheet can select");
        assertEquals(type, styled.cssType(),
                "the KDL node name and the CSS type must be the same name");
    }

    /// The node a stylesheet actually selects, which is not always the node a
    /// document writes.
    ///
    /// A **composite** — `scroll`, and `tabs` before it — is a stateful
    /// composition node that styles nothing and builds the node carrying its CSS
    /// type. That is deliberate and not an exception to parity: a stateful widget
    /// that was also styled would put two nodes of the same type in the cascade,
    /// one inside the other, and every rule would apply to both
    /// ([ADR-0109], [ADR-0116]). So parity is checked against what the widget
    /// *describes* rather than against the widget, which is what a stylesheet
    /// sees either way.
    ///
    /// Returns the widget itself when it is styled, so the simple primitives are
    /// checked exactly as before.
    private static Styled styledNode(Widget widget) {
        if (widget instanceof Styled styled) {
            return styled;
        }
        return firstStyled(new ElementTree(widget).root());
    }

    /// The same walk, returning the element rather than the widget — because the
    /// cascade resolves against an element and `id` and `class` are read off it.
    private static Element styledElement(Element element) {
        if (element.widget() instanceof Styled) {
            return element;
        }
        for (var child : element.children()) {
            var found = styledElement(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static Styled firstStyled(Element element) {
        if (element.widget() instanceof Styled styled) {
            return styled;
        }
        for (var child : element.children()) {
            var found = firstStyled(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @ParameterizedTest
    @MethodSource("builtIns")
    @DisplayName("every built-in is selectable by its type, id and class")
    void styleable(String type) {
        var markup = type.equals("text") ? "text id=\"x\" class=\"a\" \"hi\"" : type + " id=\"x\" class=\"a\"";
        var widget = Widgets.inflater().inflate(KdlParser.parse(markup).getFirst());
        var root = new ElementTree(widget).root();
        // For a composite, the styled node is the one it builds -- and `id` and
        // `class` have to have travelled down to it, which is the half of this
        // that a composition node can get wrong.
        var element = widget instanceof Styled ? root : styledElement(root);
        assertNotNull(element, type + " describes nothing a stylesheet can select");

        // The three selector forms §8 supports, against a widget built the KDL
        // way -- so `id` and `class` really did survive inflation.
        for (var selector : List.of(type, "#x", ".a")) {
            var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, selector + " { background: #123456 }");
            var style = ComputedStyle.of(
                    new StyleResolver(List.of(sheet)).resolve(element), CssLength.Context.DEFAULT);
            assertEquals(0xFF123456, style.background(),
                    () -> "\"" + selector + "\" did not match a " + type);
        }
    }

    @ParameterizedTest
    @MethodSource("builtIns")
    @DisplayName("every built-in renders to a box")
    void rendersToABox(String type) {
        var markup = type.equals("text") ? "text \"hi\"" : type;
        var widget = Widgets.inflater().inflate(KdlParser.parse(markup).getFirst());

        // Not rendered here -- text needs a real font, which needs the native
        // library -- but the contract that makes rendering possible is checkable
        // without one.
        assertInstanceOf(Paints.class,
                widget instanceof Paints ? widget : styledNode(widget));
    }

    @Test
    @DisplayName("the generated catalog registers every declared widget")
    void registryMatchesDeclaredTypes() {
        // Three lists that must not drift: what this test iterates, what the
        // catalog registers, and what CSS is expected to select. Containment
        // rather than equality now that one inflater carries the whole module --
        // `tabs`, `menu`, `popover` and `hud` are registered and are checked by
        // their own suites rather than by this one.
        var registered = Set.copyOf(Widgets.inflater().registered());

        assertTrue(registered.containsAll(Primitives.builtInTypes()),
                "declared built-ins missing from the catalog: "
                        + minus(Primitives.builtInTypes(), registered));
        assertTrue(registered.containsAll(Controls.controlTypes()),
                "declared controls missing from the catalog: "
                        + minus(Controls.controlTypes(), registered));
    }

    /// What `declared` has and the catalog does not — the useful half of a
    /// containment failure, because "a set is not a subset" says nothing about
    /// which name was forgotten.
    private static List<String> minus(List<String> declared, Set<String> registered) {
        return declared.stream().filter(name -> !registered.contains(name)).toList();
    }

    @Test
    @DisplayName("a Java-built and a KDL-built widget are the same value")
    void javaAndKdlAgree() {
        var fromJava = new Row(
                List.of(new Text("hi", Attributes.NONE)),
                new Attributes("r", Set.of("wide"), "r"));

        var fromKdl = Widgets.inflater().inflate(
                KdlParser.parse("row id=\"r\" class=\"wide\" { text \"hi\" }").getFirst());

        // Records, so equality is structural -- which is what makes "the same
        // widget in two syntaxes" a checkable claim rather than a slogan.
        assertEquals(fromJava, fromKdl);
    }

    @Test
    @DisplayName("class is space-separated, as in HTML")
    void multipleClasses() {
        var widget = (Styled) Widgets.inflater()
                .inflate(KdlParser.parse("panel class=\"card raised\"").getFirst());

        assertEquals(Set.of("card", "raised"), widget.classes());
    }

    @Test
    @DisplayName("an unregistered node names itself and the alternatives")
    void unknownWidget() {
        var thrown = org.junit.jupiter.api.Assertions.assertThrows(
                io.github.digitalsmile.goldberry.kdl.KdlSyntaxException.class,
                () -> Widgets.inflater().inflate(KdlParser.parse("buttton").getFirst()));

        assertTrue(thrown.getMessage().contains("buttton"));
        assertTrue(thrown.getMessage().contains("panel"));
    }
}
