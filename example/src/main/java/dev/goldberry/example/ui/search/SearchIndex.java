package dev.goldberry.example.ui.search;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/// Every card in the gallery, and every screen's summary, as a list a search can
/// run over without building a single screen.
///
/// A card only exists once its screen is built, and the window builds one
/// screen at a time — so the list is written ahead of time, by a test that
/// builds every screen the way the window does and reads each card's title and
/// summary off what it built. The same test fails when the list on disk no
/// longer matches the screens, and `-Dgoldberry.golden.update=true` rewrites
/// it, so the list cannot drift from what a reader sees.
///
/// ## The file
///
/// UTF-8, one line per entry, four fields separated by tabs: the screen, the
/// card's id (empty for the screen's own header), the title and the summary. A
/// tab, a line break or a backslash inside a field is written `\t`, `\n` or
/// `\\`. Lines starting with `#` are comments.
///
/// Read more: [Views](https://goldberry.dev/docs/applications.html#views).
public final class SearchIndex {

    /// Where the list is, as a resource of this module.
    public static final String RESOURCE = "/dev/goldberry/example/search/index.tsv";

    /// Where the list is in the source tree, from the module's directory: what
    /// the test rewrites.
    public static final String SOURCE = "src/main/resources" + RESOURCE;

    private static final String HEADER = """
            # The showcase's search index: screen, card id (empty for the screen's header), title, summary.
            # Written by SearchIndexTest; rewrite it with -Dgoldberry.golden.update=true.
            """;

    private final List<IndexedCard> entries;
    private final Map<String, Integer> counts;

    /// An index of `entries`, in the order given.
    public SearchIndex(List<IndexedCard> entries) {
        this.entries = List.copyOf(entries);
        this.counts = this.entries.stream()
                .filter(entry -> !entry.isHeader())
                .collect(Collectors.groupingBy(
                        IndexedCard::screen, LinkedHashMap::new, Collectors.summingInt(entry -> 1)));
    }

    /// The list this build ships, read once.
    public static SearchIndex shipped() {
        return Shipped.INDEX;
    }

    /// Every entry, headers and cards, in the gallery's order.
    public List<IndexedCard> entries() {
        return entries;
    }

    /// The cards alone.
    public List<IndexedCard> cards() {
        return entries.stream().filter(entry -> !entry.isHeader()).toList();
    }

    /// The cards on the screen `name`, in the order it builds them.
    public List<IndexedCard> cardsOn(String name) {
        return entries.stream()
                .filter(entry -> !entry.isHeader() && entry.screen().equals(name))
                .toList();
    }

    /// How many cards each screen holds, by name. A screen with none is absent.
    public Map<String, Integer> counts() {
        return Map.copyOf(counts);
    }

    /// The summary the screen `name` opens with, when the index has it.
    public Optional<String> summaryOf(String name) {
        return entries.stream()
                .filter(entry -> entry.isHeader() && entry.screen().equals(name))
                .map(IndexedCard::summary)
                .findFirst();
    }

    /// The index as the file holds it.
    public String write() {
        var out = new StringBuilder(HEADER);
        for (var entry : entries) {
            out.append(escape(entry.screen()))
                    .append('\t')
                    .append(escape(entry.id()))
                    .append('\t')
                    .append(escape(entry.title()))
                    .append('\t')
                    .append(escape(entry.summary()))
                    .append('\n');
        }
        return out.toString();
    }

    /// Reads what [#write] wrote.
    ///
    /// @throws IllegalArgumentException for a line that is not four fields, naming it
    public static SearchIndex parse(String text) {
        var entries = new ArrayList<IndexedCard>();
        var number = 0;
        for (var line : text.split("\n", -1)) {
            number++;
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            var fields = line.split("\t", -1);
            if (fields.length != 4) {
                throw new IllegalArgumentException(
                        "line " + number + " of the search index has " + fields.length + " fields, not 4: " + line);
            }
            entries.add(new IndexedCard(
                    unescape(fields[0]), unescape(fields[1]), unescape(fields[2]), unescape(fields[3])));
        }
        return new SearchIndex(entries);
    }

    static String escape(String field) {
        return field.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n");
    }

    static String unescape(String field) {
        var out = new StringBuilder(field.length());
        for (var i = 0; i < field.length(); i++) {
            var c = field.charAt(i);
            if (c != '\\' || i + 1 == field.length()) {
                out.append(c);
                continue;
            }
            var next = field.charAt(++i);
            out.append(
                    switch (next) {
                        case 't' -> '\t';
                        case 'n' -> '\n';
                        default -> next;
                    });
        }
        return out.toString();
    }

    /// The shipped list, read the first time anybody asks. Empty, rather than a
    /// window that will not open, when the resource is missing: search then
    /// finds screens and commands and no cards.
    private static final class Shipped {

        static final SearchIndex INDEX = read();

        private static SearchIndex read() {
            try (InputStream in = SearchIndex.class.getResourceAsStream(RESOURCE)) {
                if (in == null) {
                    return new SearchIndex(List.of());
                }
                return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException("could not read the search index", e);
            }
        }
    }
}
