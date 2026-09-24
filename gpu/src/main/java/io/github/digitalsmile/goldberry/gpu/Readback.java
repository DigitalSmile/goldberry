package io.github.digitalsmile.goldberry.gpu;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.natives.sdl.SdlException;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuTransferBuffer;
import io.github.digitalsmile.goldberry.render.PixelBuffer;
import io.github.digitalsmile.goldberry.render.model.PhysicalSize;
import io.github.digitalsmile.goldberry.render.model.PixelFormat;

/// Pixels of a texture on their way back to the CPU, recorded by
/// [GpuFrame#readback] and readable once that frame is submitted.
///
/// [#await] is **the one call in this API that blocks**: it waits for the GPU
/// to finish the frame. It is how headless rendering, `Offscreen`, the goldens
/// and a window that cannot be claimed show GPU content
/// (`docs/gpu-plan.md`, D3). A frame that is drawing to the screen should not
/// wait on one.
///
/// Closing one that was never awaited gives its memory back without waiting.
public final class Readback implements AutoCloseable {

    private enum State {
        RECORDED,
        SUBMITTED,
        DONE,
        DISCARDED,
        CLOSED
    }

    private final GpuDevice device;
    private final TextureFormat format;
    private final int width;
    private final int height;
    private @Nullable SdlGpuTransferBuffer transfer;
    private @Nullable SharedFence fence;
    private @Nullable ByteBuffer pixels;
    private State state = State.RECORDED;

    Readback(GpuDevice device, SdlGpuTransferBuffer transfer, TextureFormat format, int width, int height) {
        this.device = device;
        this.transfer = transfer;
        this.format = format;
        this.width = width;
        this.height = height;
    }

    /// The format of the texture read.
    public TextureFormat format() {
        return format;
    }

    /// The width of the region read, in pixels.
    public int width() {
        return width;
    }

    /// The height of the region read, in pixels.
    public int height() {
        return height;
    }

    /// Whether the pixels have arrived and been copied out: [#await] would
    /// not block.
    public boolean isDone() {
        return state == State.DONE;
    }

    /// Waits for the GPU, then returns the pixels: tightly packed rows, top row
    /// first, in the texture's format and native byte order. A read-only view
    /// of memory the Java heap owns, so it outlives the GPU's; each call returns
    /// a fresh view of the same bytes.
    ///
    /// @throws IllegalStateException when the frame was not submitted, was
    ///                               discarded, or this was closed
    /// @throws GpuException          when the wait fails: a lost device
    public ByteBuffer await() {
        device.requireThread();
        switch (state) {
            case RECORDED -> throw new IllegalStateException("the frame this was read in has not been submitted");
            case DISCARDED -> throw new IllegalStateException("the frame this was read in was discarded");
            case CLOSED -> throw new IllegalStateException("this readback is closed");
            case SUBMITTED -> copyOut();
            case DONE -> {}
        }
        var done = pixels;
        if (done == null) {
            throw new IllegalStateException("no pixels");
        }
        return done.asReadOnlyBuffer().order(ByteOrder.nativeOrder());
    }

    /// [#await], as a [PixelBuffer] of premultiplied BGRA: the form a golden
    /// compares and Blend2D draws. A [TextureFormat#B8G8R8A8_UNORM] texture's
    /// bytes are used as they are; a [TextureFormat#R8G8B8A8_UNORM] one's are
    /// swizzled. Either is taken to hold premultiplied colour, as everything
    /// the toolkit composites does.
    ///
    /// @throws IllegalStateException when the format is neither, or as [#await]
    /// @throws GpuException          as [#await]
    public PixelBuffer awaitPixels() {
        if (format != TextureFormat.B8G8R8A8_UNORM && format != TextureFormat.R8G8B8A8_UNORM) {
            throw new IllegalStateException(format + " has no PixelBuffer form; read it with await()");
        }
        var bytes = await();
        var size = new PhysicalSize(width, height);
        if (format == TextureFormat.B8G8R8A8_UNORM) {
            return new PixelBuffer(size, PixelFormat.BGRA32_PREMULTIPLIED, width * 4, bytes);
        }
        var swizzled = ByteBuffer.allocate(bytes.remaining());
        for (var i = 0; i < swizzled.capacity(); i += 4) {
            swizzled.put(i, bytes.get(i + 2));
            swizzled.put(i + 1, bytes.get(i + 1));
            swizzled.put(i + 2, bytes.get(i));
            swizzled.put(i + 3, bytes.get(i + 3));
        }
        return new PixelBuffer(size, PixelFormat.BGRA32_PREMULTIPLIED, width * 4, swizzled.asReadOnlyBuffer());
    }

    /// Gives back its memory without waiting. Idempotent.
    @Override
    public void close() {
        device.requireThread();
        if (state == State.CLOSED) {
            return;
        }
        state = State.CLOSED;
        pixels = null;
        releaseGpuSide();
    }

    /// Its frame was submitted, and `submitted` signals when the pixels are in.
    void submitted(SharedFence submitted) {
        if (state == State.CLOSED) {
            submitted.release();
            return;
        }
        fence = submitted;
        state = State.SUBMITTED;
    }

    /// Its frame was discarded, and the pixels will never come.
    void discarded() {
        if (state == State.RECORDED) {
            state = State.DISCARDED;
            releaseGpuSide();
        }
    }

    private void copyOut() {
        var buffer = transfer;
        var submitted = fence;
        if (buffer == null || submitted == null || device.isClosed()) {
            throw new IllegalStateException("the device this was read on is closed");
        }
        try {
            submitted.await();
            var mapped = buffer.map(false);
            try {
                var copy = ByteBuffer.allocate(mapped.remaining());
                copy.put(0, mapped, 0, mapped.remaining());
                pixels = copy;
            } finally {
                buffer.unmap();
            }
        } catch (SdlException e) {
            throw GpuException.of(e);
        }
        state = State.DONE;
        releaseGpuSide();
    }

    private void releaseGpuSide() {
        var buffer = transfer;
        transfer = null;
        if (buffer != null) {
            buffer.close();
        }
        var submitted = fence;
        fence = null;
        if (submitted != null) {
            submitted.release();
        }
    }
}
