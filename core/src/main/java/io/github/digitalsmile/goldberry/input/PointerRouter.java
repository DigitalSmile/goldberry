package io.github.digitalsmile.goldberry.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.bind.Subscription;
import io.github.digitalsmile.goldberry.css.select.Selector.PseudoClass;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.event.TextEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.input.handler.Located;
import io.github.digitalsmile.goldberry.input.handler.Measured;
import io.github.digitalsmile.goldberry.input.hit.Extent;
import io.github.digitalsmile.goldberry.input.hit.HitTest;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.input.key.Mod;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.input.key.Shortcut;
import io.github.digitalsmile.goldberry.render.Cursor;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// Turns pointer positions into events, pseudo-classes and focus.
///
/// Holds the small amount of state that input needs between frames — who is
/// hovered, who is pressed, who has focus — and it holds it against **elements**,
/// which is why the element tree exists
/// (ADR-0052):
/// a widget is rebuilt constantly and could not remember any of this.
///
/// Confined to the UI thread.
public final class PointerRouter {

    /// One per window. Its state is that window's pointer and focus.
    public PointerRouter() {}

    private List<HitTest.Region> regions = List.of();

    private @Nullable Element hovered;
    private @Nullable Element pressed;
    private @Nullable Element focused;
    private boolean focusFromKeyboard;

    /// Where the keyboard goes back to when the thing holding it leaves the tree
    /// — §7's "restores focus on close".
    ///
    /// **The first state the focus trap has held**, and it was deferred for as
    /// long as it could be. Everything else about the trap is a question about
    /// the tree, asked fresh: [#deepestModal] walks it on every focus change,
    /// which is exactly why a dialog opened from inside a dialog gives the first
    /// one back for nothing when it unmounts. A remembered element cannot be
    /// derived that way — what had focus before a modal opened is a fact about
    /// the *past*, and the tree does not record it
    /// (ADR-0180).
    ///
    /// So it is kept to one slot, written at exactly one moment — the trap taking
    /// focus — and it is allowed to go stale on purpose: [#refocus] drops it the
    /// moment what it points at leaves the tree, rather than anything having to
    /// keep it true.
    private @Nullable Element restoreTo;

    /// Whether [#restoreTo] had the ring when it lost focus, so that giving the
    /// keyboard back gives back the state it was in. A dialog dismissed with
    /// `Escape` should leave the ring where the user last saw it.
    private boolean restoreFromKeyboard;

    /// Who is receiving pointer events regardless of where the pointer is.
    ///
    /// §7.1 asks for pointer capture on drag, and a drag is exactly the case
    /// where the pointer leaves the thing it is dragging: a slider whose thumb
    /// stops moving when the pointer wanders off the track is the bug this
    /// prevents, and so is a button that never learns the press it started ended
    /// somewhere else.
    private @Nullable Element captured;

    /// Whether [#captured] was taken by the press rather than asked for.
    ///
    /// An implicit capture is released by the matching release; an explicit one
    /// is not, because the widget that asked for it is the only thing that knows
    /// when its gesture is over.
    private boolean capturedImplicitly;

    /// Where the button went down, or `NaN` between presses.
    ///
    /// A drag gesture needs its origin and a widget cannot hold one: widgets are
    /// values rebuilt every frame, so there is nowhere on a `toggle` for "the
    /// press started here" to live. The router already spans exactly that
    /// interval — it takes an implicit capture on the press and drops it on the
    /// release ([ADR-0058]) — so it is both the only thing that can know and the
    /// thing whose lifetime already matches. Read through
    /// [PointerEvent#dragX()].
    private float pressOriginX = Float.NaN;
    private float pressOriginY = Float.NaN;

    /// Where the pointer was last seen, or `NaN` when it is not in this window.
    ///
    /// A **fourth** position field, and it is the only one that outlives a
    /// gesture: the two above span a press-to-release and are `NaN` outside one,
    /// which is precisely what makes them useless for the question this answers.
    /// [#updateRegions] needs a point to ask [#updateCursor] about, and "where the
    /// pointer is right now" is a fact about the window rather than about a drag
    /// ([ADR-0237]).
    ///
    /// `NaN` is the whole of "we do not know", and it means it twice: before the
    /// pointer has ever arrived, and after [#pointerExited] — which is another
    /// window's pointer or none at all, and either way not a place to ask about.
    private float pointerX = Float.NaN;
    private float pointerY = Float.NaN;

    /// What the pressed control's value was when the gesture began, or `NaN`.
    ///
    /// The third gesture-origin field, and it exists because two of the origins a
    /// drag can have are not points. A slider reads a *position* off the pointer
    /// and needs no history; a knob's drag is a **rate** — 200 logical pixels of
    /// travel is its whole range (§3) — so where it lands depends on where it
    /// started, and by the second frame the value has already moved.
    ///
    /// The router does not know or care what the number means: it asks
    /// [Handles#gestureAnchor()] once on the press and hands the answer back on
    /// every event of the gesture ([ADR-0089]). Read through
    /// [PointerEvent#anchor()].
    private double pressOriginValue = Double.NaN;

    /// The modifiers held when the button went down — see
    /// [PointerEvent#gestureModifiers()].
    private Modifiers pressOriginModifiers = Modifiers.NONE;

    /// Whether anything changed that a stylesheet could react to.
    private boolean stylesDirty;

    /// Replaces the hit-test snapshot, normally right after a frame is painted.
    public void updateRegions(List<HitTest.Region> regions) {
        this.regions = List.copyOf(Objects.requireNonNull(regions, "regions"));
        // Found once per frame and kept beside the regions, which is the same
        // rule ADR-0054 already states for hit testing: input is answered against
        // the frame that was painted, so the tree it was painted from is the tree
        // to ask. Walking for a modal on every pointer motion would be a tree walk
        // per mouse move ([ADR-0232]).
        modal = deepestModal(focusRoot);
        refocus();
        notifyMeasured();
        notifyLocated();
        // The shape follows the frame and not only the pointer ([ADR-0237]). A
        // control that disables itself under a still pointer resolves
        // `cursor: not-allowed` in the frame it is painted for, and nothing else
        // would ever ask -- the user deciding whether to click is the one who is
        // not moving. Last, so it reads the tree the notifications above have
        // finished with.
        if (!Float.isNaN(pointerX)) {
            updateCursor(pointerX, pointerY);
        }
        restate();
    }

    /// Re-asks the two pointer pseudo-classes against the tree that was painted.
    ///
    /// The other half of the same frame hook, and the half `mark` already
    /// believed it had ([ADR-0237]). Its comment says a control "that was hovered
    /// before it became disabled does not keep the state, which is a real
    /// sequence, because a button commonly disables itself in its own press
    /// handler while the pointer is still over it" — and that was **false**, for
    /// a reason no reader of `mark` could see: clearing is not suppressed, but
    /// nothing was calling it. `updateHover` is the only caller and it returns
    /// early when the element under the pointer has not changed, so the wash
    /// survived every subsequent move *within* the control and went away only
    /// when the pointer left it.
    ///
    /// `mark(…, true)` is the whole implementation because `mark` already knows
    /// the rule: it turns a set into a clear on a disabled element, so re-asserting
    /// what the pointer is over sets it where the control is live and takes it
    /// away where it is not. `docs/design-system.md` §2.1 is what makes that
    /// non-discretionary — a disabled control that still lightened under the
    /// pointer would be telling the user it can be used.
    ///
    /// **No `ENTERED` or `EXITED` is emitted**, deliberately. Nothing entered or
    /// exited anything: the pointer has not moved and the element under it is the
    /// one that was there. This is about what a control looks like, which is the
    /// same line `mark` itself draws.
    private void restate() {
        for (var element : chain(hovered)) {
            mark(element, PseudoClass.HOVER, true);
        }
        for (var element : chain(pressed)) {
            mark(element, PseudoClass.ACTIVE, true);
        }
    }

