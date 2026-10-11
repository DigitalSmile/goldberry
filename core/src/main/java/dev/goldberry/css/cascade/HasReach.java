package dev.goldberry.css.cascade;

/// What a change below an element, or after it among its siblings, restyles
/// on that element through the sheets' `:has()`: the answer
/// [StyleResolver#hasReach] gives the element tree as it walks up from a
/// change.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#selectors).
public enum HasReach {

    /// Nothing: no `:has()` sits on a compound that could match it.
    NONE,

    /// Its own style, which a `:has()` on its compound may now answer
    /// differently.
    OWN,

    /// Its own style and everything below it, because the compound that
    /// carries the `:has()` stands left of a combinator: `card:has(.x) text`.
    SUBTREE
}
