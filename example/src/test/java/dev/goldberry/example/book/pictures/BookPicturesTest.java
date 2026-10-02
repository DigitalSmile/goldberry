package dev.goldberry.example.book.pictures;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;

import dev.goldberry.RendererRequirement;
import dev.goldberry.golden.GoldenImage;

/// Every picture of a widget in the guide is what the code draws today.
///
/// The pictures under `book/src/images` are renders of the guide's own `kdl`
/// samples, two per widget, and this holds each of them to the renderer the
/// way a golden is held: a stylesheet that changed a button's corner changes
/// the button's picture, and the picture in the guide follows in the same
/// change. The guide's pictures are taken from its own samples, in both themes.
///
/// `./gradlew :example:test --tests '*BookPicturesTest*' -Dgoldberry.golden.update=true`
/// retakes them all. That is the whole workflow for a new widget: write its
/// chapter, run this once, commit the two files it wrote.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BookPicturesTest {

    /// The headings the guide does not picture, and why: the five whose sample
    /// is `kdl,ignore` because it needs something bound that a preview cannot
    /// supply, and the three that inflate but draw nothing on their own because
    /// a parent draws them. A heading not in this map that has no plain `kdl`
    /// sample fails below, so a new `kdl,ignore` is a decision made here rather
    /// than a picture quietly lost — and a name here that the catalogue no
    /// longer has fails too.
    private static final Map<String, String> NOT_PICTURED = Map.ofEntries(
            Map.entry("canvas3d", "draws through a renderer= the application binds; the GPU screen pictures it"),
            Map.entry("media-player", "plays through a player= the application binds; its own golden pictures it"),
            Map.entry("video-view", "plays through a player= the application binds"),
            Map.entry("audio-player", "plays through a player= the application binds"),
            Map.entry("media-controls", "drive a player= the application binds"),
            Map.entry("canvas", "paints through a painter the application writes; the Canvas screen pictures it"),
            Map.entry("action", "drawn by its dialog; the dialog's picture shows three"),
            Map.entry("marker", "drawn by its timeline entry; the timeline's picture shows one"));

    private final Path source = Path.of("..", "book", "src").toAbsolutePath().normalize();
    private final Path images = source.resolve("images");
    private List<PicturePlan.Entry> plan;
    private PictureCamera camera;

    @BeforeAll
    void setUp() {
        RendererRequirement.enforce();
        plan = PicturePlan.read(source);
        camera = new PictureCamera();
    }

    @AfterAll
    void tearDown() {
        if (camera != null) {
            camera.close();
        }
    }

    @Test
    @DisplayName("the plan found the catalogue, so a chapter that lost its headings is noticed")
    void thereIsAPlan() {
        var pictured =
                plan.stream().filter(PicturePlan.Pictured.class::isInstance).count();
        assertTrue(
                pictured > 60,
                () -> "only " + pictured + " widget headings with a kdl sample; the catalogue used to have far more");
    }

    @Test
    @DisplayName("a heading without a picture is one of the known few, each with its reason")
    void everySkipIsKnown() {
        var unexplained = plan.stream()
                .filter(PicturePlan.Skipped.class::isInstance)
                .map(PicturePlan.Skipped.class::cast)
                .filter(skipped -> !NOT_PICTURED.containsKey(skipped.name()))
                .map(skipped -> skipped.name() + " in " + skipped.chapter() + ": " + skipped.reason())
                .toList();
        var names = plan.stream().map(PicturePlan.Entry::name).collect(Collectors.toSet());
        var stale = NOT_PICTURED.keySet().stream()
                .filter(name -> !names.contains(name))
                .sorted()
                .toList();
        assertAll(
                () -> assertTrue(unexplained.isEmpty(), () -> "widgets the guide no longer pictures: " + unexplained),
                () -> assertTrue(stale.isEmpty(), () -> "names in NOT_PICTURED the catalogue no longer has: " + stale));
    }

    /// The pictures to take: every heading with a sample, less the ones a
    /// parent draws.
    private Stream<WidgetPicture> pictures() {
        return plan.stream()
                .filter(PicturePlan.Pictured.class::isInstance)
                .map(PicturePlan.Pictured.class::cast)
                .map(PicturePlan.Pictured::picture)
                .filter(picture -> !NOT_PICTURED.containsKey(picture.name()));
    }

    @TestFactory
    @DisplayName("a widget's picture is what the code draws")
    Stream<DynamicTest> everyPictureIsCurrent() {
        return pictures()
                .flatMap(picture -> Stream.of(Shade.values())
                        .map(shade -> DynamicTest.dynamicTest(picture.file(shade), () -> {
                            var viewport = PictureCamera.viewport();
                            GoldenImage.assertMatchesAtOneScale(
                                    images.resolve(picture.file(shade)),
                                    viewport.width(),
                                    viewport.height(),
                                    PictureCamera.SCALE,
                                    camera.scene(picture, shade));
                        })));
    }
}
