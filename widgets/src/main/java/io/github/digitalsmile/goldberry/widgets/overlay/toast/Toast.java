package io.github.digitalsmile.goldberry.widgets.overlay.toast;

import java.time.Duration;
import java.util.Objects;

/// One notification — `docs/core-widgets.md` §7's `toast`, as a **value**.
///
/// ```java
/// toasts.show(new Toast("Draft saved"));
/// toasts.show(new Toast("Message sent").action("Undo", this::undo).timeout(Duration.ofSeconds(8)));
/// ```
///
/// ## Not a widget, where `message` is one
///
/// The two look alike and are the opposite kind of thing, which §7 spends a
/// sentence on: a message is **part of the layout** and is about the thing next
/// to it, so an author writes one where it goes; a toast is **transient**,
/// floats over the window and is about something that just happened, so nobody
/// writes one anywhere — an application *raises* it and the stack decides where
/// it goes and how long it stays.
///
/// So this is a value handed to [ToastController#show], and a widget only ever
/// exists for as long as the stack is drawing one
/// (ADR-0177).
/// A `Toast` cannot be put in a document for the same reason: a document is a
/// description of a screen, and a toast is a thing that happened.
///
/// ## No kind
///
/// §7 gives `message` four kinds and gives a toast none, and the omission reads
/// deliberate: a message says *what is true of this region* and has to be told
/// apart from three other things it might be saying, where a toast says *what
/// just happened* and there is only one of those on the screen at a time. Adding
/// a red toast would be inventing vocabulary the specification declined to.
///
/// @param text    what it says
/// @param label   the action button's label, or null for a toast with no button
/// @param onPress what the action button does, or null
/// @param timeout how long it stays once it has arrived — the clock stops while
///                the pointer is over it (§7's "hover-pause")
public record Toast(String text, String label, Runnable onPress, Duration timeout) {

    /// §2: "timeout 5s default".
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    /// A toast that goes on its own after [#DEFAULT_TIMEOUT].
    public Toast(String text) {
        this(text, null, null, DEFAULT_TIMEOUT);
    }

    public Toast {
        Objects.requireNonNull(text, "text");
        timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("a toast's timeout is how long it stays, and " + timeout + " is not a"
                    + " length of time. Duration.ZERO is the one that never goes.");
        }
        // A label with nothing behind it is a button that does nothing, which is
        // worse than no button: it invites the one click that will not work.
        if (label != null && onPress == null) {
            throw new IllegalArgumentException("a toast's action button is labelled \"" + label + "\" and does nothing."
                    + " Give it a handler, or leave the label out.");
        }
    }

    /// This toast with a button after its words — §7's "optional action button".
    public Toast action(String label, Runnable onPress) {
        return new Toast(
                text, Objects.requireNonNull(label, "label"), Objects.requireNonNull(onPress, "onPress"), timeout);
    }

    /// This toast with a chosen lifetime.
    ///
    /// [Duration#ZERO] is the one that **never goes on its own**: a toast
    /// reporting something the user must acknowledge, which is the case an
    /// action button is usually for. It is still dismissible.
    public Toast timeout(Duration value) {
        return new Toast(text, label, onPress, value);
    }

    /// Whether this toast goes on its own.
    public boolean expires() {
        return !timeout.isZero();
    }

    /// Whether it carries a button.
    public boolean hasAction() {
        return label != null;
    }
}
