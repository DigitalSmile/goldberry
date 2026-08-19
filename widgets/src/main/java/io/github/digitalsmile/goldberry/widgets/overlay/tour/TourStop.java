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
        Stop stop, LogicalRect target, int index, int count,
        Runnable onBack, Runnable onNext, Runnable onSkip)
        implements Widget.Leaf, Styled, Paints, Handles {

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

    private static float points(StyleLength length) {
        return length instanceof StyleLength.Points p ? p.value() : 0;
    }

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
                new TourVeil(target, LogicalRect.of(0, 0, 0, 0)),
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
        // The window's size, read off the style the cascade resolved for this
        // node -- which fills the window, because a filling overlay is inset to
        // all four sides. Zero when it has not been laid out yet, which reads as
        // "do not clamp" and is right for a frame that has nothing to clamp to.
        var width = points(style.width());
        var height = points(style.height());
        var veil = children.get(0);
        var card = children.get(1);
        var below = target.top() + target.size().height() + GAP;
        var fitsBelow = height <= 0 || below + ESTIMATED_HEIGHT + GAP <= height;
        var cardTop = fitsBelow ? below : Math.max(GAP, target.top() - ESTIMATED_HEIGHT - GAP);
        var cardLeft = target.left();
        if (width > 0) {
            cardLeft = Math.min(cardLeft, width - WIDTH - GAP);
        }
        cardLeft = Math.max(GAP, cardLeft);
        return Box.of().style(style).children(
                veil.position(PositionType.ABSOLUTE)
                        .inset(Insets.all(StyleLength.points(0))),
                card.position(PositionType.ABSOLUTE)
                        .inset(new Insets(
                                StyleLength.points(cardLeft), StyleLength.UNDEFINED,
                                StyleLength.points(cardTop), StyleLength.UNDEFINED))
                        .size(StyleLength.points(WIDTH), StyleLength.UNDEFINED));
    }
}
