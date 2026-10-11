package dev.goldberry.example.ui.shell;

import java.util.List;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.layout.Insets;
import dev.goldberry.layout.Length;
import dev.goldberry.layout.Position;
import dev.goldberry.paint.Box;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// A ring drawn for a moment around a card that search has just scrolled to,
/// so the eye lands on it.
///
/// Floated over the whole window and transparent everywhere but the ring, so
/// the card under it stays live; the application takes it away again after a
/// second. The ring is a `spotlight-ring` the stylesheet draws, and the window
/// is a `spotlight` with nothing on it.
///
/// Read more: [Overlays](https://goldberry.dev/docs/components/overlays.html#tours).
///
/// @param target where the card was painted, in the window's coordinates
public record Spotlight(LogicalRect target) implements Widget.Leaf, Styled, Paints {

    /// How far the ring stands off the card, in logical pixels.
    static final float OUTSET = 4;

    @Override
    public String cssType() {
        return "spotlight";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public List<Widget> children() {
        return List.of(new Ring());
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var ring = children.getFirst()
                .position(Position.ABSOLUTE)
                // CSS order: top, right, bottom, left.
                .inset(new Insets(
                        Length.points(target.top() - OUTSET),
                        Length.UNDEFINED,
                        Length.UNDEFINED,
                        Length.points(target.left() - OUTSET)))
                .size(
                        Length.points(target.size().width() + OUTSET * 2),
                        Length.points(target.size().height() + OUTSET * 2));
        return Box.of().style(style).children(ring);
    }

    /// The ring itself: an edge and no fill.
    record Ring() implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "spotlight-ring";
        }

        @Override
        public Set<String> classes() {
            return Set.of();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style);
        }
    }
}
