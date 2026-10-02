package dev.goldberry.build.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code NOTICE} names every component {@code THIRD-PARTY-NOTICES.md} says a
 * published artifact carries.
 *
 * <p>{@code ./gradlew checkLicenses} holds the notices and {@code licenses/} to
 * each other; nothing held {@code NOTICE} to either. It is the file that
 * section 4(d) of the Apache-2.0 licence makes a redistributor pass on, and the
 * one every jar carries in {@code META-INF}, and it went without FFmpeg and
 * dav1d for as long as
 * {@code goldberry-ffmpeg-natives} existed, because step 3 of "Adding a
 * dependency" is a step a person has to remember.
 *
 * <p>Only the three tables of things that ship are read. The showcase's table
 * is an application's, not an artifact's, and "Not distributed" is what it says.
 */
@DisplayName("NOTICE")
class NoticeDisclosureTest {

    /** The sections whose rows are redistributed in a published artifact. */
    private static final List<String> SHIPPED_SECTIONS =
            List.of("Statically linked", "Dynamically linked", "Embedded in the published jars");

    /** A table row whose first cell links a licence file: {@code | [Name](licenses/x.txt) |}. */
    private static final Pattern ROW = Pattern.compile("^\\| \\[([^]]+)]\\(licenses/", Pattern.MULTILINE);

    /**
     * The names in the shipped sections' tables. A section runs from its
     * {@code ## } heading to the next; its {@code ### } subsections are part of it.
     */
    static List<String> shippedComponents(String notices) {
        return Stream.of(notices.split("\n## "))
                .filter(section -> SHIPPED_SECTIONS.stream().anyMatch(section::startsWith))
                .flatMap(section -> ROW.matcher(section).results().map(match -> match.group(1)))
                .toList();
    }

    /**
     * Whether {@code notice} lists {@code component}: an indented line that starts
     * with the name and then a space, the form every entry there has. A name that
     * only appears in prose or inside a URL, as dav1d does in its upstream's, is
     * not a listing.
     */
    static boolean lists(String notice, String component) {
        return notice.lines().anyMatch(line -> line.startsWith("  ") && line.strip().startsWith(component + " "));
    }

    @Test
    @DisplayName("counts an indented entry as a listing, and prose or a URL as none")
    void listing() {
        var notice = """
                FFmpeg's libraries are replaceable.

                  Beta           LGPL              https://example.org/dav1d
                """;
        assertTrue(lists(notice, "Beta"));
        assertFalse(lists(notice, "FFmpeg"));
        assertFalse(lists(notice, "dav1d"));
    }

    @Test
    @DisplayName("reads the rows of the shipped tables and no others")
    void readsTheShippedTables() {
        var notices = """
                # Third-party notices

                ## Statically linked into `libgoldberry`

                | Component | Licence |
                |---|---|
                | [Alpha](licenses/alpha.txt) | MIT |

                ## Dynamically linked, in `x-natives`

                | [Beta](licenses/beta.txt) | LGPL |

                ### A subsection with prose, and [a link](licenses/beta.txt) not in a row

                ## Embedded in the published jars

                | [Gamma Sans](licenses/gamma.txt) | OFL |

                ## Compiled into the showcase

                | [Delta](licenses/delta.txt) | ISC |

                ## Not distributed

                | JUnit | EPL |
                """;
        assertEquals(List.of("Alpha", "Beta", "Gamma Sans"), shippedComponents(notices));
    }

    @Test
    @DisplayName("names every component a published artifact carries")
    void namesEveryShippedComponent() {
        var notices = Repository.read("THIRD-PARTY-NOTICES.md");
        var notice = Repository.read("NOTICE");

        var shipped = shippedComponents(notices);
        // A guard that parsed nothing would pass over everything.
        assertTrue(shipped.size() >= 10, () -> "read only " + shipped + " from THIRD-PARTY-NOTICES.md");

        var missing = shipped.stream().filter(name -> !lists(notice, name)).toList();
        assertEquals(
                List.of(),
                missing,
                "NOTICE does not name these, which THIRD-PARTY-NOTICES.md says ship "
                        + "(step 3 of \"Adding a dependency\")");
    }
}
