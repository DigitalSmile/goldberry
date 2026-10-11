/// The widgets of the **Lookalike** screen's music player: a sidebar, a
/// playlist page, a now-playing panel and a player bar, held together by
/// [dev.goldberry.example.ui.lookalike.view.StreamingApp].
///
/// Every part is a stateless record over the player's session; the one stateful
/// widget holds the session and the timer. The look is entirely
/// `chapter-lookalike.css`: the parts are ordinary rows, columns, pressables,
/// icons and sliders.
///
/// Marked for NullAway, as every package in the repository is.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html).
@NullMarked
package dev.goldberry.example.ui.lookalike.view;

import org.jspecify.annotations.NullMarked;
