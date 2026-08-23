package io.github.digitalsmile.goldberry.widgets.overlay.toast;

import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Corner;

import java.util.Objects;

/// The stack every [Toast] appears in — `docs/core-widgets.md` §7's toast layer.
///
/// One per window, put in the window's own overlay layer by [Toasts#at]. An
/// application never builds one of these itself and never puts one in its tree:
/// it holds a [ToastController] and raises toasts through it.
///
/// ## The queue is the widget, and that is why `toast` is not `message`
///
/// A `message` is a description an author writes where it goes, so it has no
/// owner and had to learn to fade itself out
/// ([ADR-0175](../../../../../../../../book/src/adr/0175-a-banner-says-its-kind-twice.md)).
/// A toast is raised rather than written, so something has to hold it — and that
/// something is this. Holding the list is what lets the stack do the two things
/// a lone banner could not:
///
///   - **keep a toast alive past its own dismissal**, so it can fade out with
///     nothing outside it having to know; and
///   - **know what its siblings are**, which is what §3's "siblings reflow via
///     `translate`" needs and what nothing else in the catalog is in a position
///     to do.
///
/// ## What the corner decides
///
/// Where the stack sits, which way a new toast slides in from, and which end of
/// the column is the newest — a stack at the bottom grows upwards and one at the
/// top grows down. All three come off one value, because they are one decision.
///
/// @param controller the handle an application raises toasts through
/// @param corner     which corner the stack sits in — §7's "stacking corner
///                   configurable"
/// @param maximum    how many are on screen at once; the rest wait, which is
///                   what §7's "queued" means
public record Toaster(ToastController controller, Corner corner, int maximum)
        implements Widget.Stateful {

    /// How many toasts are visible before the rest queue.
    ///
    /// Three, and the number is a judgement rather than a specification: §7 says
    /// "queued" and does not say how many. Four notifications stacked in a corner
    /// is a wall of text nobody reads, and one at a time makes a burst of them
    /// take half a minute to get through.
    public static final int DEFAULT_MAXIMUM = 3;

    public Toaster {
        Objects.requireNonNull(controller, "controller");
        corner = corner == null ? Corner.BOTTOM_END : corner;
        if (maximum < 1) {
            throw new IllegalArgumentException(
                    "a toast stack that shows " + maximum + " toasts shows none of them");
        }
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
