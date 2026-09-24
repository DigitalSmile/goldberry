package io.github.digitalsmile.goldberry.media.view;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.Overlay;
import io.github.digitalsmile.goldberry.bind.Subscription;
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
///   `Space`/`K`, `←`/`→`, `↑`/`↓`, `M`, `Home`, `,`/`.`, `<`/`>`, and `F`
///   and `Esc` for fullscreen.
/// - **A failure is shown over the picture**, in `.media-error`: an H.264 file
///   says which codecs it could not play (§7, S7).
/// - **A stream's title**, when it announces one, is a line in the overlay, in
///   `.media-now-playing` (§7, S6).
/// - **Fullscreen**, where the host has a window to ask (ADR-0473): a button at
///   the end of the controls, `F`, and `Esc` to leave. A copy of the player
///   covers the whole window, `.is-fullscreen` and with no id, and the window is
///   asked to fill its display. Leaving gives the window back as it was, and the
///   user leaving with the platform's own button takes the copy away too. A
///   window manager that refuses leaves the copy filling the window, which `F`
///   and `Esc` still leave.
/// - **Subtitles**, once chosen from the subtitles menu or loaded with
///   [MediaPlayer#loadSubtitles], are lines over the foot of the picture, in
///   `.media-subtitles` and `.media-subtitle`: above the controls while they
///   show, and lower while they hide.
///
/// Parts, for a stylesheet: `media-player` itself, the `video-view` in it, the
/// `.media-overlay` column laid over the bottom, and `media-controls` in that.
/// The state is on the root as `.is-playing`, `.is-paused` and the rest, and
/// the full-window copy is `.is-fullscreen`, with `.media-fullscreen` on its
/// button.
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

    /// What both the player in the layout and its full-window copy do: follow
    /// the player, lay the overlay over the picture, and hide the controls while
    /// the pointer rests.
    ///
    /// @param <W> the widget: [MediaPlayerView], or the copy [FullscreenPlayer]
    abstract static sealed class PlayerState<W extends Widget> extends FollowingState<W>
            permits MediaPlayerState, FullscreenState {

        final Transport transport = new Transport();
        private boolean idle;

        @Nullable
        Host host;

        private EventLoop.@Nullable Timer hide;

        /// The `media-player` this state shows.
        abstract MediaPlayerView view(W widget);

        /// Fullscreen as this state offers it, or null where it offers none.
        abstract Transport.@Nullable Fullscreen fullscreen();

        /// Whether this is the copy over the whole window: `.is-fullscreen`.
        boolean fillsWindow() {
            return false;
        }

        @Override
        boolean showsPictures() {
            return true;
        }

        @Override
        final MediaPlayer player(W widget) {
            return view(widget).player();
        }

        @Override
        Widget build(BuildContext context, PlayerStatus status) {
            host = context.host().orElse(null);
            var view = view(widget());
            var player = view.player();
            var fullscreen = fullscreen();

            var overlay = new ArrayList<Widget>(3);
            Transport.nowPlaying(status).ifPresent(overlay::add);
            status.error()
                    .ifPresent(error -> overlay.add(new Text(error.message(), Attributes.NONE.classes("media-error"))));
            overlay.add(new MediaControlsBar(
                    transport.controls(player, status, true, fullscreen),
                    null,
                    Set.of("media-controls"),
                    event -> transport.onKey(player, event, fullscreen)));

            var classes = MediaControls.stateClasses(view.attributes(), status, transport);
            if (hidden(status)) {
                classes.add("is-pointer-idle");
            }
            if (fillsWindow()) {
                classes.add("is-fullscreen");
            }
            var parts = new ArrayList<Widget>(3);
            parts.add(new VideoSurface(player, view.fit(), Attributes.NONE, () -> Transport.toggle(player)));
            subtitleLines(player).ifPresent(parts::add);
            parts.add(new Column(overlay, Attributes.NONE.classes("media-overlay")));
            return new MediaPlayerBox(
                    parts,
                    fillsWindow() ? null : view.attributes().id(),
                    classes,
                    this::activity,
                    event -> transport.onKey(player, event, fullscreen));
        }

        /// The cues showing now, a line each, in a column over the foot of the
        /// picture: above the controls while they show, and lower when they hide.
        /// Nothing when no cue shows.
        static Optional<Widget> subtitleLines(MediaPlayer player) {
            var lines = player.currentSubtitles().stream()
                    .flatMap(cue -> cue.text().lines())
                    .map(line -> (Widget) new Text(line, Attributes.NONE.classes("media-subtitle")))
                    .toList();
            return lines.isEmpty()
                    ? Optional.empty()
                    : Optional.of(new Column(lines, Attributes.NONE.classes("media-subtitles")));
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

    /// The player where the application laid it out, and the owner of its
    /// fullscreen (ADR-0473).
    ///
    /// Entering lays a [FullscreenPlayer] over the whole window with
    /// [Host#fill], then asks the window to fill its display, unless it already
    /// does. Leaving takes the copy away and gives the window back as it was.
    /// The window leaving fullscreen by the platform's own button or key takes
    /// the copy away too, so it never covers a window the user has made small
    /// again. While the copy shows, this one stops painting pictures nobody
    /// can see.
    static final class MediaPlayerState extends PlayerState<MediaPlayerView> {

        private @Nullable Overlay copy;
        private @Nullable Subscription watch;
        private boolean windowWasFullscreen;

        @Override
        MediaPlayerView view(MediaPlayerView widget) {
            return widget;
        }

        @Override
        Transport.@Nullable Fullscreen fullscreen() {
            var current = host;
            return current != null && current.canFullscreen()
                    ? new Transport.Fullscreen(copy != null, this::toggleFullscreen)
                    : null;
        }

        @Override
        boolean showsPictures() {
            return copy == null;
        }

        /// Whether the full-window copy is showing.
        boolean isFullscreen() {
            return copy != null;
        }

        void toggleFullscreen() {
            if (copy == null) {
                enterFullscreen();
            } else {
                leaveFullscreen();
            }
        }

        private void enterFullscreen() {
            var current = host;
            if (current == null || !current.canFullscreen() || copy != null) {
                return;
            }
            windowWasFullscreen = current.isFullscreen();
            copy = current.fill(new FullscreenPlayer(widget(), this::leaveFullscreen));
            watch = current.onFullscreenChanged(full -> {
                if (!full) {
                    // The user left with the platform's own button: the window
                    // is already what it should be, so only the copy goes.
                    leave(false);
                }
            });
            if (!windowWasFullscreen) {
                current.setFullscreen(true);
            }
            refresh();
        }

        /// What `F`, `Esc` and the button do from either copy: leave, and give
        /// the window back as it was.
        private void leaveFullscreen() {
            leave(true);
        }

        /// Takes the copy away and, when `restoreWindow`, asks the window to
        /// leave fullscreen too, unless it was fullscreen before this player
        /// was.
        private void leave(boolean restoreWindow) {
            if (copy == null) {
                return;
            }
            copy.remove();
            copy = null;
            if (watch != null) {
                watch.close();
                watch = null;
            }
            var current = host;
            if (restoreWindow && !windowWasFullscreen && current != null) {
                current.setFullscreen(false);
            }
            refresh();
        }

        @Override
        protected void didUpdateWidget(MediaPlayerView previous) {
            super.didUpdateWidget(previous);
            var current = host;
            if (copy != null && current != null && !previous.equals(widget())) {
                // An overlay holds the widget it was given, so a new player or
                // fit means a new copy; the window stays as it is.
                copy.remove();
                copy = current.fill(new FullscreenPlayer(widget(), this::leaveFullscreen));
            }
        }

        @Override
        protected void dispose() {
            leaveFullscreen();
            super.dispose();
        }
    }

    /// The full-window copy [MediaPlayerState] lays over the window while
    /// fullscreen: the same player, fit and classes, `.is-fullscreen`, and no
    /// id, since the one in the layout keeps it.
    ///
    /// @param view  the player in the layout, whose player and fit are shown
    /// @param leave what `F`, `Esc` and the button do: the owner leaving
    record FullscreenPlayer(MediaPlayerView view, Runnable leave) implements Widget.Stateful {

        FullscreenPlayer {
            Objects.requireNonNull(view, "view");
            Objects.requireNonNull(leave, "leave");
        }

        @Override
        public State<?> createState() {
            return new FullscreenState();
        }
    }

    /// Shows a [FullscreenPlayer]: a player whose fullscreen is always on.
    static final class FullscreenState extends PlayerState<FullscreenPlayer> {

        @Override
        MediaPlayerView view(FullscreenPlayer widget) {
            return widget.view();
        }

        @Override
        Transport.Fullscreen fullscreen() {
            return new Transport.Fullscreen(true, widget().leave());
        }

        @Override
        boolean fillsWindow() {
            return true;
        }
    }
}
