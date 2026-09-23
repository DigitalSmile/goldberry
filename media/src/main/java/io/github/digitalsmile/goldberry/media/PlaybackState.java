package io.github.digitalsmile.goldberry.media;

/// Where a [MediaPlayer] is in its life (`docs/goldberry-media.md` §3, "State").
///
/// ```
/// IDLE → OPENING → BUFFERING ⇄ PLAYING ⇄ PAUSED → ENDED
///                                                 ERROR, from any state
/// ```
///
/// A seek from `ENDED` goes back to `BUFFERING`. A new [MediaPlayer#open] goes
/// back to `OPENING` from any state.
public enum PlaybackState {
    /// Nothing opened.
    IDLE,
    /// Opening the source and probing it.
    OPENING,
    /// Filling the buffers before playing, or refilling them after a stall.
    BUFFERING,
    /// Playing.
    PLAYING,
    /// Paused by [MediaPlayer#pause()].
    PAUSED,
    /// Played to the end.
    ENDED,
    /// Failed; [PlayerStatus#error()] says why.
    ERROR;

    /// Whether the player is past opening and not failed: a position means
    /// something.
    public boolean hasMedia() {
        return this == BUFFERING || this == PLAYING || this == PAUSED || this == ENDED;
    }
}
