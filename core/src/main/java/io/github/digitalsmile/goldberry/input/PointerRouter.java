package io.github.digitalsmile.goldberry.input;

import io.github.digitalsmile.goldberry.css.Selector.PseudoClass;
import io.github.digitalsmile.goldberry.widget.Element;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
        var target = elementAt(x, y);
        updateHover(target, x, y);
        if (target != null) {
            dispatch(new PointerEvent(PointerEvent.Kind.MOVED, x, y, null, 0, target));
        }
    }

    /// The pointer left the window entirely.
    public void pointerExited() {
        updateHover(null, Float.NaN, Float.NaN);
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
        // §7.2: focus travels by pointer press -- and it lands on the nearest
        // focusable ancestor, not only on a directly focusable target, so
        // clicking the label inside a button focuses the button.
        focus(nearestFocusable(target), false);
        dispatch(new PointerEvent(PointerEvent.Kind.PRESSED, x, y, button, clickCount, target));
    }

    /// A button came up.
    public void pointerReleased(float x, float y, PointerEvent.Button button, int clickCount) {
        var target = elementAt(x, y);
        updateHover(target, x, y);
        setPressed(null);
        if (target != null) {
            dispatch(new PointerEvent(PointerEvent.Kind.RELEASED, x, y, button, clickCount, target));
        }
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
        if (key == Key.TAB && !modifiers.control() && !modifiers.alt() && !modifiers.meta()) {
            return moveFocus(modifiers.shift() ? -1 : 1);
        }
        return false;
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

    private void mark(Element element, PseudoClass pseudoClass, boolean active) {
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
