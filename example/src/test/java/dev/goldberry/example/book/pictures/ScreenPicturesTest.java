package dev.goldberry.example.book.pictures;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;

import dev.goldberry.RendererRequirement;
import dev.goldberry.example.ShowcaseScene;
import dev.goldberry.golden.GoldenImage;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.render.model.PhysicalSize;
import dev.goldberry.text.font.Fonts;

/// The showcase screens the guide shows, in both shades at twice the detail.
///
/// The same screens `GalleryGoldenTest` holds goldens of, through the same
/// [ShowcaseScene], at the gallery's sizes; what differs is the scale, the two
/// shades, and the font book rather than the one-font renderer, because a
/// picture in the guide is of what the application draws and the application
/// opens a book. Held to the renderer the way the widget pictures are
/// (`-Dgoldberry.golden.update=true` retakes them).
///
/// Not every screen: the ones whose subject a picture can show. The Web screen
/// is a picture of a page that is not there, the GPU screen needs a
/// device, the Canvas screen is pinned at one scale, and the media screens are
/// stills of a player at rest, which the chapter's own golden shows better.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScreenPicturesTest {

    /// A screen the guide pictures, and the logical size it is drawn at — the
    /// gallery's, so the two agree on where the fold is.
    record ScreenPicture(String screen, int width, int height) {

        String file(Shade shade) {
            return "screen-" + screen + "-" + shade.suffix() + ".webp";
        }
    }

    static final List<ScreenPicture> SCREENS = List.of(
            new ScreenPicture("basic", 1200, 1720),
            new ScreenPicture("panels", 1200, 900),
            new ScreenPicture("markdown", 1200, 1000),
            new ScreenPicture("html", 1200, 1000),
            new ScreenPicture("overlays", 1200, 900),
            new ScreenPicture("forms", 1200, 1500),
            new ScreenPicture("navigation", 1200, 900),
            new ScreenPicture("collections", 1200, 1040),
            new ScreenPicture("charts", 1200, 900));

    private final Path images =
            Path.of("..", "book", "src", "images").toAbsolutePath().normalize();
    private ShowcaseScene scene;
    private Fonts fonts;

    @BeforeAll
    void setUp() {
        RendererRequirement.enforce();
        scene = new ShowcaseScene();
        fonts = Fonts.bundled();
    }

    @AfterAll
    void tearDown() {
        if (fonts != null) {
            fonts.close();
        }
        if (scene != null) {
            scene.close();
        }
    }

    @TestFactory
    @DisplayName("a screen's picture is what the application draws")
    Stream<DynamicTest> everyScreenIsCurrent() {
        return SCREENS.stream()
                .flatMap(picture -> Stream.of(Shade.values())
                        .map(shade -> DynamicTest.dynamicTest(picture.file(shade), () -> {
                            var physical = new PhysicalSize(
                                    Math.round(picture.width() * PictureCamera.SCALE),
                                    Math.round(picture.height() * PictureCamera.SCALE));
                            GoldenImage.assertMatchesAtOneScale(
                                    images.resolve(picture.file(shade)),
                                    physical.width(),
                                    physical.height(),
                                    PictureCamera.SCALE,
                                    (size, scale) -> Offscreen.of(size)
                                            .scale(scale)
                                            .stylesheets(scene.stylesheets(shade.theme()))
                                            .fonts(fonts)
                                            .render(scene.root(picture.screen())));
                        })));
    }
}
