package dev.goldberry.kdl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UnreadPropertyTest {

    /// What a factory built: its node's name and what it read.
    private record Built(String type, List<String> values, List<Built> children) {}

    /// An inflater whose `row` reads `id` and nothing else, and whose `chart`
    /// reads its `point` children's `x` after their own factory, which reads
    /// nothing, has run.
    private static KdlInflater<Built> inflater() {
        var inflater = new KdlInflater<Built>();
        inflater.register("row", (node, children) -> {
            var id = node.stringProperty("id");
            return new Built("row", id == null ? List.of() : List.of(id), children);
        });
        inflater.register("point", (node, children) -> new Built("point", List.of(), children));
        inflater.register("chart", (node, children) -> {
            var xs = new ArrayList<String>();
            for (var point : node.childrenNamed("point")) {
                xs.add(String.valueOf(point.numberProperty("x", 0)));
            }
            return new Built("chart", xs, children);
        });
        return inflater;
    }

    private static KdlNode one(String source) {
        return KdlParser.parse(source).getFirst();
    }

    @Nested
    @DisplayName("refusing")
    class Refusing {

        @Test
        @DisplayName("an inflater refuses until told otherwise")
        void refusesByDefault() {
            assertEquals(UnreadPolicy.REFUSE, new KdlInflater<Built>().unread());
        }

        @Test
        @DisplayName("a property nothing reads is refused at its node, naming what the node did read")
        void namesTheNodeAndItsReads() {
            var refused =
                    assertThrows(KdlSyntaxException.class, () -> inflater().inflate(one("""

                              row id="toolbar" gap=8
                            """)));

            assertEquals(2, refused.line());
            assertEquals(3, refused.column());
            assertTrue(refused.getMessage().startsWith("row at 2:3 ignores gap=8; it reads id"), refused.getMessage());
        }

        @Test
        @DisplayName("every unread property in the document is listed, in document order")
        void listsEveryOne() {
            var refused =
                    assertThrows(KdlSyntaxException.class, () -> inflater().inflateAll(KdlParser.parse("""
                            row gpa=8
                            row { row id="inner" colour="red" }
                            """)));

            var message = refused.getMessage();
            assertTrue(message.contains("row at 1:1 ignores gpa=8"), message);
            assertTrue(message.contains("also row at 2:7 ignores colour=\"red\"; it reads id"), message);
            assertEquals(1, refused.line());
        }

        @Test
        @DisplayName("a property asked for and absent is not a finding")
        void askingForAnAbsentPropertyIsFine() {
            var built = inflater().inflate(one("row"));
            assertEquals(List.of(), built.values());
        }

        @Test
        @DisplayName("two equal nodes are two places an author wrote")
        void equalNodesAreTrackedApart() {
            var calls = new AtomicInteger();
            var inflater = new KdlInflater<Built>().register("button", (node, children) -> {
                if (calls.getAndIncrement() == 0) {
                    node.stringProperty("press");
                }
                return new Built("button", List.of(), children);
            });

            var refused = assertThrows(KdlSyntaxException.class, () -> inflater.inflateAll(KdlParser.parse("""
                    button press="save"
                    button press="save"
                    """)));
            assertEquals(2, refused.line());
        }
    }

    @Nested
    @DisplayName("what counts as a read")
    class Reads {

        @Test
        @DisplayName("a parent reading its children after their factories ran reads them")
        void aParentReadsItsChildren() {
            var built = inflater().inflate(one("chart { point x=1; point x=2 }"));
            assertEquals(List.of("1.0", "2.0"), built.values());
        }

        @Test
        @DisplayName("every typed accessor counts")
        void everyAccessorCounts() {
            var inflater = new KdlInflater<Built>().register("all", (node, children) -> {
                node.property("a");
                node.stringProperty("b");
                node.booleanProperty("c");
                node.flagProperty("d");
                node.numberProperty("e", 0);
                return new Built("all", List.of(), children);
            });

            inflater.inflate(one("all a=1 b=\"x\" c=#true d=#false e=2"));
        }

        @Test
        @DisplayName("a lookup in the properties map reads that property alone")
        void aLookupReadsOne() {
            var inflater = new KdlInflater<Built>().register("box", (node, children) -> {
                node.properties().get("width");
                node.properties().containsKey("height");
                return new Built("box", List.of(), children);
            });

            inflater.inflate(one("box width=1 height=2"));
            var refused = assertThrows(KdlSyntaxException.class, () -> inflater.inflate(one("box width=1 depth=3")));
            assertTrue(refused.getMessage().contains("ignores depth=3; it reads width, height"), refused.getMessage());
        }

        @Test
        @DisplayName("walking the properties map reads all of them")
        void walkingReadsAll() {
            var inflater = new KdlInflater<Built>()
                    .register(
                            "data",
                            (node, children) -> new Built(
                                    "data", List.copyOf(node.properties().keySet()), children));

            assertEquals(
                    List.of("a", "b"), inflater.inflate(one("data a=1 b=2")).values());
        }

        @Test
        @DisplayName("a read before the inflation does not count for it")
        void readsOutsideDoNotCount() {
            var node = one("row gap=8");
            node.stringProperty("gap");
            assertThrows(KdlSyntaxException.class, () -> inflater().inflate(node));
        }

        @Test
        @DisplayName("each inflation starts from nothing read")
        void inflationsDoNotShareReads() {
            var node = one("row gap=8");
            var reading = new KdlInflater<Built>()
                    .register("row", (n, children) -> new Built("row", List.of(n.stringProperty("gap")), children));
            reading.inflate(node);

            assertThrows(KdlSyntaxException.class, () -> inflater().inflate(node));
        }

        @Test
        @DisplayName("outside an inflation the properties map is the node's own")
        void noViewOutside() {
            var node = one("row gap=8");
            assertSame(node.properties(), node.properties());
        }
    }

    @Nested
    @DisplayName("the other policies")
    class Policies {

        @Test
        @DisplayName("WARN builds the document")
        void warnBuilds() {
            var built = inflater().unread(UnreadPolicy.WARN).inflate(one("row id=\"a\" gap=8"));
            assertEquals(List.of("a"), built.values());
        }

        @Test
        @DisplayName("IGNORE builds the document")
        void ignoreBuilds() {
            var inflater = inflater().unread(UnreadPolicy.IGNORE);
            assertEquals(UnreadPolicy.IGNORE, inflater.unread());
            assertEquals("row", inflater.inflate(one("row gap=8")).type());
        }
    }

    @Nested
    @DisplayName("describing")
    class Describing {

        @Test
        @DisplayName("a node that reads nothing says so")
        void readsNothing() {
            var property = new UnreadProperty("spacer", "width", KdlValue.of(4), List.of(), 3, 7);
            assertEquals("spacer at 3:7 ignores width=4; it reads no properties", property.describe());
        }
    }
}
