package dev.goldberry.css.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import dev.goldberry.css.conformance.Outcome.Kind;

/// What the stylesheet engine reads and what it does not, one row per probe.
///
/// The rows are in `rows.txt` beside this class, grouped as the CSS support
/// analysis groups them. Each records what the engine does **today**, and a row
/// whose feature is missing names the plan item that changes it. When an item
/// lands, its rows fail here, and are moved to their new outcome in the same
/// commit. A row that fails with no item to explain it is a regression.
class CssConformanceTest {

    static Stream<Arguments> rows() {
        return Row.all().stream().map(row -> Arguments.argumentSet(row.toString(), row));
    }

    @ParameterizedTest
    @MethodSource("rows")
    @DisplayName("the engine does with each row what the table says")
    void asRecorded(Row row) {
        var actual = Probe.of(row);
        assertEquals(row.expected(), actual, () -> row + " (plan: " + row.plan() + ")");
        if (actual == Outcome.REFUSED) {
            assertTrue(
                    Probe.strictRefusesAsUnsupported(row.css()),
                    () -> row + " is dropped by a lenient parse, so a strict one refuses it as unsupported");
        }
    }

    @Nested
    @DisplayName("the table itself")
    class Table {

        @ParameterizedTest
        @MethodSource("dev.goldberry.css.conformance.CssConformanceTest#rows")
        @DisplayName("each row is well formed: a gap names its plan item, a supported row none")
        void wellFormed(Row row) {
            assertNull(row.malformation(), row::toString);
        }

        @Test
        @DisplayName("no row is written twice")
        void noDuplicates() {
            var seen = new HashSet<String>();
            var duplicates = Row.all().stream()
                    .filter(row -> !seen.add(row.kind() + " " + row.css()))
                    .toList();
            assertEquals(List.of(), duplicates);
        }

        @Test
        @DisplayName("probes both kinds and every outcome a gap can have")
        void covers() {
            var outcomes = Row.all().stream().map(Row::expected).distinct().toList();
            for (var outcome : Outcome.values()) {
                if (outcome != Outcome.MALFORMED) {
                    assertTrue(outcomes.contains(outcome), () -> "no row comes out " + outcome);
                }
            }
        }

        @Test
        @DisplayName("refuses a line it cannot read rather than skipping it")
        void refusesBadLines() {
            assertThrows(IllegalArgumentException.class, () -> Row.parse("decl | color: red | APPLIED", 1));
            assertThrows(IllegalArgumentException.class, () -> Row.parse("rule | a {} | PARSED | -", 1));
            assertThrows(IllegalArgumentException.class, () -> Row.parse("decl | color: red | WORKS | -", 1));
        }

        @Test
        @DisplayName("keeps a | inside CSS as CSS")
        void pipesInsideCss() {
            var row = Row.parse("sheet | ns|a { color: red } | REFUSED | by-design", 1);
            assertEquals("ns|a { color: red }", row.css());
            assertEquals(Kind.SHEET, row.kind());
        }

        @Test
        @DisplayName("finds a row that contradicts itself")
        void malformations() {
            assertTrue(Row.parse("decl | color: red | APPLIED | CSS-14", 1).malformation() != null);
            assertTrue(Row.parse("decl | color: hsl(0 0% 0%) | DROPPED | -", 1).malformation() != null);
            assertTrue(
                    Row.parse("decl | color: hsl(0 0% 0%) | DROPPED | soon", 1).malformation() != null);
            assertTrue(Row.parse("decl | a: b | REFUSED | unplanned", 1).malformation() != null);
            assertTrue(Row.parse("decl | a: b | IGNORED | CSS-15", 1).malformation() != null);
            assertNull(Row.parse("decl | a: b | IGNORED | CSS-15 | a: c", 1).malformation());
        }
    }
}
