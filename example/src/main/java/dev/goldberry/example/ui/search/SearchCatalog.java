package dev.goldberry.example.ui.search;

import java.util.ArrayList;
import java.util.List;

import dev.goldberry.example.ui.gallery.Gallery;

/// Everything the palette can find, in the order ties are broken: the screens
/// in the sidebar's order, the cards screen by screen, then the commands.
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html#text-input).
public final class SearchCatalog {

    private SearchCatalog() {}

    /// The screens and cards `index` knows, then `commands`.
    ///
    /// A card on a screen the gallery no longer has is left out rather than
    /// listed: a result that opens nothing is worse than a missing one, and
    /// the test that writes the index fails on it first.
    public static List<SearchEntry> entries(SearchIndex index, List<SearchEntry.Command> commands) {
        var entries = new ArrayList<SearchEntry>();
        for (var tab : Gallery.TABS) {
            entries.add(new SearchEntry.Screen(tab, index.summaryOf(tab.name()).orElse("")));
        }
        for (var card : index.cards()) {
            if (Gallery.has(card.screen())) {
                entries.add(new SearchEntry.Card(card, Gallery.tab(card.screen())));
            }
        }
        entries.addAll(commands);
        return List.copyOf(entries);
    }
}
