package io.github.digitalsmile.goldberry.widgets.panel.list;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;

/// The two things a list remembers, and neither is its value.
///
/// The selection is the application's ([ADR-0063]); what is kept here is the
/// **anchor** a `Shift` range runs from and the **typeahead** in progress. Both
/// are facts about what the user did a moment ago rather than about the model,
/// which is exactly what a widget's state is for.
///
/// Unlike [io.github.digitalsmile.goldberry.widgets.panel.tree.Tree], there is no
/// flattened list to keep: a list's rows *are* its items, in order, so every
/// handler reads `widget().items()` and there is no second copy to fall out of
/// step with the one on screen.
final class ListState<T> extends State<ListView<T>> {

    /// The window, for the keyboard moves that land on another row — captured in
    /// `build`, which is the only place a widget is handed one ([ADR-0140]).
    private Host host;

    /// How long a typeahead lasts before the next letter starts a new one.
    ///
    /// A second, which is `select`'s figure and `tree`'s and the interval every
    /// desktop list uses ([ADR-0141]): long enough to type "no" and reach Norway
    /// rather than Oman, short enough that coming back a moment later starts
    /// again.
    private static final long TYPEAHEAD_MILLIS = 1000;

    /// The typeahead so far, and when it was last added to.
    private String typed = "";
    private long typedAt;

    /// The row a `Shift` range runs **from** — the last one chosen without it.
    ///
    /// State, because it is exactly what a modifier cannot carry: `Shift` says
    /// "through to here" and the *here it started from* is a fact about what the
    /// user did two gestures ago. Kept as an id, and allowed to go stale — a row
    /// that has left the model simply fails to be found and the range starts at
    /// the pressed row, which is what a reader who has re-sorted the model
    /// underneath their own selection means anyway.
    private String anchor;

    /// How many rows beyond each edge of the viewport are built anyway.
    ///
    /// [Located] is **last frame's** (ADR-0119 rule 1), so a wheel that travels
    /// half a viewport between two frames would show a band of nothing for one of
    /// them. Four rows is the cheapest insurance against that — 128 logical pixels
    /// at the default height, which is more than a detent moves — and it costs
    /// eight built rows on a list of any size.
    private static final int OVERSCAN = 4;

    /// What the first frame builds, before anything has been laid out.
    ///
    /// A guess, and it has to be one: the window is computed from a painted
    /// rectangle and the first frame is what *produces* that rectangle. The
    /// spacers make the guess harmless — the column's total height is right from
    /// the first frame however few rows are in it, so nothing jumps when the
    /// second frame corrects the window.
    private static final int FIRST_GUESS = 40;

    /// The half-open range of item indices currently built, `[first, last)`.
    private int first;
    private int last = FIRST_GUESS;

    /// `--gb-list-row-height`'s default, in logical pixels — the regular
    /// density's, and what a list gets when nothing has styled its tree.
    private static final double ROW_HEIGHT = 32;

    /// The token that overrides it.
    private static final String ROW_HEIGHT_TOKEN = "--gb-list-row-height";

    /// How tall a row is, as the last build resolved it.
    ///
    /// Held rather than read from the widget because
    /// [ListView#rowHeightFromToken()] is an instruction to go and ask rather
    /// than a height, and `located` runs where there is nothing to ask
    /// (ADR-0254).
    private double rowHeight;

