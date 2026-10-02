/// What is under a point, answered from a snapshot of the last painted frame — and
/// how big each node came out when it was painted.
///
/// Hit testing does not lay out again: a pointer event is about what the user can
/// see, and that is the last frame, not the next. The same snapshot answers the one
/// question a widget cannot ask itself while building, since `build` runs before
/// layout: what size it is. Exported as the snapshot the pointer router works off.
///
/// `@NullMarked`, which puts this package under NullAway.
///
/// Read more: [Input and focus](https://goldberry.dev/docs/guide/input.html#how-an-event-travels).
@NullMarked
package dev.goldberry.input.hit;

import org.jspecify.annotations.NullMarked;
