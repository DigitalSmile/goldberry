package dev.goldberry.example.ui.input;

import dev.goldberry.paint.Frame;
import dev.goldberry.paint.Path;
import dev.goldberry.paint.stroke.Stroke;
import dev.goldberry.render.model.LogicalSize;

/// What every pad on the input screen draws first: a 24-point grid, so a
/// position can be read off it.
final class InputPads {

    private static final float STEP = 24;

    private InputPads() {}

    /// The grid, over the whole of `size`.
    static void grid(Frame frame, LogicalSize size) {
        var grid = Path.builder();
        for (var x = 0f; x <= size.width(); x += STEP) {
            grid.moveTo(x, 0).lineTo(x, size.height());
        }
        for (var y = 0f; y <= size.height(); y += STEP) {
            grid.moveTo(0, y).lineTo(size.width(), y);
        }
        frame.strokePath(grid.build(), Stroke.of(1), InputColors.MUTED);
    }
}
