package dev.goldberry.widgets.core.image;

import java.util.List;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.image.anim.MovingPicture;
import dev.goldberry.paint.Box;
import dev.goldberry.paint.Frame;
import dev.goldberry.paint.Painter;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.widget.style.Paints;

/// What [AnimationBox] and [AnimationFigure] share: how big the box is, which
/// moment is drawn in it, and whether the frame loop is asked for another.
///
/// @param animation the animation
/// @param fit       how its canvas fills the box
/// @param autoplay  whether it plays without being asked
/// @param playhead  when it started
record AnimationPaint(MovingPicture animation, Fit fit, boolean autoplay, AnimationState.Playhead playhead) {

    /// Whether it moves in this frame: asked to, and not by a user who asked
    /// for less movement.
    boolean playing(Paints.Context context) {
        return autoplay && !context.reducedMotion();
    }

    /// Milliseconds into the animation this frame shows: the first frame when
    /// it is not playing.
    long elapsed(Paints.Context context) {
        if (!playing(context)) {
            playhead.stop();
            return 0;
        }
        return playhead.elapsedAt(context.nowMillis());
    }

    Box render(ComputedStyle style, List<Box> children, Paints.Context context) {
        var box = Box.of().style(style).children(children.toArray(Box[]::new));
        var size = ImagePaint.intrinsic(style.width(), style.height(), animation.width(), animation.height(), 0, style);
        if (size != null) {
            box = box.size(size[0], size[1]);
        }
        return box.painting(new Moment(animation, elapsed(context), fit));
    }

    /// Whether the frame loop should come back: while it plays and has not
    /// finished. Asked straight after [#render], so the playhead is already set.
    boolean isAnimating(Paints.Context context) {
        return playing(context) && !animation.isDoneAt(playhead.elapsedAt(context.nowMillis()));
    }

    /// One moment of an animation, drawn into the box.
    ///
    /// A record, so two frames that show the same moment of the same animation
    /// are the same painting, and a frame that repaints for some other reason
    /// sees nothing new here.
    ///
    /// @param animation the animation
    /// @param elapsed   milliseconds in
    /// @param fit       how the canvas fills the box
    record Moment(MovingPicture animation, long elapsed, Fit fit) implements Painter {

        @Override
        public void paint(Frame frame, LogicalSize size) {
            var boxWidth = size.width();
            var boxHeight = size.height();
            var naturalWidth = animation.width();
            var naturalHeight = animation.height();
            if (!(boxWidth > 0) || !(boxHeight > 0)) {
                return;
            }
            var scale = switch (fit) {
                case CONTAIN -> Math.min(boxWidth / naturalWidth, boxHeight / naturalHeight);
                case COVER -> Math.max(boxWidth / naturalWidth, boxHeight / naturalHeight);
                case NONE -> 1.0;
                case FILL -> Double.NaN;
            };
            if (Double.isNaN(scale)) {
                animation.paint(frame, elapsed, 0, 0, boxWidth, boxHeight);
                return;
            }
            var width = naturalWidth * scale;
            var height = naturalHeight * scale;
            animation.paint(frame, elapsed, (boxWidth - width) / 2, (boxHeight - height) / 2, width, height);
        }
    }
}
