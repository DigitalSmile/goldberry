package io.github.digitalsmile.goldberry.example.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widgets.controls.chip.Chip;
import io.github.digitalsmile.goldberry.widgets.core.Row;

/// What the Icons and Emoji sheets share once they are grouped: the groups, the
/// rows a virtualized list is built from, the heading between two groups, and
/// the chips that choose one.
///
/// ## A heading is a row
///
/// Both sheets are a virtualized `list` of equal-height rows ([ADR-0316]), and
/// a virtualized list is told one pitch and trusts it. So a group's heading is
/// **a row of the same pitch**, its label sitting at the bottom of the row the
/// way a section label sits on the content under it. The alternative — a list
/// per group — would be forty lists in one viewport, each virtualizing a window
/// the others' heights have moved.
///
/// ## One selection, and "All" is the absence of one
///
/// The chips are single-choice: pressing a category shows that category, and
/// pressing it again — or pressing **All** — shows every group under its
/// heading. The selection is a string rather than an index, so it survives the
/// list of groups being rebuilt, and [#ALL] is the empty one.
final class CategorySheet {

    /// The selection that shows every group.
    static final String ALL = "";

    private CategorySheet() {}

    /// One category and what is in it, in the order the sheet shows them.
    ///
    /// @param name  the category's own name — Lucide's `food-beverage`,
    ///              Unicode's `Smileys & Emotion` — which is also its key
    /// @param items what is filed under it; never empty on a sheet
    record Group<T>(String name, List<T> items) {

        Group {
            Objects.requireNonNull(name, "name");
            items = List.copyOf(items);
        }
    }

    /// One row of a grouped sheet: a heading, or up to a row's worth of tiles.
    sealed interface SheetRow<T> {

        /// Unique across the sheet, and stable across a filter or a reflow: it
        /// is the key the reconciler and the list's own focus run through, and
        /// an index would make every row a different row the moment a letter is
        /// typed.
        String id();
    }

    /// The label above a group.
    ///
    /// @param group the group's name, which is what the id is made of
    /// @param count how many of its items are on the sheet — after the search,
    ///              so the heading agrees with what is under it
    record Heading<T>(String group, int count) implements SheetRow<T> {

        @Override
        public String id() {
            return "heading:" + group;
        }
    }

    /// A row of tiles, all from one group.
    ///
    /// @param group    the group the tiles are from — part of the id, because
    ///                 an icon in two categories heads a row in each
    /// @param items    up to a row's worth
    /// @param firstKey the first item's key, which makes the id unique
    record Tiles<T>(String group, List<T> items, String firstKey) implements SheetRow<T> {

        @Override
        public String id() {
            return group + "|" + firstKey;
        }
    }

    /// `groups`, narrowed to the `selected` one (or all of them) and to the items
    /// that pass `matches`, with every group left empty dropped.
    ///
    /// A group emptied by a search disappears rather than keeping a heading
    /// over nothing, which is what a reader scanning for the thing they typed
    /// wants: the headings left are exactly where it is.
    static <T> List<Group<T>> narrow(List<Group<T>> groups, String selected, Predicate<? super T> matches) {
        var narrowed = new ArrayList<Group<T>>(groups.size());
        for (var group : groups) {
            if (!selected.equals(ALL) && !group.name().equals(selected)) {
                continue;
            }
            var kept = group.items().stream().filter(matches).toList();
            if (!kept.isEmpty()) {
                narrowed.add(new Group<>(group.name(), kept));
            }
        }
        return narrowed;
    }

    /// The sheet's rows: each group's heading, then its items in rows of
    /// `columns`.
    ///
    /// @param key what identifies an item, for [Tiles#id()]
    static <T> List<SheetRow<T>> rows(List<Group<T>> groups, int columns, Function<? super T, String> key) {
        if (columns < 1) {
            throw new IllegalArgumentException("a sheet has at least one column, not " + columns);
        }
        var rows = new ArrayList<SheetRow<T>>();
        for (var group : groups) {
            rows.add(new Heading<>(group.name(), group.items().size()));
            var items = group.items();
            for (var from = 0; from < items.size(); from += columns) {
                var slice = items.subList(from, Math.min(from + columns, items.size()));
                rows.add(new Tiles<>(group.name(), slice, key.apply(slice.getFirst())));
            }
        }
        return rows;
    }

