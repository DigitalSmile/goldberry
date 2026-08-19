package io.github.digitalsmile.goldberry.widgets.controls.select;

import io.github.digitalsmile.goldberry.bind.Observable;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.widget.Attributed;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Bindable;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.Markup;
import io.github.digitalsmile.goldberry.widgets.Wiring;
import io.github.digitalsmile.goldberry.widgets.controls.option.Option;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

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
/// @param disabled    whether the whole control refuses to open
/// @param attributes  `id` and `class`, exactly as on the primitives
@Markup("select")
public record Select(
        String value, List<Widget> children, Observable<?> source, Consumer<String> onChange,
        String placeholder, boolean disabled, Attributes attributes)
        implements Widget.Stateful, Attributed<Select>, Bindable<Select> {

    public Select {
        children = List.copyOf(children == null ? List.of() : children);
        placeholder = placeholder == null ? "" : placeholder;
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    /// A select with a value and a handler, unbound — the Java spelling of
    /// `select value="…" change="…"`.
    public Select(String value, Consumer<String> onChange, Option... options) {
        this(value, List.of(options), null, onChange, "", false, Attributes.NONE);
    }

    /// A select that is not wired yet — what a layout preview builds.
    public Select(Option... options) {
        this(null, List.of(options), null, null, "", false, Attributes.NONE);
    }

    /// A select that follows a property. The Java spelling of `bind=`.
    ///
    /// @param source read-only by construction ([ADR-0063])
    public static Select of(Observable<?> source, Consumer<String> onChange, Option... options) {
        return new Select(null, List.of(options),
                Objects.requireNonNull(source, "source"), onChange, "", false, Attributes.NONE);
    }

    /// This select with the text its closed form reads when nothing is chosen.
    public Select placeholder(String value) {
        return new Select(this.value, children, source, onChange, value, disabled, attributes);
    }

    /// This select, disabled or not.
    public Select disabled(boolean value) {
        return new Select(this.value, children, source, onChange, placeholder, value, attributes);
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
        var option = selected();
        return option == null ? placeholder : option.label();
    }

    @Override
    public Select bound(Observable<?> source) {
        return new Select(value, children, source, onChange, placeholder, disabled, attributes);
    }

    @Override
    public Select withAttributes(Attributes attributes) {
        return new Select(value, children, source, onChange, placeholder, disabled, attributes);
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
        return new Select(node.stringProperty("value"), children,
                wiring.bound(node), wiring.valued(node, "change"),
                node.stringProperty("placeholder"), Wiring.disabled(node), Attributes.of(node));
    }
}
