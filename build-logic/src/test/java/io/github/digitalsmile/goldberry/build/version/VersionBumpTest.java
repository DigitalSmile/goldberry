package io.github.digitalsmile.goldberry.build.version;

import io.github.digitalsmile.goldberry.build.repository.Repository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Year;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("the bump that follows a tag")
class VersionBumpTest {

    /** A file shaped like the real one: four comment lines, then the declaration. */
    private static final String PROPERTIES = """
            # The release line being worked towards, as YEAR.RELEASE[.PATCH] (ADR-0333).
            # Never a -SNAPSHOT.
            goldberryVersion=2026.1

            org.gradle.parallel=true
            """;

    @Nested
    @DisplayName("the arithmetic")
    class Arithmetic {

        @Test
        @DisplayName("a release line moves to the next release of its year")
        void nextRelease() {
            var bump = VersionBump.of(PROPERTIES, Year.of(2026));
            assertAll(
                    () -> assertEquals(new CalendarVersion(2026, 1, 0), bump.from()),
                    () -> assertEquals(new CalendarVersion(2026, 2, 0), bump.to()),
                    () -> assertEquals("2026.1 -> 2026.2", bump.toString()));
        }

        @Test
        @DisplayName("a new year restarts the count, which is why the year is a parameter")
        void nextYear() {
            assertEquals(new CalendarVersion(2027, 1, 0), VersionBump.of(PROPERTIES, Year.of(2027)).to());
        }

        @Test
        @DisplayName("a patch line bumps its patch and never its release")
        void patchStaysOnItsLine() {
            // The decision this test exists for: a `release/2026.1` branch that
            // moved to 2026.2 would claim the next feature release from a
            // maintenance branch.
            var properties = PROPERTIES.replace("2026.1", "2026.1.1");
            assertEquals(
                    new CalendarVersion(2026, 1, 2),
                    VersionBump.of(properties, Year.of(2027)).to());
        }
    }

    @Nested
    @DisplayName("the rewrite")
    class Rewrite {

        @Test
        @DisplayName("keeps every comment and every other property")
        void onlyTheValueMoves() {
            var rewritten = VersionBump.of(PROPERTIES, Year.of(2026)).properties();
            assertEquals(PROPERTIES.replace("goldberryVersion=2026.1", "goldberryVersion=2026.2"), rewritten);
        }

        @Test
        @DisplayName("what it writes is what the build then reads")
        void theBuildAgrees() {
            // The round trip is the point: a rewrite the version parser refuses
            // would leave master unbuildable, which is a worse outcome than the
            // forgotten bump this replaces.
            var bump = VersionBump.of(PROPERTIES, Year.of(2026));
            var declared = bump.properties().lines()
                    .filter(line -> line.startsWith(VersionBump.PROPERTY + "="))
                    .map(line -> line.substring(VersionBump.PROPERTY.length() + 1))
                    .findFirst()
                    .orElseThrow();
            assertEquals(bump.to(), CalendarVersion.parse(declared));
        }

        @Test
        @DisplayName("the branch is named for where it is going")
        void branchName() {
            assertEquals("bump/2026.2", VersionBump.of(PROPERTIES, Year.of(2026)).branch());
        }
    }

    @Nested
    @DisplayName("what it refuses")
    class Refusals {

        @Test
        @DisplayName("a file with no declaration")
        void noDeclaration() {
            var thrown = assertThrows(
                    IllegalArgumentException.class,
                    () -> VersionBump.of("org.gradle.parallel=true\n", Year.of(2026)));
            assertTrue(thrown.getMessage().contains("goldberryVersion"), thrown.getMessage());
        }

        @Test
        @DisplayName("a file that declares it twice, rather than picking one")
        void twoDeclarations() {
            var thrown = assertThrows(
                    IllegalArgumentException.class,
                    () -> VersionBump.of(PROPERTIES + "goldberryVersion=2026.5\n", Year.of(2026)));
            assertTrue(thrown.getMessage().contains("twice"), thrown.getMessage());
        }

        @Test
        @DisplayName("a commented-out declaration is not one")
        void commentedOut() {
            // The anchor in the pattern is what makes this an error rather than a
            // bump of the comment.
            assertThrows(
                    IllegalArgumentException.class,
                    () -> VersionBump.of("# goldberryVersion=2026.1\n", Year.of(2026)));
        }

        @Test
        @DisplayName("a snapshot suffix, which has one author")
        void snapshot() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> VersionBump.of(PROPERTIES.replace("2026.1", "2026.1-SNAPSHOT"), Year.of(2026)));
        }
    }

    @Test
    @DisplayName("the repository's own gradle.properties can be bumped")
    void theRealFile() {
        // The drift guard half: this is the file the workflow will hand the task,
        // so a property renamed or a comment rewritten into the declaration's line
        // fails here rather than on release day.
        var bump = VersionBump.of(Repository.read("gradle.properties"), Year.now());
        assertAll(
                () -> assertTrue(bump.to().compareTo(bump.from()) > 0, bump + " does not move forward"),
                () -> assertTrue(bump.branch().startsWith("bump/"), bump.branch()));
    }
}
