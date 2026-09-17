package io.github.digitalsmile.goldberry.widgets.core.scroll;

import java.util.List;
import java.util.Objects;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;

/// Popup content, in a viewport when it is taller than the screen — the answer
/// both `menu` and `select` give to [Host.Fit].
///
/// ```java
/// host.popup(list, field, Placement.BELOW, width, new Fitted("select-viewport"));
/// ```
///
/// ## What it replaces
///
/// A popup taller than the work area is clamped to the near edge by [io.github.digitalsmile.goldberry.Placement],
/// which keeps the top visible and silently drops everything below it. A menu
/// that loses its last three commands with no indication that it has is the
/// worst kind of wrong, and it was the honest thing to do before `scroll`
/// existed (ADR-0118).
///
/// `Menus` has done this since, from an **estimate** — rows times an assumed
/// height — because nothing reported what a menu actually measured. `select`
/// did not do it at all, so a list longer than the screen still lost its bottom.
/// One guess and one gap, and both go away once the popup facility says what it
/// measured (ADR-0179).
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
