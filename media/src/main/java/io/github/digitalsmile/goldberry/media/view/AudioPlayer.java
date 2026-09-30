package io.github.digitalsmile.goldberry.media.view;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.media.MediaPlayer;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.markup.Markup;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;

/// `audio-player`: compact controls over a [MediaPlayer] (`docs/goldberry-media.md`
/// §6).
///
/// Play and pause, the elapsed and remaining time, a seek bar, mute, and volume,
/// all built from the ordinary controls, so they take the theme and the keyboard
/// focus like any other. The seek bar is left out for a source that cannot seek.
/// For a live stream a `LIVE` label stands in its place, and what the station
/// says is playing (its ICY `StreamTitle`, §7 S6) is a line over the controls.
/// In [io.github.digitalsmile.goldberry.media.PlaybackState#ERROR] the error's
/// message is shown under the controls.
///
/// ```java
/// var player = MediaPlayer.builder().build();
/// player.open(Source.of(Path.of("episode.opus")));
/// return new AudioPlayer(player);
/// ```
///
/// ```kdl
/// audio-player player="episode"
/// ```
///
/// The widget does not own the player: whoever made it opens sources on it and
/// closes it. The widget follows its status, on the UI thread, and while playing
/// it reads the position four times a second.
///
/// Parts, for a stylesheet: `.audio-player` on the whole, and `.media-play`,
/// `.media-time`, `.media-seek`, `.media-live`, `.media-rate`, `.media-mute`, `.media-volume`,
/// `.media-now-playing` and `.media-error` on its pieces. The state is on the root as `.is-playing`,
/// `.is-paused`, `.is-buffering`, `.is-ended` or `.is-error`.
///
/// @param player     the player to show and drive
/// @param attributes id, classes and key
@Markup("audio-player")
public record AudioPlayer(MediaPlayer player, Attributes attributes)
        implements Widget.Stateful, Attributed<AudioPlayer> {

    public AudioPlayer {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(attributes, "attributes");
    }

    /// Controls over `player`.
    public AudioPlayer(MediaPlayer player) {
        this(player, Attributes.NONE);
    }

    @Override
    public State<?> createState() {
        return new AudioPlayerState();
    }

    @Override
    public AudioPlayer withAttributes(Attributes attributes) {
        return new AudioPlayer(player, attributes);
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    /// `audio-player player="name"`: the player is a named object the application
    /// registered with the document.
    ///
    /// @throws IllegalArgumentException when the node has children, which an
    ///                                  `audio-player` does not take
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        if (!children.isEmpty()) {
            throw new IllegalArgumentException("audio-player takes no children");
        }
        return new AudioPlayer(
                Objects.requireNonNull(wiring.handle(node, "player", MediaPlayer.class), "player"),
                Attributes.of(node));
    }
}
