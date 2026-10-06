package dev.goldberry.render.backend.headless;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.goldberry.render.BackendException;
import dev.goldberry.render.Cursor;
import dev.goldberry.render.DamageRect;
import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.composite.ReadbackSurface;
import dev.goldberry.render.display.Display;
import dev.goldberry.render.display.DisplayLayout;
import dev.goldberry.render.event.BackendEvent;
import dev.goldberry.render.model.DisplayScale;
import dev.goldberry.render.model.LogicalPoint;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.render.model.PhysicalSize;
import dev.goldberry.render.window.Attention;
import dev.goldberry.render.window.BackendWindow;
import dev.goldberry.render.window.GpuSurface;
import dev.goldberry.render.window.IconImage;
import dev.goldberry.render.window.Presentation;
import dev.goldberry.render.window.WindowSpec;

/// A window that exists only as state.
///
/// Presented frames are kept instead of shown, and every rule the SPI states is
/// checked here rather than assumed — which is the point: a real backend that
/// breaks one of them fails the same tests.
///
/// Read more: [Windows, popups and the host](https://goldberry.dev/docs/guide/windows.html).
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

    /// The size asked for through [#resize] and not yet in force — see there.
    private @Nullable LogicalSize requestedSize;

    private @Nullable PixelBuffer lastFrame;
    private List<DamageRect> lastDamage = List.of();
    private int presentCount;

    /// The compositor's read-back surface for this window, made the first time
    /// one is asked for, and closed with the window.
    private @Nullable ReadbackSurface readback;
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

    /// The work area of the display this window is on — the backend's one work
    /// area, unless a test gave the backend displays of its own.
    @Override
    public Optional<LogicalRect> workArea() {
        backend.requireUiThread();
        if (!open) {
            return Optional.empty();
        }
        if (!backend.hasOwnDisplays()) {
            return Optional.of(backend.workArea());
        }
        return Optional.of(display().map(Display::usableBounds).orElse(backend.workArea()));
    }

    /// [#moveTo], which a window manager that lets an application place its
    /// windows does — unless the backend was told to behave like Wayland, where
    /// nothing is placed and this answers false.
    @Override
    public boolean place(LogicalPoint at) {
        backend.requireUiThread();
        requireOpen();
        if (!backend.placesWindows()) {
            return false;
        }
        moveTo(at);
        return true;
    }

    /// The display most of this window is on, or the nearest one.
    @Override
    public Optional<Display> display() {
        backend.requireUiThread();
        if (!open) {
            return Optional.empty();
        }
        return new DisplayLayout(backend.displays()).nearest(new LogicalRect(position, size));
    }

    /// The attention asked for and not withdrawn, or null.
    private @Nullable Attention attention;

    /// Every attention request, in order, cancellations left out.
    private final List<Attention> attentionRequests = new java.util.ArrayList<>();

    /// Recorded, because there is no taskbar here. Answers true.
    @Override
    public boolean requestAttention(Attention value) {
        backend.requireUiThread();
        requireOpen();
        attention = Objects.requireNonNull(value, "attention");
        attentionRequests.add(value);
        return true;
    }

    @Override
    public boolean cancelAttention() {
        backend.requireUiThread();
        attention = null;
        return open;
    }

    /// The attention asked for last and not withdrawn since.
    public Optional<Attention> attention() {
        return Optional.ofNullable(attention);
    }

    /// Every [#requestAttention] this window was given.
    public List<Attention> attentionRequests() {
        return List.copyOf(attentionRequests);
    }

    /// The window this one belongs to, or null.
    private @Nullable HeadlessWindow parentWindow;

    private boolean modal;

    private int raised;

    /// Recorded, and closed with its parent as SDL does.
    @Override
    public boolean parent(@Nullable BackendWindow parent) {
        backend.requireUiThread();
        requireOpen();
        if (parent != null && (!(parent instanceof HeadlessWindow window) || window instanceof HeadlessPopup)) {
            throw new IllegalArgumentException("a parent must be a top-level window of this backend");
        }
        parentWindow = (HeadlessWindow) parent;
        if (parentWindow == null) {
            modal = false;
        }
        return true;
    }

    /// Refused for a window with no parent, as SDL refuses it.
    @Override
    public boolean modal(boolean value) {
        backend.requireUiThread();
        requireOpen();
        if (value && parentWindow == null) {
            return false;
        }
        modal = value;
        return true;
    }

    @Override
    public boolean raise() {
        backend.requireUiThread();
        if (!open) {
            return false;
        }
        raised++;
        return true;
    }

    /// The window this one was made to belong to, if any.
    public Optional<HeadlessWindow> parentWindow() {
        return Optional.ofNullable(parentWindow);
    }

    /// Whether it was made modal for its parent.
    public boolean isModal() {
        return modal;
    }

    /// How many times [#raise] was asked.
    public int raiseCount() {
        return raised;
    }

    /// Puts this window somewhere on the backend's pretend desktop.
    ///
    /// There is no window manager here to do it, and a placement test needs a
    /// window that is *near an edge* — which is the only interesting case.
    /// **Posts a [BackendEvent.Moved]**, which is the half a real window manager
    /// would send and the half nothing else here can fabricate: a window move is
    /// not an event any test can produce on the platform, and it is the one
    /// thing that changes where the screen's edges are without changing anything
    /// inside the window.
    ///
    /// Silent when the window is already there, exactly as
    /// [dev.goldberry.render.backend.sdl3.Sdl3Window] is:
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

    /// The sizes the last [#setIcon] was given, in the order it was given them —
    /// empty until one is set.
    private List<IconImage> icon = List.of();

    /// Recorded, because there is no taskbar here. Answers true, so a caller's
    /// "the platform would not" path is a different test.
    @Override
    public boolean setIcon(List<IconImage> images) {
        backend.requireUiThread();
        requireOpen();
        this.icon = List.copyOf(Objects.requireNonNull(images, "images"));
        return true;
    }

    /// What [#setIcon] was last asked for.
    public List<IconImage> icon() {
        return icon;
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

    /// The request, answered by the window manager this backend stands in for:
    /// clamped to the floor, and **applied when the event is delivered** rather
    /// than here.
    ///
    /// On X11 and Wayland the window manager decides when a resize happens, and
    /// `size()` keeps reporting the old one until it has — which a caller that
    /// measures straight after this call gets wrong on two of the three
    /// desktops. Applying it instantly would make this fake the one place that
    /// bug passes, so the size lands as the `Resized` is handed over, exactly
    /// as it does through SDL and exactly as a popup's already did. Which is
    /// also what a frame in flight needs: a size that changed under the painter
    /// is a refused frame, and a request is not supposed to be one.
    @Override
    public void resize(LogicalSize size) {
        Objects.requireNonNull(size, "size");
        backend.requireUiThread();
        requireOpen();
        if (size.width() <= 0 || size.height() <= 0) {
            throw new IllegalArgumentException("a window needs a positive size, and " + size + " has none");
        }
        request(atLeastMinimum(size));
    }

    /// Announces `size` as the window manager's answer, to be applied by
    /// [#resizeDelivered].
    final void request(LogicalSize size) {
        requestedSize = size;
        backend.post(new BackendEvent.Resized(this, size, scale.toPhysical(size)));
    }

    /// Called by the backend as the resize event is handed over: the point at
    /// which a real platform's new size becomes visible to a caller.
    final void resizeDelivered() {
        if (requestedSize != null) {
            applySize(requestedSize);
            requestedSize = null;
        }
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
        // And the windows that belong to it, which SDL destroys with it too.
        backend.closeChildrenOf(this);
        backend.forget(this);
        var surface = readback;
        readback = null;
        if (surface != null) {
            surface.close();
        }
    }

    /// A read-back surface, when `:gpu` is on the module path: a headless
    /// window has nothing to composite into, so its GPU layers are rendered on
    /// the GPU and drawn into its frames as pixels. Its device is made by the
    /// first layer, and needs a video
    /// driver the GPU can use (`offscreen` under lavapipe, `cocoa` on macOS),
    /// which is what the GPU lane runs; without one the layers' painters draw
    /// what they show without a GPU.
    ///
    /// Empty without `:gpu`, and with `goldberry.gpu=off`.
    @Override
    public Optional<GpuSurface> gpuSurface() {
        backend.requireUiThread();
        if (!open) {
            return Optional.empty();
        }
        if (readback == null) {
            var compositor = backend.compositor();
            if (compositor.isEmpty()) {
                return Optional.empty();
            }
            readback = compositor.get().readback();
        }
        return Optional.of(readback);
    }

    /// [#lastFrame], copied: a headless window presents on the CPU, so the
    /// frame it was given is what it shows, GPU layers read back and all.
    @Override
    public Optional<PixelBuffer> capture() {
        backend.requireUiThread();
        return Optional.ofNullable(lastFrame).map(HeadlessWindow::copyOf);
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

    /// How this window says it presents. Nothing reaches a screen here, so it is
    /// the CPU unless a test says otherwise with [#presentAs].
    private Presentation presentation = new Presentation.Cpu("headless: nothing is shown on a screen");

    @Override
    public Presentation presentation() {
        backend.requireUiThread();
        return presentation;
    }

    /// Makes this window say it presents `value` from the next frame on, so a
    /// test can see what an application does when a window moves to the GPU or
    /// back to the CPU. Nothing about how it presents changes.
    public void presentAs(Presentation value) {
        backend.requireUiThread();
        this.presentation = Objects.requireNonNull(value, "value");
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
    /// of them constrains only the other.
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

    /// Agrees to the ask and reports it, which is what a window manager that
    /// says yes does.
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

    /// Agrees to the ask and reports it, as [#setMaximized] does.
    ///
    /// The size is left alone. A real platform follows the event with a resize
    /// to the display's size, and a test that wants that sends it with
    /// [#resizeTo]; the headless backend has no display whose size it could
    /// honestly pick.
    @Override
    public void setFullscreen(boolean fullscreen) {
        backend.requireUiThread();
        requireOpen();
        backend.post(new BackendEvent.FullscreenChanged(this, fullscreen));
    }

    /// Queues a fullscreen change the application did **not** ask for: the user
    /// pressing the platform's own button, or a window manager's key.
    public void reportFullscreen(boolean fullscreen) {
        backend.requireUiThread();
        requireOpen();
        backend.post(new BackendEvent.FullscreenChanged(this, fullscreen));
    }

    /// Queues a pointer move, as the platform would.
    ///
    /// The headless backend exists so the SPI's rules can be tested without a
    /// display; pointer events are no different, and a test that had to open a
    /// window to check a hover would not run in CI.
    public void movePointer(float x, float y) {
        movePointer(x, y, 0);
    }

    /// The same, with the platform's modifier bitmask — what a test driving a
    /// knob's fine-adjustment drag needs.
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
    /// carries the click they added up to.
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

    /// Queues the composition an input method is assembling.
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
