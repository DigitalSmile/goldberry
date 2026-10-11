/// The rules of the **Lookalike** screen's music player: what a press of play,
/// next, previous, shuffle, repeat or a heart does to what plays.
///
/// One immutable [dev.goldberry.example.ui.lookalike.player.Session] and a
/// sealed set of [dev.goldberry.example.ui.lookalike.player.Command]s; the
/// session answers each command with the next session. No widgets, so every
/// rule is tested without a window.
///
/// Marked for NullAway, as every package in the repository is.
///
/// Read more: [Views](https://goldberry.dev/docs/applications.html#views).
@NullMarked
package dev.goldberry.example.ui.lookalike.player;

import org.jspecify.annotations.NullMarked;
