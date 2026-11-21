package dev.goldberry.example.ui.richinput.suggest;

import java.util.List;
import java.util.Objects;

/// What one trigger looks up: the suggestions for what was typed after it.
///
/// ```java
/// Map.of("@", Suggester.of(Directories.PEOPLE), "#", Suggester.of(Directories.TOPICS));
/// ```
@FunctionalInterface
public interface Suggester {

    /// The most suggestions [#of] shows at once.
    int SHOWN = 5;

    /// The suggestions for `query`, in the order they are listed.
    List<Suggestion> suggest(String query);

    /// Up to [#SHOWN] of `from` that match a query, in their order.
    static Suggester of(List<Suggestion> from) {
        var all = List.copyOf(from);
        return query -> {
            Objects.requireNonNull(query, "query");
            return all.stream()
                    .filter(suggestion -> suggestion.matches(query))
                    .limit(SHOWN)
                    .toList();
        };
    }
}
