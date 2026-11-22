package dev.goldberry.example.gpu;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

import org.jspecify.annotations.Nullable;

import dev.goldberry.gpu.GpuDevice;
import dev.goldberry.gpu.GpuFrame;
import dev.goldberry.gpu.Load;
import dev.goldberry.gpu.raster.RasterTexture;
import dev.goldberry.gpu.view.Canvas3dRenderer;
import dev.goldberry.gpu.view.Canvas3dTarget;
import dev.goldberry.paint.Frame;
import dev.goldberry.paint.Path;
import dev.goldberry.paint.stroke.Stroke;
import dev.goldberry.render.model.DisplayScale;
import dev.goldberry.render.model.PhysicalRect;
import dev.goldberry.render.model.PhysicalSize;

/// The GPU screen's board: a `canvas3d` renderer whose picture is tiles the
/// toolkit's painter draws, each a [RasterTexture], copied into place on the
/// GPU with no shader of its own.
///
/// A piece walks round the board's edge, one square for each step its
/// supplier answers. A step repaints the two squares it leaves and enters,
/// and those two tiles alone go up; the rest stay on the GPU as they were.
/// What went up, and how, is told to [Report]'s consumer after each render.
///
/// Read more: [The renderer](https://goldberry.dev/docs/components/gpu.html#the-renderer).
public final class BoardTiles implements Canvas3dRenderer {

    /// Squares across.
    public static final int COLUMNS = 6;

    /// Squares down.
    public static final int ROWS = 4;

    /// The painter's units across a tile, whatever its size in pixels.
    private static final float UNITS = 64f;

    private static final int LIGHT = 0xFFE5E9F0;
    private static final int DARK = 0xFF5E81AC;
    private static final int EDGE = 0x332E3440;
    private static final int PIECE = 0xFFEBCB8B;
    private static final int PIECE_EDGE = 0xFF2E3440;
    private static final float BACKGROUND = 0.180f;

    /// What a render did: how the tiles are kept, and how many of them went up.
    ///
    /// @param inPlace  whether the tiles are painted where the GPU copies from
    /// @param uploaded the tiles this render uploaded
    /// @param tiles    the tiles there are
    public record Report(boolean inPlace, int uploaded, int tiles) {}

    private final IntSupplier step;
    private final Consumer<Report> report;
    private final List<RasterTexture> tiles = new ArrayList<>();
    private @Nullable GpuDevice device;
    private int tileSize;
    private int shown = -1;

    /// A board whose piece is on the square `step` says, reporting each
    /// render to `report`.
    public BoardTiles(IntSupplier step, Consumer<Report> report) {
        this.step = step;
        this.report = report;
    }

    @Override
    public void init(GpuDevice device) {
        this.device = device;
    }

    @Override
    public void render(GpuFrame frame, Canvas3dTarget target) {
        var size = target.size();
        var square = Math.max(1, Math.min(size.width() / COLUMNS, size.height() / ROWS));
        var now = Math.floorMod(step.getAsInt(), route());
        if (tiles.isEmpty() || square != tileSize) {
            made(required(device), square, now);
        } else if (now != shown) {
            var left = squareAt(shown);
            shown = now;
            paint(left);
            paint(squareAt(now));
        }

        var uploaded =
                (int) tiles.stream().filter(tile -> !tile.damage().isEmpty()).count();
        frame.copyPass(copy -> tiles.forEach(tile -> tile.upload(copy)));
        var colour = target.colour();
        frame.renderPass(colour, Load.clear(BACKGROUND, 0.204f, 0.251f, 1), pass -> {});
        var left = (size.width() - COLUMNS * tileSize) / 2;
        var top = (size.height() - ROWS * tileSize) / 2;
        frame.copyPass(copy -> {
            for (var index = 0; index < tiles.size(); index++) {
                var x = left + index % COLUMNS * tileSize;
                var y = top + index / COLUMNS * tileSize;
                var width = Math.min(tileSize, size.width() - x);
                var height = Math.min(tileSize, size.height() - y);
                if (x >= 0 && y >= 0 && width > 0 && height > 0) {
                    copy.copy(tiles.get(index).texture().view(0, 0), 0, 0, colour.view(0, 0), x, y, width, height);
                }
            }
        });
        report.accept(new Report(tiles.getFirst().inPlace(), uploaded, tiles.size()));
    }

    @Override
    public void dispose() {
        closeTiles();
        device = null;
    }

    /// The tiles, made on `on` at `square` pixels and painted whole, the
    /// piece on step `at`.
    private void made(GpuDevice on, int square, int at) {
        closeTiles();
        tileSize = square;
        for (var index = 0; index < COLUMNS * ROWS; index++) {
            tiles.add(RasterTexture.create(on, new PhysicalSize(square, square)));
        }
        shown = at;
        for (var index = 0; index < tiles.size(); index++) {
            paint(index);
        }
    }

    /// Paints the whole of tile `index`: its square, and the piece if it is
    /// there.
    private void paint(int index) {
        var column = index % COLUMNS;
        var row = index / COLUMNS;
        var piece = index == squareAt(shown);
        tiles.get(index)
                .paint(
                        List.of(PhysicalRect.of(0, 0, tileSize, tileSize)),
                        new DisplayScale(tileSize / UNITS),
                        frame -> square(frame, (column + row) % 2 == 0, piece));
    }

    /// One square in the painter's 64 units: light or dark, a hairline edge,
    /// and the piece over it.
    private static void square(Frame frame, boolean light, boolean piece) {
        frame.fill(light ? LIGHT : DARK);
        frame.strokePath(Path.rect(0.5, 0.5, UNITS - 1, UNITS - 1), Stroke.of(1), EDGE);
        if (piece) {
            var disc = Path.circle(UNITS / 2, UNITS / 2, UNITS * 0.3);
            frame.fillPath(disc, PIECE);
            frame.strokePath(disc, Stroke.of(2.5), PIECE_EDGE);
        }
    }

    /// The tile the piece is on at step `at` of its walk round the edge,
    /// clockwise from the top left.
    static int squareAt(int at) {
        var step = Math.floorMod(at, route());
        if (step < COLUMNS) {
            return step;
        }
        step -= COLUMNS - 1;
        if (step < ROWS) {
            return step * COLUMNS + COLUMNS - 1;
        }
        step -= ROWS - 1;
        if (step < COLUMNS) {
            return (ROWS - 1) * COLUMNS + COLUMNS - 1 - step;
        }
        step -= COLUMNS - 1;
        return (ROWS - 1 - step) * COLUMNS;
    }

    /// The squares round the edge.
    static int route() {
        return 2 * (COLUMNS + ROWS) - 4;
    }

    private void closeTiles() {
        for (var tile : tiles) {
            tile.close();
        }
        tiles.clear();
        tileSize = 0;
    }

    private static <T> T required(@Nullable T value) {
        if (value == null) {
            throw new IllegalStateException("drawn before init");
        }
        return value;
    }
}
