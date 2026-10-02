package dev.goldberry.widgets.core.scroll;

import java.util.List;
import java.util.Objects;

import dev.goldberry.Host;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;

/// Popup content, in a viewport when it is taller than the screen — the answer
/// both `menu` and `select` give to [Host.Fit].
///
/// ```java
/// host.popup(list, field, Placement.BELOW, width, new Fitted("select-viewport"));
/// ```
///
/// ## What it prevents
///
/// A popup taller than the work area is clamped to the near edge by [dev.goldberry.Placement],
/// which keeps the top visible and silently drops everything below it. A menu
/// that loses its last three commands with no indication that it has is the
/// worst kind of wrong. The popup facility reports what the content measured,
/// and this wraps it in a viewport when that is more than the screen holds.
///
/// ## Nothing happens to content that fits
///
/// Which is nearly all of it. The wrapper appears only when the height is
/// actually exceeded, so an ordinary menu has no viewport in it, no thumb to
/// fade, and nothing that takes the wheel. That is also what makes the second
/// measurement [Host.Fit] costs affordable: it is only paid by the popup that
/// needed it.
///
/// @param viewportClass the class the viewport carries, so a stylesheet can tell
///                      a menu's from a list's — neither has any appearance of
///                      its own beyond not growing
///
/// Read more: [Scroll](https://goldberry.dev/docs/layout/scroll.html#scrolling-from-java).
public record Fitted(String viewportClass) implements Host.Fit {

    /// How much of the work area the content leaves alone at each end.
    ///
    /// A panel flush against the top and bottom of the screen looks like one that
    /// has been cut off even when it has not. `Menus` had this number and it was
    /// never anything to do with menus.
    public static final float MARGIN = 8;

    public Fitted {
        Objects.requireNonNull(viewportClass, "viewportClass");
    }

    @Override
    public Widget fit(Widget content, LogicalSize measured, LogicalRect available) {
        Objects.requireNonNull(content, "content");
        var room = available.size().height() - MARGIN * 2;
        if (measured.height() <= room) {
            return content;
        }
        // The height is the opener's, because nothing in a stylesheet knows how
        // tall the display is.
        return new Scroll(List.of(content), ScrollAxis.VERTICAL, Attributes.NONE.classes(viewportClass)).height(room);
    }
}
