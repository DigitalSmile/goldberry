package io.github.digitalsmile.goldberry.widgets.nav.steps;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.bind.Observable;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.attr.Bindable;
import io.github.digitalsmile.goldberry.widgets.markup.Markup;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;

/// A progress indicator over an ordered list — `docs/core-widgets.md` §6's
/// `steps`, and the second widget of the `nav` package.
///
/// ```kdl
/// steps current=1 clickable=#true change="app.go-step" {
///     step reachable=#true "Account" description="Who you are"
///     step "Payment"
///     step "Review"
/// }
/// steps direction="vertical" bind="wizard.step" {
///     step "Download"
///     step error=#true "Verify"
///     step "Install"
/// }
/// ```
///
/// ## The list decides where each step stands
///
/// A step's state is a function of the list's **current index**: before it is
/// done, at it is current, after it is upcoming. The list writes that onto every
/// step on every build, the way a `tabs` tells a `tab` it is selected and a
/// `breadcrumbs` tells its last crumb it is where you are — so a document cannot
/// describe a list with two current steps, or none. The one word a step keeps
/// for itself is `error`, because only the application knows that step three
/// failed ([StepState]).
///
/// ## Read-only, unless asked — and reachability is the application's
///
/// §6: "Standalone it is a read-only picture of where a process is;
/// `clickable=#true` raises a `change` for a step the application says is
/// reachable, and refuses the rest — the widget never decides reachability
/// itself, because only the application knows whether step 3 is valid yet." So
/// a step is pressable only when **both** the list says `clickable` and the step
/// says `reachable`, and a press reports the step's index through `change`. The
/// list moves nothing itself: `current` is read through `bind` or written by
/// the application, which is [ADR-0063]'s rule for every value in this toolkit.
///
/// ## This node styles nothing
///
/// `steps` as a **CSS type** is [StepList], the node this one builds — the
/// arrangement every composite in the catalog has, and its reason: a composition
/// node that was also styled would put two `steps` nodes in the cascade, one
/// inside the other (ADR-0109).
///
/// @param children  the steps, as written. Anything that is not a [Step] is
///                  drawn in the row and left alone
/// @param current   the index of the current step when nothing is bound; out of
///                  range means every step is upcoming, which is what a process
///                  that has not started looks like
/// @param source    §9's `bind` — read-only; a `Number` is read as the index
/// @param direction a row or a column
/// @param clickable whether reachable steps take a press at all
/// @param onChange  told the index of the step the user pressed, or null
/// @param attributes `id` and `class`, exactly as on every other widget
@Markup("steps")
public record Steps(
        List<Widget> children,
        int current,
        @Nullable Observable<?> source,
        Direction direction,
        boolean clickable,
        @Nullable IntConsumer onChange,
        Attributes attributes)
        implements Widget.Stateless, Attributed<Steps>, Bindable<Steps> {

    /// Which way the list runs. §6's `direction="horizontal|vertical"`.
    public enum Direction {
        HORIZONTAL,
        VERTICAL;

        /// The class a vertical list carries, so a stylesheet can turn it.
        static final String VERTICAL_CLASS = "vertical";

        static Direction named(@Nullable String name) {
            return name != null && name.trim().toLowerCase(Locale.ROOT).equals("vertical") ? VERTICAL : HORIZONTAL;
        }
    }

    public Steps {
        children = List.copyOf(children == null ? List.of() : children);
        direction = direction == null ? Direction.HORIZONTAL : direction;
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    /// A horizontal, read-only list with `current` the current step.
    public Steps(int current, Widget... children) {
        this(List.of(children), current, null, Direction.HORIZONTAL, false, null, Attributes.NONE);
    }

    /// This list running the other way.
    public Steps direction(Direction value) {
        return new Steps(children, current, source, value, clickable, onChange, attributes);
    }

    /// This list taking a press on any step the application marked reachable,
    /// and reporting its index.
    public Steps clickable(@Nullable IntConsumer handler) {
        return new Steps(children, current, source, direction, true, handler, attributes);
    }

    @Override
    public Steps bound(Observable<?> value) {
        return new Steps(children, current, value, direction, clickable, onChange, attributes);
    }

    @Override
    public @Nullable Observable<?> binding() {
        return source;
    }

    @Override
    public Steps withAttributes(Attributes value) {
        return new Steps(children, current, source, direction, clickable, onChange, value);
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    /// The steps as written, before the list wrote its states onto them.
    public List<Widget> rawSteps() {
        return children;
    }

    /// Which step is current: the bound value if there is one, the written one
    /// otherwise. A bound value that is not a number reads as "none".
    public int resolvedCurrent() {
        if (source == null) {
            return current;
        }
        return source.get() instanceof Number number ? number.intValue() : -1;
    }

    /// How many [Step]s are in the list — the `N` of "step 2 of N".
    public int count() {
        var count = 0;
        for (var child : children) {
            if (child instanceof Step) {
                count++;
            }
        }
        return count;
    }

    @Override
    public Widget build(BuildContext context) {
        var now = resolvedCurrent();
        var total = count();
        var row = new ArrayList<Widget>(children.size() * 2);
        var index = 0;
        for (var child : children) {
            if (!(child instanceof Step step)) {
                row.add(child);
                continue;
            }
            if (index > 0) {
                // The connector before a step is filled when the step *before*
                // it is done: the line is drawn from where you have been.
                row.add(new StepConnector(index - 1 < now && !isError(index - 1)));
            }
            var at = index;
            var handler = onChange;
            Runnable press = clickable && step.reachable() && handler != null ? () -> handler.accept(at) : null;
            row.add(step.at(at, total, stateOf(step, at, now), press));
            index++;
        }
        return new StepList(row, direction, attributes);
    }

    /// Where a step stands, from the index — unless the step says it failed.
    static StepState stateOf(Step step, int index, int current) {
        if (step.error()) {
            return StepState.ERROR;
        }
        if (index < current) {
            return StepState.DONE;
        }
        return index == current ? StepState.CURRENT : StepState.UPCOMING;
    }

    private boolean isError(int index) {
        var seen = 0;
        for (var child : children) {
            if (child instanceof Step step) {
                if (seen == index) {
                    return step.error();
                }
                seen++;
            }
        }
        return false;
    }

    /// Builds a `steps` from markup.
    ///
    /// `change` is wired as a number, because what the user pressed is an index
    /// and a handler that got `"2"` would parse it.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        Objects.requireNonNull(node, "node");
        DoubleConsumer change = wiring.numeric(node, "change");
        IntConsumer onChange = change == null ? null : index -> change.accept(index);
        return new Steps(
                children,
                (int) node.numberProperty("current", 0),
                wiring.bound(node),
                Direction.named(node.stringProperty("direction")),
                node.booleanProperty("clickable"),
                onChange,
                Attributes.of(node));
    }
}
