/// The cascade: which declaration wins for an element, and what its `var()`s stand for.
///
/// [StyleResolver] runs the four fixed layers of [CascadeLayer] over a list of
/// stylesheets and substitutes custom properties. [Transitions] and
/// [KeyframeAnimations] are the motion settings the cascade resolves for a node,
/// carried on its `ComputedStyle`. Exported to applications; read by the renderer,
/// the stylesheet lint and the theme audit.
///
/// Null-marked: every reference is non-null unless annotated `@Nullable`.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#the-cascade-four-layers).
@NullMarked
package dev.goldberry.css.cascade;

import org.jspecify.annotations.NullMarked;