    /// Puts the keyboard back when whatever had it has left the tree.
    ///
    /// ## What this fixes, which is more than it sounds
    ///
    /// [Element#unmount] tells the tree and nothing else, so a router whose
    /// focused element was inside a closing dialog went on **holding it** — an
    /// unmounted element, receiving key events, keeping its whole dead subtree
    /// reachable. "Focus is not restored" was the half of that anybody could see.
    ///
    /// So this is two rules, and the first has nothing to do with dialogs:
    ///
    ///  1. **The router never holds an element that is not in the tree.** A tab
    ///     that switched, a list that shortened and a dialog that closed all end
    ///     the same way, and letting go is right for all three.
    ///  2. **If there is somewhere to put the keyboard back, put it there** —
    ///     §7's "restores focus on close", from the one slot [#restoreTo] keeps.
    ///
    /// ## Called once a frame, and separately callable
    ///
    /// From [#updateRegions], which every window runs after it paints. Public and
    /// not folded into it because the question is about the **element tree**
    /// rather than about the frame: a test that closes a dialog without drawing
    /// anything still needs the answer, and passing an empty region list to get it
    /// would throw the hit-test snapshot away.
    ///
    /// Being a frame late is not a compromise here. Nothing can press a key
    /// between a tree flushing and the frame it produces, which is the same
    /// argument [Measured] makes ([ADR-0117]).
    public void refocus() {
        // What we were going to hand back to may itself have gone -- a dialog
        // opened from a row of a list that the dialog's own action then removed.
        if (restoreTo != null && !restoreTo.isMounted()) {
            restoreTo = null;
        }
        if (focused == null || focused.isMounted()) {
            return;
        }
        // Reachability is checked because a *nested* modal closing leaves an
        // outer one still up, and the keyboard may not leave it. The slot is
        // kept rather than spent in that case, so the answer survives until the
        // outer modal goes too.
        if (restoreTo != null && isFocusable(restoreTo) && isReachable(restoreTo)) {
            var target = restoreTo;
            restoreTo = null;
            focus(target, restoreFromKeyboard);
            return;
        }
        // Nothing to go back to, so let go. `focus(null, …)` is the router's own
        // word for "nobody", and it is what a press on empty space already does —
        // a modal still being up does not change that, because a null focus is
        // reachable from anywhere.
        focus(null, false);
    }

    /// The window's own rectangle, for a [Located] widget nothing clips.
    ///
    /// Set from the frame rather than assumed, because "nothing clips me" has to
    /// resolve to a real rectangle for `affix` to pin against — see
    /// [Located#located].
    private LogicalRect windowBounds = LogicalRect.of(0, 0, 0, 0);

    /// Tells the router how big the window is, for the rectangle above.
    public void windowBounds(LogicalRect bounds) {
        this.windowBounds = bounds == null ? LogicalRect.of(0, 0, 0, 0) : bounds;
    }

    /// What each [Measured] widget was told last time, so an unchanging window
    /// notifies nothing.
    ///
    /// Keyed by element identity and rebuilt from the regions each frame, which
    /// also disposes of the entries for elements that left the tree — a map that
    /// held them would be a leak of exactly the shape [Element#unmount] exists to
    /// prevent.
    private java.util.Map<Element, Measurement> measuredBounds = java.util.Map.of();

    /// The pair a [Measured] widget was last told, so both halves are compared.
    ///
    /// Both, and not just the bounds: a scroll view whose viewport is unchanged
    /// while its content grew is exactly the case a scrollbar has to redraw for,
    /// and a check on the outer rectangle alone would miss every one of them.
    private record Measurement(Widget widget, Extent bounds, Extent part) {

        boolean sameAs(Measurement other) {
            return other != null && other.widget == widget && other.bounds.equals(bounds) && other.part.equals(part);
        }
    }

    /// Tells every widget that asked what the frame just laid it out as.
    ///
    /// Here because this is the one place that holds the painted rectangles and
    /// the one call every window makes once per frame — the same argument that
    /// put [#localFor] here rather than in the widget ([ADR-0117]).
    private void notifyMeasured() {
        java.util.IdentityHashMap<Element, Measurement> next = null;
        for (var region : regions) {
            if (!(region.owner() instanceof Element element) || !(element.widget() instanceof Measured measured)) {
                continue;
            }
            var bounds = new Extent(region.width(), region.height());
            var part = bounds;
            // `partOf` returns an Element or null, so `instanceof Element` here
            // was a null check wearing a pattern's clothes -- it read as though
            // the type were in question when only the presence ever was.
            var named = element.widget() instanceof Handles handles && handles.localPart() != null
                    ? partOf(element, handles.localPart())
                    : null;
            if (named != null) {
                var namedExtent = extentOf(named);
                if (namedExtent != Extent.NONE) {
                    part = namedExtent;
                }
            }
            // The widget as well as the numbers, for [Location]'s reason: a node
            // that has been rebuilt may act differently on measurements it has
            // already been given.
            var measurement = new Measurement(element.widget(), bounds, part);
            if (next == null) {
                next = new java.util.IdentityHashMap<>();
            }
            next.put(element, measurement);
            // Only on a change: a still window must notify nothing, or §1.7's
            // idle frame loop would be woken every frame by a widget being told
            // what it already knew.
            if (measurement.sameAs(measuredBounds.get(element))) {
                continue;
            }
            measured.measured(bounds, part);
        }
        measuredBounds = next == null ? java.util.Map.of() : next;
    }

    /// What each [Located] widget was last told, so a still window notifies
    /// nothing — [#measuredBounds]'s reason exactly.
    private java.util.Map<Element, Location> locations = java.util.Map.of();

    /// What a [Located] widget was last told, **and which widget was told it**.
    ///
    /// The widget matters as much as the rectangles. A node that has just been
    /// rebuilt may want something different from the same numbers — a header that
    /// has this moment been asked to scroll itself into view is in exactly that
    /// position, and comparing rectangles alone would decide it had nothing to
    /// hear and never call it ([ADR-0119]).
    ///
    /// Compared by **identity**, which keeps §1.7's idle guarantee intact: an
    /// element that was not rebuilt holds the same widget instance, so a still
    /// window still notifies nobody.
    private record Location(Widget widget, LogicalRect self, LogicalRect clip) {

        boolean sameAs(Location other) {
            return other != null && other.widget == widget && other.self.equals(self) && other.clip.equals(clip);
        }
    }

    /// Tells every [Located] widget where the frame just put it.
    ///
    /// Separate from [#notifyMeasured] rather than folded into it, because the
    /// two answer different questions and almost nothing wants both: a scrollbar
    /// needs a size and does not care where it is, and an `affix` needs a
    /// position and does not care how big it is. One walk each, over the nodes
    /// that asked ([ADR-0119]).
    private void notifyLocated() {
        java.util.IdentityHashMap<Element, Location> next = null;
        for (var region : regions) {
            if (!(region.owner() instanceof Element element) || !(element.widget() instanceof Located located)) {
                continue;
            }
            var location = new Location(element.widget(), paintedRect(region), clipRect(region));
            if (next == null) {
                next = new java.util.IdentityHashMap<>();
            }
            next.put(element, location);
            if (location.sameAs(locations.get(element))) {
                continue;
            }
            located.located(location.self(), location.clip());
        }
        locations = next == null ? java.util.Map.of() : next;
    }

    /// Where `region` was actually painted, which is not where it was laid out
    /// when something above it was transformed.
    ///
    /// A region stores the layout rectangle and the **inverse** of the matrix,
    /// because undoing a transform is what hit testing needs and inverting once
    /// while painting is what stops two inversions disagreeing (ADR-0068). Going
    /// forwards means inverting it back, which is exact for the translations this
    /// is ever asked about and is only done for the handful of nodes that asked
    /// to be told where they are.
    private static LogicalRect paintedRect(HitTest.Region region) {
        return region.painted();
    }

    /// What confines `region`, or the window when nothing does.
    private LogicalRect clipRect(HitTest.Region region) {
        var clip = region.clip();
        if (clip == null || clip.isNone()) {
            return windowBounds;
        }
        return LogicalRect.of((float) clip.left(), (float) clip.top(), (float) clip.width(), (float) clip.height());
    }

    /// Told when the hovered or the focused node changes — see [#onPointingChanged].
    ///
    /// A `CopyOnWriteArrayList` for one reason and it is not threads: a listener
    /// may cancel itself, or another, from inside a notification, and iterating a
    /// snapshot is what makes that safe without a copy per notification. Hovers
    /// are frequent and registrations are not.
    private final List<Runnable> pointingListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    /// Called when the pointer moves to a different node, or focus does.
    ///
    /// The first caller was the thing that opens a `tooltip`:
    /// `docs/core-widgets.md` §7 attaches one by attribute to any widget and shows
    /// it "on hover *and on keyboard focus* after delay", so something above the
    /// router has to know when either moved and start a timer. The router itself
    /// opens nothing — it has no window and no notion of one ([ADR-0105]).
    ///
    /// ## Every listener is told, and none can stop another
    ///
    /// This was one slot, which made a second registration silently drop the
    /// first. The question that kept it a slot was what it means for two things to
    /// react to one hover, and the answer is that **this is a notification and not
    /// an event**: there is nothing to consume, no order that matters, and no way
    /// for one listener to change what another sees. Each is told and reads
    /// [#hovered()] or [#focused()] for itself.
    ///
    /// An event — one that could be consumed, or that carried a target — would be
    /// the thing worth refusing, because then a second listener really would be a
    /// second thing deciding what a hover means ([ADR-0230]).
    ///
    /// @return a registration to close; a listener that outlives what it points at
    ///         is the leak this exists to prevent
    public Subscription onPointingChanged(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        pointingListeners.add(listener);
        return () -> pointingListeners.remove(listener);
    }

