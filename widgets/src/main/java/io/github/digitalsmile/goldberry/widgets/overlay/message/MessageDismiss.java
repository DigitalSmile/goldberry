package io.github.digitalsmile.goldberry.widgets.overlay.message;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.semantics.Role;
import io.github.digitalsmile.goldberry.widget.semantics.Semantics;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The × that closes a [Message] — a **part**.
///
/// ## Focusable, where `tab-close` is not
///
/// The two look alike and answer opposite questions. A `tab-close` sits inside a
/// tab strip, which is **one** Tab stop with the arrows roving inside it (§7.2),
/// and the keyboard's way to close a tab is `Delete` on the tab itself. A message
/// is not a focus scope and owns no keyboard map: if this were not focusable
/// there would be no way to dismiss a banner without a pointer, and §7 asks for
/// the dismiss without saying it is for the mouse only.
///
/// So it takes `Space` and `Enter` the way a `button` does, and it is a Tab stop
/// — one per dismissable banner, which is the honest cost of the affordance.
///
/// @param onDismiss what to tell; never null, because a banner with nothing
///                  listening does not build one of these at all
record MessageDismiss(Runnable onDismiss) implements Widget.Leaf, Styled, Paints, Handles, Semantics {

    /// The same weight the glyph at the other end draws at, so the two ends of a
    /// banner are one pen.
    private static final double STROKE = 1.5;

    @Override
    public String cssType() {
        return "message-dismiss";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    /// See the class note: this is the keyboard's only way out of a banner.
    @Override
    public boolean isFocusable() {
        return true;
    }

    @Override
    public void onPointer(PointerEvent event) {
        if (event.kind() == PointerEvent.Kind.CLICKED) {
            onDismiss.run();
            event.consume();
        }
    }

    /// `Space` and `Enter`, `button`'s map — repeats ignored, because holding the
    /// key down is one dismissal and the banner is gone after the first.
    @Override
    public void onKey(KeyEvent event) {
        if (event.kind() != KeyEvent.Kind.PRESSED
                || event.isRepeat()
                || !event.modifiers().none()) {
            return;
        }
        if (event.key() == Key.SPACE || event.key() == Key.ENTER) {
            onDismiss.run();
            event.consume();
        }
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).mark(new Box.Mark(Box.Mark.Kind.CROSS, style.color(), STROKE));
    }

    @Override
    public Role role() {
        return Role.BUTTON;
    }

    @Override
    public String accessibleName() {
        return "Dismiss";
    }
}
