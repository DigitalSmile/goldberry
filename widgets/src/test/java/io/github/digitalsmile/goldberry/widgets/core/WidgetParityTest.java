package io.github.digitalsmile.goldberry.widgets.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.css.cascade.StyleResolver;
import io.github.digitalsmile.goldberry.css.value.CssLength;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widgets.CatalogMarkup;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Widgets;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// The parity invariant, enforced.
///
/// §11: "Every widget: Java record + KDL node + CSS-styleable, per the parity
/// invariant. […] A widget that can't is a build failure."
///
/// This is that build failure. Adding a primitive without registering it for KDL,
/// or giving it a CSS type that nothing can select, fails here rather than being
/// discovered by whoever tries to style it.
class WidgetParityTest {

    /// Every registered widget that describes a node of its own.
    ///
    /// **The whole catalog, which it was not.** This was
    /// `Primitives.builtInTypes()` — ten names, and eleven more in `ChainingTest`
    /// — so §11's "every widget" was checked over a quarter of the widgets and
    /// the other three quarters were on trust. The names come from the
    /// inflater now ([CatalogMarkup]), less the handful below that describe no
    /// node at all.
    static List<String> builtIns() {
        return CatalogMarkup.types().stream()
                .filter(type -> !NOT_A_NODE.containsKey(type))
                .toList();
    }

    /// The registered names that describe **no node of their own**, and the
    /// reason each one does not — because "it has no CSS type" is a claim that
    /// has to be argued rather than a line in a list.
    ///
    /// Every one of them is still a widget, still a record and still chainable;
    /// what they are not is something a stylesheet selects, so the three
    /// assertions below have nothing to assert about them.
    private static final Map<String, String> NOT_A_NODE = Map.of(
            "action",
                    "a dialog's button as a value — the dialog draws it, and a `dialog-action` node"
                            + " nobody could write would be a second name for the same thing",
            "page", "a wizard's step as a value, for `action`'s reason",
            "marker", "a chart annotation: what it describes is drawn on an axis by the chart",
            "list", "a `Bound`, which describes nothing until a model is bound — see BoundMarkupTest",
            "table", "a `Bound`, as `list` is",
            "tree", "a `Bound`, as `list` is");

    /// The two the widened sweep found: registered widgets with a CSS type of
    /// their own that a document **cannot name or class**.
    ///
    /// `series` and `point` inflate to `ChartSeries` and `ChartPoint`, which are
    /// a chart's data — numbers and a name — and implement none of [Attributed].
    /// So `series id="cpu" class="warn"` parses, builds and silently drops both:
    /// the one place in the catalog where markup accepts an attribute and throws
    /// it away. They are checked by their **type** below like everything else,
    /// and the `#id`/`.class` half is the part that has nothing to land on.
    ///
    /// Recorded rather than fixed: both live under `data/`, and whether a value
    /// that is never drawn on its own should carry an id is a question for
    /// whoever owns the charts.
    private static final Map<String, String> NO_ATTRIBUTES = Map.of(
            "series", "a chart's data rather than a node an author styles",
            "point", "one of a `series`' numbers");

    /// An exemption is a claim about a widget, and a claim can go stale: a name
    /// that left the catalog leaves a line nobody will notice is dead, and a
    /// widget that grew a CSS type or a set of attributes would be quietly
    /// skipped for ever.
    @Test
    @DisplayName("every exemption still names a registered widget that still needs exempting")
    void theExemptionsAreLive() {
        var registered = Set.copyOf(Widgets.inflater().registered());
        for (var exempt : NOT_A_NODE.keySet()) {
            assertTrue(registered.contains(exempt), exempt + " is exempted from parity and is not registered");
            var built = built(exempt);
            assertNotNull(built, exempt + " does not inflate at all, which is a failure and not an exemption");
            assertNull(
                    styledNode(built, exempt),
                    exempt + " describes a node a stylesheet can select now; take it out of NOT_A_NODE");
        }
        for (var exempt : NO_ATTRIBUTES.keySet()) {
            assertTrue(registered.contains(exempt), exempt + " is exempted from parity and is not registered");
            assertFalse(
                    built(exempt) instanceof Attributed<?>,
                    exempt + " carries attributes now; take it out of NO_ATTRIBUTES");
        }
    }

    private static Widget built(String type) {
        return Widgets.inflater()
                .inflate(KdlParser.parse(CatalogMarkup.markup(type, "")).getFirst());
    }

