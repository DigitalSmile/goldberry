package dev.goldberry.example.ui.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/// How well one word of a query matches one piece of text, and where.
///
/// Four tiers, best first, so a reader who types the start of a name finds it
/// above everything that merely contains the letters:
///
/// | Tier          | `tab` against      | Score            |
/// | ------------- | ------------------ | ---------------- |
/// | The whole     | `Tabs`             | 1000 − extra     |
/// | A prefix      | `Tables`           | 800 − extra      |
/// | A word start  | `Closable tabs`    | 600 − position   |
/// | Anywhere      | `Stable`           | 400 − position   |
/// | Initials      | `Text area box`    | 200 − gaps       |
///
/// The last tier is letters in order, each at the start of a word, which is
/// how `ta` finds Text area. Letters scattered through the middle of words are
/// not a match: they find everything and rank nothing.
///
/// Case-insensitive, and pure: the same arguments give the same answer.
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html#text-input).
public final class FuzzyMatcher {

    private FuzzyMatcher() {}

    /// A run of matched characters, `start` inclusive and `end` exclusive.
    public record Span(int start, int end) {

        public Span {
            if (start < 0 || end < start) {
                throw new IllegalArgumentException("a span runs forwards from zero: " + start + ".." + end);
            }
        }
    }

    /// A match: how good it is, and which characters it used.
    public record Match(int score, List<Span> spans) {

        public Match {
            spans = List.copyOf(spans);
        }
    }

    /// The best match of `term` in `text`, or empty.
    ///
    /// @param term one word of a query, already trimmed; blank matches nothing
    /// @param initials whether the initials tier is tried: worth it against a
    ///                 title and noise against a paragraph
    public static Optional<Match> match(String term, String text, boolean initials) {
        if (term.isBlank() || text.isEmpty()) {
            return Optional.empty();
        }
        var needle = term.toLowerCase(Locale.ROOT);
        var haystack = text.toLowerCase(Locale.ROOT);
        var extra = haystack.length() - needle.length();
        if (haystack.equals(needle)) {
            return Optional.of(new Match(1000, List.of(new Span(0, text.length()))));
        }
        if (haystack.startsWith(needle)) {
            return Optional.of(new Match(800 - Math.min(extra, 100), List.of(new Span(0, needle.length()))));
        }
        var wordStart = wordStart(haystack, needle);
        if (wordStart >= 0) {
            return Optional.of(new Match(
                    600 - Math.min(wordStart, 100), List.of(new Span(wordStart, wordStart + needle.length()))));
        }
        var anywhere = haystack.indexOf(needle);
        if (anywhere >= 0) {
            return Optional.of(
                    new Match(400 - Math.min(anywhere, 100), List.of(new Span(anywhere, anywhere + needle.length()))));
        }
        return initials && needle.length() > 1 ? initials(haystack, needle) : Optional.empty();
    }

    /// Where `needle` starts a word of `haystack`, or -1.
    private static int wordStart(String haystack, String needle) {
        var from = 0;
        while (true) {
            var at = haystack.indexOf(needle, from);
            if (at < 0) {
                return -1;
            }
            if (isWordStart(haystack, at)) {
                return at;
            }
            from = at + 1;
        }
    }

    /// `needle`'s letters in order, each starting a word of `haystack`.
    private static Optional<Match> initials(String haystack, String needle) {
        var spans = new ArrayList<Span>(needle.length());
        var from = 0;
        var gaps = 0;
        for (var i = 0; i < needle.length(); i++) {
            var wanted = needle.charAt(i);
            var found = -1;
            for (var at = from; at < haystack.length(); at++) {
                if (haystack.charAt(at) == wanted && isWordStart(haystack, at)) {
                    found = at;
                    break;
                }
            }
            if (found < 0) {
                return Optional.empty();
            }
            gaps += found - from;
            spans.add(new Span(found, found + 1));
            from = found + 1;
        }
        return Optional.of(new Match(200 - Math.min(gaps, 100), spans));
    }

    private static boolean isWordStart(String text, int at) {
        return at == 0 || !Character.isLetterOrDigit(text.charAt(at - 1));
    }
}