    @Override
    public Widget build(BuildContext context) {
        host = context.host().orElse(null);
        var list = widget();
        // **Nothing is an answer in a `NONE` list**, which is a rule about the
        // selection rather than about any one item -- so it is asked once here
        // rather than per row.
        var selectable = list.selection() != Selection.NONE && list.onSelect() != null;
        var typeahead = list.text() != null;
        var items = list.items();
        // Resolved here and **banked**, because `located` needs the same number
        // and runs from the router with no context at all (ADR-0254). Build runs
        // before `located` in every frame, so the banked value is never older
        // than the geometry it is measured against.
        rowHeight = list.rowHeightFromToken() ? context.token(ROW_HEIGHT_TOKEN, ROW_HEIGHT) : list.rowHeight();
        var virtual = rowHeight > 0;
        var from = virtual ? Math.min(first, items.size()) : 0;
        var to = virtual ? Math.min(last, items.size()) : items.size();

        var children = new ArrayList<Widget>(to - from + 2);
        if (virtual && from > 0) {
            children.add(new ListBox.ListSpacer(from * rowHeight));
        }
        for (var index = from; index < to; index++) {
            var item = items.get(index);
            var id = list.identity().apply(item);
            children.add(new ListRow(
                    rowId(id),
                    selectable,
                    list.selected().contains(id),
                    list.factory().apply(item),
                    menuOf(item),
                    modifiers -> select(id, modifiers),
                    this::moveToEnd,
                    typeahead ? text -> typeahead(id, text) : null));
        }
        if (virtual && to < items.size()) {
            children.add(new ListBox.ListSpacer((items.size() - to) * rowHeight));
        }
        return new ListBox(children, virtual ? this::located : null, list.attributes());
    }

    /// Told where the frame put the list and what clips it — the whole of the
    /// virtualization, and it is two divisions.
    ///
    /// `self.top()` is where the list has been **scrolled to** rather than where
    /// it was laid out, so the distance from it down to the clip's top is exactly
    /// how far into the model the viewport has reached ([ADR-0119]).
    ///
    /// It changes what is *built* and never what this node *measures*, which is
    /// what keeps it from oscillating: the spacers absorb every row the window
    /// leaves out, so the rectangle reported by the next frame is the one that
    /// produced this window.
    private void located(
            io.github.digitalsmile.goldberry.render.model.LogicalRect self,
            io.github.digitalsmile.goldberry.render.model.LogicalRect clip) {

        var list = widget();
        var height = rowHeight;
        if (height <= 0 || list.items().isEmpty()) {
            return;
        }
        var above = clip.top() - self.top();
        var wantedFirst = (int) Math.max(0, Math.floor(above / height) - OVERSCAN);
        var visible = (int) Math.ceil(clip.size().height() / height) + 1 + 2 * OVERSCAN;
        var wantedLast = Math.min(list.items().size(), wantedFirst + visible);
        // Widened, never narrowed, by whatever the keyboard is reaching for: a
        // row that is being focused has to exist to be focused (see #reach).
        if (reaching >= 0) {
            wantedFirst = Math.min(wantedFirst, reaching);
            wantedLast = Math.max(wantedLast, reaching + 1);
        }
        if (wantedFirst == first && wantedLast == last) {
            return;
        }
        var nextFirst = wantedFirst;
        var nextLast = wantedLast;
        setState(() -> {
            first = nextFirst;
            last = nextLast;
        });
    }

    /// The index the keyboard is on its way to, or -1.
    ///
    /// **The one thing virtualization breaks and has to put back.** `Home`, `End`
    /// and the typeahead all move the focus by *name* through `host.focus`
    /// ([ADR-0176]), and a name resolves against the element tree — so a virtual
    /// list asked for its last row was asking for a row that does not exist, and
    /// `End` did nothing at all.
    ///
    /// So the move is two steps: build the row, then focus it. This holds the
    /// index between them, and [#located] keeps the window over it so that a
    /// frame arriving in the middle cannot take it away again.
    private int reaching = -1;

    /// How many turns of the loop a reach will wait for its row to be built.
    ///
    /// **Two, and it is a retry rather than a delay**, because the order is not
    /// guaranteed: the frame loop fires its timers *after* the platform pump, and
    /// whether the repaint a `setState` asked for was drawn inside that pump or is
    /// still queued depends on the pacer. So the reach asks, and asks again if the
    /// tree has not caught up — which is decidable, because
    /// [io.github.digitalsmile.goldberry.Host#focus] answers whether it found
    /// anything.
    ///
    /// Bounded, so an id that names no row at all costs two turns and stops rather
    /// than re-arming a timer for the life of the window.
    private static final int REACH_ATTEMPTS = 2;

