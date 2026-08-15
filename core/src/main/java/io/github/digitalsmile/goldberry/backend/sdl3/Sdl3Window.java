package io.github.digitalsmile.goldberry.backend.sdl3;

import io.github.digitalsmile.goldberry.backend.BackendException;
import io.github.digitalsmile.goldberry.backend.BackendWindow;
import io.github.digitalsmile.goldberry.backend.DamageRect;
import io.github.digitalsmile.goldberry.backend.DisplayScale;
import io.github.digitalsmile.goldberry.backend.LogicalSize;
import io.github.digitalsmile.goldberry.backend.PhysicalSize;
import io.github.digitalsmile.goldberry.backend.PixelBuffer;
import io.github.digitalsmile.goldberry.natives.sdl.SdlException;
import io.github.digitalsmile.goldberry.natives.sdl.SdlVideo;
import io.github.digitalsmile.goldberry.natives.sdl.SdlWindowHandle;
import java.util.List;
import java.util.Objects;

/// An SDL window behind the SPI.
final class Sdl3Window implements BackendWindow {

    private final Sdl3Backend backend;
    private final SdlWindowHandle handle;

    private String title;
    private boolean open = true;
    private boolean framePending;
    private boolean shown;

    Sdl3Window(Sdl3Backend backend, SdlWindowHandle handle, String title) {
        this.backend = backend;
        this.handle = handle;
        this.title = title;
    }

    @Override
    public LogicalSize size() {
        backend.requireUiThread();
        var size = video().windowSize(handle);
        return new LogicalSize(size.width(), size.height());
    }

    @Override
    public PhysicalSize physicalSize() {
        backend.requireUiThread();
        // SDL's own number, not size() times scale(). On a fractional scale the
        // compositor's answer can differ by a pixel from anything computed, and
        // the frame has to match what will actually be presented into.
        var size = video().windowSizeInPixels(handle);
        return new PhysicalSize(size.width(), size.height());
    }

    @Override
    public DisplayScale scale() {
        backend.requireUiThread();
        return new DisplayScale(video().displayScale(handle));
    }

    @Override
    public void present(PixelBuffer frame, List<DamageRect> damage) {
        backend.requireUiThread();
        requireOpen();
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(damage, "damage");

        var expected = physicalSize();
        if (!frame.size().equals(expected)) {
            throw new IllegalArgumentException(
                    "frame is " + frame.size() + " but the window is " + expected
                            + ". The frame was rasterized against a stale size.");
        }
        for (var rect : damage) {
            if (!rect.fitsWithin(expected)) {
                throw new IllegalArgumentException(
                        "damage " + rect + " falls outside the " + expected + " frame");
            }
        }

        var rects = new int[damage.size() * 4];
        for (var i = 0; i < damage.size(); i++) {
            var rect = damage.get(i);
            rects[i * 4] = rect.x();
            rects[i * 4 + 1] = rect.y();
            rects[i * 4 + 2] = rect.width();
            rects[i * 4 + 3] = rect.height();
        }

        try {
            video().present(
                    handle,
                    frame.pixels().duplicate(),
                    frame.stride(),
                    new SdlVideo.SdlSize(expected.width(), expected.height()),
                    rects);
        } catch (SdlException e) {
            throw new BackendException("presenting a frame failed", e);
        }

        // Shown only once there is something to look at. A window mapped before
        // its first frame flashes whatever was in the compositor's buffer.
        if (!shown) {
            shown = true;
            video().showWindow(handle);
        }
        framePending = false;
    }

    @Override
    public void requestFrame() {
        backend.requireUiThread();
        requireOpen();
        framePending = true;
    }

    @Override
    public void setTitle(String title) {
        backend.requireUiThread();
        requireOpen();
        this.title = Objects.requireNonNull(title, "title");
        video().setWindowTitle(handle, title);
    }

    @Override
    public String title() {
        backend.requireUiThread();
        return title;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void close() {
        if (!open) {
            return;
        }
        backend.requireUiThread();
        open = false;
        framePending = false;
        backend.forget(this);
        video().destroyWindow(handle);
    }

    /// Consumes an outstanding frame request. Coalescing is implicit: the flag is
    /// either set or not, however many times it was asked for.
    boolean takeFrameRequest() {
        if (!framePending || !open) {
            return false;
        }
        framePending = false;
        return true;
    }

    /// Drops SDL's cached surface, so the next present gets one at the new size.
    void invalidateSurface() {
        if (open) {
            video().invalidateSurface(handle);
        }
    }

    int handleId() {
        return handle.id();
    }

    private SdlVideo video() {
        return backend.video();
    }

    private void requireOpen() {
        if (!open) {
            throw new BackendException("the window is closed");
        }
    }
}
