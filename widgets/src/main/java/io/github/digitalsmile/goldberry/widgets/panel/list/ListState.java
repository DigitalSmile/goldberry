package io.github.digitalsmile.goldberry.widgets.panel.list;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

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

    @Override
    public Widget build(BuildContext context) {
        host = context.host().orElse(null);
        var list = widget();
        // **Nothing is an answer in a `NONE` list**, which is a rule about the
        // selection rather than about any one item -- so it is asked once here
        // rather than per row.
        var selectable = list.selection() != Selection.NONE && list.onSelect() != null;
        var typeahead = list.text() != null;
        var rows = new ArrayList<Widget>(list.items().size());
        for (var item : list.items()) {
            var id = list.identity().apply(item);
            rows.add(new ListRow(rowId(id), selectable,
                    list.selected().contains(id),
                    list.factory().apply(item),
                    menuOf(item),
                    modifiers -> select(id, modifiers),
                    this::moveToEnd,
                    typeahead ? text -> typeahead(id, text) : null));
        }
        return new ListBox(rows, list.attributes());
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
        return named == null || named.isBlank()
                ? Attributes.NONE
                : Attributes.NONE.contextMenu(named);
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
        host.focus(rowId(widget().identity().apply(item)), true);
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
        var now = System.currentTimeMillis();
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
            host.focus(rowId(match), true);
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
}
