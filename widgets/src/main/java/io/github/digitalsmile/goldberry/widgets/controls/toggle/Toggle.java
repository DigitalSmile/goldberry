package io.github.digitalsmile.goldberry.widgets.controls.toggle;

import io.github.digitalsmile.goldberry.widget.Attributed;
import io.github.digitalsmile.goldberry.widget.Bindable;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widgets.text.Text;

import io.github.digitalsmile.goldberry.bind.Observable;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.Handles;
import io.github.digitalsmile.goldberry.input.Key;
import io.github.digitalsmile.goldberry.input.KeyEvent;
import io.github.digitalsmile.goldberry.input.PointerEvent;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/// A switch — `docs/core-widgets.md` §3's `toggle`. The fifth control.
///
/// A binary control like [io.github.digitalsmile.goldberry.widgets.controls.checkbox.Checkbox], and the reason it comes next is the half
/// that is *not* like one: §3 says "switch; **drag** or click/Space", so this is
/// the first widget in the catalog with a **gesture** rather than an activation.
/// Everything shipped so far responds to a click, a key or a focus change, all of
/// which are single events. A drag is a sequence, and a widget is a value rebuilt
/// every frame with nowhere to keep one.
///
/// ## The drag, and why the router carries its origin
///
/// A press on a toggle takes the pointer until the release
/// ([ADR-0058](../../../../../../../../book/src/adr/0058-a-press-captures-the-pointer.md)),
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
/// Eight is `travel / 2` (§3 gives travel 16) rather than a number chosen by
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
/// ## The value is the application's
///
/// Controlled in the sense
/// [ADR-0063](../../../../../../../../book/src/adr/0063-data-flows-down-events-flow-up.md)
/// settled: dragging a bound toggle whose handler does nothing moves neither the
/// property nor the thumb. What travels up is **the value the user asked for**
/// rather than "toggle", because a drag is a request for a *particular* state —
/// dragging right on a switch that is already on asks for on, and asking for
/// "the other one" there would turn it off.
///
/// That is why the handler is a `Consumer<Boolean>` and not a `Runnable`, and it
/// is the second valued action in the toolkit after `radio-group`'s
/// ([ADR-0073](../../../../../../../../book/src/adr/0073-a-composite-is-one-tab-stop.md)).
/// A `Space` press, which has no direction, asks for the opposite of what is
/// showing — the one place this widget reads its own value.
///
/// @param label    the text beside the switch; the click target includes it, as
///                 on a checkbox
/// @param on       the value, when nothing is bound. The caller's to supply on
///                 every rebuild, which is what makes it controlled
/// @param source   §9's `bind`, read-only — see [#resolved()]
/// @param onChange what the user asked for, `true` or `false`
public record Toggle(
        String label, boolean on, Observable<?> source, Consumer<Boolean> onChange,
        boolean disabled, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Handles, Attributed<Toggle>, Bindable<Toggle> {

    /// Half of §3's "travel 16": the point at which the thumb has passed the
    /// middle, and therefore the point at which a gesture is a drag rather than a
    /// click. Not a feel-tuned constant — it is derived from the metric, so a
    /// theme that changed the travel would want this to follow.
    private static final float DRAG_THRESHOLD = 8;

    public Toggle {
        Objects.requireNonNull(label, "label");
        attributes = attributes == null ? Attributes.NONE : attributes;
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
    /// is a `boolean` or an `Observable` (ADR-0094).
    public static Toggle of(String label, Observable<?> source, Consumer<Boolean> onChange) {
        return new Toggle(label, false, source, onChange, false, Attributes.NONE);
    }

    /// The value actually showing: the bound property's if there is one, else
    /// [#on()].
    ///
    /// A `Boolean` is taken as itself. Anything else — including a null, which is
    /// a property that has not loaded — reads as [#on()], because guessing that
    /// some other object means "on" would be worse than showing what the markup
    /// said. That is [io.github.digitalsmile.goldberry.widgets.controls.checkbox.Checkbox#resolved()]'s rule with one fewer case, since a
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
    public Observable<?> binding() {
        return source;
    }

    @Override
    public String cssType() {
        return "toggle";
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
        if (event.kind() != PointerEvent.Kind.RELEASED
                || event.button() != PointerEvent.Button.PRIMARY) {
            return;
        }
        var drag = event.dragX();
        ask(Math.abs(drag) >= DRAG_THRESHOLD ? drag > 0 : !resolved());
        event.consume();
    }

    /// `Space` asks for the opposite — and `Enter` deliberately does not.
    ///
    /// The line [io.github.digitalsmile.goldberry.widgets.controls.checkbox.Checkbox] draws and for the same reason: Enter belongs to a
    /// dialog's default action (`docs/design-system.md` §2.3), and a control that
    /// swallowed it would leave a form with no keyboard route to submit once
    /// focus was on one.
    @Override
    public void onKey(KeyEvent event) {
        if (event.kind() != KeyEvent.Kind.PRESSED || event.isRepeat() || !event.modifiers().none()) {
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
}
