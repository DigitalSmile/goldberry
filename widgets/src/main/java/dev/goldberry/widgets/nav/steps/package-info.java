/// Steps: a progress indicator over an ordered list, each entry done, current,
/// upcoming or failed.
///
/// [dev.goldberry.widgets.nav.steps.Steps] is the list and
/// [dev.goldberry.widgets.nav.steps.Step] is one entry in it. The list writes
/// where each step stands on every build, so a document cannot describe two
/// current steps or none. Everything else here is a part: CSS-selectable, and
/// not constructible from markup.
///
/// Null-marked: every reference is non-null unless it says `@Nullable`.
///
/// Read more: [Navigation](https://goldberry.dev/docs/components/navigation.html#steps).
@NullMarked
package dev.goldberry.widgets.nav.steps;

import org.jspecify.annotations.NullMarked;
