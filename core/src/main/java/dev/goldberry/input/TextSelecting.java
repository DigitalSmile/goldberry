package dev.goldberry.input;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.Host;
import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.input.select.TextSelection;
import dev.goldberry.render.model.LogicalPoint;
import dev.goldberry.text.edit.keys.EditCommand;
import dev.goldberry.text.edit.keys.EditKeys;
import dev.goldberry.text.edit.keys.EditSurface;
import dev.goldberry.widget.Element;

/// The router's half of selecting plain text: what a press, a drag, a release
/// and `Ctrl+C` that nothing else took do to the tree's [TextSelection].
///
/// Kept out of [PointerRouter] so the gesture reads in one place. It runs
/// **after** dispatch and only on what dispatch left: a press a widget
/// consumed is that widget's, and leaves the selection alone, so a toolbar's
/// Copy button can act on what is selected.
final class TextSelecting {

    /// Where an element's content box is, as the router measured it from the
    /// last paint.
    interface Locator {

        /// `(x, y)` from the corner of `element`'s content box.
        LogicalPoint contentPoint(Element element, float x, float y);

        /// How wide `element`'s content box was painted, or zero.
        double contentWidth(Element element);
    }

    private final Locator locator;

    /// The selection the last selecting press went to, which may be another
    /// tree's than the next press lands in: a press in a popup clears the
    /// window's selection, as a click anywhere else does.
    private @Nullable TextSelection<Element> active;

    TextSelecting(Locator locator) {
        this.locator = Objects.requireNonNull(locator, "locator");
    }

    /// A press went to `target`, which may be null for a press on nothing, and
    /// `event` is what dispatch made of it.
    ///
    /// @param control whether the press went to a control: a focusable widget
    ///                on the target's chain. A press on a button leaves the
    ///                selection alone, as a browser's does, so a toolbar's Copy
    ///                can act on it; a press on anything else clears it.
    void pressed(
            @Nullable Element target, @Nullable PointerEvent event, float x, float y, int clickCount, boolean control) {
        if (event != null && event.button() != PointerEvent.Button.PRIMARY) {
            // A right click opens a menu about what is selected, and must not
            // take the selection away first.
            return;
        }
        if (event != null && event.isConsumed()) {
            // A widget took the press: the gesture is its own.
            return;
        }
        var selection = target == null ? null : target.textSelection();
        if (target != null && selection != null && selection.selectable(target)) {
            // A selectable box wins over the control it is in: words inside a
            // focusable scroll are still selected by dragging across them.
            if (active != null && active != selection) {
                clear(active);
            }
            var at = locator.contentPoint(target, x, y);
            if (selection.press(target, at.x(), at.y(), locator.contentWidth(target), clickCount)) {
                active = selection;
                repaint(target);
                if (event != null) {
                    event.consume();
                }
            }
            return;
        }
        if (control) {
            // A button, a field: the selection is left as it is, and so is the
            // route `Ctrl+C` takes to it.
            return;
        }
        if (active != null) {
            clear(active);
            active = null;
        }
    }

    /// The pointer moved; a held drag extends the selection.
    void moved(float x, float y) {
        var selection = active;
        if (selection == null || !selection.isDragging()) {
            return;
        }
        var holder = selection.holder();
        if (holder == null) {
            return;
        }
        var at = locator.contentPoint(holder, x, y);
        if (selection.drag(at.x(), at.y(), locator.contentWidth(holder))) {
            repaint(holder);
        }
    }

    /// The button came up: a selection that was being dragged is finished,
    /// and goes to the primary selection where the desktop has one. A double
    /// or triple click finished on its press, and goes there too.
    void released() {
        var selection = active;
        if (selection == null) {
            return;
        }
        selection.release();
        var holder = selection.holder();
        if (holder != null && !selection.isEmpty()) {
            holder.host().flatMap(Host::primarySelection).ifPresent(primary -> primary.text(selection.selectedText()));
        }
    }

    /// A key nothing focused took. `Ctrl+C` (`Cmd+C` on macOS) copies the
    /// selection, when there is one.
    ///
    /// @return whether it did
    boolean key(KeyEvent event) {
        var selection = active;
        if (selection == null || selection.isEmpty()) {
            return false;
        }
        if (EditKeys.of(event, EditSurface.WRAPPED) != EditCommand.Simple.COPY) {
            return false;
        }
        var holder = selection.holder();
        var host = holder == null ? null : holder.host().orElse(null);
        return host != null && host.clipboard().text(selection.selectedText());
    }

    /// The selection presses go to now, or null.
    @Nullable
    TextSelection<Element> active() {
        return active;
    }

    private static void clear(TextSelection<Element> selection) {
        var holder = selection.holder();
        if (selection.clear() && holder != null) {
            repaint(holder);
        }
    }

    private static void repaint(Element element) {
        element.host().ifPresent(Host::repaint);
    }
}
