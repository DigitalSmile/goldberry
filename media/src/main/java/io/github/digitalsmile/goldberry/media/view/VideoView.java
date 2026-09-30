package io.github.digitalsmile.goldberry.media.view;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.media.MediaPlayer;
import io.github.digitalsmile.goldberry.media.PlayerStatus;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.core.image.Fit;
import io.github.digitalsmile.goldberry.widgets.markup.Markup;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;

/// `video-view`: a [MediaPlayer]'s pictures, and nothing else
/// (`docs/goldberry-media.md` §6). The surface `media-player` is built on, for an
/// application that draws its own controls or none.
///
/// ```java
/// var player = MediaPlayer.builder().build();
/// player.open(Source.of(Path.of("clip.webm")));
/// return new VideoView(player, Fit.COVER);
/// ```
///
/// ```kdl
/// video-view player="trailer" fit="cover"
/// ```
///
/// `fit` is §6's `contain | cover | fill` (and `none`, the picture at its own
/// size), `object-fit` by another name: [Fit]. The picture is centred; the rest
/// of the box shows its background, which `media.css` makes black.
///
/// The widget does not own the player. It follows the player's status, so a
/// picture a paused seek lands on is shown when it is ready, and while the player
/// plays it draws a new picture on every frame.
///
/// @param player     the player to show
/// @param fit        how a picture fills a box of another shape
/// @param attributes id, classes and key
@Markup("video-view")
public record VideoView(MediaPlayer player, Fit fit, Attributes attributes)
        implements Widget.Stateful, Attributed<VideoView> {

    public VideoView {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(fit, "fit");
        Objects.requireNonNull(attributes, "attributes");
    }

    /// `player`'s pictures, letterboxed.
    public VideoView(MediaPlayer player) {
        this(player, Fit.CONTAIN, Attributes.NONE);
    }

    /// `player`'s pictures, placed by `fit`.
    public VideoView(MediaPlayer player, Fit fit) {
        this(player, fit, Attributes.NONE);
    }

    @Override
    public State<?> createState() {
        return new VideoViewState();
    }

    @Override
    public VideoView withAttributes(Attributes attributes) {
        return new VideoView(player, fit, attributes);
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    /// `video-view player="name" fit="cover"`: the player is a named object the
    /// application registered with the document.
    ///
    /// @throws IllegalArgumentException when the node has children, or `fit` is
    ///                                  not one of the four
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        if (!children.isEmpty()) {
            throw new IllegalArgumentException("video-view takes no children");
        }
        return new VideoView(
                Objects.requireNonNull(wiring.handle(node, "player", MediaPlayer.class), "player"),
                Fit.named(node.stringProperty("fit")),
                Attributes.of(node));
    }

    /// Follows the player, and builds the surface.
    static final class VideoViewState extends FollowingState<VideoView> {

        @Override
        boolean showsPictures() {
            return true;
        }

        @Override
        MediaPlayer player(VideoView widget) {
            return widget.player();
        }

        @Override
        Widget build(BuildContext context, PlayerStatus status) {
            var view = widget();
            return new VideoSurface(view.player(), view.fit(), view.attributes(), null, presenter());
        }
    }
}
