package io.github.digitalsmile.goldberry.backend.headless;

import io.github.digitalsmile.goldberry.backend.BackendEvent;
import io.github.digitalsmile.goldberry.backend.BackendException;
import io.github.digitalsmile.goldberry.backend.BackendWindow;
import io.github.digitalsmile.goldberry.backend.Cursor;
import io.github.digitalsmile.goldberry.backend.DamageRect;
import io.github.digitalsmile.goldberry.backend.DisplayScale;
import io.github.digitalsmile.goldberry.backend.LogicalSize;
import io.github.digitalsmile.goldberry.backend.PhysicalSize;
import io.github.digitalsmile.goldberry.backend.PixelBuffer;
import io.github.digitalsmile.goldberry.backend.WindowSpec;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// A window that exists only as state.
///
/// Presented frames are kept instead of shown, and every rule the SPI states is
/// checked here rather than assumed — which is the point: a real backend that
/// breaks one of them fails the same tests.
public sealed class HeadlessWindow implements BackendWindow permits HeadlessPopup {

    private final HeadlessBackend backend;

    private LogicalSize size;
    private DisplayScale scale;
    private String title;
    private boolean open = true;
    private boolean framePending;

    private PixelBuffer lastFrame;
    private List<DamageRect> lastDamage = List.of();
    private int presentCount;
    private Cursor cursor = Cursor.DEFAULT;
    private int cursorChanges;

    HeadlessWindow(HeadlessBackend backend, WindowSpec spec, DisplayScale scale) {
        this(backend, spec.size(), scale, spec.title());
    }

    HeadlessWindow(HeadlessBackend backend, LogicalSize size, DisplayScale scale, String title) {
        this.backend = backend;
        this.size = size;
        this.scale = scale;
        this.title = title;
    }

    /// The backend, for a subclass that has to reach it.
    final HeadlessBackend backend() {
        return backend;
    }


    @Override
    public LogicalSize size() {
        backend.requireUiThread();
        return size;
    }

    @Override
    public PhysicalSize physicalSize() {
        backend.requireUiThread();
        return scale.toPhysical(size);
    }

