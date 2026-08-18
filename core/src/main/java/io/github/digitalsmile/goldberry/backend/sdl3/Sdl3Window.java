package io.github.digitalsmile.goldberry.backend.sdl3;

import io.github.digitalsmile.goldberry.backend.BackendException;
import io.github.digitalsmile.goldberry.backend.BackendWindow;
import io.github.digitalsmile.goldberry.backend.Cursor;
import io.github.digitalsmile.goldberry.backend.DamageRect;
import io.github.digitalsmile.goldberry.backend.DisplayScale;
import io.github.digitalsmile.goldberry.backend.LogicalSize;
import io.github.digitalsmile.goldberry.backend.PhysicalSize;
import io.github.digitalsmile.goldberry.backend.PixelBuffer;
import io.github.digitalsmile.goldberry.natives.log.Logs;
import io.github.digitalsmile.goldberry.natives.sdl.SdlException;
import io.github.digitalsmile.goldberry.natives.sdl.SdlSystemCursor;
import io.github.digitalsmile.goldberry.natives.sdl.SdlVideo;
import io.github.digitalsmile.goldberry.natives.sdl.SdlWindowHandle;
import io.github.digitalsmile.goldberry.backend.PixelFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;

/// An SDL window behind the SPI.
///
/// Not final, and [Sdl3Popup] is the one subclass: a popup **is** an SDL window —
/// it acquires a frame, presents, paces and closes identically, and its events
/// arrive through the same pump under its own id — and the two things it adds are
/// an owner and a position. Sharing the class is also what keeps popups in
/// [Sdl3Backend]'s window map without a second lookup path, so a popup's events
/// find their way home by the code that was already there.
sealed class Sdl3Window implements BackendWindow permits Sdl3Popup {

    private static final Logger LOG = Logs.of(Sdl3Window.class);

    /// Distinguishes "asked, and SDL would not say" from "not asked yet", so a
    /// display that has no refresh rate is not re-queried every pump.
    private static final float UNAVAILABLE = -1f;

    private final Sdl3Backend backend;
    private final SdlWindowHandle handle;

    private String title;
    private boolean open = true;
    private boolean framePending;
    private boolean shown;

    /// The buffer handed out by the last [#acquireFrame()], so [#present] can
    /// recognise it coming back and skip the copy.
    private PixelBuffer acquired;

    /// 0 = not asked yet, [#UNAVAILABLE] = asked and refused. See [#refreshRate()].
    private float refreshRate;

    /// The sizes the last reported resize carried. See [#resizedTo].
    private LogicalSize reportedSize;
    private PhysicalSize reportedPhysicalSize;

    /// The handle, for a subclass that has to make its own SDL calls.
    final SdlWindowHandle handle() {
        return handle;
    }

    /// The backend, for the same reason.
    final Sdl3Backend backend() {
        return backend;
    }

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

    /// SDL keeps one window surface and hands the same one back until the window
    /// is resized, so what was painted last frame is still there.
    ///
    /// True on both branches of `SDL_GetWindowSurface`: where the platform lends
    /// mapped memory it is the platform's own surface, and where SDL falls back
    /// to a heap buffer and copies into a texture on present (its Wayland driver,
    /// ADR-0046) that heap buffer is equally persistent. What SDL does *after*
    /// present does not disturb it.
    ///
    /// A resize invalidates it, and the caller notices by the size changing
    /// rather than by anything said here.
    @Override
    public boolean retainsFrameContents() {
        return true;
    }

    @Override
    public Optional<PixelBuffer> acquireFrame() {
        backend.requireUiThread();
        requireOpen();
        try {
            var surface = video().acquireSurface(handle);
            acquired = new PixelBuffer(
                    new PhysicalSize(surface.width(), surface.height()),
                    PixelFormat.BGRA32_PREMULTIPLIED,
                    surface.stride(),
                    surface.pixels());
            return Optional.of(acquired);
        } catch (SdlException e) {
            // A surface SDL will not give us is not a failure: the caller
            // allocates its own and present() copies, which is the path every
            // other backend takes anyway.
            acquired = null;
            return Optional.empty();
        }
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
            if (frame == acquired) {
                // Painted straight into SDL's surface. Nothing to copy -- just
                // tell SDL which parts changed.
                video().presentAcquired(
                        handle, new SdlVideo.SdlSize(expected.width(), expected.height()), rects);
            } else {
                video().present(
                        handle,
                        frame.pixels().duplicate(),
                        frame.stride(),
                        new SdlVideo.SdlSize(expected.width(), expected.height()),
                        rects);
            }
        } catch (SdlException e) {
            throw new BackendException("presenting a frame failed", e);
        } finally {
            // SDL's memory is only ours between acquire and present.
            acquired = null;
        }

        // Shown only once there is something to look at. A window mapped before
        // its first frame flashes whatever was in the compositor's buffer.
        if (!shown) {
            shown = true;
            video().showWindow(handle);
        }

