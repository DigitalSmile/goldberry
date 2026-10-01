package dev.goldberry.example.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import dev.goldberry.example.gpu.Cube;
import dev.goldberry.gpu.view.Canvas3d;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.chip.Chip;
import dev.goldberry.widgets.controls.slider.Slider;
import dev.goldberry.widgets.core.Stack;
import dev.goldberry.widgets.overlay.hud.Hud;
import dev.goldberry.widgets.panel.card.Card;
import dev.goldberry.widgets.panel.masonry.Masonry;
import dev.goldberry.widgets.text.Text;

/// The **GPU** screen: `canvas3d`, drawn by the showcase's own renderer and
/// shaders (`docs/gpu-plan.md`, phase 5; ADR-0482).
///
/// Three cards:
///
/// - **Every frame.** A cube spinning in a `continuous` canvas, with a chip
///   painted over it: UI over 3D, which is what composing a GPU layer in paint
///   order is for (ADR-0481).
/// - **On demand.** The same cube in a canvas drawn only when its revision
///   changes, which the slider does. Between moves nothing is rendered and the
///   last picture is shown.
/// - **What it costs.** A `hud` with the present readings, which show what the
///   window's composite takes: the upload, the wait for the display, the
///   submit. On a window presenting on the CPU they read dashes.
///
/// How the window shows the canvases is its launch's choice:
/// `-Pgoldberry.gpu.composite=never` reads them back, and
/// `-Pgoldberry.gpu=off` shows what a canvas shows without a GPU.
public record GpuScreen() implements Widget.Stateful {

    static final String NOTE = "canvas3d is a box the GPU draws into, with an application's renderer: here, a cube"
            + " lit in the showcase's own shaders. Where the window presents through the GPU it is composited under"
            + " the UI; where it cannot, it is rendered and read back. Run with -Pgoldberry.gpu.composite=never to"
            + " see read-back, or -Pgoldberry.gpu=off to see no GPU at all.";

    @Override
    public State<?> createState() {
        return new GpuState();
    }

    static final class GpuState extends State<GpuScreen> {

        private final Cube spinning = Cube.spinning();

        /// How far the on-demand cube is turned, in radians, and the revision
        /// that has its canvas draw it again.
        private double turn = 0.6;

        private long revision;
        private final Cube turned = Cube.turnedBy(() -> turn);

        @Override
        public Widget build(BuildContext context) {
            return new Wall(
                    "gpu", "GPU", NOTE, 2, Masonry.UNSET, List.of(spinningCard(), onDemandCard(), framesCard()));
        }

        private Widget spinningCard() {
            var canvas = new Canvas3d(spinning)
                    .continuous(true)
                    .depth(Canvas3d.Depth.D16)
                    .withAttributes(attributes("gpu-spinning"));
            var over = new Chip("UI over the 3D view").withAttributes(Attributes.NONE.classes("gpu-over"));
            return captioned(
                    "Every frame",
                    "gpu-spinning-card",
                    new Stack(List.of(canvas, over), Attributes.NONE.id("gpu-spinning-stage")),
                    caption("A continuous canvas: drawn on every frame, and keeping the window drawing them while"
                            + " it is shown. The chip is painted after it, so it is over it."));
        }

        private Widget onDemandCard() {
            var canvas = new Canvas3d(turned)
                    .depth(Canvas3d.Depth.D16)
                    .revision(revision)
                    .withAttributes(attributes("gpu-turned"));
            var slider = new Slider(
                    0,
                    2 * Math.PI,
                    turn,
                    0.01,
                    value -> setState(() -> {
                        turn = value;
                        revision++;
                    }));
            return captioned(
                    "On demand",
                    "gpu-turned-card",
                    canvas,
                    slider,
                    caption("Drawn when its revision changes, which the slider does. Between moves nothing is"
                            + " rendered: the compositor shows the last picture again."));
        }

        private Widget framesCard() {
            return captioned(
                    "What it costs",
                    "gpu-frames-card",
                    new Hud(Hud.PRESENT, Attributes.NONE.id("gpu-hud")),
                    caption("The window's frame, and what its composite took: the upload of what changed in the"
                            + " UI, the wait for the display, and the submit, which counts the canvases' own"
                            + " rendering. Dashes where the window presents on the CPU."));
        }

        private static Attributes attributes(String id) {
            return new Attributes(id, Set.of(), id);
        }

        private static Widget caption(String text) {
            return new Text(text, Attributes.NONE.classes("caption"));
        }

        private static Widget captioned(String title, String id, Widget... parts) {
            var children = new ArrayList<Widget>(parts.length + 1);
            children.add(new Text(title, Attributes.NONE.classes("card-title")));
            children.addAll(List.of(parts));
            return new Card(List.copyOf(children), new Attributes(id, Set.of("wall-card"), id));
        }
    }
}
