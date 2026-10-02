/// The interfaces a widget implements to take part in input: reacting to the
/// pointer and the keyboard, being told what size it came out as and where it
/// was put, and becoming the subject of a context menu.
///
/// Each is opt-in. A widget that implements none of them is scenery: the router
/// never asks it anything, so dispatch costs the number of interested nodes
/// rather than the depth of the tree. Exported to applications as one of input's
/// parts, split by the role each plays.
///
/// `@NullMarked`, which puts this package under NullAway.
///
/// Read more: [Input and focus](https://goldberry.dev/docs/guide/input.html#what-a-custom-widget-implements).
@NullMarked
package dev.goldberry.input.handler;

import org.jspecify.annotations.NullMarked;