    /// Focuses the row for `id`, building it first when it is outside the window.
    ///
    /// The cheap path is the common one: a row already in the window is focused
    /// on the spot, which is what every arrow key does. Only a jump to a row the
    /// window does not hold — `End` on ten thousand, or a typeahead reaching the
    /// far end — has to widen the window and wait for the rebuild.
    private void reach(String id) {
        if (host == null) {
            return;
        }
        var index = indexOf(id);
        if (rowHeight <= 0 || (index >= first && index < last)) {
            host.focus(rowId(id), true);
            return;
        }
        reaching = index;
        setState(() -> {
            first = Math.min(first, index);
            last = Math.max(last, index + 1);
        });
        reachAgain(id, REACH_ATTEMPTS);
    }

    /// One attempt at the focus, with the ones that are left.
    ///
    /// Releases [#reaching] as soon as the focus lands or the attempts run out —
    /// holding it would pin a row nobody is looking at in the window for ever.
    private void reachAgain(String id, int attemptsLeft) {
        host.after(java.time.Duration.ZERO, () -> {
            if (host.focus(rowId(id), true) || attemptsLeft <= 1) {
                reaching = -1;
                return;
            }
            reachAgain(id, attemptsLeft - 1);
        });
    }

    /// §10's per-item context menu, as attributes on the row.
    ///
    /// [Attributes#NONE] when the item names none, which is also what a list with
    /// no `itemMenu` gives every row — an attributes object that says nothing is
    /// the same answer as no attributes, and one branch is cheaper to read than
    /// two.
    private Attributes menuOf(T item) {
        if (widget().itemMenu() == null) {
            return Attributes.NONE;
        }
        var named = widget().itemMenu().apply(item);
        return named == null || named.isBlank() ? Attributes.NONE : Attributes.NONE.contextMenu(named);
    }

    /// A row's focus name, **scoped to its list**.
    ///
    /// `host.focus` takes a name that is global to the window ([ADR-0176]), so
    /// two lists showing items with the same identity would each answer to the
    /// other's `Home`. Prefixing with the list's own `id` settles it wherever the
    /// application gave one — which is the case a screen with two lists on it
    /// already has, because a stylesheet needs to tell them apart too.
    ///
    /// A list with no `id` keeps the bare prefix and the collision with it. That
    /// is `tree`'s behaviour unchanged, and the residue is small: two lists, both
    /// unnamed, holding an item with the same identity.
    private String rowId(String itemId) {
        var own = widget().attributes().id();
        return (own == null ? "list" : own) + "-" + itemId;
    }

    /// §10's `Home` and `End`: the first and last rows of the model.
    private void moveToEnd(int direction) {
        var items = widget().items();
        if (items.isEmpty() || host == null) {
            return;
        }
        var item = direction < 0 ? items.getFirst() : items.getLast();
        // Through #reach rather than straight to the host: on a virtual list the
        // last row is exactly the one that is not built.
        reach(widget().identity().apply(item));
    }

    /// §10's type-to-select, over the rows as they are on screen.
    ///
    /// The three cases are `select`'s and `tree`'s, and the middle one is why
    /// this is not a string concatenation: the **same letter again** asks for the
    /// next row beginning with it rather than searching for "dd". Wrapping,
    /// because the rows are a cycle to a reader pressing one key repeatedly.
    ///
    /// It moves the **focus** and does not select. A list reports what the user
    /// asked for and selects nothing itself ([ADR-0063]), and typing is a way of
    /// getting somewhere rather than a way of choosing — `Enter` is still what
    /// chooses, which is the same split `select`'s open list draws.
    private void typeahead(String from, String text) {
        var items = widget().items();
        if (items.isEmpty() || host == null) {
            return;
        }
        var now = now();
        var stale = now - typedAt > TYPEAHEAD_MILLIS;
        typedAt = now;
        if (stale || (text.length() == 1 && typed.equals(text))) {
            typed = text;
        } else {
            typed = typed + text;
        }

        var here = indexOf(from);
        // A repeated single letter steps on from where the focus is; a longer
        // prefix starts from the current row, because "no" then "nor" must not
        // skip Norway for having matched it once already.
        var start = typed.length() == 1 ? here + 1 : Math.max(0, here);
        var match = matching(start);
        if (match == null && start > 0) {
            match = matching(0);
        }
        if (match != null && !match.equals(from)) {
            reach(match);
        }
    }