    private void notifyPointing() {
        for (var listener : pointingListeners) {
            listener.run();
        }
    }

    public @Nullable Element hovered() {
        return hovered;
    }

    public @Nullable Element pressed() {
        return pressed;
    }

    public @Nullable Element focused() {
        return focused;
    }

    /// The shape the pointer is currently showing.
    private Cursor cursor = Cursor.DEFAULT;
    private Consumer<Cursor> cursorSink = c -> {};

    /// Where to send the cursor shape when it changes (§7.3).
    ///
    /// A callback rather than a backend window, so the router still knows nothing
    /// about the platform — [io.github.digitalsmile.goldberry.Window] wires this
    /// to the backend, and a test wires it to a list.
    public void onCursorChange(Consumer<Cursor> sink) {
        this.cursorSink = Objects.requireNonNull(sink, "sink");
        sink.accept(cursor);
    }

    /// What the pointer currently looks like.
    public Cursor cursor() {
        return cursor;
    }

    /// Whether a pseudo-class changed since this was last asked.
    ///
    /// §8 makes invalidation coarse: a pseudo-class change recomputes the
    /// subtree. This is the flag that says one happened, so a frame loop can
    /// restyle only when it must — and clearing on read means "did anything
    /// change since the last frame" is the exact question it answers.
    public boolean takeStylesDirty() {
        var dirty = stylesDirty;
        stylesDirty = false;
        return dirty;
    }

    /// The pointer moved to a logical position.
    public void pointerMoved(float x, float y) {
        pointerMoved(x, y, Modifiers.NONE);
    }

    /// The same, with the modifier keys the platform reported at the time.
    public void pointerMoved(float x, float y, Modifiers modifiers) {
        pointerAt(x, y);
        var under = elementAt(x, y);
        updateHover(under, x, y);
        updateCursor(x, y);
        var target = captured != null ? captured : under;
        if (target != null) {
            dispatch(new PointerEvent(
                    PointerEvent.Kind.MOVED, x, y, null, 0, pressOriginX, pressOriginY, modifiers, target));
        }
    }

    /// The pointer left the window entirely.
    ///
    /// Capture survives it. A drag that leaves the window and comes back is one
    /// gesture, and the platform keeps sending the motion — releasing here would
    /// drop the second half of every drag that overshoots an edge.
    public void pointerExited() {
        pointerAt(Float.NaN, Float.NaN);
        updateHover(null, Float.NaN, Float.NaN);
        setCursor(Cursor.DEFAULT);
    }

    /// Remembers where the pointer is, for [#updateRegions].
    ///
    /// Called from every entry point that carries a position rather than from
    /// [#pointerMoved] alone: a press, a release and a wheel all state where the
    /// pointer is, and a window whose first event was a click would otherwise
    /// paint frame after frame with nowhere to ask about.
    private void pointerAt(float x, float y) {
        pointerX = x;
        pointerY = y;
    }

    /// A button went down.
    public void pointerPressed(float x, float y, PointerEvent.Button button, int clickCount) {
        pointerPressed(x, y, button, clickCount, Modifiers.NONE);
    }

    /// The same, with modifiers.
    public void pointerPressed(
            float x, float y, PointerEvent.@Nullable Button button, int clickCount, Modifiers modifiers) {
        pointerAt(x, y);
        var target = elementAt(x, y);
        updateHover(target, x, y);
        if (target == null) {
            // A press on nothing still moves focus off whatever had it, which is
            // what clicking the background is for -- **unless a modal is in
            // force**, where "nothing" is the application behind the dialog and
            // dropping focus there would empty a trap the next frame has to
            // refill ([ADR-0232]).
            if (modal == null) {
                focus(null, false);
            }
            return;
        }

        setPressed(target);
        // Recorded before the dispatch, so the PRESSED event itself already
        // reports a zero drag rather than NaN -- a handler that reads dragX on
        // every pointer event should not have to special-case the first one.
        pressOriginX = x;
        pressOriginY = y;
        pressOriginValue = anchorFor(target);
        pressOriginModifiers = modifiers;
        if (captured == null) {
            // Implicit capture: from here until the button comes up, this
            // element gets the pointer wherever it goes (§7.1).
            captured = target;
            capturedImplicitly = true;
        }
        focusFromPress(target);
        dispatch(new PointerEvent(
                PointerEvent.Kind.PRESSED, x, y, button, clickCount, pressOriginX, pressOriginY, modifiers, target));
    }

    /// A button came up.
    ///
    /// The release goes to whoever captured the press, not to whatever happens to
    /// be under the pointer now. A button pressed and then released 200 pixels
    /// away has not been clicked, but it has certainly stopped being pressed, and
    /// it is the only thing that can know the difference.
    public void pointerReleased(float x, float y, PointerEvent.Button button, int clickCount) {
        pointerReleased(x, y, button, clickCount, Modifiers.NONE);
    }

    /// The same, with modifiers.
    public void pointerReleased(
            float x, float y, PointerEvent.@Nullable Button button, int clickCount, Modifiers modifiers) {
        pointerAt(x, y);
        var under = elementAt(x, y);
        var target = captured != null ? captured : under;
        // Read before `setPressed(null)` clears it: whether this was a click is a
        // question about who the press went to.
        var wasPressed = pressed;
        updateHover(under, x, y);
        setPressed(null);
        if (capturedImplicitly) {
            releasePointer();
        }
        updateCursor(x, y);
        // Read into locals and cleared at the end: the release and the click are
        // the last two events of the gesture and both want its origin, and a
        // handler that fires another press from inside one -- which
        // `ActionRegistry`-driven code does -- must not have this overwritten under it.
        var originX = pressOriginX;
        var originY = pressOriginY;
        pressOriginX = Float.NaN;
        pressOriginY = Float.NaN;
        // Deliberately *not* cleared here. The release and the click are the last
        // two events of the gesture and both are dispatched below, and a knob
        // reads its anchor on the release to decide whether the drag moved at
        // all. Cleared after them, beside the point origins it belongs with.
        if (target == null) {
            return;
        }
        dispatch(new PointerEvent(
                PointerEvent.Kind.RELEASED, x, y, button, clickCount, originX, originY, modifiers, target));

        // A click is a press and a release on the same node, which is not the
        // same thing as a release: dragging off a button and letting go is how a
        // user cancels, and every control would otherwise have to work that out
        // for itself from a release it cannot locate. Synthesized here, from
        // pointer flow, exactly as §7.1 says the synthetic events are.
        //
        // "On the same node" means the release landed on the pressed element or
        // inside it -- releasing on a button's own label is a click on the
        // button.
        if (button == PointerEvent.Button.PRIMARY
                && wasPressed != null
                && chain(under).contains(wasPressed)) {
            dispatch(new PointerEvent(
                    PointerEvent.Kind.CLICKED, x, y, button, clickCount, originX, originY, modifiers, wasPressed));
        }
        pressOriginValue = Double.NaN;
        pressOriginModifiers = Modifiers.NONE;
    }

    /// The first non-`NaN` [Handles#gestureAnchor()] on `target`'s chain.
    ///
    /// Deepest-first, which is dispatch order: a press that lands on a control's
    /// *part* -- a knob's arc, a slider's thumb -- must be anchored by the
    /// control that will handle it, and the part itself has no value to report
    /// ([ADR-0089]).
    private static double anchorFor(Element target) {
        for (var element : chain(target)) {
            if (element.widget() instanceof Handles handles) {
                var anchor = handles.gestureAnchor();
                if (!Double.isNaN(anchor)) {
                    return anchor;
                }
            }
        }
        return Double.NaN;
    }

    /// The wheel turned, or a touchpad scrolled, at a logical position.
    ///
    /// Deltas are in lines and positive is down and right — see
    /// [PointerEvent#deltaY()]. It travels the same capture/bubble path as every
    /// other pointer event, so a scroll view consumes it and an ancestor scroll
    /// view does not also scroll.
    public boolean pointerWheel(float x, float y, float deltaX, float deltaY) {
        return pointerWheel(x, y, deltaX, deltaY, Modifiers.NONE);
    }

    /// The same, with modifiers — `Shift` for a fine step on a knob (§3).
    public boolean pointerWheel(float x, float y, float deltaX, float deltaY, Modifiers modifiers) {
        return pointerWheel(x, y, deltaX, deltaY, (int) deltaX, (int) deltaY, modifiers);
    }