        // Deliberately does NOT clear framePending. The request was consumed when
        // its FrameDue was emitted; clearing it here would also discard a request
        // made by the painter *during* this frame, which is how an animation stops
        // after one frame.
    }

    @Override
    public void requestFrame() {
        backend.requireUiThread();
        requireOpen();
        if (framePending) {
            // Already asked. Pushing a second wakeup would put an event on SDL's
            // queue for every repaint() call in a batch.
            return;
        }
        framePending = true;

        // Wake the loop. Without this the request sits until the next platform
        // event or the loop's idle heartbeat -- so a repaint asked for from an
        // event handler was drawn up to a second later, and a window being
        // resized showed black where it had not caught up yet.
        backend.wakeup();
    }

    @Override
    public void setCursor(Cursor cursor) {
        backend.requireUiThread();
        if (!open) {
            return;
        }
        backend.setCursor(toSdl(Objects.requireNonNull(cursor, "cursor")));
    }

    /// The platform shape for a toolkit one.
    ///
    /// Two of §7.3's shapes have no system cursor anywhere: `grab` and `grabbing`
    /// are a CSS invention that X11's cursor font, Win32's `IDC_*` set and
    /// `SDL_SystemCursor` all lack. They fall back to `move`, which says "this can
    /// be dragged" less precisely rather than saying nothing — until custom image
    /// cursors ship and the fallback can become the real thing.
    private static SdlSystemCursor toSdl(Cursor cursor) {
        return switch (cursor) {
            case DEFAULT -> SdlSystemCursor.DEFAULT;
            case POINTER -> SdlSystemCursor.POINTER;
            case TEXT -> SdlSystemCursor.TEXT;
            case MOVE, GRAB, GRABBING -> SdlSystemCursor.MOVE;
            case WAIT -> SdlSystemCursor.WAIT;
            case PROGRESS -> SdlSystemCursor.PROGRESS;
            case CROSSHAIR -> SdlSystemCursor.CROSSHAIR;
            case NOT_ALLOWED -> SdlSystemCursor.NOT_ALLOWED;
            case EW_RESIZE -> SdlSystemCursor.EW_RESIZE;
            case NS_RESIZE -> SdlSystemCursor.NS_RESIZE;
            case NESW_RESIZE -> SdlSystemCursor.NESW_RESIZE;
            case NWSE_RESIZE -> SdlSystemCursor.NWSE_RESIZE;
        };
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
        // First, and not tidiness. SDL destroys a window's popups with it, so
        // after this call their handles are dangling and their ids are free for
        // reuse — while `windowsById` still holds them. The event loop runs until
        // `windows()` is empty, so an orphaned popup is a process that will not
        // exit.
        backend.closePopupsOf(this);
        backend.forget(this);
        video().destroyWindow(handle);
    }

    /// Whether a frame has been asked for and not yet handed out. Read by the
    /// pacer, which must not consume the request while deciding how long to wait.
    boolean isFramePending() {
        return framePending && open;
    }

    /// How many times a second this window's display refreshes, or 0 if the
    /// platform will not say.
    ///
    /// Cached, because it is read once per pump and a native call per pump for a
    /// number that changes only when the window moves monitors is waste. Dropped
    /// by [#forgetRefreshRate()] when it might have changed.
    float refreshRate() {
        if (!open) {
            return 0f;
        }
        if (refreshRate == 0f) {
            try {
                refreshRate = video().refreshRate(handle);
            } catch (SdlException e) {
                // Not fatal, and not worth retrying every pump: an unknowable
                // refresh rate means an unpaced loop, which is what the loop did
                // before it could ask at all.
                LOG.debug("SDL would not report a refresh rate for \"{}\"", title, e);
                refreshRate = UNAVAILABLE;
            }
        }
        return refreshRate == UNAVAILABLE ? 0f : refreshRate;
    }

    /// Forgets the cached refresh rate, so the next [#refreshRate()] asks again.
    ///
    /// Called when the window may have moved to another display — which is what
    /// a scale change usually is, and the case where a 60 Hz pace on a 144 Hz
    /// monitor would otherwise persist for the life of the window.
    void forgetRefreshRate() {
        refreshRate = 0f;
    }

    /// Records a resize and says whether it is news.
    ///
    /// SDL sends `WINDOW_RESIZED` liberally, and since ADR-0060 the same one
    /// arrives twice by design: the event watch handles it while the platform is
    /// still inside its resize loop, and the queue hands the copy over when the
    /// loop ends. A resize to the size the window already has costs a layout pass
    /// and a frame and changes nothing, so it is reported once.
    ///
    /// Both sizes are compared. On a fractional scale the logical size can stay
    /// put while the backing store moves by a pixel, and that is a real resize as
    /// far as anything that rasterizes is concerned.
    boolean resizedTo(LogicalSize logical, PhysicalSize physical) {
        if (logical.equals(reportedSize) && physical.equals(reportedPhysicalSize)) {
            return false;
        }
        reportedSize = logical;
        reportedPhysicalSize = physical;
        return true;
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

    int handleId() {
        return handle.id();
    }

    SdlVideo video() {
        return backend.video();
    }

    private void requireOpen() {
        if (!open) {
            throw new BackendException("the window is closed");
        }
    }
}
