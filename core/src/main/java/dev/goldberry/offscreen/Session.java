package dev.goldberry.offscreen;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import dev.goldberry.Host;
import dev.goldberry.Overlay;
import dev.goldberry.OverlayLayer;
import dev.goldberry.image.Image;
import dev.goldberry.input.PointerRouter;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.input.hit.HitTest;
import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Modifiers;
import dev.goldberry.input.key.Shortcut;
import dev.goldberry.motion.Clock;
import dev.goldberry.paint.Clip;
import dev.goldberry.paint.overflow.Overrun;
import dev.goldberry.render.event.TimerQueue;
import dev.goldberry.render.model.LogicalPoint;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.WidgetRenderer;
import dev.goldberry.widget.root.WindowRoot;
import dev.goldberry.widget.semantics.Role;
import dev.goldberry.widget.semantics.Semantics;

/// A widget tree in a window that is not there, driven the way a user drives
/// one.
///
/// ```java
/// try (var session = Offscreen.of(800, 600)
///         .stylesheets(Controls.stylesheets(Theme.NORD_DARK))
///         .session(new SettingsScreen(settings))) {
///     session.click("apply");
///     session.type("Deploy Orc");
///     session.key(Key.ENTER);
///     assertEquals(List.of(), session.overruns());
/// }
/// ```
///
/// A [Filmstrip] with input. The tree is mounted once under a
/// [WindowRoot], with a [Host] of its own, so a widget that opens a dialog
/// with `Dialogs.show(host, …)` gets one drawn over the content — and a
/// press on that dialog's button goes where it would in a window. Every
/// event goes through the same [PointerRouter] a window's does, against the
/// rectangles the last frame laid out, so a press that a scrim would swallow
/// is swallowed here too.
///
/// ## A frame between every two events
///
/// A window paints between a press and the next press, and the second one is
/// answered against what the first one changed. So does this: each call that
/// sends input builds, lays out and captures the regions after it — without
/// painting, because the rectangles are what the next event needs — and
/// runs whatever timers are due. [#frame()] is when there is a picture.
///
/// ## Time
///
/// The clock is virtual and starts at zero, as a strip's does. Input does not
/// move it. [#advance(Duration)] does, stopping at each timer on the way — a
/// dialog's closing animation ends, and its answer arrives, at the moment it
/// would in a window and not a moment later.
///
/// ## The host
///
/// Overlays, timers, focus by id, accelerators and modality are real. There
/// are no popup windows — the answer SDL's `dummy` driver gives, which every
/// control falls back from — and no tray, web view, clipboard or file
/// dialogs. [Host#window()] throws.
///
/// Confined to the thread it was opened on. Must be closed.
///
/// Read more: [Driving input](https://goldberry.dev/docs/guide/testing.html#driving-input).
public final class Session implements AutoCloseable {

    /// How many build-and-lay-out turns one event may take to settle before
    /// the session stops asking. Each turn is a frame a window would paint;
    /// a tree still changing after ten is changing on its own, and the next
    /// call will see where it got to.
    private static final int MAX_TURNS = 10;

    private final Filmstrip strip;

    private final SessionHost host;

    private final TimerQueue timers;

    private final OverlayLayer layer;

