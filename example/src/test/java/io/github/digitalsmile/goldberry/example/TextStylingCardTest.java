package io.github.digitalsmile.goldberry.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.example.ui.TextStylingCard;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.input.hit.HitTest;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.paint.tree.RenderTree;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.text.font.Fonts;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.checkbox.Checkbox;
import io.github.digitalsmile.goldberry.widgets.controls.segmented.Segmented;
import io.github.digitalsmile.goldberry.widgets.form.textarea.TextArea;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// The Forms screen's text-property card, used rather than photographed.
///
/// `GalleryGoldenTest` can show the card in one state. What it cannot show is the
/// thing the card exists for: that changing an option **restyles the field**
/// rather than replacing it, so the text, the caret and the undo history a reader
/// has built up survive every toggle — and that the pair of rules is one class,
/// because `text-decoration` is one property holding a set (ADR-0321, ADR-0324).
///
/// The widget-level proof that a caret follows the alignment is in `:widgets`,
/// where the control lives; this is the application's half.
class TextStylingCardTest {

    /// A column of the Forms wall, near enough — wide enough that the sample wraps
    /// into lines of different lengths, which is what an alignment is visible in.
    private static final int CARD_WIDTH = 420;

    private static final int CARD_HEIGHT = 560;

    private ElementTree tree;
    private Fonts fonts;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        tree = new ElementTree(new TextStylingCard(), new TourTestHost(List.of()));
        render();
    }

    private void render() {
        tree.flush();
        if (fonts == null) {
            fonts = Fonts.bundled();
        }
        new WidgetRenderer(Controls.stylesheets(Theme.NORD_DARK), fonts).render(tree);
    }

    /// The classes the field is wearing — which is the whole output of the card's
    /// state, and what the stylesheet turns into declarations.
    private Set<String> classes() {
        var area = area();
        assertNotNull(area, "the card has no text-area on it");
        return area.attributes().classes();
    }

    private TextArea area() {
        return find(
                tree.root(),
                TextArea.class,
                widget -> "styled-area".equals(widget.attributes().id()));
    }

    /// Toggles the checkbox with that id, the way its own click handler does.
    private void check(String id) {
        var box = find(
                tree.root(),
                Checkbox.class,
                widget -> id.equals(widget.attributes().id()));
        assertNotNull(box, "nothing with id " + id + " is on the card");
        assertNotNull(box.onChange(), id + " is not wired to anything");
        box.onChange().run();
        render();
    }

    /// Picks a segment, the way a press on one does.
    private void pick(String id, String value) {
        var bar = find(
                tree.root(),
                Segmented.class,
                widget -> id.equals(widget.attributes().id()));
        assertNotNull(bar, "nothing with id " + id + " is on the card");
        bar.onChange().accept(value);
        render();
    }

    /// The line that prints what the options amount to.
    private String declarations() {
        var text = find(
                tree.root(),
                Text.class,
                widget -> "styled-css".equals(widget.attributes().id()));
        assertNotNull(text, "the card does not print its declarations");
        return text.content();
    }

    private static <W> W find(Element element, Class<W> type, java.util.function.Predicate<W> matches) {
        if (type.isInstance(element.widget()) && matches.test(type.cast(element.widget()))) {
            return type.cast(element.widget());
        }
        for (var child : element.children()) {
            var found = find(child, type, matches);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Test
    @DisplayName("starts at the initial value of every property, so no class says the default")
    void startsPlain() {
        assertEquals(Set.of(), classes(), "a rule restating an initial value is a rule nothing can be told from");
        assertTrue(declarations().startsWith("text-align: start"), declarations());
        assertTrue(declarations().contains("font-weight: 400"), declarations());
        assertTrue(declarations().contains("font-style: normal"), declarations());
        assertTrue(declarations().contains("text-decoration: none"), declarations());
        assertTrue(declarations().contains("font-family: Inter"), declarations());
    }

    @Test
    @DisplayName("an alignment is one class, and going back to start removes it")
    void alignment() {
        pick("styled-align", "center");
        assertEquals(Set.of("align-center"), classes());
        assertTrue(declarations().startsWith("text-align: center"), declarations());

        pick("styled-align", "end");
        assertEquals(Set.of("align-end"), classes());

        pick("styled-align", "start");
        assertEquals(Set.of(), classes());
    }

    @Test
    @DisplayName("the two rules are one class, because text-decoration is one property")
    void theRulesAreOneClass() {
        check("styled-under");
        assertEquals(Set.of("rule-underline"), classes());
        assertTrue(declarations().contains("text-decoration: underline;"), declarations());

        check("styled-struck");
        assertEquals(
                Set.of("rule-both"),
                classes(),
                "two classes each writing text-decoration would leave the later one winning");
        assertTrue(declarations().contains("text-decoration: underline line-through"), declarations());

        check("styled-under");
        assertEquals(Set.of("rule-struck"), classes());
    }

    @Test
    @DisplayName("a face is a face: weight, style and family are three classes")
    void theFaceProperties() {
        check("styled-bold");
        check("styled-italic");
        check("styled-mono");

        assertEquals(Set.of("bold", "italic", "mono"), classes());
        assertTrue(declarations().contains("font-weight: 600"), declarations());
        assertTrue(declarations().contains("font-style: italic"), declarations());
        assertTrue(declarations().contains("font-family: \"JetBrains Mono\""), declarations());
    }

    @Test
    @DisplayName("the scale is a class only when it is not the default rank")
    void theScale() {
        pick("styled-scale", "heading");
        assertEquals(Set.of("size-heading"), classes());
        assertTrue(declarations().contains("font-size: 15px"), declarations());
        assertTrue(declarations().contains("line-height: 20px"), declarations());

        pick("styled-scale", "caption");
        assertEquals(Set.of("size-caption"), classes());
        assertTrue(declarations().contains("font-size: 11px"), declarations());

        pick("styled-scale", "body");
        assertEquals(Set.of(), classes(), "`body` is the rank a field is already set at");
    }

    @Test
    @DisplayName("every option can be on at once, and the class set says so")
    void allOfThem() {
        pick("styled-align", "center");
        pick("styled-scale", "heading");
        check("styled-bold");
        check("styled-italic");
        check("styled-under");
        check("styled-struck");
        check("styled-mono");

        assertEquals(Set.of("align-center", "size-heading", "bold", "italic", "mono", "rule-both"), classes());
    }

    /// The assertion the card is really about: toggling an option **restyles** the
    /// field. A control that was replaced would have a new state — and a new caret,
    /// an empty undo history and whatever text it was constructed with — so a
    /// reader who had typed into it would lose their work on the next click
    /// (ADR-0315's reconciliation, from the application's side).
    @Test
    @DisplayName("changing an option keeps the same field, so what a reader typed survives")
    void theFieldIsRestyledAndNotReplaced() {
        var before = state();
        var text = area().value();

        pick("styled-align", "center");
        check("styled-bold");
        check("styled-mono");

        assertSame(before, state(), "the field was rebuilt from scratch, so its caret and undo went with it");
        assertEquals(text, area().value(), "and the value it is offered has not changed either");
        assertFalse(classes().isEmpty(), "the fixture has to have changed something");
    }

    /// The element's state, which is the identity a caret and an undo history hang
    /// off.
    private Object state() {
        var element = findElement(tree.root());
        assertNotNull(element, "the card has no text-area element");
        return element.state().orElseThrow();
    }

    private static Element findElement(Element element) {
        if (element.widget() instanceof TextArea area
                && "styled-area".equals(area.attributes().id())) {
            return element;
        }
        for (var child : element.children()) {
            var found = findElement(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /// And the picture, because the properties themselves *are* visible: a
    /// centred, semibold italic paragraph with both rules through it is every one
    /// of them at once, and an assertion about class names would pass just as well
    /// if the cascade dropped every declaration.
    ///
    /// The state is driven through the card's own handlers rather than constructed,
    /// so this is the same path a reader's clicks take.
    @Test
    @DisplayName("every property at once, drawn")
    void decorated() {
        var sheets = new ArrayList<Stylesheet>(Controls.stylesheets(Theme.NORD_DARK));
        sheets.add(Stylesheet.resource(CascadeLayer.APPLICATION, Showcase.class, "showcase.css"));

        // Reduced motion, so the picture is of the text and not of a segmented
        // indicator caught mid-flight: the bars this test presses animate their
        // selection over §1.7's fast duration, and where that has got to by the
        // time the frame is painted is a wall-clock fact. The transitions still
        // run and still end — they simply end at once.
        var renderer = new WidgetRenderer(sheets, fonts).reducedMotion(true);
        pick("styled-align", "center");
        pick("styled-scale", "heading");
        check("styled-bold");
        check("styled-italic");
        check("styled-under");
        check("styled-struck");

        // A router and two warm frames, because a `text-area` wraps at the width
        // the **last frame** measured it at ([ADR-0116]'s rule, which the control
        // states in as many words). Without one the field is unmeasured, reports
        // "do not wrap", and the picture would be of a state no window ever shows.
        var router = new PointerRouter();
        router.focusRoot(tree.root());
        router.windowBounds(LogicalRect.of(0, 0, CARD_WIDTH, CARD_HEIGHT));
        var warm = TestFrames.of(CARD_WIDTH, CARD_HEIGHT, 1.0f, 0);
        try (var render = RenderTree.create()) {
            for (var i = 0; i < 2; i++) {
                tree.flush();
                render.update(warm.frame(), renderer.render(tree));
                router.updateRegions(HitTest.capture(render));
            }
        } finally {
            warm.end();
        }

        GoldenImage.assertMatches("forms-text-properties", CARD_WIDTH, CARD_HEIGHT, 1.0f, frame -> {
            try (var render = RenderTree.create()) {
                tree.flush();
                render.update(frame, renderer.render(tree));
                render.paint(frame);
            }
        });
    }
}
