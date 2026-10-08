package dev.goldberry.widgets.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.input.hit.HitTest;
import dev.goldberry.kdl.KdlParser;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.paint.Box;
import dev.goldberry.text.SpanPaint;
import dev.goldberry.text.flow.TextDecoration;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.WidgetRenderer;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.semantics.Role;
import dev.goldberry.widget.style.Styled;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.Widgets;
import dev.goldberry.widgets.controls.TestFont;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.Row;

/// `rich-text`: one paragraph of runs, each styled by the cascade, wrapping as
/// one text.
///
/// The scene is the one the gap was reported with: a keyword in the middle of
/// an ability's sentence, in a 300 px column.
///
/// Read more: [Text and links](https://goldberry.dev/docs/components/text.html#text).
@DisplayName("rich-text")
class RichTextTest {

    private static final String GIVE = "Give it ";
    private static final String BLEEDING = "Bleeding";
    private static final String REST = " equal to the amount of boost it lost, then reset it.";
    private static final String KEYWORD = "rich-text > run.keyword { color: #ebcb8b; font-weight: bold }";

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static RichText sentence() {
        return new RichText(
                List.of(new Run(GIVE), new Run(BLEEDING, Set.of("keyword")), new Run(REST)),
                Attributes.NONE.id("ability"));
    }

    private static List<Stylesheet> sheets(String... rules) {
        var sheets = new ArrayList<>(Controls.stylesheets(Theme.NORD_DARK));
        sheets.add(Stylesheet.parse(CascadeLayer.APPLICATION, "#column { width: 300px }\n" + String.join("\n", rules)));
        return sheets;
    }

    /// The box `widget` renders to, with the cascade of `rules` over the theme.
    private static Box render(Widget widget, String... rules) {
        var renderer = new WidgetRenderer(sheets(rules), TestFont.get());
        return renderer.render(new ElementTree(widget));
    }

    private static Box.Text textOf(Box box) {
        var text = find(box);
        if (text == null) {
            throw new AssertionError("no text in " + box);
        }
        return text;
    }

