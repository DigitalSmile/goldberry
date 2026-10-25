package dev.goldberry.media.view.gpu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.goldberry.assets.BundledFont;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.gpu.GpuTexture;
import dev.goldberry.gpu.TextureFormat;
import dev.goldberry.gpu.TextureSpec;
import dev.goldberry.gpu.composite.CompositeHarness;
import dev.goldberry.image.Image;
import dev.goldberry.media.HardwareDecoding;
import dev.goldberry.media.MediaNativesRequirement;
import dev.goldberry.media.MediaPlayer;
import dev.goldberry.media.PlaybackState;
import dev.goldberry.media.audio.AudioFormat;
import dev.goldberry.media.audio.VirtualSink;
import dev.goldberry.media.gpu.PictureRenderer;
import dev.goldberry.media.io.MediaIO;
import dev.goldberry.media.io.MediaIOProvider;
import dev.goldberry.media.io.MemoryIO;
import dev.goldberry.media.io.Source;
import dev.goldberry.media.picture.Picture;
import dev.goldberry.media.picture.PictureForm;
import dev.goldberry.media.picture.VideoPicture;
import dev.goldberry.media.picture.VideoPlanes;
import dev.goldberry.media.picture.YuvDiagnosis;
import dev.goldberry.media.picture.YuvReference;
import dev.goldberry.media.picture.YuvSource;
import dev.goldberry.media.view.MediaStyles;
import dev.goldberry.media.view.VideoView;
import dev.goldberry.natives.sdl.gpu.GpuTestLauncher;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.paint.BoxPainter;
import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.window.GpuSurface;
import dev.goldberry.text.font.Font;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.WidgetRenderer;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.Density;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.image.Fit;

/// `video-view` on a real device: a
/// decoded picture shown through `:gpu`'s video layer, composited and read
/// back, against the CPU's goldens; the player asked for planes once the layer
/// is shown, and for converted pictures again where a frame has no GPU.
///
/// The view is 160×90, the clip's own size, so a picture is drawn 1:1: BGRA
/// comes through exactly, and planes differ from libyuv's conversion only by
/// rounding. Both interpolate chroma bilinearly from centred samples, 9:3:3:1,
/// so the two present paths draw the same picture. A picture off by more is
/// put down to the misreading that would have drawn it ([YuvDiagnosis]).
@Tag(GpuTestLauncher.TAG)
@DisplayName("video-view on the GPU")
class VideoOnGpuTest {

    private static final long AT_400 = 400_000_000L;
    private static final Path GOLDEN =
            Path.of("src", "test", "resources", "golden", "video", "clip-vp9-webm-400ms.png");

    /// The most a channel of the planes' picture may differ from the Java
    /// reference of the same arithmetic ([YuvReference]): the rounding of a
    /// linear sampler's weights and of the shader's floats.
    static final int REFERENCE_TOLERANCE = 2;

    /// The most a channel of the planes' picture may differ from libyuv's, CPU
    /// present's: the same arithmetic in floats on the GPU and in fixed point
    /// in libyuv, each within rounding of the reference.
    static final int CPU_TOLERANCE = 2;

    /// The same for a ten-bit picture: libyuv's ten-bit path truncates its last
    /// shift where the eight-bit one rounds, a level more.
    static final int TEN_BIT_CPU_TOLERANCE = 3;

    record Memory(byte[] data) implements MediaIOProvider {
        @Override
        public Set<String> schemes() {
            return Set.of("mem");
        }

        @Override
        public MediaIO open(Source source) {
            return new MemoryIO(data);
        }
    }

    private static CompositeHarness harness;
    private static Font font;
    private MediaPlayer player;

    @BeforeAll
    static void open() {
        harness = CompositeHarness.open();
        font = Font.bundled(BundledFont.UI, 13);
    }

    @AfterAll
    static void closeAll() {
        if (font != null) {
            font.close();
        }
        if (harness != null) {
            harness.close();
        }
    }

    @AfterEach
    void closePlayer() {
        if (player != null) {
            player.close();
        }
    }

    /// A video-view of `player` at 160×90 in no padding, mounted for as long as
    /// the test needs it, as a window's tree is: `Offscreen.render` would
    /// unmount it, closing the layer, before a composited picture had drawn it.
    private static final class Mounted implements AutoCloseable {
        final ElementTree tree;
        final WidgetRenderer renderer;