    /// Where an id sits in the model, or -1.
    private int indexOf(String id) {
        var items = widget().items();
        for (var i = 0; i < items.size(); i++) {
            if (widget().identity().apply(items.get(i)).equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /// The identity of the first item from `start` onwards whose text begins with
    /// what has been typed, or null.
    private String matching(int start) {
        var list = widget();
        var wanted = typed.toLowerCase(Locale.ROOT);
        var items = list.items();
        for (var i = Math.max(0, start); i < items.size(); i++) {
            var item = items.get(i);
            var text = list.text().apply(item);
            if (text != null && text.toLowerCase(Locale.ROOT).startsWith(wanted)) {
                return list.identity().apply(item);
            }
        }
        return null;
    }

    /// Asks for a selection. It does **not** select — the value is the
    /// application's ([ADR-0063]).
    ///
    /// **What goes out is the whole set**, even in single-selection mode where it
    /// always holds one. A `Shift` range is computed over the rows, which only
    /// this class can see, so an id on its own would be an answer the application
    /// could not turn back into a selection. [ListView#selected(String, Consumer)]
    /// unwraps it again for the callers that have one value.
    private void select(String id, Modifiers modifiers) {
        var list = widget();
        if (list.onSelect() == null || list.selection() == Selection.NONE) {
            return;
        }
        if (list.selection() == Selection.SINGLE) {
            // Modifiers are ignored rather than refused: `Ctrl` and `Shift` mean
            // "and also" and "through to", and a control holding one row has
            // nothing to say to either. Reporting the row is the whole answer.
            anchor = id;
            list.onSelect().accept(java.util.Set.of(id));
            return;
        }
        var next = new LinkedHashSet<String>();
        if (modifiers.shift()) {
            // **Replaces rather than adds.** A shifted press is "the range from
            // there to here", and a reader who over-shoots presses `Shift` again
            // one row back and gets the range they meant -- which is only true if
            // the second press is not adding to the first. The anchor is
            // deliberately not moved, so a run of them sweeps from one end.
            next.addAll(rangeTo(id));
        } else if (modifiers.control()) {
            // The only way to take a row *out* of a selection, which is why the
            // toggle lives on `Ctrl` and not on a plain press.
            next.addAll(list.selected());
            if (!next.remove(id)) {
                next.add(id);
            }
            anchor = id;
        } else {
            next.add(id);
            anchor = id;
        }
        list.onSelect().accept(Collections.unmodifiableSet(next));
    }

    /// Every row from the anchor through `id`, in the order they are on screen.
    private List<String> rangeTo(String id) {
        var to = indexOf(id);
        if (to < 0) {
            return List.of();
        }
        var from = anchor == null ? to : indexOf(anchor);
        if (from < 0) {
            // The anchor has left the model. The pressed row is the range.
            from = to;
        }
        var items = widget().items();
        var out = new ArrayList<String>(Math.abs(to - from) + 1);
        for (var i = Math.min(from, to); i <= Math.max(from, to); i++) {
            out.add(widget().identity().apply(items.get(i)));
        }
        return out;
    }

    /// How long ago the previous keystroke arrived, on the window's clock.
    ///
    /// The **host's** and not `System.nanoTime()`, which is what this used to
    /// read. A typeahead measured against the real clock is the one behaviour in
    /// this catalog a test cannot drive: asserting that a gap longer than the
    /// window starts a fresh search meant sleeping for it and hoping, so nothing
    /// asserted it. Against a `Clock.virtual()` it is `advance(600)` and a
    /// keystroke (`docs/testing.md` §0.1).
    ///
    /// Falls back to the system clock when there is no host, which is a widget
    /// built and driven outside a window. Typeahead still works there; it is
    /// simply not drivable, and there is nothing else to read.
    private long now() {
        return (long)
                (host == null
                        ? io.github.digitalsmile.goldberry.motion.Clock.system().nowMillis()
                        : host.clock().nowMillis());
    }
}
