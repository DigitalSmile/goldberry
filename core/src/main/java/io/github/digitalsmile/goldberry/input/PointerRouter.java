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

    /// Whether anything changed that a stylesheet could react to.
    private boolean stylesDirty;

    /// Replaces the hit-test snapshot, normally right after a frame is painted.
    public void updateRegions(List<HitTest.Region> regions) {
        this.regions = List.copyOf(Objects.requireNonNull(regions, "regions"));
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
        var under = elementAt(x, y);
        updateHover(under, x, y);
        updateCursor(x, y);
        var target = captured != null ? captured : under;
        if (target != null) {
            dispatch(new PointerEvent(PointerEvent.Kind.MOVED, x, y, null, 0, target));
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
        var target = elementAt(x, y);
        updateHover(target, x, y);
        if (target == null) {
            // A press on nothing still moves focus off whatever had it, which is
            // what clicking the background is for.
            focus(null, false);
            return;
        }

        setPressed(target);
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
        dispatch(new PointerEvent(PointerEvent.Kind.PRESSED, x, y, button, clickCount, target));
    }

    /// A button came up.
    ///
    /// The release goes to whoever captured the press, not to whatever happens to
    /// be under the pointer now. A button pressed and then released 200 pixels
    /// away has not been clicked, but it has certainly stopped being pressed, and
    /// it is the only thing that can know the difference.
    public void pointerReleased(float x, float y, PointerEvent.Button button, int clickCount) {
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
        if (target == null) {
            return;
        }
        dispatch(new PointerEvent(PointerEvent.Kind.RELEASED, x, y, button, clickCount, target));

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
            dispatch(new PointerEvent(PointerEvent.Kind.CLICKED, x, y, button, clickCount, wasPressed));
        }
    }

    /// The wheel turned, or a touchpad scrolled, at a logical position.
    ///
    /// Deltas are in lines and positive is down and right — see
    /// [PointerEvent#deltaY()]. It travels the same capture/bubble path as every
    /// other pointer event, so a scroll view consumes it and an ancestor scroll
    /// view does not also scroll.
    public void pointerWheel(float x, float y, float deltaX, float deltaY) {
        var target = captured != null ? captured : elementAt(x, y);
        if (target != null) {
            dispatch(PointerEvent.wheel(x, y, deltaX, deltaY, target));
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
        if (focused != null) {
            mark(focused, PseudoClass.FOCUS, false);
            mark(focused, PseudoClass.FOCUS_VISIBLE, false);
        }
        focused = element;
        focusFromKeyboard = fromKeyboard;
        if (focused != null) {
            mark(focused, PseudoClass.FOCUS, true);
            if (fromKeyboard) {
                mark(focused, PseudoClass.FOCUS_VISIBLE, true);
            }
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
        var action = shortcuts.get(new Shortcut(key, modifiers));
        if (action != null) {
            action.run();
            return true;
        }
        if (key == Key.TAB && !modifiers.control() && !modifiers.alt() && !modifiers.meta()) {
            return moveFocus(modifiers.shift() ? -1 : 1);
        }
        return false;
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

    private static void collectFocusable(Element element, List<Element> out) {
        if (isFocusable(element)) {
            out.add(element);
        }
        for (var child : element.children()) {
            collectFocusable(child, out);
        }
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
        hovered = next;
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

    private void setPressed(Element next) {
        if (pressed == next) {
            return;
        }
        if (pressed != null) {
            mark(pressed, PseudoClass.ACTIVE, false);
        }
        pressed = next;
        if (pressed != null) {
            mark(pressed, PseudoClass.ACTIVE, true);
        }
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
        if (active && element.widget() instanceof Styled styled && styled.isDisabled()) {
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
        return element.widget() instanceof Handles handles && handles.isFocusable();
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
    private static void dispatch(PointerEvent event) {
        var chain = chain(event.target());

        // Capture is root-first, so the chain -- which is deepest-first -- is
        // walked backwards.
        for (var i = chain.size() - 1; i >= 0; i--) {
            if (event.isConsumed()) {
                return;
            }
            if (chain.get(i).widget() instanceof Handles handles) {
                handles.onPointerCapture(event);
            }
        }
        for (var element : chain) {
            if (event.isConsumed()) {
                return;
            }
            if (element.widget() instanceof Handles handles) {
                handles.onPointer(event);
            }
        }
    }
}
