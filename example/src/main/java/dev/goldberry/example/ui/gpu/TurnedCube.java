package dev.goldberry.example.ui.gpu;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.gpu.Cube;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.gpu.view.Canvas3d;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.slider.Slider;

/// The same cube in a canvas drawn on demand: when its revision changes, which
/// the slider does. Between moves nothing is rendered and the last picture is
/// shown again.
///
/// Read more: [`canvas3d`](https://goldberry.dev/docs/components/gpu.html#canvas3d).
record TurnedCube() implements Widget.Stateful {

    @Override
    public State<?> createState() {
        return new TurnedState();
    }

    static final class TurnedState extends State<TurnedCube> {

        /// How far the cube is turned, in radians.
        private double turn = 0.6;

        /// The canvas's revision: a new one has it drawn again.
        private long revision;

        private final Cube cube = Cube.turnedBy(() -> turn);

        @Override
        public Widget build(BuildContext context) {
            var canvas = new Canvas3d(cube)
                    .depth(Canvas3d.Depth.D16)
                    .revision(revision)
                    .withAttributes(Attributes.NONE.id("gpu-turned"));
            var slider = new Slider(
                    0,
                    2 * Math.PI,
                    turn,
                    0.01,
                    value -> setState(() -> {
                        turn = value;
                        revision++;
                    }));
            return new ShowcaseCard(
                            "gpu-turned-card",
                            "On demand",
                            "Without continuous, a canvas3d is drawn once, then again when its size or its revision"
                                    + " changes. Move the slider: each move is a new revision, and between moves"
                                    + " nothing is rendered.",
                            DocLink.to("components/gpu", "canvas3d"))
                    .of(canvas, slider.withAttributes(Attributes.NONE.id("gpu-turn")));
        }
    }
}
