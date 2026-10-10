package dev.goldberry.widgets.core.image;

import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;

/// An [AnimationView]'s playhead: when its animation started, kept across
/// frames and started again when the source changes.
final class AnimationState extends State<AnimationView> {

    private Playhead playhead = new Playhead();

    @Override
    protected void didUpdateWidget(AnimationView previous) {
        if (previous.source() != widget().source()) {
            playhead = new Playhead();
        }
    }

    @Override
    public Widget build(BuildContext context) {
        var view = widget();
        var playing = new AnimationPaint(view.source(), view.fit(), view.autoplay(), playhead);
        return view.decorative()
                ? new AnimationBox(playing, view.attributes())
                : new AnimationFigure(playing, view.alt(), view.attributes());
    }

    /// When an animation began, on the frame clock: unset until the first frame
    /// that plays it, and unset again when it stops playing, so that it starts
    /// from the beginning when it plays again.
    ///
    /// Mutable, and read and written only during a render, on the UI thread.
    static final class Playhead {

        private double startedAt = Double.NaN;

        /// Milliseconds since it started, starting it now if it had not.
        long elapsedAt(double now) {
            if (Double.isNaN(startedAt)) {
                startedAt = now;
            }
            return (long) Math.floor(now - startedAt);
        }

        /// Back to the beginning.
        void stop() {
            startedAt = Double.NaN;
        }
    }
}
