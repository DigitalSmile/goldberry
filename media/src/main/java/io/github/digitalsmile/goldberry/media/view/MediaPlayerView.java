package io.github.digitalsmile.goldberry.media.view;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.media.MediaPlayer;
import io.github.digitalsmile.goldberry.media.PlaybackState;
import io.github.digitalsmile.goldberry.media.PlayerStatus;
import io.github.digitalsmile.goldberry.render.event.EventLoop;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.core.image.Fit;
import io.github.digitalsmile.goldberry.widgets.markup.Markup;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// `media-player`: a [MediaPlayer]'s pictures with its controls over them
/// (`docs/goldberry-media.md` §6): `video-view` and `media-controls` in one box.
///
/// ```java
/// var player = MediaPlayer.builder().build();
/// player.open(Source.of(Path.of("clip.webm")));
/// return new MediaPlayerView(player);
/// ```
///
/// ```kdl
/// media-player player="trailer" fit="contain"
/// ```
///
/// - **The controls hide while it plays.** They show while the pointer moves over
///   the player and for [#IDLE_AFTER] after it stops, and whenever the player is
///   not playing. Hidden is `.is-pointer-idle` on the root, and `media.css`
///   fades the overlay out on it, so a theme can do something else. (Not
///   `.is-idle`: that is the player's own state before anything is opened.)
/// - **A click on the picture plays or pauses.**
/// - **The keys** of `media-controls` work anywhere in it once it has focus:
///   `Space`/`K`, `←`/`→`, `↑`/`↓`, `M`, `Home`, `,`/`.` and `<`/`>`.
/// - **A failure is shown over the picture**, in `.media-error`: an H.264 file
///   says which codecs it could not play (§7, S7).
/// - **A stream's title**, when it announces one, is a line in the overlay, in
///   `.media-now-playing` (§7, S6).
///
/// Parts, for a stylesheet: `media-player` itself, the `video-view` in it, the
/// `.media-overlay` column laid over the bottom, and `media-controls` in that.
/// The state is on the root as `.is-playing`, `.is-paused` and the rest.
///
/// @param player     the player to show and drive
/// @param fit        how a picture fills the box
/// @param attributes id, classes and key
@Markup("media-player")
public record MediaPlayerView(MediaPlayer player, Fit fit, Attributes attributes)
        implements Widget.Stateful, Attributed<MediaPlayerView> {

    /// How long after the pointer stops the controls hide, while playing.
    public static final Duration IDLE_AFTER = Duration.ofMillis(2500);

    public MediaPlayerView {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(fit, "fit");
        Objects.requireNonNull(attributes, "attributes");
    }

    /// `player`, letterboxed, with its controls.
    public MediaPlayerView(MediaPlayer player) {
        this(player, Fit.CONTAIN, Attributes.NONE);
    }

    @Override
    public State<?> createState() {
        return new MediaPlayerState();
    }

    @Override
    public MediaPlayerView withAttributes(Attributes attributes) {
        return new MediaPlayerView(player, fit, attributes);
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    /// `media-player player="name" fit="cover"`: the player is a named object the
    /// application registered with the document.
    ///
    /// @throws IllegalArgumentException when the node has children, or `fit` is
    ///                                  not one of the four
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        if (!children.isEmpty()) {
            throw new IllegalArgumentException("media-player takes no children");
        }
        return new MediaPlayerView(
                wiring.handle(node, "player", MediaPlayer.class),
                Fit.named(node.stringProperty("fit")),
                Attributes.of(node));
    }

    /// Follows the player, and decides whether the controls are showing.
    static final class MediaPlayerState extends FollowingState<MediaPlayerView> {

        private final Transport transport = new Transport();
        private boolean idle;
        private @Nullable Host host;
        private EventLoop.@Nullable Timer hide;

        @Override
        boolean showsPictures() {
            return true;
        }

        @Override
        MediaPlayer player(MediaPlayerView widget) {
            return widget.player();
        }

        @Override
        Widget build(BuildContext context, PlayerStatus status) {
            host = context.host().orElse(null);
            var widget = widget();
            var player = widget.player();

            var overlay = new ArrayList<Widget>(3);
            Transport.nowPlaying(status).ifPresent(overlay::add);
            status.error()
                    .ifPresent(error -> overlay.add(new Text(error.message(), Attributes.NONE.classes("media-error"))));
            overlay.add(new MediaControlsBar(
                    transport.controls(player, status),
                    null,
                    Set.of("media-controls"),
                    event -> transport.onKey(player, event)));

            var classes = MediaControls.stateClasses(widget.attributes(), status, transport);
            if (hidden(status)) {
                classes.add("is-pointer-idle");
            }
            return new MediaPlayerBox(
                    List.of(
                            new VideoSurface(player, widget.fit(), Attributes.NONE, () -> Transport.toggle(player)),
                            new Column(overlay, Attributes.NONE.classes("media-overlay"))),
                    widget.attributes().id(),
                    classes,
                    this::activity,
                    event -> transport.onKey(player, event));
        }

        /// Whether the controls are hidden: only while playing, with the pointer
        /// idle, and never while the seek bar is held.
        boolean hidden(PlayerStatus status) {
            return idle && status.state() == PlaybackState.PLAYING && !transport.scrubbing();
        }

        /// The pointer moved over the player (`true`), or left it (`false`).
        void activity(boolean over) {
            cancelHide();
            if (!over) {
                setIdle(true);
                return;
            }
            setIdle(false);
            var window = host;
            if (window != null) {
                hide = window.after(IDLE_AFTER, () -> {
                    hide = null;
                    setIdle(true);
                });
            }
        }

        private void setIdle(boolean value) {
            if (idle != value && isMounted()) {
                setState(() -> idle = value);
            }
        }

        private void cancelHide() {
            var current = hide;
            hide = null;
            if (current != null) {
                current.cancel();
            }
        }

        @Override
        protected void dispose() {
            cancelHide();
            transport.close();
            super.dispose();
        }
    }
}