    /// The same, stating the platform's accumulated detents — see
    /// [PointerEvent#ticksY()]. The backend's entry point.
    /// Returns whether anything consumed it, which is what a caller needs to
    /// know to scroll something else — the reason [#keyPressed] reports the same
    /// thing. §2.4's scroll chaining is exactly this: an inner scroller consumes
    /// until its edge and then stops, and what is above it takes over
    /// ([ADR-0116]).
    public boolean pointerWheel(
            float x, float y, float deltaX, float deltaY, int ticksX, int ticksY, Modifiers modifiers) {
        pointerAt(x, y);
        var target = captured != null ? captured : elementAt(x, y);
        if (target == null) {
            return false;
        }
        var event = PointerEvent.wheel(x, y, deltaX, deltaY, ticksX, ticksY, modifiers, target);
        dispatch(event);
        return event.isConsumed();
    }

    /// Sends every pointer event to `element` until [#releasePointer()].
    ///
    /// What a slider asks for when its thumb is grabbed. A press already takes
    /// capture implicitly, so this is for a widget that wants to keep it past the
    /// release — a drag that continues until Escape, say.
    public void capturePointer(Element element) {
        captured = Objects.requireNonNull(element, "element");
        capturedImplicitly = false;
    }

    /// Ends capture. Harmless when nothing has it.
    public void releasePointer() {
        captured = null;
        capturedImplicitly = false;
    }

    /// Who has the pointer, or null.
    public @Nullable Element captured() {
        return captured;
    }

    /// Moves focus, recording whether it came from the keyboard.
    ///
    /// §7.2 keeps `:focus` and `:focus-visible` distinct: the focus ring renders
    /// only for keyboard focus. Both are set here so a stylesheet can tell them
    /// apart without input having to know what a ring is.
    public void focus(@Nullable Element element, boolean fromKeyboard) {
        if (element != null && !isFocusable(element)) {
            return;
        }
        // §7's focus trap, and the whole of it: while something modal is mounted,
        // the focused node is inside it. Enforced here rather than at each of the
        // routes that move focus, because "each of the routes" is Tab, a press, a
        // roving arrow, a control focusing itself and whatever asks next -- and a
        // trap that covered four of five would be no trap at all.
        //
        // This is also what focuses a dialog when it opens: the first frame after
        // it mounts, something asks for focus somewhere outside it, and the
        // request is redirected to the first thing inside.
        var modal = deepestModal(focusRoot);
        if (modal != null && !isReachable(element)) {
            var inside = firstFocusableIn(modal);
            if (inside == null || inside == element) {
                return;
            }
            // The one moment [#restoreTo] is written: the trap is taking the
            // keyboard off something outside the modal, and that something is
            // what a close should give it back to.
            //
            // Guarded on being empty, so focus moving *within* a modal never
            // overwrites where it came from — and so a second, nested modal does
            // not either. One slot means the outermost answer wins, which is the
            // one the user will still be looking at when everything has closed.
            if (restoreTo == null && focused != null && !isReachable(focused)) {
                restoreTo = focused;
                restoreFromKeyboard = focusFromKeyboard;
            }
            element = inside;
        }
        if (focused == element && focusFromKeyboard == fromKeyboard) {
            return;
        }
        var lost = focused;
        if (focused != null) {
            mark(focused, PseudoClass.FOCUS, false);
            mark(focused, PseudoClass.FOCUS_VISIBLE, false);
        }
        focused = element;
        focusFromKeyboard = fromKeyboard;
        notifyPointing();
        if (focused != null) {
            mark(focused, PseudoClass.FOCUS, true);
            if (fromKeyboard) {
                mark(focused, PseudoClass.FOCUS_VISIBLE, true);
            }
        }
        // After both pseudo-classes are settled, because a handler may look at
        // them -- and after `focused` is reassigned, because a handler that
        // raises a change will have this router asked about focus again before
        // it returns.
        if (lost != null && lost != focused) {
            notifyFocus(lost, false, fromKeyboard);
        }
        if (focused != null) {
            notifyFocus(focused, true, fromKeyboard);
        }
        notifyFocusWithin(lost, focused, fromKeyboard);
    }

    private static void notifyFocus(Element element, boolean gained, boolean fromKeyboard) {
        if (element.widget() instanceof Handles handles) {
            handles.onFocusChanged(gained, fromKeyboard);
        }
    }

    /// Tells the containers that gained or lost the keyboard — CSS's
    /// `:focus-within`, as a notification ([Handles#onFocusWithin]).
    ///
    /// **The difference of the two chains, not both of them.** Focus moving
    /// between two controls inside one `field` leaves that field's subtree
    /// focused throughout, and a container told "left" and then "entered" for a
    /// move that never crossed its boundary would validate, pause or collapse
    /// for no reason. So an ancestor of both is told nothing.
    ///
    /// Walking two chains on every focus change is the cost, and it is a walk to
    /// the root of a widget tree per keystroke that moves focus — the same order
    /// as the pointer dispatch that already happens per motion.
    private static void notifyFocusWithin(@Nullable Element lost, Element gained, boolean fromKeyboard) {
        if (lost == gained) {
            return;
        }
        var left = lost == null ? List.<Element>of() : chain(lost);
        var entered = gained == null ? List.<Element>of() : chain(gained);
        var shared = new java.util.HashSet<>(left);
        shared.retainAll(new java.util.HashSet<>(entered));

        // Deepest first for the leave and deepest first for the enter, which is
        // the order the pointer's bubble phase uses -- a container that reacts by
        // rebuilding should see its children settle before it does.
        for (var element : left) {
            if (!shared.contains(element) && element.widget() instanceof Handles handles) {
                handles.onFocusWithin(false, fromKeyboard);
            }
        }
        for (var element : entered) {
            if (!shared.contains(element) && element.widget() instanceof Handles handles) {
                handles.onFocusWithin(true, fromKeyboard);
            }
        }
    }

    /// The root of the focusable tree, for Tab traversal.
    ///
    /// Set by whoever owns the widget tree. Without it, focus still works by
    /// pointer -- traversal is the only thing that needs to enumerate.
    private @Nullable Element focusRoot;

    public void focusRoot(Element root) {
        this.focusRoot = root;
    }

    /// A key went down. Returns whether anything consumed it.
    ///
    /// Tab is handled here rather than by a widget, because traversal is a
    /// property of the tree and not of any node in it (§7.2). It moves focus
    /// **from the keyboard**, so `:focus-visible` comes on and the focus ring
    /// appears -- which is exactly the distinction §7.2 draws.
    public boolean keyPressed(Key key, Modifiers modifiers, boolean repeat) {
        var event = new KeyEvent(KeyEvent.Kind.PRESSED, key, modifiers, repeat, focused);
        dispatchKey(event);
        if (event.isConsumed()) {
            return true;
        }
        // Accelerators come after the focused chain has declined the key, which
        // is what lets a text field keep Ctrl+A for "select all" while the window
        // binds it to something else (§7.2).
        //
        // An unnamed key is skipped rather than looked up. `Shortcut` refuses to
        // hold `Key.UNKNOWN` -- an accelerator on it could never fire, so the
        // constructor is right to say so -- and building one here to use as a map
        // key threw that exception on the UI thread and took the window with it.
        // This is not an edge case: `Key` names the keys a *shortcut* might use,
        // so every letter, digit and punctuation mark that arrives as text is
        // `UNKNOWN`, and the crash was one keystroke away at all times.
        if (key != Key.UNKNOWN) {
            var bound = shortcuts.get(new Shortcut(key, modifiers));
            if (bound != null) {
                bound.action().run();
                return true;
            }
        }
        if (key == Key.TAB && !modifiers.control() && !modifiers.alt() && !modifiers.meta()) {
            return moveFocus(modifiers.shift() ? -1 : 1);
        }
        // Roving focus inside a composite (§7.2), by the same argument that puts
        // Tab here: which node an arrow key reaches is a property of the group's
        // shape, and the radio it is currently on cannot see its siblings.
        //
        // After the focused chain has declined the key, so a widget that means
        // something else by an arrow -- a slider stepping its value, a text field
        // moving its caret -- keeps it by consuming it, and never has to know it
        // is inside a group.
        if (modifiers.none()) {
            return switch (key) {
                case LEFT -> moveFocusWithinScope(-1, false, FocusScope.Axis.HORIZONTAL);
                case RIGHT -> moveFocusWithinScope(1, false, FocusScope.Axis.HORIZONTAL);
                case UP -> moveFocusWithinScope(-1, false, FocusScope.Axis.VERTICAL);
                case DOWN -> moveFocusWithinScope(1, false, FocusScope.Axis.VERTICAL);
                // Null axis: Home and End name a position in the set rather than
                // a direction on screen, so they reach the ends of any scope.
                case HOME -> moveFocusWithinScope(-1, true, null);
                case END -> moveFocusWithinScope(1, true, null);
                default -> false;
            };
        }
        return false;
    }

