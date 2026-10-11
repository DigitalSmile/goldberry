package dev.goldberry.example.ui.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.example.ui.search.FuzzyMatcher.Span;

@DisplayName("one word of a query against a piece of text")
class FuzzyMatcherTest {

    private static int score(String term, String text) {
        return FuzzyMatcher.match(term, text, true).orElseThrow().score();
    }

    @Test
    @DisplayName("ranks the whole above a prefix, a prefix above a word start, a word start above anywhere")
    void tiers() {
        var whole = score("tabs", "Tabs");
        var prefix = score("tab", "Tables");
        var word = score("tab", "Closable tabs");
        var anywhere = score("tab", "Stable");
        var initials = score("ta", "Text area");

        assertTrue(whole > prefix, whole + " > " + prefix);
        assertTrue(prefix > word, prefix + " > " + word);
        assertTrue(word > anywhere, word + " > " + anywhere);
        assertTrue(anywhere > initials, anywhere + " > " + initials);
    }

    @Test
    @DisplayName("ignores case, and marks what matched")
    void spans() {
        var match = FuzzyMatcher.match("SLIDER", "A vertical slider", true).orElseThrow();

        assertEquals(List.of(new Span(11, 17)), match.spans());
    }

    @Test
    @DisplayName("finds initials, each at the start of a word")
    void initials() {
        var match = FuzzyMatcher.match("ta", "Text area", true).orElseThrow();

        assertEquals(List.of(new Span(0, 1), new Span(5, 6)), match.spans());
    }

    @Test
    @DisplayName("does not match letters scattered through the middle of words")
    void noScatter() {
        assertEquals(Optional.empty(), FuzzyMatcher.match("bn", "Button", true));
        assertEquals(Optional.empty(), FuzzyMatcher.match("ta", "Text area", false), "initials only when asked");
    }

    @Test
    @DisplayName("matches nothing for a blank word or an empty text")
    void blanks() {
        assertEquals(Optional.empty(), FuzzyMatcher.match(" ", "Tabs", true));
        assertEquals(Optional.empty(), FuzzyMatcher.match("tabs", "", true));
    }

    @Test
    @DisplayName("prefers the earlier of two places a word could start")
    void earlierIsBetter() {
        assertTrue(score("bar", "Scroll bar") > score("bar", "The scroll bar"));
    }
}
