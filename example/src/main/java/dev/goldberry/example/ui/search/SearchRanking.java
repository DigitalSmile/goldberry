package dev.goldberry.example.ui.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.IntStream;

import org.jspecify.annotations.Nullable;

import dev.goldberry.example.ui.search.FuzzyMatcher.Match;
import dev.goldberry.example.ui.search.FuzzyMatcher.Span;

/// Puts a list of [SearchEntry]s in order for a query.
///
/// The query is split into words, and **every** word has to match the entry
/// somewhere — its title, or its context (the screen a card is on, the section
/// a screen is in, the summary). A word counts three times as much in the
/// title as anywhere else, so `tabs` puts the Tabs card above every card whose
/// summary mentions tabs. Screens come a little ahead of cards and commands at
/// an equal score, because a reader who typed a screen's name most likely
/// wants the screen.
///
/// Ties keep the order the entries were given in, which is the gallery's: the
/// sort is stable.
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html#text-input).
public final class SearchRanking {

    /// How much a word matched in a title counts against one matched elsewhere.
    static final int TITLE_WEIGHT = 3;

    private SearchRanking() {}

    /// One entry, placed: its score and the characters of its title to mark.
    ///
    /// @param entry the entry
    /// @param score how well it matched; higher is better
    /// @param title the spans of the title that matched, in order
    public record Ranked(SearchEntry entry, int score, List<Span> title) {

        public Ranked {
            title = List.copyOf(title);
        }
    }

    /// The entries matching `query`, best first, at most `limit` of them.
    ///
    /// @param kind only this kind, or null for all three
    public static List<Ranked> rank(
            String query, List<? extends SearchEntry> entries, SearchEntry.@Nullable Kind kind, int limit) {
        var terms = terms(query);
        var ranked = new ArrayList<Ranked>();
        for (var entry : entries) {
            if (kind != null && entry.kind() != kind) {
                continue;
            }
            score(terms, entry).ifPresent(ranked::add);
        }
        // Indices rather than the list itself, so the sort is visibly stable:
        // equal scores keep the gallery's order.
        return IntStream.range(0, ranked.size())
                .boxed()
                .sorted(Comparator.comparingInt((Integer at) -> -ranked.get(at).score())
                        .thenComparingInt(at -> ranked.get(at).entry().title().length())
                        .thenComparingInt(at -> at))
                .limit(limit)
                .map(ranked::get)
                .toList();
    }

    /// The words of `query`, lower-cased, with the empty ones dropped.
    static List<String> terms(String query) {
        return List.of(query.trim().toLowerCase(Locale.ROOT).split("\\s+")).stream()
                .filter(term -> !term.isEmpty())
                .toList();
    }

    /// How `entry` matches every one of `terms`, or empty when one of them
    /// matches nowhere.
    static Optional<Ranked> score(List<String> terms, SearchEntry entry) {
        if (terms.isEmpty()) {
            return Optional.empty();
        }
        var total = 0;
        var spans = new ArrayList<Span>();
        for (var term : terms) {
            var inTitle = FuzzyMatcher.match(term, entry.title(), true);
            var elsewhere = entry.context().stream()
                    .map(text -> FuzzyMatcher.match(term, text, false))
                    .flatMap(Optional::stream)
                    .mapToInt(Match::score)
                    .max();
            int titleScore = inTitle.map(match -> match.score() * TITLE_WEIGHT).orElse(0);
            var contextScore = elsewhere.orElse(0);
            if (titleScore == 0 && contextScore == 0) {
                return Optional.empty();
            }
            total += Math.max(titleScore, contextScore);
            inTitle.ifPresent(match -> spans.addAll(match.spans()));
        }
        total += switch (entry.kind()) {
            case SCREEN -> 50;
            case COMMAND -> 20;
            case CARD -> 0;
        };
        return Optional.of(new Ranked(entry, total, merge(spans)));
    }

    /// Spans in order with the overlaps joined, so a title is marked once per
    /// run however many words matched it.
    static List<Span> merge(List<Span> spans) {
        var sorted = spans.stream().sorted(Comparator.comparingInt(Span::start)).toList();
        var merged = new ArrayList<Span>();
        for (var span : sorted) {
            if (!merged.isEmpty() && span.start() <= merged.getLast().end()) {
                var last = merged.removeLast();
                merged.add(new Span(last.start(), Math.max(last.end(), span.end())));
            } else {
                merged.add(span);
            }
        }
        return merged;
    }
}
