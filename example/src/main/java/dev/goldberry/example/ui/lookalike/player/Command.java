package dev.goldberry.example.ui.lookalike.player;

import java.time.Duration;
import java.util.Objects;

/// Everything the player's controls can ask for.
///
/// Sealed, so [Session#apply] is one exhaustive `switch` and a control added
/// here without a rule there does not compile.
public sealed interface Command {

    /// Play if paused, pause if playing.
    record Toggle() implements Command {}

    /// The next track, as the button means it — shuffle and repeat included.
    record Next() implements Command {}

    /// Back to the start of this track, or to the one before when this one has
    /// barely begun.
    record Previous() implements Command {}

    /// Time passed while playing.
    ///
    /// @param elapsed how much
    record Tick(Duration elapsed) implements Command {
        public Tick {
            if (elapsed.isNegative()) {
                throw new IllegalArgumentException("time only moves forward, not by " + elapsed);
            }
        }
    }

    /// Jump within the playing track.
    ///
    /// @param fraction where, from `0` at the start to `1` at the end
    record Seek(double fraction) implements Command {}

    /// Set the volume.
    ///
    /// @param level from `0` to `1`
    record Volume(double level) implements Command {}

    /// Mute, or give the volume back.
    record Mute() implements Command {}

    /// Put a heart on a track, or take it off.
    ///
    /// @param trackId which track
    record Like(String trackId) implements Command {
        public Like {
            Objects.requireNonNull(trackId, "trackId");
        }
    }

    /// Turn shuffle on or off.
    record Shuffle() implements Command {}

    /// Move the repeat button on by one.
    record CycleRepeat() implements Command {}

    /// Play a playlist from one of its tracks.
    ///
    /// @param playlistId which playlist becomes the queue
    /// @param index      which of its tracks plays first
    record Play(String playlistId, int index) implements Command {
        public Play {
            Objects.requireNonNull(playlistId, "playlistId");
        }
    }

    /// Show a playlist in the main view, leaving what plays alone.
    ///
    /// @param playlistId which playlist
    record Open(String playlistId) implements Command {
        public Open {
            Objects.requireNonNull(playlistId, "playlistId");
        }
    }

    /// Show or hide the now-playing panel at the right.
    record NowPlayingPanel() implements Command {}
}
