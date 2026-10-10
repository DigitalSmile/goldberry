package dev.goldberry.widgets.controls.toggle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.goldberry.bind.Observable;
import dev.goldberry.css.ComputedStyle;
import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.input.handler.Handles;
import dev.goldberry.input.key.Key;
import dev.goldberry.kdl.KdlNode;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.attr.Bindable;
import dev.goldberry.widget.semantics.Role;
import dev.goldberry.widget.semantics.Semantics;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;
import dev.goldberry.widgets.markup.Markup;
import dev.goldberry.widgets.markup.Wiring;
import dev.goldberry.widgets.text.Text;

/// A switch: a pill with a disc that slides, flipped by a click, `Space` or a
/// drag of its thumb.
///
/// ```kdl
/// toggle bind="prefs.frost" change="prefs.set-frost" "Frosted sidebar"
/// toggle on=#true disabled=#true "Bound by oath"
/// ```
///
/// In Java, `Toggle.of("Frosted sidebar", source, actions::setFrost)` for a
/// bound one, or `new Toggle("Bound by oath", true)`.
///
/// A binary control like [dev.goldberry.widgets.controls.checkbox.Checkbox],
/// with one difference: it answers a **gesture** as well as an activation. A
/// drag is a sequence of events, and a widget is a value rebuilt every frame
/// with nowhere to keep one, so the router carries the drag's origin.
///
/// A press on a toggle takes the pointer until the release,
/// so every move in between arrives here wherever it goes. What is missing is
/// *where it started*, and this widget cannot remember: the `Toggle` that sees
/// the release is a different instance from the one that saw the press. The
/// router spans exactly that interval already, so it reports the offset as
/// [PointerEvent#dragX()] and nothing here holds state.
///
/// The rule is one comparison against half the thumb's travel:
///
/// - moved **≥ 8px**: the user dragged, and the value they asked for is the
///   direction they dragged in — right is on, left is off, however far past the
///   track they went.
/// - moved **< 8px**: the user clicked, so the value flips.
///
/// Eight is half of the thumb's 16px travel rather than a number chosen by
/// feel: it is the point at which a thumb dragged from either end has passed the
/// middle, so the value the user is asking for is the one the thumb is nearer to.
///
/// **There is no cancel gesture, and that is a real difference from every other
/// control here.** A button or a checkbox is cancelled by dragging off it and
/// letting go, which is why they act on `CLICKED` and not on a release. For a
/// switch, dragging *is* the interaction, so a drag that ends far away is still a
/// drag in that direction — the same behaviour as every platform switch, and the
/// reason this is the one control that reads `RELEASED`.
///
/// The value is the application's. Data flows down and events flow up:
/// dragging a bound toggle whose handler does nothing moves neither the
/// property nor the thumb. What travels up is **the value the user asked for**
/// rather than "toggle", because a drag is a request for a *particular* state —
/// dragging right on a switch that is already on asks for on, and asking for
/// "the other one" there would turn it off. That is why the handler is a
/// `Consumer<Boolean>` and not a `Runnable`; from markup the action receives
/// `"true"` or `"false"`. A `Space` press, which has no direction, asks for the
/// opposite of what is showing — the one place this widget reads its own value.
///
/// Read more: [Choices](https://goldberry.dev/docs/components/choices.html#toggle).
///
/// @param label    the text beside the switch; the click target includes it, as
///                 on a checkbox
/// @param on       the value, when nothing is bound. The caller's to supply on
///                 every rebuild, which is what makes it controlled
/// @param source   `bind`, read-only, or null — see [#resolved()]
/// @param onChange what the user asked for, `true` or `false`; may be null for
///                 a toggle that is not wired yet
/// @param disabled whether it refuses the gesture and matches `:disabled`
/// @param attributes `id` and `class`, exactly as on the primitives
@Markup("toggle")
public record Toggle(
        String label,
        boolean on,
        @Nullable Observable<?> source,
        @Nullable Consumer<Boolean> onChange,
        boolean disabled,
        Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Handles, Attributed<Toggle>, Bindable<Toggle>, Semantics {

    /// Half of the thumb's 16px travel: the point at which the thumb has passed
    /// the middle, and therefore the point at which a gesture is a drag rather than a
    /// click. Not a feel-tuned constant — it is derived from the metric, so a
    /// theme that changed the travel would want this to follow.
    private static final float DRAG_THRESHOLD = 8;

    /// Written out so that the parameters taking null for a default can say so.
    public Toggle(
            String label,
            boolean on,
            @Nullable Observable<?> source,
            @Nullable Consumer<Boolean> onChange,
            boolean disabled,
            @Nullable Attributes attributes) {
        Objects.requireNonNull(label, "label");
        attributes = attributes == null ? Attributes.NONE : attributes;
        this.label = label;
        this.on = on;
        this.source = source;
        this.onChange = onChange;
        this.disabled = disabled;
        this.attributes = attributes;
    }

    /// A toggle wired to a handler and given its value directly.
    public Toggle(String label, boolean on, Consumer<Boolean> onChange) {
        this(label, on, null, onChange, false, Attributes.NONE);
    }

    /// An unwired toggle — what a layout preview builds.
    public Toggle(String label, boolean on) {
        this(label, on, null, null, false, Attributes.NONE);
    }

    /// A switch that follows a property — the Java spelling of `bind=`.
    ///
    /// Named rather than overloaded: it would otherwise be a second
    /// three-argument constructor differing only in whether the second parameter
    /// is a `boolean` or an `Observable`.
    public static Toggle of(String label, Observable<?> source, Consumer<Boolean> onChange) {
        return new Toggle(label, false, source, onChange, false, Attributes.NONE);
    }

    /// The value actually showing: the bound property's if there is one, else
    /// [#on()].
    ///
    /// A `Boolean` is taken as itself. Anything else — including a null, which is
    /// a property that has not loaded — reads as [#on()], because guessing that
    /// some other object means "on" would be worse than showing what the markup
    /// said. That is
    /// [dev.goldberry.widgets.controls.checkbox.Checkbox#resolved()]'s rule with
    /// one fewer case, since a
    /// switch has no mixed state to reach.
    public boolean resolved() {
        if (source == null) {
            return on;
        }
        return source.get() instanceof Boolean value ? value : on;
    }

    @Override
    public Toggle bound(Observable<?> source) {
        return new Toggle(label, on, source, onChange, disabled, attributes);
    }

    @Override
    public Toggle withAttributes(Attributes attributes) {
        return new Toggle(label, on, source, onChange, disabled, attributes);
    }

    @Override
    public @Nullable Observable<?> binding() {
        return source;
    }

    @Override
    public String cssType() {
        return "toggle";
    }

    @Override
    public @Nullable String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    @Override
    public boolean isFocusable() {
        return !disabled;
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public boolean isChecked() {
        return resolved();
    }

    /// The track and the label. The thumb is the track's own child, because it
    /// travels inside it and `transform` is resolved against the box it sits in.
    @Override
    public List<Widget> children() {
        var children = new ArrayList<Widget>(2);
        children.add(new ToggleTrack(resolved(), disabled));
        if (!label.isEmpty()) {
            children.add(new Text(label));
        }
        return List.copyOf(children);
    }

    /// A release rather than a click — see the class note for why this is the one
    /// control that reads one.
    ///
    /// `dragX()` is `NaN` for any event delivered with no button held, and
    /// `Math.abs(NaN) >= 8` is `false`, so such an event reads as a click rather
    /// than as a drag. That is the right answer arrived at by the value's own
    /// arithmetic rather than by a guard, which is why the router reports `NaN`
    /// and not zero.
    @Override
    public void onPointer(PointerEvent event) {
        if (event.kind() != PointerEvent.Kind.RELEASED || event.button() != PointerEvent.Button.PRIMARY) {
            return;
        }
        var drag = event.dragX();
        ask(Math.abs(drag) >= DRAG_THRESHOLD ? drag > 0 : !resolved());
        event.consume();
    }

    /// `Space` asks for the opposite — and `Enter` deliberately does not.
    ///
    /// The line [dev.goldberry.widgets.controls.checkbox.Checkbox] draws and for
    /// the same reason: Enter belongs to a
    /// dialog's default action, and a control that
    /// swallowed it would leave a form with no keyboard route to submit once
    /// focus was on one.
    @Override
    public void onKey(KeyEvent event) {
        if (event.kind() != KeyEvent.Kind.PRESSED
                || event.isRepeat()
                || !event.modifiers().none()) {
            return;
        }
        if (event.key() == Key.SPACE) {
            ask(!resolved());
            event.consume();
        }
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }

    /// Asks the application for a value. It does **not** set one.
    ///
    /// Raised even when it matches what is showing — dragging right on a switch
    /// already on is a legitimate thing to do and reports what it means. A
    /// `Property` swallows a value it already holds, so this settles rather than
    /// looping, which is the same no-op re-selection `radio-group` relies on.
    private void ask(boolean value) {
        if (!disabled && onChange != null) {
            onChange.accept(value);
        }
    }

    /// Builds a `toggle` from markup.
    ///
    /// The change is a **valued** action: what the user asked for is `true` or
    /// `false` rather than "the other one", because a drag is a request for a
    /// particular state and dragging right on a switch already on asks for on.
    @SuppressWarnings("unused")
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Toggle(
                Wiring.label(node),
                node.booleanProperty("on"),
                wiring.bound(node),
                wiring.flag(node, "change"),
                Wiring.disabled(node),
                Attributes.of(node));
    }

    @Override
    public Role role() {
        return Role.SWITCH;
    }

    @Override
    public String accessibleName() {
        return label;
    }
}
