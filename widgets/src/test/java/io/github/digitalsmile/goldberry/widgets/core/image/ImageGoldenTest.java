package io.github.digitalsmile.goldberry.widgets.core.image;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.image.Image;
import io.github.digitalsmile.goldberry.paint.BoxPainter;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.core.Row;

/// What an image looks like (§14, [ADR-0050], [ADR-0358]).
///
/// The picture is a 120×60 banner of four coloured quarters with a white
/// border, so a crop, a letterbox and a stretch are each visible by which
/// colours and how much border survive.
///
/// `./gradlew :widgets:test -Dgoldberry.golden.update=true` rewrites them.
class ImageGoldenTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static Image banner() {
        var width = 120;
        var height = 60;
        var argb = new int[width * height];
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                var border = x < 3 || y < 3 || x >= width - 3 || y >= height - 3;
                var quarter = (x < width / 2 ? 0 : 1) + (y < height / 2 ? 0 : 2);
                argb[y * width + x] = border
                        ? 0xFFECEFF4
                        : switch (quarter) {
                            case 0 -> 0xFFBF616A;
                            case 1 -> 0xFFA3BE8C;
                            case 2 -> 0xFF5E81AC;
                            default -> 0xFFEBCB8B;
                        };
            }
        }
        return Image.ofArgb(width, height, argb);
    }

    private static Attributes classes(String... names) {
        return new Attributes(null, Set.of(names), null);
    }

    private static Widget page() {
        var source = ImageSource.of(banner());
        var fits = new Row(
                List.of(
                        new ImageView(source, "contain").fit(Fit.CONTAIN).withAttributes(classes("square")),
                        new ImageView(source, "cover").fit(Fit.COVER).withAttributes(classes("square")),
                        new ImageView(source, "fill").fit(Fit.FILL).withAttributes(classes("square")),
                        new ImageView(source, "none").fit(Fit.NONE).withAttributes(classes("small"))),
                new Attributes("fits", Set.of(), "fits"));
        ImageLoader never = _ -> new CompletableFuture<>();
        ImageLoader broken = _ -> CompletableFuture.failedFuture(new IllegalStateException("gone"));
        var states = new Row(
                List.of(
                        new ImageView(source, "natural"),
                        new ImageView(source, "capped").withAttributes(classes("capped")),
                        new ImageView(source, "loading").loader(never).withAttributes(classes("square")),
                        new ImageView(source, "Quarterly chart").loader(broken).withAttributes(classes("wide"))),
                new Attributes("states", Set.of(), "states"));
        return new Column(List.of(fits, states), new Attributes("page", Set.of(), "page"));
    }

    private void paint(String name, Theme theme) {
        var renderer = new WidgetRenderer(
                List.of(Controls.baseStylesheet(), theme.load(), Stylesheet.parse(CascadeLayer.APPLICATION, """
                                #page { gap: 16px; padding: 16px; background: var(--gb-bg) }
                                #fits, #states { gap: 16px; align-items: flex-start }
                                image.square { width: 96px; height: 96px; outline: 1px solid var(--gb-border) }
                                image.small { width: 80px; height: 40px; outline: 1px solid var(--gb-border) }
                                image.capped { max-width: 60px }
                                image.wide { width: 140px; height: 96px }
                                """)),
                TestFont.get());
        GoldenImage.assertMatches(
                name, 500, 272, 1.0f, frame -> BoxPainter.paint(frame, renderer.render(new ElementTree(page()))));
    }

    @Test
    @DisplayName("four fits in one box shape, then natural, capped, loading and failed")
    void dark() {
        paint("image-dark", Theme.NORD_DARK);
    }

    @Test
    @DisplayName("and the same on light")
    void light() {
        paint("image-light", Theme.NORD_LIGHT);
    }
}
