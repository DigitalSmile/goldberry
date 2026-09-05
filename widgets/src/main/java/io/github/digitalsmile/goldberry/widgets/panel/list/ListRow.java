package io.github.digitalsmile.goldberry.widgets.panel.list;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.event.TextEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.input.handler.Selects;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.log.Logs;
import io.github.digitalsmile.goldberry.natives.yoga.style.StyleLength;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.semantics.Role;
import io.github.digitalsmile.goldberry.widget.semantics.Semantics;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// One row of a [ListView] — a **part**, so it is styleable and not
/// constructible ([ADR-0065]).
///
/// ## It carries attributes, which no other part in the catalog does
///
/// §10 asks for "item context menus", and a menu is a **name on a widget**
/// ([ADR-0108]) that the launcher finds by walking up from an element and asking
/// whether its widget is [Attributed]. Naming it on whatever the item-factory
/// returned would work for a right-click and not for the keyboard: the menu key
/// walks up from the **focused** element ([ADR-0208]), and the focused element is
/// this row rather than anything inside it. So the row is where the name has to
/// live, and carrying an [Attributes] is how a widget says one.
///
/// Its `id` and classes are *not* the attributes', because both are computed —
/// the id is the focus name [ListState] moves the keyboard by, and `selected` is
/// a fact about the model. What the attributes carry here is the menu, and room
/// for whatever a later version needs to say per row.
///
/// ## The keyboard splits the same way a tree's does
///
/// `Up` and `Down` are the scope's ([ListBox]). `Enter` chooses, which is
/// `option`'s rule in a list and the same one: a set where the keyboard commits
/// must not choose before it. `Home`, `End` and type-to-select all need rows this
/// one cannot see, so each is a callback the list hands down — the shape
/// `TreeRow` established ([ADR-0209]).
///
/// @param id         this row's focus name, already scoped to its list
/// @param selectable whether it may be chosen — false when the list selects
///                   nothing
/// @param selected   whether it is a chosen row
/// @param content    what the item-factory returned
/// @param attributes what the row *says*: §10's per-item context menu
/// @param onSelect   asked to be chosen, **with the modifiers that were held** —
///                   `Ctrl` and `Shift` mean different things in a multi-select
///                   list, and only the list knows what they resolve to
/// @param onEnd      asked to move to the first or last row
/// @param onType     what was typed, for §10's type-to-select
/// @param checkedPitch the pitch the list is spacing rows at, or 0 — set on
///                   **one** row of the window and zero on the rest, because the
///                   answer is the same for all of them and a check that ran per
///                   row would fire twenty times per frame and could not tell a
///                   frame from a sibling. The first built row carries it, which
///                   is where `book/src/TODO.md` said the assertion belonged
///                   ([ADR-0257])
record ListRow(
        String id,
        boolean selectable,
        boolean selected,
        Widget content,
        Attributes attributes,
        Consumer<Modifiers> onSelect,
        IntConsumer onEnd,
        Consumer<String> onType,
        double checkedPitch)
        implements Widget.Leaf, Styled, Paints, Handles, Selects, Attributed<ListRow>, Semantics {

    private static final org.slf4j.Logger LOG = Logs.of(ListRow.class);

    @Override
    public String cssType() {
        return "list-row";
    }

    /// The focus name, not the attributes' `id` — [ListState] moves the keyboard
    /// by it and nothing else sets one.
    @Override
    public String id() {
        return id;
    }

    /// Its item's identity, so a row keeps its element — and its focus — when the
    /// model is rebuilt or reordered. The rule §10 states for `list` keys and §3
    /// restates for a tree's expansion.
    @Override
    public Object key() {
        return id;
    }

    @Override
    public Set<String> classes() {
        return selected ? Set.of("selected") : Set.of();
    }

    /// Every row is a stop within the list's own scope, so the arrows rove
    /// between them and the list is one Tab stop from outside (§7.2).
    ///
    /// **A row is focusable even when nothing is selectable.** §10's `none` is a
    /// statement about what may be *chosen*, not about what may be read: a list
    /// nobody can select from is still one a keyboard user must be able to walk,
    /// or its rows are unreachable content.
    @Override
    public boolean isFocusable() {
        return true;
    }

    @Override
    public List<Widget> children() {
        return List.of(content);
    }

    @Override
    public ListRow withAttributes(Attributes value) {
        return new ListRow(id, selectable, selected, content, value, onSelect, onEnd, onType, checkedPitch);
    }

    @Override
    public void onPointer(PointerEvent event) {
        if (event.kind() != PointerEvent.Kind.CLICKED) {
            return;
        }
        if (selectable) {
            // **The modifiers travel with it.** A click is `Ctrl`-clicked or
            // `Shift`-clicked or neither, and which of the three it was decides
            // what the new selection is -- a question only the list can answer,
            // because a range runs over rows this one cannot see.
            onSelect.accept(event.modifiers());
            event.consume();
        }
        // A row in a `NONE` list does **not** consume: there is nothing for the
        // click to mean here, and swallowing it would stop a button the
        // item-factory put on the row from ever being pressed.
    }

    /// A right-click selects the row it is over, which is what every file manager
    /// does before it opens a menu ([ADR-0224]).
    ///
    /// **Unless the row is already in the selection.** Right-clicking one of five
    /// chosen files opens a menu about the five; collapsing them to one would
    /// throw away the very thing the user is about to act on, and it is the
    /// failure that makes an application write this by hand and get it wrong.
    ///
    /// With no modifiers, because a right-click is not a `Ctrl`-click: the
    /// gesture means "act on this", and the list resolves that to a selection of
    /// one exactly as an unmodified press does.
    @Override
    public void selectForContextMenu() {
        if (!selectable || selected) {
            return;
        }
        onSelect.accept(Modifiers.NONE);
    }

    @Override
    public void onKey(KeyEvent event) {
        if (event.kind() != KeyEvent.Kind.PRESSED) {
            return;
        }
        // **`Enter` is handled before the unmodified guard**, because it is the
        // one key here that means something different when a modifier is held:
        // `Ctrl+Enter` adds a row to a selection and `Shift+Enter` sweeps to it,
        // which are the keyboard's halves of the gestures the pointer has.
        // `Alt` and the platform key are nobody's here and fall through, so an
        // application's `Alt+Enter` accelerator still reaches it.
        if (event.key() == Key.ENTER) {
            if (selectable && !event.modifiers().alt() && !event.modifiers().meta()) {
                onSelect.accept(event.modifiers());
                event.consume();
            }
            return;
        }
        if (!event.modifiers().none()) {
            return;
        }
        switch (event.key()) {
            // §10's `Home`/`End`. To the ends of the **model** rather than of the
            // viewport, which is what every list means by it and what `Ctrl+End`
            // means in every document -- a list's own scrolling is a `scroll`
            // ancestor's business, and the focus ring is what asks it to follow
            // ([ADR-0120]).
            case HOME -> {
                onEnd.accept(-1);
                event.consume();
            }
            case END -> {
                onEnd.accept(1);
                event.consume();
            }
            default -> {}
        }
    }

    /// §10's "type-to-select when items expose text".
    ///
    /// A [TextEvent] rather than a key for `select`'s reason: what a typeahead
    /// wants is what was *typed*, and one character can take several keys.
    ///
    /// **Not consumed when there is no typeahead.** A list whose items expose no
    /// text hands `onType` down as null, and a row that swallowed the text anyway
    /// would stop a `text-input` elsewhere from ever seeing a keystroke that
    /// bubbled past it.
    @Override
    public void onText(TextEvent event) {
        if (event.text().isEmpty() || onType == null) {
            return;
        }
        onType.accept(event.text());
        event.consume();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        warnIfTheRowIsNotThePitch(style);
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }

    /// Mismatches seen **once**, as `pitch/height`, and not yet believed.
    ///
    /// The first frame of a list that reads its pitch from a token is measured
    /// before there is a cascade to read it from: a `Stateful` widget builds once
    /// inside the `ElementTree` constructor, so that build answers the token's
    /// *default* and the second build is the first that can see the stylesheet
    /// ([ADR-0254]). Under `--gb-list-row-height: 26px` that is one frame of a
    /// 32px pitch against 26px rows — a real disagreement, for one frame, that
    /// nobody sees and that settles by construction.
    ///
    /// So a mismatch has to be seen twice. A genuine one is seen on every frame
    /// for as long as the list is up; a settling one is seen once and never
    /// again, because the pair it would be keyed under stops occurring.
    private static final java.util.Set<String> SEEN_PITCH = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /// Which mismatches have been reported, as `pitch/height`.
    ///
    /// `render` runs per row per paint, so an unguarded warning would be one
    /// line per visible row per frame — the log [ADR-0243] has just finished
    /// quietening, multiplied by twenty. Keyed by the **pair** so that two lists
    /// virtualizing on two wrong pitches are two reports, and one list is one
    /// however many rows it has.
    private static final java.util.Set<String> REPORTED_PITCH = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /// Forgets what has been reported, for a test that drives the same mismatch
    /// twice. `ComputedStyle.forgetReportedDrops`'s reason exactly.
    static void forgetReportedPitch() {
        SEEN_PITCH.clear();
        REPORTED_PITCH.clear();
    }

    /// How many distinct mismatches have been reported, so a test can say *once*
    /// rather than merely *at all*.
    static int reportedPitchCount() {
        return REPORTED_PITCH.size();
    }

    /// Half a logical pixel, which is smaller than any real disagreement and
    /// larger than the rounding a `var()` through a percentage can produce.
    private static final double SLACK = 0.5;

    /// Says once that this row's height and the pitch the list virtualizes on
    /// are not the same number.
    ///
    /// **The symptom is not a wrong row.** It is rows drifting out of step with
    /// the scrollbar, and it gets worse the further down the model you are: the
    /// spacers are `index × pitch` tall and the rows between them are whatever
    /// the stylesheet says, so a one-pixel disagreement is twenty pixels at row
    /// twenty and two hundred at row two hundred. Nothing else would ever have
    /// said so ([ADR-0257]).
    ///
    /// Read off the **cascade** rather than off a measurement, which is what
    /// makes it exact and free: `list-row` declares `height:
    /// var(--gb-list-row-height)`, so the number is resolved before this row is
    /// laid out and no `Measured` round trip is needed to learn it. A row whose
    /// height is `auto` says nothing — there is no declared number to disagree
    /// with, and guessing from one frame's measurement would report a row that
    /// had not finished arriving.
    private void warnIfTheRowIsNotThePitch(ComputedStyle style) {
        if (checkedPitch <= 0) {
            // Either the list is not virtualizing -- every row is built, so
            // nothing depends on them being one height -- or this is not the row
            // that does the checking. See [#checkedPitch].
            return;
        }
        if (!(style.height() instanceof StyleLength.Points points)) {
            return;
        }
        if (Math.abs(points.value() - checkedPitch) <= SLACK) {
            return;
        }
        var pair = checkedPitch + "/" + points.value();
        if (!SEEN_PITCH.add(pair)) {
            // Seen before, so it is not the settling frame. `add` returns false
            // when it was already there, which is the whole test.
            report(pair, points.value());
        }
    }

    /// Says it, once per distinct disagreement.
    private void report(String pair, double resolved) {
        if (REPORTED_PITCH.add(pair)) {
            LOG.warn(
                    "a virtualized list is spacing rows {}px apart and `list-row` resolves to {}px;"
                            + " the rows will drift out of step with the scrollbar, further with every"
                            + " row. Use ListView.virtualized() with no argument, which reads"
                            + " --gb-list-row-height, or make the two numbers agree.",
                    checkedPitch,
                    resolved);
        }
    }

    @Override
    public Role role() {
        return Role.ROW;
    }

    /// No name of its own: a row is named by the content it was handed.
    @Override
    public @Nullable String accessibleName() {
        return null;
    }
}