    /// Opens a session over `root`. [Offscreen#session(Widget)] is the door,
    /// for [Filmstrip]'s reason.
    Session(
            Offscreen.Surface surface,
            @Nullable Fonts ownFonts,
            Fonts book,
            WidgetRenderer renderer,
            Clock.Virtual clock,
            Widget root) {

        this.timers = TimerQueue.over(clock);
        this.layer = new OverlayLayer();
        var router = new PointerRouter();
        this.host = new SessionHost(
                router,
                clock,
                timers,
                layer,
                book,
                surface.scale().toLogical(surface.size()),
                surface.scale().factor());
        var tree = new ElementTree(new WindowRoot(root, layer.overlays()), host);
        router.focusRoot(tree.root());
        this.strip = new Filmstrip(surface, ownFonts, renderer, clock, router, tree);
        try {
            settle();
        } catch (RuntimeException e) {
            // The strip is built and owns the tree; nobody else holds it yet.
            try {
                strip.close();
            } catch (RuntimeException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }
    }

    /// The picture at the current clock, of the tree as the last event left it.
    ///
    /// GPU layers are in it when the session was opened from a builder given
    /// [Offscreen#gpu(dev.goldberry.render.window.GpuSurface)], as they are in
    /// [Offscreen#render(Widget)]'s picture.
    ///
    /// @throws IllegalStateException if this session has been closed
    public Image frame() {
        settle();
        var picture = strip.frame();
        host.regions(strip.regions());
        return picture;
    }

    /// Moves the clock on by `by`, firing every timer due on the way at the
    /// time it was due, with a frame after each.
    ///
    /// What is already due fires first, at the current time: a timer with no
    /// delay that the last [#frame()] left behind is the usual one. Then the
    /// clock steps to each later timer in turn, in order, however the timers
    /// came to be pending. A chain that re-arms itself with no delay fires once
    /// per step and does not hold the clock back.
    ///
    /// @throws IllegalArgumentException if negative
    /// @throws IllegalStateException if this session has been closed
    public Session advance(Duration by) {
        Objects.requireNonNull(by, "by");
        requireOpen();
        if (by.isNegative()) {
            throw new IllegalArgumentException("a session advances forwards, and " + by + " does not");
        }
        var clock = strip.clock();
        var target = clock.nowMillis() + by.toNanos() / 1_000_000.0;
        settle();
        // Strictly after the clock: a timer due at or before it was fired by
        // the settle above, or is a chain that settle re-armed at the same
        // time, and stepping to it would not move the clock at all.
        for (var due = timers.nextDueMillisAfter(clock.nowMillis());
                due.isPresent() && due.getAsLong() <= target;
                due = timers.nextDueMillisAfter(clock.nowMillis())) {
            clock.set((double) due.getAsLong());
            settle();
        }
        clock.set(target);
        settle();
        return this;
    }

    /// Where the clock is, in milliseconds since the session opened.
    public double nowMillis() {
        return strip.nowMillis();
    }

    /// Whether anything in the tree is still moving — [Filmstrip#isAnimating()].
    public boolean isAnimating() {
        return strip.isAnimating();
    }

    /// Presses and releases the primary button on the node with this `id`.
    ///
    /// [#click(Element)] on [#byId(String)]'s answer.
    ///
    /// @throws NoSuchElementException if nothing with that id is in the tree,
    ///         was drawn, or is visible
    /// @throws IllegalStateException if the press would land on something else
    public Session click(String id) {
        return click(element(id));
    }

    /// Presses and releases the primary button on `element`.
    ///
    /// At the centre of where it was drawn, clipped to what is visible of it —
    /// its own box, or the outermost box drawn for it when it is a composite
    /// with none of its own. **Refused when something else would take the
    /// press** — a dialog's scrim, a toast, a sibling drawn over it — because a
    /// test that clicks a button a user could not click is a test of nothing.
    /// [#click(float, float)] presses wherever it is told.
    ///
    /// @throws NoSuchElementException if it was not drawn, or nothing of it is
    ///         visible
    /// @throws IllegalStateException if the press would land on something else
    public Session click(Element element) {
        var point = pointOn(element);
        aim(element, point);
        return press(point.x(), point.y());
    }

    /// Presses and releases the primary button at `(x, y)`, in the window's
    /// logical coordinates, on whatever is there.
    public Session click(float x, float y) {
        settle();
        router().pointerMoved(x, y);
        settle();
        return press(x, y);
    }

    /// Moves the pointer onto the centre of the node with this `id`.
    ///
    /// @throws NoSuchElementException if nothing with that id was drawn
    public Session hover(String id) {
        return hover(element(id));
    }

    /// Moves the pointer onto the centre of `element`.
    ///
    /// @throws NoSuchElementException if it was not drawn
    public Session hover(Element element) {
        var point = pointOn(element);
        return hover(point.x(), point.y());
    }

    /// Moves the pointer to `(x, y)`.
    public Session hover(float x, float y) {
        settle();
        router().pointerMoved(x, y);
        settle();
        return this;
    }

    /// Turns the wheel at `(x, y)` by `(dx, dy)` notches.
    public Session wheel(float x, float y, float dx, float dy) {
        settle();
        router().pointerMoved(x, y);
        router().pointerWheel(x, y, dx, dy);
        settle();
        return this;
    }

    /// Commits `text` to whatever has the keyboard, as one piece — what an input
    /// method does at the end of a composition, and what typing on a keyboard
    /// with no composition does one character at a time.
    public Session type(String text) {
        Objects.requireNonNull(text, "text");
        settle();
        router().textInput(text);
        settle();
        return this;
    }

    /// Presses and releases `key` with no modifiers.
    public Session key(Key key) {
        return key(key, Modifiers.NONE);
    }

    /// Presses and releases `key` with `modifiers` held. Accelerators fire, Tab
    /// moves focus, and the focused widget hears the rest.
    public Session key(Key key, Modifiers modifiers) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(modifiers, "modifiers");
        settle();
        router().keyPressed(key, modifiers, false);
        settle();
        router().keyReleased(key, modifiers);
        settle();
        return this;
    }

