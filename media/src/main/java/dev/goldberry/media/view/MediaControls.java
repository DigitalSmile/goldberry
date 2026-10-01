package dev.goldberry.media.view;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.kdl.KdlNode;
import dev.goldberry.media.MediaPlayer;
import dev.goldberry.media.PlayerStatus;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.markup.Markup;
import dev.goldberry.widgets.markup.Wiring;

/// `media-controls`: the transport controls of a [MediaPlayer], on their own
/// (`docs/goldberry-media.md` §6), for an application that lays them out itself
/// under a `video-view`, or drives a player it shows elsewhere.
///
/// ```java
/// new Column(List.of(new VideoView(player), new MediaControls(player)), Attributes.NONE)
/// ```
///
/// ```kdl
/// media-controls player="trailer"
/// ```
///
/// Play and pause, the elapsed and remaining time, a seek bar that scrubs to
/// keyframes while dragged and lands exactly on release, mute, and volume, with
/// the keys of [Transport]'s table: `Space`/`K`, `←`/`→`, `↑`/`↓`, `M`, `Home`,
/// `,`/`.` to step a picture, and `<`/`>` for the rate.
/// All built from the ordinary controls, so they take the theme like any other.
///
/// Parts, for a stylesheet: `media-controls` itself, and `.media-play`,
/// `.media-time`, `.media-seek`, `.media-live`, `.media-rate`, `.media-mute` and `.media-volume`
/// on its pieces. The state is on it as `.is-playing`, `.is-paused`,
/// `.is-buffering`, `.is-ended` or `.is-error`, and `.is-scrubbing` while the
/// seek bar is held.
///
/// @param player     the player to drive
/// @param attributes id, classes and key
@Markup("media-controls")
public record MediaControls(MediaPlayer player, Attributes attributes)
        implements Widget.Stateful, Attributed<MediaControls> {

    public MediaControls {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(attributes, "attributes");
    }

    /// Controls over `player`.
    public MediaControls(MediaPlayer player) {
        this(player, Attributes.NONE);
    }

    @Override
    public State<?> createState() {
        return new MediaControlsState();
    }

    @Override
    public MediaControls withAttributes(Attributes attributes) {
        return new MediaControls(player, attributes);
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    /// `media-controls player="name"`: the player is a named object the
    /// application registered with the document.
    ///
    /// @throws IllegalArgumentException when the node has children
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        if (!children.isEmpty()) {
            throw new IllegalArgumentException("media-controls takes no children");
        }
        return new MediaControls(
                Objects.requireNonNull(wiring.handle(node, "player", MediaPlayer.class), "player"),
                Attributes.of(node));
    }

    /// The classes a transport bar carries for `status`: the owner's, the state's,
    /// and `.is-scrubbing` while the seek bar is held.
    static LinkedHashSet<String> stateClasses(Attributes attributes, PlayerStatus status, Transport transport) {
        var classes = new LinkedHashSet<>(attributes.classes());
        classes.add("is-" + status.state().name().toLowerCase(Locale.ROOT));
        if (transport.scrubbing()) {
            classes.add("is-scrubbing");
        }
        return classes;
    }

    /// Follows the player and builds the bar.
    static final class MediaControlsState extends FollowingState<MediaControls> {

        private final Transport transport = new Transport();

        @Override
        MediaPlayer player(MediaControls widget) {
            return widget.player();
        }

        @Override
        Widget build(BuildContext context, PlayerStatus status) {
            var widget = widget();
            var player = widget.player();
            return new MediaControlsBar(
                    transport.controls(player, status, true),
                    widget.attributes().id(),
                    stateClasses(widget.attributes(), status, transport),
                    event -> transport.onKey(player, event));
        }

        @Override
        protected void dispose() {
            transport.close();
            super.dispose();
        }
    }
}
