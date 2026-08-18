package io.github.digitalsmile.goldberry.input;

import io.github.digitalsmile.goldberry.backend.Cursor;
import io.github.digitalsmile.goldberry.css.Selector.PseudoClass;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.Styled;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/// Turns pointer positions into events, pseudo-classes and focus.
///
/// Holds the small amount of state that input needs between frames — who is
/// hovered, who is pressed, who has focus — and it holds it against **elements**,
/// which is why the element tree exists ([ADR-0052](../../../../../../book/src/adr/0052-state-lives-on-the-element-and-rebuilds-are-deferred.md)):
/// a widget is rebuilt constantly and could not remember any of this.
///
/// Confined to the UI thread.
public final class PointerRouter {

    /// One per window. Its state is that window's pointer and focus.
    public PointerRouter() {
    }

    private List<HitTest.Region> regions = List.of();

    private Element hovered;
    private Element pressed;
    private Element focused;
    private boolean focusFromKeyboard;

    /// Who is receiving pointer events regardless of where the pointer is.
    ///
    /// §7.1 asks for pointer capture on drag, and a drag is exactly the case
    /// where the pointer leaves the thing it is dragging: a slider whose thumb
    /// stops moving when the pointer wanders off the track is the bug this
    /// prevents, and so is a button that never learns the press it started ended
    /// somewhere else.
    private Element captured;

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
    }

    /// Told when the hovered or the focused node changes — see [#onPointingChanged].
    private Runnable pointingListener;

    /// Called when the pointer moves to a different node, or focus does.
    ///
    /// **One listener, and it is the launcher's.** What needs this is the thing
    /// that opens a `tooltip`: `docs/core-widgets.md` §7 attaches one by attribute
    /// to any widget and shows it "on hover *and on keyboard focus* after delay",
    /// so something above the router has to know when either moved and start a
    /// timer. The router itself opens nothing — it has no window and no notion of
    /// one ([ADR-0105]).
    ///
    /// Not a list: a second listener would be a second thing deciding what a
    /// hover means, and there is exactly one.
    public void onPointingChanged(Runnable listener) {
        this.pointingListener = listener;
    }

    private void notifyPointing() {
        if (pointingListener != null) {
            pointingListener.run();
        }
    }

    public Element hovered() {
        return hovered;
    }

    public Element pressed() {
        return pressed;
    }

    public Element focused() {
        return focused;
    }

    /// The shape the pointer is currently showing.
    private Cursor cursor = Cursor.DEFAULT;
    private Consumer<Cursor> cursorSink = c -> { };

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
        var under = elementAt(x, y);
        updateHover(under, x, y);
        updateCursor(x, y);
        var target = captured != null ? captured : under;
        if (target != null) {
            dispatch(new PointerEvent(PointerEvent.Kind.MOVED, x, y, null, 0,
                    pressOriginX, pressOriginY, modifiers, target));
        }
    }

    /// The pointer left the window entirely.
    ///
    /// Capture survives it. A drag that leaves the window and comes back is one
    /// gesture, and the platform keeps sending the motion — releasing here would
    /// drop the second half of every drag that overshoots an edge.
    public void pointerExited() {
        updateHover(null, Float.NaN, Float.NaN);
        setCursor(Cursor.DEFAULT);
    }

    /// A button went down.
    public void pointerPressed(float x, float y, PointerEvent.Button button, int clickCount) {
        pointerPressed(x, y, button, clickCount, Modifiers.NONE);
    }

    /// The same, with modifiers.
    public void pointerPressed(float x, float y, PointerEvent.Button button, int clickCount,
            Modifiers modifiers) {
        var target = elementAt(x, y);
        updateHover(target, x, y);
        if (target == null) {
            // A press on nothing still moves focus off whatever had it, which is
            // what clicking the background is for.
            focus(null, false);
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
        // §7.2: focus travels by pointer press -- and it lands on the nearest
        // focusable ancestor, not only on a directly focusable target, so
        // clicking the label inside a button focuses the button.
        focus(nearestFocusable(target), false);
        dispatch(new PointerEvent(PointerEvent.Kind.PRESSED, x, y, button, clickCount,
                pressOriginX, pressOriginY, modifiers, target));
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
    public void pointerReleased(float x, float y, PointerEvent.Button button, int clickCount,
            Modifiers modifiers) {
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
        // `Actions`-driven code does -- must not have this overwritten under it.
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
        dispatch(new PointerEvent(PointerEvent.Kind.RELEASED, x, y, button, clickCount,
                originX, originY, modifiers, target));

        // A click is a press and a release on the same node, which is not the
        // same thing as a release: dragging off a button and letting go is how a
        // user cancels, and every control would otherwise have to work that out
        // for itself from a release it cannot locate. Synthesized here, from
        // pointer flow, exactly as §7.1 says the synthetic events are.
        //
        // "On the same node" means the release landed on the pressed element or
        // inside it -- releasing on a button's own label is a click on the
        // button.
        if (button == PointerEvent.Button.PRIMARY && wasPressed != null
                && chain(under).contains(wasPressed)) {
            dispatch(new PointerEvent(PointerEvent.Kind.CLICKED, x, y, button, clickCount,
                    originX, originY, modifiers, wasPressed));
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
    public void pointerWheel(float x, float y, float deltaX, float deltaY) {
        pointerWheel(x, y, deltaX, deltaY, Modifiers.NONE);
    }

    /// The same, with modifiers — `Shift` for a fine step on a knob (§3).
    public void pointerWheel(float x, float y, float deltaX, float deltaY, Modifiers modifiers) {
        var target = captured != null ? captured : elementAt(x, y);
        if (target != null) {
            dispatch(PointerEvent.wheel(x, y, deltaX, deltaY, modifiers, target));
        }
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
    public Element captured() {
        return captured;
    }

    /// Moves focus, recording whether it came from the keyboard.
    ///
    /// §7.2 keeps `:focus` and `:focus-visible` distinct: the focus ring renders
    /// only for keyboard focus. Both are set here so a stylesheet can tell them
    /// apart without input having to know what a ring is.
    public void focus(Element element, boolean fromKeyboard) {
        if (element != null && !isFocusable(element)) {
            return;
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
    }

    private static void notifyFocus(Element element, boolean gained, boolean fromKeyboard) {
        if (element.widget() instanceof Handles handles) {
            handles.onFocusChanged(gained, fromKeyboard);
        }
    }

    /// The root of the focusable tree, for Tab traversal.
    ///
    /// Set by whoever owns the widget tree. Without it, focus still works by
    /// pointer -- traversal is the only thing that needs to enumerate.
    private Element focusRoot;

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
            var action = shortcuts.get(new Shortcut(key, modifiers));
            if (action != null) {
                action.run();
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
    private boolean moveFocusWithinScope(int direction, boolean toEnd, FocusScope.Axis axis) {
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
    private static Element enclosingScope(Element element) {
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
    private final Map<Shortcut, Runnable> shortcuts = new LinkedHashMap<>();

    /// Binds an accelerator, replacing any binding for the same combination.
    ///
    /// `Ctrl+S` does not fire on `Ctrl+Shift+S` — the modifiers must match
    /// exactly — so the two can be bound to different things, which applications
    /// do.
    public PointerRouter shortcut(Shortcut shortcut, Runnable action) {
        shortcuts.put(
                Objects.requireNonNull(shortcut, "shortcut"),
                Objects.requireNonNull(action, "action"));
        return this;
    }

    /// Binds an accelerator built from enums — `Mod.CTRL.and(Key.S)`.
    ///
    /// The form that cannot be misspelled, and the one an application should
    /// reach for; [#shortcut(String, Runnable)] is for a menu table or a config
    /// file, where the accelerator is text before it is anything
    /// ([ADR-0095](../../../../../../book/src/adr/0095-a-shortcut-is-built-from-enums.md)).
    public PointerRouter shortcut(Mod modifier, Key key, Runnable action) {
        return shortcut(modifier.and(key), action);
    }

    /// Binds an accelerator written the way a menu prints it — `"Ctrl+S"`.
    ///
    /// @throws IllegalArgumentException if the text names no key this toolkit has
    public PointerRouter shortcut(String shortcut, Runnable action) {
        return shortcut(Shortcut.of(shortcut), action);
    }

    /// Unbinds an accelerator. Harmless when nothing was bound.
    public void removeShortcut(Shortcut shortcut) {
        shortcuts.remove(Objects.requireNonNull(shortcut, "shortcut"));
    }

    /// Every accelerator bound here, in the order they were bound — which is what
    /// a menu or a keyboard-shortcut sheet wants to print.
    public Map<Shortcut, Runnable> shortcuts() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(shortcuts));
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
        if (focusRoot == null) {
            return false;
        }
        var focusable = new ArrayList<Element>();
        collectFocusable(focusRoot, focusable);
        if (focusable.isEmpty()) {
            return false;
        }
        var current = focusable.indexOf(focused);
        // Wraps, and starts from the top when nothing had focus.
        var next = current < 0
                ? (direction > 0 ? 0 : focusable.size() - 1)
                : Math.floorMod(current + direction, focusable.size());
        focus(focusable.get(next), true);
        return true;
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
    private static Element scopeEntry(Element scope) {
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
                handles.onKeyCapture(event);
            }
        }
        for (var element : chain) {
            if (event.isConsumed()) {
                return;
            }
            if (element.widget() instanceof Handles handles) {
                handles.onKey(event);
            }
        }
    }

    // --- internals ---------------------------------------------------------

    private Element elementAt(float x, float y) {
        return HitTest.at(regions, x, y)
                .filter(Element.class::isInstance)
                .map(Element.class::cast)
                .orElse(null);
    }

    /// Moves `:hover` from one chain to another.
    ///
    /// Hover applies to the whole ancestor chain, not just the deepest node —
    /// `.card:hover .title` has to work — so the chains are compared rather than
    /// the two elements. Only the parts that differ change, which is what stops
    /// a move within one widget from invalidating its ancestors.
    private void updateHover(Element next, float x, float y) {
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
    private void setPressed(Element next) {
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
    /// Only *setting* is suppressed. Clearing always goes through, so a control
    /// that was hovered before it became disabled does not keep the state — which
    /// is a real sequence, because a button commonly disables itself in its own
    /// press handler while the pointer is still over it.
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
        }
    }

    /// An element and its ancestors, deepest first.
    private static List<Element> chain(Element element) {
        var chain = new ArrayList<Element>();
        for (var current = element; current != null; current = parentOf(current)) {
            chain.add(current);
        }
        return chain;
    }

    private static Element parentOf(Element element) {
        return element.parent() instanceof Element parent ? parent : null;
    }

    private static boolean isFocusable(Element element) {
        return element.widget() instanceof Handles handles && handles.isFocusable()
                && !isDisabled(element);
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
    /// [ADR-0077](../../../../../../book/src/adr/0077-disabled-propagates-for-input-and-not-for-paint.md):
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

    private static Element nearestFocusable(Element element) {
        for (var current = element; current != null; current = parentOf(current)) {
            if (isFocusable(current)) {
                return current;
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
        if (isInput(event.kind()) && isDisabled(event.target())) {
            return;
        }
        var chain = chain(event.target());
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
                handles.onPointer(event);
            }
        }
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
        if (name != null && partOf(element, name) instanceof Element part) {
            var local = localTo(part, event);
            if (local != PointerEvent.Local.UNKNOWN) {
                return local;
            }
        }
        return localTo(element, event);
    }

    /// The first descendant of `element` whose CSS type is `cssType`, in document
    /// order.
    private static Element partOf(Element element, String cssType) {
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
                return new PointerEvent.Local(
                        event.x() - region.left(), event.y() - region.top(),
                        region.width(), region.height());
            }
        }
        return PointerEvent.Local.UNKNOWN;
    }
}
