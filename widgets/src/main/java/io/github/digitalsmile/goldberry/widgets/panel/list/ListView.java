package io.github.digitalsmile.goldberry.widgets.panel.list;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// A vertical list over an item model — `docs/core-widgets.md` §10's `list`.
///
/// ```java
/// ListView.of(List.of("Norway", "Sweden", "Finland"))
///         .selected(chosen, this::pick)
/// ```
///
/// ## Why it is not called `List`
///
/// The CSS type is `list` and the class is not, because a widget named `List`
/// would shadow `java.util.List` in every file that built one — including this
/// one, whose model *is* a `java.util.List`. `ListBox` is the node a stylesheet
/// sees; this is the widget an application writes, in the same arrangement
/// `Tree`/`TreeBox` already uses.
///
/// ## Any widget as a row
///
/// §10 asks for "an item-factory (any widget as row)", so an item is whatever
/// the application has and [#factory] turns one into a row. Three functions
/// rather than an interface to implement, because the common case is three
/// lambdas and the rare one is a method reference each:
///
/// - [#identity] — what the item *is*, as a string. Selection, focus and the
///   reconciler's key all run through it, so two items with one identity are one
///   row as far as this widget is concerned.
/// - [#factory] — what the item *looks* like.
/// - [#text] — what the item *reads* as, for §10's "type-to-select when items
///   expose text". Optional: a list of colour swatches exposes no text and gets
///   no typeahead, which is the honest answer rather than a typeahead that
///   matches nothing.
///
/// The factory is called during `build` and its widget becomes the row's child,
/// which is what makes the recycling §10 promises for v1.x a performance change
/// rather than an API break: a recycler calls the same function with a different
/// item.
///
/// ## Controlled, like every other value in this toolkit
///
/// It **reads** which rows are selected and reports what the user asked for; it
/// selects nothing itself ([ADR-0063]). What goes out is the whole set even in
/// single-selection mode, for [Selection#MULTIPLE]'s reason — a `Shift` range is
/// computed over rows only this widget can see. The [#selected(String, Consumer)]
/// pair unwraps it again for a caller that holds one value.
///
/// ## What it is made of
///
/// ```
/// list                 this node. Stateful, styles nothing, holds the anchor
/// ├── list-spacer      the rows above the window, as a height — virtual only
/// ├── list-row × n     one per item in the window, carrying the factory's widget
/// │   └── …            whatever the factory returned
/// └── list-spacer      the rows below it, likewise
/// ```
///
/// The two spacers are absent entirely unless [#virtualized(double)] is on, in
/// which case the window is every row.
///
/// Stateful and unstyled for [io.github.digitalsmile.goldberry.widgets.core.scroll.Scroll]'s
/// reason: a stateful widget that also carried the CSS type would put two `list`
/// nodes in the cascade, one inside the other, and every rule would apply twice.
///
/// ## Every row, unless it is told a row height
///
/// §10's v1 "renders instantiated rows — fine into the low thousands", and that
/// is what a list does by default. [#virtualized(double)] is §10's committed
/// follow-up: told how tall a row is, the list builds **only the rows the
/// viewport can see** and stands the rest off with two spacers
/// (ADR-0213).
///
/// Opt-in, and it takes a number rather than a flag, because the number is the
/// one thing the widget cannot find out: §8's subset resolves
/// `--gb-list-row-height` for the cascade and no widget can read a resolved
/// custom property. A caller that styles its rows to a different height passes
/// that height here, and one that does not virtualize passes nothing.
///
/// Either way a list taller than its box is a
/// [io.github.digitalsmile.goldberry.widgets.core.scroll.Scroll]'s to scroll,
/// exactly as a tree is. `Home` and `End` go to the ends of the **model** rather
/// than of the viewport, and the focus ring is what asks an ancestor to follow
/// ([ADR-0120]).
///
/// @param <T>         the item type — anything, including a record or a `String`
/// @param items       the model, in the order it is drawn
/// @param identity    an item's id: its key, its focus name and how a selection
///                    names it
/// @param factory     §10's item-factory — any widget as a row
/// @param text        what an item reads as, for type-to-select; null for none
/// @param itemMenu    the name of a context menu for an item, or null — §10's
///                    "item context menus"
/// @param selected    the ids of the chosen items; empty for none
/// @param onSelect    the selection the user asked for, whole
/// @param selection   how many rows may be chosen at once
/// @param rowHeight   how tall a row is, in logical pixels — the number that
///                    turns virtualization on; zero builds every row
/// @param attributes  `id` and `class`, exactly as on the primitives
public record ListView<T>(
        List<T> items,
        Function<T, String> identity,
        Function<T, Widget> factory,
        Function<T, String> text,
        Function<T, String> itemMenu,
        Set<String> selected,
        Consumer<Set<String>> onSelect,
        Selection selection,
        double rowHeight,
        Attributes attributes)
        implements Widget.Stateful, Attributed<ListView<T>> {

    public ListView {
        items = List.copyOf(items == null ? List.of() : items);
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(factory, "factory");
        // A LinkedHashSet copy rather than Set.copyOf, because the order a caller
        // gave is the order a diagnostic prints and the order a test asserts --
        // and Set.copyOf's is a hash order that changes between runs.
        selected = unmodifiableOrdered(selected);
        selection = selection == null ? Selection.SINGLE : selection;
        if (rowHeight < 0 || Double.isNaN(rowHeight)) {
            throw new IllegalArgumentException("a row height must be a positive number of logical pixels,"
                    + " or zero to build every row; got " + rowHeight);
        }
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    private static Set<String> unmodifiableOrdered(Set<String> values) {
        return values == null || values.isEmpty()
                ? Set.of()
                : java.util.Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }

    /// A list over `items`, with no selection callback yet.
    ///
    /// The two functions §10 cannot supply a default for: what an item *is* and
    /// what it looks like. Everything else has one.
    public ListView(List<T> items, Function<T, String> identity, Function<T, Widget> factory) {
        this(items, identity, factory, null, null, Set.of(), null, Selection.SINGLE, 0, Attributes.NONE);
    }

    /// A list of plain strings, drawn as `text` — the shape a list most often
    /// has, and the one every other constructor here would make a caller spell
    /// out three times.
    ///
    /// The identity **is** the string, which is right for a list of names and
    /// wrong for a list with a duplicate in it: two identical strings are one row
    /// to the selection and to the reconciler. A caller with duplicates has items
    /// that are not strings, and should say what their identity is.
    public static ListView<String> of(List<String> labels) {
        return new ListView<>(
                labels,
                java.util.function.Function.identity(),
                Text::new,
                java.util.function.Function.identity(),
                null,
                Set.of(),
                null,
                Selection.SINGLE,
                0,
                Attributes.NONE);
    }

    /// The one chosen id, or null — the single-selection reading of [#selected].
    ///
    /// Null rather than empty because a caller in single-selection mode has a
    /// value or has none.
    public String selectedOne() {
        return selected.isEmpty() ? null : selected.iterator().next();
    }

    /// This list with `values` selected and `onSelect` told what the user asked
    /// for.
    ///
    /// The two together, because a row nobody is listening to cannot change and a
    /// listener with no value has nothing to draw — ADR-0063's loop needs both
    /// ends or neither.
    public ListView<T> selected(Set<String> values, Consumer<Set<String>> onSelect) {
        return new ListView<>(
                items, identity, factory, text, itemMenu, values, onSelect, selection, rowHeight, attributes);
    }

    /// The same, for the caller that holds **one** value.
    ///
    /// A `String` and not a set of one, for
    /// [io.github.digitalsmile.goldberry.widgets.panel.tree.Tree]'s
    /// reason: an application holds a field, and asking it to wrap that in a set
    /// to hand it over and unwrap it to read it back would be ceremony in the
    /// common case for the benefit of the rare one. What crosses inside is a set
    /// either way.
    public ListView<T> selected(String value, Consumer<String> onSelect) {
        return selected(
                value == null ? Set.of() : Set.of(value),
                onSelect == null
                        ? null
                        : chosen -> onSelect.accept(
                                chosen.isEmpty() ? null : chosen.iterator().next()));
    }

    /// This list with a different selection model — §10's "none / single / multi".
    public ListView<T> selection(Selection value) {
        return new ListView<>(
                items, identity, factory, text, itemMenu, selected, onSelect, value, rowHeight, attributes);
    }

    /// This list with items that expose text, which is what §10 makes
    /// type-to-select conditional on.
    public ListView<T> text(Function<T, String> value) {
        return new ListView<>(
                items, identity, factory, value, itemMenu, selected, onSelect, selection, rowHeight, attributes);
    }

    /// This list building **only the rows its viewport can see** — §10's
    /// committed virtualization, turned on by saying how tall a row is.
    ///
    /// ```java
    /// ListView.of(names).virtualized(32)      // --gb-list-row-height's default
    /// ```
    ///
    /// The height is the caller's because the widget cannot find it out: a
    /// stylesheet resolves `--gb-list-row-height` and no widget can read a
    /// resolved custom property, so a number nobody states is a number nobody
    /// has. A list whose rows are styled to some other height passes that
    /// instead, and a list whose rows vary in height must not virtualize at all
    /// — the arithmetic is index × height and there is no other way to know where
    /// row 4,000 begins without building the 3,999 above it.
    ///
    /// Everything else is unchanged: the same item-factory is called with the
    /// same items, which is what §10 means by "a performance upgrade, not an API
    /// break". `Home`, `End` and the typeahead still reach rows that are not
    /// built (ADR-0213).
    ///
    /// @param height a row's height in logical pixels, or zero to build them all
    public ListView<T> virtualized(double height) {
        return new ListView<>(
                items, identity, factory, text, itemMenu, selected, onSelect, selection, height, attributes);
    }

    /// This list with a context menu per item — §10's "item context menus".
    ///
    /// A function rather than one name, because the point of a per-item menu is
    /// that a folder and a file do not offer the same commands. Returning null
    /// for an item gives that row no menu, which is how a heading opts out.
    ///
    /// It is named on the **row** rather than on whatever the factory returned,
    /// so that both ways in find it: a right-click walks up from what is under
    /// the pointer, and the menu key walks up from what has the focus, which is
    /// the row itself ([ADR-0208]).
    public ListView<T> itemMenu(Function<T, String> value) {
        return new ListView<>(
                items, identity, factory, text, value, selected, onSelect, selection, rowHeight, attributes);
    }

    @Override
    public ListView<T> withAttributes(Attributes value) {
        return new ListView<>(
                items, identity, factory, text, itemMenu, selected, onSelect, selection, rowHeight, value);
    }

    @Override
    public Object key() {
        return attributes.key();
    }

    @Override
    public State<?> createState() {
        return new ListState<T>();
    }
}