    /// Moves focus within the composite the focused node is in, wrapping.
    ///
    /// Both axes rove, and that is ARIA's rule for a radio group rather than
    /// laziness: a group's direction is the stylesheet's — `flex-direction` on
    /// `radio-group` — so input cannot know which pair of arrows a user is
    /// looking at, and answering to only one pair would be wrong half the time.
    /// A composite that genuinely has an axis (a tab list along the top, a menu
    /// bar) will have to say so; nothing needs that yet.
    ///
    /// @param direction -1 for the previous, 1 for the next
    /// @param toEnd     whether to go all the way (Home/End) rather than one step
    /// @return whether focus moved
    private boolean moveFocusWithinScope(int direction, boolean toEnd, FocusScope.@Nullable Axis axis) {
        var scope = enclosingScope(focused);
        // A scope that does not answer to this axis leaves the key alone, and
        // "alone" is the whole point: the focused chain has already declined it,
        // so nothing happens -- which is what a menu item with no submenu should
        // do about `Right`, rather than sliding focus down the list (ADR-0078).
        if (scope == null || !scopeOf(scope).roves(axis)) {
            return false;
        }
        var within = new ArrayList<Element>();
        for (var child : scope.children()) {
            collectFocusable(child, within);
        }
        if (within.isEmpty()) {
            return false;
        }
        var current = within.indexOf(focused);
        var next = toEnd
                ? (direction > 0 ? within.size() - 1 : 0)
                : Math.floorMod((current < 0 ? 0 : current) + direction, within.size());
        focus(within.get(next), true);
        return true;
    }

    /// The nearest ancestor of `element` that is a composite, or null.
    ///
    /// Strictly an ancestor: a scope is not itself one of the things its arrow
    /// keys move between, and a focusable widget that also declared itself a
    /// scope would otherwise rove within its own children from outside them.
    private static @Nullable Element enclosingScope(Element element) {
        if (element == null) {
            return null;
        }
        for (var current = parentOf(element); current != null; current = parentOf(current)) {
            if (isFocusScope(current)) {
                return current;
            }
        }
        return null;
    }

    /// The window's accelerators (§7.2).
    ///
    /// Per window rather than per application, because that is the scope a user
    /// means: `Ctrl+W` closes *this* window, and a dialog's Escape is not the main
    /// window's.
    private final Map<Shortcut, Binding> shortcuts = new LinkedHashMap<>();

    /// One accelerator: what it runs, and **who asked for it**.
    ///
    /// The owner is why this is a record rather than a `Runnable`. A `menubar`
    /// binds every accelerator in its menus when it is mounted and gives them
    /// back when it is not — and it used to give back whatever was on those keys,
    /// including a binding the application made in between
    /// (ADR-0220).
    ///
    /// Compared by **identity**: "who bound it" is a question about an object,
    /// not about a value that might be equal to another one. Null is nobody in
    /// particular, which is what an application's own binding is.
    private record Binding(Runnable action, Object owner) {

        boolean ownedBy(Object candidate) {
            return owner == candidate;
        }
    }

    /// Binds an accelerator, replacing any binding for the same combination.
    ///
    /// `Ctrl+S` does not fire on `Ctrl+Shift+S` — the modifiers must match
    /// exactly — so the two can be bound to different things, which applications
    /// do.
    public PointerRouter shortcut(Shortcut shortcut, Runnable action) {
        return shortcut(shortcut, action, null);
    }

    /// The same, remembering **who** bound it.
    ///
    /// The owner is a token for [#removeShortcut(Shortcut, Object)] and nothing
    /// else: it is never called, never compared by value, and never held past the
    /// binding it belongs to. A widget that binds while it is mounted passes
    /// itself.
    public PointerRouter shortcut(Shortcut shortcut, Runnable action, Object owner) {
        shortcuts.put(
                Objects.requireNonNull(shortcut, "shortcut"),
                new Binding(Objects.requireNonNull(action, "action"), owner));
        return this;
    }

    /// Binds an accelerator built from enums — `Mod.CTRL.and(Key.S)`.
    ///
    /// The form that cannot be misspelled, and the one an application should
    /// reach for; [#shortcut(String, Runnable)] is for a menu table or a config
    /// file, where the accelerator is text before it is anything
    /// (ADR-0095).
    public PointerRouter shortcut(Mod modifier, Key key, Runnable action) {
        return shortcut(modifier.and(key), action);
    }

    /// Binds an accelerator written the way a menu prints it — `"Ctrl+S"`.
    ///
    /// @throws IllegalArgumentException if the text names no key this toolkit has
    public PointerRouter shortcut(String shortcut, Runnable action) {
        return shortcut(Shortcut.of(shortcut), action);
    }

    /// Unbinds an accelerator, **whoever** bound it. Harmless when nothing was.
    public void removeShortcut(Shortcut shortcut) {
        shortcuts.remove(Objects.requireNonNull(shortcut, "shortcut"));
    }

    /// Unbinds an accelerator **only if `owner` is what is currently bound to
    /// it**.
    ///
    /// What a `menubar` going away should do: give back the keys it took and
    /// leave alone the ones somebody else has taken since. Two things claiming
    /// `Ctrl+O` is a conflict the last registration wins, and this is the same
    /// conflict at the other end — the loser must not be able to unbind the
    /// winner (ADR-0220).
    public void removeShortcut(Shortcut shortcut, Object owner) {
        Objects.requireNonNull(shortcut, "shortcut");
        var bound = shortcuts.get(shortcut);
        if (bound != null && bound.ownedBy(owner)) {
            shortcuts.remove(shortcut);
        }
    }

    /// Every accelerator bound here, in the order they were bound — which is what
    /// a menu or a keyboard-shortcut sheet wants to print.
    public Map<Shortcut, Runnable> shortcuts() {
        var actions = new LinkedHashMap<Shortcut, Runnable>();
        shortcuts.forEach((shortcut, bound) -> actions.put(shortcut, bound.action()));
        return Collections.unmodifiableMap(actions);
    }

    /// A key came up.
    public void keyReleased(Key key, Modifiers modifiers) {
        dispatchKey(new KeyEvent(KeyEvent.Kind.RELEASED, key, modifiers, false, focused));
    }

    /// Committed text reached the window.
    ///
    /// Goes to the focused node only. Text with nowhere to land is dropped
    /// rather than broadcast -- a keystroke that types into whatever happens to
    /// be under the pointer is a bug, not a feature.
    public void textInput(String text) {
        if (focused == null) {
            return;
        }
        var event = new TextEvent(text, focused);
        for (var element : chain(focused)) {
            if (event.isConsumed()) {
                return;
            }
            if (element.widget() instanceof Handles handles) {
                handles.onText(event);
            }
        }
    }

    /// Moves focus `direction` places through the focusable nodes, wrapping.
    ///
    /// Document order, which for a tree is a depth-first walk -- the order the
    /// nodes were declared in, which is what a user reading the window expects
    /// Tab to follow.
    ///
    /// @return whether focus moved
    public boolean moveFocus(int direction) {
        return moveFocus(direction, true);
    }

    /// [#moveFocus(int)], saying whether the keyboard asked.
    ///
    /// `false` is for the one caller that moves focus without anyone pressing
    /// anything: a menu focuses its first item as it opens, so that an arrow key
    /// has somewhere to start — and a first row lit up before the user has
    /// touched the keyboard is a menu that looks like it has already chosen
    /// (ADR-0112).
    public boolean moveFocus(int direction, boolean fromKeyboard) {
        var root = traversalRoot();
        if (root == null) {
            return false;
        }
        var focusable = new ArrayList<Element>();
        collectFocusable(root, focusable);
        if (focusable.isEmpty()) {
            return false;
        }
        var current = focusable.indexOf(focused);
        // Wraps, and starts from the top when nothing had focus.
        var next = current < 0
                ? (direction > 0 ? 0 : focusable.size() - 1)
                : Math.floorMod(current + direction, focusable.size());
        focus(focusable.get(next), fromKeyboard);
        return true;
    }

    /// Where Tab enumerates from: the deepest mounted modal, or the whole window.
    ///
    /// `docs/core-widgets.md` §7's focus trap, and it is one method rather than a
    /// mechanism because a trap is exactly this — traversal starting somewhere
    /// else. Nothing is registered when a dialog opens and nothing has to be
    /// unregistered when it closes; the answer is recomputed from the tree, so a
    /// modal that goes away by any route at all gives the keyboard back
    /// (ADR-0176).
    ///
    /// The walk costs the size of the tree and happens on a Tab press, which is
    /// an order of magnitude rarer than a frame.
    private @Nullable Element traversalRoot() {
        var modal = deepestModal(focusRoot);
        return modal != null ? modal : focusRoot;
    }

