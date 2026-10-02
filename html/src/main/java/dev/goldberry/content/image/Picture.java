package dev.goldberry.content.image;

import java.util.List;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.image.Image;
import dev.goldberry.layout.Length;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// An image inside a document — `picture`, a **part**.
///
/// What draws an image for both content views, where before it was only its alt
/// text. The toolkit could already decode an image and draw one into a frame; what
/// was missing was a widget that does so inside a document, and an answer about who
/// fetches. This is the widget; the fetching is still the application's, through
/// [dev.goldberry.content.ImageSource].
///
/// A part rather than a widget an application builds: it is a CSS type, `picture`,
/// that a stylesheet can reach, and a document is what puts one on the screen.
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
/// Read more:
/// [Links, images and tasks](https://goldberry.dev/docs/components/content.html#links-images-and-tasks).
///
/// @param image what to draw. A **value** — it owns no native handle, so
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
