package io.github.digitalsmile.goldberry.media.view;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.image.Image;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.media.MediaPlayer;
import io.github.digitalsmile.goldberry.media.picture.Picture;
import io.github.digitalsmile.goldberry.media.picture.VideoPicture;
import io.github.digitalsmile.goldberry.media.view.gpu.VideoPresenter;
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
/// Each time it renders, it asks the player for [MediaPlayer#shownPicture()]
/// (the newest picture whose time has come on the master clock) and draws it
/// placed by [Fit], centred, over the box's own background. It does not keep the
/// frame loop turning itself: the widget that owns it rebuilds it when the next
/// picture falls due ([FollowingState]), so a 25 fps video costs 25 frames a
/// second, and a paused one none.
///
/// **On the GPU where it can be** (`docs/gpu-plan.md`, phase 6; ADR-0484). With
/// a [VideoPresenter] -- `:gpu` is on the module path -- the picture is a GPU
/// layer over the rectangle [Fit] gives it: its planes converted by a shader,
/// or its BGRA drawn as it is, composited under the window's frame or read
/// back. Where the frame cannot show a layer, and without a presenter, it is
/// drawn on the CPU as before.
///
/// **No conversion on the CPU.** A converted picture is already the toolkit's
/// premultiplied BGRA, so it is wrapped rather than copied, and scaled to the
/// box at the blit. A picture of planes has nothing to blit, and the CPU path
/// draws nothing for it: its view asks for planes only while the GPU shows
/// them.
///
/// **No size of its own**, like `canvas`: a stylesheet gives it one. `media.css`
/// lets it grow.
///
/// @param player     the player whose pictures are drawn
/// @param fit        how a picture fills a box of another shape
/// @param attributes id and classes
/// @param onClick    what a click on the picture does, or null for nothing:
///                   `media-player` plays and pauses
/// @param gpu        what places the picture on the GPU, or null to draw it on
///                   the CPU alone
record VideoSurface(
        MediaPlayer player,
        Fit fit,
        Attributes attributes,
        @Nullable Runnable onClick,
        @Nullable VideoPresenter gpu)
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
        var picture = player.shownPicture();
        return picture.isPresent() ? box.painting(painter(picture.get(), fit, gpu)) : box;
    }

    /// Draws `picture` into the box, placed by `fit`: through `gpu` where the
    /// frame shows GPU layers, and on the CPU otherwise.
    ///
    /// A new painter each frame, on purpose: the render tree compares painters by
    /// identity, and a new one is what tells it this box has new pixels.
    static Painter painter(Picture picture, Fit fit, @Nullable VideoPresenter gpu) {
        var image = picture instanceof VideoPicture converted
                ? Image.of(new PixelBuffer(
                        new PhysicalSize(converted.width(), converted.height()),
                        PixelFormat.BGRA32_PREMULTIPLIED,
                        converted.stride(),
                        converted.pixels()))
                : null;
        return (frame, size) -> {
            var placement = fit.place(
                    picture.width(), picture.height(), picture.width(), picture.height(), size.width(), size.height());
            if (placement == null) {
                return;
            }
            if (gpu != null && gpu.place(frame, picture, placement)) {
                return;
            }
            if (image != null) {
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
    public String accessibleName() {
        return "Video";
    }
}
