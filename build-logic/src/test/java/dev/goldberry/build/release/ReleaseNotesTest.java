package dev.goldberry.build.release;

import java.util.List;

import dev.goldberry.build.version.CalendarVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A release's notes, from its changelog section and the template. */
@DisplayName("ReleaseNotes")
class ReleaseNotesTest {

    private static final String CHANGELOG = """
            # Changelog

            What changed.

            <!--
            ## 2026.9 — YYYY-MM-DD

            ### Added
            -->

            ## Unreleased

            - Something on master.

            ## 2026.3 — 2026-11-01

            ### Fixed

            - A seek bar that jumped back.

            ## 2026.2 — 2026-10-02

            Welcome.
            """;

    @Nested
    @DisplayName("the sections")
    class Sections {

        @Test
        @DisplayName("are read in order, by the heading's first word, with nothing from before the first")
        void inOrder() {
            assertEquals(List.of("Unreleased", "2026.3", "2026.2"),
                    ReleaseNotes.sections(CHANGELOG).stream().map(ReleaseNotes.Section::name).toList());
        }

        @Test
        @DisplayName("leave out the template a comment holds, though it looks like a section")
        void notTheTemplate() {
            assertTrue(ReleaseNotes.sections(CHANGELOG).stream().noneMatch(section -> section.name().equals("2026.9")));
        }

        @Test
        @DisplayName("hold the text under the heading, trimmed, and up to the next one")
        void body() {
            var sections = ReleaseNotes.sections(CHANGELOG);
            assertAll(
                    () -> assertEquals("### Fixed\n\n- A seek bar that jumped back.", sections.get(1).body()),
                    () -> assertEquals("Welcome.", sections.get(2).body()));
        }

        @Test
        @DisplayName("name a version, or Unreleased, and nothing else")
        void namesAVersion() {
            assertAll(
                    () -> assertTrue(ReleaseNotes.sections(CHANGELOG).getFirst().version().isEmpty()),
                    () -> assertEquals(CalendarVersion.parse("2026.3"),
                            ReleaseNotes.sections(CHANGELOG).get(1).version().orElseThrow()),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> ReleaseNotes.sections("## Next\n\nSoon.\n")));
        }
    }

    @Nested
    @DisplayName("the notes for a version")
    class Notes {

        @Test
        @DisplayName("are its section, whatever comes before and after it")
        void itsSection() {
            assertEquals("Welcome.", ReleaseNotes.of(CHANGELOG, "2026.2").changes());
        }

        @Test
        @DisplayName("are refused for a version with no section, and say to rename Unreleased when there is one")
        void noSection() {
            var error = assertThrows(IllegalArgumentException.class, () -> ReleaseNotes.of(CHANGELOG, "2026.4"));
            assertAll(
                    () -> assertTrue(error.getMessage().contains("`## 2026.4`"), error.getMessage()),
                    () -> assertTrue(error.getMessage().contains("rename `## Unreleased`"), error.getMessage()));
        }

        @Test
        @DisplayName("are refused when the section is empty")
        void emptySection() {
            assertThrows(IllegalArgumentException.class, () -> ReleaseNotes.of("## 2026.2\n\n## 2026.1\n\nOld.\n", "2026.2"));
        }

        @Test
        @DisplayName("are refused for something that is not a version")
        void notAVersion() {
            assertThrows(IllegalArgumentException.class, () -> ReleaseNotes.of(CHANGELOG, "2026.2-SNAPSHOT"));
        }
    }

    @Nested
    @DisplayName("the rendered body")
    class Render {

        @Test
        @DisplayName("puts the section where the template says, and the version wherever it is named")
        void fillsTheTemplate() {
            var body = ReleaseNotes.of(CHANGELOG, "2026.3")
                    .render("{{changes}}\n\nbom:{{version}} and v{{version}}\n");
            assertEquals("### Fixed\n\n- A seek bar that jumped back.\n\nbom:2026.3 and v2026.3\n", body);
        }

        @Test
        @DisplayName("is refused from a template with nowhere for the changes")
        void needsTheChanges() {
            assertThrows(IllegalArgumentException.class,
                    () -> ReleaseNotes.of(CHANGELOG, "2026.2").render("Goldberry {{version}}\n"));
        }
    }
}