    /// The topmost modal under `element`, or null.
    ///
    /// Deepest first, so a dialog opened from inside a dialog traps within the
    /// second one — and gives the first one back when it unmounts, because this
    /// is a question about the tree rather than a stack somebody maintains.
    ///
    /// **Children in reverse**, because two modals that are siblings are two
    /// overlays on one window and the later one is drawn on top. Walking
    /// forwards would hand the keyboard to the dialog *underneath* the one the
    /// user is looking at.
    private static @Nullable Element deepestModal(Element element) {
        if (element == null) {
            return null;
        }
        var children = element.children();
        for (var i = children.size() - 1; i >= 0; i--) {
            var found = deepestModal(children.get(i));
            if (found != null) {
                return found;
            }
        }
        return element.widget() instanceof Handles handles && handles.isModal() ? element : null;
    }

    /// The modal in force for the frame that was last painted, or null.
    ///
    /// Refreshed by [#updateRegions] beside the regions themselves, for the
    /// reason those exist: input is answered against the frame the user can see,
    /// so the tree that frame came from is the tree to ask ([ADR-0054]).
    private @Nullable Element modal;

    /// Whether the **pointer** may reach `element`.
    ///
    /// Modality used to be two unrelated mechanisms: [Handles#isModal] trapped
    /// the keyboard, and the pointer was blocked by a `dialog`'s scrim happening
    /// to cover the window — "modality by geometry", as `Handles` put it. So a
    /// modal without a scrim trapped the keyboard and let every click through,
    /// and nothing said so ([ADR-0232]).
    ///
    /// The rule is one flag now: **while a modal is mounted, the pointer reaches
    /// its subtree and its ancestors, and nothing else.**
    ///
    /// The ancestors are not a loophole, they are the point: a `dialog`'s scrim
    /// is the panel's *parent*, and a click on it is what closes the dialog. An
    /// ancestor is on the path between the modal and the root — a path, not a
    /// subtree — so a button in the application is neither, and is unreachable.
    private boolean isPointable(Element element) {
        if (modal == null) {
            return true;
        }
        // Inside it.
        for (var current = element; current != null; current = parentOf(current)) {
            if (current == modal) {
                return true;
            }
        }
        // Or on the path from it to the root.
        for (var current = modal; current != null; current = parentOf(current)) {
            if (current == element) {
                return true;
            }
        }
        return false;
    }

    /// Whether `element` is inside the modal that currently has the keyboard —
    /// vacuously true when nothing is modal.
    private boolean isReachable(Element element) {
        var modal = deepestModal(focusRoot);
        if (modal == null || element == null) {
            return true;
        }
        for (var current = element; current != null; current = parentOf(current)) {
            if (current == modal) {
                return true;
            }
        }
        return false;
    }

    /// Focuses the node with this `id`, if there is one and it can take focus.
    ///
    /// The programmatic door — §4's "a form jumping to its first error", a
    /// dialog putting the caret in its first field. By **id** rather than by
    /// element for [io.github.digitalsmile.goldberry.Host#anchor]'s reason: a
    /// widget has no element and never will, and an id is the one name a
    /// description and a tree agree on.
    ///
    /// Not from the keyboard, so no focus ring appears: nobody pressed anything.
    /// A caller that wants the ring — a dialog opening on a keyboard shortcut —
    /// says so.
    ///
    /// **`focusById` and not an overload of [#focus(Element, boolean)]**, because
    /// `focus(null, false)` is a real call this class makes — it is how focus is
    /// dropped — and two overloads taking a nullable reference make it ambiguous.
    /// A name is cheaper than a cast at every call site that clears focus.
    ///
    /// @return whether focus moved
    public boolean focusById(String id, boolean fromKeyboard) {
        var found = findById(focusRoot, id);
        if (found == null) {
            return false;
        }
        // A container resolves to **the first focusable thing inside it**, which
        // is what makes "focus this dialog" and "focus this form" mean what a
        // caller intends. A dialog's panel takes no focus itself -- a panel that
        // was a Tab stop would be a stop with nothing to do on it -- so without
        // this, the one caller that most needs this method could not use it.
        var target = isFocusable(found) ? found : firstFocusableIn(found);
        if (target == null || !isFocusable(target) || !isReachable(target)) {
            return false;
        }
        focus(target, fromKeyboard);
        return true;
    }

    /// The first focusable node under `element`, in document order.
    ///
    /// What a modal is given when focus is outside it. Not [#collectFocusable]'s
    /// whole list: this is asked on every focus change, and the answer is the
    /// first entry.
    private static @Nullable Element firstFocusableIn(Element element) {
        var found = new ArrayList<Element>();
        collectFocusable(element, found);
        return found.isEmpty() ? null : found.getFirst();
    }

