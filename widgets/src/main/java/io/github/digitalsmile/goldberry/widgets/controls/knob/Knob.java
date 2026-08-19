package io.github.digitalsmile.goldberry.widgets.controls.knob;

import io.github.digitalsmile.goldberry.widget.Attributed;
import io.github.digitalsmile.goldberry.widget.Bindable;
import io.github.digitalsmile.goldberry.widget.Attributes;

import io.github.digitalsmile.goldberry.bind.Observable;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.Handles;
import io.github.digitalsmile.goldberry.input.KeyEvent;
import io.github.digitalsmile.goldberry.input.PointerEvent;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;
import java.util.function.DoubleConsumer;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.widgets.Wiring;
import io.github.digitalsmile.goldberry.widgets.Markup;

/// A rotary control — `docs/core-widgets.md` §3's `knob`, and the tenth in the
/// catalog.
///
/// ```kdl
/// knob min=0 max=11 value=5 step=1
/// knob class="large" detents=5 bind="mix.send"
/// ```
///
/// ## Its drag is a rate, and that is the whole of what is new
///
/// [io.github.digitalsmile.goldberry.widgets.controls.slider.Slider] and this control answer the same question — what value is the user
/// asking for — from opposite ends. A slider's value is a **position**: the
/// pointer is somewhere along a track and the fraction it sits at *is* the
/// answer, read fresh on every event with no history at all ([ADR-0079]). A knob
/// has no track. §3 gives it a **rate** instead: "value drag 200px per full
/// range", so the answer is `where it started + how far you have dragged`.
///
/// "Where it started" is the problem, and it is not one any existing machinery
/// solved. A widget is an immutable value rebuilt from the model, so by the
/// second frame of the drag the value at the press is gone — overwritten by the
/// value the drag itself asked for. The router already remembers the two facts
/// with exactly this lifetime, `pressX` and `pressY` ([ADR-0075]), so it now
/// remembers a third: [Handles#gestureAnchor()] is asked once on the press and
/// handed back on every event of the gesture as [PointerEvent#anchor()]
/// ([ADR-0089]).
///
/// The fine modifier is a gesture fact for the same reason. Reading the *live*
/// modifier would rescale travel already covered — press Shift 100px into a drag
/// and the value jumps from half a range below where it started to a twentieth
/// of one — so what counts is [PointerEvent#gestureModifiers()], the modifiers
/// held when the button went down.
///
/// ## Detents are not `step`
///
/// `step` is a grid: every value the control reports lands on it, from the
/// keyboard and from the pointer alike. **Detents are magnetic**: the knob is
/// continuous, and a drag that comes near one is pulled onto it. That is what a
/// detent is on a physical control, and it is the reason both exist — a knob
/// with a centre detent is not a knob with a coarse step.
///
/// §3 pins the count's meaning nowhere, so [#PULL] is derived rather than taken:
/// a detent owns the middle half of the gap to its neighbour, which leaves the
/// outer half reachable. A pull of a whole half would make the detents a grid
/// and delete the distinction this paragraph is about.
///
/// @param min        the low end of the range
/// @param max        the high end
/// @param value      what to show when nothing is bound
/// @param step       the grid the keyboard moves on, or 0 for continuous
/// @param detents    how many magnetic points across the travel, or 0 for none;
///                   at least 2, because one detent is not a set of them
/// @param source     §9's `bind=`, or null
/// @param onChange   what the user is asking for — never what the knob decided
/// @param disabled   whether it refuses input and matches `:disabled`
/// @param attributes `id` and `class`; `class="large"` is §3's 48px diameter
@Markup("knob")
public record Knob(
        double min, double max, double value, double step, int detents,
        Observable<?> source, DoubleConsumer onChange,
        boolean disabled, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Handles, Attributed<Knob>, Bindable<Knob> {

    /// §3: "value drag **200px** per full range". Logical pixels, so a knob
    /// behaves the same on a hidpi screen as on a 1× one.
    private static final double TRAVEL = 200;

    /// §3: "**×0.1** with fine modifier".
    private static final double FINE = 0.1;

    /// §3: "arc **270°**", and the travel starts at seven-thirty.
    ///
    /// Radians clockwise from three o'clock, which is [Box.Mark]'s convention.
    /// `3π/4` is the lower left; sweeping 270° clockwise from there ends at the
    /// lower right, leaving the 90° gap **centred at the bottom** — which is
    /// where every rotary control in the world puts it, because that is where the
    /// shaft comes out.
    static final double ARC_START = 0.75 * Math.PI;

    /// The 270° of [#ARC_START].
    static final double ARC_SWEEP = 1.5 * Math.PI;

    /// How close a drag must come to a detent to be caught by it, as a fraction
    /// of the gap between two — see the class comment.
    private static final double PULL = 0.25;

    /// How far the pointer may travel and still have been a *click* — [io.github.digitalsmile.goldberry.widgets.controls.toggle.Toggle]'s
    /// number, and the same job: one gesture has to mean two things and the
    /// distance is what tells them apart.
    private static final float CLICK_SLOP = 8;

    public Knob {
        if (!Double.isFinite(min) || !Double.isFinite(max) || min >= max) {
            throw new IllegalArgumentException(
                    "a knob needs a range with a low end below a high one, not " + min + ".." + max);
        }
        if (!Double.isFinite(step) || step < 0) {
            throw new IllegalArgumentException("a step is zero or positive, not " + step);
        }
        if (detents == 1 || detents < 0) {
            throw new IllegalArgumentException(
                    "detents are a set of positions, so there are none or at least two — not "
                            + detents);
        }
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    /// The form most knobs want: a range, a value and a handler.
    public Knob(double min, double max, double value, double step, DoubleConsumer onChange) {
        this(min, max, value, step, 0, null, onChange, false, Attributes.NONE);
    }

    /// A `0..1` knob, which is what most bindings want.
    public Knob(double value, DoubleConsumer onChange) {
        this(0, 1, value, 0, 0, null, onChange, false, Attributes.NONE);
    }

    /// A knob that follows a property — the Java spelling of `bind=`.
    ///
    /// Named for [io.github.digitalsmile.goldberry.widgets.controls.slider.Slider#of]'s
    /// reason: it would otherwise be a second five-argument constructor differing
    /// only in one parameter's type (ADR-0094).
    public static Knob of(double min, double max, double step, Observable<?> source, DoubleConsumer onChange) {
        return new Knob(min, max, min, step, 0, source, onChange, false, Attributes.NONE);
    }


    /// The value actually showing: the bound property's if there is one, else
    /// [#value()] — clamped either way, for [io.github.digitalsmile.goldberry.widgets.controls.slider.Slider#resolved()]'s reason.
    public double resolved() {
        var raw = source == null ? value
                : source.get() instanceof Number number ? number.doubleValue() : value;
        return clamp(raw);
    }

    /// Where round the travel the value sits, `0..1`.
    ///
    /// Linear, and deliberately without `Scale`: §3 gives the dB mapping to
    /// `fader` and not to this. A knob that wanted one would be asking for the
    /// same `Scale` this range already has room for, which is a thing to add when
    /// something needs it rather than because a sibling has it.
    public double fraction() {
        return (resolved() - min) / (max - min);
    }

    /// The angle a fraction of the travel points at, in radians clockwise from
    /// three o'clock — [Box.Mark]'s convention, and [KnobDial]'s pointer.
    static double angleAt(double fraction) {
        return ARC_START + ARC_SWEEP * (fraction < 0 ? 0 : fraction > 1 ? 1 : fraction);
    }

    /// The inverse: where round the travel an angle falls, `0..1`.
    ///
    /// The 90° the travel does **not** cover is the interesting part. A click in
    /// the gap at the bottom has no fraction, and the two honest answers are "do
    /// nothing" and "the nearer end" — this returns the nearer end, because the
    /// gap is where a user clicks to mean *all the way down* or *all the way up*
    /// and refusing them both would make the bottom of the control dead.
    static double fractionAt(double angle) {
        var turn = 2 * Math.PI;
        var delta = (angle - ARC_START) % turn;
        if (delta < 0) {
            delta += turn;
        }
        if (delta <= ARC_SWEEP) {
            return delta / ARC_SWEEP;
        }
        // Past the end of the travel: in the gap. Nearer to the top of it or to
        // the bottom of it, splitting the 90° down the middle.
        return delta - ARC_SWEEP <= (turn - ARC_SWEEP) / 2 ? 1 : 0;
    }

    /// What `PageUp` and `PageDown` move by — [io.github.digitalsmile.goldberry.widgets.controls.slider.Slider#largeStep()]'s rule, so the
    /// two controls do not disagree about what a page is.
    public double largeStep() {
        return step > 0 ? step * 10 : (max - min) / 10;
    }

    /// This knob with §3's optional detents — magnetic positions across the
    /// travel, which is not the same thing as a `step` (see the class comment).
    public Knob detents(int detents) {
        return new Knob(min, max, value, step, detents, source, onChange, disabled, attributes);
    }

    /// This knob, disabled or not.
    public Knob disabled(boolean value) {
        return new Knob(min, max, this.value, step, detents, source, onChange, value, attributes);
    }

    @Override
    public Knob bound(Observable<?> source) {
        return new Knob(min, max, value, step, detents, source, onChange, disabled, attributes);
    }

    @Override
    public Knob withAttributes(Attributes attributes) {
        return new Knob(min, max, value, step, detents, source, onChange, disabled, attributes);
    }

    @Override
    public Observable<?> binding() {
        return source;
    }

    @Override
    public String cssType() {
        return "knob";
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

    /// The full arc, with the value's part of it nested inside.
    ///
    /// **Nested rather than stacked**, because the two rings are concentric and
    /// §8's subset has no `position: absolute` — a `stack` is M3's. A child at
    /// `width: 100%; height: 100%` with no padding between them occupies exactly
    /// its parent's box, so nesting *is* stacking for as long as nothing needs to
    /// overlap in more than one direction.
    @Override
    public List<Widget> children() {
        return List.of(new KnobTrack(fraction(), disabled));
    }

    /// The **dial**, so a press can tell the ring from the body.
    ///
    /// Clicking the ring positions the value at the angle clicked; clicking the
    /// dial does not, because the dial is the thing you grab to drag. Which means
    /// the control has to know where the dial ends — and it cannot, because a
    /// widget has no idea how it was laid out and the inset is the stylesheet's
    /// (`knob-arc { padding }`), so an application that changes it would move a
    /// boundary this file had hard-coded.
    ///
    /// [Handles#localPart()] is exactly that question already answered
    /// ([ADR-0080]): the router measures [PointerEvent#local()] against the named
    /// part, so `local.x()` beyond `local.width()` *is* "outside the dial",
    /// derived from the geometry that was actually painted. The drag is
    /// unaffected — it reads `dragY()`, which is the window's.
    @Override
    public String localPart() {
        return "knob-dial";
    }

    /// The value at the moment a drag begins — see [Handles#gestureAnchor()].
    ///
    /// [#resolved()] and not [#value()]: what the drag continues from is what the
    /// user can see, which on a bound knob is the property's.
    @Override
    public double gestureAnchor() {
        return resolved();
    }

    /// §3's vertical drag, and §3.1's "1:1, no animation".
    ///
    /// Vertical is the primary gesture and the only one here. §3 offers
    /// "circular-drag optional" and it stays unbuilt: a circular drag has to
    /// decide what happens when the pointer crosses the 90° gap at the bottom,
    /// and every answer is either a jump or a wrap that depends on which way
    /// round the user went — which needs the accumulated angle, a second piece of
    /// gesture state, for a gesture that is nobody's first choice.
    @Override
    public void onPointer(PointerEvent event) {
        if (event.kind() == PointerEvent.Kind.WHEEL) {
            wheel(event);
            return;
        }
        if (event.kind() == PointerEvent.Kind.CLICKED) {
            click(event);
            return;
        }
        // Only while a button is down. Outside a gesture the anchor is NaN, which
        // is the router saying so through the arithmetic rather than through a
        // flag (ADR-0075, ADR-0089).
        var dragging = switch (event.kind()) {
            case PRESSED -> event.button() == PointerEvent.Button.PRIMARY;
            case MOVED -> !Double.isNaN(event.anchor());
            default -> false;
        };
        if (!dragging || Double.isNaN(event.anchor())) {
            return;
        }
        // Up is more. `dragY` is positive downwards, because a screen's y is, and
        // a knob turned up by dragging down would be the one control in the
        // toolkit that disagrees with every other.
        var sensitivity = event.gestureModifiers().shift() ? FINE : 1;
        ask(detented(event.anchor() - (event.dragY() / TRAVEL) * (max - min) * sensitivity));
        // Consumed so an ancestor -- a scroll view, a list row -- does not also
        // act on a drag that is plainly this control's.
        event.consume();
    }

    /// A click on the **ring** turns the knob to the angle clicked.
    ///
    /// The ring is a track, so clicking it means what clicking a slider's track
    /// means. The dial is not: it is the thing you grab, and a press that jumped
    /// before the drag started would move the value out from under the gesture
    /// that was about to set it.
    ///
    /// On `CLICKED` rather than on `PRESSED`, and that is the whole of what makes
    /// it compose with the drag. A press is the first event of *both* gestures and
    /// cannot know which one it is; the router synthesizes `CLICKED` only when the
    /// press and the release landed on the same node ([ADR-0058]), and the
    /// remaining ambiguity — a drag that ended where it began — is settled by
    /// [#CLICK_SLOP], which is [io.github.digitalsmile.goldberry.widgets.controls.toggle.Toggle]'s answer to the same question. Jumping on
    /// the press would also have to fight the anchor: the router reads
    /// [#gestureAnchor()] *before* dispatching, so a drag after a jump would
    /// continue from the value the jump replaced.
    private void click(PointerEvent event) {
        if (Math.hypot(event.dragX(), event.dragY()) >= CLICK_SLOP) {
            return;
        }
        var local = event.local();
        if (local.width() <= 0 || local.height() <= 0) {
            return;
        }
        // Measured against `knob-dial`, so the centre of the dial is the centre of
        // the control and anything past its edge is the ring.
        var dx = local.x() - local.width() / 2;
        var dy = local.y() - local.height() / 2;
        var radius = Math.min(local.width(), local.height()) / 2;
        if (Math.hypot(dx, dy) <= radius) {
            // On the dial. A grab, not a jump.
            return;
        }
        ask(detented(min + fractionAt(Math.atan2(dy, dx)) * (max - min)));
        event.consume();
    }

    /// A wheel line moves what an arrow key moves.
    ///
    /// §3's "×0.1 with fine modifier" is attached to the **value drag** and not to
    /// this, so a modified wheel is deliberately the same as an unmodified one:
    /// `core-widgets.md` §3 lists "wheel steps, keyboard arrows, modifier for fine
    /// adjustment" in one breath and `design-system.md` §3 is the precise one.
    ///
    /// Detents are not applied. A wheel line is a discrete request for the next
    /// value, exactly as an arrow key is, and magnetism belongs to a continuous
    /// gesture — a wheel that snapped would make some clicks do nothing.
    ///
    /// A touchpad reports **fractions** of a line, and the two kinds of knob have
    /// to treat them differently. A continuous knob passes the fraction straight
    /// through, so a gentle two-finger drag moves smoothly. A stepped one cannot:
    /// its values are a grid, so a third of a step rounds back to where it
    /// started — and because every event computes from the current value rather
    /// than accumulating, it would round back *every time* and the knob would sit
    /// there while the user scrolled. So a stepped knob moves **at least one
    /// step** for any scroll at all, and more for a fast one.
    private void wheel(PointerEvent event) {
        var lines = event.deltaY();
        if (lines == 0) {
            return;
        }
        // Positive deltaY is *down the document*, which is away from the user, so
        // it lowers the value -- the same sign the drag uses.
        var direction = lines > 0 ? -1 : 1;
        if (step > 0) {
            ask(resolved() + direction * Math.max(1, Math.round(Math.abs(lines))) * step);
        } else {
            ask(resolved() - lines * (max - min) / 100);
        }
        event.consume();
    }

    /// [io.github.digitalsmile.goldberry.widgets.controls.slider.Slider]'s keyboard map exactly, and deliberately so: §3 gives both
    /// controls "keyboard arrows (step), PgUp/PgDn, Home/End", and a knob that
    /// answered them differently would be a second thing to learn.
    @Override
    public void onKey(KeyEvent event) {
        if (event.kind() != KeyEvent.Kind.PRESSED || !event.modifiers().none()) {
            return;
        }
        var current = resolved();
        var moved = switch (event.key()) {
            case LEFT, DOWN -> stepFrom(current, -1, 0.01);
            case RIGHT, UP -> stepFrom(current, 1, 0.01);
            case PAGE_DOWN -> stepFrom(current, -1, 0.1);
            case PAGE_UP -> stepFrom(current, 1, 0.1);
            case HOME -> min;
            case END -> max;
            default -> Double.NaN;
        };
        if (Double.isNaN(moved)) {
            return;
        }
        ask(moved);
        // Always consumed, even when the value did not move: a knob at its
        // maximum still owns Right, and letting it through would move focus off
        // the control being adjusted (ADR-0073, ADR-0078, ADR-0079).
        event.consume();
    }

    /// [io.github.digitalsmile.goldberry.widgets.controls.slider.Slider#stepFrom]'s rule — the next value the user can *reach*, which is
    /// not always one step away when the model is off the grid.
    private double stepFrom(double current, int direction, double share) {
        if (step <= 0) {
            return current + direction * share * (max - min);
        }
        if (share >= 0.1) {
            return current + direction * largeStep();
        }
        var index = (current - min) / step;
        var next = direction > 0
                ? Math.floor(index + 1e-9) + 1
                : Math.ceil(index - 1e-9) - 1;
        return min + next * step;
    }

    /// Pulls `raw` onto the nearest detent if it is close enough to one.
    ///
    /// Returns `raw` untouched when there are no detents, which is the ordinary
    /// case, and when it is outside a detent's pull — that "and" is what makes a
    /// detent magnetic rather than a grid.
    private double detented(double raw) {
        if (detents < 2) {
            return raw;
        }
        var spacing = (max - min) / (detents - 1);
        var nearest = min + Math.round((raw - min) / spacing) * spacing;
        return Math.abs(raw - nearest) <= spacing * PULL ? nearest : raw;
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }

    /// Asks the application for a value, snapped and clamped. It does **not** set
    /// one ([ADR-0063]).
    private void ask(double raw) {
        if (!disabled && onChange != null) {
            onChange.accept(snap(clamp(raw)));
        }
    }

    private double clamp(double raw) {
        return raw < min ? min : raw > max ? max : raw;
    }

    /// [io.github.digitalsmile.goldberry.widgets.controls.slider.Slider#snap]'s rule: the grid counts from `min`, and the ends are always
    /// reachable even when the range is not a whole number of steps.
    private double snap(double raw) {
        if (step <= 0 || raw == min || raw == max) {
            return raw;
        }
        return clamp(min + Math.round((raw - min) / step) * step);
    }

    /// Builds a `knob` from markup.
    ///
    /// `detents` is a count, like `slider`'s `ticks` — a value a document can
    /// carry, naming nothing the application has to have registered (ADR-0080).
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        var min = node.numberProperty("min", 0);
        var max = node.numberProperty("max", 1);
        return new Knob(min, max,
                node.numberProperty("value", min),
                node.numberProperty("step", 0),
                (int) node.numberProperty("detents", 0),
                wiring.bound(node), wiring.numeric(node, "change"),
                Wiring.disabled(node), Attributes.of(node));
    }
}
