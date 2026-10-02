package dev.goldberry.css.cascade;

/// Where a stylesheet sits in the cascade: toolkit base, theme, application or
/// inline.
///
/// ```java
/// var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, css);
/// ```
///
/// The four layers are fixed and closed; there is no `@layer`. At equal
/// specificity a declaration from a later layer wins, so an application rule
/// beats a theme rule beats a base rule, and the order cannot be argued with by a
/// stylesheet that loads late. Declaration order is the enum's order, so
/// [#compareTo] is the cascade comparison.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#the-cascade-four-layers).
public enum CascadeLayer {

    /// What the widgets ship with: every built-in control's default appearance.
    TOOLKIT_BASE,

    /// `nord-light` / `nord-dark`, and anything an application swaps in for them.
    /// A theme is a custom-property layer, which is why it sits above the base
    /// rules that read those properties.
    THEME,

    /// The application's own stylesheets.
    APPLICATION,

    /// What a widget writes for one node through `Styled.restyle`, for a value no
    /// selector can express. Last, because it is the most specific statement
    /// anyone can make about one element.
    INLINE
}
