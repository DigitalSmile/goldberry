package dev.goldberry.widgets.core.image;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.image.anim.MovingPicture;
import dev.goldberry.image.anim.VectorAnimation;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;

/// A moving picture, playing: [ImageView]'s moving sibling.
///
/// ```java
/// var sticker = VectorAnimation.of(bytes);                         // a tgs, or Lottie JSON
/// new AnimationView(sticker, "🎉")
/// AnimationView.decorative(sticker).autoplay(false)
/// ```
///
/// It plays any [MovingPicture]: a [VectorAnimation], or a video sticker, which
/// `goldberry-media` reads as a `VideoAnimation`.
///
/// ## It plays on the frame loop
///
/// There is no timer. The view asks the frame loop for the next frame while
/// the animation moves, the way a spinner does, and draws the picture for the
/// frame's own clock — so every animation in a window is drawn on one tick, a
/// test drives it with a virtual clock, and an animation that has finished
/// ([VectorAnimation#loops(int)]) stops asking and leaves the loop idle.
///
/// It starts from its first frame when it is first drawn, and again when its
/// source changes; a view that was unmounted and mounted again starts again.
///
/// ## It is drawn at the size it is shown, over what is beneath it
///
/// The animation is drawn straight onto the frame, at the display's scale and
/// under whatever transform the tree put the view under. A vector animation is
/// as sharp in a 32-pixel chip as in a 256-pixel row and costs no raster of its
/// own; a video's picture is scaled to the box. Transparent parts let the
/// background through. A view that is scrolled out of sight is not painted, and
/// so not drawn.
///
/// ## It stands still when asked to
///
/// With `autoplay(false)`, or when the platform says its user wants less
/// movement, the view shows the animation's first frame and asks for no frames
/// at all. Turning either back starts it from the beginning.
///
/// ## The size is the canvas's until a stylesheet says otherwise
///
/// Its CSS type is `image`, so what a stylesheet says about images it says
/// about this, and the box is sized as an [ImageView]'s is: the animation's
/// canvas in logical pixels with no `width` or `height`, the other side
/// following the canvas's shape with one of them, and [Fit] deciding with
/// both.
///
/// ## Alt text is required, unless it is decoration
///
/// [ImageView]'s rule, for its reason: a sticker stands for something, usually
/// the emoji it was sent as, and that is its alt text.
///
/// Built in Java only; there is no markup node for it yet.
///
/// Read more: [Canvas, images and QR codes](https://goldberry.dev/docs/components/drawing.html#image).
///
/// @param source     the animation
/// @param alt        what it shows, for a reader who cannot see it
/// @param decorative whether it shows nothing a reader needs
/// @param autoplay   whether it plays without being asked
/// @param fit        how the canvas fills a box of another shape
/// @param attributes `id` and `class`, exactly as on every other widget
public record AnimationView(
        MovingPicture source, String alt, boolean decorative, boolean autoplay, Fit fit, Attributes attributes)
        implements Widget.Stateful, Attributed<AnimationView> {

    /// Written out so that the parameters taking null for a default can say so.
    public AnimationView(
            MovingPicture source,
            String alt,
            boolean decorative,
            boolean autoplay,
            @Nullable Fit fit,
            @Nullable Attributes attributes) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(alt, "alt");
        if (!decorative && alt.isBlank()) {
            throw new IllegalArgumentException(
                    "an animation needs alt text, or to be decorative: a picture a reader is told is a figure and"
                            + " nothing more is worse than one they are not told about");
        }
        this.source = source;
        this.alt = alt;
        this.decorative = decorative;
        this.autoplay = autoplay;
        this.fit = fit == null ? Fit.CONTAIN : fit;
        this.attributes = attributes == null ? Attributes.NONE : attributes;
    }

    /// An animation that plays, described by `alt`.
    public AnimationView(MovingPicture source, String alt) {
        this(source, alt, false, true, Fit.CONTAIN, Attributes.NONE);
    }

    /// An animation that is decoration, with no alt text and no semantics.
    public static AnimationView decorative(MovingPicture source) {
        return new AnimationView(source, "", true, true, Fit.CONTAIN, Attributes.NONE);
    }

    /// This view showing `value` instead, from its first frame.
    public AnimationView source(MovingPicture value) {
        return new AnimationView(value, alt, decorative, autoplay, fit, attributes);
    }

    /// This view, playing on its own (`true`, the default) or standing on its
    /// first frame (`false`).
    public AnimationView autoplay(boolean value) {
        return new AnimationView(source, alt, decorative, value, fit, attributes);
    }

    /// This view filling its box another way.
    public AnimationView fit(Fit value) {
        return new AnimationView(source, alt, decorative, autoplay, value, attributes);
    }

    @Override
    public AnimationView withAttributes(Attributes value) {
        return new AnimationView(source, alt, decorative, autoplay, fit, value);
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    @Override
    public State<?> createState() {
        return new AnimationState();
    }
}
