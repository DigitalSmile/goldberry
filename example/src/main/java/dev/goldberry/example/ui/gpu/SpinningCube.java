package dev.goldberry.example.ui.gpu;

import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.gpu.Cube;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.gpu.view.Canvas3d;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.chip.Chip;
import dev.goldberry.widgets.core.Stack;

/// A cube spinning in a `continuous` canvas, with a chip painted over it: UI over
/// a 3D view, which is what placing a GPU layer in paint order is for.
///
/// The renderer is on the state, because a canvas keeps its renderer while it
/// is mounted and makes a new layer when it is handed another one.
///
/// Read more: [`canvas3d`](https://goldberry.dev/docs/components/gpu.html#canvas3d).
record SpinningCube() implements Widget.Stateful {

    @Override
    public State<?> createState() {
        return new SpinningState();
    }

    static final class SpinningState extends State<SpinningCube> {

        private final Cube cube = Cube.spinning();

        @Override
        public Widget build(BuildContext context) {
            var canvas = new Canvas3d(cube)
                    .continuous(true)
                    .depth(Canvas3d.Depth.D16)
                    .withAttributes(Attributes.NONE.id("gpu-spinning"));
            var over = new Chip("UI over the 3D view").withAttributes(Attributes.NONE.classes("gpu-over"));
            return new ShowcaseCard(
                            "gpu-spinning-card",
                            "Every frame",
                            "A continuous canvas3d is drawn on every frame and keeps the window drawing while it is"
                                    + " shown. The cube is the showcase's own renderer and shaders; the chip is"
                                    + " painted after it, so it is over it.",
                            DocLink.to("components/gpu", "canvas3d"))
                    .of(new Stack(List.of(canvas, over), Attributes.NONE.id("gpu-spinning-stage")));
        }
    }
}
