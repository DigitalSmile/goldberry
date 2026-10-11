/// The kinds of curve an [dev.goldberry.motion.Easing] can be: a cubic Bézier,
/// a step function, and a piecewise linear function, each as CSS defines it.
///
/// The design system's three curves are a Bézier and a linear function, and are
/// named on `Easing` itself. The rest of what these types can express is there
/// for an application's own stylesheets.
///
/// Null-marked: every reference is non-null unless annotated `@Nullable`.
///
/// Read more: [Motion](https://goldberry.dev/docs/guide/design-system.html#motion).
@NullMarked
package dev.goldberry.motion.easing;

import org.jspecify.annotations.NullMarked;
