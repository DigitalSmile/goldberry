package dev.goldberry.gpu.composite;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Function;

import dev.goldberry.gpu.GpuDevice;
import dev.goldberry.gpu.TextureFormat;
import dev.goldberry.gpu.TextureSpec;
import dev.goldberry.image.Image;
import dev.goldberry.natives.sdl.Sdl;
import dev.goldberry.natives.sdl.gpu.GpuDeviceRequirement;
import dev.goldberry.natives.sdl.gpu.SdlGpuDevice;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTextureFormat;
import dev.goldberry.render.GpuPlacement;
import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.composite.ReadbackSurface;
import dev.goldberry.render.model.PhysicalRect;
import dev.goldberry.render.window.GpuSurface;

/// A picture with GPU layers in it, taken both ways a window shows them, on a
/// real device (ADR-0481): composited, as a composited window's swapchain
/// would show it, and read back, as a window on the CPU would.
///
/// A picture is taken by a function from a surface to an image -- usually
/// `Offscreen.of(size).gpu(surface).paint(…)` or `.render(…)` -- so a painter
/// and a widget tree are taken the same way. Both ways go through the
/// production passes: [LayerTextures#renderAll] and [UiComposite] composited,
/// [SdlReadbackSurface] read back.
///
/// Public for the tests in `…gpu.view`, which draw `canvas3d` with it.
public final class CompositeHarness implements AutoCloseable {

    private final SdlGpuDevice required;
    private final SdlCompositor compositor;
    private final GpuDevice api;
    private final UiComposite composite;
    private final List<SdlGpuDevice> others = new java.util.ArrayList<>();

    private CompositeHarness(SdlGpuDevice required) {
        this.required = required;
        this.compositor = new SdlCompositor();
        this.api = compositor.api().orElseThrow();
        this.composite = new UiComposite(compositor.device().orElseThrow());
    }

    /// Initialises SDL's video under the lane's driver, which the compositor's
    /// device needs, or skips or fails the calling test without one.
    public static CompositeHarness open() {
        return new CompositeHarness(GpuDeviceRequirement.enforce());
    }

    /// The device layers render with, as the GPU API sees it.
    public GpuDevice device() {
        return api;
    }

    /// A second device, as a replaced one would be: closed with the harness,
    /// and closed early by [#closeOther].
    public GpuDevice otherDevice() {
        var other = SdlGpuDevice.create(SdlGpuDevice.Options.defaults().withDebugMode(true));
        others.add(other);
        return ApiAccess.device(other);
    }

    /// Closes every device [#otherDevice] made, as a lost device would be.
    public void closeOthers() {
        others.forEach(SdlGpuDevice::close);
        others.clear();
    }

    /// A read-back surface on this device, for a test to render with itself.
    public ReadbackSurface readBackSurface() {
        return compositor.readback();
    }

    /// What a composited picture showed, and the layers its frame placed.
    public record Composited(Image image, List<GpuPlacement> placed) {}

    /// `picture` painted with holes, then composited with its layers as a
    /// composited window's present does, into a texture that is read back.
    public Composited composited(Function<GpuSurface, Image> picture) {
        var recorder = new Recorder();
        var frame = picture.apply(recorder);
        var size = frame.size();
        try (var textures = new LayerTextures();
                var ui = api.createTexture(
                        TextureSpec.sampled(TextureFormat.B8G8R8A8_UNORM, size.width(), size.height()));
                var target = api.createTexture(
                        TextureSpec.renderTarget(TextureFormat.B8G8R8A8_UNORM, size.width(), size.height()))) {
            try (var upload = api.beginFrame()) {
                upload.copyPass(copy -> copy.upload(ui, frame.pixels(), List.of(PhysicalRect.of(size))));
                upload.submit();
            }
            var layers = textures.renderAll(api, recorder.placed);
            var commands = compositor.device().orElseThrow().acquireCommandBuffer();
            composite.draw(
                    commands,
                    ApiAccess.texture(target),
                    SdlGpuTextureFormat.B8G8R8A8_UNORM,
                    ApiAccess.texture(ui),
                    layers);
            commands.submit();
            try (var read = api.beginFrame()) {
                var readback = read.readback(target);
                read.submit();
                return new Composited(direct(readback.awaitPixels()), recorder.placed);
            }
        }
    }

    /// `picture` painted with each layer rendered, read back and drawn into it.
    public Image readBack(Function<GpuSurface, Image> picture) {
        try (var surface = compositor.readback()) {
            return picture.apply(surface);
        }
    }

    /// Asserts `a` and `b` differ by at most two levels in 256 in any channel:
    /// the two ways differ, if at all, only where translucent UI is blended
    /// over a layer, once by Blend2D and once by the GPU.
    public static void assertSamePicture(String name, Image a, Image b) {
        var worst = 0;
        var differing = 0;
        for (var y = 0; y < a.height(); y++) {
            for (var x = 0; x < a.width(); x++) {
                var first = a.argb(x, y);
                var second = b.argb(x, y);
                for (var shift = 0; shift < 32; shift += 8) {
                    var difference = Math.abs(((first >>> shift) & 0xFF) - ((second >>> shift) & 0xFF));
                    worst = Math.max(worst, difference);
                    if (difference > 2) {
                        differing++;
                    }
                }
            }
        }
        if (worst > 2) {
            // Both pictures, where the golden harness writes its own, to look at.
            try {
                var directory = java.nio.file.Path.of("build", "golden-failures");
                java.nio.file.Files.createDirectories(directory);
                java.nio.file.Files.write(directory.resolve(name + "-composited.png"), a.encodePng());
                java.nio.file.Files.write(directory.resolve(name + "-read-back.png"), b.encodePng());
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }
        assertTrue(
                worst <= 2,
                name + ": composited and read back differ by " + worst + " levels, in " + differing + " channels;"
                        + " both are in build/golden-failures");
    }

    @Override
    public void close() {
        closeOthers();
        composite.close();
        compositor.close();
        required.close();
        Sdl.get().quit();
    }

    /// What a composited window keeps of a frame: the layers it placed.
    private static final class Recorder implements GpuSurface.Composited {
        List<GpuPlacement> placed = List.of();

        @Override
        public void placed(List<GpuPlacement> layers) {
            placed = layers;
        }
    }

    /// `pixels` in direct memory, which an image drawn or encoded needs.
    private static Image direct(PixelBuffer pixels) {
        var source = pixels.pixels();
        var copy = ByteBuffer.allocateDirect(source.remaining()).order(source.order());
        copy.put(0, source, source.position(), source.remaining());
        return Image.of(new PixelBuffer(pixels.size(), pixels.format(), pixels.stride(), copy));
    }
}
