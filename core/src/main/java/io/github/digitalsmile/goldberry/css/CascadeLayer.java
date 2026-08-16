package io.github.digitalsmile.goldberry.css;

/// Where a stylesheet sits in the cascade.
///
/// Fixed and closed, per `ARCHITECTURE.md` §8: toolkit base → theme →
/// application → inline. Not `@layer`, which lets a stylesheet invent its own
/// ordering — four layers that everyone knows is worth more here than an
/// open-ended mechanism, and it means the order cannot be argued with by a
/// stylesheet that loads in an unexpected order.
///
/// Declaration order is the enum's order, so [#compareTo] is the cascade
/// comparison.
public enum CascadeLayer {

    /// What the widgets ship with. Every built-in control's default appearance.
    TOOLKIT_BASE,

    /// `nord-light` / `nord-dark`, and anything an application swaps in for them
    /// (§10). A theme is a custom-property layer, which is why it sits above the
    /// base rules that read those properties.
    THEME,

    /// The application's own stylesheets.
    APPLICATION,

    /// A `style="…"` on a single node. Last, because it is the most specific
    /// statement anyone can make about one element.
    INLINE
}
