package io.github.digitalsmile.goldberry.media.view;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.image.Image;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.media.MediaPlayer;
import io.github.digitalsmile.goldberry.media.VideoPicture;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.paint.Painter;
import io.github.digitalsmile.goldberry.render.PixelBuffer;
import io.github.digitalsmile.goldberry.render.model.PhysicalSize;
import io.github.digitalsmile.goldberry.render.model.PixelFormat;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.semantics.Role;
import io.github.digitalsmile.goldberry.widget.semantics.Semantics;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widgets.core.image.Fit;

/// The node a stylesheet means by `video-view`: the surface a player's pictures
/// are drawn on (`docs/goldberry-media.md` §3, "Presentation", CPU present).
///
/// Each time it renders, it asks the player for [MediaPlayer#currentPicture()]
/// (the newest picture whose time has come on the master clock) and draws it
/// placed by [Fit], centred, over the box's own background. It does not keep the
/// frame loop turning itself: the widget that owns it rebuilds it when the next
/// picture falls due ([FollowingState]), so a 25 fps video costs 25 frames a
/// second, and a paused one none.
///
/// **No conversion here.** The picture is already the toolkit's premultiplied
/// BGRA, so it is wrapped rather than copied, and scaled to the box at the blit.
///
/// **No size of its own**, like `canvas`: a stylesheet gives it one. `media.css`
/// lets it grow.
///
/// @param player     the player whose pictures are drawn
/// @param fit        how a picture fills a box of another shape
/// @param attributes id and classes
/// @param onClick    what a click on the picture does, or null for nothing:
///                   `media-player` plays and pauses
record VideoSurface(
        MediaPlayer player,
        Fit fit,
        Attributes attributes,
        @Nullable Runnable onClick)
        implements Widget.Leaf, io.github.digitalsmile.goldberry.widget.style.Styled, Paints, Handles, Semantics {

    @Override
    public String cssType() {
        return "video-view";
    }

    @Override
    public @Nullable String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var box = Box.of().style(style);
        var picture = player.currentPicture();
        return picture.isPresent() ? box.painting(painter(picture.get(), fit)) : box;
    }

    /// Draws `picture` into the box, placed by `fit`.
    ///
    /// A new painter each frame, on purpose: the render tree compares painters by
    /// identity, and a new one is what tells it this box has new pixels.
    static Painter painter(VideoPicture picture, Fit fit) {
        var image = Image.of(new PixelBuffer(
                new PhysicalSize(picture.width(), picture.height()),
                PixelFormat.BGRA32_PREMULTIPLIED,
                picture.stride(),
                picture.pixels()));
        return (frame, size) -> {
            var placement = fit.place(
                    picture.width(), picture.height(), picture.width(), picture.height(), size.width(), size.height());
            if (placement != null) {
                frame.drawImage(
                        image,
                        placement.source(),
                        placement.x(),
                        placement.y(),
                        placement.width(),
                        placement.height(),
                        1);
            }
        };
    }

    @Override
    public void onPointer(PointerEvent event) {
        if (onClick != null && event.kind() == PointerEvent.Kind.CLICKED) {
            onClick.run();
            event.consume();
        }
    }

    @Override
    public Role role() {
        return Role.FIGURE;
    }

    @Override
    public @Nullable String accessibleName() {
        return "Video";
    }
}