    @Override
    public DisplayScale scale() {
        backend.requireUiThread();
        return scale;
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
                            + ". The frame was rasterized against a stale size --"
                            + " a resize was processed after layout and before paint.");
        }
        for (var rect : damage) {
            if (!rect.fitsWithin(expected)) {
                throw new IllegalArgumentException(
                        "damage " + rect + " falls outside the " + expected + " frame");
            }
        }

        // Copied, because the SPI lends the buffer for the duration of the call
        // and Blend2D reuses it for the next frame. A test asserting on pixels
        // must be asserting on the frame it was given, not on whatever came next.
        this.lastFrame = copyOf(frame);
        this.lastDamage = List.copyOf(damage);
        this.presentCount++;

        // Deliberately does NOT clear framePending -- see frameDelivered(). A
        // painter that asks for the next frame while painting this one must keep
        // its request, or an animation runs exactly once.
    }

    @Override
    public void requestFrame() {
        backend.requireUiThread();
        requireOpen();
        // Coalescing is the contract: asking twice before the frame arrives must
        // not draw twice.
        if (framePending) {
            return;
        }
        framePending = true;
        backend.post(new BackendEvent.FrameDue(this));
    }

    @Override
    public void setTitle(String title) {
        backend.requireUiThread();
        requireOpen();
        this.title = Objects.requireNonNull(title, "title");
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
    }

    /// The last frame presented, if any. What a golden-image test asserts on.
    public Optional<PixelBuffer> lastFrame() {
        backend.requireUiThread();
        return Optional.ofNullable(lastFrame);
    }

    /// The damage list that came with [#lastFrame()].
    public List<DamageRect> lastDamage() {
        backend.requireUiThread();
        return lastDamage;
    }

    /// How many frames have been presented. Frame-loop tests count these.
    public int presentCount() {
        backend.requireUiThread();
        return presentCount;
    }

    /// Whether a [#requestFrame()] is outstanding.
    public boolean isFramePending() {
        backend.requireUiThread();
        return framePending;
    }

    /// Marks the outstanding request as consumed, at the moment its `FrameDue` is
    /// handed to the sink.
    ///
    /// Delivery, not presentation, is what satisfies a request — the same rule
    /// the sdl3 backend follows, where `takeFrameRequest()` clears the flag as the
    /// event is emitted. Clearing on present instead would discard a request the
    /// painter made while painting.
    void frameDelivered() {
        framePending = false;
    }

    /// Resizes the window as the platform would, and queues the event.
    ///
    /// The logical size changes; the scale does not.
    public void resizeTo(LogicalSize newSize) {
        backend.requireUiThread();
        requireOpen();
        applySize(newSize);
        backend.post(new BackendEvent.Resized(this, size, physicalSize()));
    }

    /// Changes the size this window reports, without announcing it.
    ///
    /// For [HeadlessPopup], which announces its own resize when it is *asked* for
    /// and applies it when the event is delivered — which is the order a real
    /// window manager does it in.
    final void applySize(LogicalSize newSize) {
        this.size = Objects.requireNonNull(newSize, "newSize");
    }

    /// Moves the window to a display with a different scale, and queues the
    /// event.
    ///
    /// The logical size is unchanged — that is what distinguishes this from a
    /// resize, and the case a toolkit gets wrong by treating the two as one.
    public void rescaleTo(DisplayScale newScale) {
        backend.requireUiThread();
        requireOpen();
        this.scale = Objects.requireNonNull(newScale, "newScale");
        backend.post(new BackendEvent.ScaleChanged(this, scale, physicalSize()));
    }

    /// Queues a close request, as a title-bar button would.
    public void requestClose() {
        backend.requireUiThread();
        requireOpen();
        backend.post(new BackendEvent.CloseRequested(this));
    }

    /// Queues a pointer move, as the platform would.
    ///
    /// The headless backend exists so the SPI's rules can be tested without a
    /// display (ADR-0019); pointer events are no different, and a test that had
    /// to open a window to check a hover would not run in CI.
    public void movePointer(float x, float y) {
        movePointer(x, y, 0);
    }

    /// The same, with the platform's modifier bitmask — what a test driving a
    /// knob's fine-adjustment drag needs (ADR-0089).
    public void movePointer(float x, float y, int modifiers) {
        backend.requireUiThread();
        requireOpen();
        backend.post(new BackendEvent.PointerMoved(this, x, y, modifiers));
    }

    /// Queues a pointer press. `button` is SDL's numbering: 1 is primary.
    public void pressPointer(float x, float y, int button, int clickCount) {
        pressPointer(x, y, button, clickCount, 0);
    }

    /// The same, with modifiers.
    public void pressPointer(float x, float y, int button, int clickCount, int modifiers) {
        backend.requireUiThread();
        requireOpen();
        backend.post(new BackendEvent.PointerPressed(this, x, y, button, clickCount, modifiers));
    }

    /// Queues a pointer release.
    public void releasePointer(float x, float y, int button, int clickCount) {
        releasePointer(x, y, button, clickCount, 0);
    }

    /// The same, with modifiers.
    public void releasePointer(float x, float y, int button, int clickCount, int modifiers) {
        backend.requireUiThread();
        requireOpen();
        backend.post(new BackendEvent.PointerReleased(this, x, y, button, clickCount, modifiers));
    }

    /// Queues a wheel turn. Deltas are in lines, positive down and right —
    /// already normalized, as a real backend delivers them.
    public void scrollPointer(float x, float y, float deltaX, float deltaY) {
        scrollPointer(x, y, deltaX, deltaY, 0);
    }

    /// The same, with modifiers.
    public void scrollPointer(float x, float y, float deltaX, float deltaY, int modifiers) {
        backend.requireUiThread();
        requireOpen();
        backend.post(new BackendEvent.PointerWheel(this, x, y, deltaX, deltaY, modifiers));
    }

    /// Queues the pointer leaving the window.
    public void exitPointer() {
        backend.requireUiThread();
        requireOpen();
        backend.post(new BackendEvent.PointerExited(this));
    }

    /// Queues a key press. `keycode` is SDL's virtual keycode.
    public void pressKey(int keycode, int modifiers, boolean repeat) {
        backend.requireUiThread();
        requireOpen();
        backend.post(new BackendEvent.KeyPressed(this, keycode, modifiers, repeat));
    }

    /// Queues a key release.
    public void releaseKey(int keycode, int modifiers) {
        backend.requireUiThread();
        requireOpen();
        backend.post(new BackendEvent.KeyReleased(this, keycode, modifiers));
    }

    /// Queues committed text, as the platform's own translation would produce.
    public void inputText(String text) {
        backend.requireUiThread();
        requireOpen();
        backend.post(new BackendEvent.TextInput(this, Objects.requireNonNull(text, "text")));
    }

    /// Queues an expose, as an uncovered or restored window would.
    public void expose() {
        backend.requireUiThread();
        requireOpen();
        backend.post(new BackendEvent.Exposed(this));
    }

    @Override
    public void setCursor(Cursor next) {
        backend.requireUiThread();
        Objects.requireNonNull(next, "cursor");
        if (cursor == next) {
            // The SPI says repeating a shape must be free, so the backend that
            // exists to check the SPI's rules counts what a real one would do.
            return;
        }
        cursor = next;
        cursorChanges++;
    }

    /// The shape the pointer would be showing.
    public Cursor cursor() {
        backend.requireUiThread();
        return cursor;
    }

    /// How many times the cursor actually changed — not how many times it was
    /// set. A router that told the platform on every pointer move would show up
    /// here as a number that climbs with the mouse.
    public int cursorChanges() {
        backend.requireUiThread();
        return cursorChanges;
    }

    private static PixelBuffer copyOf(PixelBuffer frame) {
        var pixels = frame.pixels();
        var copy = java.nio.ByteBuffer.allocate(pixels.remaining());
        copy.put(pixels.duplicate()).flip();
        return new PixelBuffer(frame.size(), frame.format(), frame.stride(), copy);
    }

    private void requireOpen() {
        if (!open) {
            throw new BackendException("the window is closed");
        }
    }
}