    @ParameterizedTest
    @MethodSource("builtIns")
    @DisplayName("every built-in is constructible from KDL")
    void constructibleFromKdl(String type) {
        // A dozen widgets refuse to exist without an argument -- §13's rule that
        // a control nobody can read out is a failure rather than a blank -- and
        // `CatalogMarkup` is where what each one is handed lives.
        var markup = CatalogMarkup.markup(type, "");
        var widget = Widgets.inflater().inflate(KdlParser.parse(markup).getFirst());

        assertNotNull(widget);
        var styled = styledNode(widget, type);
        assertNotNull(styled, type + " describes nothing a stylesheet can select");
        assertEquals(type, styled.cssType(), "the KDL node name and the CSS type must be the same name");
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
    private static Styled styledNode(Widget widget, String type) {
        var element = styledElement(new ElementTree(widget).root(), type);
        return element == null ? null : (Styled) element.widget();
    }

    /// The same walk, returning the element rather than the widget — because the
    /// cascade resolves against an element and `id` and `class` are read off it.
    ///
    /// **The node carrying `type`, and only the first styled node as a fallback.**
    /// Taking the first was right while this swept ten primitives and is wrong
    /// over the catalog: a `dialog` describes a `dialog-scrim` with the `dialog`
    /// inside it, so the first styled node is the scrim — the widget's parity is
    /// perfect and the *walk* would have failed it. A composite is allowed to
    /// wrap the node that carries its name; what it may not do is not have one.
    private static Element styledElement(Element element, String type) {
        var named = named(element, type);
        return named != null ? named : anyStyled(element);
    }

    private static Element named(Element element, String type) {
        if (element.widget() instanceof Styled styled && type.equals(styled.cssType())) {
            return element;
        }
        for (var child : element.children()) {
            var found = named(child, type);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static Element anyStyled(Element element) {
        if (element.widget() instanceof Styled) {
            return element;
        }
        for (var child : element.children()) {
            var found = anyStyled(child);
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
        var markup = CatalogMarkup.markup(type, " id=\"x\" class=\"a\"");
        var widget = Widgets.inflater().inflate(KdlParser.parse(markup).getFirst());
        var root = new ElementTree(widget).root();
        // For a composite, the styled node is the one it builds -- and `id` and
        // `class` have to have travelled down to it, which is the half of this
        // that a composition node can get wrong.
        var element = styledElement(root, type);
        assertNotNull(element, type + " describes nothing a stylesheet can select");

        // The three selector forms §8 supports, against a widget built the KDL
        // way -- so `id` and `class` really did survive inflation. Two of them
        // for a widget that carries no attributes at all: see [#NO_ATTRIBUTES].
        for (var selector : NO_ATTRIBUTES.containsKey(type) ? List.of(type) : List.of(type, "#x", ".a")) {
            var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, selector + " { background: #123456 }");
            var style = ComputedStyle.of(new StyleResolver(List.of(sheet)).resolve(element), CssLength.Context.DEFAULT);
            assertEquals(0xFF123456, style.background(), () -> "\"" + selector + "\" did not match a " + type);
        }
    }

    /// ADR-0109's rule, asked of the catalog: a stateful composite builds the
    /// node that carries its CSS type, and **one** of them. Two nodes of one type
    /// nested in the cascade take every rule for that type twice — a border drawn
    /// inside a border, a padding applied at both levels — and neither of them is
    /// wrong on its own, which is what makes it hard to see.
    ///
    /// `BreadcrumbsTest` was the only place this was asserted, for the one widget
    /// whose author happened to think of it.
    @ParameterizedTest
    @MethodSource("builtIns")
    @DisplayName("every built-in describes exactly one node of its own type")
    void oneNodeCarriesTheType(String type) {
        var widget = Widgets.inflater()
                .inflate(KdlParser.parse(CatalogMarkup.markup(type, "")).getFirst());

        assertEquals(
                1,
                carrying(new ElementTree(widget).root(), type),
                type + " describes more than one node a `" + type + "` rule matches, so every rule applies twice");
    }

    private static int carrying(Element element, String type) {
        var here = element.widget() instanceof Styled styled && type.equals(styled.cssType()) ? 1 : 0;
        for (var child : element.children()) {
            here += carrying(child, type);
        }
        return here;
    }

    @ParameterizedTest
    @MethodSource("builtIns")
    @DisplayName("every built-in renders to a box")
    void rendersToABox(String type) {
        var markup = CatalogMarkup.markup(type, "");
        var widget = Widgets.inflater().inflate(KdlParser.parse(markup).getFirst());

        // Not rendered here -- text needs a real font, which needs the native
        // library -- but the contract that makes rendering possible is checkable
        // without one.
        assertInstanceOf(Paints.class, widget instanceof Paints ? widget : styledNode(widget, type));
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

        assertTrue(
                registered.containsAll(Primitives.builtInTypes()),
                "declared built-ins missing from the catalog: " + minus(Primitives.builtInTypes(), registered));
        assertTrue(
                registered.containsAll(Controls.controlTypes()),
                "declared controls missing from the catalog: " + minus(Controls.controlTypes(), registered));
    }

    /// What `declared` has and the catalog does not — the useful half of a
    /// containment failure, because "a set is not a subset" says nothing about
    /// which name was forgotten.
    private static List<String> minus(List<String> declared, Set<String> registered) {
        return declared.stream().filter(name -> !registered.contains(name)).toList();
    }

    /// Every type a base rule selects, which is every name a theme can reach.
    private static Set<String> styledTypes() {
        var types = new TreeSet<String>();
        for (var rule : Controls.baseStylesheet().rules()) {
            for (var selector : rule.selectors()) {
                for (var part : selector.parts()) {
                    var type = part.compound().type();
                    if (type != null) {
                        types.add(type);
                    }
                }
            }
        }
        return types;
    }

    /// A widget nothing styles paints the default box, which is nobody's
    /// intention and shows up as "it rendered, just wrong" rather than as a
    /// failure.
    ///
    /// **This is what seventeen per-widget tests were doing.** `badge`, `button`,
    /// `checkbox`, `radio`, `toggle`, `slider`, `segmented`, `select`, `knob`,
    /// `chip`, `progress`, `affix`, `scroll`, `stack` and `qr-code` each asserted
    /// that a hand-kept `List.of` contained their own name; none of them would
    /// have noticed the widget next to it going unstyled, because a list contains
    /// what it was written to contain. Asked of the catalog, the same question
    /// covers all 72.
    /// The four the sweep found: registered widgets the base stylesheet names
    /// nowhere, and why each one is right to have no rule.
    ///
    /// Every one of them paints nothing of its own, so a default rule would be a
    /// colour nobody asked for rather than an appearance. An application can
    /// still select all four; what the toolkit does not do is get there first.
    private static final Map<String, String> UNSTYLED = Map.of(
            "spacer", "a gap: it has a size and no surface",
            "stack", "a layout container, drawn entirely by what is stacked in it",
            "canvas", "the application paints it, which is the whole of what it is for",
            "sparkline", "inherits `color` like text, deliberately — see its own doc comment");

    @ParameterizedTest
    @MethodSource("builtIns")
    @DisplayName("every registered widget has a rule that styles it")
    void everyRegisteredTypeIsStyled(String type) {
        if (UNSTYLED.containsKey(type)) {
            assertFalse(
                    styledTypes().contains(type),
                    type + " has a base rule now (" + UNSTYLED.get(type) + " is stale); take it out of UNSTYLED");
            return;
        }
        assertTrue(
                styledTypes().contains(type),
                type + " is in the catalog and the base stylesheet names it nowhere, so it paints the default box");
    }

    /// The three registered names that read like a part of another widget, and
    /// why each is a widget in its own right.
    ///
    /// A `radio-group` is what a document writes; the radios inside it are its
    /// children. `text-input` and `text-area` are controls that happen to begin
    /// with the name of the `text` primitive, which is a collision of English
    /// rather than of meaning.
    private static final Set<String> READ_LIKE_PARTS = Set.of("radio-group", "text-input", "text-area");

    /// ADR-0065's half that has no other holder: a **part** is CSS-selectable and
    /// deliberately not KDL-constructible, because a `toggle-track` outside a
    /// `toggle` is a pill that means nothing.
    ///
    /// Eleven classes asserted this by naming parts one at a time —
    /// `radio-indicator`, `check-mark`, `slider-thumb`, `chip-dismiss`,
    /// `knob-arc`, `select-chevron`, `progress-fill`, `segmented-indicator` and
    /// the rest — which holds only for the parts somebody remembered to list.
    /// Asked of the catalog it holds for every part there will ever be:
    /// `@Markup("toggle-track")` on `ToggleTrack` fails here, and each of those
    /// lists would have kept passing unless its author happened to have named
    /// that one.
    @Test
    @DisplayName("no name shaped like a part of a registered widget is itself registered")
    void partsAreNotConstructible() {
        var registered = Set.copyOf(Widgets.inflater().registered());

        // The stylesheet styles things markup cannot write -- the premise the
        // rest of this rests on, and the half that would rot silently if the
        // parts ever became ordinary widgets.
        var parts = styledTypes().stream()
                .filter(type -> !registered.contains(type))
                .toList();
        assertFalse(parts.isEmpty(), "the base stylesheet selects no part at all, so ADR-0065 has nothing to hold");

        for (var name : registered) {
            if (READ_LIKE_PARTS.contains(name)) {
                continue;
            }
            for (var owner : registered) {
                assertFalse(
                        name.startsWith(owner + "-"),
                        () -> name + " is registered and reads as a part of " + owner
                                + "; a part is CSS-selectable and not KDL-constructible (ADR-0065)");
            }
        }
    }

    /// An exemption is a claim, and [#READ_LIKE_PARTS] is three of them.
    @Test
    @DisplayName("every name excused from the part rule is still registered and still reads like a part")
    void theNameExemptionsAreLive() {
        var registered = Set.copyOf(Widgets.inflater().registered());
        for (var name : READ_LIKE_PARTS) {
            assertTrue(registered.contains(name), name + " is excused from the part rule and is not registered");
            assertTrue(
                    registered.stream().anyMatch(owner -> name.startsWith(owner + "-")),
                    name + " no longer reads as a part of anything; take it out of READ_LIKE_PARTS");
        }
    }

    @Test
    @DisplayName("a Java-built and a KDL-built widget are the same value")
    void javaAndKdlAgree() {
        var fromJava = new Row(List.of(new Text("hi", Attributes.NONE)), new Attributes("r", Set.of("wide"), "r"));

        var fromKdl = Widgets.inflater()
                .inflate(KdlParser.parse("row id=\"r\" class=\"wide\" { text \"hi\" }")
                        .getFirst());

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