        Mounted(MediaPlayer player) {
            var sheets = new ArrayList<Stylesheet>(Controls.stylesheets(Theme.NORD_DARK, Density.REGULAR));
            sheets.add(MediaStyles.stylesheet());
            sheets.add(Stylesheet.parse(
                    CascadeLayer.APPLICATION, "video-view { width: 160px; height: 90px; flex-grow: 0; }"));
            tree = new ElementTree(new Column(List.of(new VideoView(player, Fit.FILL)), Attributes.NONE));
            renderer = new WidgetRenderer(sheets, font);
        }

        /// The view painted over `surface`, or on the CPU alone for null.
        Image picture(GpuSurface surface) {
            var offscreen = Offscreen.of(160, 90);
            if (surface != null) {
                offscreen = offscreen.gpu(surface);
            }
            return offscreen.paint((frame, logical) -> BoxPainter.paint(frame, renderer.render(tree)));
        }

        @Override
        public void close() {
            tree.unmount();
        }
    }

    private void openPausedAt400() {
        MediaNativesRequirement.enforce();
        byte[] data;
        try (var in = getClass().getResourceAsStream("/dev/goldberry/media/fixtures/clip-vp9.webm")) {
            data = in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        player = MediaPlayer.builder()
                .hardwareDecoding(HardwareDecoding.OFF)
                .sink(() -> new VirtualSink(AudioFormat.DEFAULT, false))
                .ioProviders(List.of(new Memory(data)))
                .decoderProviders(List.of())
                .build();
        player.open(Source.of(URI.create("mem:///clip-vp9.webm")));
        awaitShown(status -> player.status().state() == PlaybackState.PLAYING);
        player.pause();
        player.seek(Duration.ofMillis(400));
        awaitShown(picture -> picture instanceof VideoPicture && picture.ptsNanos() == AT_400);
    }

    private Picture awaitShown(java.util.function.Predicate<Picture> wanted) {
        var deadline = System.nanoTime() + 10_000_000_000L;
        Picture last = null;
        while (System.nanoTime() < deadline) {
            var shown = player.shownPicture();
            if (shown.isPresent()) {
                last = shown.get();
                if (wanted.test(last)) {
                    return last;
                }
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("the picture never came; the last was " + last + ", " + player.status());
    }

    /// How far apart two channels of a tone-mapped picture may be in light, as
    /// a fraction of white, where they are dark: 0.004, under a nit of the 203
    /// white is. Near black the encoding's `1 / 2.4` power is all but vertical,
    /// so where the gamut conversion leaves a channel a hair above zero on one
    /// path and clips it on the other, a GPU sampler's coarse filter weights at
    /// a chroma edge read as a dozen levels and are a fraction of a nit.
    private static final double HDR_DARK_LIGHT = 0.004;

    /// How far GPU present may land from CPU present on a tone-mapped picture,
    /// where they differ by more than [#HDR_DARK_LIGHT]: twice
    /// [#TEN_BIT_CPU_TOLERANCE], since PQ's curve is up to twice as steep as
    /// SDR's gamma in the mid-tones, so the sampler's rounding at a chroma
    /// edge comes out twice as far. CPU present itself is held to the
    /// reference within a level (`YuvConverterTest`).
    private static final int HDR_TOLERANCE = 2 * TEN_BIT_CPU_TOLERANCE;

    /// [#worst(Image, Image)] counting only the channels that differ by more
    /// than [#HDR_DARK_LIGHT] in light: the levels a viewer can see.
    private static int worstSeen(Image a, Image b) {
        assertEquals(a.size(), b.size());
        var worst = 0;
        for (var y = 0; y < a.height(); y++) {
            for (var x = 0; x < a.width(); x++) {
                for (var shift = 0; shift < 24; shift += 8) {
                    var first = (a.argb(x, y) >>> shift) & 0xFF;
                    var second = (b.argb(x, y) >>> shift) & 0xFF;
                    var light = Math.abs(Math.pow(first / 255.0, 2.4) - Math.pow(second / 255.0, 2.4));
                    if (light > HDR_DARK_LIGHT) {
                        worst = Math.max(worst, Math.abs(first - second));
                    }
                }
            }
        }
        return worst;
    }

    /// The largest difference of any channel between `a` and `b`.
    private static int worst(Image a, Image b) {
        assertEquals(a.size(), b.size());
        var worst = 0;
        for (var y = 0; y < a.height(); y++) {
            for (var x = 0; x < a.width(); x++) {
                for (var shift = 0; shift < 32; shift += 8) {
                    worst = Math.max(
                            worst, Math.abs(((a.argb(x, y) >>> shift) & 0xFF) - ((b.argb(x, y) >>> shift) & 0xFF)));
                }
            }
        }
        return worst;
    }

    @Test
    @DisplayName("shows a converted picture exactly, then asks for planes, which match the CPU's picture")
    void planesMatchTheCpu() {
        openPausedAt400();
        var golden = Image.decode(GOLDEN);
        try (var view = new Mounted(player)) {
            // The picture the player held when the view came: converted, drawn
            // through the layer as BGRA, byte for byte.
            assertEquals(0, worst(golden, harness.readBack(view::picture)), "BGRA through the GPU");
            assertEquals(PictureForm.PLANES, player.pictureForm(), "shown on the GPU, the view asks for planes");

            // Paused, nothing new is decoded until a seek: the same position,
            // now as planes.
            player.seek(Duration.ofMillis(400));
            var planes =
                    (VideoPlanes) awaitShown(picture -> picture instanceof VideoPlanes && picture.ptsNanos() == AT_400);
            var readBack = harness.readBack(view::picture);
            var composited = harness.composited(view::picture);
            assertEquals(1, composited.placed().size());
            CompositeHarness.assertSamePicture("video-view-planes", composited.image(), readBack);

            // Exact to the arithmetic: the GPU against the same conversion and
            // chroma sampling in Java.
            var source = YuvSource.of(planes);
            var reference = YuvReference.argb(source);
            var worstFromReference = 0;
            for (var y = 0; y < 90; y++) {
                for (var x = 0; x < 160; x++) {
                    worstFromReference =
                            Math.max(worstFromReference, worst(readBack.argb(x, y), reference[y * 160 + x]));
                }
            }

            // And CPU present's own picture, within rounding: GPU present
            // matches CPU present pixel by pixel.
            var worstFromCpu = 0;
            var squares = 0.0;
            for (var y = 0; y < 90; y++) {
                for (var x = 0; x < 160; x++) {
                    var a = readBack.argb(x, y);
                    var b = golden.argb(x, y);
                    worstFromCpu = Math.max(worstFromCpu, worst(a, b));
                    for (var shift = 0; shift < 24; shift += 8) {
                        var d = ((a >>> shift) & 0xFF) - ((b >>> shift) & 0xFF);
                        squares += d * d;
                    }
                }
            }
            var psnr = 10 * Math.log10(255.0 * 255.0 / (squares / (160 * 90 * 3)));
            System.out.printf(
                    "video-view on the GPU, clip-vp9 at 400 ms: %d from the Java reference;"
                            + " from CPU present's picture %d at worst, PSNR %.1f dB%n",
                    worstFromReference, worstFromCpu, psnr);
            YuvDiagnosis.assertReadsAsTagged(source, pixels(readBack), REFERENCE_TOLERANCE, "GPU present");
            assertTrue(
                    worstFromCpu <= CPU_TOLERANCE,
                    "the GPU's picture differs from the CPU's by " + worstFromCpu + " levels, at " + psnr + " dB; "
                            + diagnose(source, readBack, golden));
        }
    }

    /// Both pictures against the reference, in words: which present path is
    /// off, and as what.
    private static String diagnose(YuvSource source, Image gpu, Image cpu) {
        return "GPU present: "
                + YuvDiagnosis.mismatch(source, pixels(gpu), REFERENCE_TOLERANCE)
                        .orElse("matches the reference")
                + "; CPU present: "
                + YuvDiagnosis.mismatch(source, pixels(cpu), TEN_BIT_CPU_TOLERANCE)
                        .orElse("matches the reference");
    }

    /// `image`'s pixels as `0xAARRGGBB`, row by row.
    private static int[] pixels(Image image) {
        var pixels = new int[image.width() * image.height()];
        for (var y = 0; y < image.height(); y++) {
            for (var x = 0; x < image.width(); x++) {
                pixels[y * image.width() + x] = image.argb(x, y);
            }
        }
        return pixels;
    }

    /// The largest difference of any colour channel between two pixels.
    private static int worst(int a, int b) {
        var worst = 0;
        for (var shift = 0; shift < 24; shift += 8) {
            worst = Math.max(worst, Math.abs(((a >>> shift) & 0xFF) - ((b >>> shift) & 0xFF)));
        }
        return worst;
    }

    /// The picture of `fixture` at `millis`, as planes: paused on an accurate
    /// seek with a view of planes attached, so it is the same every run.
    private VideoPlanes planesAt(String fixture, int millis) {
        MediaNativesRequirement.enforce();
        byte[] data;
        try (var in = getClass().getResourceAsStream("/dev/goldberry/media/fixtures/" + fixture)) {
            data = in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        player = MediaPlayer.builder()
                .hardwareDecoding(HardwareDecoding.OFF)
                .sink(() -> new VirtualSink(AudioFormat.DEFAULT, false))
                .ioProviders(List.of(new Memory(data)))
                .decoderProviders(List.of())
                .build();
        player.attachView(PictureForm.PLANES);
        player.open(Source.of(URI.create("mem:///" + fixture)));
        awaitShown(picture -> true);
        player.pause();
        player.seek(Duration.ofMillis(millis));
        var at = millis * 1_000_000L;
        return (VideoPlanes) awaitShown(picture -> picture instanceof VideoPlanes && picture.ptsNanos() == at);
    }

    @Test
    @DisplayName(
            "a player offered a renderer's upload memory stages its planes there, and they draw as CPU present does")
    void stagedPlanes() {
        MediaNativesRequirement.enforce();
        byte[] data;
        try (var in = getClass().getResourceAsStream("/dev/goldberry/media/fixtures/clip-vp9-709.webm")) {
            data = in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        player = MediaPlayer.builder()
                .hardwareDecoding(HardwareDecoding.OFF)
                .sink(() -> new VirtualSink(AudioFormat.DEFAULT, false))
                .ioProviders(List.of(new Memory(data)))
                .decoderProviders(List.of())
                .build();
        var golden = Image.decode(Path.of("src", "test", "resources", "golden", "video", "clip-vp9-709-webm-80ms.png"));
        try (var renderer = new PictureRenderer();
                var target = harness.device()
                        .createTexture(TextureSpec.renderTarget(TextureFormat.B8G8R8A8_UNORM, 160, 90))) {
            var attachment = player.attachView(PictureForm.PLANES);
            attachment.setPlaneStore(renderer.planeStore());
            player.open(Source.of(URI.create("mem:///clip-vp9-709.webm")));
            var first = awaitShown(picture -> picture instanceof VideoPlanes);
            // The first picture asks for memory of its shape; a render makes it.
            draw(renderer, first, target);
            assertTrue(((VideoPlanes) first).staged().isEmpty(), "nothing to stage into before a render");
            player.pause();
            player.seek(Duration.ofMillis(80));
            var planes = (VideoPlanes)
                    awaitShown(picture -> picture instanceof VideoPlanes && picture.ptsNanos() == 80_000_000L);
            assertTrue(planes.staged().isPresent(), "staged in the renderer's memory");
            var drawn = draw(renderer, planes, target);
            var difference = worst(golden, drawn);
            System.out.printf("staged planes, clip-vp9-709 at 80 ms: %d from CPU present%n", difference);
            assertTrue(difference <= CPU_TOLERANCE, "staged planes differ from CPU present by " + difference);
            assertEquals(YuvSource.of(planes).code(0, 10, 10), planes.sample(0, 10, 10), "still readable");
        }
    }

    /// `picture` drawn by `renderer` into `target`, read back.
    private static Image draw(PictureRenderer renderer, Picture picture, GpuTexture target) {
        try (var frame = harness.device().beginFrame()) {
            renderer.render(frame, picture, target);
            var readback = frame.readback(target);
            frame.submit();
            var pixels = readback.awaitPixels();
            var source = pixels.pixels();
            var copy = ByteBuffer.allocateDirect(source.remaining()).order(source.order());
            copy.put(0, source, source.position(), source.remaining());
            return Image.of(new PixelBuffer(pixels.size(), pixels.format(), pixels.stride(), copy));
        }
    }

    @ParameterizedTest(name = "{0} at {1} ms")
    @CsvSource({
        "clip-vp8.webm, 0",
        "clip-vp8.webm, 400",
        "clip-vp9.webm, 0",
        "clip-vp9.webm, 960",
        "clip-av1.mkv, 0",
        "clip-av1.mkv, 400",
        "clip-vp9-10bit.webm, 80",
        "clip-vp9-709.webm, 80",
        "clip-vp9-2020-10bit.webm, 80",
        "clip-vp9-full.webm, 80",
        "clip-av1-pq.mkv, 80",
        "clip-vp9-hlg.webm, 80",
    })
    @DisplayName("every fixture's planes on the GPU, composited and read back, match CPU present's golden")
    void parity(String fixture, int millis) {
        var planes = planesAt(fixture, millis);
        var golden = Image.decode(Path.of(
                "src", "test", "resources", "golden", "video", fixture.replace('.', '-') + "-" + millis + "ms.png"));
        try (var layer = new dev.goldberry.gpu.video.VideoLayer()) {
            layer.show(Pictures.image(planes));
            Function<GpuSurface, Image> picture = surface -> Offscreen.of(160, 90)
                    .gpu(surface)
                    .paint((frame, size) -> assertTrue(frame.gpuLayer(layer, 0, 0, size.width(), size.height())));
            var readBack = harness.readBack(picture);
            CompositeHarness.assertSamePicture(
                    fixture + "-" + millis, harness.composited(picture).image(), readBack);
            var difference = worst(golden, readBack);
            System.out.printf(
                    "GPU present, %s (%s %s%s) at %d ms: %d from CPU present%n",
                    fixture, planes.format(), planes.matrix(), planes.fullRange() ? " full" : "", millis, difference);
            var tolerance = planes.format().bytesPerSample() == 2 ? TEN_BIT_CPU_TOLERANCE : CPU_TOLERANCE;
            if (!planes.colour().isPlainSdr()) {
                // A tone-mapped picture is compared in light where it is dark:
                // see HDR_DARK_LIGHT and HDR_TOLERANCE.
                difference = worstSeen(golden, readBack);
                tolerance = HDR_TOLERANCE;
            }
            assertTrue(
                    difference <= tolerance,
                    fixture + " at " + millis + " ms differs from CPU present by " + difference + " levels; "
                            + diagnose(YuvSource.of(planes), readBack, golden));
        }
    }

    @Test
    @DisplayName("a sticker's picture with alpha is drawn on the CPU over the background, where a frame shows layers")
    void stickerStaysOnTheCpu() {
        MediaNativesRequirement.enforce();
        byte[] data;
        try (var in = getClass().getResourceAsStream("/dev/goldberry/media/fixtures/sticker-vp9-alpha.webm")) {
            data = in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        player = MediaPlayer.builder()
                .hardwareDecoding(HardwareDecoding.OFF)
                .sink(() -> new VirtualSink(AudioFormat.DEFAULT, false))
                .ioProviders(List.of(new Memory(data)))
                .decoderProviders(List.of())
                .build();
        player.open(Source.of(URI.create("mem:///sticker-vp9-alpha.webm")));
        awaitShown(picture -> picture instanceof VideoPicture);
        player.pause();
        var sheets = new ArrayList<Stylesheet>(Controls.stylesheets(Theme.NORD_DARK, Density.REGULAR));
        sheets.add(MediaStyles.stylesheet());
        sheets.add(Stylesheet.parse(
                CascadeLayer.APPLICATION,
                "video-view { width: 64px; height: 64px; flex-grow: 0; background-color: transparent; }"));
        var tree = new ElementTree(new Column(List.of(new VideoView(player, Fit.FILL)), Attributes.NONE));
        var renderer = new WidgetRenderer(sheets, font);
        var blue = 0xFF0000FF;
        Function<GpuSurface, Image> picture = surface -> Offscreen.of(64, 64)
                .background(blue)
                .gpu(surface)
                .paint((frame, logical) -> BoxPainter.paint(frame, renderer.render(tree)));
        try {
            var composited = harness.composited(picture);
            assertEquals(List.of(), composited.placed(), "no layer: the video layer would replace the background");
            var drawn = harness.readBack(picture);
            // Row 35 is transparent in every picture of the clip, and from row 40
            // down it is red at half alpha.
            assertEquals(blue, drawn.argb(10, 35), "the background through the transparent");
            var blended = drawn.argb(10, 50);
            assertEquals(126, (blended >>> 16) & 0xFF, 3, "half red");
            assertEquals(127, blended & 0xFF, 3, "over half blue");
            assertEquals(PictureForm.CONVERTED, player.pictureForm(), "the view kept asking for converted pictures");
        } finally {
            tree.unmount();
        }
    }

    @Test
    @DisplayName("where a frame has no GPU, draws on the CPU and asks for converted pictures again")
    void fallsBackToTheCpu() {
        openPausedAt400();
        var golden = Image.decode(GOLDEN);
        try (var view = new Mounted(player)) {
            harness.readBack(view::picture);
            player.seek(Duration.ofMillis(400));
            awaitShown(picture -> picture instanceof VideoPlanes && picture.ptsNanos() == AT_400);

            // A frame with no GPU surface: nothing to draw planes with, so the
            // view asks for converted pictures, which a seek brings back.
            view.picture(null);
            assertEquals(PictureForm.CONVERTED, player.pictureForm());
            awaitShown(picture -> picture instanceof VideoPicture && picture.ptsNanos() == AT_400);
            assertEquals(0, worst(golden, view.picture(null)), "the CPU's own picture");
        }
        assertEquals(PictureForm.CONVERTED, player.pictureForm(), "unmounted, the view let go");
    }
}
