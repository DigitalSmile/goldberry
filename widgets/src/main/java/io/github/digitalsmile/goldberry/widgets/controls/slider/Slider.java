package io.github.digitalsmile.goldberry.widgets.controls.slider;

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
import io.github.digitalsmile.goldberry.widgets.controls.Scale;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.DoubleConsumer;

/// A continuous value on a track — `docs/core-widgets.md` §3's `slider`. The
/// sixth control.
///
/// The first control whose value is **a number rather than a state**, and that is
/// what makes it different from everything before it. A checkbox has two
/// positions and a switch has two; a stylesheet can name both, and
/// `toggle-track:checked toggle-thumb { transform: translate(16px) }` is how the
/// switch's thumb moves. No stylesheet can name a number that came out of a
/// model, so the thumb has to be placed by the widget — and it is placed by
/// **flex ratio** rather than by a transform, because a transform cannot express
/// it ([ADR-0079](../../../../../../../../book/src/adr/0079-a-continuous-value-is-placed-by-ratio.md)).
///
/// ## Direct manipulation
///
/// §3.1: "drag: **1:1, no animation**". A press anywhere on the control jumps the
/// value to where it landed and starts a drag; every move until the release
/// follows the pointer exactly. Nothing eases, because a thumb that eased toward
/// the finger would lag it, and lag is the one thing a control being dragged must
/// not have.
///
/// The pointer's position along the track comes from
/// [PointerEvent.Local#fractionX()] — the router's answer to "where inside *you*
/// did this happen", which a widget cannot work out for itself
/// ([ADR-0079]). The press already takes the pointer until the release
/// ([ADR-0058]), so a drag that wanders off the track keeps working.
///
/// ## The keyboard
///
/// Arrows step, `PageUp`/`PageDown` take [#largeStep()], `Home` and `End` go to
/// the ends. Both arrow **pairs** step, for the reason `radio-group` roves on
/// both: a slider's direction is its stylesheet's, and a vertical one is the same
/// widget with a class.
///
/// The arrow keys are **consumed**, which is load-bearing rather than tidy: a
/// slider inside a `radio-group`-style focus scope would otherwise have its
/// arrows taken as traversal. ADR-0073 put scope traversal *after* the focused
/// chain declines the key precisely so this works, and this is the first control
/// that actually relies on it.
///
/// ## The value is the application's
///
/// Controlled in the sense [ADR-0063] settled: dragging a bound slider whose
/// handler does nothing moves neither the property nor the thumb. What travels up
/// is the value asked for, already **snapped to [#step()] and clamped** to the
/// range — a widget that reported a raw fraction would make every application
/// repeat the same arithmetic, and get it slightly differently wrong each time.
///
/// ## The two optional halves of §3, and the scale
///
/// §3 asks for "optional **tick marks** and **value label**", and for a fader's
/// "optional **dB scale** mapping". All three land here
/// ([ADR-0080](../../../../../../../../book/src/adr/0080-a-value-is-measured-along-a-part.md)):
///
/// ```kdl
/// slider min=0 max=100 value=40 step=5 ticks=5 format="%.0f%%"
/// slider class="vertical" scale="db" max=1 format="%.2f" bind="audio.gain"
/// ```
///
/// - [#ticks()] is a **count**, and the marks are evenly spaced along the
///   *travel* rather than along the value — which is the same thing on a linear
///   slider and the only useful thing on a scaled one.
/// - [#format()] is a format **string** rather than a function, because §11's
///   parity invariant compares two records for equality and two lambdas are never
///   equal. It is validated when the slider is built, so a `%d` against a double
///   fails at inflation rather than on the frame that first draws it.
/// - [#scale()] is the curve between the value and the position — see `Scale`.
///
/// The label sits **at the end of the control, beside the track**, so the value
/// is no longer a position along the slider: it is a position along the
/// [SliderTrack], which is what [#localPart()] tells the router.
///
/// @param min      the value at the start of the track
/// @param max      the value at the end; must be greater than `min`
/// @param value    where the thumb is, when nothing is bound
/// @param step     what the value snaps to, and what an arrow key moves by. `0`
///                 means continuous
/// @param ticks    how many marks to draw along the travel, both ends included.
///                 `0` is none, and one mark is refused as meaningless
/// @param format   a [java.util.Formatter] pattern for the value label, or null
///                 for no label
/// @param scale    the curve between the value and the position; `Scale#LINEAR`
///                 unless a fader says otherwise
/// @param source   §9's `bind`, read-only — see [#resolved()]
/// @param onChange what the user asked for, already snapped and clamped
public record Slider(
        double min, double max, double value, double step,
        int ticks, String format, Scale scale,
        Observable<?> source, DoubleConsumer onChange,
        boolean disabled, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Handles, Attributed<Slider>, Bindable<Slider> {

    public Slider {
        if (!Double.isFinite(min) || !Double.isFinite(max) || max <= min) {
            throw new IllegalArgumentException(
                    "a slider needs max > min, not min=" + min + " max=" + max);
        }
        if (!Double.isFinite(step) || step < 0) {
            throw new IllegalArgumentException("step must be zero or positive, not " + step);
        }
        if (ticks == 1 || ticks < 0) {
            throw new IllegalArgumentException(
                    "tick marks are the ends and what is between them, so there are none or at"
                            + " least two — not " + ticks);
        }
        scale = scale == null ? Scale.LINEAR : scale;
        scale.validate(min, max);
        // Formatted once here so a bad pattern is an inflation error naming the
        // document, rather than an IllegalFormatConversionException thrown out
        // of a paint on whichever frame first has a value to draw. It costs one
        // more `format` per build of a labelled slider, which is nothing beside
        // the one the label already does.
        if (format != null) {
            label(format, min);
        }
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    /// The eight-argument form every unlabelled, unticked, linear slider wants —
    /// which is most of them.
    public Slider(double min, double max, double value, double step,
            Observable<?> source, DoubleConsumer onChange,
            boolean disabled, Attributes attributes) {
        this(min, max, value, step, 0, null, Scale.LINEAR, source, onChange, disabled, attributes);
    }

    /// A `0..1` slider, which is what most bindings want.
    public Slider(double value, DoubleConsumer onChange) {
        this(0, 1, value, 0, null, onChange, false, Attributes.NONE);
    }

    /// A slider over a range, wired to a handler.
    public Slider(double min, double max, double value, double step, DoubleConsumer onChange) {
        this(min, max, value, step, null, onChange, false, Attributes.NONE);
    }

    /// A slider that follows a property — the Java spelling of `bind=`.
    ///
    /// A **named factory** rather than a fifth constructor, because it would be
    /// the second five-argument one and the two differ only in whether the fourth
    /// parameter is a `double` or an `Observable`. A reader cannot tell those
    /// apart at a call site, and the compiler will happily pick the wrong one for
    /// a `null` ([ADR-0094](../../../../../../../../book/src/adr/0094-name-the-overload-not-the-allocation.md)).
    ///
    /// `of` and not some other verb because the catalog already uses it for
    /// exactly this: [io.github.digitalsmile.goldberry.widgets.text.Text#of],
    /// `Badge.of`, `Checkbox.of` and `RadioGroup.of` are all the bound variant.
    public static Slider of(double min, double max, double step, Observable<?> source, DoubleConsumer onChange) {
        return new Slider(min, max, min, step, source, onChange, false, Attributes.NONE);
    }

    /// The value actually showing: the bound property's if there is one, else
    /// [#value()] — clamped to the range either way.
    ///
    /// Any `Number` is taken, because a model holding an `Integer` percentage is
    /// at least as likely as one holding a `Double`. Anything else — including a
    /// null, which is a property that has not loaded — reads as [#value()], the
    /// same rule [io.github.digitalsmile.goldberry.widgets.controls.checkbox.Checkbox#resolved()] and [io.github.digitalsmile.goldberry.widgets.controls.toggle.Toggle#resolved()] follow.
    ///
    /// **Clamped rather than trusted.** A model outside the range is an
    /// application bug, and a thumb rendered off the end of its track is a worse
    /// way to report it than a thumb pinned at the end.
    public double resolved() {
        var raw = source == null ? value
                : source.get() instanceof Number number ? number.doubleValue() : value;
        return clamp(raw);
    }

    /// Where the thumb sits, `0..1`. What [SliderGroove] positions by.
    ///
    /// Through the `Scale`, which is the whole of what a dB fader changes: the
    /// value is still a gain and the position is still a fraction of the travel,
    /// and only the curve between them differs.
    public double fraction() {
        return scale.toFraction(resolved(), min, max);
    }

    /// The value label's text, or null when there is no label.
    ///
    /// Formatted with [java.util.Locale#ROOT] rather than the default locale.
    /// That is not tidiness: a golden image rendered on a machine set to `de_DE`
    /// would draw `0,5` where CI drew `0.5`, and the failure would be a pixel
    /// diff on a developer's machine that nobody could reproduce on another.
    /// A locale-aware label is the application's to pass in already formatted.
    public String text() {
        return format == null ? null : label(format, resolved());
    }

    private static String label(String format, double value) {
        return String.format(Locale.ROOT, format, value);
    }


    /// What `PageUp` and `PageDown` move by — ten steps, or a tenth of the range
    /// when the slider is continuous.
    ///
    /// Not in §3, and derived rather than invented: "large step" has to be a
    /// multiple of the small one or the two disagree about where the value can
    /// land, and a tenth is what a continuous slider has instead of a step.
    ///
    /// The continuous answer is the range's, and is what a `PageUp` moves by only
    /// on a **linear** slider: with a scale, a page is a tenth of the travel
    /// rather than a tenth of the range, and the two coincide exactly when the
    /// scale is `Scale#LINEAR`.
    public double largeStep() {
        return step > 0 ? step * 10 : (max - min) / 10;
    }

    /// This slider with §3's optional tick marks — a count along the **travel**,
    /// not one per `step`.
    public Slider ticks(int ticks) {
        return new Slider(min, max, value, step, ticks, format, scale,
                source, onChange, disabled, attributes);
    }

    /// This slider with §3's optional value label, as a `String.format` pattern.
    ///
    /// A pattern and not a function, because §11 compares two records for
    /// equality and two lambdas are never equal (ADR-0080).
    public Slider format(String format) {
        return new Slider(min, max, value, step, ticks, format, scale,
                source, onChange, disabled, attributes);
    }

    /// This slider on a [Scale] — `fader`'s decibel mapping.
    public Slider scale(Scale scale) {
        return new Slider(min, max, value, step, ticks, format, scale,
                source, onChange, disabled, attributes);
    }

    @Override
    public Slider bound(Observable<?> source) {
        return new Slider(min, max, value, step, ticks, format, scale, source, onChange, disabled, attributes);
    }

    @Override
    public Slider withAttributes(Attributes attributes) {
        return new Slider(min, max, value, step, ticks, format, scale, source, onChange, disabled, attributes);
    }

    @Override
    public Observable<?> binding() {
        return source;
    }

    @Override
    public String cssType() {
        return "slider";
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

    /// The track, and — beside it, on the control's main axis — the value.
    ///
    /// The track holds the groove and the tick marks, which is what makes it the
    /// box the value is measured along even when a label has taken a chunk of the
    /// control's width ([#localPart()]).
    @Override
    public List<Widget> children() {
        var children = new ArrayList<Widget>(2);
        children.add(new SliderTrack(fraction(), ticks, disabled));
        if (format != null) {
            children.add(new SliderValue(text(), disabled));
        }
        return List.copyOf(children);
    }

    /// The value is a position along the **track**, not along the control.
    ///
    /// The two are the same box until a label is added, and then they are not:
    /// the label takes its width off the end of the track, so a pointer at the
    /// right-hand end of a labelled control is at 100% of the track and 88% of
    /// the slider. Measuring along the slider would put the value 12% short at
    /// that end, in a way that draws perfectly and reports no error at all
    /// ([ADR-0080]).
    @Override
    public String localPart() {
        return "slider-track";
    }

    /// A press jumps, and every move until the release follows — §3.1's "1:1".
    ///
    /// Both the press and the moves after it read the same thing, so there is no
    /// separate "am I dragging" state to keep: the router's implicit capture is
    /// what makes a `MOVED` between a press and a release mean "still dragging",
    /// and a `MOVED` with no button held reports `NaN` for its drag and is
    /// ignored here.
    @Override
    public void onPointer(PointerEvent event) {
        var dragging = switch (event.kind()) {
            case PRESSED -> event.button() == PointerEvent.Button.PRIMARY;
            // Only while a button is down. `dragX()` is NaN otherwise, which is
            // the router reporting "no gesture" through the arithmetic rather
            // than through a flag (ADR-0075).
            case MOVED -> !Double.isNaN(event.dragX());
            default -> false;
        };
        if (!dragging) {
            return;
        }
        ask(scale.toValue(fractionOf(event), min, max));
        // Consumed so an ancestor -- a scroll view, a list row -- does not also
        // act on a drag that is plainly this control's.
        event.consume();
    }

    /// Which way along the control the pointer is, honouring the `vertical` class.
    ///
    /// A vertical slider **inverts** the fraction, because zero is at the top of a
    /// screen and at the bottom of a fader. That inversion is the widget's rather
    /// than the router's: `fractionY()` reports what the pointer did, and what it
    /// means is a fact about the control.
    private double fractionOf(PointerEvent event) {
        return isVertical() ? 1 - event.local().fractionY() : event.local().fractionX();
    }

    /// Whether this slider runs bottom-to-top — `docs/core-widgets.md` §3's
    /// `fader`, spelled as a class for the reason `radio-group.inline` is: the
    /// widget names the semantics and the stylesheet names the axis.
    public boolean isVertical() {
        return attributes.classes().contains("vertical");
    }

    @Override
    public void onKey(KeyEvent event) {
        if (event.kind() != KeyEvent.Kind.PRESSED || !event.modifiers().none()) {
            return;
        }
        // Repeats are deliberately honoured, unlike every control before this:
        // holding an arrow to run a value up is how a slider is used, while
        // holding Space on a checkbox to flutter it is not.
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
        // Always consumed, even when the value did not move -- a slider at its
        // maximum still owns Right, and letting it through would hand the key to
        // a focus scope and move focus off the control the user is adjusting
        // (ADR-0073, ADR-0078).
        event.consume();
    }

    /// The **next value the user can reach** in `direction`, which is not always
    /// one step away.
    ///
    /// A model is allowed to hold a value off the grid — nothing snaps it on the
    /// way in, because snapping a value the application set would be the control
    /// overruling the model. So a slider from 0 to 100 stepping by 25 can be
    /// showing 40, and `Right` should offer **50**: the next value that is
    /// actually reachable, not `40 + 25 = 65` rounded to 75. Both readings agree
    /// whenever the value is already on the grid, which is every other time.
    ///
    /// A **continuous** slider has no grid, so it moves by `share` of the travel
    /// rather than of the range — a hundredth for an arrow, a tenth for a page.
    /// On a linear scale those are the same number; on a fader they are not, and
    /// stepping by a hundredth of the *gain* would move the thumb by a hair at
    /// the top of the travel and by a third of it at the bottom. A slider that
    /// does have a grid keeps it, because a grid is what the author asked for and
    /// the values on it are theirs rather than the screen's.
    private double stepFrom(double current, int direction, double share) {
        if (step <= 0) {
            return scale.toValue(
                    scale.toFraction(current, min, max) + direction * share, min, max);
        }
        if (share >= 0.1) {
            return current + direction * largeStep();
        }
        // The epsilon keeps a value that is *on* the grid from being read as
        // fractionally off it by floating-point, which would make one arrow press
        // out of every few do nothing.
        var index = (current - min) / step;
        var next = direction > 0
                ? Math.floor(index + 1e-9) + 1
                : Math.ceil(index - 1e-9) - 1;
        return min + next * step;
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }

    /// Asks the application for a value, snapped and clamped. It does **not** set
    /// one.
    ///
    /// Snapping happens here rather than in the application because it is a fact
    /// about *this control* — `step` is the slider's own attribute — and because
    /// an application repeating the arithmetic would be an application getting it
    /// slightly wrong. A `Property` swallows a value it already holds, so a drag
    /// within one step raises changes that settle rather than looping.
    private void ask(double raw) {
        if (!disabled && onChange != null) {
            onChange.accept(snap(clamp(raw)));
        }
    }

    private double clamp(double raw) {
        return raw < min ? min : raw > max ? max : raw;
    }

    /// Rounds to the nearest step **from `min`**, not from zero.
    ///
    /// A slider from 1 to 10 with a step of 2 offers 1, 3, 5, 7, 9 — the steps a
    /// user can actually reach from where the track starts. Snapping from zero
    /// would offer 2, 4, 6, 8, 10 and make `min` unreachable, which is the more
    /// surprising of the two and the one that hides at the end of the track.
    private double snap(double raw) {
        // The ends are always reachable, even when the range is not a whole
        // number of steps: 0..10 stepping by 3 has a grid of 0, 3, 6, 9, and a
        // user who presses End and lands on 9 has been told the end of the track
        // is not the end. `max` is a value the slider promises; the grid is a
        // convenience over the values between.
        if (step <= 0 || raw == min || raw == max) {
            return raw;
        }
        return clamp(min + Math.round((raw - min) / step) * step);
    }
}
