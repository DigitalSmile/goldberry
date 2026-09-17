package io.github.digitalsmile.goldberry.widgets.nav.steps;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.semantics.Role;
import io.github.digitalsmile.goldberry.widget.semantics.Semantics;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widgets.markup.Markup;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;

/// One entry of a [Steps] — a label, an optional description, and the two things
/// only the application can say about it.
///
/// ```kdl
/// step "Account" description="Who you are"
/// step error=#true "Payment"
/// step reachable=#true "Review"
/// ```
///
/// ## What a document writes and what the list writes
///
/// A document writes `error` and `reachable`; the list writes the index, the
/// count and the [StepState] on every build, and a document cannot. That is
/// [io.github.digitalsmile.goldberry.widgets.nav.breadcrumbs.Crumb]'s
/// arrangement, and it is what keeps "one step is current" an invariant rather
/// than a hope.
///
/// A step is focusable and pressable only when the list handed it a handler —
/// which it does when it is `clickable` and the step is `reachable`. A step
/// with no handler is a picture: it takes no focus, because a row of Tab stops
/// that do nothing is worse than none.
///
/// ## Mirrored to `:checked`, and the state is a class
///
/// The current step is `:checked`, which is the pseudo-class every "one of the
/// set is the one" in this catalog uses. The four states are also classes —
/// `step.done`, `step.current`, `step.upcoming`, `step.error` — because a
/// stylesheet wants to colour all four and `:checked` names one.
///
/// @param label       the step's name
/// @param description an optional second line, in `caption`
/// @param error       whether the application says this step failed
/// @param reachable   whether the application says a press may go here
/// @param index       supplied by [Steps] on every build; not an attribute
/// @param count       supplied by [Steps]; how many steps the list holds
/// @param state       supplied by [Steps]
/// @param onPress     supplied by [Steps] when this step may be pressed
/// @param attributes  `id` and `class`, exactly as on every other widget
@Markup("step")
public record Step(
        String label,
        @Nullable String description,
        boolean error,
        boolean reachable,
        int index,
        int count,
        StepState state,
        @Nullable Runnable onPress,
        Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Handles, Attributed<Step>, Semantics {

    public Step {
        Objects.requireNonNull(label, "label");
        if (label.isEmpty()) {
            throw new IllegalArgumentException(
                    "a step needs a label: a list of steps is read as a sequence, and a step with no"
                            + " word in it is a numbered circle nobody can name (§13)");
        }
        state = state == null ? StepState.UPCOMING : state;
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    /// A step with a name.
    public Step(String label) {
        this(label, null, false, false, 0, 0, StepState.UPCOMING, null, Attributes.NONE);
    }

    /// A step with a name and a second line.
    public Step(String label, @Nullable String description) {
        this(label, description, false, false, 0, 0, StepState.UPCOMING, null, Attributes.NONE);
    }

    /// This step, failed.
    public Step error(boolean value) {
        return new Step(label, description, value, reachable, index, count, state, onPress, attributes);
    }

    /// This step, one the application allows a press to reach.
    public Step reachable(boolean value) {
        return new Step(label, description, error, value, index, count, state, onPress, attributes);
    }

    /// Used by [Steps] to tell a step where it stands.
    Step at(int position, int total, StepState where, @Nullable Runnable press) {
        return new Step(label, description, error, reachable, position, total, where, press, attributes);
    }

    @Override
    public Step withAttributes(Attributes value) {
        return new Step(label, description, error, reachable, index, count, state, onPress, value);
    }

    @Override
    public String cssType() {
        return "step";
    }

    @Override
    public @Nullable String id() {
        return attributes.id();
    }

    /// The document's classes plus the state's word.
    @Override
    public Set<String> classes() {
        var classes = new java.util.HashSet<>(attributes.classes());
        classes.add(state.word());
        return Set.copyOf(classes);
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    @Override
    public boolean isChecked() {
        return state == StepState.CURRENT;
    }

    @Override
    public boolean isFocusable() {
        return onPress != null;
    }

    @Override
    public List<Widget> children() {
        return List.of(new StepMarker(index, state), new StepBody(label, description));
    }

    @Override
    public void onPointer(PointerEvent event) {
        if (event.kind() == PointerEvent.Kind.CLICKED) {
            if (onPress != null) {
                onPress.run();
                event.consume();
            }
        }
    }

    /// `Space` and `Enter`, which is §3's rule for everything you press.
    @Override
    public void onKey(KeyEvent event) {
        if (event.kind() != KeyEvent.Kind.PRESSED
                || event.isRepeat()
                || !event.modifiers().none()
                || onPress == null) {
            return;
        }
        if (event.key() == Key.SPACE || event.key() == Key.ENTER) {
            onPress.run();
            event.consume();
        }
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }

    /// [Role#ROW] — one item of a list. §6 asks for exactly that, and a step
    /// that can be pressed is still an item first: what the press does is go
    /// *to* it.
    @Override
    public Role role() {
        return Role.ROW;
    }

    /// §6: "states are in each item's accessible name, since colour alone
    /// cannot carry `error`". So the name is `Payment, step 2 of 4, current`.
    @Override
    public String accessibleName() {
        return label + ", step " + (index + 1) + " of " + count + ", " + state.word();
    }

    /// Builds a `step` from markup.
    ///
    /// No index, no count, no state — the list writes those.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Step(
                Wiring.label(node),
                node.stringProperty("description"),
                node.booleanProperty("error"),
                node.booleanProperty("reachable"),
                0,
                0,
                StepState.UPCOMING,
                null,
                Attributes.of(node));
    }
}