    private static Box.@Nullable Text find(Box box) {
        if (box.text() != null) {
            return box.text();
        }
        for (var child : box.children()) {
            var found = find(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static List<HitTest.Region> regions(Widget content, String... rules) {
        var column = new Column(List.of(new Column(List.of(content), Attributes.NONE.id("column"))), Attributes.NONE);
        try (var session = Offscreen.of(400, 300).stylesheets(sheets(rules)).session(column)) {
            session.frame();
            return session.regions().stream()
                    .filter(region -> region.owner() instanceof Element element
                            && element.widget() instanceof Styled styled
                            && Set.of("text", "rich-text").contains(styled.cssType()))
                    .toList();
        }
    }

    @Nested
    @DisplayName("wrapping")
    class Wrapping {

        @Test
        @DisplayName("is one paragraph at 300 px: one box, lines running across the keyword")
        void oneParagraph() {
            var boxes = regions(sentence(), KEYWORD);

            assertEquals(1, boxes.size(), "one box for the whole sentence");
            var box = boxes.getFirst();
            assertTrue(box.width() <= 300.5, () -> "no wider than its column: " + box);

            var text = textOf(render(sentence(), KEYWORD));
            var paragraph = text.paragraph();
            var layout = paragraph.layout(box.width());
            assertTrue(layout.lineCount() >= 2, "the sentence wraps");
            assertEquals(layout.height(), box.height(), 1.0, "the box is as tall as its lines, on the pixel grid");
            var keywordEnd = GIVE.length() + BLEEDING.length();
            var first = layout.lines().getFirst();
            assertTrue(
                    first.end() > keywordEnd,
                    () -> "the first line runs on past the keyword: " + first.textIn(paragraph.text()));
        }

        @Test
        @DisplayName("where a row of texts in the same column wraps as three columns")
        void theRowDoesNot() {
            var row = new Row(new Text(GIVE), new Text(BLEEDING, Attributes.NONE.classes("keyword")), new Text(REST));
            var boxes = regions(row);

            assertEquals(3, boxes.size());
            // Side by side: each starts to the right of the one before it, so the
            // sentence reads down three columns rather than across one paragraph.
            assertTrue(boxes.get(1).left() > boxes.get(0).left());
            assertTrue(boxes.get(2).left() > boxes.get(1).left());
            assertTrue(boxes.get(0).height() > 20, () -> "\"Give it\" wraps inside a column of its own: " + boxes);
        }
    }

    @Nested
    @DisplayName("the cascade")
    class Cascade {

        @Test
        @DisplayName("styles each run as a child node: the keyword takes its colour and weight")
        void perRun() {
            var styled = textOf(render(sentence(), KEYWORD));
            var plain = textOf(render(sentence()));
            var start = GIVE.length();
            var end = start + BLEEDING.length();

            assertEquals(List.of(new SpanPaint(start, end, 0xFFEBCB8B, TextDecoration.NONE)), styled.spans());
            assertTrue(
                    styled.paragraph().widthBetween(start, end)
                            > plain.paragraph().widthBetween(start, end),
                    "the keyword is shaped bold, so it is wider");
            assertEquals(
                    plain.paragraph().widthBetween(0, start),
                    styled.paragraph().widthBetween(0, start),
                    0.001,
                    "and the words around it are not");
        }

        @Test
        @DisplayName("a run inherits what it does not set, and an underline is its own")
        void inheritsAndDecorates() {
            var text = textOf(render(
                    sentence(),
                    "#ability { color: #a3be8c }",
                    "rich-text > run.keyword { text-decoration: underline }"));
            var start = GIVE.length();

            assertEquals(0xFFA3BE8C, text.argb());
            assertEquals(
                    List.of(new SpanPaint(
                            start, start + BLEEDING.length(), 0xFFA3BE8C, Set.of(TextDecoration.UNDERLINE))),
                    text.spans());
        }

        @Test
        @DisplayName("runs that look like the paragraph draw as one text: no spans")
        void unstyledIsPlain() {
            var text = textOf(render(sentence()));

            assertTrue(text.spans().isEmpty());
            assertEquals(GIVE + BLEEDING + REST, text.paragraph().text());
        }

        @Test
        @DisplayName("one run is the paragraph a text with the same words gets")
        void oneRunIsAText() {
            var renderer = new WidgetRenderer(sheets(), TestFont.get());
            var rich = textOf(renderer.render(new ElementTree(new RichText(new Run("Order")))));
            var text = textOf(renderer.render(new ElementTree(new Text("Order"))));

            assertTrue(rich.paragraph() == text.paragraph(), "the same shaping, from the same cache");
            assertEquals(text, rich);
        }

        @Test
        @DisplayName("a settled frame joins nothing new")
        void settledFrameIsCached() {
            var renderer = new WidgetRenderer(sheets(KEYWORD), TestFont.get());
            var first = textOf(renderer.render(new ElementTree(sentence())));
            var again = textOf(renderer.render(new ElementTree(sentence())));

            assertTrue(first.paragraph() == again.paragraph(), "the same joined paragraph, frame to frame");
        }
    }

    @Nested
    @DisplayName("markup")
    class Markup {

        @Test
        @DisplayName("inflates runs with their classes, in order")
        void inflates() {
            var widgets = Widgets.inflater().inflateAll(KdlParser.parse("""
                    rich-text id="order" {
                        run "Order" class="keyword"
                        run ": Reset the power of a unit."
                    }
                    """));

            var rich = assertInstanceOf(RichText.class, widgets.getFirst());
            assertEquals("order", rich.attributes().id());
            assertEquals(2, rich.runs().size());
            assertEquals("Order", rich.runs().get(0).text());
            assertEquals(Set.of("keyword"), rich.runs().get(0).classes());
            assertEquals(": Reset the power of a unit.", rich.runs().get(1).text());
            assertEquals(Set.of(), rich.runs().get(1).classes());
            assertEquals(
                    new RichText(new Run("Order", Set.of("keyword")), new Run(": Reset the power of a unit.")).runs(),
                    rich.runs());
        }

        @Test
        @DisplayName("refuses a child that is not a run, and words written as an argument")
        void refusesOtherChildren() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> Widgets.inflater().inflateAll(KdlParser.parse("rich-text { text \"no\" }")));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> Widgets.inflater().inflateAll(KdlParser.parse("rich-text \"no\"")));
        }
    }

    @Nested
    @DisplayName("semantics")
    class Semantics {

        @Test
        @DisplayName("its name is the words end to end, with no trace of the styles")
        void accessibleName() {
            assertEquals(Role.TEXT, sentence().role());
            assertEquals(GIVE + BLEEDING + REST, sentence().accessibleName());

            var column = new Column(List.of(sentence()), Attributes.NONE.id("column"));
            try (var session =
                    Offscreen.of(400, 300).stylesheets(sheets(KEYWORD)).session(column)) {
                session.frame();
                assertNotNull(session.byRole(Role.TEXT, GIVE + BLEEDING + REST).orElse(null));
            }
        }

        @Test
        @DisplayName("each run is a node of its own under the paragraph")
        void runsAreNodes() {
            var tree = new ElementTree(sentence());
            tree.flush();
            var root = tree.root();

            assertEquals("rich-text", root.type());
            assertEquals(3, root.children().size());
            assertEquals("run", root.children().get(1).type());
            assertEquals(Set.of("keyword"), root.children().get(1).classes());
        }
    }
}
