package io.github.digitalsmile.goldberry.widgets;

import io.github.digitalsmile.goldberry.bind.Observable;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.FocusScope;
import io.github.digitalsmile.goldberry.input.Handles;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.Widgets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/// A set of options of which exactly one is chosen (§11,
/// `docs/core-widgets.md` §3).
///
/// ```kdl
/// radio-group bind="prefs.theme" change="pickTheme" {
///     radio value="light" "Light"
///     radio value="dark"  "Dark"
///     radio value="system" "Follow the system"
/// }
/// ```
///
/// ## The group holds the invariant, because no radio can
///
/// "Exactly one of these is on" is a fact about the set. A radio that owned its
/// own checked state would let a document describe two selected options, or none,
/// and every consumer of the group would then need a rule for what that means.
/// So the group holds one value, [#children()] rewrites each [Radio] with whether
/// it matches, and the invariant is true by construction rather than by
/// maintenance — including for a group that is bound to a value none of its
/// options carries, which shows nothing selected and is exactly right for a model
/// that has not loaded.
///
/// ## Controlled, like every other value in this toolkit
///
/// The group **reads** its value through `bind` and reports what the user asked
/// for through `change`. It sets nothing ([ADR-0063]). A radio group whose
/// handler does nothing does not move, which is the visible form of "the state
/// did not change" and is where the bug is when one will not.
///
/// `change` is the first action in the toolkit that has to say **which one**, so
/// it is a `Consumer<String>` taking the picked option's `value` — see
/// [Actions#bind(String, java.util.function.Consumer)]. A plain `Runnable`
/// resolves against it too, for a handler that reads the model itself.
///
/// ## One Tab stop, arrows inside
///
/// [#focusScope()] is what makes a group of six options one Tab stop rather than
/// six (`docs/design-system.md` §7.2). The arrow keys are the router's, not this
/// widget's — see [ADR-0073] — and Tab **re-enters at the selected option**,
/// because the entry point is derived from `:checked` rather than remembered.
///
/// The group is not itself focusable: focus lands on one of its radios, which is
/// what lets the focus ring sit on the option the user is about to pick.
///
/// @param value      the option selected when nothing is bound; ignored when
///                   `source` is set
/// @param children   the options, as written. Non-[Radio] children are laid out
///                   and left alone, so a group can carry a heading
/// @param source     §9's `bind` — read-only, so this control cannot write to the
///                   model even by accident ([ADR-0063])
/// @param onChange   what to tell the application, given the picked value
/// @param disabled   whether the whole group refuses selection; passed down to
///                   every option, so `radio:disabled` matches without a
///                   descendant combinator
/// @param attributes `id` and `class`, exactly as on the primitives
public record RadioGroup(
        String value, List<Widget> children, Observable<?> source, Consumer<String> onChange,
        boolean disabled, Widgets.Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Handles {

    public RadioGroup {
        children = List.copyOf(children == null ? List.of() : children);
        attributes = attributes == null ? Widgets.Attributes.NONE : attributes;
    }

    /// A group with a selected value and a handler, unbound — the Java spelling
    /// of `radio-group value="…" change="…"`.
    ///
    /// The value is the caller's to supply on every rebuild, which is what makes
    /// it controlled.
    public RadioGroup(String value, Consumer<String> onChange, Radio... options) {
        this(value, List.of(options), null, onChange, false, Widgets.Attributes.NONE);
    }

    /// A group that is not wired yet — what a layout preview builds.
    public RadioGroup(String value, Radio... options) {
        this(value, List.of(options), null, null, false, Widgets.Attributes.NONE);
    }

    /// A group that follows a property. The Java spelling of `bind=`.
    ///
    /// @param source read-only by construction ([ADR-0063])
    public static RadioGroup of(Observable<?> source, Consumer<String> onChange, Radio... options) {
        return new RadioGroup(null, List.of(options),
                Objects.requireNonNull(source, "source"), onChange, false,
                Widgets.Attributes.NONE);
    }

    /// This group, disabled or not.
    public RadioGroup disabled(boolean value) {
        return new RadioGroup(this.value, children, source, onChange, value, attributes);
    }

    /// This group with classes added — `radio-group.inline` from Java.
    public RadioGroup styled(String... classes) {
        return new RadioGroup(value, children, source, onChange, disabled,
                new Widgets.Attributes(attributes.id(), Set.of(classes), attributes.key()));
    }

    /// Which option is selected **right now** — the bound value, or [#value()].
    ///
    /// Read at build rather than captured, for the reason [Widgets.Text#resolved()]
    /// is: a change that lands between a build and a frame is shown by that frame
    /// rather than the one after it.
    ///
    /// The bound value is compared by its `toString`, so a property holding an
    /// enum, an `Integer` or a `String` all work against the strings a document
    /// wrote. That is a coercion, and it is the narrow kind: it never has to guess
    /// what an object *means*, only how the author would have spelled it. A null —
    /// a model that has not loaded — selects nothing rather than the first option,
    /// because a group that guessed would report a value the user never picked.
    public String resolved() {
        if (source == null) {
            return value;
        }
        var current = source.get();
        return current == null ? null : String.valueOf(current);
    }

    @Override
    public Observable<?> binding() {
        return source;
    }

    @Override
    public String cssType() {
        return "radio-group";
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

    /// One Tab stop, with **both** arrow pairs roving inside it (§7.2, [ADR-0073]).
    ///
    /// [FocusScope#BOTH] rather than an axis, and this is the one composite in
    /// the catalog for which that is right: a group's direction is its
    /// *stylesheet's* — `flex-direction` on `radio-group`, which `.inline` flips
    /// — so input cannot know which pair a user is looking at, and answering to
    /// only one would be wrong half the time. It is also ARIA's rule for a radio
    /// group. A `menu` or a `tabs` will name an axis, because theirs is theirs
    /// ([ADR-0078](../../../../../../../book/src/adr/0078-a-focus-scope-has-an-axis.md)).
    ///
    /// A scope even when the group is disabled: a disabled group has no focusable
    /// options left, so the traversal contributes nothing and skips it either
    /// way — and saying "not a composite" while disabled would make the answer
    /// depend on state that has nothing to do with the group's shape.
    @Override
    public FocusScope focusScope() {
        return FocusScope.BOTH;
    }

    /// Not focusable itself. The focus lands on an option, so the ring is drawn
    /// around the thing the user is about to pick rather than around the set.
    @Override
    public boolean isFocusable() {
        return false;
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    /// The options, each told whether it is the selected one.
    ///
    /// This is where the invariant is applied, on every build: nothing is stored,
    /// so there is no path by which two options can be on at once. Comparing by
    /// `equals` rather than by identity, because the value came from a property
    /// the application owns and may be a fresh `String` every time it is set.
    @Override
    public List<Widget> children() {
        var selected = resolved();
        var out = new ArrayList<Widget>(children.size());
        for (var child : children) {
            if (child instanceof Radio option) {
                out.add(option.within(
                        option.value().equals(selected),
                        onChange == null ? null : () -> onChange.accept(option.value()),
                        // The group's own `disabled` is deliberately **not**
                        // pushed down. It propagates for input by itself -- the
                        // router walks up the ancestors (ADR-0077) -- and pushing
                        // it would make every option match `:disabled` too, so the
                        // 45% would apply once for the group and again for each
                        // option and land at 20%. An option's own flag is kept,
                        // because a document may disable one option in a group
                        // that is otherwise available.
                        option.disabled()));
            } else {
                // A heading, a caption, a separator. Left exactly as written: a
                // group that silently dropped what it did not recognise would be
                // a document whose text disappeared with no error.
                out.add(child);
            }
        }
        return List.copyOf(out);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        // No direction of its own, unlike `row` and `column`: a radio group is
        // legitimately either, and `controls.css` makes it a column that a class
        // can turn into a row. That is the opposite of the choice `row` makes,
        // and deliberately -- a `row` that a stylesheet could turn into a column
        // would be a name that lies, while "radio-group" names the semantics and
        // says nothing about the axis.
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }
}