    /// How many distinct items `groups` holds — which is not the sum of their
    /// sizes when an item is in more than one, as most Lucide icons are.
    static <T> int distinct(List<Group<T>> groups) {
        return (int) groups.stream()
                .flatMap(group -> group.items().stream())
                .distinct()
                .count();
    }

    /// Lucide's category ids as a reader would write them: `food-beverage` is
    /// "Food & beverage", `account` is "Account". A name that is already
    /// written for reading — Unicode's "Smileys & Emotion" — comes back as it
    /// went in.
    static String label(String name) {
        if (name.isEmpty()) {
            return name;
        }
        var spaced = name.replace("-", " & ");
        return spaced.substring(0, 1).toUpperCase(Locale.ROOT) + spaced.substring(1);
    }

    /// A chip id for a group — lower case, letters and digits, hyphenated — so a
    /// test and a tour can find "the Arrows chip" by a stable name.
    static String slug(String name) {
        var slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return slug.isEmpty() ? "all" : slug;
    }

    /// The chip row: **All**, then one chip per group, the selected one checked.
    ///
    /// Pressing the chip that is already selected goes back to **All** — a chip
    /// row is a filter, and taking a filter off by pressing it again is what a
    /// hand expects of one.
    ///
    /// @param prefix the id prefix, `icon-category` or `emoji-category`, so each
    ///               chip is `<prefix>-<slug>`
    static <T> Widget chips(List<Group<T>> groups, String selected, Consumer<String> choose, String prefix) {
        var chips = new ArrayList<Widget>(groups.size() + 1);
        chips.add(new Chip("All", selected.equals(ALL), () -> choose.accept(ALL))
                .keyed(ALL)
                .id(prefix + "-all"));
        for (var group : groups) {
            var name = group.name();
            var chosen = name.equals(selected);
            chips.add(new Chip(label(name), chosen, () -> choose.accept(chosen ? ALL : name))
                    .keyed(name)
                    .id(prefix + "-" + slug(name)));
        }
        return new Row(chips, Attributes.NONE.id(prefix + "-chips").classes("category-chips"));
    }

    /// The empty cells that pad a part-full row of tiles out to `columns`, so it
    /// divides the width the way a full row does — see `.icon-cell-filler`.
    static void pad(List<Widget> cells, int columns) {
        for (var pad = cells.size(); pad < columns; pad++) {
            cells.add(new Row(
                    List.of(), Attributes.NONE.classes("icon-cell-filler").key("pad-" + pad)));
        }
    }

    /// The label over a group: its name and how many are in it.
    ///
    /// A widget of its own rather than a `text`, so the stylesheet can sit it at
    /// the bottom of a row the height of a row of tiles — see [CategorySheet]'s
    /// note on why a heading is a row.
    ///
    /// @param label what the heading reads, already written for reading
    /// @param count how many are under it
    record SheetHeading(String label, int count, Attributes attributes) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "sheet-heading";
        }

        @Override
        public String id() {
            return attributes.id();
        }

        @Override
        public Set<String> classes() {
            return attributes.classes();
        }

        @Override
        public Object key() {
            return attributes.key();
        }

        @Override
        public List<Widget> children() {
            return List.of(new SheetHeadingLabel(label + "  ·  " + count));
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).children(children.toArray(Box[]::new));
        }
    }

    /// The heading's text, a part of its own for [IconTile]'s reason: a box
    /// with text is a measured leaf and cannot also be laid out as a row.
    record SheetHeadingLabel(String text) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "sheet-heading-label";
        }

        @Override
        public Set<String> classes() {
            return Set.of();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).children(Box.text(context.paragraph(style, text), style.color()));
        }
    }
}
