package io.github.digitalsmile.goldberry.build.version;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CalendarVersion")
class CalendarVersionTest {

    @Nested
    @DisplayName("parse")
    class Parse {

        @Test
        @DisplayName("reads a release")
        void readsARelease() {
            assertEquals(new CalendarVersion(2026, 1, 0), CalendarVersion.parse("2026.1"));
        }

        @Test
        @DisplayName("reads a patch")
        void readsAPatch() {
            assertEquals(new CalendarVersion(2027, 3, 2), CalendarVersion.parse("2027.3.2"));
        }

        @Test
        @DisplayName("tolerates the whitespace a properties file leaves")
        void stripsWhitespace() {
            assertEquals(new CalendarVersion(2026, 2, 0), CalendarVersion.parse(" 2026.2\t"));
        }

        @ParameterizedTest(name = "refuses \"{0}\"")
        @ValueSource(strings = {
                "", "2026", "2026.", "2026.0", "2026.1.0", "2026.01", "2026.1.01",
                "26.1", "2026.1-SNAPSHOT", "v2026.1", "0.1.0", "2025.1", "2026.1.1.1"})
        void refusesAnythingElse(String text) {
            assertThrows(IllegalArgumentException.class, () -> CalendarVersion.parse(text));
        }

        @Test
        @DisplayName("names the expected shape when it refuses")
        void explainsTheShape() {
            var error = assertThrows(IllegalArgumentException.class, () -> CalendarVersion.parse("0.1.0-SNAPSHOT"));
            assertTrue(error.getMessage().contains("2026.1"), error.getMessage());
        }
    }

    @Test
    @DisplayName("round-trips through its text, omitting a zero patch")
    void roundTrips() {
        for (var text : List.of("2026.1", "2026.12", "2031.2.7")) {
            assertEquals(text, CalendarVersion.parse(text).toString());
        }
    }

    @Test
    @DisplayName("refuses components the parser would never produce")
    void validatesComponents() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new CalendarVersion(2025, 1, 0)),
                () -> assertThrows(IllegalArgumentException.class, () -> new CalendarVersion(10_000, 1, 0)),
                () -> assertThrows(IllegalArgumentException.class, () -> new CalendarVersion(2026, 0, 0)),
                () -> assertThrows(IllegalArgumentException.class, () -> new CalendarVersion(2026, 1, -1)));
    }

    @Nested
    @DisplayName("what comes next")
    class Next {

        @Test
        @DisplayName("the next release in the same year counts up")
        void sameYear() {
            assertEquals(CalendarVersion.parse("2026.3"), CalendarVersion.parse("2026.2").nextRelease(Year.of(2026)));
        }

        @Test
        @DisplayName("a new year starts again at one")
        void newYear() {
            assertEquals(CalendarVersion.parse("2027.1"), CalendarVersion.parse("2026.3").nextRelease(Year.of(2027)));
        }

        @Test
        @DisplayName("a patch's next release drops the patch")
        void fromAPatch() {
            assertEquals(CalendarVersion.parse("2026.2"), CalendarVersion.parse("2026.1.4").nextRelease(Year.of(2026)));
        }

        @Test
        @DisplayName("a patch counts up on its line")
        void nextPatch() {
            var release = CalendarVersion.parse("2026.1");
            assertAll(
                    () -> assertEquals(CalendarVersion.parse("2026.1.1"), release.nextPatch()),
                    () -> assertEquals(CalendarVersion.parse("2026.1.2"), release.nextPatch().nextPatch()),
                    () -> assertFalse(release.isPatch()),
                    () -> assertTrue(release.nextPatch().isPatch()));
        }
    }

    @Test
    @DisplayName("orders by year, then release, then patch -- not as text")
    void ordersNumerically() {
        var versions = new ArrayList<>(List.of(
                CalendarVersion.parse("2026.10"),
                CalendarVersion.parse("2027.1"),
                CalendarVersion.parse("2026.2.1"),
                CalendarVersion.parse("2026.2"),
                CalendarVersion.parse("2026.9")));
        versions.sort(null);
        assertEquals(List.of("2026.2", "2026.2.1", "2026.9", "2026.10", "2027.1"),
                versions.stream().map(CalendarVersion::toString).toList());
    }
}
