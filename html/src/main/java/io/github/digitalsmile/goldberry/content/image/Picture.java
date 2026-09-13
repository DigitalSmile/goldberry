package io.github.digitalsmile.goldberry.content.image;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.image.Image;
import io.github.digitalsmile.goldberry.layout.Length;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// An image inside a document — `picture`, a **part**.
///
/// What closes "an image is its alt text" for both content views, and it is not the
/// engine's work that `docs/gaps.md` G17 said was missing: `Image.decode` and
/// `Frame.drawImage` have existed since ADR-0283, and what was absent was a widget
/// that draws one and an answer about who fetches. This is the widget; the fetching
/// is still the application's, through [ImageSource] (ADR-0300).
///
/// A part rather than a widget an application builds (ADR-0065): it is a CSS type,
/// `picture`, and a document is what puts one on the screen.
///
/// ## How big it is, and why that is not Yoga's decision
///
/// The box carries its size rather than a measure callback, because the toolkit's
/// measured leaves are text and icons and there is no general measure hook on [Box].
/// So the size is arithmetic here:
///
/// - **Natural size**, in logical pixels, from the image's own pixel dimensions.
/// - **Capped by `max-width` and `max-height`** from the cascade, *in proportion* —
///   which is the part a `max-width` rule alone would get wrong, because Yoga would
///   clamp the width and leave the height, and the picture would be squashed rather
///   than smaller.
/// - A **percentage** cap is ignored rather than guessed at: resolving one needs the
///   container's width, which is exactly what a measure callback would have and this
///   does not. `html.css` and `markdown.css` therefore write their caps in points,
///   and say so beside the rule.
///
/// @param image what to draw. A **value** (ADR-0283) — it owns no native handle, so
///        holding one in a widget that is rebuilt every frame is safe, which is the
///        property that makes this a two-line widget
/// @param alt what the author wrote for a reader who cannot see it, used as the
///        accessible name
public record Picture(Image image, String alt) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "picture";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var natural = image.size();
        var width = (double) natural.width();
        var height = (double) natural.height();
        // In proportion, and the smaller of the two caps wins -- so a tall image in a
        // narrow column and a wide one in a short box both end up inside their box
        // with their shape intact.
        var scale = Math.min(
                cap(style.limits().maxWidth(), width), cap(style.limits().maxHeight(), height));
        if (scale < 1.0) {
            width *= scale;
            height *= scale;
        }
        var drawnWidth = width;
        var drawnHeight = height;
        return Box.of()
                .style(style)
                .size(Length.points((float) drawnWidth), Length.points((float) drawnHeight))
                .painting((frame, size) -> frame.drawImage(image, 0, 0, drawnWidth, drawnHeight));
    }

    /// How much of `natural` fits under `cap`, as a factor of 1 or less.
    ///
    /// 1 for no cap at all and for a percentage, which this cannot resolve — see the
    /// class comment.
    private static double cap(Length limit, double natural) {
        return switch (limit) {
            case Length.Points points when points.value() > 0 && natural > 0 -> Math.min(1.0, points.value() / natural);
            default -> 1.0;
        };
    }
}
