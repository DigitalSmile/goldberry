package io.github.digitalsmile.goldberry.widgets.overlay.tour;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.value.Transform;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.natives.yoga.Insets;
import io.github.digitalsmile.goldberry.natives.yoga.style.PositionType;
import io.github.digitalsmile.goldberry.natives.yoga.style.StyleLength;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.semantics.Role;
import io.github.digitalsmile.goldberry.widget.semantics.Semantics;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.core.Phase;
import io.github.digitalsmile.goldberry.widgets.core.Row;
import io.github.digitalsmile.goldberry.widgets.core.Spacer;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// One stop of a [Tour]: the veil, and the card beside the target.
///
/// It is the node that fills the window, so the veil can be sized against it and
/// the card can be placed anywhere in it.
///
/// ## Placement
///
/// Below the target when there is room, above it when there is not, clamped
/// horizontally so a card beside something at the window's edge stays on screen.
/// That is `Placement`'s flip-and-shift, done here in six lines rather than
/// reused, because `Placement` positions a **window** against a display's work
/// area and this positions a box inside another box — the same idea, different
/// coordinate space, and sharing it would mean teaching it about both
/// (ADR-0121).
///
/// ## Keyboard
///
/// §5: "`Esc` skips the whole tour, not one stop." Left and Right move between
/// stops, which is what the arrows mean in every wizard.
record TourStop(
        Stop stop,
        LogicalRect target,
        LogicalRect cameFrom,
        Phase travel,
        Phase arrival,
        LogicalRect window,
        int index,
        int count,
        Runnable onBack,
        Runnable onNext,
        Runnable onSkip,
        java.util.function.Consumer<LogicalRect> onWindow,
        double cardHeight,
        java.util.function.DoubleConsumer onCardHeight)
        implements Widget.Leaf,
                Styled,
                Paints,
                Handles,
                io.github.digitalsmile.goldberry.input.handler.Located,
                Semantics {

    /// How far the card sits from the target, and from the window's edge.
    private static final float GAP = 12;

    /// The card's width. Fixed, because a tour reads as a sequence and a card
    /// that changed width between stops would draw attention to itself rather
    /// than to what it points at.
    private static final float WIDTH = 280;

    /// Enough for a title, three lines of body and the buttons — the height used
    /// to decide whether the card fits below its target **on the first frame**,
    /// before the card has been laid out and had anything to report.
    ///
    /// It used to be the number for every frame, and being wrong put a card above
    /// its target when it would have fitted below. [#cardHeight] is what the card
    /// actually came out as, banked by [TourState] from the frame before — the
    /// same last-frame read this widget already does for the window's own
    /// rectangle, one node further in ([ADR-0268]).
    private static final float ESTIMATED_HEIGHT = 132;

    /// How far the ring sits outside the target, so it frames the widget rather
    /// than covering its outermost pixels.
    private static final float RING = 3;

    @Override
    public String cssType() {
        return "tour";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public boolean isFocusable() {
        return true;
    }

    /// How big the window is.
    ///
    /// This node is inset on all four sides of a filling overlay, so its own
    /// rectangle **is** the window — and there is no other way to learn it. The
    /// cascade cannot say: a box sized by absolute insets has no `width` in its
    /// style, so reading the resolved style gives nothing
    /// (ADR-0121).
    ///
    /// Safe against [io.github.digitalsmile.goldberry.input.handler.Located]'s rule: what
    /// this reports is fixed by the overlay's insets, so nothing drawn inside it
    /// can change what is reported.
    @Override
    public void located(LogicalRect self, LogicalRect clip) {
        onWindow.accept(self);
    }

    @Override
    public List<Widget> children() {
        var buttons = new ArrayList<Widget>(4);
        buttons.add(new Button("Skip", onSkip).withAttributes(Attributes.NONE.classes("tour-skip")));
        buttons.add(new Spacer());
        if (onBack != null) {
            buttons.add(new Button("Back", onBack).withAttributes(Attributes.NONE.classes("tour-back")));
        }
        // The last stop's forward button says so, because "Next" on the last of
        // five is a promise the tour cannot keep.
        buttons.add(new Button(index + 1 >= count ? "Done" : "Next", onNext)
                .withAttributes(Attributes.NONE.classes("tour-next")));
        return List.of(
                new TourVeil(target, cameFrom, travel, window),
                // The lit rectangle gets an edge of its own. Without one the
                // target is "the part that is not dim", which reads as a hole
                // rather than as the subject — and where the widget's own
                // background matches the window's, as a toolbar's does, there is
                // no visible boundary at all.
                new TourRing(target),
                new TourCard(
                        new Column(
                                new Text(stop.title(), Attributes.NONE.classes("tour-title")),
                                new Text(stop.body(), Attributes.NONE.classes("tour-body")),
                                new Text((index + 1) + " of " + count, Attributes.NONE.classes("tour-count")),
                                new Row(buttons.toArray(Widget[]::new))),
                        onCardHeight));
    }

    @Override
    public void onKey(KeyEvent event) {
        if (event.kind() != KeyEvent.Kind.PRESSED) {
            return;
        }
        switch (event.key()) {
            case ESCAPE -> {
                onSkip.run();
                event.consume();
            }
            case RIGHT -> {
                onNext.run();
                event.consume();
            }
            case LEFT -> {
                if (onBack != null) {
                    onBack.run();
                    event.consume();
                }
            }
            default -> {}
        }
    }

    /// Where the cut-out is **right now**: between the stop it is leaving and the
    /// one it is arriving at, on §3.1's `base`.
    ///
    /// §3.1's tour row asks for the cut-out to "`translate`+size" between stops,
    /// and both halves fall out of interpolating the rectangle: a target that
    /// moves and changes size does both at once, and doing it as one rectangle is
    /// what keeps the ring and the veil's hole agreeing with each other on every
    /// frame.
    ///
    /// This is a `Phase` and not a `transition` for the reason [Phase] itself
    /// gives: a cut-out's rectangle is computed from an anchor the cascade has
    /// never seen, so there are no two styles to interpolate between
    /// ([ADR-0269]).
    private LogicalRect litRectAt(double now) {
        if (travel == null || cameFrom == null) {
            return target;
        }
        var t = travel.progressAt(now);
        if (t >= 1) {
            return target;
        }
        return LogicalRect.of(
                lerp(cameFrom.left(), target.left(), t),
                lerp(cameFrom.top(), target.top(), t),
                lerp(cameFrom.size().width(), target.size().width(), t),
                lerp(cameFrom.size().height(), target.size().height(), t));
    }

    private static float lerp(double from, double to, double t) {
        return (float) (from + (to - from) * t);
    }

    /// §1.7's overlay curve on the card: `opacity` 0→1 with a 4px rise.
    ///
    /// §3.1 says a tour's card animates "as `popover`", and that row is
    /// "`opacity` 0→1, `translateY` −4→0, `scale` 0.98→1 from anchor origin,
    /// base". Two of the three are here; the **scale** is not, and deliberately —
    /// `transform-origin` is resolved against a box the painter measures, and a
    /// card that scaled from its own centre rather than from its anchor would
    /// read as a pop rather than as an arrival. `popover` itself has the same gap
    /// and the same reason.
    ///
    /// The arrival is the **tour's**, not the stop's: it runs once when the tour
    /// opens, so advancing does not fade the card in again. What moves between
    /// stops is the cut-out, which is [#litRectAt].
    private Box arriving(Box card, double now) {
        var progress = arrival.progressAt(now);
        if (progress >= 1) {
            return card;
        }
        return card.opacity(progress)
                .transform(Transform.of(new Transform.Function.Translate(
                        Transform.Length.px(0), Transform.Length.px((1 - progress) * -RISE))));
    }

    /// How far the card rises as it arrives — §3.1's `translateY` −4→0.
    private static final float RISE = 4;

    /// Frames are owed while either phase is running — the arrival that fades the
    /// card in, and the travel that moves the cut-out between stops.
    ///
    /// Without this the first frame of each would be the only one: nothing else
    /// in a tour changes, so the loop would go idle mid-animation and leave a
    /// half-faded card on screen ([ADR-0269]).
    @Override
    public boolean isAnimating() {
        return arrival.isRunning() || (travel != null && travel.isRunning());
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        // The window, as the last frame measured this node -- see `located`.
        // Zero on the very first frame, which reads as "do not clamp" and is
        // right for a frame that has nothing to clamp against yet.
        var width = window.size().width();
        var height = window.size().height();
        var veil = children.get(0);
        var ring = children.get(1);
        var card = children.get(2);
        // Where the cut-out is on this frame. The ring and the card follow it
        // rather than the destination, so the three move together -- §3.1's
        // "veil cut-out `translate`+size base" is one rectangle travelling and
        // not three things arriving separately (ADR-0269).
        var lit = litRectAt(context.nowMillis());
        var below = lit.top() + lit.size().height() + GAP;
        // What the card measured last frame, or the estimate on the first —
        // where nothing has been laid out and there is nothing to have measured.
        var card_h = cardHeight > 0 ? (float) cardHeight : ESTIMATED_HEIGHT;
        var fitsBelow = height <= 0 || below + card_h + GAP <= height;
        var cardTop = fitsBelow ? below : Math.max(GAP, lit.top() - card_h - GAP);
        // Centred on the target rather than aligned to its left edge. A stop
        // describing a narrow control had its card start at that control's `x`,
        // which for anything near the left of the window put every card in the
        // same place and made the sequence look as though it were not moving.
        var cardLeft = lit.left() + lit.size().width() / 2 - WIDTH / 2;
        if (width > 0) {
            cardLeft = Math.min(cardLeft, width - WIDTH - GAP);
        }
        cardLeft = Math.max(GAP, cardLeft);
        // A column, so the card is content-height. In a row the cross axis is
        // the vertical one, and an absolutely-positioned child with a `top` and
        // no `bottom` is stretched down the whole window by the default
        // `align-items: stretch` -- which made the first tour a card the height
        // of the screen. The veil is unaffected either way: it states all four
        // insets, so there is nothing left for an alignment to decide.
        return Box.of()
                .style(style)
                .direction(io.github.digitalsmile.goldberry.natives.yoga.style.FlexDirection.COLUMN)
                .alignItems(io.github.digitalsmile.goldberry.natives.yoga.style.Align.FLEX_START)
                .children(
                        veil.position(PositionType.ABSOLUTE).inset(Insets.all(StyleLength.points(0))),
                        ring.position(PositionType.ABSOLUTE)
                                .inset(new Insets(
                                        StyleLength.points(lit.top() - RING),
                                        StyleLength.UNDEFINED,
                                        StyleLength.UNDEFINED,
                                        StyleLength.points(lit.left() - RING)))
                                .size(
                                        StyleLength.points(lit.size().width() + RING * 2),
                                        StyleLength.points(lit.size().height() + RING * 2)),
                        arriving(card, context.nowMillis())
                                .position(PositionType.ABSOLUTE)
                                // `Insets` is in CSS order -- top, right, bottom, left.
                                // Left and top the other way round anchors the card by
                                // its top *and its bottom*, which stretches it down the
                                // whole window and puts it against the left edge.
                                .inset(new Insets(
                                        StyleLength.points(cardTop),
                                        StyleLength.UNDEFINED,
                                        StyleLength.UNDEFINED,
                                        StyleLength.points(cardLeft)))
                                .size(StyleLength.points(WIDTH), StyleLength.UNDEFINED));
    }

    @Override
    public Role role() {
        return Role.DIALOG;
    }

    @Override
    public String accessibleName() {
        return stop.title();
    }
}
