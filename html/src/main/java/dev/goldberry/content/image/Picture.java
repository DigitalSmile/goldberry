package dev.goldberry.content.image;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.image.Image;
import dev.goldberry.layout.Length;
import dev.goldberry.paint.Box;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.text.Paragraph;
import dev.goldberry.text.bidi.Directionality;
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
/// The image's intrinsic size is capped in proportion by its resolved CSS
/// limits. Its bottom edge shares the baseline of neighbouring text.
public final class Picture implements Widget.Leaf, Styled, Paints {
    private final Image image;
    private final String alt;
    private @Nullable Paragraph span;
    private @Nullable LogicalSize previousSize;
    private @Nullable Directionality previousDirection;

    public Picture(Image image, String alt) {
        this.image = image;
        this.alt = alt;
    }

    public Image image() {
        return image;
    }

    public String alt() {
        return alt;
    }

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
        var size = LogicalSize.of((float) width, (float) height);
        var font = context.font(style);
        if (span == null
                || span.font() != font
                || !size.equals(previousSize)
                || !style.directionality().equals(previousDirection)) {
            span = Paragraph.picture(font, image, size, style.directionality());
            previousSize = size;
            previousDirection = style.directionality();
        }
        return Box.text(span, style.color()).style(style);
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
