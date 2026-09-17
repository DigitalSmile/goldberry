package io.github.digitalsmile.goldberry.example.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.digitalsmile.goldberry.assets.BundledAssets;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.bind.Subscription;
import io.github.digitalsmile.goldberry.bind.runtime.Models;
import io.github.digitalsmile.goldberry.example.ShowcaseModel;
import io.github.digitalsmile.goldberry.text.font.FaceCoverage;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.core.Row;
import io.github.digitalsmile.goldberry.widgets.core.scroll.Scroll;
import io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollAxis;
import io.github.digitalsmile.goldberry.widgets.form.textinput.TextInput;
import io.github.digitalsmile.goldberry.widgets.panel.list.ListView;
import io.github.digitalsmile.goldberry.widgets.panel.list.Selection;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// The **Emoji** screen: every character the shipped emoji face has, and a field
/// to find one — [ADR-0386].
///
/// ## Why it is beside the Icons screen and not part of it
///
/// The two sheets look alike and demonstrate different things. An icon is a
/// **path** the application holds and hands to a box; an emoji is **text**, a
/// code point drawn through a face the cascade picked with `font-family`. So the
/// Icons screen is about an asset with an API, and this one is about §6.1's font
/// chain: one declaration — `.emoji-glyph { font-family: OpenMoji }` — is the
/// whole of how an application reaches the emoji slot.
///
/// It is also where the showcase *opts into* `goldberry-emoji`. The face is not
/// in `goldberry-core`, because CC BY-SA asks for attribution where the work is
/// seen ([ADR-0384]) — so this screen carries the credit the licence asks for,
/// in the note under its title, which is what an application's About box would
/// do.
///
/// ## Where the list comes from
///
/// The face's own `cmap`, read by [FaceCoverage] — 1845 characters in OpenMoji's
/// monochrome build — filtered to the ones whose **default presentation is
/// emoji**, which is 1205 of them. Not a list transcribed into this file: a
/// transcription is a second copy of the font's contents that goes stale the
/// first time the pinned version moves.
///
/// The filter is `Character.isEmojiPresentation`, and it is not
/// `Character.isEmoji`: the second is true of `0`, `#` and `↔`, because those
/// have emoji *sequences* built on them — so a sheet built on it opens on the
/// digits and a reader looking for a cat scrolls past the ASCII. What a picker
/// wants is the characters that are pictures on their own.
///
/// The names are the **JDK's**: `Character.getName` is Unicode's own name for a
/// code point, which is how a reader searches for one ("HEART", "CAT") and costs
/// no second asset.
///
/// ## Everything else is the Icons screen's
///
/// The reflow, the virtualized rows, the padded last row and the viewport that
/// leaves the header alone are all [IconsScreen]'s, for [IconsScreen]'s reasons,
/// and the two share [IconSheet] and the arithmetic in [IconsScreen#columnsFor].
/// What differs is the tile, the source of the list, and the fact that a
/// character with no glyph in the face simply is not in it.
///
/// @param model   where the query lives, so a `text-input` can bind to it
/// @param actions what the field reports to
public record EmojiScreen(ShowcaseModel model, ShowcaseModel.Actions actions) implements Widget.Stateful {

    /// The size the glyphs are drawn at, in logical pixels.
    ///
    /// 24 rather than the icon sheet's 20: an emoji is a picture rather than a
    /// stroke, and OpenMoji's monochrome build is drawn with detail that closes
    /// up below this.
    static final double GLYPH_SIZE = 24;

    @Override
    public State<?> createState() {
        return new EmojiState();
    }

    /// One row of the sheet, which is what the `list` virtualizes over.
    ///
    /// Identified by its **first code point**, which is unique for
    /// [IconsScreen.IconRow]'s reason: an index would make every row a different
    /// row the moment a letter is typed.
    ///
    /// @param entries up to `columns` of them; the last row has fewer
    record EmojiRow(List<Entry> entries) {

        String id() {
            return Integer.toHexString(entries.getFirst().codePoint());
        }
    }

    /// One emoji: the code point, the string to draw, and Unicode's name for it.
    ///
    /// The string is kept rather than derived per frame because
    /// `Character.toChars` allocates, and a sheet builds a row of them on every
    /// frame a reader scrolls.
    ///
    /// @param codePoint what the face has a glyph for
    /// @param character the same, as the two chars a supplementary code point is
    /// @param name      Unicode's name, which is what a search matches
    record Entry(int codePoint, String character, String name) {}

    /// The query, the entries, and the rows.
    static final class EmojiState extends State<EmojiScreen> {

        /// Every character the face has, in code point order, with its name.
        ///
        /// Read once when the screen is created: the `cmap` is parsed in one pass
        /// and 1845 names come out of the JDK's own tables, which is a few
        /// milliseconds on the frame the screen is first built and nothing after
        /// that.
        private final List<Entry> all = entries();

        /// Whether the face was there at all. A showcase that shipped without
        /// `goldberry-emoji` should say so rather than draw an empty sheet
        /// ([ADR-0384]).
        private final boolean available = BundledAssets.hasEmojiFont();

        private String filteredFor;

        private List<Entry> matching = List.of();

        private int columns = IconsScreen.DEFAULT_COLUMNS;

        private Subscription watching;

        @Override
        protected void initState() {
            // Watched rather than bound, for the icon sheet's reason: a different
            // query is a different number of rows, which is a structural change
            // rather than a value inside one (ADR-0109).
            watching = Models.observable(widget().model(), "app.emoji-query").subscribe(value -> setState(() -> {}));
        }

