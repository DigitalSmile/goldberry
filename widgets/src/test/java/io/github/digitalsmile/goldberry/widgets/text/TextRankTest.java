package io.github.digitalsmile.goldberry.widgets.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.Widgets;

/// `text style="body"` — [ADR-0381], and `docs/ARCHITECTURE.md` §17.1's last
/// spelling disagreement.
///
/// §2 asks for a `style=` attribute over §1.4's ranks; what shipped was
/// `class=`, which is the same thing spelled as CSS spells it. Both work now,
/// and what these assert is that they are one mechanism rather than two: a rule
/// written `text.title` matches either.
class TextRankTest {

    private static Text inflate(String kdl) {
        return (Text) Widgets.inflater().inflateAll(KdlParser.parse(kdl)).getFirst();
    }

    @Nested
    @DisplayName("the markup spelling")
    class Markup {

        @Test
        @DisplayName("`style=` is the class the stylesheet already had a rule for")
        void styleIsAClass() {
            assertEquals(
                    Set.of("title"),
                    inflate("text style=\"title\" \"Hello\"").attributes().classes());
        }

        @Test
        @DisplayName("and the two spellings are the same document")
        void bothSpellings() {
            assertEquals(
                    inflate("text class=\"caption\" \"Hello\"").attributes().classes(),
                    inflate("text style=\"caption\" \"Hello\"").attributes().classes());
        }

        @Test
        @DisplayName("a rank written either way keeps whatever classes were written beside it")
        void classesAreKept() {
            var text = inflate("text style=\"body-strong\" class=\"muted\" \"Hello\"");
            assertTrue(text.attributes().classes().contains("body-strong"));
            assertTrue(text.attributes().classes().contains("muted"), "the class beside it survives");
        }

        @Test
        @DisplayName("a rank nobody has is refused where it is written")
        void unknownRank() {
            // The whole reason `style=` is checked and `class=` is not: a class is
            // an open vocabulary and a rank is a closed one, so a typo in a rank
            // is a mistake rather than a rule that has not been written yet.
            // The type is named. `Exception.class` stood here, which would have
            // passed on a NullPointerException from the inflater's own scaffolding
            // (the 2026-09-18 review, §6); 52 sibling sites in this module name
            // the type they mean.
            var thrown =
                    assertThrows(IllegalArgumentException.class, () -> inflate("text style=\"subtitle\" \"Hello\""));
            assertTrue(
                    message(thrown).contains("type rank"),
                    () -> "the error says what the ranks are: " + message(thrown));
        }
    }

    private static String message(Throwable thrown) {
        for (var current = thrown; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains("type rank")) {
                return current.getMessage();
            }
        }
        return String.valueOf(thrown.getMessage());
    }

    @Nested
    @DisplayName("the Java spelling")
    class Java {

        @Test
        @DisplayName("`style(Rank)` is the same class again")
        void style() {
            assertEquals(
                    Set.of("heading"),
                    new Text("Hello").style(TextRank.HEADING).attributes().classes());
        }

        @Test
        @DisplayName("and it keeps the classes the text already had")
        void keepsClasses() {
            var text = new Text("Hello", Attributes.NONE.classes("muted")).style(TextRank.BODY);
            assertEquals(Set.of("muted", "body"), text.attributes().classes());
        }
    }

    @Nested
    @DisplayName("the rank itself")
    class Ranks {

        @Test
        @DisplayName("is spelled as CSS spells it")
        void cssSpelling() {
            assertEquals("body-strong", TextRank.BODY_STRONG.cssClass());
            assertEquals("mono", TextRank.MONO.cssClass());
        }

        @Test
        @DisplayName("and is read in either spelling")
        void parsedEitherWay() {
            assertEquals(TextRank.BODY_STRONG, TextRank.of("body-strong"));
            assertEquals(TextRank.BODY_STRONG, TextRank.of("BODY_STRONG"));
            assertEquals(TextRank.CAPTION, TextRank.of(" caption "));
        }

        @Test
        @DisplayName("the six are §1.4's six")
        void theScale() {
            // If a seventh is added here it has to be added to `controls.css`
            // too, or `style=` would name a rank with no rule behind it.
            assertEquals(7, TextRank.values().length, "six ranks and `body-strong`, which is `body` at 600");
        }
    }
}
