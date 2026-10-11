package dev.goldberry.example.ui.search;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import dev.goldberry.Host;
import dev.goldberry.example.ui.search.FuzzyMatcher.Span;
import dev.goldberry.example.ui.search.SearchRanking.Ranked;
import dev.goldberry.example.ui.shell.Reveal;
import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.key.Mod;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.pressable.Pressable;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.core.icon.IconView;
import dev.goldberry.widgets.core.scroll.Scroll;
import dev.goldberry.widgets.core.scroll.ScrollAxis;
import dev.goldberry.widgets.form.textinput.TextInput;
import dev.goldberry.widgets.text.RichText;
import dev.goldberry.widgets.text.Run;
import dev.goldberry.widgets.text.Text;

/// The search palette's content: a field, a filter by kind, and the results.
///
/// ```text
/// ⌕ [tabs                                        ]
/// (All) (Screens) (Cards) (Commands)
/// ▸ Tabs                                  Panels
///   A strip of tabs over the panel each one shows.
///   Tabs card · Navigation
/// ↑↓ move · Enter open · Tab filter · Esc close
/// ```
///
/// The field keeps the keyboard the whole time: the arrows move the marked
/// result, `Enter` opens it, `Tab` and `Shift+Tab` step through the kinds, and
/// every other key types. With nothing typed the list is what the reader picked
/// lately, then every screen.
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html#text-input).
///
/// @param entries what there is to find
/// @param recent  the keys of recent picks, most recent first
/// @param onPick  what choosing a result does
public record CommandPalette(List<SearchEntry> entries, List<String> recent, Consumer<SearchEntry> onPick)
        implements Widget.Stateful {

    /// The palette's id.
    public static final String ID = "search-palette";

    /// The field's id.
    public static final String INPUT = "search-input";

    /// The most results listed: more than a reader scans, few enough to build
    /// on every keystroke.
    public static final int LIMIT = 60;

    /// How far `PgUp` and `PgDn` move the mark.
    static final int PAGE = 8;

    public CommandPalette {
        entries = List.copyOf(entries);
        recent = List.copyOf(recent);
    }

    /// The id of the result at `index`.
    public static String resultId(int index) {
        return "search-result-" + index;
    }

    /// What an empty query lists: the recent picks that still exist, then every
    /// screen not among them.
    static List<Ranked> idle(List<SearchEntry> entries, List<String> recent, SearchEntry.@Nullable Kind kind) {
        Map<String, SearchEntry> byKey =
                entries.stream().collect(Collectors.toMap(SearchEntry::key, Function.identity(), (a, b) -> a));
        var listed = new ArrayList<Ranked>();
        for (var key : recent) {
            var entry = byKey.get(key);
            if (entry != null && (kind == null || entry.kind() == kind)) {
                listed.add(new Ranked(entry, 0, List.of()));
            }
        }
        var wanted = kind == null ? SearchEntry.Kind.SCREEN : kind;
        for (var entry : entries) {
            if (entry.kind() == wanted && !recent.contains(entry.key()) && listed.size() < LIMIT) {
                listed.add(new Ranked(entry, 0, List.of()));
            }
        }
        return listed;
    }

    @Override
    public State<?> createState() {
        return new PaletteState();
    }

    /// The query, the kind, and which result is marked.
    static final class PaletteState extends State<CommandPalette> {

        private String query = "";
        private SearchEntry.@Nullable Kind kind;
        private int active;
        private List<Ranked> results = List.of();
        private @Nullable Host host;

        @Override
        protected void initState() {
            results = rank();
        }

        @Override
        protected void didUpdateWidget(CommandPalette previous) {
            results = rank();
        }

        private List<Ranked> rank() {
            var palette = widget();
            return query.isBlank()
                    ? idle(palette.entries(), palette.recent(), kind)
                    : SearchRanking.rank(query, palette.entries(), kind, LIMIT);
        }

        @Override
        public Widget build(BuildContext context) {
            host = context.host().orElse(null);
            var body = new ArrayList<Widget>();
            body.add(new Row(
                    List.of(
                            new IconView("search").withAttributes(Attributes.NONE.classes("search-glyph")),
                            new TextInput(query, this::typed)
                                    .placeholder("Search screens, cards and commands")
                                    .withAttributes(Attributes.NONE.id(INPUT).name("Search"))),
                    Attributes.NONE.classes("palette-input")));
            body.add(kinds());
            body.add(results.isEmpty() ? empty() : list());
            body.add(new Row(
                    List.of(
                            hint("↑↓", "move"),
                            hint("Enter", "open"),
                            hint("Tab", "filter"),
                            hint("Esc", "close"),
                            new Text(
                                    results.size() + (results.size() == 1 ? " result" : " results"),
                                    Attributes.NONE.classes("palette-count"))),
                    Attributes.NONE.classes("palette-foot")));
            return new PaletteKeys(new Column(body, Attributes.NONE.id(ID).classes("palette")), this::key);
        }

        private Widget kinds() {
            var chips = new ArrayList<Widget>();
            chips.add(kindChip(null, "All"));
            for (var each : SearchEntry.Kind.values()) {
                chips.add(kindChip(each, each.title()));
            }
            return new Row(chips, Attributes.NONE.classes("palette-kinds"));
        }

        private Widget kindChip(SearchEntry.@Nullable Kind value, String label) {
            var classes = value == kind ? new String[] {"palette-kind", "selected"} : new String[] {"palette-kind"};
            return new Pressable(
                    "Show " + label.toLowerCase(Locale.ROOT),
                    () -> setKind(value),
                    false,
                    List.of(new Text(label)),
                    Attributes.NONE.classes(classes));
        }

        private Widget empty() {
            return new Column(
                    List.of(
                            new Text(
                                    "Nothing matches “" + query.trim() + "”.",
                                    Attributes.NONE.classes("palette-empty")),
                            new Text("Try fewer letters, or the start of a word.", Attributes.NONE.classes("caption"))),
                    Attributes.NONE.classes("palette-results", "empty"));
        }

        private Widget list() {
            var rows = new ArrayList<Widget>(results.size());
            for (var index = 0; index < results.size(); index++) {
                rows.add(row(index, results.get(index)));
            }
            return new Scroll(
                    List.of(new Column(rows, Attributes.NONE.classes("palette-list"))),
                    ScrollAxis.VERTICAL,
                    Attributes.NONE.classes("palette-results"));
        }

        private Widget row(int index, Ranked ranked) {
            var entry = ranked.entry();
            var classes = index == active ? new String[] {"palette-result", "active"} : new String[] {"palette-result"};
            var text = new Column(
                    List.of(
                            new RichText(
                                    marked(entry.title(), ranked.title()), Attributes.NONE.classes("palette-title")),
                            new Text(entry.detail(), Attributes.NONE.classes("palette-detail"))),
                    Attributes.NONE.classes("palette-text"));
            return new Pressable(
                    entry.title(),
                    () -> widget().onPick().accept(entry),
                    false,
                    List.of(
                            new IconView(icon(entry)).withAttributes(Attributes.NONE.classes("palette-icon")),
                            text,
                            new Text(tag(entry), Attributes.NONE.classes("palette-tag"))),
                    Attributes.NONE.id(resultId(index)).classes(classes));
        }

        private static Widget hint(String key, String what) {
            return new Row(
                    List.of(
                            new Text(key, Attributes.NONE.classes("kbd")),
                            new Text(what, Attributes.NONE.classes("caption"))),
                    Attributes.NONE.classes("palette-hint"));
        }

        private void typed(String value) {
            if (value.equals(query)) {
                return;
            }
            setState(() -> {
                query = value;
                active = 0;
                results = rank();
            });
        }

        private void setKind(SearchEntry.@Nullable Kind value) {
            setState(() -> {
                kind = value;
                active = 0;
                results = rank();
            });
        }

        /// The keys [PaletteKeys] hands over; true for one it took.
        private boolean key(KeyEvent event) {
            var shift = event.modifiers().has(Mod.SHIFT);
            return switch (event.key()) {
                case DOWN -> move(1);
                case UP -> move(-1);
                case PAGE_DOWN -> move(PAGE);
                case PAGE_UP -> move(-PAGE);
                case ENTER -> open();
                case TAB -> {
                    stepKind(shift ? -1 : 1);
                    yield true;
                }
                default -> false;
            };
        }

        private boolean move(int by) {
            if (results.isEmpty()) {
                return true;
            }
            var next = Math.clamp((long) active + by, 0, results.size() - 1);
            if (next != active) {
                setState(() -> active = next);
                var window = host;
                if (window != null) {
                    window.after(Duration.ZERO, () -> Reveal.inView(window, resultId(next)));
                }
            }
            return true;
        }

        private boolean open() {
            if (active < results.size()) {
                widget().onPick().accept(results.get(active).entry());
            }
            return true;
        }

        private void stepKind(int by) {
            var kinds = List.of(SearchEntry.Kind.values());
            // -1 is All, then the kinds in order.
            var at = kind == null ? -1 : kinds.indexOf(kind);
            var next = Math.floorMod(at + 1 + by, kinds.size() + 1) - 1;
            setKind(next < 0 ? null : kinds.get(next));
        }
    }

    /// The icon a result is drawn with.
    static String icon(SearchEntry entry) {
        return switch (entry) {
            case SearchEntry.Screen screen -> screen.tab().icon();
            case SearchEntry.Card card -> card.screen().icon();
            case SearchEntry.Command _ -> "zap";
        };
    }

    /// The tag at the end of a result: where it is, or what it is.
    static String tag(SearchEntry entry) {
        return switch (entry) {
            case SearchEntry.Screen screen -> screen.tab().section().title();
            case SearchEntry.Card card -> card.screen().title();
            case SearchEntry.Command command -> command.shortcut().isEmpty() ? "Command" : command.shortcut();
        };
    }

    /// `title` as runs, with the matched spans in a `mark` run each.
    static List<Run> marked(String title, List<Span> spans) {
        var runs = new ArrayList<Run>();
        var at = 0;
        for (var span : spans) {
            var start = Math.min(span.start(), title.length());
            var end = Math.min(span.end(), title.length());
            if (start > at) {
                runs.add(new Run(title.substring(at, start)));
            }
            if (end > start) {
                runs.add(new Run(title.substring(start, end), Set.of("mark")));
            }
            at = Math.max(at, end);
        }
        if (at < title.length() || runs.isEmpty()) {
            runs.add(new Run(title.substring(at)));
        }
        return runs;
    }
}
