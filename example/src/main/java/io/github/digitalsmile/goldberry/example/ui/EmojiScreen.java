package io.github.digitalsmile.goldberry.example.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.Overlay;
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
import io.github.digitalsmile.goldberry.widgets.overlay.dialog.Dialogs;
import io.github.digitalsmile.goldberry.widgets.panel.list.ListView;
import io.github.digitalsmile.goldberry.widgets.panel.list.Selection;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// The **Emoji** screen: every character the shipped emoji face has, grouped as
/// Unicode groups them, a row of chips to choose a group, a field to find one,
/// and a dialog of any of them at five sizes — [ADR-0386].
///
/// ## Grouped as Unicode groups them
///
/// Unicode files every emoji under one of ten groups — "Smileys & Emotion",
/// "Animals & Nature", "Flags" — in `emoji-test.txt`, and in an order within
/// each that is the order a picker is meant to show: the grinning face before
/// the tears of joy, the cat before the lion. The JDK carries every emoji
/// property but that one, so the build compiles the file into a table
/// ([Catalogs]) and the sheet shows each group under a heading, in Unicode's
/// order. A chip per group narrows it to one. Code points Unicode groups
/// nowhere go under "Other", last.
///
/// ## Pressing a tile opens its sizes
///
/// A [PressableTile], as on the icon sheet: the dialog shows the emoji at five
/// sizes **and** in a line of ordinary text at five more ([Specimens]) — the
/// second being the half that shows an emoji routed out of prose and back
/// ([ADR-0393]), sharp at every size ([ADR-0456]).
///
/// ## Why it is beside the Icons screen and not part of it
///
/// The two sheets look alike and demonstrate different things. An icon is a
/// **path** the application holds and hands to a box; an emoji is **text**, a
/// code point drawn through a face the cascade picked with `font-family`. So the
/// Icons screen is about an asset with an API, and this one is about §6.1's font
/// chain: one declaration — `.emoji-glyph { font-family: "Noto Color Emoji" }`
/// — is the whole of how an application reaches the emoji slot.
///
/// It is also where the showcase *opts into* `goldberry-emoji`. The face is not
/// in `goldberry-core` ([ADR-0384]); it is Noto Color Emoji, under the SIL OFL,
/// drawn from its COLRv1 paint graphs ([ADR-0456]). The licence asks for no
/// credit on screen, and this screen names the face under its title anyway,
/// which is what an application's About box would do.
///
/// ## Where the list comes from
///
/// The face's own `cmap`, read by [FaceCoverage] — 1499 characters in Noto's
/// COLRv1 build — filtered to the ones whose **default presentation is
/// emoji**, which is 1212 of them. Not a list transcribed into this file: a
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
    /// stroke, and a Noto glyph is a dozen shaded shapes that close up into one
    /// blur below this.
    static final double GLYPH_SIZE = 24;

    @Override
    public State<?> createState() {
        return new EmojiState();
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

        /// [#all], filed under Unicode's groups in Unicode's order.
        private final List<CategorySheet.Group<Entry>> groups =
                Catalogs.emojiGroups(all, Entry::codePoint, Catalogs.emojiGroups());

        /// The chosen group, or [CategorySheet#ALL] — the screen's own state,
        /// for the icon sheet's reason.
        private String category = CategorySheet.ALL;

        private @Nullable String filteredFor;

        private @Nullable String filteredCategory;

        private List<CategorySheet.Group<Entry>> matching = List.of();

        private @Nullable Host host;

        /// The open specimen dialog, or null.
        private @Nullable Overlay specimen;

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
            closeSpecimen();
        }

        @Override
        public Widget build(BuildContext context) {
            host = context.host().orElse(null);
            var query = widget().model().emojiQuery();
            refilter(query);

            return new Column(List.of(header(query), scrolledSheet()), Attributes.NONE.id("emoji-screen"));
        }

        /// One line of ordinary prose with emoji in it, in **no particular
        /// font** — [ADR-0393]'s half of this screen.
        ///
        /// The sheet below is every emoji drawn through
        /// `font-family: "Noto Color Emoji"`,
        /// which is an application choosing the face. This is the other thing,
        /// and it is the one an application actually writes: a sentence in the UI
        /// face, with the pictures routed out of it by the itemizer and back into
        /// it at the right place. Nothing here names a font.
        private Widget routed() {
            return new Text(
                    "Routed without naming a font: rolling to eu-2 🎉 at 14:00 👀 — a family 👨‍👩‍👧,"
                            + " a flag 🇬🇧, and a wave 👋🏽 with its own skin tone.",
                    Attributes.NONE.id("emoji-routed").classes("screen-note"));
        }

        /// The title, the credit the licence asks for, the field and the count.
        private Widget header(String query) {
            var note = available
                    ? "Noto Color Emoji's " + all.size() + " emoji, read out of the face's own cmap and"
                            + " drawn in colour from its COLRv1 paint graphs — gradients, transforms and"
                            + " composites, sharp at any scale; press one to see it at five sizes."
                            + " Noto Color Emoji by Google, SIL Open Font License 1.1 (ADR-0456)."
                    : "The emoji face is not on this build's module path. It ships as goldberry-emoji,"
                            + " so an application that never draws an emoji does not carry it — add the"
                            + " artifact to draw these (ADR-0384).";
            return new Column(
                    List.of(
                            new Text("Every bundled emoji", Attributes.NONE.classes("screen-title")),
                            new Text(note, Attributes.NONE.classes("screen-note")),
                            routed(),
                            CategorySheet.chips(groups, category, this::choose, "emoji-category"),
                            new Row(
                                    List.of(
                                            TextInput.of(
                                                            Models.observable(widget().model(), "app.emoji-query"),
                                                            widget().actions()::setEmojiQuery)
                                                    .placeholder("Search by Unicode name — try \"cat\" or \"1f6\"")
                                                    .id("emoji-search"),
                                            new Text(
                                                    count(query),
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
            var grid = new ListView<>(
                            CategorySheet.rows(matching, columns, entry -> Integer.toHexString(entry.codePoint())),
                            CategorySheet.SheetRow::id,
                            this::rowOf)
                    .selection(Selection.NONE)
                    .virtualized(IconsScreen.ROW_PITCH)
                    .id("emoji-wall");
            return new Scroll(
                    List.of(new IconSheet(grid, this::measured)),
                    ScrollAxis.VERTICAL,
                    Attributes.NONE.id("emoji-viewport"));
        }

        /// "1212 emoji", "169 in Smileys & Emotion", or "12 of 1212".
        private String count(String query) {
            var found = CategorySheet.distinct(matching);
            if (!query.isBlank()) {
                return found + " of " + all.size();
            }
            return category.equals(CategorySheet.ALL) ? all.size() + " emoji" : found + " in " + category;
        }

        /// A heading, or a row of tiles padded to [#columns].
        private Widget rowOf(CategorySheet.SheetRow<Entry> row) {
            return switch (row) {
                case CategorySheet.Heading<Entry> heading ->
                    new CategorySheet.SheetHeading(
                            heading.group(), heading.count(), Attributes.NONE.classes("emoji-heading"));
                case CategorySheet.Tiles<Entry> tiles -> {
                    var cells = new ArrayList<Widget>(columns);
                    for (var entry : tiles.items()) {
                        cells.add(new EmojiTile(
                                entry.character(),
                                entry.name(),
                                () -> open(entry),
                                Attributes.NONE.key(entry.codePoint())));
                    }
                    CategorySheet.pad(cells, columns);
                    yield new Row(cells, Attributes.NONE.classes("icon-row"));
                }
            };
        }

        /// A chip was pressed.
        private void choose(String chosen) {
            if (!chosen.equals(category)) {
                setState(() -> category = chosen);
            }
        }

        /// Opens the specimen dialog for `entry`, replacing one already open —
        /// and does nothing without a host, for the icon sheet's reason.
        private void open(Entry entry) {
            if (host == null) {
                return;
            }
            closeSpecimen();
            var group = Catalogs.emojiGroups().getOrDefault(entry.codePoint(), Catalogs.OTHER);
            specimen = Dialogs.show(
                    host,
                    Specimens.emoji(entry.character(), entry.name(), entry.codePoint(), group, this::closeSpecimen));
        }

        private void closeSpecimen() {
            if (specimen != null) {
                specimen.remove();
                specimen = null;
            }
        }

        private void measured(double width) {
            var next = IconsScreen.columnsFor(width);
            if (next == columns) {
                return;
            }
            setState(() -> columns = next);
        }

        /// Rebuilds [#matching] when the query or the group has changed, and not
        /// otherwise.
        ///
        /// Matched against the Unicode name **and** the hex code point, because
        /// both are how somebody looks for an emoji: by what it is called, and by
        /// the `U+1F6…` they read in a bug report.
        private void refilter(String query) {
            if (query.equals(filteredFor) && category.equals(filteredCategory)) {
                return;
            }
            filteredFor = query;
            filteredCategory = category;
            var needle = query.trim().toLowerCase(Locale.ROOT);
            matching = CategorySheet.narrow(
                    groups,
                    category,
                    entry -> needle.isEmpty()
                            || entry.name().contains(needle)
                            || Integer.toHexString(entry.codePoint()).contains(needle));
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
