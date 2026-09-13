package io.github.digitalsmile.goldberry.render.backend.headless;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.render.BackendException;
import io.github.digitalsmile.goldberry.render.Cursor;
import io.github.digitalsmile.goldberry.render.DamageRect;
import io.github.digitalsmile.goldberry.render.PixelBuffer;
import io.github.digitalsmile.goldberry.render.event.BackendEvent;
import io.github.digitalsmile.goldberry.render.model.DisplayScale;
import io.github.digitalsmile.goldberry.render.model.LogicalPoint;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.render.model.PhysicalSize;
import io.github.digitalsmile.goldberry.render.window.BackendWindow;
import io.github.digitalsmile.goldberry.render.window.WindowSpec;

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
    private boolean textInputActive;
    private @Nullable LogicalRect textInputArea;
    private double textInputCursor;
    private boolean open = true;
    private boolean framePending;

    /// Where this window pretends to be on the backend's desktop.
    private LogicalPoint position = LogicalPoint.ZERO;

    /// The floor a "user" drag is stopped at — see [#resizeTo].
    private LogicalSize minimumSize = WindowSpec.NO_MINIMUM;

    private @Nullable PixelBuffer lastFrame;
    private List<DamageRect> lastDamage = List.of();
    private int presentCount;
    private Cursor cursor = Cursor.DEFAULT;
    private int cursorChanges;

    HeadlessWindow(HeadlessBackend backend, WindowSpec spec, DisplayScale scale) {
        this(backend, spec.size(), scale, spec.title());
        this.minimumSize = spec.minimumSize();
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
            throw new IllegalArgumentException("frame is " + frame.size() + " but the window is " + expected
                    + ". The frame was rasterized against a stale size --"
                    + " a resize was processed after layout and before paint.");
        }
        for (var rect : damage) {
            if (!rect.fitsWithin(expected)) {
                throw new IllegalArgumentException("damage " + rect + " falls outside the " + expected + " frame");
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
    public Optional<LogicalPoint> position() {
        backend.requireUiThread();
        return open ? Optional.of(position) : Optional.empty();
    }

    @Override
    public Optional<LogicalRect> workArea() {
        backend.requireUiThread();
        return open ? Optional.of(backend.workArea()) : Optional.empty();
    }

    /// Puts this window somewhere on the backend's pretend desktop.
    ///
    /// There is no window manager here to do it, and a placement test needs a
    /// window that is *near an edge* — which is the only interesting case.
    /// **Posts a [BackendEvent.Moved]**, which is the half a real window manager
    /// would send and the half nothing else here can fabricate: a window move is
    /// not an event any test can produce on the platform, and it is the one
    /// thing that changes where the screen's edges are without changing anything
    /// inside the window ([ADR-0061], [ADR-0270]).
    ///
    /// Silent when the window is already there, exactly as
    /// [io.github.digitalsmile.goldberry.render.backend.sdl3.Sdl3Window] is:
    /// a test that moves a window to where it already is should get the same
    /// nothing a compositor would deliver.
    public void moveTo(LogicalPoint next) {
        backend.requireUiThread();
        requireOpen();
        Objects.requireNonNull(next, "position");
        if (next.equals(position)) {
            return;
        }
        this.position = next;
        backend.post(new BackendEvent.Moved(this, next));
    }

    @Override
    public void setTitle(String title) {
        backend.requireUiThread();
        requireOpen();
        this.title = Objects.requireNonNull(title, "title");
    }

    /// Stands in for the window manager, which is the only way this rule can be
    /// tested at all.
    ///
    /// A real backend hands the floor to the platform and never sees it enforced;
    /// here the enforcement is [#resizeTo]'s, so a test can drag the window too
    /// small and assert on what the application is told. That is the same bargain
    /// the rest of this class makes: "every rule the SPI states is checked here
    /// rather than assumed".
    @Override
    public void setMinimumSize(LogicalSize minimum) {
        backend.requireUiThread();
        requireOpen();
        this.minimumSize = Objects.requireNonNull(minimum, "minimum");
    }

    @Override
    public LogicalSize minimumSize() {
        backend.requireUiThread();
        return minimumSize;
    }

    /// Records whether text input was asked for, so a test can assert that a
    /// field turned it on.
    ///
    /// The headless backend delivers committed text through
    /// [#inputText(String)] regardless of this flag. That is deliberate: a test
    /// that types into a field it forgot to focus should fail on the focus, not
    /// on a second gate that only exists on the real platform — and the flag is
    /// still here so the *contract* can be tested, which is what
    /// [BackendWindow#textInput(boolean)]
    /// says a field must do.
    @Override
    public void textInput(boolean active) {
        backend.requireUiThread();
        if (!isOpen()) {
            return;
        }
        this.textInputActive = active;
    }

    /// [BackendWindow#textInputArea], remembered rather than sent anywhere.
    ///
    /// A real backend hands this to the platform and the platform draws the
    /// candidate window; there is nothing here to draw it. What a test can still
    /// assert is the part Goldberry is responsible for — that the area follows
    /// the caret and is cleared when focus leaves — which is what
    /// [#textInputAreaValue()] and [#textInputCursor()] are for.
    @Override
    public void textInputArea(@Nullable LogicalRect area, double cursor) {
        backend.requireUiThread();
        if (!isOpen()) {
            return;
        }
        this.textInputArea = area;
        this.textInputCursor = cursor;
    }

    /// The last area a field reported, or empty if it has been cleared.
    public Optional<LogicalRect> textInputAreaValue() {
        return Optional.ofNullable(textInputArea);
    }

    /// The caret offset that came with it.
    public double textInputCursor() {
        return textInputCursor;
    }

    /// Whether [#textInput(boolean)] was last asked to turn it on.
    public boolean isTextInputActive() {
        return textInputActive;
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
        // A real platform destroys a window's popups with it, so this backend
        // does too — a fake that left them open would be the one place the event
        // loop's "run until every window has closed" quietly never finishes.
        backend.closePopupsOf(this);
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
    ///
    /// **Clamped to [#minimumSize] first**, because that is what a window manager
    /// does: the drag stops at the floor rather than being refused, so a test
    /// that asks for 100x100 against a 400x300 minimum gets a `Resized` for
    /// 400x300 and not a resize that did not happen. Per axis, so a zero on one
    /// of them constrains only the other (ADR-0304).
    public void resizeTo(LogicalSize newSize) {
        backend.requireUiThread();
        requireOpen();
        applySize(atLeastMinimum(newSize));
        backend.post(new BackendEvent.Resized(this, size, physicalSize()));
    }

    /// `wanted`, raised to the floor on each axis independently.
    private LogicalSize atLeastMinimum(LogicalSize wanted) {
        Objects.requireNonNull(wanted, "wanted");
        return new LogicalSize(
                Math.max(wanted.width(), minimumSize.width()), Math.max(wanted.height(), minimumSize.height()));
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
    /// Agrees to the ask and reports it, which is what a window manager that
    /// says yes does ([ADR-0252]).
    ///
    /// A real one may refuse, and the headless backend has no way to model
    /// *which* — so it models the agreeable case and [#reportMaximized] is how a
    /// test drives the other, including the one that matters most: the **user**
    /// maximizing a window nobody asked to.
    @Override
    public void setMaximized(boolean maximized) {
        backend.requireUiThread();
        requireOpen();
        backend.post(new BackendEvent.MaximizedChanged(this, maximized));
    }

    /// Queues a maximize/restore the application did **not** ask for.
    ///
    /// The half that makes "remember whether the user maximized it" testable:
    /// every other route into this state starts with the application, and that is
    /// the route that does not.
    public void reportMaximized(boolean maximized) {
        backend.requireUiThread();
        requireOpen();
        backend.post(new BackendEvent.MaximizedChanged(this, maximized));
    }

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

    /// The same, with modifiers. Detents are the truncation of the deltas.
    public void scrollPointer(float x, float y, float deltaX, float deltaY, int modifiers) {
        scrollPointer(x, y, deltaX, deltaY, (int) deltaX, (int) deltaY, modifiers);
    }

    /// Queues a wheel turn stating its accumulated detents separately.
    ///
    /// What a test needs to reach the case the pair exists for: a trackpad
    /// reporting fractions too small to truncate to anything, one of which
    /// carries the click they added up to ([ADR-0115]).
    public void scrollPointer(float x, float y, float deltaX, float deltaY, int ticksX, int ticksY, int modifiers) {
        backend.requireUiThread();
        requireOpen();
        backend.post(new BackendEvent.PointerWheel(this, x, y, deltaX, deltaY, ticksX, ticksY, modifiers));
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

    /// Queues the composition an input method is assembling — `docs/gaps.md` G15.
    ///
    /// What a test of a Japanese, Chinese or Korean user does: several of these
    /// as the string grows, then an [#inputText] with the accepted candidate and
    /// an [#endComposing] to close it.
    ///
    /// @param start  where the converting clause begins inside `text`, as a char
    ///               offset, or -1 for a platform that reports none
    /// @param length how many chars of it, or -1
    public void composeText(String text, int start, int length) {
        backend.requireUiThread();
        requireOpen();
        backend.post(new BackendEvent.TextEditing(this, Objects.requireNonNull(text, "text"), start, length));
    }

    /// [#composeText] with no converting clause, which is what several platforms
    /// report.
    public void composeText(String text) {
        composeText(text, -1, -1);
    }

    /// Queues the end of a composition — the empty `TEXT_EDITING` that arrives
    /// whether the user accepted a candidate or abandoned one.
    public void endComposing() {
        composeText("", -1, -1);
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
