package io.github.digitalsmile.goldberry.widgets.controls.slider;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.DoubleConsumer;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.semantics.Role;
import io.github.digitalsmile.goldberry.widget.semantics.Semantics;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The node a stylesheet means when it writes `slider` — what [Slider] describes,
/// and where the pointer and the keyboard actually arrive.
///
/// The split is `scroll`'s and `tabs`': a stateful widget that was also styled
/// would put two `slider` nodes in the cascade, one inside the other, and every
/// rule would apply twice (ADR-0109, ADR-0116). [Slider] is the value an
/// application writes and this is the node a theme selects; they are never the
/// same element.
///
/// ## The pointer is mapped over the travel, not over the track
///
/// The thumb is a 16px disc centred on the value, so its centre can only reach
/// from 8px to `width − 8`: half a thumb is unreachable at each end. Mapping the
/// pointer over the track's **full width** — which is what this did until
/// ADR-0430 — therefore asks for a value the thumb cannot be drawn at, and the
/// gap between the finger and the disc grows to half a thumb at the extremes. A
/// user dragging to `max` sees the thumb stop 8px short of the pointer and lets
/// go early.
///
/// So the fraction is `(x − thumb/2) / (width − thumb)`, clamped — see
/// [#travel]. It is **monotonic** and it reaches both ends exactly, which the old
/// mapping also did; what it adds is that the position it names is a position the
/// thumb can occupy.
///
/// The number it needs is `thumb`, and the stylesheet owns it. It arrives through
/// `--gb-slider-thumb-size`, read in [#render] where the cascade is in hand and
/// banked into [SliderState] for [#onPointer], which has no context to ask
/// ([ADR-0251]).
///
/// ## The tick marks already had this right
///
/// `slider-ticks` is inset by `padding: 0 8px` — half a thumb, written in the
/// stylesheet beside the thumb's own width — so a mark has always named a
/// position the thumb's centre can reach, and `SliderGeometryTest` has always
/// asserted it. The marks and the thumb agreed with each other while the *finger*
/// was the thing up to 8px out. This closes the gap from the other side.
///
/// @param slider  the value this node draws, and the whole of the arithmetic
/// @param thumb   the thumb's width in logical pixels, as the last [#render]
///                resolved it
/// @param onSized where to bank a thumb width that changed
record SliderControl(Slider slider, double thumb, DoubleConsumer onSized)
        implements Widget.Leaf, Styled, Paints, Handles, Semantics {

    /// §3's thumb 16, and the **default** — `--gb-slider-thumb-size` is the token
    /// that overrides it, exactly as `--gb-scroll-line` overrides a wheel line.
    ///
    /// A default and not a constant, because a `density-compact` sheet or an
    /// application that wants a fatter grip is entitled to move it, and a number
    /// no widget can read is a number an author sets and nothing honours.
    static final double THUMB = 16;

    /// The token an application overrides [#THUMB] with — §3's "metrics ship as
    /// component-token defaults".
    ///
    /// **It is also what `slider-thumb` sizes itself from**, and that is the
    /// whole point: one declaration feeds the disc the user sees and the
    /// arithmetic the pointer goes through, so the two cannot disagree. The one
    /// place they still can is `slider-ticks`' inset, which has to be *half* of
    /// it and there is no `calc()` in §8's subset — `SliderTest` holds the two
    /// numbers together instead (ADR-0430).
    static final String THUMB_TOKEN = "--gb-slider-thumb-size";

    @Override
    public String cssType() {
        return "slider";
    }

    @Override
    public @Nullable String id() {
        return slider.attributes().id();
    }

    @Override
    public Set<String> classes() {
        return slider.attributes().classes();
    }

    @Override
    public boolean isFocusable() {
        return !slider.disabled();
    }

    @Override
    public boolean isDisabled() {
        return slider.disabled();
    }

    /// The track, and — beside it, on the control's main axis — the value.
    ///
    /// The track holds the groove and the tick marks, which is what makes it the
    /// box the value is measured along even when a label has taken a chunk of the
    /// control's width ([#localPart()]).
    @Override
    public List<Widget> children() {
        var children = new ArrayList<Widget>(2);
        children.add(new SliderTrack(slider.fraction(), slider.ticks(), slider.disabled()));
        if (slider.format() != null) {
            children.add(new SliderValue(slider.text(), slider.disabled()));
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
    public @Nullable String localPart() {
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
        var dragging =
                switch (event.kind()) {
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
        slider.ask(slider.scale().toValue(fractionOf(event), slider.min(), slider.max()));
        // Consumed so an ancestor -- a scroll view, a list row -- does not also
        // act on a drag that is plainly this control's.
        event.consume();
    }

    /// Which way along the control the pointer is, honouring the `vertical` class.
    ///
    /// A vertical slider **inverts** the fraction, because zero is at the top of a
    /// screen and at the bottom of a fader. That inversion is the widget's rather
    /// than the router's: the router reports what the pointer did, and what it
    /// means is a fact about the control.
    private double fractionOf(PointerEvent event) {
        var local = event.local();
        return slider.isVertical() ? 1 - travel(local.y(), local.height()) : travel(local.x(), local.width());
    }

    /// Where `along` falls on the thumb's **travel**, `0..1`.
    ///
    /// The travel is `extent − thumb`, because the centre of a disc `thumb` wide
    /// starts half a thumb in and stops half a thumb short. Clamped rather than
    /// trusted: the press takes the pointer until the release (ADR-0058), so a
    /// drag that wanders off the end of the track arrives here with `along`
    /// outside the box, and pinning it at the end is what "1:1" means there.
    ///
    /// **A track no wider than its thumb has no travel at all**, and then this
    /// falls back to the position fraction. That is not a special case invented
    /// for the arithmetic — it is the only answer that is still monotonic and
    /// still reaches both ends, which are the two properties anything that maps a
    /// pointer to a value has to keep. It also covers
    /// [PointerEvent.Local#UNKNOWN], the zero-sized local a press with no layout
    /// behind it carries, which reads as the start of the track exactly as it did
    /// before.
    private double travel(double along, double extent) {
        if (extent <= 0) {
            return 0;
        }
        var length = extent - thumb;
        return length <= 0 ? clamp01(along / extent) : clamp01((along - thumb / 2) / length);
    }

    private static double clamp01(double raw) {
        return raw < 0 ? 0 : raw > 1 ? 1 : raw;
    }

    @Override
    public void onKey(KeyEvent event) {
        if (event.kind() != KeyEvent.Kind.PRESSED || !event.modifiers().none()) {
            return;
        }
        // Repeats are deliberately honoured, unlike every control before this:
        // holding an arrow to run a value up is how a slider is used, while
        // holding Space on a checkbox to flutter it is not.
        var current = slider.resolved();
        var moved =
                switch (event.key()) {
                    case LEFT, DOWN -> slider.stepFrom(current, -1, 0.01);
                    case RIGHT, UP -> slider.stepFrom(current, 1, 0.01);
                    case PAGE_DOWN -> slider.stepFrom(current, -1, 0.1);
                    case PAGE_UP -> slider.stepFrom(current, 1, 0.1);
                    case HOME -> slider.min();
                    case END -> slider.max();
                    default -> Double.NaN;
                };
        if (Double.isNaN(moved)) {
            return;
        }
        slider.ask(moved);
        // Always consumed, even when the value did not move -- a slider at its
        // maximum still owns Right, and letting it through would hand the key to
        // a focus scope and move focus off the control the user is adjusting
        // (ADR-0073, ADR-0078).
        event.consume();
    }

    /// Draws the box, and banks the thumb's width on the way past.
    ///
    /// The **only** place the cascade and this node are in the same room. A
    /// `render` knows which element it is answering for, so `--gb-slider-thumb-size`
    /// resolves against *this* slider and an author may set it on one control
    /// without moving every other one — which is the property a Java constant
    /// would not have had ([ADR-0251]).
    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        onSized.accept(context.length(THUMB_TOKEN, THUMB));
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }

    @Override
    public Role role() {
        return Role.SLIDER;
    }

    /// No name of its own: a slider carries no label of its own; the `field` or the text beside it names it.
    @Override
    public @Nullable String accessibleName() {
        return null;
    }
}
