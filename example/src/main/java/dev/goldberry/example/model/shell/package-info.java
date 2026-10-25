/// What the window around the screens knows: the screen showing, Back and Forward, the reading size, the sidebar and
/// the bar's readings.
///
/// One `@Model` slice of the showcase's state, with its actions nested in it;
/// see [dev.goldberry.example.model]. Opened to `:core`, which binds it
/// reflectively when it was not woven.
///
/// Marked for NullAway, as every package in the repository is.
///
/// Read more: [Values](https://goldberry.dev/docs/applications.html#values).
@NullMarked
package dev.goldberry.example.model.shell;

import org.jspecify.annotations.NullMarked;
