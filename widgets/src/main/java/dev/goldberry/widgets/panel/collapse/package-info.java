/// The `collapse`: a header and a body that folds away.
///
/// [dev.goldberry.widgets.panel.collapse.Collapse] unmounts its
/// body while closed rather than hiding it, so a closed section keeps no
/// subscriptions alive. It can keep its own open state or be controlled by the
/// application; several in an accordion column become exclusive. The header, the
/// chevron and the body are parts.
///
/// Every reference is non-null unless it says `@Nullable`.
///
/// Read more: [Panels](https://goldberry.dev/docs/components/panels.html#collapse).
@NullMarked
package dev.goldberry.widgets.panel.collapse;

import org.jspecify.annotations.NullMarked;
