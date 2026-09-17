package io.github.digitalsmile.goldberry.widgets.panel.timeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.semantics.Role;
import io.github.digitalsmile.goldberry.widgets.Widgets;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// §10's `timeline` — events along an axis, and what tells it from a list
/// with dots ([ADR-0345]).
class TimelineTest {

    private static Element listNode(Timeline timeline) {
        return new ElementTree(timeline).root().children().getFirst();
    }

    private static List<Element> entries(Timeline timeline) {
        return listNode(timeline).children();
    }

    private static Timeline three() {
        return new Timeline(
                new Entry("Pushed", new Text("Three commits.")).at("09:12"),
                new Entry("Built").at("09:15").colour(0xFFA3BE8C),
                new Entry("Deployed"));
    }

    /// The CSS types down one entry's rail: the marker, and the line if any.
    private static List<String> rail(Element entry) {
        return entry.children().stream()
                .filter(child -> "timeline-rail".equals(child.type()))
                .findFirst()
                .orElseThrow()
                .children()
                .stream()
                .map(Element::type)
                .toList();
    }

    private static List<String> types(Element element) {
        return element.children().stream().map(Element::type).toList();
    }

    @Nested
    @DisplayName("the list")
    class TheList {

        @Test
        @DisplayName("the list is the styled node, and the composition styles nothing")
        void theListCarriesTheCssType() {
            var root = new ElementTree(three().id("log")).root();
            var painted = root.children().getFirst();

            assertInstanceOf(Timeline.class, root.widget());
            assertInstanceOf(TimelineList.class, painted.widget());
            assertEquals("timeline", painted.type());
            assertEquals("log", painted.id());
        }

        @Test
        @DisplayName("one entry per event, each a rail beside a side")
        void anEntryIsARailAndASide() {
            var rows = entries(three());

            assertEquals(3, rows.size());
            assertEquals(List.of("timeline-rail", "timeline-side"), types(rows.getFirst()));
        }

        @Test
        @DisplayName("the line runs on from every marker but the last")
        void theLineStopsAtTheLastEvent() {
            var rows = entries(three());

            assertEquals(List.of("timeline-marker-cell", "timeline-line"), rail(rows.get(0)));
            assertEquals(List.of("timeline-marker-cell", "timeline-line"), rail(rows.get(1)));
            assertEquals(List.of("timeline-marker-cell"), rail(rows.get(2)), "the story is over");
        }

        @Test
        @DisplayName("pending runs the line past the last event to an unfilled marker")
        void pendingIsATrailingMarker() {
            var rows = entries(three().pending(true));

            assertEquals(4, rows.size());
            assertEquals(List.of("timeline-marker-cell", "timeline-line"), rail(rows.get(2)), "and then…");
            var trailing = (Entry) rows.get(3).widget();
            assertTrue(trailing.placement().pending());
            assertTrue(trailing.classes().contains("pending"));
            assertEquals(List.of("timeline-marker-cell"), rail(rows.get(3)));
            assertTrue(rows.get(3).children().get(1).children().isEmpty(), "no words on the pending marker");
            assertNull(trailing.accessibleName(), "and nothing to announce");
        }

        @Test
        @DisplayName("horizontal and alternate are classes on the list")
        void directionAndAlignAreClasses() {
            var turned =
                    listNode(three().direction(Timeline.Direction.HORIZONTAL).align(Timeline.Align.ALTERNATE));

            assertTrue(turned.classes().containsAll(List.of("horizontal", "alternate")));
            assertFalse(listNode(three()).classes().contains("horizontal"));
        }

        @Test
        @DisplayName("alternating gives every entry a side on both sides, and fills the odd ones across")
        void alternateCrossesTheAxis() {
            var rows = entries(three().align(Timeline.Align.ALTERNATE));

            for (var row : rows) {
                assertEquals(List.of("timeline-side", "timeline-rail", "timeline-side"), types(row));
            }
            // The words are in the first side, then the second, then the first.
            assertEquals(1, rows.get(0).children().get(0).children().size());
            assertEquals(0, rows.get(0).children().get(2).children().size());
            assertEquals(0, rows.get(1).children().get(0).children().size());
            assertEquals(1, rows.get(1).children().get(2).children().size());
            assertTrue(((Entry) rows.get(1).widget()).classes().contains("end"));
            assertFalse(((Entry) rows.get(2).widget()).classes().contains("end"));
        }
    }

    @Nested
    @DisplayName("an entry")
    class AnEntry {

        @Test
        @DisplayName("its words are a head and, when there is a body, the content under it")
        void headAndContent() {
            var rows = entries(three());
            var body = rows.get(0).children().get(1).children().getFirst();

            assertEquals("timeline-body", body.type());
            assertEquals(List.of("timeline-head", "timeline-content"), types(body));
            assertEquals(
                    List.of("timeline-label", "timeline-time"),
                    types(body.children().getFirst()));

            var bare = rows.get(2).children().get(1).children().getFirst();
            assertEquals(List.of("timeline-head"), types(bare), "no body, no content part");
            assertEquals(List.of("timeline-label"), types(bare.children().getFirst()), "no time, no time part");
        }

        @Test
        @DisplayName("the marker takes the entry's colour, and says so when it holds an icon")
        void marker() {
            var rows = entries(three());
            var cell = rows.get(1).children().getFirst().children().getFirst();
            var coloured = (TimelineMarker) cell.children().getFirst().widget();

            assertEquals(0xFFA3BE8C, coloured.colour());
            assertFalse(coloured.classes().contains("icon"));
        }

        @Test
        @DisplayName("it is a row of the list, named by its label and its time")
        void semantics() {
            var pushed = (Entry) entries(three()).getFirst().widget();
            var deployed = (Entry) entries(three()).get(2).widget();

            assertEquals(Role.ROW, pushed.role());
            assertEquals("Pushed, 09:12", pushed.accessibleName());
            assertEquals("Deployed", deployed.accessibleName());
            assertEquals(Role.GROUP, ((TimelineList) listNode(three()).widget()).role());
        }

        @Test
        @DisplayName("an entry needs a word")
        void needsALabel() {
            assertThrows(IllegalArgumentException.class, () -> new Entry(""));
        }
    }

    @Nested
    @DisplayName("markup")
    class Markup {

        @Test
        @DisplayName("a timeline and its entries inflate with what the document may say")
        void inflates() {
            var timeline = (Timeline)
                    Widgets.inflater().inflateAll(KdlParser.parse("""
                            timeline direction="horizontal" align="alternate" pending=#true id="log" {
                                entry time="09:12" "Pushed" { text "Three commits." }
                                entry colour="#a3be8c" "Built"
                                entry color="#bf616a" "Failed"
                            }
                            """)).getFirst();

            assertEquals(Timeline.Direction.HORIZONTAL, timeline.direction());
            assertEquals(Timeline.Align.ALTERNATE, timeline.align());
            assertTrue(timeline.pending());
            assertEquals("log", timeline.attributes().id());

            var written = timeline.rawEntries().stream().map(Entry.class::cast).toList();
            assertEquals("09:12", written.get(0).time());
            assertEquals(1, written.get(0).body().size());
            assertEquals(0xFFA3BE8C, written.get(1).colour());
            assertEquals(0xFFBF616A, written.get(2).colour(), "either spelling of colour");
            assertEquals(Entry.Placement.NONE, written.get(0).placement(), "a document cannot place an entry");
        }
    }
}
