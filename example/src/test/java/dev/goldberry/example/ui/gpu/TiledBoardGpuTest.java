package dev.goldberry.example.ui.gpu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.example.ShowcaseStyles;
import dev.goldberry.example.gpu.BoardTiles;
import dev.goldberry.golden.GoldenImage;
import dev.goldberry.golden.Tolerance;
import dev.goldberry.gpu.view.Canvas3d;
import dev.goldberry.natives.sdl.Sdl;
import dev.goldberry.natives.sdl.gpu.GpuDeviceRequirement;
import dev.goldberry.natives.sdl.gpu.GpuTestLauncher;
import dev.goldberry.natives.sdl.gpu.SdlGpuDevice;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.offscreen.Session;
import dev.goldberry.render.backend.headless.HeadlessBackend;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.render.window.GpuSurface;
import dev.goldberry.render.window.WindowSpec;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.text.Text;

/// The board of raster tiles on a real device: the picture its tiles hold, and
/// the card's line, which says a new board went up whole and a move sent two
/// tiles. On the GPU lane, `:example:gpuTest`.
@Tag(GpuTestLauncher.TAG)
@DisplayName("the board of raster tiles, on the GPU")
class TiledBoardGpuTest {

    private static final int TILES = BoardTiles.COLUMNS * BoardTiles.ROWS;

    private static SdlGpuDevice device;
    private static HeadlessBackend backend;
    private static GpuSurface surface;

    @BeforeAll
    static void open() {
        device = GpuDeviceRequirement.enforce();
        backend = new HeadlessBackend();
        var window = backend.createWindow(WindowSpec.of("board", LogicalSize.of(384, 256)));
        surface = window.gpuSurface().orElseThrow(() -> new AssertionError("no GPU surface with :gpu here"));
    }

    @AfterAll
    static void close() {
        if (device == null) {
            return;
        }
        backend.close();
        device.close();
        Sdl.get().quit();
    }

    @Test
    @DisplayName("draws the board its tiles hold, the piece on its square, the same on every driver")
    void board() {
        var sheets = new ArrayList<Stylesheet>(Controls.stylesheets(Theme.NORD_DARK));
        sheets.add(Stylesheet.parse(CascadeLayer.APPLICATION, "canvas3d { width: 100%; height: 100%; }"));
        var reports = new ArrayList<BoardTiles.Report>();
        // Copies and the CPU's painting only: nothing a driver filters.
        GoldenImage.assertMatchesAtOneScale("gpu-board", 384, 256, 1f, Tolerance.GPU, (at, scale) -> {
            var board = new BoardTiles(() -> 3, reports::add);
            return Offscreen.of(at.width(), at.height())
                    .scale(scale.factor())
                    .stylesheets(sheets)
                    .gpu(surface)
                    .render(new Canvas3d(board).withAttributes(Attributes.NONE));
        });
        assertFalse(reports.isEmpty(), "the board was drawn");
        assertEquals(new BoardTiles.Report(reports.getFirst().inPlace(), TILES, TILES), reports.getFirst());
    }

    @Test
    @DisplayName("says a new board went up whole, and a move sent the two squares it changed")
    void aMoveSendsTwoTiles() {
        var sheets = new ArrayList<Stylesheet>(Controls.stylesheets(Theme.NORD_DARK));
        sheets.addAll(ShowcaseStyles.sheets());
        try (var session =
                Offscreen.of(560, 420).stylesheets(sheets).gpu(surface).session(new TiledBoard())) {
            settle(session);
            var first = line(session);
            assertTrue(first.endsWith(": " + TILES + " of " + TILES + " tiles went up"), first);
            session.click("gpu-board-move");
            settle(session);
            var moved = line(session);
            assertTrue(moved.endsWith(": 2 of " + TILES + " tiles went up"), moved);
            assertEquals(first.substring(0, first.indexOf(':')), moved.substring(0, moved.indexOf(':')));
        }
    }

    /// A few frames: the one that draws the board, and the one its report
    /// rebuilds the card in.
    private static void settle(Session session) {
        for (var i = 0; i < 4; i++) {
            session.frame();
        }
    }

    private static String line(Session session) {
        return ((Text) session.byId("gpu-board-path").orElseThrow().widget()).content();
    }
}
