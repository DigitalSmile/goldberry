package dev.goldberry.widgets.core.image;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.golden.GoldenImage;
import dev.goldberry.image.Image;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.paint.BoxPainter;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.WidgetRenderer;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.controls.TestFont;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.Row;

/// Pictures a stylesheet names, held as golden images: `background-image`
/// layers tiled, covering and drawn once under a radius, and a nine-slice
/// `border-image` on three panel sizes at 100% and 200%.
///
/// The pictures are drawn here and written to a temporary directory, so the
/// stylesheet reads them as files the way an application's would, through the
/// shared image cache.
///
/// `./gradlew :widgets:test -Dgoldberry.golden.update=true` rewrites them.
class CssImageGoldenTest {

    @TempDir
    static Path pictures;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static Attributes box(String id, String... classes) {
        return new Attributes(id, Set.of(classes), id);
    }

    private static String write(String name, Image image) throws IOException {
        var file = pictures.resolve(name);
        Files.write(file, image.encodePng());
        return file.toString();
    }

    /// A 16-pixel weave of two browns with a lighter thread down its left edge.
    private static Image weave() {
        var argb = new int[16 * 16];
        for (var y = 0; y < 16; y++) {
            for (var x = 0; x < 16; x++) {
                argb[y * 16 + x] = x == 0 ? 0xFFA0703C : ((x / 4 + y / 4) % 2 == 0 ? 0xFF8B5A2B : 0xFF6B4423);
            }
        }
        return Image.ofArgb(16, 16, argb);
    }

    /// A 40 × 20 banner of four coloured quarters.
    private static Image banner() {
        var argb = new int[40 * 20];
        for (var y = 0; y < 20; y++) {
            for (var x = 0; x < 40; x++) {
                argb[y * 40 + x] = switch ((x < 20 ? 0 : 1) + (y < 10 ? 0 : 2)) {
                    case 0 -> 0xFFBF616A;
                    case 1 -> 0xFFA3BE8C;
                    case 2 -> 0xFF5E81AC;
                    default -> 0xFFEBCB8B;
                };
            }
        }
        return Image.ofArgb(40, 20, argb);
    }

    /// A 64-pixel crest: a yellow disc on an opaque blue square, so a corner cut
    /// by a radius shows.
    private static Image crest() {
        var argb = new int[64 * 64];
        for (var y = 0; y < 64; y++) {
            for (var x = 0; x < 64; x++) {
                var dx = x - 31.5;
                var dy = y - 31.5;
                argb[y * 64 + x] = dx * dx + dy * dy < 24 * 24 ? 0xFFEBCB8B : 0xFF5E81AC;
            }
        }
        return Image.ofArgb(64, 64, argb);
    }

