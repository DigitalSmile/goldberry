package io.github.digitalsmile.goldberry.widget.attr;

import io.github.digitalsmile.goldberry.bind.Observable;
import io.github.digitalsmile.goldberry.widget.Widget;

/// A widget whose value can come from a property — §9's `bind=`, chainable.
///
/// ```java
/// new RadioGroup(null, this::pickTheme,
///         new Radio("dark", "Nord dark"),
///         new Radio("light", "Nord light"))
///     .bound(themeName)
///     .id("themes")
/// ```
///
/// The sibling of [Attributed], and it exists for the same reason: a widget with
/// a range, a handler, a disabled flag *and* a binding has a constructor with six
/// positional arguments, four of which are usually defaults. Every widget that
/// has a `source` component implements this, so the binding is one named step in
/// a chain rather than the fourth `null` in a row
/// (ADR-0093).
///
/// An [Observable] and never a `Property`: data flows down and events flow up, so
/// a widget reads and watches and cannot write
/// (ADR-0063).
///
/// @param <W> the implementing widget's own type
public interface Bindable<W extends Widget> extends Widget {

    /// A copy of this widget following `source`.
    ///
    /// What the value *means* is the widget's own business: for `text` it is the
    /// content, for `checkbox` the checked state, for `slider` the number. The
    /// framework only knows when to rebuild.
    W bound(Observable<?> source);
}