        @Override
        protected void dispose() {
            if (watching != null) {
                watching.close();
                watching = null;
            }
        }

        @Override
        public Widget build(BuildContext context) {
            var query = widget().model().emojiQuery();
            refilter(query);

            return new Column(List.of(header(query), scrolledSheet()), Attributes.NONE.id("emoji-screen"));
        }

        /// The title, the credit the licence asks for, the field and the count.
        private Widget header(String query) {
            var note = available
                    ? "OpenMoji's " + all.size() + " emoji, read out of the face's own cmap. A document draws"
                            + " one by writing it in text and letting font-family: OpenMoji pick the slot."
                            + " Emoji artwork by OpenMoji (openmoji.org), CC BY-SA 4.0 — the credit this"
                            + " application owes for adding goldberry-emoji (ADR-0384)."
                    : "The emoji face is not on this build's module path. It ships as goldberry-emoji,"
                            + " because CC BY-SA asks for attribution where the work is seen — add the"
                            + " artifact and its credit to draw these (ADR-0384).";
            return new Column(
                    List.of(
                            new Text("Every bundled emoji", Attributes.NONE.classes("screen-title")),
                            new Text(note, Attributes.NONE.classes("screen-note")),
                            new Row(
                                    List.of(
                                            TextInput.of(
                                                            Models.observable(widget().model(), "app.emoji-query"),
                                                            widget().actions()::setEmojiQuery)
                                                    .placeholder("Search by Unicode name — try \"cat\" or \"1f6\"")
                                                    .id("emoji-search"),
                                            new Text(
                                                    query.isEmpty()
                                                            ? all.size() + " emoji"
                                                            : matching.size() + " of " + all.size(),
                                                    Attributes.NONE
                                                            .id("emoji-count")
                                                            .classes("caption"))),
                                    Attributes.NONE.id("emoji-search-row"))),
                    Attributes.NONE.id("emoji-header"));
        }

        /// The sheet, in a viewport of its own — [IconsScreen.IconsState#scrolledSheet()].
        private Widget scrolledSheet() {
            if (matching.isEmpty()) {
                return new Text(
                        available
                                ? "No emoji has that in its Unicode name. The names are English and spaced —"
                                        + " \"grinning face\", \"black cat\", \"rocket\"."
                                : "Nothing to show without the face.",
                        Attributes.NONE.id("emoji-empty").classes("screen-note"));
            }
            var grid = new ListView<>(rows(), EmojiRow::id, this::rowOf)
                    .selection(Selection.NONE)
                    .virtualized(IconsScreen.ROW_PITCH)
                    .id("emoji-wall");
            return new Scroll(
                    List.of(new IconSheet(grid, this::measured)),
                    ScrollAxis.VERTICAL,
                    Attributes.NONE.id("emoji-viewport"));
        }

        private List<EmojiRow> rows() {
            var rows = new ArrayList<EmojiRow>(matching.size() / columns + 1);
            for (var from = 0; from < matching.size(); from += columns) {
                rows.add(new EmojiRow(matching.subList(from, Math.min(from + columns, matching.size()))));
            }
            return rows;
        }

        /// One row of tiles, padded to [#columns] so it divides the width the way
        /// a full row does.
        private Widget rowOf(EmojiRow row) {
            var cells = new ArrayList<Widget>(columns);
            for (var entry : row.entries()) {
                cells.add(new EmojiTile(entry.character(), entry.name(), Attributes.NONE.key(entry.codePoint())));
            }
            for (var pad = row.entries().size(); pad < columns; pad++) {
                cells.add(new Row(
                        List.of(), Attributes.NONE.classes("icon-cell-filler").key("pad-" + pad)));
            }
            return new Row(cells, Attributes.NONE.classes("icon-row"));
        }

        private void measured(double width) {
            var next = IconsScreen.columnsFor(width);
            if (next == columns) {
                return;
            }
            setState(() -> columns = next);
        }

        /// Rebuilds [#matching] when the query has changed, and not otherwise.
        ///
        /// Matched against the Unicode name **and** the hex code point, because
        /// both are how somebody looks for an emoji: by what it is called, and by
        /// the `U+1F6…` they read in a bug report.
        private void refilter(String query) {
            if (query.equals(filteredFor)) {
                return;
            }
            filteredFor = query;
            var needle = query.trim().toLowerCase(Locale.ROOT);
            if (needle.isEmpty()) {
                matching = all;
                return;
            }
            var found = new ArrayList<Entry>(64);
            for (var entry : all) {
                if (entry.name().toLowerCase(Locale.ROOT).contains(needle)
                        || Integer.toHexString(entry.codePoint()).contains(needle)) {
                    found.add(entry);
                }
            }
            matching = List.copyOf(found);
        }

        /// The face's characters, named.
        ///
        /// Empty when the face is not there, which is the one branch this screen
        /// has that the icon sheet does not: `goldberry-core` carries the icons
        /// and does not carry the emoji.
        private static List<Entry> entries() {
            if (!BundledAssets.hasEmojiFont()) {
                return List.of();
            }
            var points = FaceCoverage.codePoints(BundledAssets.font(BundledFont.EMOJI));
            var entries = new ArrayList<Entry>(points.length);
            for (var point : points) {
                if (!Character.isEmojiPresentation(point)) {
                    continue;
                }
                var name = Character.getName(point);
                if (name == null) {
                    continue;
                }
                entries.add(new Entry(point, new String(Character.toChars(point)), name.toLowerCase(Locale.ROOT)));
            }
            return List.copyOf(entries);
        }
    }
}