    private static void paint(String name, Widget page, String css, int width, int height, float scale) {
        var renderer = new WidgetRenderer(
                List.of(
                        Controls.baseStylesheet(),
                        Theme.NORD_DARK.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, css)),
                TestFont.get());
        GoldenImage.assertMatches(
                name, width, height, scale, frame -> BoxPainter.paint(frame, renderer.render(new ElementTree(page))));
    }

    @Test
    @DisplayName("a tiled weave, a cover, one crest under a radius and a weave tiled across under one")
    void backgrounds() throws IOException {
        var weave = write("weave.png", weave());
        var banner = write("banner.png", banner());
        var crest = write("crest.png", crest());
        var page = new Row(
                List.of(
                        new Column(List.of(), box("tiled", "tile")),
                        new Column(List.of(), box("cover", "tile")),
                        new Column(List.of(), box("crest", "tile")),
                        new Column(List.of(), box("across", "tile"))),
                box("page"));
        var css = """
                #page { gap: 16px; padding: 16px; background: var(--gb-bg); align-items: flex-start }
                .tile { width: 120px; height: 80px; background-color: #3b4252 }
                #tiled { background-image: url("%1$s") }
                #cover { background-image: url("%2$s"); background-size: cover }
                #crest { background: url("%3$s") no-repeat, #4c566a; border-radius: 24px }
                #across {
                    background-image: url("%1$s"), linear-gradient(#88c0d0, #5e81ac);
                    background-repeat: repeat-x;
                    background-size: 24px;
                    background-position: 0 28px;
                    border-radius: 16px;
                }
                """.formatted(weave, banner, crest);
        paint("css-background-images", page, css, 560, 112, 1.0f);
    }

    /// The panel sprite: 48-pixel corners each with a diamond of its own colour,
    /// edges of slate with a notched gold rule 8 pixels in, and a dark middle.
    private static int panelPixel(int x, int y, int size) {
        var inX = Math.min(x, size - 1 - x);
        var inY = Math.min(y, size - 1 - y);
        if (inX < 48 && inY < 48) {
            var cx = x < 48 ? 24 : size - 24;
            var cy = y < 48 ? 24 : size - 24;
            if (Math.abs(x + 0.5 - cx) + Math.abs(y + 0.5 - cy) < 16) {
                return switch ((x < 48 ? 0 : 1) + (y < 48 ? 0 : 2)) {
                    case 0 -> 0xFFBF616A;
                    case 1 -> 0xFFA3BE8C;
                    case 2 -> 0xFF5E81AC;
                    default -> 0xFFD08770;
                };
            }
            return 0xFF4C566A;
        }
        if (inX < 48 || inY < 48) {
            // Notched every 16 pixels along its length, so a stretched edge and
            // a tiled one are told apart.
            var in = Math.min(inX, inY);
            var along = inY < 48 ? x : y;
            return in >= 8 && in < 12 && along % 16 >= 4 ? 0xFFEBCB8B : 0xFF4C566A;
        }
        return 0xFF2E3440;
    }

    private static Image panel() {
        var argb = new int[144 * 144];
        for (var y = 0; y < 144; y++) {
            for (var x = 0; x < 144; x++) {
                argb[y * 144 + x] = panelPixel(x, y, 144);
            }
        }
        return Image.ofArgb(144, 144, argb);
    }

    /// The same sprite at twice the pixels, with a white thread down the middle
    /// of the gold rule that only the 2x picture is fine enough to carry.
    private static Image panelAt2x() {
        var argb = new int[288 * 288];
        for (var y = 0; y < 288; y++) {
            for (var x = 0; x < 288; x++) {
                var pixel = panelPixel(x / 2, y / 2, 144);
                var inX = Math.min(x, 287 - x);
                var inY = Math.min(y, 287 - y);
                var inCorner = inX < 96 && inY < 96;
                argb[y * 288 + x] = !inCorner && Math.min(inX, inY) == 20 ? 0xFFECEFF4 : pixel;
            }
        }
        return Image.ofArgb(288, 288, argb);
    }

    private static Widget panels() {
        return new Column(
                List.of(
                        new Row(
                                List.of(
                                        new Column(List.of(), box("small", "panel")),
                                        new Column(List.of(), box("medium", "panel")),
                                        new Column(List.of(), box("large", "panel"))),
                                box("sizes", "line")),
                        new Row(
                                List.of(
                                        new Column(List.of(), box("round", "panel")),
                                        new Column(List.of(), box("repeat", "panel"))),
                                box("tiles", "line"))),
                box("page"));
    }

    private static String panelCss(String sprite) {
        return """
                #page { gap: 16px; padding: 16px; background: var(--gb-bg) }
                .line { gap: 16px; align-items: flex-start }
                .panel { border-image: url("%s") 48 fill / 48px stretch }
                #small { width: 96px; height: 96px }
                #medium { width: 160px; height: 120px }
                #large { width: 240px; height: 150px }
                #round { width: 230px; height: 110px; border-image-repeat: round }
                #repeat { width: 230px; height: 110px; border-image-repeat: repeat }
                """.formatted(sprite);
    }

    @Test
    @DisplayName("a 48px-corner panel on three sizes, then rounded and repeated, at 100%")
    void nineSlice() throws IOException {
        // No @2x beside this one, so every scale the sweep draws it at uses the
        // same pixels and the picture is the same at each.
        var sprite = write("panel-flat.png", panel());
        paint("css-border-image", panels(), panelCss(sprite), 560, 308, 1.0f);
    }

    /// At 200% the `@2x` sprite is drawn, which is a different picture from the
    /// 1x one by design — the white thread — so this golden is held at its own
    /// scale only.
    @Test
    @DisplayName("the same at 200%, drawn from the @2x sprite with its corners pixel for pixel")
    void nineSliceAt2x() throws IOException {
        var sprite = write("panel.png", panel());
        write("panel@2x.png", panelAt2x());
        var renderer = new WidgetRenderer(
                List.of(
                        Controls.baseStylesheet(),
                        Theme.NORD_DARK.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, panelCss(sprite))),
                TestFont.get());
        GoldenImage.assertMatchesAtOneScale(
                "css-border-image-2x",
                1120,
                616,
                2.0f,
                (size, scale) -> Offscreen.of(size)
                        .scale(scale)
                        .paint((frame, logical) ->
                                BoxPainter.paint(frame, renderer.render(new ElementTree(panels())))));
    }
}