    /// Presses `key`, repeats the press `repeats` times as a platform does while
    /// a key is held down, and releases it. Each repeat reaches accelerators
    /// and the focused widget marked as one, which is how a test shows a toggle
    /// bound with `Repeat.IGNORE` does not flip while its key is held.
    ///
    /// @throws IllegalArgumentException if `repeats` is negative
    public Session hold(Key key, Modifiers modifiers, int repeats) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(modifiers, "modifiers");
        if (repeats < 0) {
            throw new IllegalArgumentException("a key is repeated zero or more times, not " + repeats);
        }
        settle();
        router().keyPressed(key, modifiers, false);
        settle();
        for (var i = 0; i < repeats; i++) {
            router().keyPressed(key, modifiers, true);
            settle();
        }
        router().keyReleased(key, modifiers);
        settle();
        return this;
    }

    /// [#hold(Key, Modifiers, int)] with no modifiers.
    public Session hold(Key key, int repeats) {
        return hold(key, Modifiers.NONE, repeats);
    }

    /// Presses and releases a key written the way a menu prints it —
    /// `"Ctrl+S"`, `"Escape"`.
    ///
    /// @throws IllegalArgumentException if the text names no key this toolkit has
    public Session key(String accelerator) {
        var shortcut = Shortcut.of(accelerator);
        return key(shortcut.key(), shortcut.modifiers());
    }

    /// Moves the keyboard focus to the node with this `id`, as a click on it
    /// would.
    ///
    /// @return whether focus moved
    public boolean focus(String id) {
        Objects.requireNonNull(id, "id");
        settle();
        var moved = router().focusById(id, false);
        settle();
        return moved;
    }

    /// What the last frame drew at `(x, y)`, topmost first — the deepest node
    /// there, whether or not a modal lets the pointer reach it.
    public Optional<Element> elementAt(float x, float y) {
        return HitTest.at(strip.regions(), x, y)
                .filter(Element.class::isInstance)
                .map(Element.class::cast);
    }

    /// The node with this `id`, wherever it is — in the content, in an overlay,
    /// drawn or not.
    public Optional<Element> byId(String id) {
        Objects.requireNonNull(id, "id");
        return find(strip.tree().root(), element -> id.equals(element.id()));
    }

    /// Every node that describes itself with `role`, in tree order.
    public List<Element> byRole(Role role) {
        Objects.requireNonNull(role, "role");
        var found = new ArrayList<Element>();
        collect(strip.tree().root(), role, found);
        return List.copyOf(found);
    }

    /// The node with `role` whose accessible name is `name` — "the button
    /// labelled Apply", which is how a user finds it.
    public Optional<Element> byRole(Role role, String name) {
        Objects.requireNonNull(name, "name");
        return byRole(role).stream()
                .filter(element ->
                        element.widget() instanceof Semantics semantics && name.equals(semantics.accessibleName()))
                .findFirst();
    }

    /// Where everything was laid out in the last frame, in paint order.
    public List<HitTest.Region> regions() {
        return strip.regions();
    }

    /// Every box laid out past the box it is in, as of the last frame.
    ///
    /// The whole tree, asked directly, and unaffected by what the process-wide
    /// overflow log has already said: an empty list here is evidence.
    public List<Overrun> overruns() {
        requireOpen();
        return strip.render().overruns();
    }

    /// What has the keyboard, if anything.
    public Optional<Element> focused() {
        return Optional.ofNullable(router().focused());
    }

    /// What the pointer is over, if anything.
    public Optional<Element> hovered() {
        return Optional.ofNullable(router().hovered());
    }

    /// What is floating over the content — an open dialog is one of these.
    public List<Overlay> overlays() {
        return layer.current();
    }

    /// The host the tree was built with: what `BuildContext.host()` answers, and
    /// what to hand `Dialogs.show` from a test.
    public Host host() {
        return host;
    }

    /// The router every event goes through, for the gesture this class does
    /// not spell out — a drag, a right-click, a double click.
    ///
    /// Call [#hover(float, float)] or any other input method afterwards, or
    /// [#frame()], to give the tree its next frame.
    public PointerRouter router() {
        requireOpen();
        return strip.router();
    }

    /// Unmounts the tree and releases everything the session was keeping.
    /// Idempotent.
    @Override
    public void close() {
        strip.close();
    }

    private Session press(float x, float y) {
        var router = router();
        router.pointerPressed(x, y, PointerEvent.Button.PRIMARY, 1);
        settle();
        router.pointerReleased(x, y, PointerEvent.Button.PRIMARY, 1);
        settle();
        return this;
    }

    /// Moves the pointer to `point` and refuses if what it lands on is not
    /// `target` or something inside it.
    ///
    /// The router's answer and not the hit test's, because the router is what
    /// a press goes to: it leaves out everything behind a modal.
    private void aim(Element target, LogicalPoint point) {
        router().pointerMoved(point.x(), point.y());
        settle();
        var under = router().hovered();
        if (under != null && within(under, target)) {
            return;
        }
        throw new IllegalStateException("a press on " + describe(target) + " at " + point + " would land on "
                + (under == null ? "nothing the pointer may reach" : describe(under))
                + " instead; " + topmost(point));
    }

    private String topmost(LogicalPoint point) {
        return elementAt(point.x(), point.y())
                .map(element -> "the topmost node there is " + describe(element))
                .orElse("nothing is drawn there");
    }

    private static String describe(Element element) {
        var type = element.type();
        var id = element.id();
        var name = type == null ? element.widget().getClass().getSimpleName() : "`" + type + "`";
        return id == null ? name : name + " `#" + id + "`";
    }

    /// Whether `node` is `ancestor` or somewhere under it.
    private static boolean within(Element node, Element ancestor) {
        for (var at = node; at != null; at = at.parent() instanceof Element parent ? parent : null) {
            if (at == ancestor) {
                return true;
            }
        }
        return false;
    }

    private Element element(String id) {
        Objects.requireNonNull(id, "id");
        settle();
        return byId(id).orElseThrow(() -> new NoSuchElementException("nothing with id `" + id + "` is in the tree"));
    }

    /// The centre of what is visible of `element`: its own box, or the
    /// outermost one drawn under it. Paint order puts a parent before its
    /// children, so the first region found is the outermost.
    private LogicalPoint pointOn(Element element) {
        Objects.requireNonNull(element, "element");
        settle();
        var drawn = false;
        for (var region : strip.regions()) {
            if (!(region.owner() instanceof Element owner) || !within(owner, element)) {
                continue;
            }
            drawn = true;
            var visible = visible(region);
            if (visible != null) {
                return new LogicalPoint(visible.left() + visible.width() / 2, visible.top() + visible.height() / 2);
            }
        }
        throw new NoSuchElementException(
                describe(element) + (drawn ? " was drawn and none of it is visible" : " is not drawn in this session"));
    }

    /// What of `region` survives its clip, or null when nothing does.
    private static @Nullable LogicalRect visible(HitTest.Region region) {
        var painted = region.painted();
        var clip = region.clip().intersect(Clip.of(painted.left(), painted.top(), painted.width(), painted.height()));
        if (clip.isEmpty()) {
            return null;
        }
        return LogicalRect.of((float) clip.left(), (float) clip.top(), (float) clip.width(), (float) clip.height());
    }

    /// Builds and lays out until nothing is left to do: what a window's frames
    /// do between two events, with the timers that came due in between.
    private void settle() {
        requireOpen();
        for (var turn = 0; turn < MAX_TURNS; turn++) {
            strip.pass();
            host.regions(strip.regions());
            var fired = timers.fireDue();
            if (!fired && !strip.tree().needsBuild()) {
                return;
            }
        }
    }

    private void requireOpen() {
        if (strip.isClosed()) {
            throw new IllegalStateException("this Session has been closed; its tree is unmounted");
        }
    }

    private static Optional<Element> find(Element from, Predicate<Element> test) {
        if (test.test(from)) {
            return Optional.of(from);
        }
        for (var child : from.children()) {
            var found = find(child, test);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private static void collect(Element from, Role role, List<Element> into) {
        if (from.widget() instanceof Semantics semantics && semantics.role() == role) {
            into.add(from);
        }
        for (var child : from.children()) {
            collect(child, role, into);
        }
    }

    @Override
    public String toString() {
        return "Session[" + strip + ", " + layer.current().size() + " overlay(s)]";
    }
}
