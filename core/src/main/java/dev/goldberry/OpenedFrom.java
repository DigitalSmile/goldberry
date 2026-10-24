package dev.goldberry;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.StyleElement;
import dev.goldberry.widget.BuildContext;

/// Names the widget a popup is opened from, so the window's stylesheets reach
/// into it as they would reach into that widget's children.
///
/// A popup's contents are the root of a tree of their own. With an opener, a
/// descendant or child combinator steps out of that root into the opener and
/// on up the window's tree: `.toolbar > popover` matches a popover opened from
/// a toolbar, and `.sidebar menu item` the rows of a menu opened from inside a
/// sidebar. Properties are still not inherited, and `:root` still matches the
/// popup's own root.
///
/// Theme, application and inline rules reach in. The toolkit's own base sheet
/// does not: it styles a control's parts, `scroll:hover scroll-thumb` say, and
/// the list a `select` opens inside a hovered scroll is not one of them.
///
/// ```java
/// var list = OpenedFrom.call(context(), () -> host.popup(panel, field, Placement.BELOW));
/// ```
///
/// Every `Host` call that opens a popup reads it. A popup opened against an id
/// is opened from the widget with that id unless a caller named another, and a
/// context menu or a tooltip from the widget it belongs to, so this is for a
/// popup opened against a rectangle, which names no widget.
///
/// Read more: [Overlays](https://goldberry.dev/docs/components/overlays.html#popover).
public final class OpenedFrom {

    /// Scoped rather than passed: every opening method on `Host` would otherwise
    /// need one more overload, and an opener is a fact about the call, not about
    /// what is opened.
    private static final ScopedValue<StyleElement> OPENER = ScopedValue.newInstance();

    private OpenedFrom() {}

    /// Runs `open` with `context`'s widget as the opener of any popup it opens.
    ///
    /// @param context the widget's own context, from `State.context()` or a
    ///                `build`; one that is not an element in a tree names no
    ///                opener, and `open` runs as it would have
    /// @return what `open` returned
    public static <T> T call(BuildContext context, Supplier<T> open) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(open, "open");
        return context instanceof StyleElement element ? within(element, open) : open.get();
    }

    /// The same for an element already in hand, unless an outer call named one:
    /// what the toolkit's own openers use, so a caller's choice wins over theirs.
    static <T> T unlessNamed(@Nullable StyleElement element, Supplier<T> open) {
        return element == null || OPENER.isBound() ? open.get() : within(element, open);
    }

    /// The same for an opening that answers nothing.
    static void runUnlessNamed(@Nullable StyleElement element, Runnable open) {
        if (element == null || OPENER.isBound()) {
            open.run();
        } else {
            ScopedValue.where(OPENER, element).run(open);
        }
    }

    /// The opener named for the popup being opened now, if any.
    static Optional<StyleElement> current() {
        return OPENER.isBound() ? Optional.of(OPENER.get()) : Optional.empty();
    }

    private static <T> T within(StyleElement element, Supplier<T> open) {
        return ScopedValue.where(OPENER, element).call(open::get);
    }
}
