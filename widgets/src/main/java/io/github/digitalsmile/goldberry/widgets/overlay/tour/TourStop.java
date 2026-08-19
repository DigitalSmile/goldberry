package io.github.digitalsmile.goldberry.widgets.overlay.tour;

import io.github.digitalsmile.goldberry.backend.LogicalRect;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.Handles;
import io.github.digitalsmile.goldberry.input.Key;
import io.github.digitalsmile.goldberry.input.KeyEvent;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.natives.yoga.Insets;
import io.github.digitalsmile.goldberry.natives.yoga.PositionType;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.core.Row;
import io.github.digitalsmile.goldberry.widgets.core.Spacer;
import io.github.digitalsmile.goldberry.widgets.text.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
/// ([ADR-0121](../../../../../../../../book/src/adr/0121-a-tour-is-a-veil-and-a-sequence.md)).
///
/// ## Keyboard
///
/// §5: "`Esc` skips the whole tour, not one stop." Left and Right move between
/// stops, which is what the arrows mean in every wizard.
record TourStop(
        Stop stop, LogicalRect target, LogicalRect window, int index, int count,
        Runnable onBack, Runnable onNext, Runnable onSkip,
        java.util.function.Consumer<LogicalRect> onWindow)
        implements Widget.Leaf, Styled, Paints, Handles,
                io.github.digitalsmile.goldberry.input.Located {

    /// How far the card sits from the target, and from the window's edge.
    private static final float GAP = 12;

    /// The card's width. Fixed, because a tour reads as a sequence and a card
    /// that changed width between stops would draw attention to itself rather
    /// than to what it points at.
    private static final float WIDTH = 280;

    /// Enough for a title, three lines of body and the buttons — the height used
    /// to decide whether the card fits below its target. An estimate, and the
    /// consequence of being wrong is a card placed above when it would have fitted
    /// below.
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
    /// ([ADR-0121](../../../../../../../../book/src/adr/0121-a-tour-is-a-veil-and-a-sequence.md)).
    ///
    /// Safe against [io.github.digitalsmile.goldberry.input.Located]'s rule: what
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
                new TourVeil(target, window),
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
                                new Text((index + 1) + " of " + count,
                                        Attributes.NONE.classes("tour-count")),
                                new Row(buttons.toArray(Widget[]::new)))));
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
            default -> {
            }
        }
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
        var below = target.top() + target.size().height() + GAP;
        var fitsBelow = height <= 0 || below + ESTIMATED_HEIGHT + GAP <= height;
        var cardTop = fitsBelow ? below : Math.max(GAP, target.top() - ESTIMATED_HEIGHT - GAP);
        // Centred on the target rather than aligned to its left edge. A stop
        // describing a narrow control had its card start at that control's `x`,
        // which for anything near the left of the window put every card in the
        // same place and made the sequence look as though it were not moving.
        var cardLeft = target.left() + target.size().width() / 2 - WIDTH / 2;
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
        return Box.of().style(style)
                .direction(io.github.digitalsmile.goldberry.natives.yoga.FlexDirection.COLUMN)
                .alignItems(io.github.digitalsmile.goldberry.natives.yoga.Align.FLEX_START)
                .children(
                veil.position(PositionType.ABSOLUTE)
                        .inset(Insets.all(StyleLength.points(0))),
                ring.position(PositionType.ABSOLUTE)
                        .inset(new Insets(
                                StyleLength.points(target.top() - RING),
                                StyleLength.UNDEFINED, StyleLength.UNDEFINED,
                                StyleLength.points(target.left() - RING)))
                        .size(StyleLength.points(target.size().width() + RING * 2),
                                StyleLength.points(target.size().height() + RING * 2)),
                card.position(PositionType.ABSOLUTE)
                        // `Insets` is in CSS order -- top, right, bottom, left.
                        // Left and top the other way round anchors the card by
                        // its top *and its bottom*, which stretches it down the
                        // whole window and puts it against the left edge.
                        .inset(new Insets(
                                StyleLength.points(cardTop), StyleLength.UNDEFINED,
                                StyleLength.UNDEFINED, StyleLength.points(cardLeft)))
                        .size(StyleLength.points(WIDTH), StyleLength.UNDEFINED));
    }
}
