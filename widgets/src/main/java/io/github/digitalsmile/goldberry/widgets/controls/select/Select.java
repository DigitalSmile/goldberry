package io.github.digitalsmile.goldberry.widgets.controls.select;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import io.github.digitalsmile.goldberry.bind.Observable;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.attr.Bindable;
import io.github.digitalsmile.goldberry.widgets.controls.option.Option;
import io.github.digitalsmile.goldberry.widgets.controls.option.Suggested;
import io.github.digitalsmile.goldberry.widgets.markup.Markup;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;

/// A closed control with a list under it — `docs/core-widgets.md` §3's `select`.
///
/// ```kdl
/// select bind="app.theme" change="pickTheme" placeholder="Choose a theme" {
///     option value="nord-dark"  "Nord Dark"
///     option value="nord-light" "Nord Light"
/// }
/// ```
///
/// ```java
/// Select.of(model.theme(), model::pickTheme,
///         new Option("nord-dark", "Nord Dark"),
///         new Option("nord-light", "Nord Light"))
/// ```
///
/// ## It is `segmented`'s model with a popup instead of a bar
///
/// The value, the options, `change` and the exactly-one invariant are
/// [io.github.digitalsmile.goldberry.widgets.controls.segmented.Segmented]'s
/// exactly, down to the widget the options are —
/// [Option], which moved into a package of its own the day this control
/// needed it. What differs is where the choices are: a bar shows all of them
/// and this shows one, so the rest have to be *somewhere*, and that somewhere
/// is a platform window (§3: "backend popup window, so it escapes window
/// bounds") ([ADR-0141]).
///
/// ## What it is made of
///
/// ```
/// select                 this node. Stateful, styles nothing, holds whether the list is open
/// └── select-field       the closed control: focusable, takes the click and the keys
///     ├── select-value   the chosen label, or the placeholder
///     └── select-chevron the mark saying there is a list under this
///
/// select-list            in a popup window of its own, when open
/// └── option × n         the same widget a `segmented` puts in a bar
/// ```
///
/// Stateful and unstyled for the reason
/// [io.github.digitalsmile.goldberry.widgets.core.scroll.Scroll] gives: a
/// stateful widget that also carried the CSS type would put two `select` nodes
/// in the cascade, one inside the other, and every rule would apply twice.
///
/// ## Controlled, like every other value in this toolkit
///
/// It **reads** its value through `bind` and reports what the user asked for
/// through `change`; it sets nothing ([ADR-0063]). A select whose handler does
/// nothing opens, closes and never changes its label — which is the visible form
/// of "the state did not change".
///
/// ## Opening one needs a window, and this has one
///
/// `menu` cannot open itself: opening needs a `Host`, and a widget is a value
/// rebuilt every frame ([ADR-0106]). That reasoning holds and the conclusion does
/// not transfer, because opening a menu is something an *application* does and
/// opening a select is something the *control* does — a user who clicks a
/// dropdown has not asked the application anything. So the host arrives through
/// [io.github.digitalsmile.goldberry.widget.BuildContext#host()], the widget
/// still holds nothing, and a `select` built with no window behind it — a golden
/// image, a layout preview — draws its closed form and refuses to open
/// ([ADR-0140]).
///
/// @param value       the option selected when nothing is bound; ignored when
///                    `source` is set
/// @param children    the options, as written. Non-[Option] children are kept and
///                    shown in the list, so a heading between two groups survives
/// @param source      §9's `bind` — read-only, so this control cannot write to
///                    the model even by accident ([ADR-0063])
/// @param onChange    what to tell the application, given the picked value
/// @param placeholder what the closed control reads when nothing is selected.
///                    Empty for a blank field, which is what a select with a
///                    value it does not recognise falls back to
/// @param multiple    §3's `multiple=#true` — the selection is a *set*, drawn as
///                    chips in the closed control. See [#resolvedAll()]
/// @param autocomplete §3's `autocomplete=#true` — the closed control becomes an
///                    editable `text-input` and typing raises [#onQuery]
/// @param free        whether a typed value the options do not offer is kept.
///                    False refuses it and restores the last committed one, which
///                    is §3's default: a combobox is a *set* of values
/// @param onQuery     what was typed, for the application to filter on. Filtering
///                    is deliberately not this control's — see [#onQuery]
/// @param tree        §3's `tree=#true` — the popup is a
///                    [io.github.digitalsmile.goldberry.widgets.panel.tree.Tree]
///                    over these roots instead of a flat option list, or empty
/// @param disabled    whether the whole control refuses to open
/// @param attributes  `id` and `class`, exactly as on the primitives
@Markup("select")
public record Select(
        String value,
        List<Widget> children,
        Observable<?> source,
        Consumer<String> onChange,
        String placeholder,
        boolean multiple,
        boolean autocomplete,
        boolean free,
        Consumer<String> onQuery,
        List<io.github.digitalsmile.goldberry.widgets.panel.tree.TreeNode> tree,
        boolean disabled,
        Attributes attributes)
        implements Widget.Stateful, Attributed<Select>, Bindable<Select> {

    public Select {
        tree = List.copyOf(
                tree == null ? List.<io.github.digitalsmile.goldberry.widgets.panel.tree.TreeNode>of() : tree);
        children = List.copyOf(children == null ? List.of() : children);
        placeholder = placeholder == null ? "" : placeholder;
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    /// A select with a value and a handler, unbound — the Java spelling of
    /// `select value="…" change="…"`.
    public Select(String value, Consumer<String> onChange, Option... options) {
        this(value, List.of(options), null, onChange, "", false, false, false, null, List.of(), false, Attributes.NONE);
    }

    /// A select that is not wired yet — what a layout preview builds.
    public Select(Option... options) {
        this(null, List.of(options), null, null, "", false, false, false, null, List.of(), false, Attributes.NONE);
    }

    /// A select that follows a property. The Java spelling of `bind=`.
    ///
    /// @param source read-only by construction ([ADR-0063])
    public static Select of(Observable<?> source, Consumer<String> onChange, Option... options) {
        return new Select(
                null,
                List.of(options),
                Objects.requireNonNull(source, "source"),
                onChange,
                "",
                false,
                false,
                false,
                null,
                List.of(),
                false,
                Attributes.NONE);
    }

    /// This select with the text its closed form reads when nothing is chosen.
    public Select placeholder(String value) {
        return new Select(
                this.value,
                children,
                source,
                onChange,
                value,
                multiple,
                autocomplete,
                free,
                onQuery,
                tree,
                disabled,
                attributes);
    }

    /// This select, disabled or not.
    public Select disabled(boolean value) {
        return new Select(
                this.value,
                children,
                source,
                onChange,
                placeholder,
                multiple,
                autocomplete,
                free,
                onQuery,
                tree,
                value,
                attributes);
    }

    /// Which option is selected **right now** — the bound value, or [#value()].
    ///
    /// Compared by `toString` for `segmented`'s reason: a property holding an
    /// enum, an `Integer` or a `String` all work against the strings a document
    /// wrote. A null, or a value no option carries, selects nothing rather than
    /// the first one — a control that guessed would report a value the user never
    /// picked, and this one would then show it as though they had.
    public String resolved() {
        if (source == null) {
            return value;
        }
        var current = source.get();
        return current == null ? null : String.valueOf(current);
    }

    /// This select taking more than one value — §3's `multiple=#true`.
    public Select multiple(boolean value) {
        return new Select(
                this.value,
                children,
                source,
                onChange,
                placeholder,
                value,
                autocomplete,
                free,
                onQuery,
                tree,
                disabled,
                attributes);
    }

    /// This select with an editable closed control — §3's `autocomplete=#true`.
    ///
    /// ## Filtering is the application's
    ///
    /// The control raises what was typed through `query` and renders **whatever
    /// options it is handed back**; it filters nothing itself. §3 says so and
    /// gives the reason: a remote-backed autocomplete is then the same widget
    /// with a slower model, and nothing in the toolkit has to guess what
    /// "matches" means for a street address or a species name.
    ///
    /// So an application answers `query` by rebuilding this select with the
    /// options it wants offered — the same round trip `change` already makes, and
    /// the same one §4's free-text
    /// [io.github.digitalsmile.goldberry.widgets.form.textinput.TextInput#suggesting]
    /// makes ([ADR-0183]).
    ///
    /// @param onQuery told what was typed, or null for a control nobody filters
    public Select autocomplete(Consumer<String> onQuery) {
        return new Select(
                value,
                children,
                source,
                onChange,
                placeholder,
                multiple,
                true,
                free,
                onQuery,
                tree,
                disabled,
                attributes);
    }

    /// This select keeping a typed value its options do not offer.
    ///
    /// **False by default, which is §3's rule**: a combobox is a set of values
    /// with a faster way to reach them, so text that names none of them is a
    /// mistake rather than a new value, and the last committed one comes back.
    /// `free=#true` is the other reading — the suggestions are a convenience and
    /// any value is legal, which is what §4's free-text form always is.
    public Select free(boolean value) {
        return new Select(
                this.value,
                children,
                source,
                onChange,
                placeholder,
                multiple,
                autocomplete,
                value,
                onQuery,
                tree,
                disabled,
                attributes);
    }

    /// This select opening a **tree** instead of a flat list — §3's `tree=#true`.
    ///
    /// "Takes a `tree`'s model instead of a flat option list, so the popup is a
    /// `tree` and a selection is a node." The closed control is unchanged: it
    /// still shows a label and a chevron, and still reports through `change`.
    ///
    /// Selection is **leaf-only**, which is §3's default and its reason —
    /// "'Europe' is usually a heading and not an answer". A parent row is still
    /// navigable and openable; it is simply not a value
    /// (ADR-0184).
    public Select tree(List<io.github.digitalsmile.goldberry.widgets.panel.tree.TreeNode> roots) {
        return new Select(
                value,
                children,
                source,
                onChange,
                placeholder,
                multiple,
                autocomplete,
                free,
                onQuery,
                roots,
                disabled,
                attributes);
    }

    /// Whether this select's popup is a tree.
    public boolean isTree() {
        return !tree.isEmpty();
    }

    /// The label of the node [#resolved()] names, searched depth-first — what the
    /// closed control reads when the popup is a tree.
    ///
    /// A tree's rows are not [Option]s, so [#selected()] cannot answer for one:
    /// the model is a different shape and the label has to be found in it.
    public String treeLabel() {
        var current = resolved();
        return current == null ? null : labelIn(tree, current);
    }

    private static String labelIn(List<io.github.digitalsmile.goldberry.widgets.panel.tree.TreeNode> nodes, String id) {
        for (var node : nodes) {
            if (node.id().equals(id)) {
                return node.label();
            }
            var found = labelIn(node.children(), id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /// Every value selected right now, in the order the options were written.
    ///
    /// [#resolved()]'s rule, applied to a set: a bound `Collection` becomes the
    /// strings its elements stringify to, and anything else becomes one value —
    /// so a property that starts as a single value and becomes a list, or the
    /// other way round, does not have to be a different kind of binding. A null
    /// is nothing selected rather than one null selected.
    ///
    /// Ordered by the **options** and not by the model, so removing a chip and
    /// putting the value back does not move it to the end of the row. Duplicates
    /// in the model collapse, because two chips saying the same word are two
    /// affordances doing one thing.
    public List<String> resolvedAll() {
        var wanted = boundValues();
        if (wanted.isEmpty()) {
            return List.of();
        }
        var out = new ArrayList<String>(wanted.size());
        for (var option : options()) {
            if (wanted.contains(option.value()) && !out.contains(option.value())) {
                out.add(option.value());
            }
        }
        return List.copyOf(out);
    }

    /// The values the model names, before the options have had a say — unordered
    /// and possibly naming options this select does not offer.
    private java.util.Set<String> boundValues() {
        if (source == null) {
            return value == null || value.isEmpty() ? java.util.Set.of() : java.util.Set.of(value);
        }
        return switch (source.get()) {
            case null -> java.util.Set.of();
            case java.util.Collection<?> many -> {
                var set = new java.util.LinkedHashSet<String>();
                for (var element : many) {
                    if (element != null) {
                        set.add(String.valueOf(element));
                    }
                }
                yield set;
            }
            case Object one -> java.util.Set.of(String.valueOf(one));
        };
    }

    /// The options [#resolvedAll()] names, as options.
    public List<Option> selectedOptions() {
        var chosen = resolvedAll();
        var out = new ArrayList<Option>(chosen.size());
        for (var option : options()) {
            if (chosen.contains(option.value())) {
                out.add(option);
            }
        }
        return List.copyOf(out);
    }

    /// The options this select offers, in the order they were written.
    public List<Option> options() {
        var out = new ArrayList<Option>(children.size());
        for (var child : children) {
            if (child instanceof Option option) {
                out.add(option);
            }
        }
        return List.copyOf(out);
    }

    /// The option [#resolved()] names, or null when nothing is selected.
    public Option selected() {
        var current = resolved();
        if (current == null) {
            return null;
        }
        for (var option : options()) {
            if (option.value().equals(current)) {
                return option;
            }
        }
        return null;
    }

    /// What the closed control reads: the selected option's label, or the
    /// placeholder.
    ///
    /// The label and not the value, because they are different words on purpose —
    /// `option value="nord-dark" "Nord Dark"` exists so that a model can hold a
    /// key and a user can read a name.
    public String label() {
        if (isTree()) {
            var found = treeLabel();
            return found == null ? placeholder : found;
        }
        var option = selected();
        return option == null ? placeholder : option.label();
    }

    @Override
    public Select bound(Observable<?> source) {
        return new Select(
                value,
                children,
                source,
                onChange,
                placeholder,
                multiple,
                autocomplete,
                free,
                onQuery,
                tree,
                disabled,
                attributes);
    }

    @Override
    public Select withAttributes(Attributes attributes) {
        return new Select(
                value,
                children,
                source,
                onChange,
                placeholder,
                multiple,
                autocomplete,
                free,
                onQuery,
                tree,
                disabled,
                attributes);
    }

    @Override
    public Observable<?> binding() {
        return source;
    }

    @Override
    public Object key() {
        return attributes.key();
    }

    @Override
    public State<?> createState() {
        return new SelectState();
    }

    /// Builds a `select` from markup.
    ///
    /// The same valued action `segmented` and `radio-group` take: a set's handler
    /// is useless without the value picked (ADR-0073).
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        var select = control(node, children, wiring);
        // `options=` names a bound list that replaces the written options each
        // time it changes -- what an autocomplete's `query` is answered with
        // (ADR-0367).
        var options = node.stringProperty("options");
        return options == null ? select : new Suggested(wiring.bindings().resolve(options), select::withOptions);
    }

    /// This control offering `options` in place of the options it was written with.
    public Select withOptions(List<Option> options) {
        var next = new ArrayList<Widget>(children.size());
        for (var child : children) {
            if (!(child instanceof Option)) {
                next.add(child);
            }
        }
        next.addAll(options);
        return new Select(
                value,
                next,
                source,
                onChange,
                placeholder,
                multiple,
                autocomplete,
                free,
                onQuery,
                tree,
                disabled,
                attributes);
    }

    private static Select control(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Select(
                node.stringProperty("value"),
                children,
                wiring.bound(node),
                wiring.valued(node, "change"),
                node.stringProperty("placeholder"),
                node.booleanProperty("multiple"),
                node.booleanProperty("autocomplete"),
                node.booleanProperty("free"),
                wiring.valued(node, "query"),
                // Not from markup: a tree's model is nodes with suppliers under
                // them, which is a shape KDL has no way to write and which §3
                // describes as "a `tree`'s model" — the application's (ADR-0184).
                List.of(),
                Wiring.disabled(node),
                Attributes.of(node));
    }
}
