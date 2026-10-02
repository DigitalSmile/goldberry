package dev.goldberry.widgets.overlay.toast;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Corner;

/// The stack every [Toast] appears in: one per window, in its overlay layer.
///
/// One per window, put in the window's own overlay layer by [Toasts#at]. An
/// application never builds one of these itself and never puts one in its tree:
/// it holds a [ToastController] and raises toasts through it.
///
/// ## The queue is the widget, and that is why `toast` is not `message`
///
/// A `message` is a description an author writes where it goes, so it has no
/// owner and fades itself out.
/// A toast is raised rather than written, so something has to hold it — and that
/// something is this. Holding the list is what lets the stack do the two things
/// a lone banner could not:
///
///   - **keep a toast alive past its own dismissal**, so it can fade out with
///     nothing outside it having to know; and
///   - **know what its siblings are**, which is what the sibling reflow
///     needs and what nothing else in the catalog is in a position
///     to do.
///
/// ## What the corner decides
///
/// Where the stack sits, which way a new toast slides in from, and which end of
/// the column is the newest — a stack at the bottom grows upwards and one at the
/// top grows down. All three come off one value, because they are one decision.
///
/// Read more: [Overlays](https://goldberry.dev/docs/components/overlays.html#toasts).
///
/// @param controller the handle an application raises toasts through
/// @param corner     which corner the stack sits in
/// @param maximum    how many are on screen at once; the rest wait in the queue
public record Toaster(ToastController controller, Corner corner, int maximum) implements Widget.Stateful {

    /// How many toasts are visible before the rest queue.
    ///
    /// Three, and the number is a judgement: toasts queue, and nothing says how
    /// many may show at once. Four notifications stacked in a corner
    /// is a wall of text nobody reads, and one at a time makes a burst of them
    /// take half a minute to get through.
    public static final int DEFAULT_MAXIMUM = 3;

    /// Written out so that the parameters taking null for a default can say so.
    public Toaster(ToastController controller, @Nullable Corner corner, int maximum) {
        Objects.requireNonNull(controller, "controller");
        corner = corner == null ? Corner.BOTTOM_END : corner;
        if (maximum < 1) {
            throw new IllegalArgumentException("a toast stack that shows " + maximum + " toasts shows none of them");
        }
        this.controller = controller;
        this.corner = corner;
        this.maximum = maximum;
    }

    /// A stack in a corner, showing [#DEFAULT_MAXIMUM] at a time.
    public Toaster(ToastController controller, Corner corner) {
        this(controller, corner, DEFAULT_MAXIMUM);
    }

    @Override
    public State<?> createState() {
        return new ToasterState();
    }
}
