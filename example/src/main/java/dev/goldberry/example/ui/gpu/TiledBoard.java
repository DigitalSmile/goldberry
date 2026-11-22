package dev.goldberry.example.ui.gpu;

import java.time.Duration;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.goldberry.Host;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.gpu.BoardTiles;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.gpu.view.Canvas3d;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.text.Text;

/// A board of tiles the toolkit's painter draws, kept on the GPU as raster
/// textures and drawn by a `canvas3d`: a move repaints two tiles, and only
/// they go up. The line beside the button says how the tiles are kept, and
/// how many went up the last time the board was drawn.
///
/// Read more: [The renderer](https://goldberry.dev/docs/components/gpu.html#the-renderer).
record TiledBoard() implements Widget.Stateful {

    @Override
    public State<?> createState() {
        return new BoardState();
    }

    static final class BoardState extends State<TiledBoard> {

        /// Where the piece is on its walk: the canvas's revision too.
        private int step;

        /// What the last render said, or null before the first.
        private BoardTiles.@Nullable Report report;

        private @Nullable Host host;

        private final BoardTiles board = new BoardTiles(() -> step, this::reported);

        /// The renderer's report, said from inside a paint: the tree is rebuilt
        /// on the next turn of the loop, and only when it says something new.
        private void reported(BoardTiles.Report said) {
            var current = host;
            if (current == null || said.equals(report)) {
                return;
            }
            current.after(Duration.ZERO, () -> {
                if (isMounted()) {
                    setState(() -> report = said);
                }
            });
        }

        @Override
        public Widget build(BuildContext context) {
            host = context.host().orElse(null);
            var canvas = new Canvas3d(board).revision(step).withAttributes(Attributes.NONE.id("gpu-board"));
            var move = new Button("Move the piece", () -> setState(() -> step++))
                    .withAttributes(Attributes.NONE.id("gpu-board-move"));
            var said = new Text(describe(report)).withAttributes(Attributes.NONE.id("gpu-board-path"));
            return new ShowcaseCard(
                            "gpu-board-card",
                            "Tiles painted on the CPU",
                            "Each square is a raster the toolkit's painter draws, kept on the GPU as a texture"
                                    + " and copied into the canvas3d. A move repaints the two squares the piece"
                                    + " leaves and enters, and only those go up: painted where the GPU copies"
                                    + " from on a device where that pays, and staged elsewhere.",
                            DocLink.to("components/gpu", "the-renderer"))
                    .of(canvas, new Row(List.of(move, said), Attributes.NONE.classes("gpu-board-controls")));
        }

        /// The line for `report`: how the tiles are kept, and what went up.
        static String describe(BoardTiles.@Nullable Report report) {
            if (report == null) {
                return "Not drawn yet";
            }
            var how = report.inPlace() ? "Painted in place" : "Staged";
            return how + ": " + report.uploaded() + " of " + report.tiles() + " tiles went up";
        }
    }
}
