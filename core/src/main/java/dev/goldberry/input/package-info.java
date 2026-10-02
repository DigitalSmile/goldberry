/// Input dispatch: turning pointer positions into events, pseudo-classes and
/// focus, and saying which arrow keys rove inside a composite that is one Tab
/// stop.
///
/// [PointerRouter][dev.goldberry.input.PointerRouter] is the dispatcher; its
/// state — hovered, pressed, focused — is held against elements rather than
/// widgets, because a widget is rebuilt constantly and could not remember any
/// of it. The vocabulary around it lives in subpackages by the role each plays:
/// `input.event`, `input.key`, `input.hit` and `input.handler`, and the gestures
/// `input.tap` and `input.drop`. Exported to applications.
///
/// `@NullMarked`, which puts this package under NullAway.
///
/// Read more: [Input and focus](https://goldberry.dev/docs/guide/input.html).
@NullMarked
package dev.goldberry.input;

import org.jspecify.annotations.NullMarked;