    private static @Nullable Element findById(Element element, String id) {
        if (element == null || id == null) {
            return null;
        }
        if (id.equals(element.id())) {
            return element;
        }
        for (var child : element.children()) {
            var found = findById(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /// Every Tab stop under `element`, in document order — with a composite
    /// contributing exactly **one** (§7.2).
    private static void collectFocusable(Element element, List<Element> out) {
        // The scope is asked *before* the node itself, so a widget that is both
        // focusable and a composite contributes one stop rather than two -- its
        // entry, which is the node an arrow key can then move away from. Asked
        // the other way round it would be reachable twice by Tab and the second
        // arrival would have no arrows, because `enclosingScope` looks strictly
        // upwards.
        if (isFocusScope(element)) {
            var entry = scopeEntry(element);
            // A composite with nothing focusable inside it falls back to itself
            // when it is focusable, and is skipped entirely when it is not.
            if (entry == null) {
                entry = isFocusable(element) ? element : null;
            }
            if (entry != null) {
                out.add(entry);
            }
            return;
        }
        if (isFocusable(element)) {
            out.add(element);
        }
        for (var child : element.children()) {
            collectFocusable(child, out);
        }
    }

    /// Where Tab lands when it enters a composite.
    ///
    /// The focusable descendant matching `:checked`, or the first one. That is
    /// ARIA's rule for a radio group — Tab returns you to the option that is
    /// selected, not to the top of the list — and it is deliberately **derived**
    /// rather than remembered: a stored roving position would be a second piece
    /// of state beside the selection, and the two would disagree the first time
    /// an application set the value itself ([ADR-0073]).
    ///
    /// A composite whose items are not selectable — a toolbar — therefore always
    /// enters at the first, which is the right answer for it too.
    private static @Nullable Element scopeEntry(Element scope) {
        var within = new ArrayList<Element>();
        for (var child : scope.children()) {
            collectFocusable(child, within);
        }
        for (var candidate : within) {
            if (candidate.hasState(PseudoClass.CHECKED)) {
                return candidate;
            }
        }
        return within.isEmpty() ? null : within.getFirst();
    }

    /// What kind of composite `element` is, if any.
    private static FocusScope scopeOf(Element element) {
        return element.widget() instanceof Handles handles ? handles.focusScope() : FocusScope.NONE;
    }

    private static boolean isFocusScope(Element element) {
        return scopeOf(element) != FocusScope.NONE;
    }

    /// Capture root-first, then bubble from the focused node up.
    ///
    /// Same shape as pointer dispatch, and for the same reason: a dialog has to
    /// be able to swallow Escape before whatever is inside it reacts.
    private void dispatchKey(KeyEvent event) {
        if (focused == null) {
            return;
        }
        var chain = chain(focused);
        for (var i = chain.size() - 1; i >= 0; i--) {
            if (event.isConsumed()) {
                return;
            }
            if (chain.get(i).widget() instanceof Handles handles) {
                measure(chain.get(i), handles, event::measuredAs);
                handles.onKeyCapture(event);
            }
        }
        for (var element : chain) {
            if (event.isConsumed()) {
                return;
            }
            if (element.widget() instanceof Handles handles) {
                measure(element, handles, event::measuredAs);
                handles.onKey(event);
            }
        }
    }

    // --- internals ---------------------------------------------------------

    /// The topmost painted region's element at `(x, y)`, or null.
    ///
    /// **Topmost, and that is the overlay rule.** [HitTest#at] scans the capture
    /// backwards, and the capture is in paint order — so whatever was drawn last
    /// answers first. The window's overlay layer is painted after the
    /// application's root ([io.github.digitalsmile.goldberry.widget.root.WindowRoot]),
    /// so a button in a `toast` takes the pointer from whatever is under it
    /// without either of them knowing about the other. It used to be true and
    /// unwritten; it is the rule now ([ADR-0232]).
    ///
    /// **And nothing outside a modal is here at all.** See [#isPointable].
    private @Nullable Element elementAt(float x, float y) {
        return HitTest.at(regions, x, y)
                .filter(Element.class::isInstance)
                .map(Element.class::cast)
                .filter(this::isPointable)
                .orElse(null);
    }

    /// Moves `:hover` from one chain to another.
    ///
    /// Hover applies to the whole ancestor chain, not just the deepest node —
    /// `.card:hover .title` has to work — so the chains are compared rather than
    /// the two elements. Only the parts that differ change, which is what stops
    /// a move within one widget from invalidating its ancestors.
    private void updateHover(@Nullable Element next, float x, float y) {
        if (hovered == next) {
            return;
        }
        var before = chain(hovered);
        var after = chain(next);

        for (var element : before) {
            if (!after.contains(element)) {
                mark(element, PseudoClass.HOVER, false);
                emit(element, PointerEvent.Kind.EXITED, x, y);
            }
        }
        for (var element : after) {
            if (!before.contains(element)) {
                mark(element, PseudoClass.HOVER, true);
                emit(element, PointerEvent.Kind.ENTERED, x, y);
            }
        }
        var previous = hovered;
        hovered = next;
        if (previous != next) {
            notifyPointing();
        }
    }

    /// Recomputes the cursor from the rectangles under the pointer.
    ///
    /// **Frozen during a capture.** A drag decides what the pointer looks like
    /// when it starts, and a cursor that flickered as the pointer crossed the
    /// widgets underneath would be telling the user about things they cannot
    /// currently interact with.
    private void updateCursor(float x, float y) {
        if (captured != null) {
            return;
        }
        setCursor(HitTest.cursorAt(regions, x, y));
    }

    private void setCursor(Cursor next) {
        if (cursor == next) {
            return;
        }
        cursor = next;
        cursorSink.accept(next);
    }

    /// Moves `:active` from one chain to another.
    ///
    /// **The whole ancestor chain, exactly as `:hover` is** — and it was not,
    /// which made `checkbox:active` and `radio:active` very nearly dead rules.
    /// `:active` was set on the deepest element the press hit, so pressing a
    /// checkbox's 16px glyph lit up `check-indicator` and pressing its label lit
    /// up `text`, and the control itself matched only in the sliver of padding
    /// between them. `docs/design-system.md` §2.1 requires every control to render
    /// a pressed state, and a control whose pressed state depends on which of its
    /// own parts you happened to hit does not have one.
    ///
    /// Chains rather than elements, so a press that moves within one widget does
    /// not invalidate its ancestors — the same reason [#updateHover] compares
    /// them.
    private void setPressed(@Nullable Element next) {
        if (pressed == next) {
            return;
        }
        var before = chain(pressed);
        var after = chain(next);

        for (var element : before) {
            if (!after.contains(element)) {
                mark(element, PseudoClass.ACTIVE, false);
            }
        }
        for (var element : after) {
            if (!before.contains(element)) {
                mark(element, PseudoClass.ACTIVE, true);
            }
        }
        pressed = next;
    }

    /// Sets or clears one of the router's own pseudo-classes, and never lights up
    /// a disabled control.
    ///
    /// `docs/design-system.md` §2.1 gives `:disabled` one appearance — 45%
    /// opacity, no colour remap — and a control that still lightened under the
    /// pointer or darkened under a press would be telling the user it can be used.
    /// Enforced here rather than in a stylesheet because the alternative is a rule
    /// per variant per state per control: `button.danger:disabled:hover` and its
    /// dozen siblings, each able to be wrong on its own. CSS would spell it
    /// `:not(:disabled):hover`, and `:not()` is not in §8's subset.
    ///
    /// Only *setting* is suppressed, and a set on a disabled element becomes a
    /// **clear** rather than a no-op. That is what lets [#restate] re-assert what
    /// the pointer is over once a frame and get both answers out of one call: the
    /// state where the control is live, and no state where it is not.
    ///
    /// It has to be re-asserted, because this method is not reached otherwise. A
    /// button commonly disables itself in its own press handler while the pointer
    /// is still over it, and `updateHover` returns early when the element under
    /// the pointer has not changed — so before [ADR-0237] the wash survived every
    /// later move *within* the control and went away only when the pointer left
    /// it. This comment claimed the opposite for a long time.
    ///
    /// The `ENTERED` and `EXITED` events are **not** suppressed: this is about
    /// what a control looks like, not about what it is told. A disabled node still
    /// hit-tests, so that a click cannot fall through to whatever is behind it
    /// ([ADR-0059]), and a tooltip explaining *why* something is disabled is the
    /// case that needs the event.
    private void mark(Element element, PseudoClass pseudoClass, boolean active) {
        if (active && isDisabled(element)) {
            active = false;
        }
        if (element.isMounted() && element.setPseudoClass(pseudoClass, active)) {
            stylesDirty = true;
            if (TRACE_INPUT) {
                // The chain, one line per node, so a frame's `subtree walks` can
                // be read against what the pointer actually did (ADR-0151).
                INPUT_LOG.info(
                        "  {} {} {}",
                        active ? "+" : "-",
                        pseudoClass,
                        element.type() == null ? "<composition>" : element.type());
            }
        }
    }

    /// Whether to report every pseudo-class the pointer and the keyboard set.
    ///
    /// Separate from `goldberry.trace.frames` because they answer different
    /// halves of the same question — that one says what a frame cost, this says
    /// what asked for it — and because this one is loud: a pointer crossing a
    /// control writes a line per node of the chain.
    private static final boolean TRACE_INPUT = Boolean.getBoolean("goldberry.trace.input");

    private static final org.slf4j.Logger INPUT_LOG = org.slf4j.LoggerFactory.getLogger(PointerRouter.class);

    /// An element and its ancestors, deepest first.
    private static List<Element> chain(@Nullable Element element) {
        var chain = new ArrayList<Element>();
        for (var current = element; current != null; current = parentOf(current)) {
            chain.add(current);
        }
        return chain;
    }

    private static @Nullable Element parentOf(Element element) {
        return element.parent() instanceof Element parent ? parent : null;
    }

    private static boolean isFocusable(Element element) {
        return element.widget() instanceof Handles handles && handles.isFocusable() && !isDisabled(element);
    }

    /// Whether `element` is disabled — **by itself or by any ancestor**.
    ///
    /// `docs/core-widgets.md`: "disabled state propagates down the tree; a
    /// disabled container disables its descendants for input and semantics". A
    /// button inside a disabled `form` says `isDisabled() == false` about itself
    /// and is unavailable all the same, and it is not the button's business to
    /// know that.
    ///
    /// **Derived by walking up, not stored and not mirrored onto the element.**
    /// The alternative is a flag pushed down the tree on every build, which is a
    /// second copy of a fact the tree already holds — and ADR-0073 has already
    /// been through what happens when a derived thing is remembered instead: the
    /// two disagree the first time something changes without telling the thing
    /// that cached it. Nothing to invalidate, nothing to leak, and it costs a
    /// walk up the ancestors on input events only.
    ///
    /// It deliberately does **not** feed `:disabled`. See
    /// ADR-0077:
    /// the container's own 45% already fades everything under it, because opacity
    /// multiplies down a subtree, and a descendant that also matched `:disabled`
    /// would be faded twice.
    static boolean isDisabled(Element element) {
        for (var current = element; current != null; current = parentOf(current)) {
            if (current.widget() instanceof Styled styled && styled.isDisabled()) {
                return true;
            }
        }
        return false;
    }

    /// Whether this kind of event is the user *doing* something, as opposed to
    /// the pointer merely being somewhere.
    ///
    /// The line a disabled subtree is cut along. Observation still arrives —
    /// which is what keeps ADR-0059's two cases working: a disabled control still
    /// hit-tests so a click cannot fall through to whatever is behind it, still
    /// resolves `cursor: not-allowed`, and still gets the enter/exit a tooltip
    /// explaining *why* it is unavailable would need.
    private static boolean isInput(PointerEvent.Kind kind) {
        return switch (kind) {
            case PRESSED, RELEASED, CLICKED, WHEEL -> true;
            case MOVED, ENTERED, EXITED -> false;
        };
    }

    /// Moves focus as a press on `target` would — §7.2's "focus travels by
    /// pointer press".
    ///
    /// It lands on the nearest focusable **ancestor** rather than only on a
    /// directly focusable target, so clicking the label inside a button focuses
    /// the button; and on a container that [Handles#delegatesFocus()] it goes the
    /// other way, down to the first focusable child, which is how clicking a
    /// `field`'s label focuses the control beside it.
    ///
    /// Public because the rule is worth naming: [#pointerPressed] is the only
    /// caller in the toolkit, and a test that wants to ask "what would a press
    /// here focus" should be able to ask that rather than paint a frame and
    /// synthesize a press to find out.
    public void focusFromPress(Element target) {
        focus(nearestFocusable(target), false);
    }

    /// Who a press at `element` should focus.
    ///
    /// Up the chain, which is what makes clicking a button's label press the
    /// button — and, at each step, a container that
    /// [Handles#delegatesFocus()] hands the press **down** to its first focusable
    /// child instead. That is how clicking a `field`'s label focuses the control
    /// beside it, which no upward walk can reach because a label is the control's
    /// sibling.
    private static @Nullable Element nearestFocusable(Element element) {
        for (var current = element; current != null; current = parentOf(current)) {
            if (isFocusable(current)) {
                return current;
            }
            if (current.widget() instanceof Handles handles && handles.delegatesFocus()) {
                var inside = firstFocusable(current);
                if (inside != null) {
                    return inside;
                }
            }
        }
        return null;
    }

    /// The first focusable node in `element`'s subtree, in document order.
    private static @Nullable Element firstFocusable(Element element) {
        for (var child : element.children()) {
            if (isFocusable(child)) {
                return child;
            }
            var deeper = firstFocusable(child);
            if (deeper != null) {
                return deeper;
            }
        }
        return null;
    }

    private static void emit(Element element, PointerEvent.Kind kind, float x, float y) {
        if (element.widget() instanceof Handles handles) {
            handles.onPointer(new PointerEvent(kind, x, y, null, 0, element));
        }
    }

    /// Capture down the chain, then bubble back up (§7.1).
    private void dispatch(PointerEvent event) {
        // One choke point for every control, present and future -- the same
        // argument that put the `:hover` refusal in `mark` rather than in each
        // widget. A control's own `disabled` check is then a second line of
        // defence rather than the only one, and a control that forgets to write
        // it is still unavailable inside a disabled container.
        var chain = chain(event.target());
        if (isInput(event.kind()) && isDisabled(event.target())) {
            // ...except that a **wheel** chains past a dead control rather than
            // stopping at it ([ADR-0238]). The argument above is about the thing
            // aimed at: a click on a disabled button must not become a click on
            // the row underneath. A wheel is not aimed at a control at all -- it
            // is aimed at whatever scrolls -- so a disabled knob in a list that
            // stopped the list dead under the pointer is the cut being wrong
            // rather than being enforced.
            if (event.kind() != PointerEvent.Kind.WHEEL) {
                return;
            }
            // The disabled elements are a **prefix** of the chain, which is
            // deepest-first: `isDisabled` walks up, so it is true from the target
            // to the outermost disabled ancestor and false every step above.
            // Dropping them leaves the live ancestors and nothing else, so the
            // dead subtree still handles nothing -- what changes is only who gets
            // a turn after it.
            chain = chain.stream().dropWhile(PointerRouter::isDisabled).toList();
        }
        // Every event of a gesture carries its origin, exactly as `dragX` does.
        // Set here rather than at each call site so a kind added later cannot
        // forget -- including CLICKED, which is synthesized after the release.
        event.anchoredAt(pressOriginValue);
        event.gestureStartedWith(pressOriginModifiers);

        // Capture is root-first, so the chain -- which is deepest-first -- is
        // walked backwards.
        for (var i = chain.size() - 1; i >= 0; i--) {
            if (event.isConsumed()) {
                return;
            }
            if (chain.get(i).widget() instanceof Handles handles) {
                event.localTo(localFor(chain.get(i), handles, event));
                measure(chain.get(i), handles, event::measuredAs);
                handles.onPointerCapture(event);
            }
        }
        for (var element : chain) {
            if (event.isConsumed()) {
                return;
            }
            if (element.widget() instanceof Handles handles) {
                // Re-pointed per handler, not once per event: dispatch bubbles,
                // and a press on a slider's thumb targets the thumb while the
                // slider handling it wants the position along *itself*
                // (ADR-0079) -- or along one named part of itself (ADR-0080).
                event.localTo(localFor(element, handles, event));
                measure(element, handles, event::measuredAs);
                handles.onPointer(event);
            }
        }
    }

    /// Tells `sink` how big `element` and the part it names were last painted.
    ///
    /// The size half of what [#localFor] does for position, and separate from it
    /// because a key event has the first and not the second. Both events are
    /// re-measured per handler for the reason both are re-pointed per handler:
    /// dispatch bubbles, and a scroll view handling a wheel that landed on a row
    /// wants its **own** rectangle rather than the row's ([ADR-0116]).
    private void measure(Element element, Handles handles, java.util.function.BiConsumer<Extent, Extent> sink) {
        var own = extentOf(element);
        var name = handles.localPart();
        var part = own;
        var named = name == null ? null : partOf(element, name);
        if (named != null) {
            var namedExtent = extentOf(named);
            // A part that has never been painted falls back to the whole, which
            // is `localPart`'s own rule: a control keeps working while the thing
            // it measures against is absent.
            if (namedExtent != Extent.NONE) {
                part = namedExtent;
            }
        }
        sink.accept(own, part);
    }

    /// How big `element` was in the snapshot the last paint left behind.
    private Extent extentOf(Element element) {
        for (var region : regions) {
            if (region.owner() == element) {
                return new Extent(region.width(), region.height());
            }
        }
        return Extent.NONE;
    }

    /// Where `event` happened inside the box `handles` measures against — its own,
    /// or the part it names ([Handles#localPart()]).
    ///
    /// Resolved here rather than in the widget because the widget cannot see its
    /// own elements, which is the same reason the router carries a drag's origin
    /// (ADR-0075) and decides where Tab goes (ADR-0073).
    ///
    /// The fallback is on the **rectangle** and not on the element, which is the
    /// case that actually happens: a part is in the tree from the first build and
    /// has no region until the first paint, so a widget asking for one before
    /// then would be handed [PointerEvent.Local#UNKNOWN] — a zero-sized box whose
    /// every fraction is 0, which for a slider is "the user asked for the
    /// minimum". Falling back to the control's own box is slightly wrong; that
    /// answer is wrong in a way that moves a value.
    private PointerEvent.Local localFor(Element element, Handles handles, PointerEvent event) {
        var name = handles.localPart();
        var part = name == null ? null : partOf(element, name);
        if (part != null) {
            var local = localTo(part, event);
            if (local != PointerEvent.Local.UNKNOWN) {
                return local;
            }
        }
        return localTo(element, event);
    }

    /// The first descendant of `element` whose CSS type is `cssType`, in document
    /// order.
    private static @Nullable Element partOf(Element element, String cssType) {
        for (var child : element.children()) {
            if (child.widget() instanceof Styled styled && cssType.equals(styled.cssType())) {
                return child;
            }
            var found = partOf(child, cssType);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /// Where `event` happened inside `element`, from the hit-test snapshot.
    ///
    /// A linear scan of the regions, once per handler on the chain. The snapshot
    /// is a per-frame list and pointer events arrive at pointer rates, so this is
    /// far below anything a frame notices — and a map rebuilt every frame to
    /// avoid it would cost more than it saved.
    ///
    /// Falls back to [PointerEvent.Local#UNKNOWN] for an element that has no
    /// rectangle, which is a widget poked directly by a test or one whose box has
    /// not been painted yet. Zero-sized rather than null, so a widget reading
    /// `fractionX()` gets 0 instead of an exception.
    private PointerEvent.Local localTo(Element element, PointerEvent event) {
        for (var region : regions) {
            if (region.owner() == element) {
                // **Through the inverse first**, which is the same arithmetic
                // [HitTest.Region#contains] uses and for the same reason: a
                // region holds the rectangle the box was *laid out* in, and a box
                // inside a `scroll` is painted a long way from there. Subtracting
                // the layout origin from the window point answers in a coordinate
                // system nobody is in -- off by exactly the scroll offset, so a
                // control kept receiving events and started reading a position
                // from outside itself.
                //
                // Two answers to "where inside this box" is how a chart stops
                // highlighting halfway down a panel while every one of its
                // pointer events still arrives (ADR-0054, ADR-0068).
                var inverse = region.inverse();
                var x = inverse == null ? event.x() : (float) inverse.mapX(event.x(), event.y());
                var y = inverse == null ? event.y() : (float) inverse.mapY(event.x(), event.y());
                return new PointerEvent.Local(x - region.left(), y - region.top(), region.width(), region.height());
            }
        }
        return PointerEvent.Local.UNKNOWN;
    }
}
